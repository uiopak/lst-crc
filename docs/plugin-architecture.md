# Plugin Architecture

## Runtime Shape

LST-CRC is organized around one central idea: one selected comparison tab produces one active diff cache, and the diff-dependent surfaces of the plugin react to that cache instead of each recomputing Git state for themselves.

## Entry Points

- `src/main/resources/META-INF/plugin.xml` is the platform entry point. It registers the tool window factory, the custom scope provider, the search-scope provider, the status bar widget factory, the startup activity, the notification group, the tab rename action, and the two VCS Log actions.
- `MyToolWindowFactory` creates the permanent `HEAD` tab, restores persisted comparison tabs (or opens one for the current branch on first use), wires content-manager listeners, and installs the add-tab action and the settings gear menu.
- `PluginStartupActivity` initializes `VcsChangeListener` and `VisualTrackerManager`, refreshes editor tab colors, waits for VCS initialization (`ProjectLevelVcsManager.runAfterInitialization`, so it does not wait for indexing), runs the first diff load, then rebroadcasts the tool-window state and updates the status bar widget.

## Core Service Boundaries

- `ToolWindowStateService` is the orchestration layer. It owns persisted tab state, selected-tab state, refresh sequencing, missing-branch notifications, and the handoff from tab selection to Git refresh.
- `GitService` owns all Git4Idea and git CLI work: repository resolution, `git diff --name-status` / `--numstat` against the target, untracked files, unsaved-document overlays, revision content, and categorized diff construction.
- `ProjectActiveDiffDataService` is the active-diff cache. It stores the categorized file sets, path sets, line stats, and comparison context for the selected tab and publishes `DIFF_DATA_CHANGED_TOPIC` when the active diff changes.
- `LstCrcSettingsService` is the application-level `PersistentStateComponent` for plugin settings (`lstCrcSettings.xml`); on first run it imports values from the legacy `PropertiesComponent` keys. `ToolWindowSettingsProvider` reads settings through it and builds the gear menu, calling the affected components directly when a setting changes.

## Event And Refresh Flow

1. A trigger asks `ToolWindowStateService.refreshDataForCurrentSelection()` to refresh: startup, tab selection, a settings change, or `VcsChangeListener`. `VcsChangeListener` is the only source of automatic refreshes: it listens to `ChangeListManager` updates, `GitRepository.GIT_REPO_CHANGE` and document edits in repository files, and debounces them by 300 ms.
2. `ToolWindowStateService` merges concurrent requests into one coroutine refresh cycle, resolves the selected `TabInfo` and calls `GitService.getChanges(...)`, which runs on `Dispatchers.IO` under a background progress indicator.
3. `GitService` computes categorized changes across all repositories, using the per-repository target from `TabInfo.comparisonMap` (or the tab's own target), and returns the file buckets, line stats and comparison context plus any repositories whose target could not be resolved.
4. Back on the EDT, `ToolWindowStateService` handles missing branches (resetting that repository to `HEAD` and notifying) and passes the result to `ProjectActiveDiffDataService`, which drops results for a tab that is no longer selected. The diff data is pushed even for the `HEAD` tab, whatever `Include HEAD in scopes` says.
5. `ProjectActiveDiffDataService` refreshes file statuses and editor tab colors and publishes `DIFF_DATA_CHANGED_TOPIC`. `LstCrcChangesBrowser` and `VisualTrackerManager` react to the topic. Scopes and the tree renderer read the cache lazily when the IDE evaluates scope membership or repaints. Scopes and gutter markers check `Include HEAD in scopes` themselves.
6. `TOOL_WINDOW_STATE_TOPIC` is published when the tab state changes (tabs, selection, aliases, overrides) and on explicit startup synchronization, not on every diff refresh. The status widget and the tool window factory (tab titles) subscribe to it.

## State Model

- `ToolWindowState` stores the persisted UI state for the tool window: open tabs plus the selected tab index (`gitTabsIdeaPluginState.xml`).
- `TabInfo` stores the per-tab comparison target, optional alias, and a `comparisonMap` that overrides the comparison target for specific repository roots.
- `selectedTabIndex == -1` is the special encoding for the permanent `HEAD` tab. Closable comparison tabs are the only entries stored in `openTabs`.
- `ToolWindowStateService` hands out and broadcasts defensive copies, so listeners cannot mutate the persisted state.

## UI Layers

- `LstCrcChangesBrowser` is the per-tab viewer. It subscribes to `DIFF_DATA_CHANGED_TOPIC`, rebuilds its tree while keeping the viewport, translates mouse gestures into the configured actions (with a coroutine-based single/double-click delay), and reuses an already open diff tab for the same selection.
- `ExpandNewNodesStateStrategy` keeps the user's expand/collapse state across rebuilds and reveals newly added changes.
- `RepoNodeRenderer` appends comparison-context text ("(vs target)") and added/removed line counts to tree rows.
- `BranchSelectionPanel` and `SingleRepoBranchSelectionDialog` provide the searchable branch-selection flows used by the tool window and by branch-failure recovery.
- `LstCrcStatusWidget` mirrors the selected tab label or alias in the status bar and provides a quick popup for switching or adding tabs.
- `ToolWindowUiCompatibility` contains the calls into internal tool-window classes (title visibility, tab actions). `RenameTabAction` still reads the internal `BaseLabel` directly to find the clicked tab.

## IntelliJ Platform Dependencies

- Tool window APIs: `ToolWindowFactory`, `ToolWindowEx`, `ContentManager`, `ContentFactory`
- Persistence APIs: `PersistentStateComponent`, XMLB annotations, project- and application-level services
- Messaging APIs: `Topic`, message-bus publishers and subscribers
- VCS and Git APIs: `GitRepositoryManager`, `GitLineHandler` (git CLI), `GitContentRevision`, `GitFileUtils`, `ChangeListListener`, `ProjectLevelVcsManager`, `FileStatusManager`
- Changes browser APIs: `AsyncChangesBrowserBase`, `AsyncChangesTree`, `TreeModelBuilder`, `ChangesBrowserNodeRenderer`
- Scope APIs: `CustomScopesProvider`, `SearchScopeProvider`, `NamedScope`, `PackageSetBase`, `GlobalSearchScopesCore.filterScope`
- Status-bar APIs: `StatusBarWidget`, popup action groups
- Editor APIs: `FileEditorManager`, `DocumentListener`, line-status trackers (`LineStatusTrackerManager`, `SimpleLocalLineStatusTracker`), file colors

## Threading Model

- Git commands and diff work run in coroutines on `Dispatchers.IO` (or `Dispatchers.Default` for tracker work); results come back to the EDT through `Dispatchers.EDT` or `invokeLater`.
- UI construction, notification display, popup creation, and content-manager mutations stay on the EDT.
- `ToolWindowStateService` never runs two loads at once: requests that arrive during a load are folded into one more cycle. Stale results are also dropped when diff data is applied.

## Startup And HEAD Semantics

- Startup waits for VCS initialization rather than smart mode, because the diff load only needs Git repositories, not indexes. If no Git repository is found it only rebroadcasts the tool-window state.
- `HEAD` is a special case. The permanent `HEAD` tab is always selectable, and the active diff cache is always populated with real data, even on the `HEAD` tab. `Include HEAD in scopes` is checked independently by the scopes (`FileStatusScopes.kt`) and `VisualTrackerManager`, so the browser always shows changes while scopes, gutter markers and file colors follow the user's preference.

## Why The Split Matters

- Heavy diff computation and revision-content loading stay centralized in `GitService`. Some UI and action paths still read lightweight repository state directly for rendering, selection, or repo-specific actions, but the expensive comparison result is shared.
- The message bus and the active-diff cache keep components decoupled, although settings changes still call the affected components directly.
- Multi-repository handling stays in `TabInfo`, `ToolWindowStateService` and `GitService`, which keeps the rest of the UI simple.

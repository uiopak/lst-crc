# Plugin Architecture

## Runtime Shape

LST-CRC is organized around one central idea: one selected comparison tab produces one active diff cache, and the diff-dependent surfaces of the plugin react to that cache instead of each recomputing Git state for themselves.

## Entry Points

- `src/main/resources/META-INF/plugin.xml` is the platform entry point. It registers the tool window factory, the custom scope provider, the search-scope provider, the status bar widget factory, the startup activity, notification group metadata, and VCS log actions.
- `MyToolWindowFactory` creates the permanent `HEAD` tab, restores persisted comparison tabs, wires content-manager listeners, and installs the toolbar actions and settings menu.
- `PluginStartupActivity` eagerly initializes listeners and visual-tracking services, waits for smart mode, and triggers the first refresh so the plugin has a coherent initial state.

## Core Service Boundaries

- `ToolWindowStateService` is the orchestration layer. It owns persisted tab state, selected-tab state, refresh sequencing, notification flow, and the handoff from tab selection to Git refresh.
- `GitService` owns heavy Git4Idea and low-level Git work such as repository resolution, change loading, revision content, and categorized diff construction.
- `ProjectActiveDiffDataService` is the active-diff cache. It stores the categorized file sets and comparison context for the selected tab and publishes `DIFF_DATA_CHANGED_TOPIC` when the active diff changes.
- `ToolWindowSettingsProvider` owns persisted plugin settings and builds the tool-window gear menu. Other components read settings through it or subscribe to settings-change notifications.

## Event And Refresh Flow

1. A startup hook, UI action, tab-selection event, repository change, VFS event, or changelist event asks `ToolWindowStateService` to refresh the current selection.
2. `ToolWindowStateService` chooses the active tab, resolves its `TabInfo`, and calls `GitService.getChanges(...)` for the heavy diff work off the EDT.
3. `GitService` computes categorized changes across one or more repositories and returns both file buckets and comparison-context metadata.
4. `ToolWindowStateService` applies the result on the EDT, updates `ProjectActiveDiffDataService`, and asks the active `LstCrcChangesBrowser` to render the new changes.
5. `VisualTrackerManager` reacts through `DIFF_DATA_CHANGED_TOPIC`, while scopes and tree renderers consume `ProjectActiveDiffDataService` lazily when the IDE evaluates scope membership or repaints the tree. `TOOL_WINDOW_STATE_TOPIC` is broadcast on state load, tab-state changes, and explicit startup synchronization, not on every diff refresh.

## State Model

- `ToolWindowState` stores the persisted UI state for the tool window: open tabs plus the selected tab index.
- `TabInfo` stores the per-tab comparison target, optional alias, and a `comparisonMap` that overrides the comparison target for specific repositories.
- `selectedTabIndex == -1` is the special encoding for the permanent `HEAD` tab. Closable comparison tabs are the only entries stored in `openTabs`.
- Defensive deep copies isolate persisted state from UI subscribers and avoid accidental mutation by listeners.

## UI Layers

- `LstCrcChangesBrowser` is the per-tab viewer. It renders categorized changes, translates mouse gestures into configured actions, rebuilds the tree, and exposes a small internal test surface.
- `RepoNodeRenderer` decorates tree nodes with comparison-context text so users can see what each repository or grouping node is being compared against.
- `BranchSelectionPanel` and `SingleRepoBranchSelectionDialog` provide the searchable branch-selection flows used by the tool window and by branch-failure recovery.
- `LstCrcStatusWidget` mirrors the selected tab label or alias in the status bar and provides a quick popup for switching or adding tabs.

## IntelliJ Platform Dependencies

- Tool window APIs: `ToolWindowFactory`, `ToolWindowEx`, `ContentManager`, `ContentFactory`
- Persistence APIs: `PersistentStateComponent`, XMLB annotations, project-level service registration
- Messaging APIs: `Topic`, message-bus publishers and subscribers
- VCS and Git APIs: `GitRepositoryManager`, `GitChangeUtils`, `GitContentRevision`, `ChangeListListener`, `VcsDirtyScopeManager`, `FileStatusManager`
- Changes browser APIs: `AsyncChangesBrowserBase`, `AsyncChangesTree`, `TreeModelBuilder`, `ChangesBrowserNodeRenderer`
- Scope APIs: `CustomScopesProvider`, `SearchScopeProvider`, `NamedScope`, `PackageSetBase`, `GlobalSearchScope`
- Status-bar APIs: `StatusBarWidget`, popup action groups
- VFS and editor APIs: `BulkFileListener`, `FileEditorManager`, line-status trackers, file-coloring hooks

## Threading Model

- Heavy Git command and diff work stays off the EDT through background tasks, futures, and listener callbacks that defer UI work back to `invokeLater` or EDT-safe paths.
- UI construction, notification display, popup creation, and content-manager mutations stay on the EDT.
- Refresh queuing in `ToolWindowStateService` exists to prevent overlapping loads. Stale results are additionally filtered by active-selection checks when diff data is applied.

## Startup And HEAD Semantics

- Startup is multi-phase. In the normal repo-ready path, `PluginStartupActivity` initializes listeners first, refreshes current colorings before smart mode, then waits for smart mode, triggers the initial refresh, rebroadcasts tool-window state, and explicitly asks the status bar widget to update. If Git is still unavailable after smart mode, it rebroadcasts tool-window state and returns without the normal refresh path.
- `HEAD` is a special case. The permanent `HEAD` tab is always selectable, but the active diff cache is cleared on that tab unless `Include HEAD in scopes` is enabled. That setting affects scopes, custom gutter overlays, and repository-context labels in the changes tree more than the visible tab itself.

## Why The Split Matters

- The plugin deliberately keeps heavy diff computation and revision-content loading centralized. Some UI and action paths still read lightweight repository state directly for rendering, selection, or repo-specific actions, but the expensive comparison result is shared.
- The message bus and active-diff cache keep the plugin reactive without creating hard references between every component, even though some settings reactions still use direct calls for targeted UI updates.
- Multi-repository handling stays concentrated in `TabInfo`, `ToolWindowStateService`, and `GitService`, which keeps the rest of the UI relatively simple.
# File Catalog

This document lists each current `src/main` file separately and explains why it exists, what role it performs, and what it depends on. IDE Starter bridge code lives under `src/testBridge` and is included only for `starterUiTest` / `starterPerformanceTest` (or when `-PincludeTestBridge=true` is passed).

## Entry And Resource Files

### plugin.xml
- Role: Plugin descriptor that registers the tool window, custom scopes, search scopes, startup activity, the status-bar widget factory, the notification group, and VCS log actions.
- Depends on: IntelliJ extension-point schemas, `messages.LstCrcMessages`, and the implementation classes referenced by each registration.
- Connected to: `MyToolWindowFactory`, `LstCrcScopeProvider`, `LstCrcSearchScopeProvider`, `PluginStartupActivity`, `LstCrcStatusWidgetFactory`, and the action classes.
- Why it exists: It is the mandatory platform integration boundary; without it, none of the plugin runtime surfaces would be discoverable by the IDE.

### pluginIcon.svg
- Role: Marketplace and plugin-manager icon asset for the plugin.
- Depends on: JetBrains `META-INF/pluginIcon.svg` naming convention.
- Connected to: IDE plugin management UI and JetBrains Marketplace presentation.
- Why it exists: It gives the plugin a non-generic identity in the IDE and in Marketplace listings.

### LstCrcMessages.properties
- Role: Central bundle of user-facing strings for actions, settings, notifications, scopes, dialogs, and widget text.
- Depends on: `LstCrcBundle` and the resource-bundle declaration in `plugin.xml`.
- Connected to: Most UI-facing Kotlin files and plugin.xml action metadata.
- Why it exists: It keeps text localized and prevents hardcoded UI strings from spreading through the codebase.

## Root And Shared Support

### LstCrcConstants.kt
- Role: Central constant holder for shared identifiers, especially the tool-window id.
- Depends on: No runtime services; only Kotlin constants.
- Connected to: Tool-window lookups in `ToolWindowHelper`, `CreateTabFromRevisionAction`, and `RenameTabAction`.
- Why it exists: It keeps the tool-window id canonical and avoids string drift between platform registrations and runtime lookups.

### LstCrcBundle.kt
- Role: `DynamicBundle` wrapper used to read localized messages.
- Depends on: `DynamicBundle`, `@PropertyKey`, and `LstCrcMessages.properties`.
- Connected to: Actions, services, scopes, widget code, dialogs, and notifications.
- Why it exists: It provides one typed access point for localization and enables IDE inspections for missing message keys.

### LstCrcTopics.kt
- Role: Message-bus topics for active-diff changes (`DIFF_DATA_CHANGED_TOPIC`) and tool-window state changes (`TOOL_WINDOW_STATE_TOPIC`), with their listener interfaces.
- Depends on: `Topic` and listener interfaces.
- Connected to: Publishers `ProjectActiveDiffDataService` and `ToolWindowStateService`; subscribers `LstCrcChangesBrowser` and `VisualTrackerManager` (diff data), `LstCrcStatusWidget` and `MyToolWindowFactory` (tab state).
- Why it exists: It is the decoupling layer that lets multiple plugin surfaces react to the same state changes.

### LstCrcKeys.kt
- Role: Typed `Key` definitions attached to tool-window content tabs (the tab's branch or revision).
- Depends on: IntelliJ `Key` and `Content` metadata support.
- Connected to: `ToolWindowHelper`, `MyToolWindowFactory`, and `RenameTabAction`.
- Why it exists: It keeps tab metadata type-safe and avoids ad-hoc string keys.

### RevisionUtils.kt
- Role: `isCommitHash`, which tells commit hashes (7 to 40 hex characters) from branch names.
- Depends on: Nothing; it is kept local because `GitUtil.isHashString` is not available in every supported IDE version.
- Connected to: `ToolWindowStateService` (missing commits do not trigger the branch-repair flow) and `RepoNodeRenderer` (the commit context-label setting).
- Why it exists: Both callers need the same rule for what counts as a commit.

## Listeners And State

### PluginStartupActivity.kt
- Role: Startup bootstrap. It initializes `VcsChangeListener` and `VisualTrackerManager`, refreshes editor tab colors, waits for VCS initialization (not smart mode), runs the first diff load, then rebroadcasts the tool-window state (which also updates the status bar widget).
- Depends on: `ProjectActivity`, `ProjectLevelVcsManager` (looked up as a service, see `CLAUDE.md`), `GitService`, `ToolWindowStateService`, and `ProjectActiveDiffDataService`.
- Connected to: `VcsChangeListener`, `VisualTrackerManager`, and the initial active-diff load.
- Why it exists: The plugin needs deterministic initialization so scopes, widget text, gutter state, and the tool window start in sync, without waiting for indexing.

### VcsChangeListener.kt
- Role: The only source of automatic refreshes. It listens to `ChangeListManager` updates, `GitRepository.GIT_REPO_CHANGE` and document edits in repository files, and turns bursts of them into one refresh after a 300 ms debounce (a coroutine `Flow`).
- Depends on: `ChangeListManager`, `EditorFactory` document events, the Git repository topic, `ToolWindowStateService`, and `GitService` (to check whether an edited file is in a repository, off the EDT).
- Connected to: The refresh pipeline in `ToolWindowStateService`.
- Why it exists: Local edits, saves, external changes, checkouts and commits all need to refresh the comparison, but typing must not run git on every keystroke.

### TabInfo.kt
- Role: Per-tab state object containing the main comparison target, optional alias, and per-repository override map.
- Depends on: XMLB annotations for serialization.
- Connected to: `ToolWindowState`, `ToolWindowStateService`, `GitService`, widget display, and tool-window restoration.
- Why it exists: Multi-repo comparisons need state that is more expressive than a single branch name.

### ToolWindowState.kt
- Role: Persisted tool-window state container holding open tabs and the selected-tab index.
- Depends on: XMLB collection serialization and `TabInfo`.
- Connected to: `ToolWindowStateService`, `MyToolWindowFactory`, and status-widget state rendering.
- Why it exists: The plugin preserves UI continuity across restarts and needs a project-level storage shape for that data.

## Core Services

### GitService.kt
- Role: Sole Git and Git4Idea integration boundary for repository discovery, change loading, revision content, and branch snapshots.
- Depends on: Git4Idea, low-level Git commands, VCS `Change` models, and plugin state types such as `TabInfo`.
- Connected to: `ToolWindowStateService`, `VisualTrackerManager`, settings code, and branch-selection flows.
- Why it exists: Centralizing all Git logic keeps the rest of the plugin from depending directly on IntelliJ VCS internals.

### ProjectActiveDiffDataService.kt
- Role: Active-diff cache storing categorized file sets and comparison context for the selected tab.
- Depends on: `FileStatusManager`, `FileEditorManager`, and the plugin message bus.
- Connected to: `ToolWindowStateService`, scopes, renderers, `VisualTrackerManager`, and any UI that consumes the active comparison.
- Why it exists: The plugin needs one shared cache so every surface reads the same active diff instead of recomputing Git state.

### ToolWindowStateService.kt
- Role: Main orchestration service for tab state, refresh sequencing, persistence (`gitTabsIdeaPluginState.xml`), and missing-branch notifications. It merges concurrent refresh requests into one coroutine cycle and always pushes real changes into `ProjectActiveDiffDataService`, regardless of the `Include HEAD in scopes` setting.
- Depends on: `PersistentStateComponent`, `GitService`, `ProjectActiveDiffDataService`, notifications, and `SingleRepoBranchSelectionDialog` (the repair action).
- Connected to: Almost every runtime surface, especially the factory, widget, actions, and active-diff consumers. The browser is not updated directly; it reacts to `DIFF_DATA_CHANGED_TOPIC`.
- Why it exists: The plugin needs one authoritative owner for tab lifecycle and refresh ordering.

## Scope And Search Integration

### FileStatusScopes.kt
- Role: Definitions for created, modified, moved, deleted, and changed scopes backed by the active-diff cache. Membership is by file path, so revision-backed deleted files match too. It independently checks `ToolWindowSettingsProvider.isIncludeHeadInScopes()` to exclude `HEAD` data from scopes when the setting is disabled.
- Depends on: `ProjectActiveDiffDataService` path sets, `ToolWindowSettingsProvider`, `LstCrcBundle`, `NamedScope`, and `PackageSetBase`.
- Connected to: `LstCrcScopeProvider`, `LstCrcSearchScopeProvider` for the non-deleted search scopes, `LstCrcChangesBrowser` for deleted-file coloring (`DELETED_SCOPE_ID`), and IDE scope consumers.
- Why it exists: The plugin exposes the active comparison as reusable IDE named scopes, not just as a custom tree. The `Changed` scope intentionally excludes deleted files. The concrete scope classes are kept because UI tests load them by name.

### LstCrcScopeProvider.kt
- Role: `CustomScopesProvider` that contributes the plugin's named scopes to the IDE. The file also holds `LstCrcProvidedScopes`, the single set of scope instances and their search-scope wrappers.
- Depends on: The scope classes declared in `FileStatusScopes.kt`.
- Connected to: `plugin.xml` registration, `LstCrcSearchScopeProvider`, and scope consumers across the IDE.
- Why it exists: IntelliJ needs a dedicated provider to surface plugin-defined scopes in the global scope system.

### LstCrcSearchScopeProvider.kt
- Role: `SearchScopeProvider` that lists the plugin scopes, as `GlobalSearchScopesCore.filterScope(...)` wrappers, in Find/Search scope pickers.
- Depends on: `LstCrcProvidedScopes`.
- Connected to: Find/Search UI and platform search-scope infrastructure.
- Why it exists: Search-scope integration is a separate platform extension point from custom scope registration. It intentionally exposes only created, modified, moved, and changed. Deleted files remain available only as a named scope, because Find in Files cannot enumerate those revision-backed virtual files.

## Gutter And Visual Tracking

### VisualTrackerManager.kt
- Role: Intercepts line-status tracking and renders active-comparison gutter markers against the selected diff target.
- Depends on: `ProjectActiveDiffDataService`, `GitService`, `ToolWindowSettingsProvider`, and line-status tracker APIs.
- Connected to: Editor gutter state, diff-change notifications, settings changes, and active file lookups.
- Why it exists: The plugin's comparison target can differ from `HEAD`, so the standard gutter behavior is not sufficient.

## Tool Window UI And Actions

### ToolWindowHelper.kt
- Role: Shared helper for creating tabs, opening the branch-selection tab, and standardizing content setup.
- Depends on: Tool-window content APIs, `ToolWindowStateService`, `GitService`, `LstCrcChangesBrowser`, and `BranchSelectionPanel`.
- Connected to: `MyToolWindowFactory`, tool-window actions, and the UI test bridge.
- Why it exists: Tab creation and branch-selection tab management are shared workflows used from multiple entry points.

### ExpandNewNodesStateStrategy.kt
- Role: Tree-state strategy that preserves user expansion state while auto-expanding parents of newly added changes.
- Depends on: Changes-tree state APIs, `TreeUtil`, `VirtualFile`, `FilePath`, and `File` for typed node identity resolution.
- Connected to: `LstCrcChangesBrowser` tree rebuilding.
- Why it exists: The browser needs to surface newly introduced files without discarding the user's manual tree state, and it must restore selection without `TreeState.applyTo()` so offscreen selections do not recenter the viewport.

### BranchSelectionPanel.kt
- Role: Searchable tree UI for choosing a branch. Typing filters the tree (rebuilt from the matching branch names) and selects the first match; Enter or a click picks the branch.
- Depends on: `GitService` or a pre-fetched `BranchSnapshot`, Swing and IntelliJ tree/search components, and localized strings. It never runs git itself, so it can be built on the EDT.
- Connected to: `ToolWindowHelper`, `SingleRepoBranchSelectionDialog`, and branch-selection tests.
- Why it exists: Branch selection is a real workflow of its own and needs a reusable, testable UI component.

### LstCrcChangesBrowser.kt
- Role: Main per-tab changes browser. It subscribes to `DIFF_DATA_CHANGED_TOPIC`, rebuilds the tree while keeping the viewport, handles the configured mouse gestures and context menu, colors deleted rows, reuses open diff tabs, and exposes `*ForTest` hooks for the UI tests.
- Depends on: `AsyncChangesBrowserBase`, tree models, `ToolWindowSettingsProvider`, `ProjectActiveDiffDataService`, diff APIs, `RepoNodeRenderer`, and `ExpandNewNodesStateStrategy`. Uses a coroutine-based delay to tell single from double clicks.
- Connected to: `ToolWindowHelper`, `MyToolWindowFactory`, `ToolWindowSettingsProvider` (view rebuilds), and the UI tests.
- Why it exists: It is the primary user-facing comparison UI and the place where active diff data becomes an interactive tree.

### MyToolWindowFactory.kt
- Role: Platform factory that creates the tool window, restores tabs, installs listeners, and wires toolbar/settings actions.
- Depends on: Tool-window APIs, `ToolWindowStateService`, `GitService`, `ToolWindowHelper`, `OpenBranchSelectionTabAction`, and settings/menu builders.
- Connected to: `plugin.xml`, startup flow, tab restoration, and rename synchronization.
- Why it exists: Tool windows in IntelliJ are created through a dedicated factory; this file is the plugin's shell entry point.

### LstCrcStatusWidget.kt
- Role: Status-bar widget plus its co-located factory class. `plugin.xml` registers the factory, and the factory creates the runtime widget instance. The widget shows the selected comparison tab label or alias and provides a popup for switching or adding tabs.
- Depends on: Status-bar APIs, message-bus subscriptions, `ToolWindowStateService`, `ToolWindowHelper`, and settings.
- Connected to: `plugin.xml`, startup refreshes, status-bar UI, and UI test bridge reads.
- Why it exists: It gives users lightweight access to the plugin without forcing the tool window to be visible.

### ToolWindowSettingsProvider.kt
- Role: Read accessors for every setting plus the gear-menu builder for click behavior, gutter options, context labels, line stats, untracked files, widget display, and `Include HEAD in scopes`. When a toggle changes it calls the affected component directly (browser rebuild, tracker refresh, data refresh, widget refresh).
- Depends on: `LstCrcSettingsService` for storage, toggle-action APIs, and `ToolWindowUiCompatibility` for title visibility.
- Connected to: `MyToolWindowFactory`, `VisualTrackerManager`, `LstCrcChangesBrowser`, `LstCrcStatusWidget`, `GitService`, scopes, and the UI test bridge.
- Why it exists: The plugin exposes many interaction toggles and needs one place that builds them and reads their values.

### LstCrcSettingsService.kt
- Role: Application-level `PersistentStateComponent` holding every setting as a string map in `lstCrcSettings.xml`, with typed getters and setters. `LstCrcSettingDefinitions` lists each key and its default.
- Depends on: `PersistentStateComponent`; `PropertiesComponent` only for the one-time import of settings saved by earlier versions.
- Connected to: `ToolWindowSettingsProvider`, and the unit tests, Remote Robot JavaScript and Starter bridge, which call the typed accessors by name.
- Why it exists: Settings need typed, testable storage with defaults, and upgrades must keep the user's configuration.

### ToolWindowUiCompatibility.kt
- Role: The calls into internal tool-window classes: showing or hiding the tool-window title and setting the tab actions.
- Depends on: `ToolWindowEx`, `ToolWindowContentUi` and `ContentManagerImpl` (internal).
- Connected to: `MyToolWindowFactory`, `ToolWindowSettingsProvider`, and the Remote Robot tests (which call `setToolWindowTitleVisible` / `isToolWindowTitleVisible` by reflection).
- Why it exists: The public API cannot hide the tool-window title after creation; keeping the internal calls here makes them easy to find on IDE upgrades.

### LstCrcActionContext.kt
- Role: Small helpers for actions: the selected LST-CRC tab, and the single selected revision or commit in the Git Log.
- Depends on: `ToolWindowStateService`, `VcsDataKeys`, and `VcsLogDataKeys`.
- Connected to: `CreateTabFromRevisionAction`, `SetRevisionAsRepoComparisonAction`, and `ShowRepoComparisonInfoAction`.
- Why it exists: Several actions need the same selection lookups.

### OpenBranchSelectionTabAction.kt
- Role: Toolbar action that opens the temporary branch-selection tab.
- Depends on: `DumbAwareAction`, localized text, and `ToolWindowHelper`.
- Connected to: `MyToolWindowFactory` toolbar setup. The status-widget popup opens the same tab through `ToolWindowHelper`.
- Why it exists: Adding comparison tabs needs a discoverable toolbar entry point.

### CreateTabFromRevisionAction.kt
- Role: VCS Log action that asks for a tab name and opens a comparison tab for the selected revision.
- Depends on: Git Log selection (`LstCrcActionContext`), `ToolWindowHelper`, and `Messages` input UI.
- Connected to: `plugin.xml` action registration and Git log context menus.
- Why it exists: It links the Git log directly into the plugin's comparison-tab workflow.

### SetRevisionAsRepoComparisonAction.kt
- Role: VCS log action that sets the selected revision as the comparison override for one repository in the active tab.
- Depends on: VCS log selection APIs, `GitRepositoryManager`, and `ToolWindowStateService`.
- Connected to: Multi-repo tab state through `comparisonMap` updates.
- Why it exists: Per-repository overrides are a core multi-repo capability, and the Git log is a natural source for those revisions.

### ShowRepoComparisonInfoAction.kt
- Role: Toolbar action (placed right after "Group By") that shows each repository's current comparison target and opens `SingleRepoBranchSelectionDialog` to change it. In a single-repository project it opens the dialog directly.
- Depends on: `GitService`, the selected tab (`LstCrcActionContext`), popup APIs, and `SingleRepoBranchSelectionDialog`.
- Connected to: The browser toolbar and multi-repo configuration UI.
- Why it exists: Users need a visible way to inspect and edit per-repository comparison targets.

### SingleRepoBranchSelectionDialog.kt
- Role: Modal wrapper around `BranchSelectionPanel` for choosing a target for one repository. Choosing the tab's own target removes the override.
- Depends on: `DialogWrapper`, `BranchSelectionPanel`, `GitService`, and `ToolWindowStateService`.
- Connected to: `ShowRepoComparisonInfoAction` and the missing-branch notification in `ToolWindowStateService`.
- Why it exists: Multi-repo target repair needs a focused single-repo selection flow.

### RepoNodeRenderer.kt
- Role: Tree-cell renderer that appends comparison-context text ("(vs target)") to repository and grouping nodes, and added/removed line counts to change, folder and group rows.
- Depends on: Changes-tree renderers, `ProjectActiveDiffDataService`, settings, `GitService`, and `isCommitHash`.
- Connected to: `LstCrcChangesBrowser` rendering and settings-driven visibility rules.
- Why it exists: Users need to see what each subtree is being compared against, especially in multi-repo tabs, and how much changed.

### RenameTabAction.kt
- Role: Tab context-menu action that renames a closable comparison tab through an inline balloon.
- Depends on: Action APIs, popup UI classes, `LstCrcKeys`, `ToolWindowHelper`, and the internal `BaseLabel` class to find the clicked tab.
- Connected to: Tool-window tab context menus, persisted aliases, and widget display text.
- Why it exists: Tab aliases are important when multiple revisions or similar branch names are open at once.

## Test Support

### LstCrcUiTestBridge.kt
- Role: Application-level bridge that exposes plugin state and operations to IDE Starter UI tests.
- Depends on: Core plugin services, editors, scopes, VCS services, and test-only path helpers.
- Connected to: IDE Starter remote test clients and UI-test task wiring.
- Why it exists: The plugin's advanced UI flows need reliable programmatic hooks during automated UI testing. It is implemented in `src/testBridge` and wired into `main` source only for starter test tasks or explicit `-PincludeTestBridge=true` runs.
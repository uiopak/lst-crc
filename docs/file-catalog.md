# File Catalog

This document lists each current `src/main` file separately and explains why it exists, what role it performs, and what it depends on. That includes the IDE Starter test bridge file, which lives under `src/main` for service discovery but is excluded from ordinary main builds unless `starterUiTest` or `includeTestBridge` enables it.

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
- Connected to: Tool-window lookups across the factory, actions, widget, and services.
- Why it exists: It keeps the tool-window id canonical and avoids string drift between platform registrations and runtime lookups.

### LstCrcBundle.kt
- Role: `DynamicBundle` wrapper used to read localized messages.
- Depends on: `DynamicBundle`, `@PropertyKey`, and `LstCrcMessages.properties`.
- Connected to: Actions, services, scopes, widget code, dialogs, and notifications.
- Why it exists: It provides one typed access point for localization and enables IDE inspections for missing message keys.

### LstCrcTopics.kt
- Role: Message-bus topic registry for active diff, settings, and tool-window state changes.
- Depends on: `Topic` and listener interfaces.
- Connected to: Publishers in `ProjectActiveDiffDataService`, `ToolWindowStateService`, and `ToolWindowSettingsProvider`; subscribers in the widget, factory, and visual-tracking code.
- Why it exists: It is the decoupling layer that lets multiple plugin surfaces react to the same state changes.

### LstCrcKeys.kt
- Role: Typed `Key` definitions attached to tool-window content tabs.
- Depends on: IntelliJ `Key` and `Content` metadata support.
- Connected to: `ToolWindowHelper`, `MyToolWindowFactory`, `LstCrcStatusWidget`, and `RenameTabAction`.
- Why it exists: It keeps tab metadata type-safe and avoids ad-hoc string keys.

### RevisionUtils.kt
- Role: Small utility for deciding whether a revision string looks like a Git commit hash.
- Depends on: Kotlin string logic only.
- Connected to: `ToolWindowStateService` branch-failure handling and `RepoNodeRenderer` context-display logic.
- Why it exists: The plugin needs one consistent definition of commit-like revisions to avoid wrong failure handling and noisy UI labels.

### TreeUtils.kt
- Role: Tree-click helper that resolves a `TreePath` only when the mouse is actually over a visible row.
- Depends on: Swing tree APIs.
- Connected to: `LstCrcChangesBrowser` and `BranchSelectionPanel` mouse handling.
- Why it exists: It prevents clicks on whitespace from being treated like clicks on tree nodes.

## Listeners And State

### PluginStartupActivity.kt
- Role: Early startup bootstrap that initializes listeners, visual tracking, and the first refresh after smart mode.
- Depends on: Project startup lifecycle, `ToolWindowStateService`, `ProjectActiveDiffDataService`, listener services, and widget update calls.
- Connected to: `VfsListenerService`, `VcsChangeListener`, `VisualTrackerManager`, and the initial active-diff load.
- Why it exists: The plugin needs deterministic initialization so scopes, widget text, gutter state, and the tool window start in sync.

### VcsChangeListener.kt
- Role: Debounced `ChangeListListener` that refreshes the active comparison after IDE VCS updates finish.
- Depends on: `ChangeListManager`, `Alarm`, and `ToolWindowStateService`.
- Connected to: repository-change events, the browser refresh pipeline, and indirectly to `VfsListenerService` through `VcsDirtyScopeManager` and `ChangeListManager` updates.
- Why it exists: It is the stable VCS-side signal that a file-status batch is complete and safe to consume.

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
- Role: Main orchestration service for tab state, refresh sequencing, persistence, notifications, and active-browser updates.
- Depends on: `PersistentStateComponent`, `GitService`, `ProjectActiveDiffDataService`, notifications, settings, and tool-window APIs.
- Connected to: Almost every runtime surface, especially the factory, widget, actions, and active-diff consumers.
- Why it exists: The plugin needs one authoritative owner for tab lifecycle and refresh ordering.

### VfsListenerService.kt
- Role: VFS-event bridge that marks VCS state dirty when relevant project files change.
- Depends on: `BulkFileListener`, `ProjectFileIndex`, `VcsDirtyScopeManager`, and message-bus subscription to `VFS_CHANGES`.
- Connected to: `PluginStartupActivity`, `VcsChangeListener`, test flows, and the refresh pipeline.
- Why it exists: It closes the gap between raw file-system events and the IDE's higher-level VCS refresh cycle.

## Scope And Search Integration

### FileStatusScopes.kt
- Role: Definitions for created, modified, moved, deleted, and changed scopes backed by the active-diff cache.
- Depends on: `ProjectActiveDiffDataService`, `LstCrcBundle`, `NamedScope`, `PackageSetBase`, and `VcsVirtualFile` handling.
- Connected to: `LstCrcScopeProvider`, `LstCrcSearchScopeProvider` for the non-deleted search scopes, `LstCrcChangesBrowser` for deleted-file coloring, and IDE scope consumers.
- Why it exists: The plugin exposes the active comparison as reusable IDE named scopes, not just as a custom tree. The `Changed` scope intentionally excludes deleted files.

### LstCrcScopeProvider.kt
- Role: `CustomScopesProvider` that contributes the plugin's named scopes to the IDE.
- Depends on: The scope instances declared in `FileStatusScopes.kt`.
- Connected to: `plugin.xml` registration and scope consumers across the IDE.
- Why it exists: IntelliJ needs a dedicated provider to surface plugin-defined scopes in the global scope system.

### LstCrcSearchScopeProvider.kt
- Role: `SearchScopeProvider` that wraps the plugin scopes for Find/Search scope pickers.
- Depends on: `LstCrcProvidedScopes`, `NamedScopeWrapper`, and `NamedScopeManager`.
- Connected to: Find/Search UI and `NamedScopeWrapper`.
- Why it exists: Search-scope integration is a separate platform extension point from custom scope registration, and this provider re-exposes the same named-scope data for Find/Search UI. It intentionally exposes only created, modified, moved, and changed. Deleted files remain available only as a named scope because the current search integration path does not enumerate those revision-backed virtual files correctly.

### NamedScopeWrapper.kt
- Role: Adapter that turns a `NamedScope` into a `GlobalSearchScope` suitable for search UIs.
- Depends on: `GlobalSearchScope`, `NamedScope`, `PackageSetBase`, `NamedScopeManager`, and PSI fallback lookup.
- Connected to: `LstCrcSearchScopeProvider` and search infrastructure.
- Why it exists: The plugin needs custom behavior, especially library exclusion, beyond the default platform wrapper shape.

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
- Depends on: Changes-tree state APIs and `TreeUtil`.
- Connected to: `LstCrcChangesBrowser` tree rebuilding.
- Why it exists: The browser needs to surface newly introduced files without discarding the user's manual tree state.

### BranchSelectionPanel.kt
- Role: Searchable tree UI for choosing a branch or revision target.
- Depends on: `GitService`, Swing and IntelliJ tree/search components, localized strings, and `TreeUtils`.
- Connected to: `ToolWindowHelper`, `SingleRepoBranchSelectionDialog`, and branch-selection UI tests.
- Why it exists: Branch selection is a real workflow of its own and needs a reusable, testable UI component.

### LstCrcChangesBrowser.kt
- Role: Main per-tab changes browser that renders categorized changes, configures gestures, and integrates toolbars, renderers, and repository refresh handling.
- Depends on: `AsyncChangesBrowserBase`, tree models, `ToolWindowSettingsProvider`, diff actions, `RepoNodeRenderer`, and `ExpandNewNodesStateStrategy`.
- Connected to: `ToolWindowStateService`, `ToolWindowHelper`, test bridge helpers, and active repository refreshes.
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
- Role: Central settings holder and gear-menu builder for click behavior, gutter options, context visibility, widget display, and scope-related toggles.
- Depends on: `PropertiesComponent`, toggle-action APIs, message bus, and a few tool-window internals for title visibility.
- Connected to: `MyToolWindowFactory`, `VisualTrackerManager`, `LstCrcChangesBrowser`, `LstCrcStatusWidget`, and the UI test bridge.
- Why it exists: The plugin exposes many interaction toggles and needs one authoritative settings source.

### OpenBranchSelectionTabAction.kt
- Role: Toolbar action that opens the temporary branch-selection tab.
- Depends on: `DumbAwareAction`, localized text, and `ToolWindowHelper`.
- Connected to: `MyToolWindowFactory` toolbar setup and status-widget popup actions.
- Why it exists: Adding comparison tabs needs a discoverable toolbar entry point.

### CreateTabFromRevisionAction.kt
- Role: VCS log action that opens a new comparison tab from a selected revision.
- Depends on: Action-system VCS log data, `ToolWindowHelper`, `ToolWindowStateService`, and `Messages` input UI.
- Connected to: `plugin.xml` action registration and Git log context menus.
- Why it exists: It links the Git log directly into the plugin's comparison-tab workflow.

### SetRevisionAsRepoComparisonAction.kt
- Role: VCS log action that sets the selected revision as the comparison override for one repository in the active tab.
- Depends on: VCS log selection APIs, `GitRepositoryManager`, and `ToolWindowStateService`.
- Connected to: Multi-repo tab state through `comparisonMap` updates.
- Why it exists: Per-repository overrides are a core multi-repo capability, and the Git log is a natural source for those revisions.

### ShowRepoComparisonInfoAction.kt
- Role: Toolbar action that shows current repository comparison targets and opens per-repository selection dialogs.
- Depends on: `GitService`, `ToolWindowStateService`, popup APIs, and `SingleRepoBranchSelectionDialog`.
- Connected to: The browser toolbar, notification recovery flow, and multi-repo configuration UI.
- Why it exists: Users need a visible way to inspect and edit per-repository comparison targets.

### SingleRepoBranchSelectionDialog.kt
- Role: Modal wrapper around `BranchSelectionPanel` for choosing a target for one repository.
- Depends on: `DialogWrapper`, `BranchSelectionPanel`, `GitService`, and `ToolWindowStateService`.
- Connected to: `ShowRepoComparisonInfoAction` and branch-failure recovery notifications.
- Why it exists: Multi-repo target repair needs a focused single-repo selection flow.

### RepoNodeRenderer.kt
- Role: Tree-cell renderer that appends comparison-context text to repository and grouping nodes.
- Depends on: Changes-tree renderers, `ProjectActiveDiffDataService`, settings, `GitService`, and `RevisionUtils`.
- Connected to: `LstCrcChangesBrowser` rendering and settings-driven visibility rules.
- Why it exists: Users need to see what each subtree is being compared against, especially in multi-repo tabs.

### RenameTabAction.kt
- Role: Context-menu action that renames a closable comparison tab.
- Depends on: Action APIs, popup UI classes, `LstCrcKeys`, and `ToolWindowStateService`.
- Connected to: Tool-window tab context menus, persisted aliases, and widget display text.
- Why it exists: Tab aliases are important when multiple revisions or similar branch names are open at once.

## Test Support

### LstCrcUiTestBridge.kt
- Role: Application-level bridge that exposes plugin state and operations to IDE Starter UI tests.
- Depends on: Core plugin services, editors, scopes, VCS services, and test-only path helpers.
- Connected to: IDE Starter remote test clients and UI-test task wiring.
- Why it exists: The plugin's advanced UI flows need reliable programmatic hooks during automated UI testing. It is kept under `src/main` for IDE Starter service discovery, but ordinary main builds exclude it unless `starterUiTest` or `includeTestBridge` enables it.
# File Refactor Audit

This document correlates each production file with the JetBrains Platform APIs and source code it depends on and records realistic opportunities to simplify, remove, or refactor code without dropping plugin capabilities.

## Entry And Resource Files

### plugin.xml
- JetBrains correlation: Standard plugin descriptor using the `toolWindow`, `customScopesProvider`, `searchScopesProvider`, `statusBarWidgetFactory`, and `postStartupActivity` registrations plus the descriptor's notification-group and action declarations.
- Keep assessment: Necessary and already lean; it is the correct place for platform registrations and action metadata.
- Simplify or remove: No meaningful removal opportunity beyond keeping registrations aligned with actual classes.

### pluginIcon.svg
- JetBrains correlation: Uses the documented `META-INF/pluginIcon.svg` convention instead of explicit descriptor wiring.
- Keep assessment: Keep it; otherwise the plugin falls back to a generic icon.
- Simplify or remove: The only worthwhile improvement is adding `pluginIcon_dark.svg` or replacing the placeholder-style artwork with final branding.

### LstCrcMessages.properties
- JetBrains correlation: Standard `DynamicBundle` resource bundle declared in `plugin.xml` and consumed through `LstCrcBundle`.
- Keep assessment: Keep it; the keys are active through code or descriptor-based action lookups.
- Simplify or remove: No dead-string cleanup stood out. The suppression comments for descriptor-only keys are legitimate.

## Root And Shared Support

### LstCrcConstants.kt
- JetBrains correlation: Simple constant holder that supports repeated tool-window lookups across platform APIs.
- Keep assessment: Keep it as the canonical id source.
- Simplify or remove: No worthwhile simplification.

### LstCrcBundle.kt
- JetBrains correlation: Idiomatic `DynamicBundle` wrapper with `@PropertyKey` support.
- Keep assessment: Keep it; it matches standard JetBrains localization practice.
- Simplify or remove: No meaningful simplification. Inlining bundle access would make the codebase worse.

### LstCrcTopics.kt
- JetBrains correlation: Standard message-bus topic registry using `Topic.create()` and listener interfaces.
- Keep assessment: Keep it; centralized topics are the right platform pattern.
- Simplify or remove: No meaningful simplification because each topic represents a distinct event stream.

### LstCrcKeys.kt
- JetBrains correlation: Uses IntelliJ `Key<T>` as intended for component metadata.
- Keep assessment: Keep it; the indirection is valuable despite the file being small.
- Simplify or remove: No worthwhile change.

### RevisionUtils.kt
- JetBrains correlation: Lightweight Git-specific heuristic that does not need a heavier platform dependency.
- Keep assessment: Keep it; both failure handling and UI rendering depend on consistent commit-hash detection.
- Simplify or remove: No meaningful simplification.

### TreeUtils.kt
- JetBrains correlation: Small Swing helper filling a gap left by `getClosestRowForLocation()` semantics.
- Keep assessment: Keep it; it prevents false-positive tree interactions.
- Simplify or remove: No meaningful simplification.

## Listeners And State

### PluginStartupActivity.kt
- JetBrains correlation: Standard post-startup hook plus smart-mode coordination and service initialization.
- Keep assessment: Keep it; startup sequencing is necessary for reliable first render.
- Simplify or remove: Re-check for redundant widget updates or overlapping state broadcasts during startup. Those are the main candidates, not the activity itself.

### VcsChangeListener.kt
- JetBrains correlation: Uses `ChangeListListener.changeListUpdateDone()` and `Alarm`, which are the intended APIs for debounced VCS refreshes.
- Keep assessment: Keep it; this is a stable post-VCS-update refresh signal.
- Simplify or remove: Compare its debounce/refresh role with the browser's repository-change listener to see whether some refresh paths can be unified.

### TabInfo.kt
- JetBrains correlation: XMLB-friendly serialized data object with annotated attributes and map serialization.
- Keep assessment: Keep it; multi-repo comparison overrides need a persistent per-tab model.
- Simplify or remove: Only minor opportunities, such as reviewing whether `sortBeforeSave = false` is still necessary and whether copy behavior could move out of the data class.

### ToolWindowState.kt
- JetBrains correlation: Plain XMLB persistence container used by `PersistentStateComponent`.
- Keep assessment: Keep it; it is the minimal persisted state boundary.
- Simplify or remove: The only realistic improvement is reducing `deepCopy()` overhead by making the state graph more strongly immutable.

## Core Services

### GitService.kt
- JetBrains correlation: Heavy user of Git4Idea, VCS `Change` APIs, content-revision loading, and low-level Git fallbacks. This is the correct abstraction boundary for that API surface.
- Keep assessment: Keep it; the plugin needs one place that owns Git logic, multi-repo fallback behavior, and categorized change generation.
- Simplify or remove: The best opportunities are internal cleanup only: split helper data holders, reduce logging noise, and extract some categorization or content-loading helpers to shrink the file without changing responsibilities.

### ProjectActiveDiffDataService.kt
- JetBrains correlation: Uses `FileStatusManager` and `FileEditorManager` the standard way to invalidate IDE file presentation after diff changes.
- Keep assessment: Keep it; scopes, gutter tracking, and tree decorators need a shared active-diff cache.
- Simplify or remove: Revisit `notifyAffectedFiles()`. The broad `fileStatusesChanged()` call may make the per-file `fileStatusChanged(file)` loop redundant, but verify that no listeners depend on the distinction between global and per-file notifications before removing either call.

### ToolWindowStateService.kt
- JetBrains correlation: Standard project-level `PersistentStateComponent`, message-bus publishing, tool-window lookup, notifications, and background refresh orchestration.
- Keep assessment: Keep it; it is the plugin's real coordination layer and should stay authoritative.
- Simplify or remove: Extract dense branches such as failure handling, refresh queue management, and some guard logic into smaller helpers. A coroutine channel or clearer queue abstraction could replace part of the manual CAS-based refresh coordination if the team wants a larger cleanup.

### VfsListenerService.kt
- JetBrains correlation: Correctly uses `BulkFileListener` plus `VcsDirtyScopeManager` to bridge raw file events into VCS refreshes.
- Keep assessment: Keep it; external file changes and saves still need to reach the VCS pipeline.
- Simplify or remove: Review whether the path-prefix fallback adds real value beyond `ProjectFileIndex.isInContent()`; that is the only realistic simplification target.

## Scope And Search Integration

### FileStatusScopes.kt
- JetBrains correlation: Standard `PackageSetBase`, `NamedScope`, and `VcsVirtualFile` integration, with plugin-specific handling for deleted-file edge cases.
- Keep assessment: Keep it; these scopes are a core capability and are implemented against the right platform abstractions.
- Simplify or remove: The main cleanup candidates are path-handling asymmetry, the special-case deleted-file logic shape, and verifying whether the current `VcsVirtualFile` fallback can be pushed deeper into the data source. Do not remove `LSTCRC.Deleted`; it also supports non-search UI behavior such as deleted-file coloring.

### LstCrcScopeProvider.kt
- JetBrains correlation: Minimal `CustomScopesProvider` implementation.
- Keep assessment: Keep it; the extension point itself is required.
- Simplify or remove: No meaningful simplification.

### LstCrcSearchScopeProvider.kt
- JetBrains correlation: Correct `SearchScopeProvider` usage for search UI integration.
- Keep assessment: Keep it for the current search behavior because it groups the LST-CRC search scopes, intentionally omits deleted-file search, and pairs with `NamedScopeWrapper` to avoid library-backed results.
- Simplify or remove: Small cleanup only. The scope-wrapping logic could be shared more explicitly with `LstCrcScopeProvider` or cached, but replacing this provider would need to preserve the deleted-file omission and current library-search behavior.

### NamedScopeWrapper.kt
- JetBrains correlation: Reimplements the platform's named-scope-to-search-scope adapter. The real public baseline is `GlobalSearchScopesCore.filterScope(...)`; `DefaultSearchScopeProviders.wrapNamedScope(...)` is the IDE's own internal convenience wrapper built on top of that adapter. The plugin keeps its own wrapper to preserve named-scope matching while forcing `isSearchInLibraries() = false`.
- Keep assessment: Keep it while that custom behavior matters.
- Simplify or remove: Re-evaluate whether the platform adapter can be reused if library exclusion is enforced elsewhere. If not, this file is justified.

### Search-Scope Limitation
- JetBrains correlation: Deleted-file revisions are materialized as VCS-backed virtual files, but the current Find/Search integration path treats the plugin wrapper as a plain `GlobalSearchScope`, not as a file enumeration source.
- Keep assessment: Keep the current limitation explicit in the docs. The plugin can classify and color deleted files, but it should not claim deleted-file search support through Find in Files. The same search scopes are also empty on `HEAD` unless `Include HEAD in scopes` is enabled.
- Simplify or remove: If deleted-file search is ever revisited, it needs a different enumeration strategy rather than simply adding `DeletedFilesScope` to `LstCrcSearchScopeProvider`. The platform VCS path that supports change-scoped searching uses a VCS-specific local scope with explicit virtual-file and range enumeration, which deleted revisions do not fit cleanly.

## Gutter And Visual Tracking

### VisualTrackerManager.kt
- JetBrains correlation: Works against line-status tracker infrastructure and active editor presentation in a way the default Git gutter does not cover.
- Keep assessment: Keep it; this is how the plugin projects the active comparison into the editor gutter.
- Simplify or remove: The realistic targets are internal structure only, such as extracting tracker-interception helpers or revisiting refresh/debounce granularity. The capability itself should not be removed.

## Tool Window UI And Actions

### ToolWindowHelper.kt
- JetBrains correlation: Wrapper around content-manager and tool-window APIs, not a custom platform workaround.
- Keep assessment: Keep it; it prevents duplicated tab-creation workflows.
- Simplify or remove: Only very small helpers could be inlined. The branch-selection tab flow is complex enough to justify the helper object.

### ExpandNewNodesStateStrategy.kt
- JetBrains correlation: Clean use of `TreeState` and changes-tree strategy hooks.
- Keep assessment: Keep it; it solves a real UX problem in the changes browser.
- Simplify or remove: Only micro-optimizations are worth considering, such as trimming redundant tree traversals.

### BranchSelectionPanel.kt
- JetBrains correlation: Standard Swing and IntelliJ tree/search controls, implemented in an idiomatic reusable panel.
- Keep assessment: Keep it; branch selection is a first-class workflow.
- Simplify or remove: Search currently rebuilds cloned tree structures on each keystroke. If performance becomes noticeable, that filtering algorithm is the best refactor candidate.

### LstCrcChangesBrowser.kt
- JetBrains correlation: Properly extends `AsyncChangesBrowserBase` and related changes-tree infrastructure instead of reinventing the whole browser.
- Keep assessment: Keep it; this is the core user-facing comparison surface.
- Simplify or remove: Good cleanup candidates exist inside the file only: collapse duplicated click-state handling, separate some test-only helper methods, and revisit manual toolbar-border/layout work.

### MyToolWindowFactory.kt
- JetBrains correlation: Standard `ToolWindowFactory` plus `ContentManagerListener` wiring and restored content creation.
- Keep assessment: Keep it; the factory is mandatory and the current responsibilities are appropriate.
- Simplify or remove: The only meaningful cleanup is trimming repeated guard checks and reviewing whether the state-change subscription is still the best place for alias-sync behavior.

### LstCrcStatusWidget.kt
- JetBrains correlation: Standard status-bar widget plus popup-action composition and message-bus refresh subscription.
- Keep assessment: Keep it; the widget is a useful lightweight entry point.
- Simplify or remove: The popup-building code can be extracted into smaller helpers, but the behavior itself is justified.

### ToolWindowSettingsProvider.kt
- JetBrains correlation: Standard `PropertiesComponent` storage and toggle-action menu building, with one contained use of internal tool-window UI classes.
- Keep assessment: Keep it; centralized settings are the right design.
- Simplify or remove: The strongest cleanup targets are repetitive toggle factories and direct access from tests. The internal `ToolWindowContentUi` coupling should be watched during IDE upgrades.

### OpenBranchSelectionTabAction.kt
- JetBrains correlation: Straightforward `DumbAwareAction` used in a tool-window toolbar.
- Keep assessment: Keep it; users need a direct add-tab entry point.
- Simplify or remove: The file could be inlined into the factory if the team wanted fewer classes, but that would trade clarity for only a tiny reduction in file count.

### CreateTabFromRevisionAction.kt
- JetBrains correlation: Standard VCS log action using `VcsDataKeys` and dialog input.
- Keep assessment: Keep it; it is one of the plugin's main Git Log integrations.
- Simplify or remove: No meaningful simplification beyond cosmetic extraction.

### SetRevisionAsRepoComparisonAction.kt
- JetBrains correlation: Standard VCS log action using commit-selection data and repository resolution.
- Keep assessment: Keep it; multi-repo revision overrides need this integration path.
- Simplify or remove: No meaningful simplification beyond tiny helper extraction.

### ShowRepoComparisonInfoAction.kt
- JetBrains correlation: Toolbar action plus popup/action-group construction built from standard IntelliJ UI APIs.
- Keep assessment: Keep it; it exposes the multi-repo comparison model to users.
- Simplify or remove: The popup-building loop could move into a helper, but there is no compelling removal opportunity.

### SingleRepoBranchSelectionDialog.kt
- JetBrains correlation: Straightforward `DialogWrapper` usage with a reusable panel.
- Keep assessment: Keep it; the recovery and repo-specific selection flow need a dedicated dialog.
- Simplify or remove: Only tiny cleanups, such as trimming defensive dialog guards or centralizing override normalization.

### RepoNodeRenderer.kt
- JetBrains correlation: Correct use of changes-tree renderers and grouping support to append context labels.
- Keep assessment: Keep it; inline comparison context is useful, especially in multi-repo tabs.
- Simplify or remove: No significant simplification stood out. The current branching mostly reflects genuine single-repo versus multi-repo behavior.

### RenameTabAction.kt
- JetBrains correlation: Standard action plus popup balloon usage, but it also reaches into an internal tab-label class.
- Keep assessment: Keep the feature, not necessarily the exact implementation.
- Simplify or remove: This is one of the clearer cleanup candidates. Replacing the custom inline balloon with a more standard input flow would reduce reliance on `BaseLabel` and internal component traversal.

## Test Support

### LstCrcUiTestBridge.kt
- JetBrains correlation: Uses application services, editors, scopes, VCS APIs, and some reflection-heavy inspection to support IDE Starter tests. It is intentionally excluded from the published plugin in normal builds.
- Keep assessment: Keep it for UI testing; the test suite needs a bridge with broad reach.
- Simplify or remove: The best cleanup targets are reflection-heavy tracker inspection, repeated `PropertiesComponent` lookups, and consolidating repeated path and tab-manipulation helpers. Because it is test-only, these are maintainability issues rather than product risks.
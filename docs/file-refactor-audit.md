# Refactor Audit

This file lists refactoring opportunities in `src/main` that were checked against the code, plus the code that looks removable but must stay. Update it when you act on an item or find a new one. Every change must keep all plugin capabilities ([plugin-capabilities.md](plugin-capabilities.md)) and pass both UI suites (see `CLAUDE.md`).

## Open Opportunities

1. **`ToolWindowSettingsProvider` read facade.** It still has one getter per setting (for example `isShowLineStatsInTree()`), each a single `settingsService()[definition]` call. Callers could read `service<LstCrcSettingsService>()[definition]` directly, but the facade is used in about 30 places and keeps them short, so this is optional.

## Looks Removable, Must Stay

- **`*ForTest` methods and the snapshot/debug accessors** (`currentLineStatsSnapshot`, `debugGutterSummaryFor`, `findTabByDisplayName`, ...). The Remote Robot JavaScript, the Starter bridge or unit tests call them by name.
- **Members reached by reflection:** the private `LstCrcChangesBrowser.displayChanges(CategorizedChanges, String)` signature, `ToolWindowUiCompatibility.setToolWindowTitleVisible` / `isToolWindowTitleVisible`, the `LstCrcProvidedScopes` fields, and the concrete scope classes (`ModifiedFilesScope`, ...).
- **No Kotlin `internal` on those members.** The JVM name is mangled and the JavaScript cannot find it.
- **`project.service<ProjectLevelVcsManager>()`** instead of `getInstance`. It is a Kotlin companion only from 2025.3, and only `verifyPlugin` catches the difference.
- **`isCommitHash` in `RevisionUtils.kt`.** `GitUtil.isHashString` is not available in every supported IDE version.
- **The manual selection restore in `ExpandNewNodesStateStrategy`.** `TreeState.applyTo()` recenters the selected row and breaks the viewport guarantees (`C3.12`).
- **`clearActiveDiff` always publishes**, even when the cache is already empty. The browser relies on it to show its "error loading" text.
- **Deleted files stay out of Find/Search scopes.** Find in Files cannot enumerate revision-backed virtual files.
- **Defensive copies in `ToolWindowStateService`.** Listeners and callers must not be able to mutate the persisted tab state.

## Done

- PR #85 removed dead code and single-use indirection across 10 files (−211 lines). It simplified branch filtering in `BranchSelectionPanel`, `openSource` and viewport handling in `LstCrcChangesBrowser`, the status widget's connection handling and tab selection, the shared snapshot swap in `ProjectActiveDiffDataService`, and helpers in `GitService` and `VisualTrackerManager`.
- The follow-up cleanup turned every trace-level `INFO` log into a lazy `logger.debug { ... }`, so nothing is written to `idea.log`, and no message string is built, unless debug logging is on. Before, each refresh (which runs after every pause in typing) and each gutter load wrote `INFO` lines, some with the whole tab state. It also removed the extra `LstCrcStatusWidget.refresh` at startup, since the state broadcast already updates the widget.
- Logging rule: use `logger.debug { ... }` for tracing, and `warn`/`error` only for problems a user or maintainer should see.
- The review after that made refreshes cheaper and removed duplication:
  - **Edit-only refreshes reuse the last git result.** A refresh caused only by typing no longer runs `git diff --name-status`, `--numstat` and `ls-files` again: `GitService` keeps the last disk result per repository (keyed by target and settings) and only overlays the unsaved documents. VCS events, saves, tab switches and settings changes still reload from disk.
  - **Revision content is cached by commit hash.** The unsaved-document overlay used to run `git show` for every unsaved file on every refresh. Content is now cached (64 entries, files up to 512K characters) under the commit the target resolves to, so a moved branch or `HEAD` never serves stale content. Tags and abbreviated hashes are not cached.
  - **One EDT hop less per refresh.** `ProjectActiveDiffDataService` applies the result directly when it is already on the EDT.
  - **`BaseLabel` lookup moved into `ToolWindowUiCompatibility`**, so all internal tool-window calls are in one file. `RenameTabAction` no longer logs a warning when the action is invoked without a clicked tab.
  - **Settings accessors collapsed.** The 38 typed getters and setters in `LstCrcSettingsService` became `settings[definition]` and `settings[definition] = value`. The Remote Robot JavaScript uses the raw-key accessors (`getString`/`setString`, `getBoolean`/`setBoolean`, `getInt`/`setInt`).
- The 2026-09 optimization pass (one PR, no behavior change):
  - **One git process per repository instead of two.** `GitService` reads status, paths and line counts from one `git diff --raw --numstat -z` run (`--raw -z` when line stats are off). `-z` output is unquoted, so paths go through `GitContentRevision.createPath`. `--ignore-cr-at-eol` only changes the counts; raw records still list line-ending-only changes.
  - **Tab switches in the editor only re-check the visible editors' gutters.** Diff-data and settings changes still re-check every open editor.
  - **No-op refreshes skip the path sets.** `updateActiveDiff` compares the new data with the current snapshot before building its path sets.
  - **The tree renderer finds the single-repo annotation node once per tree**, not on every repaint of every row.
  - `debugGutterSummaryFor` reads tracker ranges and mode through the public API instead of reflection (same output string).
  - Inlined one-use helpers in `MyToolWindowFactory`, `ToolWindowHelper`, `RepoNodeRenderer`, `ShowRepoComparisonInfoAction`, `BranchSelectionPanel` (`TreeUtil.findNode`), `PluginStartupActivity` and `LstCrcChangesBrowser` (click bindings), and simplified `handleBranchFailures`.
  - Removed `hasSingleSelectedCommit`; `LstCrcActionVisibilityTest` now fakes the Git Log selection with real `CommitId`s.
  - Tests: one `toJsStringLiteral` in `plugin/utils/JsStrings.kt` instead of seven copies, and the viewport tests in `LstCrcBranchComparisonUiTest` share `openFeatureComparison`, `selectLastChangeAndScrollToTop` and the tracking helpers.
- The second optimization pass (2026-09, no behavior change):
  - **Unsaved edits reach the tree again.** `Change.equals` only compares paths, so the no-op check from the first pass also skipped refreshes that carried new unsaved content, and the tree kept the older `Change` (a diff opened from it showed the older text). The overlay revisions are now `TextContentRevision`s, equal only for the same text, and `updateActiveDiff` also compares revisions and file statuses. Identical results are still skipped.
  - **Tree-state keys are built once per node.** `ExpandNewNodesStateStrategy.restoreState` extends each node's path and key from its parent's in the traversal, instead of rebuilding both from the root for every node.
  - `buildLineStats` computes each change key once. `CategorizedChanges.EMPTY` replaces two spelled-out empty values. `trackedDiffArgs` builds the `git diff` options for both the stats and no-stats cases.
  - Settings use one `SettingDefinition<T>` (key, default, parser) instead of three definition classes, with one `get`/`set` operator pair. Keys, defaults and the raw-key accessors used by the Remote Robot JavaScript are unchanged.
  - Inlined one-use helpers in `ToolWindowSettingsProvider`, `VisualTrackerManager`, `LstCrcChangesBrowser`, `BranchSelectionPanel`, `CreateTabFromRevisionAction`, `SingleRepoBranchSelectionDialog`, `RenameTabAction`, `PluginStartupActivity` and `LstCrcStatusWidget`.
  - Tests: removed the unused `IdeaFrame.selectedChangesTreeItemMetadata`; `selectedChangesTreeRenderedTextSnapshot` calls `visibleRowTextsForTest()` instead of reading private renderer fields by reflection.
  - `stopUiTestProcesses` in `build.gradle.kts` uses one stop-and-wait helper for the graceful and forced passes.

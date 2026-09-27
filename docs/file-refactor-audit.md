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

- The 2026-09 eighth pass (Remote Robot test code only, no plugin change):
  - `plugin/utils/JsScripts.kt` holds the JavaScript the scripts shared by copy: the open project, the plugin class loader, the tool window, "reload the selected comparison and wait" and "notice external changes". `IdeaFrame`, `GitChangesViewFixture`, `BranchSelectionFixture`, `ActiveDiffQueries` and `PluginUiTestSteps` use it instead of their own copies.
  - `PluginUiTestSteps` has one refresh after git commands and file writes (it now also refreshes the VFS after git commands, which change files on disk).
  - Three hand-written scope scripts (about 50 lines each) in `LstCrcFileScopeUiTest` and `LstCrcBranchComparisonUiTest` became `filesMatchingScope` checks. They now compare exact scope contents; the old scripts looked up moved and created files under names the comparison does not give them, and only checked that a file was in a scope.

- The 2026-09 seventh pass (bug fixes, each with a regression test):
  - **Closing the selected tab loads the tab that takes its place.** The tool window selects the tab before a closed selected tab, and the state had already moved its selection there, so the tool window's selection loaded nothing. After closing the rightmost tab, the tab before it kept its old tree while gutters, scopes and tab colours still used the closed tab's comparison. `removeTab` now selects the tab before it (`HEAD` before the first), as the tool window does, and loads it.
  - **Underscores stay in popup names.** The status widget popup and the repository popup passed tab, branch and repository names as action text, which reads `_` as a mnemonic marker, so `feature_login` showed as `featurelogin`. They use `plainTextAction`.
  - **A failed load shows its error in the selected tab.** Cleared diff data has no branch name, and the browsers read that as `HEAD`, so the error went to the `HEAD` tab while the selected tab kept its old tree.
  - **The branch filter selects a branch.** It selected the first node whose text matched, often a folder (`feat` selected the `feature` folder) or the Local/Remote category, and Enter then did nothing. It now selects the first branch the filter leaves.
  - **Parsing edge cases:** a tab in a file name split the `--numstat` record (the file lost its line stats), and an untracked file whose name is only spaces was dropped.
  - Tests: a Remote Robot test (`testClosingSelectedTabActivatesTheTabBeforeIt`, Linux, Windows and macOS) closes the selected tab from its context menu in the real IDE and checks the tree, the active diff and the scopes; the repository popup has its own underscore test.
  - Cleanups: one `runSilentGit` helper for the plugin's three git commands, the startup tab-colour refresh no longer switches to the EDT twice, and a settings toggle helper lost a parameter it never used.
- The 2026-09 sixth pass (bug fixes, each with a regression test):
  - **A collapsed folder stays collapsed after a refresh when it is selected.** The tree saved a selected folder as the changes under it, and selecting those again made `JTree` expand the folder. This was the cause of the flaky macOS `testTreeStatePersistsAcrossTabSwitches`.
  - **A passing `git diff` failure keeps the repository's last result** instead of showing only untracked files and unsaved edits for one refresh.
  - **Gutter markers are re-checked when the repository changes** (commit, checkout, reset), even when the active diff data stays the same.
  - Tests were added for earlier fixes that had none: startup cancellation, moved files in the gutter and in the unsaved-edit overlay, the `Include HEAD in scopes` toggle, and the negative revision-content cache.
  - Diagnostics: Remote Robot runs log the plugin at debug level and upload the IDE log, and a failed run prints the plugin's log lines in the job output.
- The 2026-09 fifth pass (bug fixes first):
  - **A branch named like a file or folder compares.** `git diff <target>` had no `--`, so a branch `docs` next to a `docs` folder failed as an ambiguous argument and the tab fell back to `HEAD`.
  - **A failed `git diff` resets a tab to `HEAD` only when the target is really missing** (`git rev-parse --verify` fails). Other failures show untracked files and unsaved edits for one refresh and retry (since the sixth pass they keep the last result).
  - **Moved files compare with their old path**, in the gutter (which marked every line as added) and in the unsaved-edit overlay (whose line stats stayed at the disk version, after a failing `git show` per refresh).
  - **Selecting the "Select Branch" tab keeps the current comparison.** It used to count as the HEAD tab, so picking a branch flipped gutters, scopes and the widget to `HEAD` and reloaded twice.
  - **`Include HEAD in scopes` takes effect at once.** It asked for a reload, which finds identical data and is skipped since the first pass; it now re-evaluates trackers, file statuses and tab colors.
  - **Standalone gutter trackers are released when their file's last editor closes**, instead of living until the project closes.
  - The revision content cache also remembers files missing from a commit (untracked files with unsaved edits ran a failing `git show` per refresh). Edit-only refreshes that reuse every repository's git result show no progress indicator.
  - `GitService` is split: pure helpers in `GitDiffParsing.kt`, content loading and its cache in `RevisionContent.kt`, result types in `GitChangeModels.kt`. `LstCrcConstants.HEAD` replaces 14 `"HEAD"` literals. The browser's click and context-menu handling is `ChangesTreeClickHandler`.
  - Tests: `VcsChangeListenerTest` waits for the listener to subscribe instead of sleeping 100 ms; the viewport UI tests wait until the edit's refresh has landed before asserting, instead of sleeping or asserting right away.
- The 2026-09 fourth pass:
  - **Refresh requests wait for their own load.** `requestRefresh` used to hand a request the running cycle's future even after that cycle's last load, so `join()` could return with the old state. Requests now queue for the next load and share its future.
  - **Untracked paths are not unescaped.** `git ls-files -z` prints raw paths; `createPathFromEscaped` turned `a\b.txt` into a control character and threw on `a\q.txt`. They now go through `createPath`, like tracked paths.
  - **Settings register themselves** in `LstCrcSettingDefinitions.all`; the hand-kept list and `allKeys` are gone. The JavaScript setters `setString`/`setBoolean`/`setInt` became one `setValue(key, text)`.
  - Tests: the Remote Robot changes-view fixture reads the tree through `viewerTree()` instead of 12 copies of a component search; `addComparisonTab` and a plain `dumbAware {}` replace repeated test steps. The Starter perf test's external-commit wait reports the tab and git state when it times out, the Starter workflow uploads `out/ide-tests/logs` (where `idea.log` goes), and Remote Robot only waits the long server timeout before the robot first answers. That wait's message found the perf test's flake: `commitAll` also committed the IDE's untracked `.idea` files on `perf-branch`, so checking out the default branch deleted `.idea/vcs.xml` while the IDE ran and the Git mapping went away. Starter test repositories now list `.idea/` and `*.iml` in `.git/info/exclude`.

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
- The third optimization pass (2026-09):
  - **No failing `git show` for new files with unsaved edits.** The unsaved-edit overlay skips the target-content lookup for files `git diff` reports as added (not untracked files, which can exist in the target). The lookup always failed for them and the file kept its disk change, which it still does.
  - **Diff tabs of unsaved files follow the text.** The key that reuses an open diff tab includes the unsaved text, because unsaved revisions are always labeled `LOCAL`. Before, opening the diff again after more typing brought back the tab with the older text.
  - `PluginStartupActivity` rethrows cancellation instead of logging "Initial diff load failed" when a project closes during the first load.
  - The topic listeners are `fun interface`s, so subscribers are lambdas. `VcsChangeListener.createForTest` is gone (tests call the `internal` constructor).
  - Inlined one-use helpers in `LstCrcChangesBrowser`, `BranchSelectionPanel` (its category nodes carry their icon), `VisualTrackerManager`, `RepoNodeRenderer` and `ToolWindowHelper`, and removed overrides that restated platform defaults. The status widget's "Add tab" item activates the tool window once instead of twice. The branch-not-found notification action uses `NotificationAction.createSimple`.
  - Tests: `LstCrcBranchComparisonUiTest` reads the rendered `Main.txt` row through `visibleRowTextsForTest()` instead of reflection into renderer fields, and its editor scripts share one `runOnSelectedEditor` helper.

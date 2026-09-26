# Refactor Audit

This file lists refactoring opportunities in `src/main` that were checked against the code, plus the code that looks removable but must stay. Update it when you act on an item or find a new one. Every change must keep all plugin capabilities ([plugin-capabilities.md](plugin-capabilities.md)) and pass both UI suites (see `CLAUDE.md`).

## Open Opportunities

1. **`hasSingleSelectedCommit` duplicates `singleSelectedCommit`.** `LstCrcActionVisibilityTest` fakes the Git Log selection with strings, and replacing the size check with `singleSelectedCommit(e) != null` casts them and fails. Only change this together with the test fake.
2. **`ToolWindowSettingsProvider` read facade.** It still has one getter per setting (for example `isShowLineStatsInTree()`), each a single `settingsService()[definition]` call. Callers could read `service<LstCrcSettingsService>()[definition]` directly, but the facade is used in about 30 places and keeps them short, so this is optional.

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

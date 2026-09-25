# Refactor Audit

This file lists refactoring opportunities in `src/main` that were checked against the code, plus the code that looks removable but must stay. Update it when you act on an item or find a new one. Every change must keep all plugin capabilities ([plugin-capabilities.md](plugin-capabilities.md)) and pass both UI suites (see `CLAUDE.md`).

## Open Opportunities

1. **Settings accessor duplication.**
   - `LstCrcSettingsService` has a typed getter and setter for each of its 19 settings (38 methods), and `ToolWindowSettingsProvider` re-exposes 18 of the getters unchanged.
   - A generic `get(definition)` / `set(definition, value)` would remove about 55 lines.
   - The unit tests, the Remote Robot JavaScript (`IdeaFrame` calls setters such as `setSingleClickAction` by name) and the Starter bridge all use the typed accessors, so they have to change in the same PR.
2. **INFO logging on hot paths.**
   - `ToolWindowStateService.loadDataForTab` writes two `INFO` lines per refresh, and a refresh runs after every 300 ms pause in typing.
   - `GitService.getFileContentForRevision` writes one per gutter load, and several tab-state changes log the whole state.
   - These fill `idea.log` and belong at `debug`. No test reads these log lines.
3. **Redundant widget refresh at startup.** `PluginStartupActivity.syncUiAfterRefresh` publishes `TOOL_WINDOW_STATE_TOPIC`, which already makes `LstCrcStatusWidget` update itself, and then calls `LstCrcStatusWidget.refresh` as well.
4. **Double EDT hop per refresh.**
   - `ToolWindowStateService.loadDataForTab` switches to the EDT, and `ProjectActiveDiffDataService.updateActiveDiff` then schedules another `invokeLater`.
   - Applying the snapshot directly when already on the EDT saves one event-queue round trip per refresh.
   - This changes event ordering slightly, so run both UI suites.
5. **Internal `BaseLabel` in `RenameTabAction`.**
   - The action reads the internal `BaseLabel` class directly to find the clicked tab, although its KDoc says it goes through `ToolWindowUiCompatibility`.
   - Either move the lookup into `ToolWindowUiCompatibility`, so all internal tool-window calls stay in one file, or replace the balloon with a standard input dialog like `CreateTabFromRevisionAction` uses.
   - `LstCrcActionVisibilityTest` covers the lookup.
6. **`hasSingleSelectedCommit` duplicates `singleSelectedCommit`.** `LstCrcActionVisibilityTest` fakes the Git Log selection with strings, and replacing the size check with `singleSelectedCommit(e) != null` casts them and fails. Only change this together with the test fake.

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

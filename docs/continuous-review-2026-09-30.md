# Continuous review, 2026-09-30

Baseline: `origin/main` at `231c49a`, including PRs #101, #102 and #103.
Work branch: `codex/continuous-review-2026-09-30`. Plugin version stays at 0.0.21.

## Evidence required

- Each confirmed bug gets an automated regression test that fails before the
  production fix and passes afterward. Keep the command, failure and result here.
- Performance changes need representative measurements or counts of eliminated
  work. Refactors need a concrete simplification and behavior validation.
- Review all production areas, then perform two consecutive final passes over
  changed code and the highest-risk interactions with no additional findings.
- Final checks include `check`, coverage, both UI compilations with the bridge,
  production packaging and structure, and the six-IDE verifier if platform API
  usage changes. Both full CI UI workflows and standard PR checks must pass.
- Commit, push and create a PR. Merging, releases and version bumps are outside
  this review's authorization.

## Review coverage

All paths below are under `src/main/kotlin/com/github/uiopak/lstcrc` unless noted.

| Area | Files | Review evidence |
| --- | --- | --- |
| Git comparisons, parsing and content | `services/GitService.kt`, `GitDiffParsing.kt`, `GitChangeModels.kt`, `RevisionContent.kt` | Reviewed target precedence, raw/numstat parsing, missing-target recovery, binary counts, rename/new-file overlays, caches and cancellation. F6 and F7 fixed with real Git and nested-root tests. |
| Refresh sequencing and persistence | `services/ToolWindowStateService.kt`, `state/TabInfo.kt`, `ToolWindowState.kt` | Reviewed snapshot ownership, queued futures, selection changes, errors, cancellation and branch repair. F1 and F2 fixed; all 33 state/active-diff tests passed. |
| Active diff and scopes | `services/ProjectActiveDiffDataService.kt`, `scopes/FileStatusScopes.kt`, `LstCrcScopeProvider.kt`, `LstCrcSearchScopeProvider.kt` | Reviewed EDT acceptance, root targets, content identity, category membership, HEAD settings and deleted-file search exclusion. Existing service and scope tests pass in full check. |
| Startup and automatic refresh | `listeners/PluginStartupActivity.kt`, `VcsChangeListener.kt` | Reviewed VCS initialization, cancellation, debounce event retention, saves versus edit-only loads, project ownership and disposal. Existing startup/listener tests pass in full check. |
| Gutter tracking and editor lifetime | `gutters/VisualTrackerManager.kt` | Reviewed generations, pending full refreshes, completed bases, identical-text retention, native interception and editor disposal. Confirmed tracking uses comparison context independently of modified-file membership, so F7 preserves comparison bases. Existing gutter tests pass in full check. |
| Browser, selection and tree state | `toolWindow/LstCrcChangesBrowser.kt`, `ChangesTreeClickHandler.kt`, `ExpandNewNodesStateStrategy.kt`, `RepoNodeRenderer.kt` | Reviewed queued work, source/diff opening, tree restoration, viewport, rendering caches and multi-selection. F4 and F5 fixed; all 23 browser/click tests passed. |
| Branch picking and repository configuration | `toolWindow/BranchSelectionPanel.kt`, `SingleRepoBranchSelectionDialog.kt`, `ShowRepoComparisonInfoAction.kt`, `SetRevisionAsRepoComparisonAction.kt` | Reviewed cached branch sourcing, filtering/Enter, repository selection, current-map updates and removal of redundant overrides. Existing branch/action tests pass in full check. |
| Tool window, tab actions and widget | `toolWindow/MyToolWindowFactory.kt`, `ToolWindowHelper.kt`, `ToolWindowUiCompatibility.kt`, `LstCrcStatusWidget.kt`, `OpenBranchSelectionTabAction.kt`, `CreateTabFromRevisionAction.kt`, `RenameTabAction.kt`, `LstCrcActionContext.kt` | Reviewed persisted order, HEAD versus picker identity, activation, aliases, tab disposal, popup labels, revision selection and internal API boundaries. F8 protects truncation at supplementary characters. Factory/widget/action tests pass. |
| Settings and propagation | `toolWindow/LstCrcSettingsService.kt`, `ToolWindowSettingsProvider.kt` | Reviewed defaults, legacy import, registration completeness, typed reads, click delays and propagation to all open projects. Existing settings and gutter-toggle tests pass in full check. |
| Shared utilities and registrations | `LstCrcConstants.kt`, `utils/LstCrcKeys.kt`, `RevisionUtils.kt`, `messaging/LstCrcTopics.kt`, `resources/LstCrcBundle.kt`, `src/main/resources` | Reviewed topic/service identity, revision detection, resource keys, plugin registration and localization. No production registrations or user-facing strings changed. |
| Packaging, compatibility and test wiring | `build.gradle.kts`, `gradle.properties`, Gradle wrapper, `.github/workflows`, test bridge and test mappings | Reviewed source-set/bridge boundaries, Java toolchains, UI workflow filters, verifier wiring and Kover scope. F3 fixed; wrapper and coverage gates pass. Final production package and CI results recorded below. |

## Passes

### Pass 1, complete

- Read repository instructions, architecture, capabilities and test coverage
  documentation. No `AGENTS.md` was present in the repository or its ancestors.
- Confirmed the working tree was clean, fetched current `main`, and created the
  new branch without modifying the previous review branch.
- Examined every production area listed above. Seven confirmed findings are
  recorded below, each with failing-before and passing-after regression evidence.
- Kept the selected-tab and display-name signatures, private browser display
  signature, reflection hooks, scope types and both UI suites intact.
- Refactors share the tab deep-copy helper and silent Git command setup. No
  performance optimization is claimed without measurement.

## Findings

### F1: Tab lookups exposed mutable persisted comparison maps

- Trigger: A caller obtains `getSelectedTabInfo()` or `findTabByDisplayName()`
  and mutates or replaces that object's `comparisonMap`.
- Expected: Returned state is a snapshot; persisted targets change only through
  service update methods, which broadcast state and schedule refreshes.
- Actual: Both methods returned the stored `TabInfo`, allowing silent target
  changes while cached comparisons and listeners still reflected the old target.
- Regression tests: `testSelectedTabInfoCannotMutateStoredComparisonTargets`
  and `testDisplayNameLookupCannotMutateStoredComparisonTargets` in
  `ToolWindowStateServicePersistenceTest`.
- Before fix: Both tests failed with expected `origin/main`, actual
  `changed-without-refresh`. Command: `gradlew test --tests
  '*ToolWindowStateServicePersistenceTest.testSelectedTabInfoCannotMutateStoredComparisonTargets'
  --tests '*ToolWindowStateServicePersistenceTest.testDisplayNameLookupCannotMutateStoredComparisonTargets'`.
  Log: `build/continuous-review-state-lookups-red.log`.
- Fix: Reuse one deep-copy helper for persistence and tab lookups. Read a single
  state reference for selected-tab identity; scalar branch-name queries avoid
  allocating the tab snapshot.
- After fix: All 18 persistence and refresh tests pass. Command: `gradlew test
  --tests '*ToolWindowStateServicePersistenceTest' --tests
  '*ToolWindowStateServiceRefreshTest'`. Log:
  `build/continuous-review-state-lookups-green.log`.
- Matching capability, architecture, catalog and test-mapping docs updated.

### F2: Obsolete loading errors cleared the current comparison

- Trigger: A repository load fails after the user switches tabs or changes that
  tab's repository target. The current comparison already has cached changes.
- Expected: Discard the obsolete failure and retain the current branch, changes
  and repository context. Report and clear only failures for the current selection.
- Actual: The exception handler unconditionally logged the old error and cleared
  the active diff, although successful results already rejected obsolete targets.
- Regression tests: `testObsoleteTabLoadFailureKeepsPreviouslyLoadedComparison`
  and `testObsoleteRepositoryTargetFailureKeepsPreviouslyLoadedComparison` in
  `ToolWindowStateServiceRefreshTest`. A repository proxy holds the actual
  `GitService` load until the selection changes, then throws a controlled error.
- Before fix: Both tests failed because the active branch became null. The
  cancellation and current-error guard tests passed on the original code.
  Command: `gradlew test --tests '*ToolWindowStateServiceRefreshTest.testObsolete*'
  --tests '*ToolWindowStateServiceRefreshTest.testPlatformCancellation*'
  --tests '*ToolWindowStateServiceRefreshTest.testCurrentLoadFailure*'`.
  Log: `build/continuous-review-load-failures-red.log`.
- Fix: Compare selected branch identity and repository targets on the EDT before
  logging and clearing. Alias changes do not invalidate the comparison. Preserve
  the existing cancellation and current-error behavior.
- After fix: All 33 tests pass across persistence, refresh and active-diff service
  classes. Command: `gradlew test --tests '*ToolWindowStateServicePersistenceTest'
  --tests '*ToolWindowStateServiceRefreshTest' --tests '*ProjectActiveDiffDataServiceTest'`.
  Log: `build/continuous-review-load-failures-green.log`.
- Matching capability, architecture, catalog and test-mapping docs updated.

### F3: Wrapper regeneration would undo the Gradle upgrade

- Trigger: Run the configured `wrapper` task after merging the Gradle update.
- Expected: Preserve the checked-in Gradle 9.8.0 distribution.
- Actual: `gradle.properties` still configured 9.7.1 for the wrapper task.
- Regression check: `verifyGradleWrapperVersion` compares the task's configured
  version with the distribution URL, without modifying the wrapper. It is a
  dependency of `check`, so local checks and PR CI catch future mismatches.
- Before fix: `gradlew verifyGradleWrapperVersion` failed with "The wrapper task
  would replace gradle-9.8.0-bin.zip with Gradle 9.7.1". Log:
  `build/continuous-review-wrapper-red.log`.
- Fix: Align `gradleVersion` with 9.8.0. Plugin version stays at 0.0.21.
- After fix: The same check passed. Log:
  `build/continuous-review-wrapper-green.log`. Build usage documented in the docs index.

### F4: Queued work outlived a closed comparison tab

- Trigger: Close a comparison tab after a click or diff refresh queues its EDT
  callback but before that callback runs.
- Expected: Closing the tab discards its pending work.
- Actual: Immediate click actions still ran, and the disposed browser accepted
  the queued diff snapshot and started rebuilding its tree.
- Regression tests: `ChangesTreeClickHandlerTest.testQueuedClickDoesNotRunAfterHandlerDisposal`
  and `LstCrcChangesBrowserTest.testQueuedRefreshDoesNotUpdateDisposedBrowser`.
  `testQueuedClickRunsWhileHandlerIsAlive` protects normal click behavior.
- Before fix: The click test failed with one action rather than zero. The
  corrected browser test, run against the original browser from `HEAD`, failed
  with two file names rather than an empty snapshot. Commands: `gradlew test
  --tests '*ChangesTreeClickHandlerTest'` and `gradlew test --tests
  '*LstCrcChangesBrowserTest.testQueuedRefreshDoesNotUpdateDisposedBrowser'`.
  Logs: `build/continuous-review-browser-lifetime-red.log` and
  `build/continuous-review-browser-refresh-red.log`.
- Fix: Set a disposal flag before shutdown and check tab and project lifetime
  for queued click actions, keyboard/context actions, refreshes, viewport updates
  and revision-opening callbacks.
- After fix: All 20 browser and click-handler tests passed before the cancellation
  tests were added. Command: `gradlew test --tests '*ChangesTreeClickHandlerTest'
  --tests '*LstCrcChangesBrowserTest'`. Log:
  `build/continuous-review-browser-lifetime-green.log`.

### F5: Cancelled revision opening showed a loading error

- Trigger: Platform or coroutine cancellation interrupts revision-source content
  loading.
- Expected: Stop opening the file without warning or a partial editor.
- Actual: The generic exception handler logged a warning and displayed an Open
  Source Error dialog for cancellation.
- Regression tests: `testPlatformCancelledRevisionOpenDoesNotShowLoadingError`
  and `testCoroutineCancelledRevisionOpenDoesNotShowLoadingError` in
  `LstCrcChangesBrowserTest`. A failing content revision controls the cancellation;
  the pool future lets the test wait for completion before dispatching the warning.
  `testFailedRevisionOpenStillShowsLoadingError` protects genuine-error reporting.
- Before fix: Both tests failed with one warning instead of zero. Command:
  `gradlew test --tests '*LstCrcChangesBrowserTest.test*CancelledRevisionOpen*'`.
  Log: `build/continuous-review-revision-cancellation-red.log`.
- Fix: Rethrow platform cancellation, which the platform pool recognizes. Stop
  coroutine cancellation quietly because the pool logs unhandled coroutine
  cancellation as an error. Ordinary content failures keep their warning.
- After fix: All 23 browser and click-handler tests pass. Log:
  `build/continuous-review-revision-cancellation-green.log`. The full check also
  passes, including both UI source compilations and the coverage gate.

### Validation improvement: Enforce production coverage

- The repository generated Kover XML but had no minimum-coverage rule. Its
  previous report covered 1,536 of 2,303 production lines, 66.7%.
- Kover reports also included unexecuted Starter test code and the UI bridge.
  Reports now exclude only those test-support packages; every production package
  stays included and both UI suites still run independently.
- `check` now runs `koverVerify` with a 66% line-coverage floor derived from that
  baseline. `check` and both UI compilations passed with the rule enabled.
- Enforcement proof: temporarily raising the floor to 100% failed with measured
  production coverage of 70.9677%. Restoring 66% passed. Logs:
  `build/continuous-review-coverage-gate-red.log` and
  `build/continuous-review-coverage-gate-green.log`. The committed floor is 66%.

### F6: Nested-root unsaved files leaked into the parent overlay

- Trigger: Edit an unsaved file in a registered nested repository while the
  parent repository also has a comparison.
- Expected: Each file belongs only to its owning repository's overlay and target.
- Actual: An ancestor-path test alone included the nested file in the parent.
- Regression: `testUnsavedOverlayUsesOnlyTheOwningNestedRepository` registers a
  nested Git repository and keeps independent outer and inner unsaved files.
- Before fix: The parent returned both paths instead of only `Outer.txt`.
  Command: `gradlew test --tests '*GitServiceLineStatsTest.testUnsavedOverlayUsesOnlyTheOwningNestedRepository'`.
  Log: `build/continuous-review-nested-overlay-red.log`.
- Fix: Check repository ownership after taking the document snapshot, outside the
  read action. Preserve ancestor behavior when no owning repository is registered.
- After fix: All 32 Git comparison, overlay and line-stat tests passed before F7
  tests were added. Log: `build/continuous-review-nested-overlay-green.log`.

### F7: Unsaved restoration left content-only files marked modified

- Trigger: Edit and restore a clean file to target text, or use unsaved text to
  restore a file whose disk content differs from the target.
- Expected: No content-only changed entry or line counts remain.
- Actual: Every dirty document became a modified overlay, including equal text.
- Regressions: `testUnsavedRevertOnCleanDiskDoesNotCreateAChange` and
  `testUnsavedRevertRemovesADiskContentChange` run the actual repository loader
  against a real Git target. Their LF fixtures keep physical formatting identical.
- Before fix: Both tests expected zero changes but got one. The mode-change guard
  already passed. Command: `gradlew test --tests '*GitServiceLineStatsTest.testUnsavedRevert*'`.
  Log: `build/continuous-review-unsaved-revert-red.log`.
- Fix: Omit equal-text overlays when there is no disk change. For existing
  modifications, retain target blob IDs only when modes match and verify the
  editor's saved bytes using read-only `git hash-object --path=... --stdin`.
  Preserve file charset, BOM, line separator and Git filters. A failed hash check
  retains the change. Added entries and renames retain their identity.
- Only restored text with an existing eligible disk modification needs the hash
  command. Ordinary edited text does not add a command; restored clean files need
  none. The shared silent-command setup also handles stdin without changing its
  existing signature.
- After fix: All 39 Git comparison, overlay and line-stat tests pass, including
  line-stats-disabled, mode, CRLF, BOM and UTF-16 cases. Log:
  `build/continuous-review-unsaved-revert-green.log`.
- Encoding follow-up: `testUnsavedRevertPreservesLeadingBomTextCharacter` found
  that the initial byte reconstruction mistook leading U+FEFF text for an
  encoder-generated BOM. The real UTF-16LE fixture expected zero changes but got
  one. The BOM-producing encoder guard already passed. Command: `gradlew test
  --tests '*GitServiceLineStatsTest.testUnsavedRevertPreservesLeadingBomTextCharacter'
  --tests '*GitServiceLineStatsTest.testUnsavedRevertWithBomProducingEncoderRemovesContentChange'`.
  Log: `build/continuous-review-leading-bom-red.log`.
- Corrected BOM detection by inspecting encoded neutral text independently of
  document content. This preserves literal leading U+FEFF characters and avoids
  duplicating a BOM produced by the charset encoder. All 41 Git tests pass,
  including both encoding guards. Log: `build/continuous-review-leading-bom-green.log`.
  This follow-up restarted the final review passes and both full UI workflows.

### F8: Widget truncation split supplementary characters

- Trigger: A long alias has an emoji whose surrogate pair straddles the widget's
  truncation boundary.
- Expected: Keep whole characters, show an ellipsis and retain the complete
  alias in the tooltip. Names exactly at the limit stay whole.
- Actual: Taking 19 UTF-16 units left an unpaired high surrogate before the
  ellipsis, displayed as a broken character.
- Regression: `LstCrcStatusWidgetTest.testTruncatedAliasKeepsSupplementaryCharactersWhole`.
- Before fix: The test failed because the widget included the broken character.
  Command: `gradlew test --tests '*LstCrcStatusWidgetTest.testTruncatedAliasKeepsSupplementaryCharactersWhole'`.
  Log: `build/continuous-review-widget-unicode-red.log`.
- Fix: Move the truncation boundary back one unit when it splits a surrogate
  pair. Preserve the existing length limit, prefix, ASCII behavior and tooltip.
- After fix: All six widget tests pass. Command: `gradlew test --tests '*LstCrcStatusWidgetTest'`.
  Log: `build/continuous-review-widget-unicode-green.log`.
- Reviewed all other production substring/take sites; none truncate a user label.
  This finding restarted the requirement for two clean final review passes.

## Final validation

- Before the encoding follow-up, `gradlew check compileTestKotlin compileUiTestKotlin -PincludeTestBridge=true`
  passed with 162 unit/service tests, zero failures, the wrapper guard and Kover
  verification. Both UI suites compile. Production line coverage is 70.9677%.
  Log: `build/continuous-review-final-check.log`.
- Before the encoding follow-up, audited all 243 test methods in the unit, Remote Robot and Starter sources:
  each appears exactly once in `test-to-capability-map.md`.
- `git diff --check` passed. Refetched `origin/main`; it remains at baseline
  `231c49a`.
- The initial production package and structure check passed. Its six-IDE
  verifier reported compatibility with 2025.1.7.2, 2025.2.6.3, 2025.3.6.1,
  2026.1.5, 2026.2.3 and preview 263.5701.42. The production archive had no UI
  test classes, version 0.0.21, since-build 251 and Java 21 class major 65.
  Logs: `build/continuous-review-production-verifier.log` and
  `build/continuous-review-production-artifact.log`.
- Final validation is repeated after the encoding follow-up. Initial UI runs
  36782715804 and 36782718779 were cancelled so the corrected sources can run
  both suites.
- Final `check compileTestKotlin compileUiTestKotlin -PincludeTestBridge=true`
  passed with 164 tests, zero failures/errors, the wrapper guard and coverage
  verification. Production coverage is 1,674 covered / 2,358 total lines,
  70.9924%. Log: `build/continuous-review-final-check-encoding.log`.
- Reaudited all 245 test methods; each has exactly one reverse capability mapping.
- Final `buildPlugin verifyPluginStructure verifyPlugin` passed for the corrected
  artifact. All six IDEs listed above are compatible. The archive contains zero
  UI test classes, Java 21 class major 65, version 0.0.21 and since-build 251.
  Logs: `build/continuous-review-final-production-verifier.log` and
  `build/continuous-review-final-production-artifact.log`.
- Full CI runs use corrected code commit `c974e885e933ddf48bc6128826f23ac64c8383dd`:
  [Remote Robot on Linux, Windows and macOS](https://github.com/uiopak/lst-crc/actions/runs/36784623062)
  and [Starter UI plus performance on Linux](https://github.com/uiopak/lst-crc/actions/runs/36784626117).
  The final outcomes and standard PR checks are recorded in
  [PR #104](https://github.com/uiopak/lst-crc/pull/104). Subsequent documentation
  commits preserve all production, test, build and workflow inputs.

## Final review passes

- Initial final pass A, after F8: reviewed the complete production diff and regression
  tests. Rechecked raw Git blob/mode records, preserved formatting and rename/new
  identity, nested ownership, snapshot copies, obsolete error rejection,
  cancellation handling, browser/click disposal routes, Unicode boundaries and
  build gates. No further actionable findings.
- Initial final pass B: traced edit-only/full reloads through target resolution, the
  active-diff snapshot, scopes, browser updates and native/visual gutters.
  Confirmed that removing an equal-text change retains repository context and
  comparison bases. Revisited switch/error/close ordering, pending gutter
  generations, HEAD/settings behavior, UI bridge/reflection callers and source
  set boundaries. No further actionable findings.
- All production areas were reviewed. The later F7 encoding boundary test found
  a defect in byte reconstruction, so those passes no longer satisfy the final
  completion count. Two clean passes after that correction are required.
- Final pass A, after the encoding correction: reviewed byte reconstruction and
  both new fixtures alongside the complete production diff. Neutral encoder
  probing preserves literal U+FEFF and mandatory BOMs; charset, line endings,
  mode eligibility, hash failures and cancellation retain their behavior.
  Snapshot, error, lifetime and Unicode fixes remain coherent. No actionable
  findings.
- Final pass B, after the encoding correction: revisited the complete unsaved
  overlay-to-snapshot-to-browser/scope/gutter flow, target switching and disposal
  ordering, metadata-only changes, renamed/new paths and settings. Checked
  production/test source boundaries and all UI reflection callers. No actionable
  findings. The two consecutive clean final passes are complete; a later code
  failure would restart them.

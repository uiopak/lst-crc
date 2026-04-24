# Test Capability Matrix

## Helper Audit

### Existing Remote Robot helpers

- `PluginUiTestSteps` owns repository bootstrap and file-system mutations for Remote Robot tests: git init, commits, branches, checkout, create/modify/rename/delete, and project refresh sequencing.
- `GitChangesViewFixture` owns tool-window interactions: add/select tabs and click file rows.
- `BranchSelectionFixture` owns the searchable branch picker flow.
- `IdeaFrame` JS helpers expose plugin-specific assertions and settings mutations that are still UI-driven but bypass brittle visual interactions.

### Existing Starter helpers

- `LstCrcStarterContext` wraps repo bootstrap plus a stable `LstCrcUiTestBridgeRemote` API.
- `LstCrcUiTestBridge` already exposes most stable non-visual assertions: tab selection, tree snapshots, widget text, click-action settings, scope membership, gutter summaries, and repo comparison map inspection.

### Expansion plan

- Prefer extending `LstCrcUiTestBridge` for new capability assertions instead of adding more ad-hoc JS snippets inside Remote Robot tests.
- Keep Remote Robot coverage focused on branch picker, context menus, and other genuinely visual interactions.
- Add unit tests for state persistence, search-scope wrappers, action visibility/behavior, and notification/recovery logic where UI coverage would be slower or more brittle.
- After new coverage lands, collapse duplicated assertion patterns behind bridge methods or shared test fixtures instead of repeating long inline JS blocks.

## Capability Map

| Todo | Capability | Coverage |
| --- | --- | --- |
| 2 | Permanent `HEAD` tab | `LstCrcBranchComparisonUiTest.testGitBranchComparison`, `LstCrcBranchComparisonStarterUiTest.testGitBranchComparison`, `LstCrcFileScopeStarterUiTest.testPermanentHeadTabScopesStayEmptyUntilIncludeHeadIsEnabled` |
| 3 | Additional comparison tabs | `LstCrcBranchComparisonUiTest.testMultipleComparisonTabs`, `LstCrcBranchComparisonStarterUiTest.testMultipleComparisonTabs`, `ToolWindowStateServicePersistenceTest.testLoadStateAndGetStateDefensivelyCopyNestedTabState` |
| 4 | Git Log integration | `LstCrcInteractionUiTest.testStatusWidgetAndRevisionActions`, `LstCrcInteractionStarterUiTest.testStatusWidgetAndRevisionActions` |
| 5 | Multi-repository comparisons | Comparison-map state coverage in `ToolWindowStateServicePersistenceTest.testUpdateTabComparisonMapCopiesOverridesWithoutRefreshWhenDisabled`; repo override recovery covered by `LstCrcInteractionStarterUiTest.testMissingBranchComparisonTargetRecoversToHeadAndShowsWarning` |
| 6 | Dedicated comparison tree and categorized change data | `LstCrcBranchComparisonUiTest.testBranchComparisonUpdatesModifiedScope`, `LstCrcFileScopeUiTest.testFileOperations`, `LstCrcSettingsStarterUiTest.testRenderedTreeContextLabelsRespectSingleRepoAndCommitSettings` |
| 7 | Configurable file actions | `LstCrcInteractionUiTest.testToolWindowClickActions`, `LstCrcSettingsUiTest.testAdditionalClickSettings`, `LstCrcInteractionStarterUiTest.testToolWindowClickActions`, `LstCrcSettingsStarterUiTest.testAdditionalClickSettings` |
| 8 | Branch selection UI | Existing Remote Robot branch-selection flows in `LstCrcBranchComparisonUiTest`, `LstCrcSettingsUiTest`, and `LstCrcInteractionUiTest` |
| 9 | Repository comparison controls | `LstCrcInteractionUiTest.testStatusWidgetAndRevisionActions`, `LstCrcInteractionStarterUiTest.testStatusWidgetAndRevisionActions`, `LstCrcInteractionStarterUiTest.testMissingBranchComparisonTargetRecoversToHeadAndShowsWarning` |
| 10 | Status bar widget | `LstCrcInteractionUiTest.testStatusWidgetAndRevisionActions`, `LstCrcInteractionUiTest.testTabRenameUpdatesWidgetContext`, starter equivalents |
| 11 | Custom named scopes | `LstCrcBranchComparisonUiTest.testBranchComparisonUpdatesModifiedScope`, `LstCrcFileScopeUiTest.testFileOperations`, `LstCrcFileScopeStarterUiTest.testPermanentHeadTabScopesStayEmptyUntilIncludeHeadIsEnabled` |
| 12 | Search-scope integration | `LstCrcSearchScopeProviderTest.testGetDisplayNameAndSearchScopesReturnExpectedLstCrcWrappers`, `LstCrcSearchScopeProviderTest.testNamedScopeWrapperDelegatesPackageSetBaseContainsAndSearchFlags` |
| 13 | File coloring and context labels | `LstCrcSettingsStarterUiTest.testRenderedTreeContextLabelsRespectSingleRepoAndCommitSettings`, `LstCrcSettingsUiTest.testRenderedTreeContextLabelsRespectSingleRepoAndCommitSettings` |
| 14 | Custom gutter tracking | `LstCrcVisualUiTest.testVisualGutterMarkers`, `LstCrcVisualUiTest.testVisualGutterMarkersForModifiedAndDeletedRanges`, `LstCrcVisualStarterUiTest.testVisualGutterMarkers`, plus settings gating in `LstCrcSettingsUiTest.testGutterSettingsAndIncludeHead` and `LstCrcSettingsStarterUiTest.testGutterSettingsAndIncludeHead` |
| 15 | Automatic refresh | Existing branch comparison tests on file edits/tab switches plus `LstCrcInteractionStarterUiTest.testMissingBranchComparisonTargetRecoversToHeadAndShowsWarning` for queued recovery refresh |
| 16 | Persistent UI state | `ToolWindowStateServicePersistenceTest.testLoadStateAndGetStateDefensivelyCopyNestedTabState`, `ToolWindowStateServicePersistenceTest.testNoStateLoadedResetsToHeadSelectionSemantics`, `ToolWindowStateServicePersistenceTest.testUpdateTabComparisonMapCopiesOverridesWithoutRefreshWhenDisabled` |
| 17 | Recovery and notification flow | `LstCrcInteractionStarterUiTest.testMissingBranchComparisonTargetRecoversToHeadAndShowsWarning` |

## Added Tests

### Unit tests

- `ToolWindowStateServicePersistenceTest`: deep-copy protection, HEAD reset semantics, and defensive comparison-map copying without refresh.
- `LstCrcSearchScopeProviderTest`: search-scope provider shape and `NamedScopeWrapper` delegation.

### E2E additions

- `LstCrcFileScopeStarterUiTest.testPermanentHeadTabScopesStayEmptyUntilIncludeHeadIsEnabled`
- `LstCrcSettingsStarterUiTest.testRenderedTreeContextLabelsRespectSingleRepoAndCommitSettings`
- `LstCrcInteractionStarterUiTest.testMissingBranchComparisonTargetRecoversToHeadAndShowsWarning`
- `LstCrcSettingsUiTest.testRenderedTreeContextLabelsRespectSingleRepoAndCommitSettings`

## Refactor Targets After Coverage Lands

- Replace repeated inline JS scope inspection in Remote Robot tests with bridge-level assertions where possible.
- Share branch-comparison fixture setup between Remote Robot and Starter suites when the data preparation is identical.
- Consolidate tab-selection and post-refresh waits behind one helper per stack.
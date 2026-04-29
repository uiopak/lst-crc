# Test To Capability Map

This file maps each active test method to the capability IDs defined in [plugin-capabilities.md](plugin-capabilities.md).

## Unit And Service Tests

| Test | Capability IDs | Notes |
| --- | --- | --- |
| `LstCrcFileStatusScopesTest.testDeletedScopeMatchesDeletedPathsWhileChangedExcludesThem` | `C3.4` | Validates deleted-file scope partitioning. |
| `LstCrcSearchScopeProviderTest.testGetDisplayNameAndSearchScopesReturnExpectedLstCrcWrappers` | `C3.3` | Verifies search-scope publishing shape. |
| `LstCrcSearchScopeProviderTest.testNamedScopeWrapperDelegatesPackageSetBaseContainsAndSearchFlags` | `C3.3` | Verifies wrapper delegation behavior. |
| `LstCrcSearchScopeProviderTest.testSearchScopesReflectDetailedFileStateMembership` | `C3.3` | Verifies detailed created/modified/moved/changed search-scope membership and deleted omission. |
| `LstCrcActionVisibilityTest.testShowRepoComparisonInfoActionHiddenOnHeadAndVisibleForComparisonTab` | `C2.3` | Covers repo-comparison action visibility rules. |
| `LstCrcActionVisibilityTest.testCreateTabFromRevisionActionVisibleOnlyForSingleRevisionSelection` | `C1.3` | Covers revision-tab action visibility rules. |
| `LstCrcActionVisibilityTest.testOpenBranchSelectionTabActionHiddenWhenSelectionTabAlreadyExists` | `C2.1` | Covers branch-picker add action hiding while the selection tab is already open. |
| `LstCrcActionVisibilityTest.testOpenBranchSelectionTabActionVisibleWhenSelectionTabIsAbsent` | `C2.1` | Covers branch-picker add action visibility when no selection tab exists. |
| `LstCrcActionVisibilityTest.testSetRevisionAsRepoComparisonActionVisibleOnlyForSingleCommitSelectionWithActiveTab` | `C2.4` | Covers Git Log repo-comparison action visibility guards. |
| `LstCrcActionVisibilityTest.testRenameTabActionVisibleForClosableBranchTabWhenContextIsNestedUnderBaseLabel` | `C1.5` | Covers positive rename-action visibility through the tab-label component hierarchy. |
| `LstCrcActionVisibilityTest.testRenameTabActionHiddenWithoutRenamableTabContext` | `C1.5` | Covers rename-action rejection for wrong tool window, non-closeable tabs, missing branch keys, and unrelated components. |
| `LstCrcStatusWidgetTest.testGetTextReturnsHeadWhenHeadIsSelectedEvenIfWidgetContextEnabled` | `C1.1` | Covers widget fallback to `HEAD` text when no comparison tab is selected. |
| `LstCrcStatusWidgetTest.testGetTextUsesAliasPrefixAndTruncationForSelectedTab` | `C4.5` | Covers direct widget text computation for alias display, optional context prefix, and status-bar truncation. |
| `LstCrcStatusWidgetTest.testGetTextFallsBackToPluginNameForInvalidSelectedTabIndex` | `C5.3` | Covers defensive widget fallback when persisted tab selection is out of bounds. |
| `ProjectActiveDiffDataServiceTest.testAcceptsHeadUpdateWhenHeadTabIsSelected` | `C1.1`, `C5.1` | Covers `HEAD`-selected diff acceptance at the active-diff service boundary. |
| `ProjectActiveDiffDataServiceTest.testRejectsHeadUpdateWhileComparisonTabIsSelected` | `C5.1` | Covers rejection of `HEAD` diff events while a non-`HEAD` comparison tab remains selected. |
| `ProjectActiveDiffDataServiceTest.testRejectsStaleUpdateWhenSelectedBranchDoesNotMatch` | `C5.1` | Covers stale diff rejection when the selected comparison no longer matches the incoming update. |
| `ToolWindowStateServicePersistenceTest.testLoadStateAndGetStateDefensivelyCopyNestedTabState` | `C5.2` | Covers persisted tab state copying. |
| `ToolWindowStateServicePersistenceTest.testAddTabDeduplicatesAndRemoveTabKeepsOtherTabs` | `C5.2` | Covers add/remove tab identity handling without duplicate branch entries. |
| `ToolWindowStateServicePersistenceTest.testNoStateLoadedResetsToHeadSelectionSemantics` | `C1.1`, `C5.3` | Covers `HEAD` fallback semantics. |
| `ToolWindowStateServicePersistenceTest.testUpdateTabAliasUpdatesMatchingTabAndLeavesOtherTabsUntouched` | `C1.5`, `C5.2` | Covers direct alias updates on one tab without affecting siblings. |
| `ToolWindowStateServicePersistenceTest.testUpdateTabAliasIgnoresMissingTabAndUnchangedAlias` | `C5.2` | Covers alias-update no-op guards. |
| `ToolWindowStateServicePersistenceTest.testUpdateTabComparisonMapCopiesOverridesWithoutRefreshWhenDisabled` | `C2.3`, `C5.2` | Covers persisted per-repo override state. |
| `ToolWindowStateServicePersistenceTest.testUpdateTabComparisonMapIgnoresMissingTabAndUnchangedMap` | `C5.2` | Covers comparison-map no-op guards for missing tabs and unchanged state. |
| `GitServiceOverlayMergeTest.testPreservesNewChangeTypeWhenUnsavedOverlayIsApplied` | `C3.8` | Ensures unsaved overlays keep `NEW` semantics. |
| `GitServiceOverlayMergeTest.testKeepsModificationOverlayForNonNewFiles` | `C3.8` | Ensures non-new overlays stay modifications. |

## Remote Robot UI Tests

| Test | Capability IDs | Notes |
| --- | --- | --- |
| `LstCrcBranchComparisonUiTest.testBranchComparisonUpdatesModifiedScope` | `C1.2`, `C3.1`, `C3.2`, `C5.1` | Branch comparison plus scope refresh. |
| `LstCrcBranchComparisonUiTest.testGitBranchComparison` | `C1.1`, `C1.2`, `C2.1`, `C3.1` | Baseline branch-comparison flow. |
| `LstCrcBranchComparisonUiTest.testUnsavedLocalEditAppearsWithoutSave` | `C3.7`, `C3.8`, `C5.1` | Unsaved overlay visible before save. |
| `LstCrcBranchComparisonUiTest.testNewFileStaysCreatedDuringUnsavedEdits` | `C3.8` | Keeps new-file semantics during overlay merge. |
| `LstCrcBranchComparisonUiTest.testLocalNewFileAppearsInComparisonTab` | `C3.1` | Local new file appears in active comparison tree. |
| `LstCrcBranchComparisonUiTest.testMultipleComparisonTabs` | `C1.4` | Multiple closable tabs coexist and switch. |
| `LstCrcFileScopeUiTest.testFileOperations` | `C3.1`, `C3.2`, `C3.3` | Scope and tree behavior across file operations. |
| `LstCrcInteractionUiTest.testToolWindowClickActions` | `C4.1` | Click-action mapping in the tool window. |
| `LstCrcInteractionUiTest.testContextMenuActionsWhenEnabled` | `C4.2` | Right-click context-menu mode. |
| `LstCrcInteractionUiTest.testStatusWidgetAndRevisionActions` | `C1.3`, `C2.1`, `C2.4` | Widget flow and revision actions. |
| `LstCrcInteractionUiTest.testTabRenameUpdatesWidgetContext` | `C1.5`, `C4.5` | Alias propagation into the widget. |
| `LstCrcInteractionUiTest.testRenameTabPopupRenamesSelectedTab` | `C1.5` | Invokes `RenameTabAction` against a real tab label and commits the alias through the popup text field. |
| `LstCrcInteractionUiTest.testRenameTabContextMenuRenamesSelectedTab` | `C1.5` | Opens the real tool-window tab context menu, chooses `Rename Tab...`, and commits the alias through the popup text field. |
| `LstCrcSettingsUiTest.testTreePresentationAndTitleSettings` | `C4.4`, `C4.6` | Tool-window title and context-label settings. |
| `LstCrcSettingsUiTest.testGutterSettingsAndIncludeHead` | `C4.7`, `C4.8` | Include-`HEAD` and gutter settings. |
| `LstCrcSettingsUiTest.testAdditionalClickSettings` | `C4.1`, `C4.2`, `C4.3` | Extra click settings and delay behavior. |
| `LstCrcSettingsUiTest.testRenderedTreeContextLabelsRespectSingleRepoAndCommitSettings` | `C3.5`, `C4.6` | Single-repo and commit context-label rendering. |
| `LstCrcVisualUiTest.testVisualGutterMarkersForModifiedAndDeletedRanges` | `C3.6`, `C3.7` | Modified/deleted gutter rendering. |
| `LstCrcVisualUiTest.testVisualGutterMarkersForInsertedNewFile` | `C3.7` | Inserted new-file gutter rendering through the standalone visual tracker path. |
| `LstCrcVisualUiTest.testVisualGutterMarkers` | `C3.7` | Modified-range gutter rendering. |

## Starter UI Tests

| Test | Capability IDs | Notes |
| --- | --- | --- |
| `LstCrcBranchComparisonStarterUiTest.testGitBranchComparison` | `C1.1`, `C1.2`, `C2.1`, `C3.1` | Starter branch-comparison baseline. |
| `LstCrcBranchComparisonStarterUiTest.testMultipleComparisonTabs` | `C1.4` | Starter multi-tab flow. |
| `LstCrcFileScopeStarterUiTest.testFileOperations` | `C3.1`, `C3.2`, `C3.3` | Starter scope and tree behavior across file operations. |
| `LstCrcFileScopeStarterUiTest.testPermanentHeadTabScopesStayEmptyUntilIncludeHeadIsEnabled` | `C1.1`, `C3.3`, `C4.7` | `HEAD` scope gating behavior. |
| `LstCrcFileScopeStarterUiTest.testIncludeHeadInScopesDoesNotAffectBranchTabScopes` | `C4.7` | Verifies the `Include HEAD in scopes` toggle does not clear created/changed scopes for non-`HEAD` comparison tabs. |
| `LstCrcFileScopeStarterUiTest.testFindDialogShowsLstCrcSearchScopes` | `C3.3` | Search-scope availability in Find. |
| `LstCrcFileScopeStarterUiTest.testDeletedFilesUseDeletedScopeTreeColor` | `C3.4`, `C3.6` | Deleted-file UI treatment. |
| `LstCrcFileScopeStarterUiTest.testDeletedFileColorDoesNotLeakToModifiedRows` | `C3.6` | Verifies deleted-row coloring stays isolated from ordinary modified rows in the same comparison tree. |
| `LstCrcInteractionStarterUiTest.testToolWindowClickActions` | `C4.1` | Starter click-action mapping. |
| `LstCrcInteractionStarterUiTest.testContextMenuActionsWhenEnabled` | `C4.2` | Starter right-click context menu mode. |
| `LstCrcInteractionStarterUiTest.testStatusWidgetAndRevisionActions` | `C1.3`, `C2.1`, `C2.4` | Starter widget and revision actions. |
| `LstCrcInteractionStarterUiTest.testTabRenameUpdatesWidgetContext` | `C1.5`, `C4.5` | Starter alias propagation into widget text. |
| `LstCrcInteractionStarterUiTest.testMissingBranchComparisonTargetRecoversToHeadAndShowsWarning` | `C2.5`, `C5.1`, `C5.4` | Single-repo recovery and refresh path. |
| `LstCrcInteractionStarterUiTest.testMissingCommitComparisonTargetDoesNotRecoverToHeadOrWarn` | `C2.6`, `C5.4` | Commit misses avoid branch-repair flow. |
| `LstCrcInteractionStarterUiTest.testRepositoryComparisonToolbarDialogAllowsChangingComparison` | `C2.3` | Toolbar dialog changes repo target. |
| `LstCrcSettingsStarterUiTest.testTreePresentationAndTitleSettings` | `C4.4`, `C4.6` | Starter title and context-label settings. |
| `LstCrcSettingsStarterUiTest.testGutterSettingsAndIncludeHead` | `C4.7`, `C4.8` | Starter include-`HEAD` and gutter settings. |
| `LstCrcSettingsStarterUiTest.testAdditionalClickSettings` | `C4.1`, `C4.2`, `C4.3` | Starter click settings and delay behavior. |
| `LstCrcSettingsStarterUiTest.testRenderedTreeContextLabelsRespectSingleRepoAndCommitSettings` | `C3.5`, `C4.6` | Starter single-repo and commit context labels. |
| `LstCrcVisualStarterUiTest.testVisualGutterMarkers` | `C3.7` | Starter modified-range gutter rendering. |
| `LstCrcVisualStarterUiTest.testVisualGutterMarkersForInsertedAndDeletedRanges` | `C3.7` | Starter inserted new-file and deleted-range gutter rendering. |
| `LstCrcMultiRootStarterUiTest.testBranchSelectionUsesPrimaryRepositoryBranchesInMultiRootProject` | `C2.2` | Branch picker sources primary repo branches. |
| `LstCrcMultiRootStarterUiTest.testLinkedWorktreeBranchSwitchRefreshesActiveComparison` | `C5.5` | Linked worktree roots participate independently and refresh on branch switch. |
| `LstCrcMultiRootStarterUiTest.testMultiRootComparisonOverrideAppliesOnlyToSelectedRepository` | `C2.3`, `C3.5`, `C4.6` | Per-repo override plus multi-repo context-label toggle. |
| `LstCrcMultiRootStarterUiTest.testMissingBranchNotificationRepairReconfiguresOnlyBrokenRepository` | `C2.5`, `C5.4` | Multi-root repair flow isolates broken repo. |
| `LstCrcMultiRootStarterUiTest.testTabsAliasesAndRepoOverridesRestoreAfterRestart` | `C1.5`, `C5.2` | Alias and override persistence across restart. |

## Non-Functional Tests Outside The Capability Matrix

| Test | Reason |
| --- | --- |
| `LstCrcStarterPerformanceTest.testToolWindowOpenAndBranchLoadPerformance` | Performance smoke coverage rather than functional capability coverage. |
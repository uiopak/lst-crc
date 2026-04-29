# Test Capability Matrix

## Mapping Rules

- Capability IDs come from [plugin-capabilities.md](plugin-capabilities.md).
- This matrix is now case-oriented: each row calls out one decision path or edge case, even when several rows map back to the same capability ID.
- The reverse map in [test-to-capability-map.md](test-to-capability-map.md) enumerates every active test method.
- Recent audit additions closed two concrete gaps: multi-repo context-label visibility and linked-worktree refresh behavior.

## Detailed Capability Map

### Comparison Identity And Target Selection

| Capability ID | Case / decision path | Primary coverage |
| --- | --- | --- |
| `C1.1` | Permanent `HEAD` tab exists and is the fallback state | `LstCrcBranchComparisonUiTest.testGitBranchComparison`, `LstCrcBranchComparisonStarterUiTest.testGitBranchComparison`, `ToolWindowStateServicePersistenceTest.testNoStateLoadedResetsToHeadSelectionSemantics`, `LstCrcStatusWidgetTest.testGetTextReturnsHeadWhenHeadIsSelectedEvenIfWidgetContextEnabled` |
| `C1.1` | `HEAD` scopes stay empty until explicitly enabled | `LstCrcFileScopeStarterUiTest.testPermanentHeadTabScopesStayEmptyUntilIncludeHeadIsEnabled` |
| `C1.2` | Branch comparison tabs show branch-only and shared differences | `LstCrcBranchComparisonUiTest.testGitBranchComparison`, `LstCrcBranchComparisonStarterUiTest.testGitBranchComparison` |
| `C1.3` | Revision tabs can be created from VCS Log selection | `LstCrcInteractionUiTest.testStatusWidgetAndRevisionActions`, `LstCrcInteractionStarterUiTest.testStatusWidgetAndRevisionActions`, `LstCrcActionVisibilityTest.testCreateTabFromRevisionActionVisibleOnlyForSingleRevisionSelection` |
| `C1.4` | Multiple comparison tabs can coexist and switch independently | `LstCrcBranchComparisonUiTest.testMultipleComparisonTabs`, `LstCrcBranchComparisonStarterUiTest.testMultipleComparisonTabs` |
| `C1.5` | Alias changes update visible identity | `LstCrcInteractionUiTest.testTabRenameUpdatesWidgetContext`, `LstCrcInteractionUiTest.testRenameTabPopupRenamesSelectedTab`, `LstCrcInteractionStarterUiTest.testTabRenameUpdatesWidgetContext` |
| `C1.5` | Rename popup accepts inline alias entry for the selected tab | `LstCrcInteractionUiTest.testRenameTabPopupRenamesSelectedTab` |
| `C1.5` | Tool-window tab context menu exposes rename and applies the alias to the clicked tab | `LstCrcInteractionUiTest.testRenameTabContextMenuRenamesSelectedTab` |
| `C1.5` | Rename-tab action visibility follows closable tab and branch-identity context | `LstCrcActionVisibilityTest.testRenameTabActionVisibleForClosableBranchTabWhenContextIsNestedUnderBaseLabel`, `LstCrcActionVisibilityTest.testRenameTabActionHiddenWithoutRenamableTabContext` |
| `C1.5` | Alias state persists across restart | `LstCrcMultiRootStarterUiTest.testTabsAliasesAndRepoOverridesRestoreAfterRestart` |
| `C2.1` | Branch picker can create a comparison tab from UI entry points | `LstCrcBranchComparisonUiTest.testGitBranchComparison`, `LstCrcBranchComparisonStarterUiTest.testGitBranchComparison`, `LstCrcInteractionUiTest.testStatusWidgetAndRevisionActions`, `LstCrcInteractionStarterUiTest.testStatusWidgetAndRevisionActions` |
| `C2.1` | Add-tab action hides while the branch-selection tab already exists | `LstCrcActionVisibilityTest.testOpenBranchSelectionTabActionHiddenWhenSelectionTabAlreadyExists`, `LstCrcActionVisibilityTest.testOpenBranchSelectionTabActionVisibleWhenSelectionTabIsAbsent` |
| `C2.2` | Multi-root branch picker uses the primary repository branch list | `LstCrcMultiRootStarterUiTest.testBranchSelectionUsesPrimaryRepositoryBranchesInMultiRootProject` |
| `C2.3` | Repo comparison dialog changes one repository target | `LstCrcInteractionStarterUiTest.testRepositoryComparisonToolbarDialogAllowsChangingComparison` |
| `C2.3` | Per-repository overrides affect only the selected root | `LstCrcMultiRootStarterUiTest.testMultiRootComparisonOverrideAppliesOnlyToSelectedRepository` |
| `C2.3` | Per-repository override state persists | `ToolWindowStateServicePersistenceTest.testUpdateTabComparisonMapCopiesOverridesWithoutRefreshWhenDisabled`, `LstCrcMultiRootStarterUiTest.testTabsAliasesAndRepoOverridesRestoreAfterRestart` |
| `C2.3` | Repo-comparison toolbar action visibility follows tab state | `LstCrcActionVisibilityTest.testShowRepoComparisonInfoActionHiddenOnHeadAndVisibleForComparisonTab` |
| `C2.4` | VCS Log revision selection can target one repository inside a tab | `LstCrcInteractionUiTest.testStatusWidgetAndRevisionActions`, `LstCrcInteractionStarterUiTest.testStatusWidgetAndRevisionActions` |
| `C2.4` | Repo-comparison revision action visibility requires one selected commit and an active comparison tab | `LstCrcActionVisibilityTest.testSetRevisionAsRepoComparisonActionVisibleOnlyForSingleCommitSelectionWithActiveTab` |
| `C2.5` | Missing branch in a single repository falls back to `HEAD` and warns | `LstCrcInteractionStarterUiTest.testMissingBranchComparisonTargetRecoversToHeadAndShowsWarning` |
| `C2.5` | Missing branch repair in multi-root reconfigures only the broken repository | `LstCrcMultiRootStarterUiTest.testMissingBranchNotificationRepairReconfiguresOnlyBrokenRepository` |
| `C2.6` | Missing commit hashes do not follow branch-repair flow | `LstCrcInteractionStarterUiTest.testMissingCommitComparisonTargetDoesNotRecoverToHeadOrWarn` |

### File-State Classification, Scopes, And Search

| Capability ID | Case / decision path | Primary coverage |
| --- | --- | --- |
| `C3.1` | New/local-only files appear as created entries | `LstCrcBranchComparisonUiTest.testLocalNewFileAppearsInComparisonTab`, `LstCrcFileScopeStarterUiTest.testFileOperations` |
| `C3.1` | Modified files appear as modified entries | `LstCrcBranchComparisonUiTest.testGitBranchComparison`, `LstCrcBranchComparisonUiTest.testBranchComparisonUpdatesModifiedScope`, `LstCrcFileScopeUiTest.testFileOperations` |
| `C3.1` | Renamed or moved files appear as moved entries | `LstCrcFileScopeUiTest.testFileOperations`, `LstCrcFileScopeStarterUiTest.testFileOperations` |
| `C3.1` | Deleted files appear as deleted entries | `LstCrcFileScopeUiTest.testFileOperations`, `LstCrcFileScopeStarterUiTest.testDeletedFilesUseDeletedScopeTreeColor` |
| `C3.1` | Mixed-state comparisons can surface several file states at once | `LstCrcFileScopeUiTest.testFileOperations`, `LstCrcFileScopeStarterUiTest.testFileOperations` |
| `C3.2` | `Created` named scope membership | `LstCrcFileScopeUiTest.testFileOperations`, `LstCrcFileScopeStarterUiTest.testFileOperations` |
| `C3.2` | `Modified` named scope membership | `LstCrcBranchComparisonUiTest.testBranchComparisonUpdatesModifiedScope`, `LstCrcFileScopeUiTest.testFileOperations` |
| `C3.2` | `Moved` named scope membership | `LstCrcFileScopeUiTest.testFileOperations`, `LstCrcFileScopeStarterUiTest.testFileOperations` |
| `C3.2` | `Deleted` named scope membership | `LstCrcFileScopeUiTest.testFileOperations`, `LstCrcFileStatusScopesTest.testDeletedScopeMatchesDeletedPathsWhileChangedExcludesThem` |
| `C3.2` | `Changed` named scope includes created/modified/moved and excludes deleted | `LstCrcFileStatusScopesTest.testDeletedScopeMatchesDeletedPathsWhileChangedExcludesThem` |
| `C3.3` | Search-scope provider publishes only created/modified/moved/changed | `LstCrcSearchScopeProviderTest.testGetDisplayNameAndSearchScopesReturnExpectedLstCrcWrappers`, `LstCrcFileScopeStarterUiTest.testFindDialogShowsLstCrcSearchScopes` |
| `C3.3` | Search-scope wrappers follow detailed created/modified/moved/changed membership | `LstCrcSearchScopeProviderTest.testSearchScopesReflectDetailedFileStateMembership` |
| `C3.3` | Deleted scope stays out of Find/Search publication | `LstCrcSearchScopeProviderTest.testGetDisplayNameAndSearchScopesReturnExpectedLstCrcWrappers`, `LstCrcSearchScopeProviderTest.testSearchScopesReflectDetailedFileStateMembership`, `LstCrcFileScopeStarterUiTest.testFindDialogShowsLstCrcSearchScopes` |
| `C3.4` | Deleted-file handling uses dedicated deleted logic instead of the generic changed aggregate | `LstCrcFileStatusScopesTest.testDeletedScopeMatchesDeletedPathsWhileChangedExcludesThem`, `LstCrcFileScopeStarterUiTest.testDeletedFilesUseDeletedScopeTreeColor` |
| `C3.5` | Single-repo branch context labels render when enabled | `LstCrcSettingsUiTest.testRenderedTreeContextLabelsRespectSingleRepoAndCommitSettings`, `LstCrcSettingsStarterUiTest.testRenderedTreeContextLabelsRespectSingleRepoAndCommitSettings` |
| `C3.5` | Multi-repo override context labels render and can be hidden independently | `LstCrcMultiRootStarterUiTest.testMultiRootComparisonOverrideAppliesOnlyToSelectedRepository` |
| `C3.5` | Revision/commit context labels render when enabled | `LstCrcSettingsUiTest.testRenderedTreeContextLabelsRespectSingleRepoAndCommitSettings`, `LstCrcSettingsStarterUiTest.testRenderedTreeContextLabelsRespectSingleRepoAndCommitSettings` |
| `C3.6` | Deleted rows use deleted-file color | `LstCrcFileScopeStarterUiTest.testDeletedFilesUseDeletedScopeTreeColor` |
| `C3.6` | Deleted-file color does not leak onto non-deleted rows | `LstCrcFileScopeStarterUiTest.testDeletedFileColorDoesNotLeakToModifiedRows` |
| `C3.7` | Modified gutter markers follow the active comparison | `LstCrcVisualUiTest.testVisualGutterMarkers`, `LstCrcVisualStarterUiTest.testVisualGutterMarkers` |
| `C3.7` | Deleted gutter markers follow the active comparison | `LstCrcVisualUiTest.testVisualGutterMarkersForModifiedAndDeletedRanges` |
| `C3.8` | Unsaved edits appear before save | `LstCrcBranchComparisonUiTest.testUnsavedLocalEditAppearsWithoutSave` |
| `C3.8` | Unsaved edits of new files preserve `NEW`/`ADDED` semantics | `LstCrcBranchComparisonUiTest.testNewFileStaysCreatedDuringUnsavedEdits`, `GitServiceOverlayMergeTest.testPreservesNewChangeTypeWhenUnsavedOverlayIsApplied` |
| `C3.8` | Unsaved overlays for non-new files remain modifications | `GitServiceOverlayMergeTest.testKeepsModificationOverlayForNonNewFiles` |

### Presentation, Settings, And Interaction

| Capability ID | Case / decision path | Primary coverage |
| --- | --- | --- |
| `C4.1` | Configured click actions trigger the expected behaviors | `LstCrcInteractionUiTest.testToolWindowClickActions`, `LstCrcInteractionStarterUiTest.testToolWindowClickActions`, `LstCrcSettingsUiTest.testAdditionalClickSettings`, `LstCrcSettingsStarterUiTest.testAdditionalClickSettings` |
| `C4.2` | Right-click can switch from configured actions to context-menu mode | `LstCrcInteractionUiTest.testContextMenuActionsWhenEnabled`, `LstCrcInteractionStarterUiTest.testContextMenuActionsWhenEnabled`, `LstCrcSettingsUiTest.testAdditionalClickSettings`, `LstCrcSettingsStarterUiTest.testAdditionalClickSettings` |
| `C4.3` | Double-click delay defers single-click execution | `LstCrcSettingsUiTest.testAdditionalClickSettings`, `LstCrcSettingsStarterUiTest.testAdditionalClickSettings` |
| `C4.4` | Tool-window title visibility toggles correctly | `LstCrcSettingsUiTest.testTreePresentationAndTitleSettings`, `LstCrcSettingsStarterUiTest.testTreePresentationAndTitleSettings` |
| `C4.5` | Widget context prefix follows the visibility setting | `LstCrcInteractionUiTest.testTabRenameUpdatesWidgetContext`, `LstCrcInteractionStarterUiTest.testTabRenameUpdatesWidgetContext`, `LstCrcStatusWidgetTest.testGetTextUsesAliasPrefixAndTruncationForSelectedTab` |
| `C4.6` | Single-repo context-label setting path | `LstCrcSettingsUiTest.testTreePresentationAndTitleSettings`, `LstCrcSettingsStarterUiTest.testTreePresentationAndTitleSettings` |
| `C4.6` | Multi-repo context-label setting path | `LstCrcMultiRootStarterUiTest.testMultiRootComparisonOverrideAppliesOnlyToSelectedRepository` |
| `C4.6` | Commit/revision context-label setting path | `LstCrcSettingsUiTest.testTreePresentationAndTitleSettings`, `LstCrcSettingsStarterUiTest.testTreePresentationAndTitleSettings` |
| `C4.7` | `HEAD` named/search scopes stay empty until enabled | `LstCrcFileScopeStarterUiTest.testPermanentHeadTabScopesStayEmptyUntilIncludeHeadIsEnabled` |
| `C4.7` | `Include HEAD in scopes` does not affect non-`HEAD` comparison tabs | `LstCrcFileScopeStarterUiTest.testIncludeHeadInScopesDoesNotAffectBranchTabScopes` |
| `C4.8` | Main gutter toggle path | `LstCrcSettingsUiTest.testGutterSettingsAndIncludeHead`, `LstCrcSettingsStarterUiTest.testGutterSettingsAndIncludeHead` |
| `C4.8` | New-file gutter setting path | `LstCrcSettingsUiTest.testGutterSettingsAndIncludeHead`, `LstCrcSettingsStarterUiTest.testGutterSettingsAndIncludeHead` |

### Lifecycle, Persistence, And Recovery

| Capability ID | Case / decision path | Primary coverage |
| --- | --- | --- |
| `C5.1` | Refresh responds to local edits and tab/view changes | `LstCrcBranchComparisonUiTest.testGitBranchComparison`, `LstCrcBranchComparisonUiTest.testUnsavedLocalEditAppearsWithoutSave`, `LstCrcVisualUiTest.testVisualGutterMarkers` |
| `C5.1` | Refresh responds during branch-repair flow | `LstCrcInteractionStarterUiTest.testMissingBranchComparisonTargetRecoversToHeadAndShowsWarning` |
| `C5.1` | Active-diff updates apply for `HEAD` only when `HEAD` semantics are selected | `ProjectActiveDiffDataServiceTest.testAcceptsHeadUpdateWhenHeadTabIsSelected` |
| `C5.1` | Active-diff updates reject `HEAD` events while a comparison tab is selected | `ProjectActiveDiffDataServiceTest.testRejectsHeadUpdateWhileComparisonTabIsSelected` |
| `C5.1` | Active-diff updates reject stale branch results after tab selection changes | `ProjectActiveDiffDataServiceTest.testRejectsStaleUpdateWhenSelectedBranchDoesNotMatch` |
| `C5.2` | Persisted tab state is defensively copied | `ToolWindowStateServicePersistenceTest.testLoadStateAndGetStateDefensivelyCopyNestedTabState` |
| `C5.2` | Alias and repo-override state persists across restart | `LstCrcMultiRootStarterUiTest.testTabsAliasesAndRepoOverridesRestoreAfterRestart`, `ToolWindowStateServicePersistenceTest.testUpdateTabComparisonMapCopiesOverridesWithoutRefreshWhenDisabled` |
| `C5.2` | Adding tabs deduplicates branch identities and removing one tab preserves others | `ToolWindowStateServicePersistenceTest.testAddTabDeduplicatesAndRemoveTabKeepsOtherTabs` |
| `C5.2` | Alias updates target only the matching tab and ignore missing or unchanged updates | `ToolWindowStateServicePersistenceTest.testUpdateTabAliasUpdatesMatchingTabAndLeavesOtherTabsUntouched`, `ToolWindowStateServicePersistenceTest.testUpdateTabAliasIgnoresMissingTabAndUnchangedAlias` |
| `C5.2` | Comparison-map updates ignore missing tabs and unchanged state | `ToolWindowStateServicePersistenceTest.testUpdateTabComparisonMapIgnoresMissingTabAndUnchangedMap`, `ToolWindowStateServicePersistenceTest.testUpdateTabComparisonMapCopiesOverridesWithoutRefreshWhenDisabled` |
| `C5.3` | No-state load resets to `HEAD` semantics safely | `ToolWindowStateServicePersistenceTest.testNoStateLoadedResetsToHeadSelectionSemantics`, `LstCrcStatusWidgetTest.testGetTextFallsBackToPluginNameForInvalidSelectedTabIndex` |
| `C5.4` | Recoverable branch failures trigger notification and repair | `LstCrcInteractionStarterUiTest.testMissingBranchComparisonTargetRecoversToHeadAndShowsWarning`, `LstCrcMultiRootStarterUiTest.testMissingBranchNotificationRepairReconfiguresOnlyBrokenRepository` |
| `C5.4` | Commit misses skip the repair notification path | `LstCrcInteractionStarterUiTest.testMissingCommitComparisonTargetDoesNotRecoverToHeadOrWarn` |
| `C5.5` | Linked worktrees register as distinct comparison roots | `LstCrcMultiRootStarterUiTest.testLinkedWorktreeBranchSwitchRefreshesActiveComparison` |
| `C5.5` | Switching a linked worktree branch refreshes only that root's diff contribution | `LstCrcMultiRootStarterUiTest.testLinkedWorktreeBranchSwitchRefreshesActiveComparison` |

## Audit Notes

- The matrix now treats file-state decisions, scope publication, and search-scope publication as separate cases rather than one broad “scope support” bucket.
- Linked worktree behavior now has dedicated starter coverage instead of being assumed to behave like ordinary multi-root repositories.
- New-file gutter behavior currently has settings-toggle coverage, but end-to-end `INSERTED` rendering is not claimed until the product behavior is validated.
- Alias behavior now includes the actual tool-window tab context-menu rename path in addition to direct popup and state-level coverage.
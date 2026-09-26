# Test Capability Matrix

## Mapping Rules

- Capability IDs come from [plugin-capabilities.md](plugin-capabilities.md).
- This matrix is now case-oriented: each row calls out one decision path or edge case, even when several rows map back to the same capability ID.
- The reverse map in [test-to-capability-map.md](test-to-capability-map.md) lists every test method; this matrix lists the main tests for each case.
- Tests in `src/test/kotlin/.../plugin` are Remote Robot UI tests (`ui` tag, Linux/Windows/macOS). Tests in `src/uiTest` are IDE Starter UI tests (Linux). The rest are unit and light platform tests run by `./gradlew test`.

## Detailed Capability Map

### Comparison Identity And Target Selection

| Capability ID | Case / decision path | Primary coverage |
| --- | --- | --- |
| `C1.1` | Permanent `HEAD` tab exists and is the fallback state | `LstCrcBranchComparisonUiTest.testGitBranchComparison`, `LstCrcBranchComparisonStarterUiTest.testGitBranchComparison`, `ToolWindowStateServicePersistenceTest.testNoStateLoadedResetsToHeadSelectionSemantics`, `LstCrcStatusWidgetTest.testGetTextReturnsHeadWhenHeadIsSelectedEvenIfWidgetContextEnabled` |
| `C1.1` | `HEAD` scopes stay empty until explicitly enabled | `LstCrcFileScopeStarterUiTest.testPermanentHeadTabScopesStayEmptyUntilIncludeHeadIsEnabled` |
| `C1.2` | Branch comparison tabs show branch-only and shared differences | `LstCrcBranchComparisonUiTest.testGitBranchComparison`, `LstCrcBranchComparisonStarterUiTest.testGitBranchComparison` |
| `C1.2` | Comparisons of a real repository (google/gson at pinned release commits) match what git reports | `LstCrcRealRepositoryUiTest.testBranchComparisonsMatchGit`, `LstCrcRealRepositoryStarterUiTest.testBranchComparisonsMatchGit` |
| `C1.3` | Revision tabs can be created from VCS Log selection | `LstCrcInteractionUiTest.testStatusWidgetAndRevisionActions`, `LstCrcInteractionStarterUiTest.testStatusWidgetAndRevisionActions`, `LstCrcActionVisibilityTest.testCreateTabFromRevisionActionVisibleOnlyForSingleRevisionSelection` |
| `C1.4` | Multiple comparison tabs can coexist and switch independently | `LstCrcBranchComparisonUiTest.testMultipleComparisonTabs`, `LstCrcBranchComparisonStarterUiTest.testMultipleComparisonTabs` |
| `C1.5` | Alias changes update visible identity | `LstCrcInteractionUiTest.testTabRenameUpdatesWidgetContext`, `LstCrcInteractionUiTest.testRenameTabPopupRenamesSelectedTab`, `LstCrcInteractionStarterUiTest.testTabRenameUpdatesWidgetContext` |
| `C1.5` | Rename popup accepts inline alias entry for the selected tab | `LstCrcInteractionUiTest.testRenameTabPopupRenamesSelectedTab` |
| `C1.5` | Tool-window tab context menu exposes rename and applies the alias to the clicked tab | `LstCrcInteractionUiTest.testRenameTabContextMenuRenamesSelectedTab` |
| `C1.5` | Rename-tab action visibility follows closable tab and branch-identity context | `LstCrcActionVisibilityTest.testRenameTabActionVisibleForClosableBranchTabWhenContextIsNestedUnderBaseLabel`, `LstCrcActionVisibilityTest.testRenameTabActionHiddenWithoutRenamableTabContext` |
| `C1.5` | Alias state persists across restart | `LstCrcMultiRootStarterUiTest.testTabsAliasesAndRepoOverridesRestoreAfterRestart` |
| `C2.1` | Branch picker can create a comparison tab from UI entry points | `LstCrcBranchComparisonUiTest.testGitBranchComparison`, `LstCrcBranchComparisonStarterUiTest.testGitBranchComparison`, `LstCrcInteractionUiTest.testStatusWidgetAndRevisionActions`, `LstCrcInteractionStarterUiTest.testStatusWidgetAndRevisionActions` |
| `C2.1` | Branch filter keeps matching branches, selects the first match, and Enter submits it | `BranchSelectionPanelTest.testFilterSelectsFirstMatchingBranchFromStableSnapshot`, `BranchSelectionPanelTest.testEnterSubmitsSelectedBranch` |
| `C2.1` | Branch filter does not persist when the picker is reopened | `LstCrcInteractionUiTest.testBranchSelectionFilterDoesNotPersistAcrossReopen`, `BranchSelectionPanelTest.testNewPanelReopensWithFullBranchSnapshotAfterPreviousFilter` |
| `C2.1` | Add-tab action hides while the branch-selection tab already exists | `LstCrcActionVisibilityTest.testOpenBranchSelectionTabActionHiddenWhenSelectionTabAlreadyExists`, `LstCrcActionVisibilityTest.testOpenBranchSelectionTabActionVisibleWhenSelectionTabIsAbsent` |
| `C2.2` | Multi-root branch picker uses the primary repository branch list | `LstCrcMultiRootStarterUiTest.testBranchSelectionUsesPrimaryRepositoryBranchesInMultiRootProject` |
| `C2.3` | Repo comparison dialog changes one repository target | `LstCrcInteractionStarterUiTest.testRepositoryComparisonToolbarDialogAllowsChangingComparison` |
| `C2.3` | Per-repository overrides affect only the selected root | `LstCrcMultiRootStarterUiTest.testMultiRootComparisonOverrideAppliesOnlyToSelectedRepository` |
| `C2.3` | Per-repository override state persists | `ToolWindowStateServicePersistenceTest.testUpdateTabComparisonMapCopiesOverridesWithoutRefreshWhenDisabled`, `LstCrcMultiRootStarterUiTest.testTabsAliasesAndRepoOverridesRestoreAfterRestart` |
| `C2.3` | A per-repository override wins over the tab target; choosing the tab's own target removes the override | `GitServiceComparisonTargetTest.testResolveComparisonTargetPrecedence`, `ToolWindowStateServicePersistenceTest.testUpdateTabRepoComparisonRemovesOverrideWhenTargetMatchesDefault` |
| `C2.3` | Repo-comparison toolbar action sits right after group-by | `LstCrcChangesBrowserTest.testToolbarActionsIncludeRepoComparisonActionImmediatelyAfterGroupByWhenPresent` |
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
| `C3.1` | Rows carry `ADDED`, `DELETED` and `MODIFIED` file statuses relative to the working tree | `LstCrcBranchComparisonUiTest.testFileTypeFileStatuses`, `LstCrcBranchComparisonStarterUiTest.testFileTypeFileStatuses` |
| `C3.1` | Mixed-state comparisons can surface several file states at once | `LstCrcFileScopeUiTest.testFileOperations`, `LstCrcFileScopeStarterUiTest.testFileOperations` |
| `C3.2` | `Created` named scope membership | `LstCrcFileScopeUiTest.testFileOperations`, `LstCrcFileScopeStarterUiTest.testFileOperations` |
| `C3.2` | `Modified` named scope membership | `LstCrcBranchComparisonUiTest.testBranchComparisonUpdatesModifiedScope`, `LstCrcFileScopeUiTest.testFileOperations` |
| `C3.2` | `Moved` named scope membership | `LstCrcFileScopeUiTest.testFileOperations`, `LstCrcFileScopeStarterUiTest.testFileOperations` |
| `C3.2` | `Deleted` named scope membership | `LstCrcFileScopeUiTest.testFileOperations`, `LstCrcFileStatusScopesTest.testDeletedScopeMatchesDeletedPathsWhileChangedExcludesThem` |
| `C3.2` | `Changed` named scope includes created/modified/moved and excludes deleted | `LstCrcFileStatusScopesTest.testDeletedScopeMatchesDeletedPathsWhileChangedExcludesThem` |
| `C3.3` | Scope ids and order are stable and only non-deleted scopes are searchable | `LstCrcSearchScopeProviderTest.testProvidedScopesExposeCanonicalIdsOrderAndSearchableSubset` |
| `C3.3` | Search-scope provider publishes only created/modified/moved/changed | `LstCrcSearchScopeProviderTest.testGetDisplayNameAndSearchScopesReturnExpectedLstCrcScopes`, `LstCrcFileScopeStarterUiTest.testFindDialogShowsLstCrcSearchScopes` |
| `C3.3` | Search-scope wrappers follow detailed created/modified/moved/changed membership | `LstCrcSearchScopeProviderTest.testSearchScopesReflectDetailedFileStateMembership` |
| `C3.3` | Deleted scope stays out of Find/Search publication | `LstCrcSearchScopeProviderTest.testGetDisplayNameAndSearchScopesReturnExpectedLstCrcScopes`, `LstCrcSearchScopeProviderTest.testSearchScopesReflectDetailedFileStateMembership`, `LstCrcFileScopeStarterUiTest.testFindDialogShowsLstCrcSearchScopes` |
| `C3.4` | Deleted-file handling uses dedicated deleted logic instead of the generic changed aggregate | `LstCrcFileStatusScopesTest.testDeletedScopeMatchesDeletedPathsWhileChangedExcludesThem`, `LstCrcFileScopeStarterUiTest.testDeletedFilesUseDeletedScopeTreeColor` |
| `C3.5` | Single-repo branch context labels render when enabled | `LstCrcSettingsUiTest.testRenderedTreeContextLabelsRespectSingleRepoAndCommitSettings`, `LstCrcSettingsStarterUiTest.testRenderedTreeContextLabelsRespectSingleRepoAndCommitSettings` |
| `C3.5` | Multi-repo override context labels render and can be hidden independently | `LstCrcMultiRootStarterUiTest.testMultiRootComparisonOverrideAppliesOnlyToSelectedRepository` |
| `C3.5` | Revision/commit context labels render when enabled | `LstCrcSettingsUiTest.testRenderedTreeContextLabelsRespectSingleRepoAndCommitSettings`, `LstCrcSettingsStarterUiTest.testRenderedTreeContextLabelsRespectSingleRepoAndCommitSettings` |
| `C3.6` | Deleted rows use deleted-file color | `LstCrcFileScopeStarterUiTest.testDeletedFilesUseDeletedScopeTreeColor` |
| `C3.6` | Deleted-file color does not leak onto non-deleted rows | `LstCrcFileScopeStarterUiTest.testDeletedFileColorDoesNotLeakToModifiedRows` |
| `C3.7` | Inserted new-file gutter markers follow the active comparison | `LstCrcVisualUiTest.testVisualGutterMarkersForInsertedNewFile`, `LstCrcVisualStarterUiTest.testVisualGutterMarkersForInsertedAndDeletedRanges` |
| `C3.7` | Modified gutter markers follow the active comparison | `LstCrcVisualUiTest.testVisualGutterMarkers`, `LstCrcVisualStarterUiTest.testVisualGutterMarkers` |
| `C3.7` | Line-status trackers report inserted ranges for partial insertions and whole new files | `VisualTrackerManagerBehaviorTest.testUnderlyingTrackerReportsInsertedRangeForPartialInsertionAgainstExistingBase`, `VisualTrackerManagerBehaviorTest.testUnderlyingTrackerReportsInitialInsertedRangeForWholeNewFileAgainstEmptyBase` |
| `C3.7` | The standalone tracker for a new file draws gutter markers, and visual trackers are released with the native tracker | `VisualTrackerManagerBehaviorTest.testStandaloneTrackerInstallsGutterHighlightersForWholeNewFile`, `VisualTrackerManagerBehaviorTest.testVisualTrackerManagerCleanupOnTrackerRemoved` |
| `C3.7` | Deleted gutter markers follow the active comparison | `LstCrcVisualUiTest.testVisualGutterMarkersForModifiedAndDeletedRanges`, `LstCrcVisualStarterUiTest.testVisualGutterMarkersForInsertedAndDeletedRanges` |
| `C3.8` | Unsaved edits appear before save | `LstCrcBranchComparisonUiTest.testUnsavedLocalEditAppearsWithoutSave` |
| `C3.8` | Unsaved edits of new files preserve `NEW`/`ADDED` semantics | `LstCrcBranchComparisonUiTest.testNewFileStaysCreatedDuringUnsavedEdits`, `GitServiceOverlayMergeTest.testPreservesNewChangeTypeWhenUnsavedOverlayIsApplied` |
| `C3.8` | Unsaved overlays for non-new files remain modifications | `GitServiceOverlayMergeTest.testKeepsModificationOverlayForNonNewFiles` |
| `C3.8` | The unsaved-overlay revision reads the editor's current text, also off the EDT | `GitServiceLineStatsTest.testCreateLiveDocumentContentRevisionReadsLatestUnsavedDocumentText`, `GitServiceLineStatsTest.testCreateLiveDocumentContentRevisionAllowsBackgroundThreadAccess` |
| `C3.8` | New unsaved content on unchanged paths reaches the tree; identical content does not republish | `GitServiceLineStatsTest.testLiveDocumentContentRevisionsAreEqualOnlyForTheSameText`, `ProjectActiveDiffDataServiceTest.testSamePathsWithNewUnsavedContentPublishesNewChanges` |
| `C3.8` | Target content for the overlay is cached only under a resolved commit hash, so a moved branch or `HEAD` never serves stale content | `GitServiceComparisonTargetTest.testResolveCommitHashUsesRepositoryStateAndRejectsAmbiguousRevisions` |
| `C3.9` | Tree expansion/collapse state persists when switching between comparison tabs | `LstCrcBranchComparisonStarterUiTest.testTreeStatePersistsAcrossTabSwitches`, `LstCrcBranchComparisonUiTest.testTreeStatePersistsAcrossTabSwitches` |
| `C3.9` | A collapsed folder opens for a new change only when "Expand collapsed folders for new changes" is on | `LstCrcBranchComparisonUiTest.testNewFileInCollapsedDirExpandsDirWhenSettingEnabled`, `LstCrcBranchComparisonUiTest.testNewFileInCollapsedDirStaysCollapsedWhenSettingDisabled`, `LstCrcBranchComparisonStarterUiTest.testNewFileInCollapsedDirExpandsDirWhenSettingEnabled`, `LstCrcBranchComparisonStarterUiTest.testNewFileInCollapsedDirKeepsDirCollapsedWhenSettingDisabled` |
| `C3.10` | Line counts ignore CRLF/LF-only churn, in-process and in `git diff --numstat` | `GitServiceLineStatsTest.testCalculateLineStatsIgnoresLineEndingOnlyDifferences`, `GitServiceLineStatsTest.testTrackedLineStatsDiffArgsIgnoreLineEndingOnlyChurn`, `LstCrcBranchComparisonUiTest.testBranchComparisonLineStatsIgnoreLineEndingOnlyChanges`, `LstCrcBranchComparisonStarterUiTest.testBranchComparisonLineStatsIgnoreLineEndingOnlyChanges` |
| `C3.10` | Line counts for new, deleted, replaced and mixed-ending content | `GitServiceLineStatsTest.testCalculateLineStatsForNewFileContent`, `GitServiceLineStatsTest.testCalculateLineStatsForDeletedFileContent`, `GitServiceLineStatsTest.testCalculateLineStatsForSingleLineReplacement`, `GitServiceLineStatsTest.testCalculateLineStatsCountsRealChangesWhenLineEndingsAlsoDiffer` |
| `C3.10` | Row text, colors, the setting, and folder sums | `RepoNodeRendererTest` (all 7 tests) |
| `C3.10` | Unsaved edits update line counts immediately | `LstCrcBranchComparisonUiTest.testUnsavedSingleCharacterEditsUpdateLineStatsImmediatelyAcrossMultipleLines` |
| `C3.10` | Line counts on a real repository match git | `LstCrcRealRepositoryUiTest.testBranchComparisonsMatchGit`, `LstCrcRealRepositoryStarterUiTest.testBranchComparisonsMatchGit` |
| `C3.11` | Untracked files appear only while "Show untracked files as new" is on | `LstCrcBranchComparisonUiTest.testUntrackedFileAppearsWhenSettingEnabled`, `LstCrcBranchComparisonUiTest.testUntrackedFileStaysHiddenWhenSettingDisabled`, `LstCrcBranchComparisonStarterUiTest.testUntrackedFileAppearsWhenSettingEnabled`, `LstCrcBranchComparisonStarterUiTest.testUntrackedFileStaysHiddenWhenSettingDisabled` |
| `C3.11` | Untracked files have the `UNKNOWN` status, unlike tracked additions | `LstCrcBranchComparisonUiTest.testUntrackedFileHasUnknownFileStatus`, `LstCrcBranchComparisonStarterUiTest.testUntrackedFileHasUnknownFileStatus` |
| `C3.12` | A refresh keeps the scroll position, including with an offscreen or bottom selection | `LstCrcChangesBrowserTest` (5 refresh tests), `LstCrcBranchComparisonUiTest.testRefreshKeepsTreeViewportWhenSelectionIsOffscreen`, `LstCrcBranchComparisonUiTest.testRefreshDoesNotMoveViewportWhenSelectionIsAtBottomAndViewportIsAtTop`, `LstCrcBranchComparisonUiTest.testRefreshDoesNotChangeTopVisibleEntryWhenSelectionIsAtBottomAndViewportIsAtTop` |
| `C3.12` | The same holds on the `HEAD` tab and for refreshes caused by unsaved typing | `LstCrcBranchComparisonUiTest.testHeadRefreshDoesNotMoveViewportWhenSelectionIsAtBottomAndViewportIsAtTop`, `LstCrcBranchComparisonUiTest.testUnsavedRefreshDoesNotMoveViewportWhenSelectionIsAtBottomAndViewportIsAtTop`, `LstCrcBranchComparisonUiTest.testClickedScrolledUnsavedRefreshWithLineStatsDoesNotMoveVisibleTreeState`, `LstCrcBranchComparisonUiTest.testEditingSelectedUntrackedFileShownAsNewDoesNotScrollTreeToIt` |

### Presentation, Settings, And Interaction

| Capability ID | Case / decision path | Primary coverage |
| --- | --- | --- |
| `C4.1` | Configured click actions trigger the expected behaviors | `LstCrcInteractionUiTest.testToolWindowClickActions`, `LstCrcInteractionStarterUiTest.testToolWindowClickActions`, `LstCrcSettingsUiTest.testAdditionalClickSettings`, `LstCrcSettingsStarterUiTest.testAdditionalClickSettings` |
| `C4.1` | Each mouse button uses its own settings; other buttons do nothing | `LstCrcChangesBrowserTest.testConfiguredClickActionLookupUsesButtonSpecificSettings`, `LstCrcChangesBrowserTest.testConfiguredClickActionLookupFallsBackToNoneForUnsupportedButtons` |
| `C4.1` | Open Source wins over a focused diff, and Show Diff reuses an open diff tab | `LstCrcInteractionUiTest.testContextMenuOpenSourceWinsOverFocusedDiffAndReusesDiff`, `LstCrcInteractionStarterUiTest.testContextMenuOpenSourceWinsOverFocusedDiffAndReusesDiff` |
| `C4.2` | Right-click can switch from configured actions to context-menu mode | `LstCrcInteractionUiTest.testContextMenuActionsWhenEnabled`, `LstCrcInteractionStarterUiTest.testContextMenuActionsWhenEnabled`, `LstCrcSettingsUiTest.testAdditionalClickSettings`, `LstCrcSettingsStarterUiTest.testAdditionalClickSettings` |
| `C4.2` | The context menu offers "Show in Project" only for files that exist | `LstCrcChangesBrowserTest.testAvailableContextMenuActionsIncludeProjectTreeForNonDeletedChange`, `LstCrcChangesBrowserTest.testAvailableContextMenuActionsOmitProjectTreeForDeletedChange` |
| `C4.3` | Double-click delay defers single-click execution | `LstCrcSettingsUiTest.testAdditionalClickSettings`, `LstCrcSettingsStarterUiTest.testAdditionalClickSettings` |
| `C4.4` | Tool-window title visibility toggles correctly | `LstCrcSettingsUiTest.testTreePresentationAndTitleSettings`, `LstCrcSettingsStarterUiTest.testTreePresentationAndTitleSettings` |
| `C4.5` | Widget context prefix follows the visibility setting | `LstCrcInteractionUiTest.testTabRenameUpdatesWidgetContext`, `LstCrcInteractionStarterUiTest.testTabRenameUpdatesWidgetContext`, `LstCrcStatusWidgetTest.testGetTextUsesAliasPrefixAndTruncationForSelectedTab` |
| `C4.5` | The widget id in `plugin.xml` matches the code | `LstCrcStatusWidgetTest.testPluginXmlStatusWidgetFactoryIdMatchesWidgetConstant` |
| `C4.6` | Single-repo context-label setting path | `LstCrcSettingsUiTest.testTreePresentationAndTitleSettings`, `LstCrcSettingsStarterUiTest.testTreePresentationAndTitleSettings` |
| `C4.6` | Multi-repo context-label setting path | `LstCrcMultiRootStarterUiTest.testMultiRootComparisonOverrideAppliesOnlyToSelectedRepository` |
| `C4.6` | Commit/revision context-label setting path | `LstCrcSettingsUiTest.testTreePresentationAndTitleSettings`, `LstCrcSettingsStarterUiTest.testTreePresentationAndTitleSettings` |
| `C4.7` | `HEAD` named/search scopes stay empty until enabled | `LstCrcFileScopeStarterUiTest.testPermanentHeadTabScopesStayEmptyUntilIncludeHeadIsEnabled`, `LstCrcFileStatusScopesTest.testScopesExcludeHeadChangesWhenIncludeHeadInScopesIsDisabled` |
| `C4.7` | `Include HEAD in scopes` does not affect non-`HEAD` comparison tabs | `LstCrcFileScopeStarterUiTest.testIncludeHeadInScopesDoesNotAffectBranchTabScopes` |
| `C4.8` | Main gutter toggle path | `LstCrcSettingsUiTest.testGutterSettingsAndIncludeHead`, `LstCrcSettingsStarterUiTest.testGutterSettingsAndIncludeHead` |
| `C4.8` | New-file gutter setting path | `LstCrcSettingsUiTest.testGutterSettingsAndIncludeHead`, `LstCrcSettingsStarterUiTest.testGutterSettingsAndIncludeHead`, `LstCrcVisualUiTest.testVisualGutterMarkersForInsertedNewFile` |
| `C4.9` | Settings round-trip, reset to defaults, and import from earlier versions | `LstCrcSettingsServiceTest.testSettersAndGettersRoundTripValues`, `LstCrcSettingsServiceTest.testResetToDefaultsRestoresRepresentativeValues`, `LstCrcSettingsServiceTest.testImportsLegacyPropertiesComponentValues` |

### Lifecycle, Persistence, And Recovery

| Capability ID | Case / decision path | Primary coverage |
| --- | --- | --- |
| `C5.1` | Refresh responds to local edits and tab/view changes | `LstCrcBranchComparisonUiTest.testGitBranchComparison`, `LstCrcBranchComparisonUiTest.testUnsavedLocalEditAppearsWithoutSave`, `LstCrcVisualUiTest.testVisualGutterMarkers` |
| `C5.1` | Edits to repository files trigger one debounced refresh without blocking; other files are ignored | `VcsChangeListenerTest.testHandleDocumentChangeTriggersRefreshForRepositoryFiles`, `VcsChangeListenerTest.testHandleDocumentChangeIgnoresNonRepositoryFiles`, `VcsChangeListenerTest.testHandleDocumentChangeDoesNotBlockOnRepositoryCheck` |
| `C5.1` | Edits alone reuse the last git result; a VCS event in the same burst forces a full reload | `VcsChangeListenerTest.testDocumentEditsAloneRequestEditOnlyRefreshWhileVcsEventsRequestFullRefresh` |
| `C5.1` | A checkout and local modify/add/delete/rename on a real repository update the comparison to match git | `LstCrcRealRepositoryUiTest.testCheckoutAndLocalEditsUpdateComparison`, `LstCrcRealRepositoryStarterUiTest.testCheckoutAndLocalEditsUpdateComparison` |
| `C5.1` | Refresh responds during branch-repair flow | `LstCrcInteractionStarterUiTest.testMissingBranchComparisonTargetRecoversToHeadAndShowsWarning` |
| `C5.1` | Active-diff updates apply for `HEAD` only when `HEAD` semantics are selected | `ProjectActiveDiffDataServiceTest.testAcceptsHeadUpdateWhenHeadTabIsSelected` |
| `C5.1` | Active-diff updates reject `HEAD` events while a comparison tab is selected | `ProjectActiveDiffDataServiceTest.testRejectsHeadUpdateWhileComparisonTabIsSelected` |
| `C5.1` | Active-diff updates reject stale branch results after tab selection changes | `ProjectActiveDiffDataServiceTest.testRejectsStaleUpdateWhenSelectedBranchDoesNotMatch` |
| `C5.1` | Identical data does not re-notify listeners | `ProjectActiveDiffDataServiceTest.testUpdateActiveDiffWithIdenticalSnapshotBypassesNotification` |
| `C5.2` | Persisted tab state is defensively copied | `ToolWindowStateServicePersistenceTest.testLoadStateAndGetStateDefensivelyCopyNestedTabState` |
| `C5.2` | Alias and repo-override state persists across restart | `LstCrcMultiRootStarterUiTest.testTabsAliasesAndRepoOverridesRestoreAfterRestart`, `ToolWindowStateServicePersistenceTest.testUpdateTabComparisonMapCopiesOverridesWithoutRefreshWhenDisabled` |
| `C5.2` | Adding tabs deduplicates branch identities and removing one tab preserves others | `ToolWindowStateServicePersistenceTest.testAddTabDeduplicatesAndRemoveTabKeepsOtherTabs` |
| `C5.2` | Removing a tab keeps a sensible selection | `ToolWindowStateServicePersistenceTest.testRemoveTabClampsSelectedIndexWhenSelectedTabIsRemoved`, `ToolWindowStateServicePersistenceTest.testRemoveTabShiftsSelectedIndexWhenEarlierTabIsRemoved` |
| `C5.2` | Alias updates target only the matching tab and ignore missing or unchanged updates | `ToolWindowStateServicePersistenceTest.testUpdateTabAliasUpdatesMatchingTabAndLeavesOtherTabsUntouched`, `ToolWindowStateServicePersistenceTest.testUpdateTabAliasIgnoresMissingTabAndUnchangedAlias` |
| `C5.2` | Comparison-map updates ignore missing tabs and unchanged state | `ToolWindowStateServicePersistenceTest.testUpdateTabComparisonMapIgnoresMissingTabAndUnchangedMap`, `ToolWindowStateServicePersistenceTest.testUpdateTabComparisonMapCopiesOverridesWithoutRefreshWhenDisabled` |
| `C5.3` | No-state load resets to `HEAD` semantics safely | `ToolWindowStateServicePersistenceTest.testNoStateLoadedResetsToHeadSelectionSemantics`, `LstCrcStatusWidgetTest.testGetTextFallsBackToPluginNameForInvalidSelectedTabIndex` |
| `C5.4` | Recoverable branch failures trigger notification and repair | `LstCrcInteractionStarterUiTest.testMissingBranchComparisonTargetRecoversToHeadAndShowsWarning`, `LstCrcMultiRootStarterUiTest.testMissingBranchNotificationRepairReconfiguresOnlyBrokenRepository` |
| `C5.4` | Commit misses skip the repair notification path | `LstCrcInteractionStarterUiTest.testMissingCommitComparisonTargetDoesNotRecoverToHeadOrWarn` |
| `C5.5` | Linked worktrees register as distinct comparison roots | `LstCrcMultiRootStarterUiTest.testLinkedWorktreeBranchSwitchRefreshesActiveComparison` |
| `C5.5` | Switching a linked worktree branch refreshes only that root's diff contribution | `LstCrcMultiRootStarterUiTest.testLinkedWorktreeBranchSwitchRefreshesActiveComparison` |
| `C5.5` | Switching the primary worktree branch keeps the linked worktree's changes | `LstCrcMultiRootStarterUiTest.testPrimaryWorktreeBranchSwitchPreservesLinkedWorktreeDiffContribution` |

## Notes

- File-state decisions, scope publication and search-scope publication are separate cases rather than one broad "scope support" bucket.
- Linked worktrees have their own Starter coverage instead of being assumed to behave like ordinary multi-root repositories.
- New-file gutter markers are covered end to end (`LstCrcVisualUiTest.testVisualGutterMarkersForInsertedNewFile`) and at the tracker level (`VisualTrackerManagerBehaviorTest`). The UI tests read document-level line markers, because a new file's markers come from the plugin's standalone visual tracker rather than the IDE's own tracker.
- The real-repository tests compute every expected result with the git CLI (`GitDiffOracle`) against google/gson at pinned release commits (`GsonFixture`). Their step timings are report-only; the performance smoke test (`LstCrcStarterPerformanceTest`) only fails on hang-level limits.

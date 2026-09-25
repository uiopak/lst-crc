# Test To Capability Map

This file maps every test method to the capability IDs defined in [plugin-capabilities.md](plugin-capabilities.md). The case-by-case view is in [test-capability-matrix.md](test-capability-matrix.md).

When you add, rename or remove a test, update this file. Every `test*` method in `src/test` and `src/uiTest` must appear here exactly once per class.

## Unit And Service Tests

Location: `src/test/kotlin/com/github/uiopak/lstcrc/{gutters,listeners,scopes,services,toolWindow}`.

| Test | Capability IDs | What it checks |
| --- | --- | --- |
| `BranchSelectionPanelTest.testFilterSelectsFirstMatchingBranchFromStableSnapshot` | `C2.1` | Typing a filter keeps matching branches and selects the first match. |
| `BranchSelectionPanelTest.testEnterSubmitsSelectedBranch` | `C2.1` | Enter on the selected branch submits it. |
| `BranchSelectionPanelTest.testNewPanelReopensWithFullBranchSnapshotAfterPreviousFilter` | `C2.1` | A new panel starts unfiltered after an earlier panel was filtered. |
| `GitServiceComparisonTargetTest.testResolveComparisonTargetPrecedence` | `C2.3` | A per-repository override wins over the tab target; the `HEAD` tab compares against `HEAD`. |
| `GitServiceLineStatsTest.testCalculateLineStatsIgnoresLineEndingOnlyDifferences` | `C3.10` | CRLF/LF-only differences count as no change. |
| `GitServiceLineStatsTest.testCalculateLineStatsCountsRealChangesWhenLineEndingsAlsoDiffer` | `C3.10` | Real edits still count when line endings differ too. |
| `GitServiceLineStatsTest.testCalculateLineStatsForSingleLineReplacement` | `C3.10` | A replaced line counts as one added and one removed. |
| `GitServiceLineStatsTest.testCalculateLineStatsForNewFileContent` | `C3.10` | A new file counts all its lines as added. |
| `GitServiceLineStatsTest.testCalculateLineStatsForDeletedFileContent` | `C3.10` | A deleted file counts all its lines as removed. |
| `GitServiceLineStatsTest.testTrackedLineStatsDiffArgsIgnoreLineEndingOnlyChurn` | `C3.10` | `git diff --numstat` is run with `--ignore-cr-at-eol`. |
| `GitServiceLineStatsTest.testCreateLiveDocumentContentRevisionReadsLatestUnsavedDocumentText` | `C3.8` | The unsaved-overlay revision reads the editor's current text. |
| `GitServiceLineStatsTest.testCreateLiveDocumentContentRevisionAllowsBackgroundThreadAccess` | `C3.8` | The unsaved-overlay revision can be read off the EDT. |
| `GitServiceOverlayMergeTest.testPreservesNewChangeTypeWhenUnsavedOverlayIsApplied` | `C3.8` | Unsaved edits to a new file keep it `NEW`/`ADDED`. |
| `GitServiceOverlayMergeTest.testKeepsModificationOverlayForNonNewFiles` | `C3.8` | Unsaved edits to other files stay modifications. |
| `LstCrcActionVisibilityTest.testShowRepoComparisonInfoActionHiddenOnHeadAndVisibleForComparisonTab` | `C2.3` | Repo-comparison toolbar action is hidden on `HEAD` and shown on comparison tabs. |
| `LstCrcActionVisibilityTest.testCreateTabFromRevisionActionVisibleOnlyForSingleRevisionSelection` | `C1.3` | Git Log "create tab" needs exactly one selected revision. |
| `LstCrcActionVisibilityTest.testRenameTabActionVisibleForClosableBranchTabWhenContextIsNestedUnderBaseLabel` | `C1.5` | Rename is offered from inside a closable tab's label. |
| `LstCrcActionVisibilityTest.testRenameTabActionHiddenWithoutRenamableTabContext` | `C1.5` | Rename is hidden for other tool windows, the `HEAD` tab, tabs without a branch key and unrelated components. |
| `LstCrcActionVisibilityTest.testOpenBranchSelectionTabActionHiddenWhenSelectionTabAlreadyExists` | `C2.1` | The add-tab action hides while the branch-selection tab is open. |
| `LstCrcActionVisibilityTest.testOpenBranchSelectionTabActionVisibleWhenSelectionTabIsAbsent` | `C2.1` | The add-tab action shows when no branch-selection tab is open. |
| `LstCrcActionVisibilityTest.testSetRevisionAsRepoComparisonActionVisibleOnlyForSingleCommitSelectionWithActiveTab` | `C2.4` | Git Log repo-comparison action needs one selected commit and an active comparison tab. |
| `LstCrcChangesBrowserTest.testRefreshPreservesTreeViewportPosition` | `C3.12` | A refresh keeps the scroll position. |
| `LstCrcChangesBrowserTest.testRepeatedRefreshPreservesTreeViewportPosition` | `C3.12` | Repeated refreshes keep the scroll position. |
| `LstCrcChangesBrowserTest.testRefreshPreservesTopViewportWhenSelectionIsOffscreen` | `C3.12` | A refresh keeps the view at the top when the selection is offscreen. |
| `LstCrcChangesBrowserTest.testRefreshDoesNotMoveViewportWhileSelectionIsOffscreen` | `C3.12` | A refresh does not scroll to an offscreen selection. |
| `LstCrcChangesBrowserTest.testRefreshDoesNotMoveViewportForSelectedAddedFileWhileOffscreen` | `C3.12` | A refresh does not scroll to an offscreen selected added file. |
| `LstCrcChangesBrowserTest.testAvailableContextMenuActionsIncludeProjectTreeForNonDeletedChange` | `C4.2` | The context menu offers "Show in Project" for existing files. |
| `LstCrcChangesBrowserTest.testAvailableContextMenuActionsOmitProjectTreeForDeletedChange` | `C4.2` | The context menu omits "Show in Project" for deleted files. |
| `LstCrcChangesBrowserTest.testConfiguredClickActionLookupUsesButtonSpecificSettings` | `C4.1` | Each mouse button uses its own single/double click settings. |
| `LstCrcChangesBrowserTest.testConfiguredClickActionLookupFallsBackToNoneForUnsupportedButtons` | `C4.1` | Other mouse buttons do nothing. |
| `LstCrcChangesBrowserTest.testToolbarActionsIncludeRepoComparisonActionImmediatelyAfterGroupByWhenPresent` | `C2.3` | The repo-comparison action sits right after the group-by action in the toolbar. |
| `LstCrcFileStatusScopesTest.testDeletedScopeMatchesDeletedPathsWhileChangedExcludesThem` | `C3.2`, `C3.4` | `Deleted` matches deleted paths; `Changed` excludes them. |
| `LstCrcFileStatusScopesTest.testScopesExcludeHeadChangesWhenIncludeHeadInScopesIsDisabled` | `C4.7` | Scopes ignore `HEAD` data while `Include HEAD in scopes` is off. |
| `LstCrcSearchScopeProviderTest.testProvidedScopesExposeCanonicalIdsOrderAndSearchableSubset` | `C3.2`, `C3.3` | Scope ids and order are stable; `Deleted` is not searchable. |
| `LstCrcSearchScopeProviderTest.testGetDisplayNameAndSearchScopesReturnExpectedLstCrcScopes` | `C3.3` | The Find/Search group lists Created, Modified, Moved and Changed. |
| `LstCrcSearchScopeProviderTest.testSearchScopesReflectDetailedFileStateMembership` | `C3.3` | Search-scope membership follows the active diff and omits deleted files. |
| `LstCrcSettingsServiceTest.testResetToDefaultsRestoresRepresentativeValues` | `C4.9` | Reset restores the defaults. |
| `LstCrcSettingsServiceTest.testImportsLegacyPropertiesComponentValues` | `C4.9` | Settings from earlier versions are imported once. |
| `LstCrcSettingsServiceTest.testSettersAndGettersRoundTripValues` | `C4.9` | Every setting round-trips through its accessors. |
| `LstCrcStatusWidgetTest.testGetTextReturnsHeadWhenHeadIsSelectedEvenIfWidgetContextEnabled` | `C1.1` | The widget shows `HEAD` on the `HEAD` tab, without the context prefix. |
| `LstCrcStatusWidgetTest.testGetTextUsesAliasPrefixAndTruncationForSelectedTab` | `C1.5`, `C4.5` | The widget shows the alias, the optional prefix, and truncates long names. |
| `LstCrcStatusWidgetTest.testGetTextFallsBackToPluginNameForInvalidSelectedTabIndex` | `C5.3` | An out-of-range selected index falls back to the plugin name. |
| `LstCrcStatusWidgetTest.testPluginXmlStatusWidgetFactoryIdMatchesWidgetConstant` | `C4.5` | The widget id in `plugin.xml` matches the code. |
| `ProjectActiveDiffDataServiceTest.testAcceptsHeadUpdateWhenHeadTabIsSelected` | `C1.1`, `C5.1` | `HEAD` results are applied while the `HEAD` tab is selected. |
| `ProjectActiveDiffDataServiceTest.testRejectsStaleUpdateWhenSelectedBranchDoesNotMatch` | `C5.1` | Results for a tab that is no longer selected are dropped. |
| `ProjectActiveDiffDataServiceTest.testRejectsHeadUpdateWhileComparisonTabIsSelected` | `C5.1` | `HEAD` results are dropped while a comparison tab is selected. |
| `ProjectActiveDiffDataServiceTest.testUpdateActiveDiffWithIdenticalSnapshotBypassesNotification` | `C5.1` | Identical data does not re-notify listeners. |
| `RepoNodeRendererTest.testAddedLineStatsUseBuiltInSuccessForeground` | `C3.10` | Added counts use the theme's success color. |
| `RepoNodeRendererTest.testRemovedLineStatsUseBuiltInErrorAttributes` | `C3.10` | Removed counts use the theme's error color. |
| `RepoNodeRendererTest.testBuildTrailingMetadataTextIncludesVisibleLineStatsAndRevision` | `C3.5`, `C3.10` | Rows show the target and the line counts. |
| `RepoNodeRendererTest.testBuildTrailingMetadataTextOmitsLineStatsWhenDisabled` | `C3.10` | Line counts are hidden while the setting is off. |
| `RepoNodeRendererTest.testBuildTrailingMetadataTextSupportsLineStatsWithoutRevision` | `C3.10` | Line counts show without a target label. |
| `RepoNodeRendererTest.testAggregateLineStatsForFolderNodeSumsDescendantChanges` | `C3.10` | Folder rows sum their descendants. |
| `RepoNodeRendererTest.testAggregateLineStatsForFolderNodeReturnsNullWithoutDescendantChanges` | `C3.10` | Folders without counted changes show no counts. |
| `ToolWindowStateServicePersistenceTest.testAddTabDeduplicatesAndRemoveTabKeepsOtherTabs` | `C5.2` | Adding an existing tab is a no-op; removing one keeps the others. |
| `ToolWindowStateServicePersistenceTest.testRemoveTabClampsSelectedIndexWhenSelectedTabIsRemoved` | `C5.2` | Removing the selected tab selects a neighbour. |
| `ToolWindowStateServicePersistenceTest.testRemoveTabShiftsSelectedIndexWhenEarlierTabIsRemoved` | `C5.2` | Removing an earlier tab keeps the same tab selected. |
| `ToolWindowStateServicePersistenceTest.testLoadStateAndGetStateDefensivelyCopyNestedTabState` | `C5.2` | Loaded and returned state are defensive copies. |
| `ToolWindowStateServicePersistenceTest.testNoStateLoadedResetsToHeadSelectionSemantics` | `C1.1`, `C5.3` | With no saved state the `HEAD` tab is selected. |
| `ToolWindowStateServicePersistenceTest.testUpdateTabComparisonMapCopiesOverridesWithoutRefreshWhenDisabled` | `C2.3`, `C5.2` | Overrides are copied and stored without a refresh when asked. |
| `ToolWindowStateServicePersistenceTest.testUpdateTabAliasUpdatesMatchingTabAndLeavesOtherTabsUntouched` | `C1.5`, `C5.2` | An alias update changes only its tab. |
| `ToolWindowStateServicePersistenceTest.testUpdateTabAliasIgnoresMissingTabAndUnchangedAlias` | `C1.5`, `C5.2` | Missing tabs and unchanged aliases are ignored. |
| `ToolWindowStateServicePersistenceTest.testUpdateTabComparisonMapIgnoresMissingTabAndUnchangedMap` | `C5.2` | Missing tabs and unchanged overrides are ignored. |
| `ToolWindowStateServicePersistenceTest.testUpdateTabRepoComparisonRemovesOverrideWhenTargetMatchesDefault` | `C2.3` | Choosing the tab's own target removes the override. |
| `VcsChangeListenerTest.testHandleDocumentChangeTriggersRefreshForRepositoryFiles` | `C5.1` | Edits to repository files trigger one debounced refresh. |
| `VcsChangeListenerTest.testHandleDocumentChangeIgnoresNonRepositoryFiles` | `C5.1` | Edits outside repositories are ignored. |
| `VcsChangeListenerTest.testHandleDocumentChangeDoesNotBlockOnRepositoryCheck` | `C5.1` | The repository check never blocks the editing thread. |
| `VisualTrackerManagerBehaviorTest.testUnderlyingTrackerReportsInsertedRangeForPartialInsertionAgainstExistingBase` | `C3.7` | A partial insertion is an inserted range. |
| `VisualTrackerManagerBehaviorTest.testUnderlyingTrackerReportsInitialInsertedRangeForWholeNewFileAgainstEmptyBase` | `C3.7` | A new file against an empty base is one inserted range. |
| `VisualTrackerManagerBehaviorTest.testStandaloneTrackerInstallsGutterHighlightersForWholeNewFile` | `C3.7`, `C4.8` | The standalone tracker for a new file draws gutter markers. |
| `VisualTrackerManagerBehaviorTest.testVisualTrackerManagerCleanupOnTrackerRemoved` | `C3.7` | Visual trackers are released with the native tracker. |

## Remote Robot UI Tests

Location: `src/test/kotlin/com/github/uiopak/lstcrc/plugin (tag `ui`, run by `uiTest` on Linux, Windows and macOS)`.

| Test | Capability IDs | What it checks |
| --- | --- | --- |
| `LstCrcBranchComparisonUiTest.testBranchComparisonLineStatsIgnoreLineEndingOnlyChanges` | `C3.10` | Line counts ignore line-ending-only changes in a branch comparison. |
| `LstCrcBranchComparisonUiTest.testBranchComparisonUpdatesModifiedScope` | `C3.1`, `C3.2` | Branch-only edits appear as modified and in the `Modified` scope. |
| `LstCrcBranchComparisonUiTest.testGitBranchComparison` | `C1.1`, `C1.2`, `C2.1`, `C3.1`, `C5.1` | End-to-end branch comparison from the branch picker. |
| `LstCrcBranchComparisonUiTest.testUnsavedLocalEditAppearsWithoutSave` | `C3.8`, `C5.1` | Unsaved edits appear before save. |
| `LstCrcBranchComparisonUiTest.testRefreshKeepsTreeViewportWhenSelectionIsOffscreen` | `C3.12` | Refresh keeps the viewport with an offscreen selection. |
| `LstCrcBranchComparisonUiTest.testRefreshDoesNotMoveViewportWhenSelectionIsAtBottomAndViewportIsAtTop` | `C3.12` | Refresh does not scroll down to a bottom selection. |
| `LstCrcBranchComparisonUiTest.testHeadRefreshDoesNotMoveViewportWhenSelectionIsAtBottomAndViewportIsAtTop` | `C3.12` | Same, on the `HEAD` tab. |
| `LstCrcBranchComparisonUiTest.testUnsavedRefreshDoesNotMoveViewportWhenSelectionIsAtBottomAndViewportIsAtTop` | `C3.12` | Same, for refreshes caused by unsaved typing. |
| `LstCrcBranchComparisonUiTest.testRefreshDoesNotChangeTopVisibleEntryWhenSelectionIsAtBottomAndViewportIsAtTop` | `C3.12` | Refresh keeps the top visible row. |
| `LstCrcBranchComparisonUiTest.testClickedScrolledUnsavedRefreshWithLineStatsDoesNotMoveVisibleTreeState` | `C3.10`, `C3.12` | Unsaved refresh with line stats keeps the clicked, scrolled tree. |
| `LstCrcBranchComparisonUiTest.testEditingSelectedUntrackedFileShownAsNewDoesNotScrollTreeToIt` | `C3.11`, `C3.12` | Editing a selected untracked file does not scroll to it. |
| `LstCrcBranchComparisonUiTest.testUnsavedSingleCharacterEditsUpdateLineStatsImmediatelyAcrossMultipleLines` | `C3.8`, `C3.10` | Line counts follow single-character unsaved edits. |
| `LstCrcBranchComparisonUiTest.testNewFileStaysCreatedDuringUnsavedEdits` | `C3.8` | A new file stays created while it has unsaved edits. |
| `LstCrcBranchComparisonUiTest.testLocalNewFileAppearsInComparisonTab` | `C3.1` | A local new file appears as created. |
| `LstCrcBranchComparisonUiTest.testMultipleComparisonTabs` | `C1.4` | Several comparison tabs switch independently. |
| `LstCrcBranchComparisonUiTest.testTreeStatePersistsAcrossTabSwitches` | `C3.9` | Expand/collapse state survives tab switches. |
| `LstCrcBranchComparisonUiTest.testNewFileInCollapsedDirExpandsDirWhenSettingEnabled` | `C3.9` | A collapsed folder opens for a new change when the setting is on. |
| `LstCrcBranchComparisonUiTest.testNewFileInCollapsedDirStaysCollapsedWhenSettingDisabled` | `C3.9` | It stays collapsed when the setting is off. |
| `LstCrcBranchComparisonUiTest.testUntrackedFileAppearsWhenSettingEnabled` | `C3.11` | Untracked files appear when the setting is on. |
| `LstCrcBranchComparisonUiTest.testUntrackedFileStaysHiddenWhenSettingDisabled` | `C3.11` | Untracked files stay hidden when the setting is off. |
| `LstCrcBranchComparisonUiTest.testUntrackedFileHasUnknownFileStatus` | `C3.11` | Untracked files have the `UNKNOWN` status; tracked additions are `ADDED`. |
| `LstCrcBranchComparisonUiTest.testFileTypeFileStatuses` | `C3.1` | Rows carry `ADDED`, `DELETED` and `MODIFIED` statuses relative to the working tree. |
| `LstCrcFileScopeUiTest.testFileOperations` | `C3.1`, `C3.2` | Create, modify, rename and delete update the tree and every named scope. |
| `LstCrcInteractionUiTest.testToolWindowClickActions` | `C4.1` | Configured click actions open source, diff or project view. |
| `LstCrcInteractionUiTest.testContextMenuActionsWhenEnabled` | `C4.2` | Right click opens the context menu in context-menu mode. |
| `LstCrcInteractionUiTest.testContextMenuOpenSourceWinsOverFocusedDiffAndReusesDiff` | `C4.1`, `C4.2` | Open Source from the menu wins over a focused diff; Show Diff reuses the open diff tab. |
| `LstCrcInteractionUiTest.testStatusWidgetAndRevisionActions` | `C1.3`, `C2.1`, `C2.4` | Widget popup and Git Log actions create and retarget tabs. |
| `LstCrcInteractionUiTest.testBranchSelectionFilterDoesNotPersistAcrossReopen` | `C2.1` | The branch picker starts unfiltered each time. |
| `LstCrcInteractionUiTest.testTabRenameUpdatesWidgetContext` | `C1.5`, `C4.5` | An alias shows in the widget. |
| `LstCrcInteractionUiTest.testRenameTabPopupRenamesSelectedTab` | `C1.5` | The rename popup sets the alias. |
| `LstCrcInteractionUiTest.testRenameTabContextMenuRenamesSelectedTab` | `C1.5` | Rename from the tab context menu applies to the clicked tab. |
| `LstCrcRealRepositoryUiTest.testBranchComparisonsMatchGit` | `C1.2`, `C3.1`, `C3.2`, `C3.3`, `C3.10` | On google/gson release commits, changes, renames, line stats, scopes and Find in Files match git. |
| `LstCrcRealRepositoryUiTest.testCheckoutAndLocalEditsUpdateComparison` | `C3.1`, `C5.1` | After a checkout and local modify/add/delete/rename, the comparison matches git. |
| `LstCrcSettingsUiTest.testTreePresentationAndTitleSettings` | `C4.4`, `C4.6` | Title visibility and context-label settings. |
| `LstCrcSettingsUiTest.testGutterSettingsAndIncludeHead` | `C4.7`, `C4.8` | Gutter toggles and `Include HEAD in scopes`. |
| `LstCrcSettingsUiTest.testAdditionalClickSettings` | `C4.1`, `C4.2`, `C4.3` | Middle/right click actions, right-click mode and double-click delay. |
| `LstCrcSettingsUiTest.testRenderedTreeContextLabelsRespectSingleRepoAndCommitSettings` | `C3.5`, `C4.6` | Rendered context labels follow the single-repo and commit settings. |
| `LstCrcVisualUiTest.testVisualGutterMarkersForModifiedAndDeletedRanges` | `C3.7` | Modified and deleted gutter ranges follow the comparison target. |
| `LstCrcVisualUiTest.testVisualGutterMarkersForInsertedNewFile` | `C3.7`, `C4.8` | A new file gets inserted-range gutter markers. |
| `LstCrcVisualUiTest.testVisualGutterMarkers` | `C3.7`, `C5.1` | Gutter markers follow the comparison target and update with edits. |

## IDE Starter UI Tests

Location: `src/uiTest/kotlin/com/github/uiopak/lstcrc/starter (tags `starter` and `starter-performance`, Linux)`.

| Test | Capability IDs | What it checks |
| --- | --- | --- |
| `LstCrcBranchComparisonStarterUiTest.testBranchComparisonLineStatsIgnoreLineEndingOnlyChanges` | `C3.10` | Line counts ignore line-ending-only changes. |
| `LstCrcBranchComparisonStarterUiTest.testGitBranchComparison` | `C1.1`, `C1.2`, `C2.1`, `C3.1` | End-to-end branch comparison from the branch picker. |
| `LstCrcBranchComparisonStarterUiTest.testMultipleComparisonTabs` | `C1.4` | Several comparison tabs switch independently. |
| `LstCrcBranchComparisonStarterUiTest.testTreeStatePersistsAcrossTabSwitches` | `C3.9` | Expand/collapse state survives tab switches. |
| `LstCrcBranchComparisonStarterUiTest.testNewFileInCollapsedDirExpandsDirWhenSettingEnabled` | `C3.9` | A collapsed folder opens for a new change when the setting is on. |
| `LstCrcBranchComparisonStarterUiTest.testNewFileInCollapsedDirKeepsDirCollapsedWhenSettingDisabled` | `C3.9` | It stays collapsed when the setting is off. |
| `LstCrcBranchComparisonStarterUiTest.testUntrackedFileAppearsWhenSettingEnabled` | `C3.11` | Untracked files appear when the setting is on. |
| `LstCrcBranchComparisonStarterUiTest.testUntrackedFileStaysHiddenWhenSettingDisabled` | `C3.11` | Untracked files stay hidden when the setting is off. |
| `LstCrcBranchComparisonStarterUiTest.testUntrackedFileHasUnknownFileStatus` | `C3.11` | Untracked files have the `UNKNOWN` status. |
| `LstCrcBranchComparisonStarterUiTest.testFileTypeFileStatuses` | `C3.1` | Rows carry `ADDED`, `DELETED` and `MODIFIED` statuses. |
| `LstCrcFileScopeStarterUiTest.testFileOperations` | `C3.1`, `C3.2` | Create, modify, rename and delete update the tree and every named scope. |
| `LstCrcFileScopeStarterUiTest.testPermanentHeadTabScopesStayEmptyUntilIncludeHeadIsEnabled` | `C1.1`, `C4.7` | `HEAD` scopes stay empty until the setting is on. |
| `LstCrcFileScopeStarterUiTest.testIncludeHeadInScopesDoesNotAffectBranchTabScopes` | `C4.7` | The setting does not affect comparison tabs. |
| `LstCrcFileScopeStarterUiTest.testFindDialogShowsLstCrcSearchScopes` | `C3.3` | Find in Files lists the searchable scopes and not `Deleted`. |
| `LstCrcFileScopeStarterUiTest.testDeletedFilesUseDeletedScopeTreeColor` | `C3.1`, `C3.4`, `C3.6` | Deleted rows use the `Deleted` scope color. |
| `LstCrcFileScopeStarterUiTest.testDeletedFileColorDoesNotLeakToModifiedRows` | `C3.6` | Other rows do not get the deleted color. |
| `LstCrcInteractionStarterUiTest.testToolWindowClickActions` | `C4.1` | Configured click actions. |
| `LstCrcInteractionStarterUiTest.testContextMenuActionsWhenEnabled` | `C4.2` | Context-menu mode. |
| `LstCrcInteractionStarterUiTest.testContextMenuOpenSourceWinsOverFocusedDiffAndReusesDiff` | `C4.1`, `C4.2` | Open Source wins over a focused diff; Show Diff reuses the open diff tab. |
| `LstCrcInteractionStarterUiTest.testStatusWidgetAndRevisionActions` | `C1.3`, `C2.1`, `C2.4` | Widget popup and Git Log actions. |
| `LstCrcInteractionStarterUiTest.testTabRenameUpdatesWidgetContext` | `C1.5`, `C4.5` | An alias shows in the widget. |
| `LstCrcInteractionStarterUiTest.testMissingBranchComparisonTargetRecoversToHeadAndShowsWarning` | `C2.5`, `C5.1`, `C5.4` | A missing branch falls back to `HEAD` and warns. |
| `LstCrcInteractionStarterUiTest.testMissingCommitComparisonTargetDoesNotRecoverToHeadOrWarn` | `C2.6`, `C5.4` | A missing commit neither falls back nor warns. |
| `LstCrcInteractionStarterUiTest.testRepositoryComparisonToolbarDialogAllowsChangingComparison` | `C2.3` | The repo-comparison dialog changes the target. |
| `LstCrcMultiRootStarterUiTest.testLinkedWorktreeBranchSwitchRefreshesActiveComparison` | `C5.5` | A linked worktree is its own root and refreshes on branch switch. |
| `LstCrcMultiRootStarterUiTest.testPrimaryWorktreeBranchSwitchPreservesLinkedWorktreeDiffContribution` | `C5.5` | Switching the primary worktree keeps the linked worktree's changes. |
| `LstCrcMultiRootStarterUiTest.testBranchSelectionUsesPrimaryRepositoryBranchesInMultiRootProject` | `C2.2` | The picker lists the primary repository's branches. |
| `LstCrcMultiRootStarterUiTest.testMultiRootComparisonOverrideAppliesOnlyToSelectedRepository` | `C2.3`, `C3.5`, `C4.6` | An override changes one root; multi-repo labels follow their setting. |
| `LstCrcMultiRootStarterUiTest.testMissingBranchNotificationRepairReconfiguresOnlyBrokenRepository` | `C2.5`, `C5.4` | Repair from the notification changes only the broken root. |
| `LstCrcMultiRootStarterUiTest.testTabsAliasesAndRepoOverridesRestoreAfterRestart` | `C1.5`, `C2.3`, `C5.2` | Tabs, aliases and overrides survive an IDE restart. |
| `LstCrcRealRepositoryStarterUiTest.testBranchComparisonsMatchGit` | `C1.2`, `C3.1`, `C3.2`, `C3.3`, `C3.10` | On google/gson release commits, the comparison matches git. |
| `LstCrcRealRepositoryStarterUiTest.testCheckoutAndLocalEditsUpdateComparison` | `C3.1`, `C5.1` | After a checkout and local edits, the comparison matches git. |
| `LstCrcSettingsStarterUiTest.testTreePresentationAndTitleSettings` | `C4.4`, `C4.6` | Title visibility and context-label settings. |
| `LstCrcSettingsStarterUiTest.testGutterSettingsAndIncludeHead` | `C4.7`, `C4.8` | Gutter toggles and `Include HEAD in scopes`. |
| `LstCrcSettingsStarterUiTest.testAdditionalClickSettings` | `C4.1`, `C4.2`, `C4.3` | Middle/right click actions, right-click mode and double-click delay. |
| `LstCrcSettingsStarterUiTest.testRenderedTreeContextLabelsRespectSingleRepoAndCommitSettings` | `C3.5`, `C4.6` | Rendered context labels follow their settings. |
| `LstCrcStarterPerformanceTest.testToolWindowOpenAndBranchLoadPerformance` | `C1.2` | Performance smoke (`starterPerformanceTest`, 21 changed files): opening the tool window, loading a branch tab and switching tabs stay under hang-level limits (10 s, 60 s, ...), and each step is recorded. |
| `LstCrcVisualStarterUiTest.testVisualGutterMarkers` | `C3.7` | Gutter markers follow the comparison target. |
| `LstCrcVisualStarterUiTest.testVisualGutterMarkersForInsertedAndDeletedRanges` | `C3.7` | Inserted and deleted gutter ranges. |

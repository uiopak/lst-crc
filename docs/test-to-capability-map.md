# Test To Capability Map

This file maps every test method to the capability IDs defined in [plugin-capabilities.md](plugin-capabilities.md). The case-by-case view is in [test-capability-matrix.md](test-capability-matrix.md).

When you add, rename or remove a test, update this file. Every `test*` method in `src/test` and `src/uiTest` must appear here exactly once per class.

## Unit And Service Tests

Location: `src/test/kotlin/com/github/uiopak/lstcrc/{gutters,listeners,scopes,services,toolWindow}`.

| Test | Capability IDs | What it checks |
| --- | --- | --- |
| `GitServicePerformanceTest.testRefreshesReusePreparedDiskWork` | `C3.8`, `C3.10`, `C5.1` | Real-repository edit-only refreshes run no new Git diff, skip categorization for unchanged results, and categorize each distinct unsaved edit. Each measured snapshot must be accepted; timings and EDT allocations are report-only. |
| `GitServicePerformanceTest.testPreparedResultsInvalidateForTargetsSettingsAndDiskReloads` | `C2.3`, `C2.5`, `C3.10`, `C3.11`, `C5.1` | Cached work is replaced for targets, settings and full reloads; missing targets retry while identical edit-only loads reuse results. Test load repositories do not override public discovery. |
| `DiffComputationPerformanceTest.testAddedAndDeletedLineStatsMatchTheDiffEngine` | `C3.10` | Added and deleted counts match `ComparisonManager.compareLines` on normalized text in both directions, including empty text, blank lines, absent trailing newlines, LF, CR and CRLF. Nonempty inputs use the engine. |
| `DiffComputationPerformanceTest.testEqualLineStatsSkipTheDiffEngine` | `C3.10` | Equal normalized text returns zero counts without invoking the diff engine. |
| `DiffComputationPerformanceTest.testChangedLineStatsStillUsesTheDiffEngine` | `C3.10` | Real text changes still invoke the platform diff engine and produce correct counts. |
| `DiffComputationPerformanceTest.testDuplicateDiskPathsKeepLastChangeAndStats` | `C3.1`, `C3.10` | Cached disk results keep the last change and its counts when paths repeat. |
| `DiffComputationPerformanceTest.testUnchangedSnapshotSkipsPerChangeComparison` | `C5.1` | Reapplying an accepted identical comparison reads zero change entries on the EDT; rejecting the initial update cannot make the counter pass. |
| `DiffComputationPerformanceTest.testReportLargeLineStatsWork` | `C3.10` | Reports time and allocation for large added, deleted, equal and modified texts without timing assertions. |
| `GitServiceLineStatsTest.testUnsavedOverlayUsesOnlyTheOwningNestedRepository` | `C2.3`, `C5.1` | An unsaved file in a registered nested root is overlaid only by its owning repository. |
| `GitServiceLineStatsTest.testUnsavedRevertOnCleanDiskDoesNotCreateAChange` | `C3.1`, `C5.1` | Editing and restoring a clean file to target text leaves no changed entry or counts. |
| `GitServiceLineStatsTest.testUnsavedRevertRemovesADiskContentChange` | `C3.1`, `C5.1` | Unsaved text that restores the target replaces the disk modification with a clean comparison. |
| `GitServiceLineStatsTest.testUnsavedRevertRemovesAContentChangeWithLineStatsDisabled` | `C3.1`, `C3.10`, `C5.1` | Restoring target text also removes a disk modification while line-stat computation is disabled. |
| `GitServiceLineStatsTest.testUnsavedRevertPreservesAFileModeChange` | `C3.1`, `C5.1` | Equal editor text does not hide a tracked executable-mode change. |
| `GitServiceLineStatsTest.testUnsavedRevertPreservesChangedLineEndings` | `C3.1`, `C5.1` | Restoring logical target text retains physical line endings that still differ from the target blob. |
| `GitServiceLineStatsTest.testUnsavedRevertPreservesAnAddedByteOrderMark` | `C3.1`, `C5.1` | Restoring logical target text retains a byte-order mark that differs from the target blob. |
| `GitServiceLineStatsTest.testUnsavedRevertOfUtf16ContentRemovesTheContentChange` | `C3.1`, `C5.1` | Restored UTF-16 editor text hashes with its charset and BOM and removes the content-only change. |
| `GitServiceLineStatsTest.testUnsavedRevertPreservesLeadingBomTextCharacter` | `C3.1`, `C5.1` | A leading U+FEFF text character stays distinct from the file BOM when restored content is checked against Git. |
| `GitServiceLineStatsTest.testUnsavedRevertWithBomProducingEncoderRemovesContentChange` | `C3.1`, `C5.1` | An encoder that emits its own BOM does not receive a duplicate file BOM. |
| `ChangesTreeClickHandlerTest.testQueuedClickDoesNotRunAfterHandlerDisposal` | `C4.1`, `C5.1` | Closing the handler after a mouse click but before EDT dispatch discards its queued action. |
| `ChangesTreeClickHandlerTest.testQueuedClickRunsWhileHandlerIsAlive` | `C4.1` | The same configured click still runs once while its handler is alive. |
| `LstCrcChangesBrowserTest.testQueuedRefreshDoesNotUpdateDisposedBrowser` | `C3.12`, `C5.1` | Closing a browser before queued diff application leaves its snapshot unchanged. |
| `LstCrcChangesBrowserTest.testPlatformCancelledRevisionOpenDoesNotShowLoadingError` | `C3.4`, `C5.1` | Platform cancellation aborts source opening without a warning or partial editor. |
| `LstCrcChangesBrowserTest.testCoroutineCancelledRevisionOpenDoesNotShowLoadingError` | `C3.4`, `C5.1` | Coroutine cancellation in the platform pool stops source opening quietly. |
| `LstCrcChangesBrowserTest.testFailedRevisionOpenStillShowsLoadingError` | `C3.4`, `C5.1` | A genuine revision-content error still shows a warning and does not open a partial file. |
| `BranchSelectionPanelTest.testFilterSelectsFirstMatchingBranchFromStableSnapshot` | `C2.1` | Typing a filter keeps matching branches and selects the first match. |
| `BranchSelectionPanelTest.testFilterMatchingAFolderSelectsItsFirstBranch` | `C2.1` | A filter matching a folder or category selects its first branch, so Enter has a branch to add. |
| `BranchSelectionPanelTest.testEnterSubmitsSelectedBranch` | `C2.1` | Enter on the selected branch submits it. |
| `BranchSelectionPanelTest.testNewPanelReopensWithFullBranchSnapshotAfterPreviousFilter` | `C2.1` | A new panel starts unfiltered after an earlier panel was filtered. |
| `GitServiceComparisonTargetTest.testResolveComparisonTargetPrecedence` | `C2.3` | A per-repository override wins over the tab target; the `HEAD` tab compares against `HEAD`. |
| `GitServiceComparisonTargetTest.testResolveCommitHashUsesRepositoryStateAndRejectsAmbiguousRevisions` | `C3.8` | Revision content is cached only under a commit hash resolved from `HEAD`, a branch or a full hash; tags and short hashes are not cached. |
| `GitServiceComparisonTargetTest.testDiffFailureKeepsTheLastResultUnlessTheTargetIsMissing` | `C2.5` | Regression: a passing `git diff` failure keeps the last result; a missing target gives no result. |
| `GitServiceComparisonTargetTest.testRevisionContentCacheRemembersFilesMissingFromACommit` | `C3.8` | Regression: a file missing from a commit is remembered, so `git show` does not run on every refresh. |
| `GitServiceLineStatsTest.testCalculateLineStatsIgnoresLineEndingOnlyDifferences` | `C3.10` | CRLF/LF-only differences count as no change. |
| `GitServiceLineStatsTest.testCalculateLineStatsCountsRealChangesWhenLineEndingsAlsoDiffer` | `C3.10` | Real edits still count when line endings differ too. |
| `GitServiceLineStatsTest.testCalculateLineStatsForSingleLineReplacement` | `C3.10` | A replaced line counts as one added and one removed. |
| `GitServiceLineStatsTest.testCalculateLineStatsForNewFileContent` | `C3.10` | A new file counts all its lines as added. |
| `GitServiceLineStatsTest.testCalculateLineStatsForDeletedFileContent` | `C3.10` | A deleted file counts all its lines as removed. |
| `GitServiceLineStatsTest.testLineStatsContentFailureDoesNotDiscardOtherFiles` | `C3.10`, `C5.1` | A content-read failure omits that file's counts while other files still receive stats. |
| `GitServiceLineStatsTest.testUnavailableRevisionTextDoesNotCountAsAnEmptyFile` | `C3.10` | A present revision with unavailable text gets no counts, rather than false additions or deletions. |
| `GitServiceLineStatsTest.testLineStatsContentCancellationIsPropagated` | `C5.1` | Line-stat fallback propagates platform and coroutine cancellation. |
| `GitServiceLineStatsTest.testTrackedBinaryDiffDoesNotGetFallbackLineStats` | `C3.10` | Binary entries stay in the disk changes without content reads or fallback line counts, even when their content is readable text. |
| `GitServiceLineStatsTest.testTrackedLineStatsDiffArgsIgnoreLineEndingOnlyChurn` | `C3.10` | `git diff --numstat` is run with `--ignore-cr-at-eol`. |
| `GitServiceLineStatsTest.testTrackedDiffArgsAcceptBranchNamedLikeAFolder` | `C1.2` | A branch named like a folder (`docs`) is compared, not read as a path. |
| `GitServiceLineStatsTest.testRevisionExistsOnlyForResolvableTargets` | `C2.5` | Only a target git cannot resolve counts as missing. |
| `GitServiceLineStatsTest.testCreateLiveDocumentContentRevisionReadsLatestUnsavedDocumentText` | `C3.8` | The unsaved-overlay revision reads the editor's current text. |
| `GitServiceLineStatsTest.testCreateLiveDocumentContentRevisionAllowsBackgroundThreadAccess` | `C3.8` | The unsaved-overlay revision can be read off the EDT. |
| `GitServiceLineStatsTest.testLiveDocumentContentRevisionsAreEqualOnlyForTheSameText` | `C3.8` | Two loads of the same unsaved text are equal; different text is not. |
| `GitServiceLineStatsTest.testUnsavedEditOfAddedFileIncludesLiveTextAndLineStats` | `C3.8`, `C3.10` | New files overlay the latest unsaved text and line counts without loading target content. |
| `GitServiceLineStatsTest.testUnsavedEditOfMovedFileIsComparedWithItsOldPath` | `C3.8` | Regression: an unsaved edit of a moved file is compared with the old path's content in the target. |
| `GitServiceLineStatsTest.testUnsavedOverlayCancellationIsPropagated` | `C3.8`, `C5.1` | Unsaved-document target loading propagates platform and coroutine cancellation through both error handlers. |
| `GitServiceOverlayMergeTest.testPreservesNewChangeTypeWhenUnsavedOverlayIsApplied` | `C3.8` | Unsaved edits to a new file keep it `NEW`/`ADDED`. |
| `GitServiceOverlayMergeTest.testKeepsModificationOverlayForNonNewFiles` | `C3.8` | Unsaved edits to other files stay modifications. |
| `GitServiceOverlayMergeTest.testNewFilePathsIncludesAddedAndUntrackedFiles` | `C3.8`, `C3.11` | Added and untracked files both overlay live text without a before-revision lookup. |
| `GitServiceOverlayMergeTest.testPreservesUntrackedStatusWhenUnsavedOverlayIsApplied` | `C3.8`, `C3.11` | Unsaved edits keep an untracked file's `UNKNOWN` status. |
| `GitServiceOverlayMergeTest.testUntrackedChangesKeepBackslashesInFileNames` | `C3.11` | On Unix, `git ls-files -z` paths are not unescaped. Windows cannot represent these file names. |
| `GitServiceOverlayMergeTest.testUntrackedChangesKeepFileNamesMadeOfSpaces` | `C3.11` | An untracked file whose name is only spaces is kept. |
| `GitServiceOverlayMergeTest.testParseTrackedDiffKeepsTabsInNumstatPaths` | `C3.10` | A tab in a `--numstat -z` path stays part of the path, so the file keeps its line stats. |
| `GitServiceOverlayMergeTest.testParseTrackedDiffKeepsNewlinesAtTheEndOfNumstatPaths` | `C3.10` | Newlines in or at the end of a file name stay part of the numstat path, while Git's section separator is skipped. |
| `GitServiceOverlayMergeTest.testParseTrackedDiffReadsRawAndNumstatRecordsIncludingRenames` | `C3.1`, `C3.10` | `git diff --raw --numstat -z` output becomes changes and line stats, renames included, and moved files map to their old path. |
| `LstCrcActionVisibilityTest.testShowRepoComparisonInfoActionHiddenOnHeadAndVisibleForComparisonTab` | `C2.3` | Repo-comparison toolbar action is hidden on `HEAD` and shown on comparison tabs. |
| `LstCrcActionVisibilityTest.testRepoComparisonPopupShowsRepositoryAndTargetNamesWithUnderscores` | `C1.5`, `C2.3` | The repository popup lists each repository and its target, sorted, with underscores. |
| `LstCrcActionVisibilityTest.testCreateTabFromRevisionActionVisibleOnlyForSingleRevisionSelection` | `C1.3` | Git Log "create tab" needs exactly one selected revision. |
| `LstCrcActionVisibilityTest.testRenameTabActionVisibleForClosableBranchTabWhenContextIsNestedUnderBaseLabel` | `C1.5` | Rename is offered from inside a closable tab's label. |
| `LstCrcActionVisibilityTest.testRenameTabActionHiddenWithoutRenamableTabContext` | `C1.5` | Rename is hidden for other tool windows, the `HEAD` tab, tabs without a branch key and unrelated components. |
| `LstCrcActionVisibilityTest.testOpenBranchSelectionTabActionHiddenWhenSelectionTabAlreadyExists` | `C2.1` | The add-tab action hides while the branch-selection tab is open. |
| `LstCrcActionVisibilityTest.testOpenBranchSelectionTabActionVisibleWhenSelectionTabIsAbsent` | `C2.1` | The add-tab action shows when no branch-selection tab is open. |
| `LstCrcActionVisibilityTest.testOpenBranchSelectionTabActionVisibleWhenAComparisonTabIsNamedLikeTheSelectionTab` | `C2.1` | A comparison tab renamed "Select Branch" does not hide the add-tab action. |
| `LstCrcActionVisibilityTest.testSetRevisionAsRepoComparisonActionVisibleOnlyForSingleCommitSelectionWithActiveTab` | `C2.4` | Git Log repo-comparison action needs one selected commit and an active comparison tab. |
| `LstCrcChangesBrowserTest.testRefreshPreservesTreeViewportPosition` | `C3.12` | A refresh keeps the scroll position. |
| `LstCrcChangesBrowserTest.testRepeatedRefreshPreservesTreeViewportPosition` | `C3.12` | Repeated refreshes keep the scroll position. |
| `LstCrcChangesBrowserTest.testRefreshPreservesTopViewportWhenSelectionIsOffscreen` | `C3.12` | A refresh keeps the view at the top when the selection is offscreen. |
| `LstCrcChangesBrowserTest.testRefreshDoesNotMoveViewportWhileSelectionIsOffscreen` | `C3.12` | A refresh does not scroll to an offscreen selection. |
| `LstCrcChangesBrowserTest.testRefreshDoesNotMoveViewportForSelectedAddedFileWhileOffscreen` | `C3.12` | A refresh does not scroll to an offscreen selected added file. |
| `LstCrcChangesBrowserTest.testRefreshKeepsCollapsedFolderCollapsed` | `C3.9` | A refresh keeps a collapsed folder collapsed. |
| `LstCrcChangesBrowserTest.testRefreshKeepsSelectedCollapsedFolderCollapsedAndSelected` | `C3.9` | Regression: a selected collapsed folder stays collapsed and selected after a refresh (the macOS tab-switch flake). |
| `LstCrcChangesBrowserTest.testDiffKeyOfUnsavedEditChangesWithItsText` | `C4.1` | An open diff tab of an unsaved file is reused only for the same unsaved text. |
| `LstCrcChangesBrowserTest.testDiffKeyChangesWhenTheTargetMovesToAnotherCommit` | `C4.1` | A diff tab opened before the target moved is not reused; it is replaced. |
| `LstCrcChangesBrowserTest.testClearedDiffShowsLoadErrorInSelectedTab` | `C1.4` | Cleared diff data (a failed load) shows the error in the selected tab's browser. |
| `LstCrcChangesBrowserTest.testAvailableContextMenuActionsIncludeProjectTreeForNonDeletedChange` | `C4.2` | The context menu offers "Show in Project" for existing files. |
| `LstCrcChangesBrowserTest.testAvailableContextMenuActionsOmitProjectTreeForDeletedChange` | `C4.2` | The context menu omits "Show in Project" for deleted files. |
| `LstCrcChangesBrowserTest.testConfiguredClickActionLookupUsesButtonSpecificSettings` | `C4.1` | Each mouse button uses its own single/double click settings. |
| `LstCrcChangesBrowserTest.testConfiguredClickActionLookupFallsBackToNoneForUnsupportedButtons` | `C4.1` | Other mouse buttons do nothing. |
| `LstCrcChangesBrowserTest.testCtrlClickKeepsTheMultiSelection` | `C4.1` | Regression: a Ctrl+click keeps the selection JTree built instead of selecting only the clicked change. |
| `LstCrcChangesBrowserTest.testRightClickInsideTheSelectionKeepsIt` | `C4.1` | Regression: a right-click on a selected change keeps the selection; outside it, selects the clicked change. |
| `LstCrcChangesBrowserTest.testToolbarActionsIncludeRepoComparisonActionImmediatelyAfterGroupByWhenPresent` | `C2.3` | The repo-comparison action sits right after the group-by action in the toolbar. |
| `LstCrcFileStatusScopesTest.testDeletedScopeMatchesDeletedPathsWhileChangedExcludesThem` | `C3.2`, `C3.4` | `Deleted` matches deleted paths; `Changed` excludes them. |
| `LstCrcFileStatusScopesTest.testScopesExcludeHeadChangesWhenIncludeHeadInScopesIsDisabled` | `C4.7` | Scopes ignore `HEAD` data while `Include HEAD in scopes` is off. |
| `LstCrcSearchScopeProviderTest.testProvidedScopesExposeCanonicalIdsOrderAndSearchableSubset` | `C3.2`, `C3.3` | Scope ids and order are stable; `Deleted` is not searchable. |
| `LstCrcSearchScopeProviderTest.testGetDisplayNameAndSearchScopesReturnExpectedLstCrcScopes` | `C3.3` | The Find/Search group lists Created, Modified, Moved and Changed. |
| `LstCrcSearchScopeProviderTest.testSearchScopesReflectDetailedFileStateMembership` | `C3.3` | Search-scope membership follows the active diff and omits deleted files. |
| `LstCrcSettingsServiceTest.testResetToDefaultsRestoresRepresentativeValues` | `C4.9` | Reset restores the defaults. |
| `LstCrcSettingsServiceTest.testImportsLegacyPropertiesComponentValues` | `C4.9` | Settings from earlier versions are imported once. |
| `LstCrcSettingsServiceTest.testSettersAndGettersRoundTripValues` | `C4.9` | Every setting round-trips through its accessors. |
| `LstCrcSettingsServiceTest.testEveryDefinitionIsRegisteredOnce` | `C4.9` | Every setting definition is in `LstCrcSettingDefinitions.all` once. |
| `LstCrcStatusWidgetTest.testPopupShowsTabNamesWithUnderscoresAsTyped` | `C1.5` | The widget popup shows branch names and aliases with underscores. |
| `LstCrcStatusWidgetTest.testGetTextReturnsHeadWhenHeadIsSelectedEvenIfWidgetContextEnabled` | `C1.1` | The widget shows `HEAD` on the `HEAD` tab, without the context prefix. |
| `LstCrcStatusWidgetTest.testGetTextUsesAliasPrefixAndTruncationForSelectedTab` | `C1.5`, `C4.5` | The widget shows the alias and the optional prefix, cuts long names with an ellipsis, and its tooltip has the whole name. |
| `LstCrcStatusWidgetTest.testTruncatedAliasKeepsSupplementaryCharactersWhole` | `C1.5`, `C4.5` | Truncation never splits a supplementary character; a name exactly at the limit stays whole and the tooltip retains the full alias. |
| `LstCrcStatusWidgetTest.testGetTextFallsBackToPluginNameForInvalidSelectedTabIndex` | `C5.3` | An out-of-range selected index falls back to the plugin name. |
| `LstCrcStatusWidgetTest.testPluginXmlStatusWidgetFactoryIdMatchesWidgetConstant` | `C4.5` | The widget id in `plugin.xml` matches the code. |
| `MyToolWindowFactoryTest.testOnlyTheHeadTabSelectsHead` | `C1.1`, `C2.1` | Only the HEAD tab selects HEAD; the "Select Branch" tab keeps the current comparison. |
| `MyToolWindowFactoryTest.testStateIndexOfATabCountsTheComparisonTabsBeforeIt` | `C1.4` | A tab's state index counts only the comparison tabs before it (not HEAD or "Select Branch"). |
| `PluginStartupActivityTest.testInitialDiffLoadPropagatesCancellation` | `C5.1` | Regression: cancelling startup cancels the wait for the first diff load instead of logging it as a failure. |
| `PluginStartupActivityTest.testInitialDiffLoadFailureDoesNotStopStartup` | `C5.1` | A failed first diff load is logged and startup continues. |
| `ProjectActiveDiffDataServiceTest.testAcceptsHeadUpdateWhenHeadTabIsSelected` | `C1.1`, `C5.1` | `HEAD` results are applied while the `HEAD` tab is selected. |
| `ProjectActiveDiffDataServiceTest.testRejectsStaleUpdateWhenSelectedBranchDoesNotMatch` | `C5.1` | Results for a tab that is no longer selected are dropped. |
| `ProjectActiveDiffDataServiceTest.testRejectsUpdateWhenTheTabIsSwitchedBeforeItIsApplied` | `C5.1` | A result sent from a background thread is dropped when the tab changes before it is applied. |
| `ProjectActiveDiffDataServiceTest.testRejectsUpdateWhenRepositoryTargetChangesBeforeItIsApplied` | `C2.3`, `C5.1` | A queued result is rejected after a repository override changes in the same tab; the new target's result is accepted. |
| `ProjectActiveDiffDataServiceTest.testRejectsHeadUpdateWhileComparisonTabIsSelected` | `C5.1` | `HEAD` results are dropped while a comparison tab is selected. |
| `ProjectActiveDiffDataServiceTest.testUpdateActiveDiffWithIdenticalSnapshotBypassesNotification` | `C5.1` | Identical data does not re-notify listeners. |
| `ProjectActiveDiffDataServiceTest.testSamePathsWithNewUnsavedContentPublishesNewChanges` | `C3.8`, `C5.1` | New unsaved content on the same paths is published; the same content is not. |
| `ProjectActiveDiffDataServiceTest.testNewUnsavedContentOfTheSameFilesKeepsFileStatuses` | `C5.1` | Regression: new unsaved text of the same files does not reset file statuses; a file joining a scope does. |
| `ProjectActiveDiffDataServiceTest.testSwitchingComparisonTabsWithSameScopesKeepsFileStatuses` | `C3.7`, `C5.1` | A target-name change with the same scope membership publishes the comparison without resetting statuses; a changed category still resets them. |
| `ProjectActiveDiffDataServiceTest.testHeadSwitchOnlyResetsFileStatusesWhenScopeMembershipChanges` | `C3.7`, `C4.7` | Switching to and from `HEAD` resets statuses only when `Include HEAD in scopes` changes effective membership. |
| `ProjectActiveDiffDataServiceTest.testMovedFileIsLookedUpByItsOldPathInTheTarget` | `C3.7` | Regression: the gutter of a moved file compares with its old path in the target. |
| `RepoNodeRendererTest.testAddedLineStatsUseBuiltInSuccessForeground` | `C3.10` | Added counts use the theme's success color. |
| `RepoNodeRendererTest.testRemovedLineStatsUseBuiltInErrorAttributes` | `C3.10` | Removed counts use the theme's error color. |
| `RepoNodeRendererTest.testBuildTrailingMetadataTextIncludesVisibleLineStatsAndRevision` | `C3.5`, `C3.10` | Rows show the target and the line counts. |
| `RepoNodeRendererTest.testBuildTrailingMetadataTextOmitsLineStatsWhenDisabled` | `C3.10` | Line counts are hidden while the setting is off. |
| `RepoNodeRendererTest.testBuildTrailingMetadataTextSupportsLineStatsWithoutRevision` | `C3.10` | Line counts show without a target label. |
| `RepoNodeRendererTest.testAggregateLineStatsForFolderNodeSumsDescendantChanges` | `C3.10` | Folder rows sum their descendants. |
| `RepoNodeRendererTest.testAggregateLineStatsForFolderNodeReturnsNullWithoutDescendantChanges` | `C3.10` | Folders without counted changes show no counts. |
| `ToolWindowStateServiceRefreshTest.testJoinedRefreshLoadsTheSelectionMadeBeforeTheRequest` | `C5.1` | A joined refresh has loaded the state from before the request. |
| `ToolWindowStateServiceRefreshTest.testObsoleteTabLoadFailureKeepsPreviouslyLoadedComparison` | `C5.1` | A controlled old-tab failure preserves the currently selected comparison and is not reported as its error. |
| `ToolWindowStateServiceRefreshTest.testObsoleteRepositoryTargetFailureKeepsPreviouslyLoadedComparison` | `C2.3`, `C5.1` | A controlled failure after a root's target changes preserves its newer comparison. |
| `ToolWindowStateServiceRefreshTest.testPlatformCancellationKeepsActiveDiffAndFailsRefreshFuture` | `C5.1` | Platform cancellation fails the refresh future without clearing cached changes or logging a loading error. |
| `ToolWindowStateServiceRefreshTest.testCurrentLoadFailureStillClearsActiveDiff` | `C5.1` | A failure for the current comparison still logs an error and clears the active diff. |
| `ToolWindowStateServiceRefreshTest.testRemovingTheSelectedTabLoadsTheTabBeforeIt` | `C1.4` | Closing the selected tab loads the tab before it, or `HEAD` for the first tab. |
| `ToolWindowStateServicePersistenceTest.testAddTabDeduplicatesAndRemoveTabKeepsOtherTabs` | `C5.2` | Adding an existing tab is a no-op; removing one keeps the others. |
| `ToolWindowStateServicePersistenceTest.testAddTabAtAPositionKeepsTheSelectedTab` | `C1.4`, `C5.2` | A tab added at a position lands there and the selected tab stays selected. |
| `ToolWindowStateServicePersistenceTest.testRemoveTabClampsSelectedIndexWhenSelectedTabIsRemoved` | `C5.2` | Removing the selected tab selects a neighbour. |
| `ToolWindowStateServicePersistenceTest.testRemoveTabSelectsTheTabBeforeTheRemovedSelectedTab` | `C5.2` | Removing the selected tab selects the tab before it, as the tool window does. |
| `ToolWindowStateServicePersistenceTest.testRemoveTabShiftsSelectedIndexWhenEarlierTabIsRemoved` | `C5.2` | Removing an earlier tab keeps the same tab selected. |
| `ToolWindowStateServicePersistenceTest.testLoadStateAndGetStateDefensivelyCopyNestedTabState` | `C5.2` | Loaded and returned state are defensive copies. |
| `ToolWindowStateServicePersistenceTest.testSelectedTabInfoCannotMutateStoredComparisonTargets` | `C2.3`, `C5.2` | Mutating or replacing the selected-tab lookup's map cannot bypass persisted target updates. |
| `ToolWindowStateServicePersistenceTest.testDisplayNameLookupCannotMutateStoredComparisonTargets` | `C2.3`, `C5.2` | A display-name lookup returns an independent tab and comparison map. |
| `ToolWindowStateServicePersistenceTest.testNoStateLoadedResetsToHeadSelectionSemantics` | `C1.1`, `C5.3` | With no saved state the `HEAD` tab is selected. |
| `ToolWindowStateServicePersistenceTest.testUpdateTabComparisonMapCopiesOverridesWithoutRefreshWhenDisabled` | `C2.3`, `C5.2` | Overrides are copied and stored without a refresh when asked. |
| `ToolWindowStateServicePersistenceTest.testUpdateTabAliasUpdatesMatchingTabAndLeavesOtherTabsUntouched` | `C1.5`, `C5.2` | An alias update changes only its tab. |
| `ToolWindowStateServicePersistenceTest.testUpdateTabAliasIgnoresMissingTabAndUnchangedAlias` | `C1.5`, `C5.2` | Missing tabs and unchanged aliases are ignored. |
| `ToolWindowStateServicePersistenceTest.testUpdateTabComparisonMapIgnoresMissingTabAndUnchangedMap` | `C5.2` | Missing tabs and unchanged overrides are ignored. |
| `ToolWindowStateServicePersistenceTest.testUpdateTabRepoComparisonRemovesOverrideWhenTargetMatchesDefault` | `C2.3` | Choosing the tab's own target removes the override. |
| `ToolWindowStateServicePersistenceTest.testMissingBranchFailureDoesNotOverwriteANewerRepositoryTarget` | `C2.3`, `C2.5` | A failure for an old target does not reset a newer override to `HEAD`. |
| `ToolWindowStateServicePersistenceTest.testMissingBranchRepairPreservesNewerOverridesInOtherRepositories` | `C2.3`, `C2.5` | Repair resets only targets still missing and preserves other overrides changed during the load. |
| `VcsChangeListenerTest.testHandleDocumentChangeTriggersRefreshForRepositoryFiles` | `C5.1` | Edits to repository files trigger one debounced refresh. |
| `VcsChangeListenerTest.testHandleDocumentChangeIgnoresNonRepositoryFiles` | `C5.1` | Edits outside repositories are ignored. |
| `VcsChangeListenerTest.testHandleDocumentChangeDoesNotBlockOnRepositoryCheck` | `C5.1` | The repository check never blocks the editing thread. |
| `VcsChangeListenerTest.testDocumentEditsAloneRequestEditOnlyRefreshWhileVcsEventsRequestFullRefresh` | `C5.1` | A burst of edits alone requests an edit-only refresh; a VCS event in the burst makes it a full refresh. |
| `VcsChangeListenerTest.testVcsEventSurvivesABurstWhileRepositoryCheckIsBusy` | `C5.1` | A VCS event remains a full-refresh requirement amid a large edit burst while file resolution is blocked. |
| `VcsChangeListenerTest.testDocumentSaveSurvivesABurstWhileRepositoryCheckIsBusy` | `C5.1` | A save's full-refresh requirement survives later edits while file resolution is blocked. |
| `VcsChangeListenerTest.testRepositoryEditSurvivesABurstOfForeignDocumentEdits` | `C5.1` | Events from other projects cannot discard a pending repository-file edit. |
| `VcsChangeListenerTest.testForeignDocumentSaveDoesNotForceAFullRefresh` | `C5.1` | An unrelated project's save does not turn this project's edit-only refresh into a disk reload. |
| `VisualTrackerManagerBehaviorTest.testUnderlyingTrackerReportsInsertedRangeForPartialInsertionAgainstExistingBase` | `C3.7` | A partial insertion is an inserted range. |
| `VisualTrackerManagerBehaviorTest.testUnderlyingTrackerReportsInitialInsertedRangeForWholeNewFileAgainstEmptyBase` | `C3.7` | A new file against an empty base is one inserted range. |
| `VisualTrackerManagerBehaviorTest.testStandaloneTrackerInstallsGutterHighlightersForWholeNewFile` | `C3.7`, `C4.8` | The standalone tracker for a new file draws gutter markers. |
| `VisualTrackerManagerBehaviorTest.testDisposingManagerReleasesExistingTracker` | `C3.7` | Disposal releases an initialized visual tracker, with its existence checked before disposal. |
| `VisualTrackerManagerBehaviorTest.testStandaloneTrackerIsReleasedWhenItsLastEditorCloses` | `C3.7` | A standalone tracker is released when its file's last editor closes. |
| `VisualTrackerManagerBehaviorTest.testGutterContentCancellationIsPropagated` | `C3.7`, `C5.1` | Gutter target-content reads propagate platform and coroutine cancellation instead of returning fallback content. |
| `VisualTrackerManagerBehaviorTest.testCancelledGutterLoadCanBeRetried` | `C3.7` | A cancelled gutter load clears its reservation so the same target can be loaded again. |
| `VisualTrackerManagerBehaviorTest.testSwitchingTargetsWithIdenticalTextKeepsGutterMarkers` | `C3.7` | Different commits with identical file text keep the same tracker, base and installed highlighters without a base reset. |
| `VisualTrackerManagerBehaviorTest.testSwitchingTargetsWithDifferentTextUpdatesGutterRanges` | `C3.7` | Different target text updates the base and moves the gutter range even when file classification and line counts stay the same. |
| `VisualTrackerManagerBehaviorTest.testOlderGutterRefreshCannotReplaceNewerComparison` | `C3.7`, `C5.1` | A resolved older target delivered after newer content cannot replace the current gutter base. |
| `VisualTrackerManagerBehaviorTest.testOlderDisabledGutterRefreshCannotRemoveNewerMarkers` | `C3.7`, `C4.8` | A delayed disabled-setting decision cannot release the tracker retained by a newer enabled refresh. |
| `VisualTrackerManagerBehaviorTest.testClosingEditorPreventsPendingRefreshFromCreatingTracker` | `C3.7` | A resolved refresh delivered after the last file editor closes cannot create a tracker. |
| `VisualTrackerManagerBehaviorTest.testDisposingManagerPreventsPendingRefreshFromCreatingTracker` | `C3.7` | A resolved refresh delivered after manager disposal cannot recreate a tracker. |
| `VisualTrackerManagerBehaviorTest.testGutterRefreshIgnoresEditorsOutsideFileEditorManager` | `C3.7` | A full refresh tracks an open file while skipping a preview editor for a file outside the project's file editors. |
| `VisualTrackerManagerBehaviorTest.testRefreshDuringPendingBaseLoadStillInitializesTracker` | `C3.7`, `C5.1` | A newer refresh for the same target does not strand an unfinished base-load reservation or leave the tracker uninitialized. |
| `VisualTrackerManagerBehaviorTest.testEditorSelectionKeepsPendingRefreshOfOtherOpenFiles` | `C3.7`, `C5.1` | Switching editor focus during a full refresh still updates both open files. |
| `VisualTrackerManagerBehaviorTest.testRepositoryRefreshKeepsPendingUpdatesOfOtherOpenFiles` | `C3.7`, `C5.1` | A repository event arriving during a settings refresh carries its pending updates of all open files. |
| `VisualTrackerManagerBehaviorTest.testRepositoryChangeRechecksTheTrackersOfVisibleEditors` | `C3.7` | Regression: a repository change re-checks the trackers of visible editors even when the diff data is unchanged. |
| `VisualTrackerManagerBehaviorTest.testIncludeHeadToggleRechecksTrackers` | `C4.7`, `C3.7` | Regression: toggling `Include HEAD in scopes` re-checks the trackers at once. |
| `VisualTrackerManagerBehaviorTest.testGutterToggleFromAnotherProjectRechecksThisProjectsTrackers` | `C4.8`, `C4.9` | Regression: a gutter setting changed outside this project's menu re-checks this project's trackers. |

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
| `LstCrcBranchComparisonUiTest.testClosingSelectedTabActivatesTheTabBeforeIt` | `C1.4` | Closing the selected tab from its context menu activates the tab before it (tree, active diff, scopes), and closing the first tab activates `HEAD`. |
| `LstCrcBranchComparisonUiTest.testTreeStatePersistsAcrossTabSwitches` | `C3.9` | Expand/collapse state survives tab switches. |
| `LstCrcBranchComparisonUiTest.testNewFileInCollapsedDirExpandsDirWhenSettingEnabled` | `C3.9` | A collapsed folder opens for a new change when the setting is on. |
| `LstCrcBranchComparisonUiTest.testNewFileInCollapsedDirStaysCollapsedWhenSettingDisabled` | `C3.9` | It stays collapsed when the setting is off. |
| `LstCrcBranchComparisonUiTest.testUntrackedFileAppearsWhenSettingEnabled` | `C3.11` | Untracked files appear when the setting is on. |
| `LstCrcBranchComparisonUiTest.testUntrackedFileStaysHiddenWhenSettingDisabled` | `C3.11` | Untracked files stay hidden when the setting is off. |
| `LstCrcBranchComparisonUiTest.testUntrackedFileHasUnknownFileStatus` | `C3.11` | Untracked files have the `UNKNOWN` status; tracked additions are `ADDED`. |
| `LstCrcBranchComparisonUiTest.testFileTypeFileStatuses` | `C3.1` | Rows carry `ADDED`, `DELETED` and `MODIFIED` statuses relative to the working tree. |
| `LstCrcFileScopeUiTest.testFileOperations` | `C3.1`, `C3.2` | Create, modify, rename and delete update the tree and every named scope. |
| `LstCrcInteractionUiTest.testToolWindowClickActions` | `C4.1` | Configured click actions open source, diff or project view. |
| `LstCrcInteractionUiTest.testModifierClickBuildsAMultiSelectionThatARightClickKeeps` | `C4.1` | Regression: Ctrl/Cmd+click builds a multi-selection in the real IDE, and a right-click inside it keeps it. |
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

package com.github.uiopak.lstcrc.starter.remote

import com.intellij.driver.client.Remote

@Remote("com.github.uiopak.lstcrc.testing.LstCrcUiTestBridge", plugin = "com.github.uiopak.lstcrc")
interface LstCrcUiTestBridgeRemote {
    fun isDumbMode(): Boolean
    fun isGitVcsActive(): Boolean
    fun activateGitVcsIntegration()
    fun activateGitVcsIntegrationFor(relativePath: String)
    fun knownGitRepositoriesSnapshot(): String
    fun refreshProjectAfterExternalChange()
    fun openGitChangesView()
    fun openBranchSelectionTab()
    fun resetGitChangesViewState()
    fun createAndSelectTab(branchName: String)
    fun selectTab(tabName: String)
    fun hasTab(tabName: String): Boolean
    fun selectedTabName(): String
    fun selectedRenderedRowsSnapshot(): String
    fun selectedChangesTreeSnapshot(): String
    fun createRevisionTab(revision: String, alias: String?)
    fun updateTabAlias(branchName: String, newAlias: String?)
    fun setBranchAsRepoComparison(branchName: String)
    fun setRevisionAsRepoComparison(revision: String)
    fun branchErrorNotificationsSnapshot(): String
    fun triggerBranchErrorNotificationAction(actionText: String)
    fun selectStatusWidgetEntry(displayName: String)
    fun statusWidgetText(): String
    fun configureClickActions(
        singleClickAction: String?,
        doubleClickAction: String?,
        middleClickAction: String?,
        doubleMiddleClickAction: String?,
        rightClickAction: String?,
        doubleRightClickAction: String?,
        showContextMenu: Boolean?
    )
    fun clickSettingsSnapshot(): String
    fun setDoubleClickDelayMs(delay: Int)
    fun triggerConfiguredChangeInteraction(fileName: String, button: String, clickCount: Int)
    fun contextMenuActionsForFile(fileName: String): String
    fun selectedEditorDescriptor(): String
    fun hasDiffEditorOpen(): Boolean
    fun closeAllEditors()
    fun openFile(relativePath: String)
    fun writeProjectFile(relativePath: String, content: String)
    fun renameProjectFile(oldPath: String, newPath: String)
    fun deleteProjectFile(relativePath: String)
    fun setShowWidgetContext(show: Boolean)
    fun setShowToolWindowTitle(show: Boolean)
    fun isToolWindowTitleVisible(): Boolean
    fun setIncludeHeadInScopes(include: Boolean)
    fun setGutterSettings(enableMarkers: Boolean?, enableForNewFiles: Boolean?)
    fun setTreeContextSettings(showSingleRepo: Boolean?, showCommits: Boolean?)
        fun setMultiRepoTreeContextSetting(show: Boolean)
        fun isMultiRepoTreeContextEnabled(): Boolean
    fun treeContextSettingsSnapshot(): String
    fun gutterSettingsSnapshot(): String
    fun selectedTabComparisonMap(): String
    fun scopeContains(scopeId: String, relativePath: String): Boolean
    fun scopeExists(scopeId: String): Boolean
    fun searchScopesSnapshot(): String
    fun searchScopeContains(displayName: String, relativePath: String): Boolean
    fun branchSelectionTabBranchesSnapshot(): String
    fun openFindInFilesDialog()
    fun findDialogScopeOptionsSnapshot(): String
    fun dismissTransientUi()
    fun openRepoComparisonDialog()
    fun visibleRepoComparisonDialogTitle(): String
    fun visibleRepoComparisonDialogBranchesSnapshot(): String
    fun selectBranchInVisibleRepoComparisonDialog(branchName: String)
    fun setRepoComparisonForRoot(relativePath: String, targetRevision: String)
    fun selectedTreeFileColor(fileName: String): String
    fun deletedScopeColorSnapshot(): String
    fun visualGutterSummaryForSelectedEditor(): String
}
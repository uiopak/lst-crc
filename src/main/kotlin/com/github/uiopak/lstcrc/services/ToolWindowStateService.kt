package com.github.uiopak.lstcrc.services

import com.github.uiopak.lstcrc.state.TabInfo
import com.github.uiopak.lstcrc.state.ToolWindowState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.github.uiopak.lstcrc.toolWindow.ChangesTreePanel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.components.service
import javax.swing.Timer

@State(
    name = "com.github.uiopak.lstcrc.services.ToolWindowStateService",
    storages = [Storage("gitTabsIdeaPluginState.xml")]
)
class ToolWindowStateService(private val project: Project) : PersistentStateComponent<ToolWindowState> {

    private var myState = ToolWindowState()
    private val logger = thisLogger()

    private var refreshDebounceTimer: Timer? = null
    private var pendingRefreshBranchName: String? = null

    companion object {
        private const val HEAD_BRANCH_NAME = "HEAD"
        private const val REFRESH_DEBOUNCE_DELAY_MS = 300

        fun getInstance(project: Project): ToolWindowStateService {
            return project.getService(ToolWindowStateService::class.java)
        }
    }

    override fun getState(): ToolWindowState {
        logger.trace("getState() called. Current state: $myState")
        return myState
    }

    override fun loadState(state: ToolWindowState) {
        logger.info("loadState() called. Loading state: $state")
        XmlSerializerUtil.copyBean(state, myState)
    }

    override fun noStateLoaded() {
        logger.info("noStateLoaded() called. Initializing with default state.")
        myState = ToolWindowState() // Ensure it's a clean state
    }

    fun addTab(branchName: String) {
        logger.info("addTab('$branchName') called.")
        val currentTabs = myState.openTabs.toMutableList()
        if (currentTabs.none { it.branchName == branchName }) {
            currentTabs.add(TabInfo(branchName))
            myState.openTabs = currentTabs
            myState = myState.copy()
            logger.info("Tab '$branchName' added. New state: $myState")
        } else {
            logger.info("Tab $branchName already exists.")
        }
    }

    fun removeTab(branchName: String) {
        logger.info("removeTab($branchName) called.")
        val currentTabs = myState.openTabs.toMutableList()
        val removed = currentTabs.removeAll { it.branchName == branchName }
        if (removed) {
            myState.openTabs = currentTabs // This modification should be detected
            myState = myState.copy()      // Explicitly create a new instance
            logger.info("Tab $branchName removed. New state: $myState")
            // Consider adjusting selectedTabIndex here if necessary, though selectionChanged should also handle it
        } else {
            logger.info("Tab $branchName not found for removal.")
        }
    }

    fun setSelectedTab(index: Int) {
        logger.debug("setSelectedTab called with index: $index. Current state selectedTabIndex: ${myState.selectedTabIndex}")

        // Cancel any pending debounced refresh before changing the tab
        refreshDebounceTimer?.stop()
        pendingRefreshBranchName = null
        logger.debug("Debounced refresh timer stopped and pending branch name cleared due to tab selection change.")

        if (myState.selectedTabIndex != index) {
            myState.selectedTabIndex = index
            myState = myState.copy()
            logger.info("Selected tab index set to $index. New state: $myState")

            val newSelectedBranchName = if (index == -1) HEAD_BRANCH_NAME else getSelectedTabBranchName()

            if (newSelectedBranchName != null) {
                logger.info("Tab selection changed to: '$newSelectedBranchName'. Fetching its changes directly.")
                fetchAndDisplayChangesForBranch(newSelectedBranchName)
            } else {
                // This case should ideally not happen if index is valid or -1.
                // If index is invalid (e.g. out of bounds for actual tabs after HEAD),
                // getSelectedTabBranchName() would return null.
                logger.warn("No branch name determined for selected index $index. Clearing active diff.")
                project.service<ProjectActiveDiffDataService>().clearActiveDiff()
                getActiveChangesTreePanel(project)?.displayChanges(null, HEAD_BRANCH_NAME) // Show empty/error in HEAD panel
            }
        } else {
            logger.debug("Selected tab index $index is already set. No data fetch or UI update initiated from setSelectedTab, but pending refresh was cancelled.")
        }
    }

    fun getSelectedTabBranchName(): String? {
        if (myState.selectedTabIndex >= 0 && myState.selectedTabIndex < myState.openTabs.size) {
            return myState.openTabs[myState.selectedTabIndex].branchName
        }
        return null
    }

    fun requestRefreshForBranch(branchName: String) {
        logger.debug("requestRefreshForBranch('$branchName') called.")
        val currentSelectedBranch = if (myState.selectedTabIndex == -1) HEAD_BRANCH_NAME else getSelectedTabBranchName()

        if (branchName == currentSelectedBranch) {
            logger.debug("Request to refresh branch '$branchName' matches current active tab. Debouncing refresh.")
            pendingRefreshBranchName = branchName
            refreshDebounceTimer?.stop() // Stop any existing timer
            refreshDebounceTimer = Timer(REFRESH_DEBOUNCE_DELAY_MS) {
                pendingRefreshBranchName?.let {
                    logger.info("Debounced timer fired for branch '$it'. Fetching and displaying changes.")
                    fetchAndDisplayChangesForBranch(it)
                }
                pendingRefreshBranchName = null // Clear after fetching (or attempting to)
            }.apply {
                isRepeats = false
                start()
            }
            logger.debug("Debounce timer (re)started for branch '$branchName'.")
        } else {
            logger.debug("Request to refresh branch '$branchName' does not match current active tab ('$currentSelectedBranch'). No refresh initiated.")
        }
    }

    private fun fetchAndDisplayChangesForBranch(branchName: String) {
        logger.info("fetchAndDisplayChangesForBranch called for branch: $branchName")
        val activePanel = getActiveChangesTreePanel(project)
        // It's generally safe to call showLoadingStateAndPrepareForData unconditionally.
        // The panel itself can ensure it doesn't do redundant work if it's already loading.
        activePanel?.showLoadingStateAndPrepareForData()

        val gitService = project.service<GitService>()
        val diffDataService = project.service<ProjectActiveDiffDataService>()

        gitService.getChanges(branchName).whenCompleteAsync { categorizedChanges, throwable ->
            ApplicationManager.getApplication().invokeLater { // Ensure UI updates are on EDT
                logger.debug("getChanges for '$branchName' completed. Error: ${throwable != null}, Changes count: ${categorizedChanges?.allChanges?.size ?: "null"}")
                if (throwable != null) {
                    logger.error("Error fetching changes for $branchName: ${throwable.message}", throwable)
                    // Optionally clear active diff or set an error state in ProjectActiveDiffDataService
                    if (branchName == (if (myState.selectedTabIndex == -1) HEAD_BRANCH_NAME else getSelectedTabBranchName())) {
                         diffDataService.clearActiveDiff()
                    }
                    activePanel?.displayChanges(null, branchName) // Show error/empty state
                } else if (categorizedChanges != null) {
                    logger.debug("Successfully fetched ${categorizedChanges.allChanges.size} total changes for '$branchName'.")
                     // Update ProjectActiveDiffDataService only if the fetched branch is still the active one
                    if (branchName == (if (myState.selectedTabIndex == -1) HEAD_BRANCH_NAME else getSelectedTabBranchName())) {
                        logger.debug("Updating ProjectActiveDiffDataService for active branch '$branchName'.")
                        diffDataService.updateActiveDiff(
                            branchName,
                            categorizedChanges.allChanges,
                            categorizedChanges.createdFiles,
                            categorizedChanges.modifiedFiles,
                            categorizedChanges.movedFiles
                        )
                    } else {
                        logger.debug("Fetched data for '$branchName', but it's no longer the active tab. ProjectActiveDiffDataService not updated.")
                    }
                    activePanel?.displayChanges(categorizedChanges, branchName)
                } else { // categorizedChanges is null and throwable is null
                    logger.warn("Fetched changes for '$branchName' but CategorizedChanges object was null (and no error).")
                    if (branchName == (if (myState.selectedTabIndex == -1) HEAD_BRANCH_NAME else getSelectedTabBranchName())) {
                        diffDataService.clearActiveDiff() // Clear if no data for active tab
                    }
                    activePanel?.displayChanges(null, branchName) // Show empty/error state
                }
            }
        }
    }

    private fun getActiveChangesTreePanel(project: Project): ChangesTreePanel? {
        // The Tool Window ID, ensure this matches the ID in plugin.xml
        val toolWindowId = "GitChangesView" // Corrected ID for the tool window.
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow(toolWindowId) ?: run {
            logger.warn("Tool window with ID '$toolWindowId' not found.")
            return null
        }

        val selectedContent = toolWindow.contentManager.selectedContent ?: run {
            logger.warn("No selected content in tool window '$toolWindowId'.")
            return null
        }

        return (selectedContent.component as? ChangesTreePanel)?.also {
            logger.debug("Active ChangesTreePanel found for display name: ${selectedContent.displayName}, component: ${it.javaClass.simpleName}")
        } ?: run {
            logger.warn("Selected content's component is not a ChangesTreePanel. Display name: ${selectedContent.displayName}, Component type: ${selectedContent.component?.javaClass?.name}")
            null
        }
    }

    // refreshDataForActiveTabIfMatching is removed
}

package com.github.uiopak.lstcrc.services

import com.github.uiopak.lstcrc.state.TabInfo
import com.github.uiopak.lstcrc.state.ToolWindowState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.github.uiopak.lstcrc.toolWindow.ChangesTreePanel
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.messages.Topic
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.components.service
import java.util.EventListener // Required for MessageBus Senders

@State(
    name = "com.github.uiopak.lstcrc.services.ToolWindowStateService",
    storages = [Storage("gitTabsIdeaPluginState.xml")]
)
class ToolWindowStateService(private val project: Project) : PersistentStateComponent<ToolWindowState> {

    private var myState = ToolWindowState()
    private val logger = thisLogger() // Initialize logger

    override fun getState(): ToolWindowState {
        logger.debug("getState() called. Current state: $myState")
        return myState
    }

    override fun loadState(state: ToolWindowState) {
        logger.info("loadState() called. Loading state: $state")
        XmlSerializerUtil.copyBean(state, myState)
        project.messageBus.syncPublisher(TOPIC).stateChanged(myState.copy())
    }

    override fun noStateLoaded() {
        logger.info("noStateLoaded() called. Initializing with default state.")
        myState = ToolWindowState() // Ensure it's a clean state
        project.messageBus.syncPublisher(TOPIC).stateChanged(myState.copy())
    }

    fun addTab(branchName: String) {
        logger.info("addTab('$branchName') called.")
        val currentTabs = myState.openTabs.toMutableList()
        if (currentTabs.none { it.branchName == branchName }) {
            currentTabs.add(TabInfo(branchName = branchName, alias = null)) // Be explicit about alias
            myState.openTabs = ArrayList(currentTabs) // Ensure a new list instance for the state
            // myState.selectedTabIndex remains unchanged or could be set to the new tab's index
            myState = myState.copy() // Create a new state object
            logger.info("Tab '$branchName' added. New state: $myState")
            project.messageBus.syncPublisher(TOPIC).stateChanged(myState.copy())
        } else {
            logger.info("Tab $branchName already exists.")
        }
    }

    fun removeTab(branchName: String) {
        logger.info("removeTab($branchName) called.")
        val currentTabs = myState.openTabs.toMutableList()
        val initialSize = currentTabs.size
        val removed = currentTabs.removeAll { it.branchName == branchName }

        if (removed) {
            myState.openTabs = ArrayList(currentTabs) // Ensure a new list instance

            // Adjust selectedTabIndex if the removed tab affects it
            if (myState.selectedTabIndex >= currentTabs.size && currentTabs.isNotEmpty()) {
                myState.selectedTabIndex = currentTabs.size - 1
            } else if (currentTabs.isEmpty()) {
                myState.selectedTabIndex = -1
            }
            // If the removed tab was before or at the selected index, and that index is now out of bounds or points to a different element.
            // This logic might need to be more sophisticated depending on desired behavior post-removal.
            // For now, if selected index is now invalid, it might be better to set to -1 or last element.
            // The simple adjustment above handles shrinking from the end.

            myState = myState.copy() // Create a new state object
            logger.info("Tab $branchName removed. New state: $myState")
            project.messageBus.syncPublisher(TOPIC).stateChanged(myState.copy())
            // Consider adjusting selectedTabIndex here if necessary, though selectionChanged should also handle it
        } else {
            logger.info("Tab $branchName not found for removal.")
        }
    }

    fun setSelectedTab(index: Int) {
        logger.debug("setSelectedTab called with index: $index. Current state selectedTabIndex: ${myState.selectedTabIndex}")
        // Validate index against current openTabs size
        val validIndex = if (index >= myState.openTabs.size || index < -1) {
            logger.warn("setSelectedTab called with invalid index: $index. Open tabs: ${myState.openTabs.size}. Clamping to -1 or last valid index.")
            if (myState.openTabs.isEmpty()) -1 else myState.openTabs.size -1
        } else {
            index
        }

        if (myState.selectedTabIndex != validIndex) {
            myState.selectedTabIndex = validIndex
            myState = myState.copy() // Ensure state is copied if it's a data class
            logger.info("Selected tab index set to $validIndex. New state: $myState")
            project.messageBus.syncPublisher(TOPIC).stateChanged(myState.copy())

            // New logic to update ProjectActiveDiffDataService
            val diffDataService = project.service<ProjectActiveDiffDataService>()
            val gitService = project.service<GitService>()

            val selectedBranchName = getSelectedTabBranchName() // Uses the new index due to state update

            if (selectedBranchName != null) {
                logger.info("Tool window tab selection changed to: '$selectedBranchName'. Fetching its changes.")
                gitService.getChanges(selectedBranchName).whenCompleteAsync { categorizedChanges, throwable ->
                    logger.debug("getChanges for '$selectedBranchName' completed. Error: ${throwable != null}, Changes count: ${categorizedChanges?.allChanges?.size ?: "null"}")
                    if (throwable != null) {
                        logger.error("Error for $selectedBranchName: ${throwable.message}")
                        diffDataService.clearActiveDiff() // Keep clearing on error
                    } else if (categorizedChanges != null) {
                        logger.debug("Successfully fetched ${categorizedChanges.allChanges.size} total changes for '$selectedBranchName'. Calling diffDataService.updateActiveDiff.")
                        diffDataService.updateActiveDiff(
                            selectedBranchName,
                            categorizedChanges.allChanges,
                            categorizedChanges.createdFiles,
                            categorizedChanges.modifiedFiles,
                            categorizedChanges.movedFiles
                        )
                        // Update the UI panel
                        getActiveChangesTreePanel(project)?.displayChanges(categorizedChanges, selectedBranchName)
                            ?: logger.warn("No active ChangesTreePanel found after fetching changes for '$selectedBranchName'. UI might not update.")
                    } else { // categorizedChanges is null and throwable is null
                        logger.warn("Fetched changes for '$selectedBranchName' but CategorizedChanges object was null. Calling diffDataService.clearActiveDiff.")
                        diffDataService.clearActiveDiff() // Keep clearing if no data
                        // Update the UI panel to show no data / error state
                        getActiveChangesTreePanel(project)?.displayChanges(null, selectedBranchName)
                            ?: logger.warn("No active ChangesTreePanel found for '$selectedBranchName' (no data/error case). UI might not update.")
                    }
                }
            } else { // No specific branch tab is selected (i.e., HEAD is active)
                logger.info("Tool window tab selection changed to HEAD. Clearing ProjectActiveDiffDataService to disable custom scopes.")
                diffDataService.clearActiveDiff() // This is the key change.

                // We still need to fetch and display changes for HEAD in the tool window panel.
                val effectiveBranchNameForDisplay = "HEAD"
                logger.debug("Fetching changes for '$effectiveBranchNameForDisplay' to display in the tool window panel.")
                gitService.getChanges(effectiveBranchNameForDisplay).whenCompleteAsync { categorizedChanges, throwable ->
                    val activePanel = getActiveChangesTreePanel(project)
                    if (throwable != null) {
                        logger.error("Error loading changes for '$effectiveBranchNameForDisplay': ${throwable.message}")
                        activePanel?.displayChanges(null, effectiveBranchNameForDisplay)
                    } else {
                        // Display the changes, but do NOT update the ProjectActiveDiffDataService
                        logger.debug("Successfully fetched ${categorizedChanges?.allChanges?.size ?: "null"} total changes for '$effectiveBranchNameForDisplay'. Displaying in panel.")
                        activePanel?.displayChanges(categorizedChanges, effectiveBranchNameForDisplay)
                    }
                }
            }

        } else {
            // Use validIndex here as index might be the original, potentially invalid one.
            logger.debug("Selected tab index $validIndex is already set. No data fetch or UI update initiated from setSelectedTab.")
            // Even if index is the same, if branch name could have changed (e.g. list reordered without index change),
            // we might still want to refresh. However, current logic is fine if index directly maps to a stable tab order.
            // Consider if a refresh is needed even if index is same, e.g. by comparing branch name.
            // For now, if index is same, assume no change needed for active diff.
        }
    }

    /**
     * Explicitly broadcasts the current state to all listeners on the message bus.
     * This is useful after initialization to ensure all components are synchronized with the initial state.
     */
    fun broadcastCurrentState() {
        logger.info("Broadcasting current state explicitly to all listeners.")
        project.messageBus.syncPublisher(TOPIC).stateChanged(myState.copy())
    }

    fun getSelectedTabBranchName(): String? {
        // This is a read-only operation, no logging needed unless for specific debugging
        if (myState.selectedTabIndex >= 0 && myState.selectedTabIndex < myState.openTabs.size) {
            return myState.openTabs[myState.selectedTabIndex].branchName
        }
        return null
    }

    fun refreshDataForActiveTabIfMatching(eventBranchName: String) {
        logger.debug("refreshDataForActiveTabIfMatching called with eventBranchName: $eventBranchName")
        val currentSelectedBranch = getSelectedTabBranchName()
        val isHeadSelected = currentSelectedBranch == null

        // Determine if the refresh request matches the active UI state.
        // It should refresh if:
        // 1. The event is for HEAD and the HEAD tab is selected.
        // 2. The event is for a specific branch and that branch tab is selected.
        val shouldRefresh = (isHeadSelected && eventBranchName == "HEAD") ||
                (!isHeadSelected && eventBranchName == currentSelectedBranch)

        if (shouldRefresh) {
            logger.debug("Event for '$eventBranchName' matches current active tab. Refreshing data.")

            val gitService = project.service<GitService>()
            val diffDataService = project.service<ProjectActiveDiffDataService>()

            gitService.getChanges(eventBranchName).whenCompleteAsync { categorizedChanges, throwable ->
                val activePanel = getActiveChangesTreePanel(project)
                if (throwable != null) {
                    logger.error("Error refreshing data for active tab '$eventBranchName': ${throwable.message}", throwable)
                    activePanel?.displayChanges(null, eventBranchName)
                } else if (categorizedChanges != null) {
                    logger.debug("Successfully refreshed data for active tab '$eventBranchName'. ${categorizedChanges.allChanges.size} changes.")

                    // Only update the global diff service if we are on a specific branch tab, NOT HEAD.
                    if (!isHeadSelected) {
                        diffDataService.updateActiveDiff(
                            eventBranchName,
                            categorizedChanges.allChanges,
                            categorizedChanges.createdFiles,
                            categorizedChanges.modifiedFiles,
                            categorizedChanges.movedFiles
                        )
                    }

                    // Always update the UI panel itself.
                    activePanel?.displayChanges(categorizedChanges, eventBranchName)
                        ?: logger.warn("No active ChangesTreePanel found after refreshing data for '$eventBranchName'. UI might not update.")
                } else { // categorizedChanges is null and throwable is null
                    logger.warn("Refreshed data for '$eventBranchName', but CategorizedChanges was null (and no error). Updating panel to reflect no/error state.")
                    if (!isHeadSelected) {
                        diffDataService.clearActiveDiff()
                    }
                    activePanel?.displayChanges(null, eventBranchName)
                }
            }
        } else {
            logger.debug("Event branch '$eventBranchName' does not match current active tab state (selected branch: '$currentSelectedBranch'). No refresh initiated from refreshDataForActiveTabIfMatching.")
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

    fun updateTabAlias(branchName: String, newAlias: String?) {
        logger.info("updateTabAlias called for branch '$branchName' with new alias '$newAlias'.")
        val tabIndex = myState.openTabs.indexOfFirst { it.branchName == branchName }

        if (tabIndex != -1) {
            val updatedTabs = myState.openTabs.toMutableList()
            val oldTabInfo = updatedTabs[tabIndex]

            // Only update if alias is actually different to avoid unnecessary state changes and broadcasts.
            if (oldTabInfo.alias != newAlias) {
                updatedTabs[tabIndex] = oldTabInfo.copy(alias = newAlias)
                myState.openTabs = ArrayList(updatedTabs) // Ensure a new list instance for the state
                myState = myState.copy() // Create a new state object
                logger.info("Tab alias for '$branchName' updated. New state: $myState")
                project.messageBus.syncPublisher(TOPIC).stateChanged(myState.copy())
            } else {
                logger.debug("Alias for '$branchName' is already '$newAlias'. No state change needed.")
            }
        } else {
            logger.warn("Could not find tab for branch '$branchName' to update its alias.")
        }
    }

    companion object {
        interface ToolWindowStateListener : EventListener {
            fun stateChanged(newState: ToolWindowState)
        }

        val TOPIC = Topic.create("LST-CRC ToolWindow State Changed", ToolWindowStateListener::class.java)

        fun getInstance(project: Project): ToolWindowStateService {
            return project.getService(ToolWindowStateService::class.java)
        }
    }
}
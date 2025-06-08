package com.github.uiopak.lstcrc.services

import com.github.uiopak.lstcrc.state.TabInfo
import com.github.uiopak.lstcrc.state.ToolWindowState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.github.uiopak.lstcrc.toolWindow.ChangesTreePanel
import com.github.uiopak.lstcrc.toolWindow.ToolWindowSettingsProvider
import com.intellij.ide.util.PropertiesComponent
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
        logger.warn("LSTCRC_TRACE: setSelectedTab called with index: $index.")
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
            logger.warn("LSTCRC_TRACE: setSelectedTab - selectedBranchName from state is now: '$selectedBranchName'.")

            if (selectedBranchName != null) {
                logger.warn("LSTCRC_TRACE: setSelectedTab - Path for Specific Branch: '$selectedBranchName'. Fetching changes.")
                gitService.getChanges(selectedBranchName).whenCompleteAsync { categorizedChanges, throwable ->
                    if (project.isDisposed) return@whenCompleteAsync
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
                logger.warn("LSTCRC_TRACE: setSelectedTab - Path for HEAD tab (selectedBranchName is null).")

                val properties = PropertiesComponent.getInstance(project)
                val includeHeadInScopes = properties.getBoolean(
                    ToolWindowSettingsProvider.APP_INCLUDE_HEAD_IN_SCOPES_KEY,
                    ToolWindowSettingsProvider.DEFAULT_INCLUDE_HEAD_IN_SCOPES
                )
                val effectiveBranchNameForDisplay = "HEAD"
                logger.warn("LSTCRC_TRACE: setSelectedTab - 'Include HEAD in Scopes' setting is: $includeHeadInScopes.")

                if (includeHeadInScopes) {
                    logger.warn("LSTCRC_TRACE: setSelectedTab - Setting is ON. Fetching HEAD changes to update service.")
                    gitService.getChanges(effectiveBranchNameForDisplay).whenCompleteAsync { categorizedChanges, throwable ->
                        val activePanel = getActiveChangesTreePanel(project)
                        if (project.isDisposed) return@whenCompleteAsync

                        if (throwable != null) {
                            logger.error("Error loading changes for '$effectiveBranchNameForDisplay': ${throwable.message}")
                            diffDataService.clearActiveDiff() // Clear service on error
                            activePanel?.displayChanges(null, effectiveBranchNameForDisplay)
                        } else if (categorizedChanges != null) {
                            logger.debug("Successfully fetched ${categorizedChanges.allChanges.size} total changes for '$effectiveBranchNameForDisplay'. Updating ProjectActiveDiffDataService and displaying in panel.")
                            diffDataService.updateActiveDiff(
                                effectiveBranchNameForDisplay, // use "HEAD" as the branch name
                                categorizedChanges.allChanges,
                                categorizedChanges.createdFiles,
                                categorizedChanges.modifiedFiles,
                                categorizedChanges.movedFiles
                            )
                            activePanel?.displayChanges(categorizedChanges, effectiveBranchNameForDisplay)
                        } else {
                            logger.warn("Fetched changes for '$effectiveBranchNameForDisplay' but CategorizedChanges object was null. Clearing service.")
                            diffDataService.clearActiveDiff()
                            activePanel?.displayChanges(null, effectiveBranchNameForDisplay)
                        }
                    }
                } else {
                    logger.warn("LSTCRC_TRACE: setSelectedTab - Setting is OFF. Clearing active diff data.")
                    diffDataService.clearActiveDiff() // This is the original behavior.

                    // We still need to fetch and display changes for HEAD in the tool window panel, just not in the service.
                    logger.debug("Fetching changes for '$effectiveBranchNameForDisplay' to display in the tool window panel.")
                    gitService.getChanges(effectiveBranchNameForDisplay).whenCompleteAsync { categorizedChanges, throwable ->
                        if (project.isDisposed) return@whenCompleteAsync
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
            }

        } else {
            logger.warn("LSTCRC_TRACE: setSelectedTab - selected index $validIndex is already set. No action taken.")
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
        logger.warn("LSTCRC_TRACE: refreshDataForActiveTabIfMatching called with eventBranchName: $eventBranchName")
        val currentSelectedBranch = getSelectedTabBranchName()
        val isHeadSelected = currentSelectedBranch == null

        logger.warn("LSTCRC_TRACE: refreshDataForActiveTabIfMatching - currentSelectedBranch: '$currentSelectedBranch', isHeadSelected: $isHeadSelected")


        // Determine if the refresh request matches the active UI state.
        // It should refresh if:
        // 1. The event is for HEAD and the HEAD tab is selected.
        // 2. The event is for a specific branch and that branch tab is selected.
        val shouldRefresh = (isHeadSelected && eventBranchName == "HEAD") ||
                (!isHeadSelected && eventBranchName == currentSelectedBranch)

        if (shouldRefresh) {
            logger.warn("LSTCRC_TRACE: refreshDataForActiveTabIfMatching - Refresh condition MET. Proceeding.")

            val gitService = project.service<GitService>()
            val diffDataService = project.service<ProjectActiveDiffDataService>()

            gitService.getChanges(eventBranchName).whenCompleteAsync { categorizedChanges, throwable ->
                if (project.isDisposed) return@whenCompleteAsync

                val activePanel = getActiveChangesTreePanel(project)
                val properties = PropertiesComponent.getInstance(project)
                val includeHeadInScopes = properties.getBoolean(
                    ToolWindowSettingsProvider.APP_INCLUDE_HEAD_IN_SCOPES_KEY,
                    ToolWindowSettingsProvider.DEFAULT_INCLUDE_HEAD_IN_SCOPES
                )
                logger.warn("LSTCRC_TRACE: refreshDataForActiveTabIfMatching - Callback executing. 'Include HEAD in Scopes' setting is: $includeHeadInScopes.")


                if (throwable != null) {
                    logger.error("Error refreshing data for active tab '$eventBranchName': ${throwable.message}", throwable)
                    activePanel?.displayChanges(null, eventBranchName)
                    diffDataService.clearActiveDiff() // Clear service on error
                } else if (categorizedChanges != null) {
                    logger.debug("Successfully refreshed data for active tab '$eventBranchName'. ${categorizedChanges.allChanges.size} changes.")

                    // Update global diff service if on a branch tab, OR on HEAD tab with setting enabled.
                    if (!isHeadSelected || (isHeadSelected && includeHeadInScopes)) {
                        logger.warn("LSTCRC_TRACE: refreshDataForActiveTabIfMatching - DECISION: Updating ProjectActiveDiffDataService.")
                        diffDataService.updateActiveDiff(
                            eventBranchName,
                            categorizedChanges.allChanges,
                            categorizedChanges.createdFiles,
                            categorizedChanges.modifiedFiles,
                            categorizedChanges.movedFiles
                        )
                    } else {
                        // This case happens if we are on HEAD and the setting is OFF.
                        logger.warn("LSTCRC_TRACE: refreshDataForActiveTabIfMatching - DECISION: Clearing ProjectActiveDiffDataService because HEAD is selected but setting is OFF.")
                        diffDataService.clearActiveDiff()
                    }

                    // Always update the UI panel itself.
                    activePanel?.displayChanges(categorizedChanges, eventBranchName)
                        ?: logger.warn("No active ChangesTreePanel found after refreshing data for '$eventBranchName'. UI might not update.")
                } else { // categorizedChanges is null and throwable is null
                    logger.warn("Refreshed data for '$eventBranchName', but CategorizedChanges was null (and no error). Updating panel to reflect no/error state.")
                    diffDataService.clearActiveDiff()
                    activePanel?.displayChanges(null, eventBranchName)
                }
            }
        } else {
            logger.warn("LSTCRC_TRACE: refreshDataForActiveTabIfMatching - Refresh condition NOT MET. No action taken.")
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
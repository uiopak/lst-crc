package com.github.uiopak.lstcrc.services

// Imports will be consolidated here by the IDE or manually cleaned if necessary
// For the purpose of this operation, we list the unique necessary imports.
import com.github.uiopak.lstcrc.state.TabInfo
import com.github.uiopak.lstcrc.state.ToolWindowState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.components.service // For project.service<>()
// java.util.concurrent.CompletableFuture is not directly used here but through GitService.getChanges().
// If GitService methods used in this file require its types in this file's signature, it would be needed.
// However, whenCompleteAsync is a method on CompletableFuture itself, so the type is inferred.
// Let's keep it for clarity if complex lambda signatures were to be used, though likely not strictly needed here now.
import java.util.concurrent.CompletableFuture

@State(
    name = "com.github.uiopak.lstcrc.services.ToolWindowStateService",
    storages = [Storage("gitTabsIdeaPluginState.xml")]
)
class ToolWindowStateService(private val project: Project) : PersistentStateComponent<ToolWindowState> {

    private var myState = ToolWindowState()
    private val logger = thisLogger() // Initialize logger

    override fun getState(): ToolWindowState {
        logger.info("ToolWindowStateService: getState() called. Current state: $myState")
        return myState
    }

    override fun loadState(state: ToolWindowState) {
        logger.info("ToolWindowStateService: loadState() called. Loading state: $state")
        XmlSerializerUtil.copyBean(state, myState)
    }

    override fun noStateLoaded() {
        logger.info("ToolWindowStateService: noStateLoaded() called. Initializing with default state.")
        myState = ToolWindowState() // Ensure it's a clean state
    }

    fun addTab(branchName: String) {
        logger.info("ToolWindowStateService: addTab('$branchName') called.")
        val currentTabs = myState.openTabs.toMutableList()
        if (currentTabs.none { it.branchName == branchName }) {
            currentTabs.add(TabInfo(branchName))
            myState.openTabs = currentTabs
            myState = myState.copy()
            logger.info("ToolWindowStateService: Tab '$branchName' added. New state: $myState")
        } else {
            logger.info("ToolWindowStateService: Tab $branchName already exists.") // This can remain .info
        }
    }

    fun removeTab(branchName: String) {
        logger.info("ToolWindowStateService: removeTab($branchName) called.")
        val currentTabs = myState.openTabs.toMutableList()
        val removed = currentTabs.removeAll { it.branchName == branchName }
        if (removed) {
            myState.openTabs = currentTabs // This modification should be detected
            myState = myState.copy()      // Explicitly create a new instance
            logger.info("ToolWindowStateService: Tab $branchName removed. New state: $myState")
            // Consider adjusting selectedTabIndex here if necessary, though selectionChanged should also handle it
        } else {
            logger.info("ToolWindowStateService: Tab $branchName not found for removal.")
        }
    }

    fun setSelectedTab(index: Int) {
        logger.info("ToolWindowStateService: setSelectedTab($index) called.")
        if (myState.selectedTabIndex != index) {
            myState.selectedTabIndex = index
            myState = myState.copy() // Ensure state is copied if it's a data class
            logger.info("ToolWindowStateService: Selected tab index set to $index. New state: $myState")

            // New logic to update ProjectActiveDiffDataService
            val diffDataService = project.service<ProjectActiveDiffDataService>()
            val gitService = project.service<GitService>()

            val selectedBranchName = getSelectedTabBranchName() // Uses the new index due to state update

            if (selectedBranchName != null) {
                logger.info("Selected tool window tab changed to: $selectedBranchName. Fetching its changes for editor tab coloring.")
                gitService.getChanges(selectedBranchName).whenCompleteAsync { changes, throwable ->
                    if (throwable != null) {
                        logger.error("Error getting changes for branch $selectedBranchName to update editor tab colors: ${throwable.message}", throwable)
                        // Optionally clear diffDataService or mark error state
                        // For now, clearing if an error occurs for the selected branch
                        diffDataService.clearActiveDiff()
                    } else if (changes != null) {
                        logger.info("Successfully fetched ${changes.size} changes for $selectedBranchName. Updating ProjectActiveDiffDataService.")
                        diffDataService.updateActiveDiff(selectedBranchName, changes)
                    } else {
                        logger.warn("Fetched changes for $selectedBranchName but the list was null. Clearing active diff for coloring.")
                        diffDataService.clearActiveDiff()
                    }
                }
            } else {
                logger.info("No branch tab selected in tool window (index: $index). Clearing active diff for editor tab coloring.")
                diffDataService.clearActiveDiff()
            }

        } else {
            logger.info("ToolWindowStateService: Selected tab index $index is already set.")
            // Even if index is the same, if branch name could have changed (e.g. list reordered without index change),
            // we might still want to refresh. However, current logic is fine if index directly maps to a stable tab order.
            // Consider if a refresh is needed even if index is same, e.g. by comparing branch name.
            // For now, if index is same, assume no change needed for active diff.
        }
    }

    fun getSelectedTabBranchName(): String? {
        // This is a read-only operation, no logging needed unless for specific debugging
        if (myState.selectedTabIndex >= 0 && myState.selectedTabIndex < myState.openTabs.size) {
            return myState.openTabs[myState.selectedTabIndex].branchName
        }
        return null
    }

    companion object {
        fun getInstance(project: Project): ToolWindowStateService {
            return project.getService(ToolWindowStateService::class.java)
        }
    }
}

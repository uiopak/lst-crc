package com.github.uiopak.lstcrc.services

import com.github.uiopak.lstcrc.state.TabInfo
import com.github.uiopak.lstcrc.state.ToolWindowState
import com.github.uiopak.lstcrc.toolWindow.ChangesTreePanel
import com.github.uiopak.lstcrc.toolWindow.ToolWindowSettingsProvider
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.messages.Topic
import com.intellij.util.xmlb.XmlSerializerUtil
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
        currentTabs.removeAll { it.branchName == branchName }

        myState.openTabs = ArrayList(currentTabs) // Ensure a new list instance

        // Adjust selectedTabIndex if the removed tab affects it
        if (myState.selectedTabIndex >= currentTabs.size && currentTabs.isNotEmpty()) {
            myState.selectedTabIndex = currentTabs.size - 1
        } else if (currentTabs.isEmpty()) {
            myState.selectedTabIndex = -1
        }

        myState = myState.copy() // Create a new state object
        logger.info("Tab $branchName removed. New state: $myState")
        project.messageBus.syncPublisher(TOPIC).stateChanged(myState.copy())
    }

    fun setSelectedTab(index: Int) {
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

            val diffDataService = project.service<ProjectActiveDiffDataService>()
            val gitService = project.service<GitService>()

            val selectedBranchName = getSelectedTabBranchName() // Uses the new index due to state update

            if (selectedBranchName != null) {
                logger.debug("Tab selection changed to: '$selectedBranchName'. Fetching changes.")
                gitService.getChanges(selectedBranchName).whenCompleteAsync { categorizedChanges, throwable ->
                    if (project.isDisposed) return@whenCompleteAsync
                    if (throwable != null) {
                        logger.error("Error for $selectedBranchName: ${throwable.message}")
                        diffDataService.clearActiveDiff()
                    } else if (categorizedChanges != null) {
                        diffDataService.updateActiveDiff(
                            selectedBranchName,
                            categorizedChanges.allChanges,
                            categorizedChanges.createdFiles,
                            categorizedChanges.modifiedFiles,
                            categorizedChanges.movedFiles
                        )
                        getActiveChangesTreePanel(project)?.displayChanges(categorizedChanges, selectedBranchName)
                    } else {
                        diffDataService.clearActiveDiff()
                        getActiveChangesTreePanel(project)?.displayChanges(null, selectedBranchName)
                    }
                }
            } else { // No specific branch tab is selected (i.e., HEAD is active)
                logger.debug("Tab selection changed to HEAD.")

                // Use application-level properties for this setting.
                val properties = PropertiesComponent.getInstance()
                val includeHeadInScopes = properties.getBoolean(
                    ToolWindowSettingsProvider.APP_INCLUDE_HEAD_IN_SCOPES_KEY,
                    ToolWindowSettingsProvider.DEFAULT_INCLUDE_HEAD_IN_SCOPES
                )
                val effectiveBranchNameForDisplay = "HEAD"

                if (includeHeadInScopes) {
                    logger.debug("'Include HEAD in Scopes' is enabled. Fetching changes for HEAD and updating ProjectActiveDiffDataService.")
                    gitService.getChanges(effectiveBranchNameForDisplay).whenCompleteAsync { categorizedChanges, throwable ->
                        if (project.isDisposed) return@whenCompleteAsync
                        val activePanel = getActiveChangesTreePanel(project)
                        if (throwable != null) {
                            logger.error("Error loading changes for '$effectiveBranchNameForDisplay': ${throwable.message}")
                            diffDataService.clearActiveDiff()
                            activePanel?.displayChanges(null, effectiveBranchNameForDisplay)
                        } else if (categorizedChanges != null) {
                            diffDataService.updateActiveDiff(
                                effectiveBranchNameForDisplay,
                                categorizedChanges.allChanges,
                                categorizedChanges.createdFiles,
                                categorizedChanges.modifiedFiles,
                                categorizedChanges.movedFiles
                            )
                            activePanel?.displayChanges(categorizedChanges, effectiveBranchNameForDisplay)
                        } else {
                            diffDataService.clearActiveDiff()
                            activePanel?.displayChanges(null, effectiveBranchNameForDisplay)
                        }
                    }
                } else {
                    logger.debug("'Include HEAD in Scopes' is disabled. Clearing ProjectActiveDiffDataService.")
                    diffDataService.clearActiveDiff()

                    // Still fetch and display changes for the UI panel.
                    gitService.getChanges(effectiveBranchNameForDisplay).whenCompleteAsync { categorizedChanges, throwable ->
                        if (project.isDisposed) return@whenCompleteAsync
                        val activePanel = getActiveChangesTreePanel(project)
                        if (throwable != null) {
                            activePanel?.displayChanges(null, effectiveBranchNameForDisplay)
                        } else {
                            activePanel?.displayChanges(categorizedChanges, effectiveBranchNameForDisplay)
                        }
                    }
                }
            }

        } else {
            logger.debug("Selected tab index $validIndex is already set. No action taken.")
        }
    }

    /**
     * Explicitly broadcasts the current state to all listeners on the message bus.
     */
    fun broadcastCurrentState() {
        logger.info("Broadcasting current state explicitly to all listeners.")
        project.messageBus.syncPublisher(TOPIC).stateChanged(myState.copy())
    }

    fun getSelectedTabBranchName(): String? {
        if (myState.selectedTabIndex >= 0 && myState.selectedTabIndex < myState.openTabs.size) {
            return myState.openTabs[myState.selectedTabIndex].branchName
        }
        return null
    }

    fun refreshDataForActiveTabIfMatching(eventBranchName: String) {
        val currentSelectedBranch = getSelectedTabBranchName()
        val isHeadSelected = currentSelectedBranch == null

        val shouldRefresh = (isHeadSelected && eventBranchName == "HEAD") ||
                (!isHeadSelected && eventBranchName == currentSelectedBranch)

        if (shouldRefresh) {
            logger.debug("Event for '$eventBranchName' matches current active tab. Refreshing data.")
            val gitService = project.service<GitService>()
            val diffDataService = project.service<ProjectActiveDiffDataService>()

            gitService.getChanges(eventBranchName).whenCompleteAsync { categorizedChanges, throwable ->
                if (project.isDisposed) return@whenCompleteAsync

                val activePanel = getActiveChangesTreePanel(project)
                // Use application-level properties for this setting.
                val properties = PropertiesComponent.getInstance()
                val includeHeadInScopes = properties.getBoolean(
                    ToolWindowSettingsProvider.APP_INCLUDE_HEAD_IN_SCOPES_KEY,
                    ToolWindowSettingsProvider.DEFAULT_INCLUDE_HEAD_IN_SCOPES
                )

                if (throwable != null) {
                    logger.error("Error refreshing data for active tab '$eventBranchName': ${throwable.message}", throwable)
                    activePanel?.displayChanges(null, eventBranchName)
                    diffDataService.clearActiveDiff()
                } else if (categorizedChanges != null) {
                    // Update global diff service if on a branch tab, OR on HEAD tab with setting enabled.
                    if (!isHeadSelected || includeHeadInScopes) {
                        diffDataService.updateActiveDiff(
                            eventBranchName,
                            categorizedChanges.allChanges,
                            categorizedChanges.createdFiles,
                            categorizedChanges.modifiedFiles,
                            categorizedChanges.movedFiles
                        )
                    } else {
                        // This case happens if we are on HEAD and the setting is OFF.
                        diffDataService.clearActiveDiff()
                    }
                    activePanel?.displayChanges(categorizedChanges, eventBranchName)
                } else {
                    diffDataService.clearActiveDiff()
                    activePanel?.displayChanges(null, eventBranchName)
                }
            }
        } else {
            logger.debug("Event branch '$eventBranchName' does not match current active tab state ('$currentSelectedBranch'). No refresh initiated.")
        }
    }

    private fun getActiveChangesTreePanel(project: Project): ChangesTreePanel? {
        val toolWindowId = "GitChangesView"
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(toolWindowId)
        val selectedContent = toolWindow?.contentManager?.selectedContent
        return selectedContent?.component as? ChangesTreePanel
    }

    fun updateTabAlias(branchName: String, newAlias: String?) {
        logger.info("updateTabAlias called for branch '$branchName' with new alias '$newAlias'.")
        val tabIndex = myState.openTabs.indexOfFirst { it.branchName == branchName }

        if (tabIndex != -1) {
            val updatedTabs = myState.openTabs.toMutableList()
            val oldTabInfo = updatedTabs[tabIndex]

            if (oldTabInfo.alias != newAlias) {
                updatedTabs[tabIndex] = oldTabInfo.copy(alias = newAlias)
                myState.openTabs = ArrayList(updatedTabs)
                myState = myState.copy()
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
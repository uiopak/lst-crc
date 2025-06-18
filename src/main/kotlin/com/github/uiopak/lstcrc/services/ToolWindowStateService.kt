package com.github.uiopak.lstcrc.services

import com.github.uiopak.lstcrc.state.TabInfo
import com.github.uiopak.lstcrc.state.ToolWindowState
import com.github.uiopak.lstcrc.toolWindow.LstCrcChangesBrowser
import com.github.uiopak.lstcrc.toolWindow.ToolWindowSettingsProvider
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.messages.Topic
import com.intellij.util.xmlb.XmlSerializerUtil
import java.util.EventListener

/**
 * Manages the tool window's UI state (open tabs, selected tab) and persists it.
 * This service orchestrates the primary data flow: UI events here trigger calls to [GitService]
 * to fetch data, which then updates the [ProjectActiveDiffDataService]. It broadcasts its own
 * state changes via the [TOPIC] for UI components to consume.
 */
@State(
    name = "com.github.uiopak.lstcrc.services.ToolWindowStateService",
    storages = [Storage("gitTabsIdeaPluginState.xml")]
)
@Service(Service.Level.PROJECT)
class ToolWindowStateService(private val project: Project) : PersistentStateComponent<ToolWindowState> {

    private var myState = ToolWindowState()
    private val logger = thisLogger()

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
        myState = ToolWindowState()
        project.messageBus.syncPublisher(TOPIC).stateChanged(myState.copy())
    }

    fun addTab(branchName: String) {
        logger.info("addTab('$branchName') called.")
        val currentTabs = myState.openTabs.toMutableList()
        if (currentTabs.none { it.branchName == branchName }) {
            currentTabs.add(TabInfo(branchName = branchName, alias = null))
            // Create a new list instance to ensure the state component detects the change.
            myState.openTabs = ArrayList(currentTabs)
            myState = myState.copy()
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

        myState.openTabs = ArrayList(currentTabs)

        // Adjust selectedTabIndex if the removed tab was selected or was before the selected tab.
        if (myState.selectedTabIndex >= currentTabs.size && currentTabs.isNotEmpty()) {
            myState.selectedTabIndex = currentTabs.size - 1
        } else if (currentTabs.isEmpty()) {
            myState.selectedTabIndex = -1
        }

        myState = myState.copy()
        logger.info("Tab $branchName removed. New state: $myState")
        project.messageBus.syncPublisher(TOPIC).stateChanged(myState.copy())
    }

    fun setSelectedTab(index: Int) {
        val validIndex = if (index >= myState.openTabs.size || index < -1) {
            logger.warn("setSelectedTab called with invalid index: $index. Open tabs: ${myState.openTabs.size}. Clamping to valid range.")
            if (myState.openTabs.isEmpty()) -1 else myState.openTabs.size - 1
        } else {
            index
        }

        if (myState.selectedTabIndex != validIndex) {
            myState.selectedTabIndex = validIndex
            myState = myState.copy()
            logger.info("Selected tab index set to $validIndex. New state: $myState")
            project.messageBus.syncPublisher(TOPIC).stateChanged(myState.copy())

            val diffDataService = project.service<ProjectActiveDiffDataService>()
            val gitService = project.service<GitService>()

            val selectedBranchName = getSelectedTabBranchName()

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
                        getActiveChangesBrowser(project)?.displayChanges(categorizedChanges, selectedBranchName)
                    } else {
                        diffDataService.clearActiveDiff()
                        getActiveChangesBrowser(project)?.displayChanges(null, selectedBranchName)
                    }
                }
            } else { // This case handles the HEAD tab selection.
                logger.debug("Tab selection changed to HEAD.")
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
                        val activeBrowser = getActiveChangesBrowser(project)
                        if (throwable != null) {
                            logger.error("Error loading changes for '$effectiveBranchNameForDisplay': ${throwable.message}")
                            diffDataService.clearActiveDiff()
                            activeBrowser?.displayChanges(null, effectiveBranchNameForDisplay)
                        } else if (categorizedChanges != null) {
                            diffDataService.updateActiveDiff(
                                effectiveBranchNameForDisplay,
                                categorizedChanges.allChanges,
                                categorizedChanges.createdFiles,
                                categorizedChanges.modifiedFiles,
                                categorizedChanges.movedFiles
                            )
                            activeBrowser?.displayChanges(categorizedChanges, effectiveBranchNameForDisplay)
                        } else {
                            diffDataService.clearActiveDiff()
                            activeBrowser?.displayChanges(null, effectiveBranchNameForDisplay)
                        }
                    }
                } else {
                    logger.debug("'Include HEAD in Scopes' is disabled. Clearing ProjectActiveDiffDataService.")
                    diffDataService.clearActiveDiff()

                    // The diff data service is cleared, but we still fetch changes to show them in the UI panel.
                    gitService.getChanges(effectiveBranchNameForDisplay).whenCompleteAsync { categorizedChanges, throwable ->
                        if (project.isDisposed) return@whenCompleteAsync
                        val activeBrowser = getActiveChangesBrowser(project)
                        if (throwable != null) {
                            activeBrowser?.displayChanges(null, effectiveBranchNameForDisplay)
                        } else {
                            activeBrowser?.displayChanges(categorizedChanges, effectiveBranchNameForDisplay)
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

                val activeBrowser = getActiveChangesBrowser(project)
                val properties = PropertiesComponent.getInstance()
                val includeHeadInScopes = properties.getBoolean(
                    ToolWindowSettingsProvider.APP_INCLUDE_HEAD_IN_SCOPES_KEY,
                    ToolWindowSettingsProvider.DEFAULT_INCLUDE_HEAD_IN_SCOPES
                )

                if (throwable != null) {
                    logger.error("Error refreshing data for active tab '$eventBranchName': ${throwable.message}", throwable)
                    activeBrowser?.displayChanges(null, eventBranchName)
                    diffDataService.clearActiveDiff()
                } else if (categorizedChanges != null) {
                    // Update global diff service if on a branch tab, OR on HEAD tab with the setting enabled.
                    if (!isHeadSelected || includeHeadInScopes) {
                        diffDataService.updateActiveDiff(
                            eventBranchName,
                            categorizedChanges.allChanges,
                            categorizedChanges.createdFiles,
                            categorizedChanges.modifiedFiles,
                            categorizedChanges.movedFiles
                        )
                    } else {
                        // This case happens if we are on HEAD and the 'Include HEAD' setting is OFF.
                        diffDataService.clearActiveDiff()
                    }
                    activeBrowser?.displayChanges(categorizedChanges, eventBranchName)
                } else {
                    diffDataService.clearActiveDiff()
                    activeBrowser?.displayChanges(null, eventBranchName)
                }
            }
        } else {
            logger.debug("Event branch '$eventBranchName' does not match current active tab state ('$currentSelectedBranch'). No refresh initiated.")
        }
    }

    /**
     * Ensures the data for the currently selected tab is loaded and all dependent services are updated.
     * This orchestrates a full data refresh for the current selection.
     */
    fun refreshDataForCurrentSelection() {
        val branchToRefresh = getSelectedTabBranchName() ?: "HEAD"
        logger.info("ACTION: Refreshing data for current selection: '$branchToRefresh'")
        refreshDataForActiveTabIfMatching(branchToRefresh)
    }

    internal fun getActiveChangesBrowser(project: Project): LstCrcChangesBrowser? {
        val toolWindowId = "GitChangesView"
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(toolWindowId)
        val selectedContent = toolWindow?.contentManager?.selectedContent
        return selectedContent?.component as? LstCrcChangesBrowser
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
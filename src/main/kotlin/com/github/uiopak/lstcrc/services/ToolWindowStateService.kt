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
import java.util.concurrent.CompletableFuture

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
            currentTabs.add(TabInfo(branchName = branchName, alias = null, comparisonMap = mutableMapOf()))
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
        myState = myState.copy()
        logger.info("Tab $branchName removed from state. New state: $myState")
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

            // Broadcast state change first to update UI like the status bar widget immediately.
            broadcastCurrentState()

            // Then, load the data for the newly selected tab.
            refreshDataForCurrentSelection()

        } else {
            logger.debug("Selected tab index $validIndex is already set. No action taken.")
        }
    }

    /**
     * Central point for loading data for a given tab profile. It fetches changes from Git,
     * updates the data cache ([ProjectActiveDiffDataService]), and refreshes the UI.
     * @return A CompletableFuture that completes when the entire data loading and UI update process is finished.
     */
    private fun loadDataForTab(tabInfo: TabInfo?): CompletableFuture<Unit> {
        val isLoadingHead = tabInfo == null
        val profileName = tabInfo?.branchName ?: "HEAD"
        logger.info("DATA_FLOW: Initiating data load for profile: '$profileName'")

        val gitService = project.service<GitService>()
        val diffDataService = project.service<ProjectActiveDiffDataService>()
        val resultFuture = CompletableFuture<Unit>()

        val properties = PropertiesComponent.getInstance()
        val includeHeadInScopes = properties.getBoolean(
            ToolWindowSettingsProvider.APP_INCLUDE_HEAD_IN_SCOPES_KEY,
            ToolWindowSettingsProvider.DEFAULT_INCLUDE_HEAD_IN_SCOPES
        )

        gitService.getChanges(tabInfo).whenCompleteAsync { categorizedChanges, throwable ->
            if (project.isDisposed) {
                resultFuture.complete(Unit)
                return@whenCompleteAsync
            }

            val activeBrowser = getActiveChangesBrowser(project)

            if (throwable != null) {
                logger.error("DATA_FLOW: Error loading changes for '$profileName': ${throwable.message}", throwable)
                diffDataService.clearActiveDiff()
                activeBrowser?.displayChanges(null, profileName)
                resultFuture.completeExceptionally(throwable)
                return@whenCompleteAsync
            }

            if (categorizedChanges != null) {
                logger.info("DATA_FLOW: Successfully loaded ${categorizedChanges.allChanges.size} changes for '$profileName'.")
                // The data service is the source of truth for scopes, gutter markers, etc.
                // It should be updated if we're on a branch tab, OR if we're on the HEAD tab and the setting is enabled.
                if (!isLoadingHead || includeHeadInScopes) {
                    logger.debug("DATA_FLOW: Updating ProjectActiveDiffDataService for '$profileName'.")
                    diffDataService.updateActiveDiff(
                        profileName,
                        categorizedChanges.allChanges,
                        categorizedChanges.createdFiles,
                        categorizedChanges.modifiedFiles,
                        categorizedChanges.movedFiles,
                        categorizedChanges.comparisonContext
                    )
                } else {
                    // This case occurs when on the HEAD tab and the "Include HEAD" setting is OFF.
                    logger.debug("DATA_FLOW: On HEAD tab with 'Include HEAD in Scopes' disabled. Clearing ProjectActiveDiffDataService.")
                    diffDataService.clearActiveDiff()
                }
                // The UI panel itself should always be updated with the changes, regardless of the data service state.
                activeBrowser?.displayChanges(categorizedChanges, profileName)
            } else {
                logger.warn("DATA_FLOW: Changes for '$profileName' returned as null. Clearing data and UI.")
                diffDataService.clearActiveDiff()
                activeBrowser?.displayChanges(null, profileName)
            }
            resultFuture.complete(Unit)
        }
        return resultFuture
    }

    /**
     * Ensures the data for the currently selected tab is loaded and all dependent services are updated.
     * This orchestrates a full data refresh for the current selection and is the main entry point
     * for refresh triggers (e.g., from startup, VCS changes, or explicit user action).
     *
     * @return A [CompletableFuture] that completes when the refresh operation is finished.
     */
    fun refreshDataForCurrentSelection(): CompletableFuture<Unit> {
        val tabInfoToRefresh = getSelectedTabInfo()
        val profileName = tabInfoToRefresh?.branchName ?: "HEAD"
        logger.info("ACTION: Refreshing data for current selection: '$profileName'")
        return loadDataForTab(tabInfoToRefresh)
    }

    /**
     * Explicitly broadcasts the current state to all listeners on the message bus.
     */
    fun broadcastCurrentState() {
        logger.info("Broadcasting current state explicitly to all listeners.")
        project.messageBus.syncPublisher(TOPIC).stateChanged(myState.copy())
    }

    fun getSelectedTabInfo(): TabInfo? {
        if (myState.selectedTabIndex >= 0 && myState.selectedTabIndex < myState.openTabs.size) {
            return myState.openTabs[myState.selectedTabIndex]
        }
        return null
    }

    fun getSelectedTabBranchName(): String? {
        return getSelectedTabInfo()?.branchName
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

    fun updateTabComparisonMap(branchName: String, newMap: Map<String, String>) {
        logger.info("updateTabComparisonMap called for branch '$branchName'.")
        val tabIndex = myState.openTabs.indexOfFirst { it.branchName == branchName }

        if (tabIndex != -1) {
            val updatedTabs = myState.openTabs.toMutableList()
            val oldTabInfo = updatedTabs[tabIndex]

            if (oldTabInfo.comparisonMap != newMap) {
                updatedTabs[tabIndex] = oldTabInfo.copy(comparisonMap = newMap.toMutableMap())
                myState.openTabs = ArrayList(updatedTabs)
                myState = myState.copy()
                logger.info("Comparison map for '$branchName' updated. New state: $myState")
                project.messageBus.syncPublisher(TOPIC).stateChanged(myState.copy())

                // If this tab is currently selected, trigger a refresh to show the new diff.
                if (myState.selectedTabIndex == tabIndex) {
                    refreshDataForCurrentSelection()
                }
            } else {
                logger.debug("Comparison map for '$branchName' is unchanged. No state change needed.")
            }
        } else {
            logger.warn("Could not find tab for branch '$branchName' to update its comparison map.")
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
@file:Suppress("DialogTitleCapitalization")

package com.github.uiopak.lstcrc.services

import com.github.uiopak.lstcrc.LstCrcConstants
import com.github.uiopak.lstcrc.messaging.TOOL_WINDOW_STATE_TOPIC
import com.github.uiopak.lstcrc.messaging.ToolWindowStateListener
import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.state.TabInfo
import com.github.uiopak.lstcrc.state.ToolWindowState
import com.github.uiopak.lstcrc.toolWindow.LstCrcChangesBrowser
import com.github.uiopak.lstcrc.toolWindow.SingleRepoBranchSelectionDialog
import com.github.uiopak.lstcrc.toolWindow.ToolWindowSettingsProvider
import com.github.uiopak.lstcrc.utils.RevisionUtils
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.xmlb.XmlSerializerUtil
import git4idea.repo.GitRepository
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

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
    private val isRefreshing = AtomicBoolean(false)
    private val refreshQueued = AtomicBoolean(false)
    @Volatile
    private var activeRefreshFuture: CompletableFuture<Unit>? = null

    override fun getState(): ToolWindowState {
        logger.debug("getState() called. Current state: $myState")
        return myState
    }

    override fun loadState(state: ToolWindowState) {
        logger.info("loadState() called. Loading state: $state")
        XmlSerializerUtil.copyBean(state, myState)
        project.messageBus.syncPublisher(TOOL_WINDOW_STATE_TOPIC).stateChanged(myState.copy())
    }

    override fun noStateLoaded() {
        logger.info("noStateLoaded() called. Initializing with default state.")
        myState = ToolWindowState()
        project.messageBus.syncPublisher(TOOL_WINDOW_STATE_TOPIC).stateChanged(myState.copy())
    }

    fun addTab(branchName: String) {
        logger.info("addTab('$branchName') called.")
        val currentTabs = myState.openTabs.toMutableList()
        if (currentTabs.none { it.branchName == branchName }) {
            currentTabs.add(TabInfo(branchName = branchName, alias = null, comparisonMap = mutableMapOf()))
            myState.openTabs = ArrayList(currentTabs)
            logger.info("Tab '$branchName' added. New state: $myState")
            commitStateAndBroadcast()
        } else {
            logger.info("Tab $branchName already exists.")
        }
    }

    fun removeTab(branchName: String) {
        logger.info("removeTab($branchName) called.")
        val currentTabs = myState.openTabs.toMutableList()
        currentTabs.removeAll { it.branchName == branchName }
        myState.openTabs = ArrayList(currentTabs)
        logger.info("Tab $branchName removed from state. New state: $myState")
        commitStateAndBroadcast()
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
            if (validIndex == -1 && activeRefreshFuture == null) {
                logger.info("HEAD tab is already selected, but no refresh is active. Triggering initial HEAD refresh.")
                refreshDataForCurrentSelection()
            } else {
                logger.debug("Selected tab index $validIndex is already set. No action taken.")
            }
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

        gitService.getChanges(tabInfo).whenCompleteAsync { getChangesResult, throwable ->
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) {
                    resultFuture.complete(Unit)
                    return@invokeLater
                }

                val activeBrowser = getActiveChangesBrowser(project)

                if (throwable != null) {
                    logger.error("DATA_FLOW: Error loading changes for '$profileName': ${throwable.message}", throwable)
                    diffDataService.clearActiveDiff()
                    activeBrowser?.displayChanges(null, profileName)
                    resultFuture.completeExceptionally(throwable)
                    return@invokeLater
                }

                if (getChangesResult != null) {
                    val categorizedChanges = getChangesResult.categorizedChanges
                    logger.info("DATA_FLOW: Successfully loaded ${categorizedChanges.allChanges.size} changes for '$profileName'.")

                    // Handle any branches that were not found during the fetch.
                    if (getChangesResult.failures.isNotEmpty() && tabInfo != null) {
                        handleBranchFailures(tabInfo, getChangesResult.failures)
                    }

                    if (!isLoadingHead || includeHeadInScopes) {
                        logger.debug("DATA_FLOW: Updating ProjectActiveDiffDataService for '$profileName'.")
                        diffDataService.updateActiveDiff(
                            profileName,
                            categorizedChanges.createdFiles,
                            categorizedChanges.modifiedFiles,
                            categorizedChanges.movedFiles,
                            categorizedChanges.deletedFiles,
                            categorizedChanges.comparisonContext
                        )
                    } else {
                        logger.debug("DATA_FLOW: On HEAD tab with 'Include HEAD in Scopes' disabled. Clearing ProjectActiveDiffDataService.")
                        diffDataService.clearActiveDiff()
                    }
                    activeBrowser?.displayChanges(categorizedChanges, profileName)
                } else {
                    logger.warn("DATA_FLOW: Changes for '$profileName' returned as null. Clearing data and UI.")
                    diffDataService.clearActiveDiff()
                    activeBrowser?.displayChanges(null, profileName)
                }
                resultFuture.complete(Unit)
            }
        }
        return resultFuture
    }

    /**
     * Handles the case where one or more branches could not be found during a data load.
     * It updates the tab's configuration to fall back to 'HEAD' and notifies the user, but
     * intelligently skips failures for revisions that look like commit hashes, as these are
     * expected not to exist in all repositories in a multi-repo project.
     */
    private fun handleBranchFailures(tabInfo: TabInfo, failures: Map<GitRepository, String>) {
        // Filter out failures that are likely commit hashes, as they are not "errors" in the same
        // way a missing branch name is. It's expected a commit hash might not exist in all repos.
        val actualBranchFailures = failures.filter { (_, failedRevision) ->
            !RevisionUtils.isCommitHash(failedRevision)
        }

        if (actualBranchFailures.isEmpty()) {
            logger.info("Handling branch failures: All failures were for commit hashes, taking no action. Original failures: $failures")
            return
        }

        logger.warn("Handling branch failures for tab '${tabInfo.branchName}'. Actual branch failures: $actualBranchFailures")
        val newComparisonMap = tabInfo.comparisonMap.toMutableMap()
        var tabConfigUpdated = false

        actualBranchFailures.forEach { (repo, failedRevision) ->
            // Condition A: The failed revision was an explicit override for this repo.
            val isExplicitOverrideFailure = newComparisonMap[repo.root.path] == failedRevision

            // Condition B: The failed revision was the primary tab name, and this repo was using it implicitly.
            val isImplicitPrimaryFailure = (tabInfo.branchName == failedRevision && newComparisonMap[repo.root.path] == null)

            if (isExplicitOverrideFailure || isImplicitPrimaryFailure) {
                newComparisonMap[repo.root.path] = "HEAD" // Reset the comparison for this repo to HEAD.
                tabConfigUpdated = true
            }
        }

        if (tabConfigUpdated) {
            logger.info("Tab '${tabInfo.branchName}' config updated due to missing branches. New map: $newComparisonMap")
            // Update the state, but do NOT trigger another refresh to avoid loops within this call stack.
            updateTabComparisonMap(tabInfo.branchName, newComparisonMap, triggerRefresh = false)

            // The current refresh cycle detected the error and corrected the state.
            // Now, schedule a *new* refresh cycle to load the data for the now-valid state.
            // We use invokeLater to ensure this runs after the current refresh cycle completes and releases its lock.
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                // We must check if the tab that was corrected is still the active one.
                // The user might have switched tabs while the refresh was running.
                if (getSelectedTabInfo()?.branchName == tabInfo.branchName) {
                    logger.info("Scheduling a new data refresh after correcting active tab '${tabInfo.branchName}' configuration.")
                    refreshDataForCurrentSelection()
                } else {
                    logger.info("Tab '${tabInfo.branchName}' was corrected, but is no longer active. Skipping automatic refresh.")
                }
            }
        }

        showBranchNotFoundNotification(tabInfo, actualBranchFailures)
    }

    private fun showBranchNotFoundNotification(tabInfo: TabInfo, failures: Map<GitRepository, String>) {
        val notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("LST-CRC Branch Errors")
        val failedBranchNames = failures.values.distinct().joinToString(", ") { "'$it'" }
        val tabDisplayName = tabInfo.alias ?: tabInfo.branchName

        val content = LstCrcBundle.message("notification.branch.not.found.content", failedBranchNames, tabDisplayName)

        val notification = notificationGroup.createNotification(
            LstCrcBundle.message("notification.branch.not.found.title"),
            content,
            NotificationType.WARNING
        )

        // Set a unique display ID to prevent duplicate notifications for the same tab.
        // This makes the platform replace the old notification instead of showing a new one.
        notification.setDisplayId("LST-CRC.BranchError.${tabInfo.branchName}")

        // Add an action for each failed repository, allowing the user to fix the configuration.
        failures.keys.forEach { repo ->
            val actionText = LstCrcBundle.message("notification.action.change.comparison", repo.root.name)
            notification.addAction(object : AnAction(actionText) {
                override fun actionPerformed(e: AnActionEvent) {
                    val currentTabInfo = state.openTabs.find { it.branchName == tabInfo.branchName }
                    if (currentTabInfo != null) {
                        SingleRepoBranchSelectionDialog(project, repo, currentTabInfo).show()
                    } else {
                        logger.warn("Could not find tab '${tabInfo.branchName}' to show branch selection dialog from notification.")
                    }
                    notification.expire() // Close notification after action is clicked.
                }
            })
        }

        notification.notify(project)
    }

    /**
     * Ensures the data for the currently selected tab is loaded and all dependent services are updated.
     * This orchestrates a full data refresh for the current selection and is the main entry point
     * for refresh triggers (e.g., from startup, VCS changes, or explicit user action).
     *
     * @return A [CompletableFuture] that completes when the refresh operation is finished.
     */
    fun refreshDataForCurrentSelection(): CompletableFuture<Unit> {
        if (!isRefreshing.compareAndSet(false, true)) {
            logger.debug("ACTION: Refresh for current selection is already in progress. Queueing another refresh.")
            refreshQueued.set(true)
            return activeRefreshFuture ?: CompletableFuture.completedFuture(Unit)
        }

        val refreshFuture = CompletableFuture<Unit>()
        activeRefreshFuture = refreshFuture
        runRefreshCycle(refreshFuture)
        return refreshFuture
    }

    private fun runRefreshCycle(refreshFuture: CompletableFuture<Unit>) {
        val tabInfoToRefresh = getSelectedTabInfo()
        val profileName = tabInfoToRefresh?.branchName ?: "HEAD"
        logger.info("ACTION: Refreshing data for current selection: '$profileName'")

        loadDataForTab(tabInfoToRefresh).whenComplete { _, throwable ->
            if (throwable != null) {
                isRefreshing.set(false)
                activeRefreshFuture = null
                logger.debug("ACTION: Refreshing lock released after failure.")
                refreshFuture.completeExceptionally(throwable)
                return@whenComplete
            }

            if (refreshQueued.getAndSet(false) && !project.isDisposed) {
                logger.debug("ACTION: Running queued refresh for the latest selection.")
                runRefreshCycle(refreshFuture)
                return@whenComplete
            }

            isRefreshing.set(false)
            activeRefreshFuture = null
            logger.debug("ACTION: Refreshing lock released.")
            refreshFuture.complete(Unit)
        }
    }

    /**
     * Ensures the state copy is fresh and broadcasts it to all listeners.
     * This is the single place where state mutations are finalized.
     */
    private fun commitStateAndBroadcast() {
        myState = myState.copy()
        project.messageBus.syncPublisher(TOOL_WINDOW_STATE_TOPIC).stateChanged(myState.copy())
    }

    /**
     * Explicitly broadcasts the current state to all listeners on the message bus.
     */
    fun broadcastCurrentState() {
        logger.info("Broadcasting current state explicitly to all listeners.")
        project.messageBus.syncPublisher(TOOL_WINDOW_STATE_TOPIC).stateChanged(myState.copy())
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

    private fun getActiveChangesBrowser(project: Project): LstCrcChangesBrowser? {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(LstCrcConstants.TOOL_WINDOW_ID)
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
                logger.info("Tab alias for '$branchName' updated. New state: $myState")
                commitStateAndBroadcast()
            } else {
                logger.debug("Alias for '$branchName' is already '$newAlias'. No state change needed.")
            }
        } else {
            logger.warn("Could not find tab for branch '$branchName' to update its alias.")
        }
    }

    fun updateTabComparisonMap(branchName: String, newMap: Map<String, String>, triggerRefresh: Boolean = true) {
        logger.info("updateTabComparisonMap called for branch '$branchName'.")
        val tabIndex = myState.openTabs.indexOfFirst { it.branchName == branchName }

        if (tabIndex != -1) {
            val updatedTabs = myState.openTabs.toMutableList()
            val oldTabInfo = updatedTabs[tabIndex]

            if (oldTabInfo.comparisonMap != newMap) {
                updatedTabs[tabIndex] = oldTabInfo.copy(comparisonMap = newMap.toMutableMap())
                myState.openTabs = ArrayList(updatedTabs)
                logger.info("Comparison map for '$branchName' updated. New state: $myState")
                commitStateAndBroadcast()

                // If this tab is currently selected, trigger a refresh to show the new diff.
                if (myState.selectedTabIndex == tabIndex && triggerRefresh) {
                    logger.info("Triggering refresh after comparison map update for active tab.")
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
        // Kept for backward compatibility — prefer TOOL_WINDOW_STATE_TOPIC from LstCrcTopics.kt directly.
        @Suppress("unused")
        @Deprecated("Use TOOL_WINDOW_STATE_TOPIC from LstCrcTopics.kt", ReplaceWith("TOOL_WINDOW_STATE_TOPIC", "com.github.uiopak.lstcrc.messaging.TOOL_WINDOW_STATE_TOPIC"))
        val TOPIC = TOOL_WINDOW_STATE_TOPIC

        @Suppress("unused")
        @Deprecated("Use ToolWindowStateListener from LstCrcTopics.kt", ReplaceWith("ToolWindowStateListener", "com.github.uiopak.lstcrc.messaging.ToolWindowStateListener"))
        typealias ToolWindowStateListener = com.github.uiopak.lstcrc.messaging.ToolWindowStateListener

        fun getInstance(project: Project): ToolWindowStateService {
            return project.getService(ToolWindowStateService::class.java)
        }
    }
}
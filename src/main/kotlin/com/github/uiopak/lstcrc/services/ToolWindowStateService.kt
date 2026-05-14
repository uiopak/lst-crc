package com.github.uiopak.lstcrc.services


import com.github.uiopak.lstcrc.messaging.TOOL_WINDOW_STATE_TOPIC
import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.state.TabInfo
import com.github.uiopak.lstcrc.state.ToolWindowState
import com.github.uiopak.lstcrc.toolWindow.SingleRepoBranchSelectionDialog
import git4idea.GitUtil
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
import git4idea.repo.GitRepository
import com.intellij.openapi.application.EDT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.withContext
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Manages the tool window's UI state (open tabs, selected tab) and persists it.
 * This service orchestrates the primary data flow: UI events here trigger calls to [GitService]
 * to fetch data, which then updates the [ProjectActiveDiffDataService]. It broadcasts its own
 * state changes via the [TOOL_WINDOW_STATE_TOPIC] for UI components to consume.
 */
@State(
    name = "com.github.uiopak.lstcrc.services.ToolWindowStateService",
    storages = [Storage("gitTabsIdeaPluginState.xml")]
)
@Service(Service.Level.PROJECT)
class ToolWindowStateService(private val project: Project, val coroutineScope: CoroutineScope) : PersistentStateComponent<ToolWindowState> {

    private var myState = ToolWindowState()
    private val logger = thisLogger()
    private val activeRefresh = AtomicReference<CompletableFuture<Unit>?>(null)
    private val refreshQueued = AtomicBoolean(false)

    override fun getState(): ToolWindowState {
        logger.debug("getState() called. Current state: $myState")
        return normalizeState(myState)
    }

    override fun loadState(state: ToolWindowState) {
        logger.info("loadState() called. Loading state: $state")
        myState = normalizeState(state)
        project.messageBus.syncPublisher(TOOL_WINDOW_STATE_TOPIC).stateChanged(normalizeState(myState))
    }

    override fun noStateLoaded() {
        logger.info("noStateLoaded() called. Initializing with default state.")
        replaceState(ToolWindowState())
    }

    fun addTab(branchName: String) {
        if (project.isDisposed) return
        logger.info("addTab('$branchName') called.")
        if (myState.openTabs.any { it.branchName == branchName }) {
            logger.info("Tab $branchName already exists.")
            return
        }

        replaceState(
            myState.copy(openTabs = myState.openTabs + TabInfo(branchName = branchName, alias = null, comparisonMap = mutableMapOf()))
        )
        logger.info("Tab '$branchName' added. New state: $myState")
    }

    fun removeTab(branchName: String) {
        if (project.isDisposed) return
        logger.info("removeTab($branchName) called.")
        val removedIndex = myState.openTabs.indexOfFirst { it.branchName == branchName }
        if (removedIndex == -1) {
            logger.debug("Tab $branchName was not present. No state change needed.")
            return
        }

        val updatedTabs = myState.openTabs.filterNot { it.branchName == branchName }
        val updatedSelectedIndex = adjustedSelectedIndexAfterRemoval(removedIndex, updatedTabs.lastIndex)

        replaceState(
            myState.copy(
                openTabs = updatedTabs,
                selectedTabIndex = updatedSelectedIndex
            )
        )
        logger.info("Tab $branchName removed from state. New state: $myState")
    }

    fun setSelectedTab(index: Int) {
        if (project.isDisposed) return
        val validIndex = if (index >= myState.openTabs.size || index < -1) {
            logger.warn("setSelectedTab called with invalid index: $index. Open tabs: ${myState.openTabs.size}. Clamping to valid range.")
            if (myState.openTabs.isEmpty()) -1 else myState.openTabs.size - 1
        } else {
            index
        }

        if (myState.selectedTabIndex != validIndex) {
            myState = myState.copy(selectedTabIndex = validIndex)
            logger.info("Selected tab index set to $validIndex. New state: $myState")

            // Broadcast state change first to update UI like the status bar widget immediately.
            broadcastCurrentState()

            // Then, load the data for the newly selected tab.
            refreshDataForCurrentSelection()

        } else {
            if (validIndex == -1) {
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
     */
    private suspend fun loadDataForTab(tabInfo: TabInfo?) {
        val profileName = tabInfo?.branchName ?: "HEAD"
        logger.info("DATA_FLOW: Initiating data load for profile: '$profileName'")
        val gitService = project.service<GitService>()
        val diffDataService = project.service<ProjectActiveDiffDataService>()

        try {
            val getChangesResult = gitService.getChanges(tabInfo)
            withContext(Dispatchers.EDT) {
                if (project.isDisposed) return@withContext
                applyLoadedChanges(tabInfo, profileName, diffDataService, getChangesResult)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.EDT) {
                if (project.isDisposed) return@withContext
                logger.error("DATA_FLOW: Error loading changes for '$profileName': ${e.message}", e)
                diffDataService.clearActiveDiff()
            }
        }
    }

    private fun applyLoadedChanges(
        tabInfo: TabInfo?,
        profileName: String,
        diffDataService: ProjectActiveDiffDataService,
        getChangesResult: GetChangesResult
    ) {
        val categorizedChanges = getChangesResult.categorizedChanges
        logger.info("DATA_FLOW: Successfully loaded ${categorizedChanges.allChanges.size} changes for '$profileName'.")

        if (getChangesResult.failures.isNotEmpty() && tabInfo != null) {
            handleBranchFailures(tabInfo, getChangesResult.failures)
        }

        updateActiveDiffData(profileName, diffDataService, categorizedChanges)
    }

    private fun updateActiveDiffData(
        profileName: String,
        diffDataService: ProjectActiveDiffDataService,
        categorizedChanges: CategorizedChanges
    ) {
        logger.debug("DATA_FLOW: Updating ProjectActiveDiffDataService for '$profileName'.")
        diffDataService.updateActiveDiff(
            profileName,
            categorizedChanges
        )
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
            !GitUtil.isHashString(failedRevision, false)
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
        if (project.isDisposed) return CompletableFuture.completedFuture(Unit)

        refreshQueued.set(true)

        activeRefresh.get()?.let {
            logger.debug("ACTION: Refresh already in progress. Coalescing another refresh request.")
            return it
        }

        val refreshFuture = coroutineScope.async {
            runRefreshCycle()
        }.asCompletableFuture()

        if (!activeRefresh.compareAndSet(null, refreshFuture)) {
            logger.debug("ACTION: Refresh was scheduled concurrently. Reusing the active refresh future.")
            return activeRefresh.get() ?: refreshFuture
        }

        refreshFuture.whenComplete { _, _ ->
            if (activeRefresh.compareAndSet(refreshFuture, null) && refreshQueued.get() && !project.isDisposed) {
                logger.debug("ACTION: A refresh request arrived during completion. Scheduling another coalesced cycle.")
                refreshDataForCurrentSelection()
            }
        }

        return refreshFuture
    }

    private suspend fun runRefreshCycle() {
        while (!project.isDisposed && refreshQueued.getAndSet(false)) {
            val tabInfoToRefresh = getSelectedTabInfo()
            val profileName = tabInfoToRefresh?.branchName ?: "HEAD"
            logger.info("ACTION: Refreshing data for current selection: '$profileName'")

            loadDataForTab(tabInfoToRefresh)
        }
    }

    private fun commitStateAndBroadcast() {
        if (project.isDisposed) return
        logger.debug("commitStateAndBroadcast: broadcasting state to listeners.")
        project.messageBus.syncPublisher(TOOL_WINDOW_STATE_TOPIC).stateChanged(normalizeState(myState))
    }

    /**
     * Explicitly broadcasts the current state to all listeners on the message bus.
     */
    fun broadcastCurrentState() {
        if (project.isDisposed) return
        logger.info("Broadcasting current state explicitly to all listeners.")
        project.messageBus.syncPublisher(TOOL_WINDOW_STATE_TOPIC).stateChanged(normalizeState(myState))
    }

    fun getSelectedTabInfo(): TabInfo? {
        if (myState.selectedTabIndex >= 0 && myState.selectedTabIndex < myState.openTabs.size) {
            return myState.openTabs[myState.selectedTabIndex]
        }
        return null
    }

    fun findTabIndex(branchName: String): Int {
        return myState.openTabs.indexOfFirst { it.branchName == branchName }
    }

    @Suppress("unused")
    fun findTabByDisplayName(displayName: String): TabInfo? {
        return myState.openTabs.firstOrNull { it.branchName == displayName || it.alias == displayName }
    }

    @Suppress("unused")
    fun findTabIndexByDisplayName(displayName: String): Int {
        return myState.openTabs.indexOfFirst { it.branchName == displayName || it.alias == displayName }
    }

    fun getSelectedTabBranchName(): String? {
        return getSelectedTabInfo()?.branchName
    }

    fun updateTabAlias(branchName: String, newAlias: String?) {
        if (project.isDisposed) return
        logger.info("updateTabAlias called for branch '$branchName' with new alias '$newAlias'.")
        updateTab(branchName, triggerRefresh = false, missingMessage = "Could not find tab for branch '$branchName' to update its alias.") { oldTabInfo ->
            if (oldTabInfo.alias == newAlias) {
                logger.debug("Alias for '$branchName' is already '$newAlias'. No state change needed.")
                oldTabInfo
            } else {
                oldTabInfo.copy(alias = newAlias)
            }
        }?.let {
            logger.info("Tab alias for '$branchName' updated. New state: $myState")
        }
    }

    fun updateTabComparisonMap(branchName: String, newMap: Map<String, String>, triggerRefresh: Boolean = true) {
        if (project.isDisposed) return
        logger.info("updateTabComparisonMap called for branch '$branchName'.")
        updateTab(branchName, triggerRefresh = triggerRefresh, missingMessage = "Could not find tab for branch '$branchName' to update its comparison map.") { oldTabInfo ->
            if (oldTabInfo.comparisonMap == newMap) {
                logger.debug("Comparison map for '$branchName' is unchanged. No state change needed.")
                oldTabInfo
            } else {
                oldTabInfo.copy(comparisonMap = newMap.toMutableMap())
            }
        }?.let {
            logger.info("Comparison map for '$branchName' updated. New state: $myState")
        }
    }

    private fun replaceState(newState: ToolWindowState) {
        myState = normalizeState(newState)
        commitStateAndBroadcast()
    }

    private fun normalizeState(state: ToolWindowState): ToolWindowState {
        return ToolWindowState(
            openTabs = state.openTabs.map { tab ->
                TabInfo(
                    branchName = tab.branchName,
                    alias = tab.alias,
                    comparisonMap = tab.comparisonMap.toMutableMap()
                )
            },
            selectedTabIndex = state.selectedTabIndex
        )
    }

    private fun updateTab(
        branchName: String,
        triggerRefresh: Boolean,
        missingMessage: String,
        transform: (TabInfo) -> TabInfo
    ): Int? {
        val tabIndex = findTabIndex(branchName)
        if (tabIndex == -1) {
            logger.warn(missingMessage)
            return null
        }

        val currentTab = myState.openTabs[tabIndex]
        val updatedTab = transform(currentTab)
        if (updatedTab == currentTab) {
            return null
        }

        val updatedTabs = myState.openTabs.toMutableList()
        updatedTabs[tabIndex] = updatedTab
        replaceState(myState.copy(openTabs = updatedTabs))

        if (triggerRefresh && myState.selectedTabIndex == tabIndex) {
            logger.info("Triggering refresh after state update for active tab '$branchName'.")
            refreshDataForCurrentSelection()
        }

        return tabIndex
    }

    private fun adjustedSelectedIndexAfterRemoval(removedIndex: Int, newLastIndex: Int): Int {
        val currentSelectedIndex = myState.selectedTabIndex
        return when {
            newLastIndex < 0 -> -1
            currentSelectedIndex < 0 -> -1
            currentSelectedIndex > removedIndex -> currentSelectedIndex - 1
            currentSelectedIndex == removedIndex -> minOf(removedIndex, newLastIndex)
            else -> currentSelectedIndex
        }
    }
}
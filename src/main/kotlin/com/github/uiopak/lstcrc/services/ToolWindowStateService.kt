package com.github.uiopak.lstcrc.services

import com.github.uiopak.lstcrc.LstCrcConstants.HEAD
import com.github.uiopak.lstcrc.messaging.TOOL_WINDOW_STATE_TOPIC
import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.state.TabInfo
import com.github.uiopak.lstcrc.state.ToolWindowState
import com.github.uiopak.lstcrc.state.displayName
import com.github.uiopak.lstcrc.toolWindow.SingleRepoBranchSelectionDialog
import com.github.uiopak.lstcrc.utils.isCommitHash
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import git4idea.repo.GitRepository
import com.intellij.openapi.application.EDT
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CompletableFuture

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

    @Volatile
    private var myState = ToolWindowState()
    private val logger = thisLogger()

    // Refresh sequencing, guarded by refreshLock: requests queue for the next load, and one coroutine runs loads
    // while any are queued.
    private val refreshLock = Any()
    /** Completes when the next load has run. Every request made before that load starts gets this future. */
    private var queuedRefresh: CompletableFuture<Unit>? = null
    private var refreshRunning = false
    /** True while a queued request needs a full reload; see [refreshAfterDocumentEdit]. */
    private var fullReloadQueued = true

    override fun getState(): ToolWindowState {
        logger.debug { "getState() called. Current state: $myState" }
        return normalizeState(myState)
    }

    override fun loadState(state: ToolWindowState) {
        logger.debug { "loadState() called. Loading state: $state" }
        myState = normalizeState(state)
        broadcast()
    }

    override fun noStateLoaded() {
        logger.debug { "noStateLoaded() called. Initializing with default state." }
        replaceState(ToolWindowState())
    }

    fun addTab(branchName: String) {
        if (project.isDisposed) return
        logger.debug { "addTab('$branchName') called." }
        if (myState.openTabs.any { it.branchName == branchName }) {
            logger.debug { "Tab $branchName already exists." }
            return
        }

        replaceState(myState.copy(openTabs = myState.openTabs + TabInfo(branchName = branchName)))
        logger.debug { "Tab '$branchName' added. New state: $myState" }
    }

    fun removeTab(branchName: String) {
        if (project.isDisposed) return
        logger.debug { "removeTab($branchName) called." }
        val removedIndex = myState.openTabs.indexOfFirst { it.branchName == branchName }
        if (removedIndex == -1) {
            logger.debug { "Tab $branchName was not present. No state change needed." }
            return
        }

        val updatedTabs = myState.openTabs.filterNot { it.branchName == branchName }
        val selected = myState.selectedTabIndex
        // A closed selected tab hands the selection to the tab before it (HEAD before the first), as the tool window does.
        val updatedSelectedIndex = if (selected >= removedIndex) selected - 1 else selected

        replaceState(myState.copy(openTabs = updatedTabs, selectedTabIndex = updatedSelectedIndex))
        logger.debug { "Tab $branchName removed from state. New state: $myState" }
        // The tool window then selects the tab the state already selects, which loads nothing, so load it here.
        if (selected == removedIndex) refreshDataForCurrentSelection()
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
            logger.debug { "Selected tab index set to $validIndex. New state: $myState" }
            // Broadcast first so the status bar widget updates immediately, then load the new tab's data.
            broadcast()
            refreshDataForCurrentSelection()
        } else if (validIndex == -1) {
            // Re-selecting HEAD (e.g. on tool window creation) still needs the initial HEAD load.
            refreshDataForCurrentSelection()
        }
    }

    /**
     * Central point for loading data for a given tab profile. It fetches changes from Git,
     * updates the data cache ([ProjectActiveDiffDataService]), and refreshes the UI.
     */
    private suspend fun loadDataForTab(tabInfo: TabInfo?, reuseDiskChanges: Boolean) {
        val profileName = tabInfo?.branchName ?: HEAD
        logger.debug { "DATA_FLOW: Initiating data load for profile: '$profileName'" }
        val gitService = project.service<GitService>()
        val diffDataService = project.service<ProjectActiveDiffDataService>()

        try {
            val result = gitService.getChanges(tabInfo, reuseDiskChanges)
            withContext(Dispatchers.EDT) {
                if (project.isDisposed) return@withContext
                logger.debug { "DATA_FLOW: Loaded ${result.categorizedChanges.allChanges.size} changes for '$profileName'." }
                if (tabInfo != null && result.failures.isNotEmpty()) {
                    handleBranchFailures(tabInfo, result.failures)
                }
                diffDataService.updateActiveDiff(profileName, result.categorizedChanges)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            withContext(Dispatchers.EDT) {
                if (project.isDisposed) return@withContext
                logger.error("DATA_FLOW: Error loading changes for '$profileName': ${e.message}", e)
                diffDataService.clearActiveDiff()
            }
        }
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
            !isCommitHash(failedRevision)
        }

        if (actualBranchFailures.isEmpty()) {
            logger.debug { "Handling branch failures: All failures were for commit hashes, taking no action. Original failures: $failures" }
            return
        }

        logger.warn("Handling branch failures for tab '${tabInfo.branchName}'. Actual branch failures: $actualBranchFailures")
        // Reset a repository to HEAD when the failed revision was its explicit override, or the tab's own
        // branch that it used implicitly.
        val resetRoots = actualBranchFailures.mapNotNull { (repo, failedRevision) ->
            val root = repo.root.path
            val effectiveTarget = tabInfo.comparisonMap[root] ?: tabInfo.branchName
            root.takeIf { effectiveTarget == failedRevision }
        }

        if (resetRoots.isNotEmpty()) {
            val newComparisonMap = tabInfo.comparisonMap + resetRoots.associateWith { HEAD }
            logger.debug { "Tab '${tabInfo.branchName}' config updated due to missing branches. New map: $newComparisonMap" }
            // Update the state, but do NOT trigger another refresh to avoid loops within this call stack.
            updateTabComparisonMap(tabInfo.branchName, newComparisonMap, triggerRefresh = false)

            // The current refresh detected the error and corrected the state; load the data for the
            // corrected state as a separate task. If this refresh is still running, the request is
            // queued and runs as its next cycle. Skipped if the user has switched tabs meanwhile.
            coroutineScope.launch(Dispatchers.EDT) {
                if (!project.isDisposed && getSelectedTabInfo()?.branchName == tabInfo.branchName) {
                    logger.debug { "Scheduling a new data refresh after correcting active tab '${tabInfo.branchName}' configuration." }
                    refreshDataForCurrentSelection()
                }
            }
        }

        showBranchNotFoundNotification(tabInfo, actualBranchFailures)
    }

    private fun showBranchNotFoundNotification(tabInfo: TabInfo, failures: Map<GitRepository, String>) {
        val notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("LST-CRC Branch Errors")
        val failedBranchNames = failures.values.distinct().joinToString(", ") { "'$it'" }
        val tabDisplayName = tabInfo.displayName

        val content = LstCrcBundle.message("notification.branch.not.found.content", failedBranchNames, tabDisplayName)

        val notification = notificationGroup.createNotification(
            LstCrcBundle.message("notification.branch.not.found.title"),
            content,
            NotificationType.WARNING
        )

        // Set a unique display ID to prevent duplicate notifications for the same tab.
        // This makes the platform replace the old notification instead of showing a new one.
        @Suppress("UsePropertyAccessSyntax") // displayId is a val; the setter is the only API
        notification.setDisplayId("LST-CRC.BranchError.${tabInfo.branchName}")

        // Add an action for each failed repository, allowing the user to fix the configuration.
        failures.keys.forEach { repo ->
            val actionText = LstCrcBundle.message("notification.action.change.comparison", repo.root.name)
            notification.addAction(NotificationAction.createSimple(actionText) {
                val currentTabInfo = state.openTabs.find { it.branchName == tabInfo.branchName }
                if (currentTabInfo != null) {
                    SingleRepoBranchSelectionDialog(project, repo, currentTabInfo).show()
                } else {
                    logger.warn("Could not find tab '${tabInfo.branchName}' to show branch selection dialog from notification.")
                }
                notification.expire() // Close notification after action is clicked.
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
    fun refreshDataForCurrentSelection(): CompletableFuture<Unit> = requestRefresh(fullReload = true)

    /**
     * Refresh after unsaved edits only. Nothing changed on disk, so `GitService` may reuse its last
     * `git diff` result and only rebuild the unsaved-edit overlay. If a full refresh is merged into
     * the same cycle, the cycle reloads everything.
     */
    fun refreshAfterDocumentEdit(): CompletableFuture<Unit> = requestRefresh(fullReload = false)

    /**
     * Queues a load. Requests that arrive before the queued load starts share it, so a burst of requests runs
     * one load, and the returned future only completes after a load that started after this request.
     */
    private fun requestRefresh(fullReload: Boolean): CompletableFuture<Unit> {
        if (project.isDisposed) return CompletableFuture.completedFuture(Unit)
        synchronized(refreshLock) {
            if (fullReload) fullReloadQueued = true
            queuedRefresh?.let { return it }
            val refresh = CompletableFuture<Unit>()
            queuedRefresh = refresh
            if (!refreshRunning) {
                refreshRunning = true
                coroutineScope.launch { runRefreshCycle() }.invokeOnCompletion { cause ->
                    // Cancelled (the project is closing) or never started: fail what is still queued.
                    if (cause != null) synchronized(refreshLock) {
                        refreshRunning = false
                        queuedRefresh?.completeExceptionally(cause)
                        queuedRefresh = null
                    }
                }
            } else {
                logger.debug { "ACTION: Refresh already in progress. Queued another refresh cycle." }
            }
            return refresh
        }
    }

    private suspend fun runRefreshCycle() {
        while (true) {
            val (refresh, reuseDiskChanges) = synchronized(refreshLock) {
                val next = queuedRefresh
                if (next == null || project.isDisposed) {
                    refreshRunning = false
                    queuedRefresh = null
                    next?.complete(Unit)
                    return
                }
                queuedRefresh = null
                val reuse = !fullReloadQueued
                fullReloadQueued = false
                next to reuse
            }
            try {
                loadDataForTab(getSelectedTabInfo(), reuseDiskChanges)
            } catch (e: Throwable) {
                refresh.completeExceptionally(e)
                throw e
            }
            refresh.complete(Unit)
        }
    }

    /**
     * Explicitly broadcasts the current state to all listeners on the message bus.
     */
    fun broadcastCurrentState() = broadcast()

    /** Publishes a defensive copy of the state, so listeners cannot mutate it. */
    private fun broadcast() {
        if (project.isDisposed) return
        project.messageBus.syncPublisher(TOOL_WINDOW_STATE_TOPIC).stateChanged(normalizeState(myState))
    }

    fun getSelectedTabInfo(): TabInfo? = myState.openTabs.getOrNull(myState.selectedTabIndex)

    fun isHeadSelected(): Boolean = myState.let { it.selectedTabIndex == -1 || it.openTabs.isEmpty() }

    fun findTabIndex(branchName: String): Int = myState.openTabs.indexOfFirst { it.branchName == branchName }

    @Suppress("unused") // Used by UI tests.
    fun findTabByDisplayName(displayName: String): TabInfo? = myState.openTabs.getOrNull(findTabIndexByDisplayName(displayName))

    @Suppress("unused") // Used by UI tests.
    fun findTabIndexByDisplayName(displayName: String): Int =
        myState.openTabs.indexOfFirst { it.branchName == displayName || it.alias == displayName }

    fun getSelectedTabBranchName(): String? = getSelectedTabInfo()?.branchName

    fun updateTabAlias(branchName: String, newAlias: String?) {
        updateTab(branchName, triggerRefresh = false) { it.copy(alias = newAlias) }
    }

    fun updateTabComparisonMap(branchName: String, newMap: Map<String, String>, triggerRefresh: Boolean = true) {
        updateTab(branchName, triggerRefresh) { it.copy(comparisonMap = newMap.toMutableMap()) }
    }

    /**
     * Sets the comparison target of one repository in a tab. Choosing [defaultTarget] (the tab's own
     * branch) removes the override instead of storing a redundant one.
     */
    @JvmOverloads
    fun updateTabRepoComparison(
        branchName: String,
        repositoryRootPath: String,
        targetRevision: String,
        defaultTarget: String? = null,
        triggerRefresh: Boolean = true
    ) {
        updateTab(branchName, triggerRefresh) { tab ->
            tab.copy(comparisonMap = tab.comparisonMap.toMutableMap().apply {
                if (targetRevision == defaultTarget) remove(repositoryRootPath) else put(repositoryRootPath, targetRevision)
            })
        }
    }

    private fun replaceState(newState: ToolWindowState) {
        myState = normalizeState(newState)
        broadcast()
    }

    private fun normalizeState(state: ToolWindowState): ToolWindowState = ToolWindowState(
        openTabs = state.openTabs.map { it.copy(comparisonMap = it.comparisonMap.toMutableMap()) },
        selectedTabIndex = state.selectedTabIndex
    )

    /** Replaces one tab via [transform]; no-op (no broadcast, no refresh) when the tab is missing or unchanged. */
    private fun updateTab(branchName: String, triggerRefresh: Boolean, transform: (TabInfo) -> TabInfo) {
        if (project.isDisposed) return
        val tabIndex = findTabIndex(branchName)
        if (tabIndex == -1) {
            logger.warn("Could not find tab '$branchName' to update.")
            return
        }

        val currentTab = myState.openTabs[tabIndex]
        val updatedTab = transform(currentTab)
        if (updatedTab == currentTab) return

        replaceState(myState.copy(openTabs = myState.openTabs.toMutableList().also { it[tabIndex] = updatedTab }))
        logger.debug { "Tab '$branchName' updated. New state: $myState" }

        if (triggerRefresh && myState.selectedTabIndex == tabIndex) {
            refreshDataForCurrentSelection()
        }
    }
}
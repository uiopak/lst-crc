package com.github.uiopak.lstcrc.services

import com.github.uiopak.lstcrc.state.TabInfo
import com.github.uiopak.lstcrc.state.ToolWindowState
import com.github.uiopak.lstcrc.testsupport.LstCrcTestCase
import com.github.uiopak.lstcrc.testsupport.categorizedChanges
import com.intellij.dvcs.repo.Repository
import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LoggedErrorProcessor
import git4idea.GitVcs
import git4idea.repo.GitRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

class ToolWindowStateServiceRefreshTest : LstCrcTestCase() {

    // The refresh applies its result on the EDT, so the test waits for it from another thread.
    override fun runInDispatchThread(): Boolean = false

    /**
     * A request's future must complete after a load that started after the request, even while other
     * requests keep refreshes running. Before, a request that arrived after a cycle's last load got that
     * cycle's future, and `join()` returned before the new selection was loaded.
     */
    fun testJoinedRefreshLoadsTheSelectionMadeBeforeTheRequest() {
        val service = project.service<ToolWindowStateService>()
        val diffData = project.service<ProjectActiveDiffDataService>()
        val tabs = listOf("feature-a", "feature-b")
        service.loadState(ToolWindowState(openTabs = tabs.map { TabInfo(branchName = it) }, selectedTabIndex = -1))

        val stop = AtomicBoolean(false)
        val editRefreshes = Executors.newSingleThreadExecutor()
        editRefreshes.execute {
            while (!stop.get()) service.refreshAfterDocumentEdit()
        }
        try {
            repeat(200) { i ->
                service.setSelectedTab(i % 2)
                service.refreshDataForCurrentSelection().get(30, TimeUnit.SECONDS)
                assertEquals("iteration $i", tabs[i % 2], diffData.activeBranchName)
            }
        } finally {
            stop.set(true)
            editRefreshes.shutdown()
            editRefreshes.awaitTermination(30, TimeUnit.SECONDS)
            service.refreshDataForCurrentSelection().get(30, TimeUnit.SECONDS)
        }
    }

    /**
     * Closing the selected tab selects the tab before it, as the tool window does, and loads it. Before, the state
     * already pointed at that tab when the tool window selected it, so nothing loaded it and the closed tab's
     * comparison stayed active (tree, gutters, scopes and tab colours).
     */
    fun testRemovingTheSelectedTabLoadsTheTabBeforeIt() {
        val service = project.service<ToolWindowStateService>()
        val diffData = project.service<ProjectActiveDiffDataService>()
        val tabs = listOf("feature-a", "feature-b", "feature-c")
        service.loadState(ToolWindowState(openTabs = tabs.map { TabInfo(branchName = it) }, selectedTabIndex = 2))
        service.refreshDataForCurrentSelection().get(30, TimeUnit.SECONDS)

        service.removeTab("feature-c")
        assertEquals("feature-b", service.getSelectedTabBranchName())
        assertActiveBranchBecomes(diffData, "feature-b")

        // Closing the first tab selects HEAD.
        service.setSelectedTab(0)
        service.refreshDataForCurrentSelection().get(30, TimeUnit.SECONDS)
        service.removeTab("feature-a")
        assertTrue(service.isHeadSelected())
        assertActiveBranchBecomes(diffData, "HEAD")
    }

    fun testPlatformCancellationKeepsActiveDiffAndFailsRefreshFuture() {
        val profile = TabInfo("feature")
        val cancelled = ProcessCanceledException()
        withControlledLoadFailure(profile, profile, cancelled) { service, future, release, file, loggedErrors ->
            release.complete(Unit)
            val failure = try {
                future.get(15, TimeUnit.SECONDS)
                fail("A cancelled refresh must fail its future")
                error("unreachable")
            } catch (e: ExecutionException) {
                e.cause
            } catch (e: java.util.concurrent.CancellationException) {
                e.cause ?: e
            }
            assertSame(cancelled, failure)
            assertEquals("feature", project.service<ProjectActiveDiffDataService>().activeBranchName)
            assertEquals(listOf(file), project.service<ProjectActiveDiffDataService>().createdFiles)
            assertEquals("Cancellation must not be logged as a loading error", 0, loggedErrors.get())
            assertEquals("feature", service.getSelectedTabBranchName())
        }
    }

    fun testObsoleteTabLoadFailureKeepsPreviouslyLoadedComparison() {
        assertObsoleteLoadFailureKeepsComparison(TabInfo("feature-a"), TabInfo("feature-b"))
    }

    fun testObsoleteRepositoryTargetFailureKeepsPreviouslyLoadedComparison() {
        lateinit var root: String
        ApplicationManager.getApplication().invokeAndWait {
            root = myFixture.tempDirFixture.findOrCreateDir("controlled-load").path
        }
        assertObsoleteLoadFailureKeepsComparison(
            TabInfo("feature", comparisonMap = mapOf(root to "old-target")),
            TabInfo("feature", comparisonMap = mapOf(root to "new-target"))
        )
    }

    fun testCurrentLoadFailureStillClearsActiveDiff() {
        val profile = TabInfo("feature")
        withControlledLoadFailure(profile, profile, IllegalStateException("Controlled loading failure")) { _, future, release, _, loggedErrors ->
            release.complete(Unit)
            future.get(15, TimeUnit.SECONDS)
            assertNull(project.service<ProjectActiveDiffDataService>().activeBranchName)
            assertEquals(1, loggedErrors.get())
        }
    }

    private fun assertObsoleteLoadFailureKeepsComparison(loading: TabInfo, selected: TabInfo) {
        withControlledLoadFailure(loading, selected, IllegalStateException("Controlled loading failure")) { _, future, release, file, loggedErrors ->
            release.complete(Unit)
            future.get(15, TimeUnit.SECONDS)
            assertEquals(selected.branchName, project.service<ProjectActiveDiffDataService>().activeBranchName)
            assertEquals(listOf(file), project.service<ProjectActiveDiffDataService>().createdFiles)
            assertEquals(
                selected.comparisonMap[file.parent.path] ?: selected.branchName,
                project.service<ProjectActiveDiffDataService>().activeComparisonContext[file.parent.path]
            )
            assertEquals("An obsolete failure must not be reported for the current selection", 0, loggedErrors.get())
        }
    }

    /** Hold a real GitService load in repository.update(), then deliver its failure after changing the selection. */
    private fun withControlledLoadFailure(
        loading: TabInfo,
        selected: TabInfo,
        failure: RuntimeException,
        test: (ToolWindowStateService, CompletableFuture<Unit>, CompletableFuture<Unit>, VirtualFile, AtomicInteger) -> Unit
    ) {
        val app = ApplicationManager.getApplication()
        lateinit var file: VirtualFile
        app.invokeAndWait { file = myFixture.addFileToProject("controlled-load/Changed.txt", "content\n").virtualFile }
        val entered = CompletableFuture<Unit>()
        val release = CompletableFuture<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, _ -> })
        val service = ToolWindowStateService(project, scope)
        val sharedState = project.service<ToolWindowStateService>()
        fun select(profile: TabInfo) {
            val state = ToolWindowState(listOf(profile), 0)
            service.loadState(state)
            sharedState.loadState(state)
        }
        app.invokeAndWait {
            select(selected)
            project.service<ProjectActiveDiffDataService>().updateActiveDiff(
                selected.branchName,
                categorizedChanges(createdFiles = listOf(file)).copy(
                    comparisonContext = mapOf(file.parent.path to (selected.comparisonMap[file.parent.path] ?: selected.branchName))
                )
            )
            select(loading)
        }

        val repo = java.lang.reflect.Proxy.newProxyInstance(
            GitRepository::class.java.classLoader, arrayOf(GitRepository::class.java)
        ) { proxy, method, args ->
            when (method.name) {
                "getRoot" -> file.parent
                "getVcs" -> GitVcs.getInstance(project)
                "getProject" -> project
                "update" -> {
                    entered.complete(Unit)
                    release.get(15, TimeUnit.SECONDS)
                    throw failure
                }
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                "toString" -> "Controlled repository"
                else -> error("Unexpected repository call: ${method.name}")
            }
        } as GitRepository
        val registry = project.service<VcsRepositoryManager>()
        val lock = VcsRepositoryManager::class.java.getDeclaredField("REPO_LOCK").apply { isAccessible = true }
            .get(registry) as ReentrantReadWriteLock
        @Suppress("UNCHECKED_CAST")
        val repositories = VcsRepositoryManager::class.java.getDeclaredField("repositories").apply { isAccessible = true }
            .get(registry) as MutableMap<VirtualFile, Repository>
        val previous = lock.writeLock().withLock { repositories.put(file.parent, repo) }
        val loggedErrors = AtomicInteger()
        val errorProcessor = LoggedErrorProcessor.executeWith(object : LoggedErrorProcessor() {
            override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> {
                if (category == "#com.github.uiopak.lstcrc.services.ToolWindowStateService" && message.startsWith("DATA_FLOW: Error loading changes")) {
                    loggedErrors.incrementAndGet()
                    return Action.NONE
                }
                return super.processError(category, message, details, t)
            }
        })
        try {
            val future = service.refreshDataForCurrentSelection()
            entered.get(15, TimeUnit.SECONDS)
            app.invokeAndWait { select(selected) }
            test(service, future, release, file, loggedErrors)
        } finally {
            release.complete(Unit)
            scope.cancel()
            lock.writeLock().withLock {
                if (previous == null) repositories.remove(file.parent) else repositories[file.parent] = previous
            }
            errorProcessor.close()
            app.invokeAndWait { sharedState.noStateLoaded() }
        }
    }

    /** Waits for [expected] to become the active comparison without asking for a refresh (which would load it anyway). */
    private fun assertActiveBranchBecomes(diffData: ProjectActiveDiffDataService, expected: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (diffData.activeBranchName != expected && System.nanoTime() < deadline) Thread.sleep(20)
        assertEquals(expected, diffData.activeBranchName)
    }
}

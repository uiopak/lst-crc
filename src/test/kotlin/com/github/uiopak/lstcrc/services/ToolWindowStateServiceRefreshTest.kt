package com.github.uiopak.lstcrc.services

import com.github.uiopak.lstcrc.state.TabInfo
import com.github.uiopak.lstcrc.state.ToolWindowState
import com.github.uiopak.lstcrc.testsupport.LstCrcTestCase
import com.intellij.openapi.components.service
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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

    /** Waits for [expected] to become the active comparison without asking for a refresh (which would load it anyway). */
    private fun assertActiveBranchBecomes(diffData: ProjectActiveDiffDataService, expected: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (diffData.activeBranchName != expected && System.nanoTime() < deadline) Thread.sleep(20)
        assertEquals(expected, diffData.activeBranchName)
    }
}

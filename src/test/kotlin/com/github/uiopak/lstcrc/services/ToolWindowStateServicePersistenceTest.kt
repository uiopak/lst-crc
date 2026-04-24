package com.github.uiopak.lstcrc.services

import com.github.uiopak.lstcrc.state.TabInfo
import com.github.uiopak.lstcrc.state.ToolWindowState
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ToolWindowStateServicePersistenceTest : BasePlatformTestCase() {

    fun testLoadStateAndGetStateDefensivelyCopyNestedTabState() {
        val service = project.service<ToolWindowStateService>()
        val inputState = ToolWindowState(
            openTabs = listOf(
                TabInfo(
                    branchName = "feature-a",
                    alias = "Feature A",
                    comparisonMap = mutableMapOf("C:/repo-a" to "origin/main")
                )
            ),
            selectedTabIndex = 0
        )

        service.loadState(inputState)
        inputState.openTabs.first().branchName = "mutated-branch"
        inputState.openTabs.first().alias = "Mutated Alias"
        inputState.openTabs.first().comparisonMap["C:/repo-a"] = "mutated/main"

        val firstRead = service.state
        assertEquals(1, firstRead.openTabs.size)
        assertEquals("feature-a", firstRead.openTabs.first().branchName)
        assertEquals("Feature A", firstRead.openTabs.first().alias)
        assertEquals(mapOf("C:/repo-a" to "origin/main"), firstRead.openTabs.first().comparisonMap)
        assertEquals(0, firstRead.selectedTabIndex)
        assertEquals("feature-a", service.getSelectedTabInfo()?.branchName)

        firstRead.openTabs.first().branchName = "leaked-branch"
        firstRead.openTabs.first().alias = "Leaked Alias"
        firstRead.openTabs.first().comparisonMap["C:/repo-a"] = "leaked/main"

        val secondRead = service.state
        assertEquals("feature-a", secondRead.openTabs.first().branchName)
        assertEquals("Feature A", secondRead.openTabs.first().alias)
        assertEquals(mapOf("C:/repo-a" to "origin/main"), secondRead.openTabs.first().comparisonMap)
    }

    fun testNoStateLoadedResetsToHeadSelectionSemantics() {
        val service = project.service<ToolWindowStateService>()
        service.loadState(
            ToolWindowState(
                openTabs = listOf(TabInfo(branchName = "feature-a", alias = "Feature A")),
                selectedTabIndex = 0
            )
        )

        service.noStateLoaded()

        val state = service.state
        assertEmpty(state.openTabs)
        assertEquals(-1, state.selectedTabIndex)
        assertNull(service.getSelectedTabInfo())
        assertNull(service.getSelectedTabBranchName())
    }

    fun testUpdateTabComparisonMapCopiesOverridesWithoutRefreshWhenDisabled() {
        val service = project.service<ToolWindowStateService>()
        service.loadState(
            ToolWindowState(
                openTabs = listOf(
                    TabInfo(
                        branchName = "feature-a",
                        alias = "Feature A",
                        comparisonMap = mutableMapOf("C:/repo-a" to "origin/main")
                    )
                ),
                selectedTabIndex = 0
            )
        )

        val callerOwnedMap = mutableMapOf(
            "C:/repo-a" to "release/1.0",
            "C:/repo-b" to "HEAD"
        )

        service.updateTabComparisonMap("feature-a", callerOwnedMap, triggerRefresh = false)
        callerOwnedMap["C:/repo-a"] = "mutated/main"

        val state = service.state
        assertEquals(0, state.selectedTabIndex)
        assertEquals(1, state.openTabs.size)
        assertEquals("feature-a", state.openTabs.first().branchName)
        assertEquals("Feature A", state.openTabs.first().alias)
        assertEquals(
            mapOf(
                "C:/repo-a" to "release/1.0",
                "C:/repo-b" to "HEAD"
            ),
            state.openTabs.first().comparisonMap
        )
        assertEquals("feature-a", service.getSelectedTabInfo()?.branchName)
    }
}
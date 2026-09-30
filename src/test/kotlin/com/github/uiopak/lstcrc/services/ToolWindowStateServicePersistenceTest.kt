package com.github.uiopak.lstcrc.services

import com.github.uiopak.lstcrc.state.TabInfo
import com.github.uiopak.lstcrc.state.ToolWindowState
import com.intellij.openapi.components.service
import com.github.uiopak.lstcrc.testsupport.LstCrcTestCase

class ToolWindowStateServicePersistenceTest : LstCrcTestCase() {

    fun testAddTabDeduplicatesAndRemoveTabKeepsOtherTabs() {
        val service = project.service<ToolWindowStateService>()

        service.noStateLoaded()
        service.addTab("feature-a")
        service.addTab("feature-a")
        service.addTab("feature-b")

        assertEquals(listOf("feature-a", "feature-b"), service.state.openTabs.map { it.branchName })

        service.removeTab("feature-a")

        val state = service.state
        assertEquals(listOf("feature-b"), state.openTabs.map { it.branchName })
    }

    // A branch picked in a "Select Branch" tab that is not the last tab opens at that tab's position.
    fun testAddTabAtAPositionKeepsTheSelectedTab() {
        val service = project.service<ToolWindowStateService>()
        service.loadState(
            ToolWindowState(
                openTabs = listOf(TabInfo(branchName = "feature-a"), TabInfo(branchName = "feature-b"), TabInfo(branchName = "feature-c")),
                selectedTabIndex = 1
            )
        )

        service.addTab("feature-d", index = 1)

        assertEquals(listOf("feature-a", "feature-d", "feature-b", "feature-c"), service.state.openTabs.map { it.branchName })
        assertEquals("feature-b", service.getSelectedTabInfo()?.branchName)
    }

    fun testRemoveTabClampsSelectedIndexWhenSelectedTabIsRemoved() {
        val service = project.service<ToolWindowStateService>()
        service.loadState(
            ToolWindowState(
                openTabs = listOf(
                    TabInfo(branchName = "feature-a", alias = "Feature A"),
                    TabInfo(branchName = "feature-b", alias = "Feature B")
                ),
                selectedTabIndex = 1
            )
        )

        service.removeTab("feature-b")

        assertEquals(0, service.state.selectedTabIndex)
        assertEquals("feature-a", service.getSelectedTabInfo()?.branchName)
    }

    fun testRemoveTabSelectsTheTabBeforeTheRemovedSelectedTab() {
        val service = project.service<ToolWindowStateService>()
        service.loadState(
            ToolWindowState(
                openTabs = listOf(TabInfo(branchName = "feature-a"), TabInfo(branchName = "feature-b"), TabInfo(branchName = "feature-c")),
                selectedTabIndex = 1
            )
        )

        service.removeTab("feature-b")

        assertEquals("feature-a", service.getSelectedTabInfo()?.branchName)
    }

    fun testRemoveTabShiftsSelectedIndexWhenEarlierTabIsRemoved() {
        val service = project.service<ToolWindowStateService>()
        service.loadState(
            ToolWindowState(
                openTabs = listOf(
                    TabInfo(branchName = "feature-a", alias = "Feature A"),
                    TabInfo(branchName = "feature-b", alias = "Feature B"),
                    TabInfo(branchName = "feature-c", alias = "Feature C")
                ),
                selectedTabIndex = 2
            )
        )

        service.removeTab("feature-a")

        assertEquals(1, service.state.selectedTabIndex)
        assertEquals("feature-c", service.getSelectedTabInfo()?.branchName)
    }

    fun testLoadStateAndGetStateDefensivelyCopyNestedTabState() {
        val service = project.service<ToolWindowStateService>()
        val inputComparisonMap = mutableMapOf("C:/repo-a" to "origin/main")
        val inputState = ToolWindowState(
            openTabs = listOf(
                TabInfo(
                    branchName = "feature-a",
                    alias = "Feature A",
                    comparisonMap = inputComparisonMap
                )
            ),
            selectedTabIndex = 0
        )

        service.loadState(inputState)
        inputComparisonMap["C:/repo-a"] = "mutated/main"

        val firstRead = service.state
        assertEquals(1, firstRead.openTabs.size)
        assertEquals("feature-a", firstRead.openTabs.first().branchName)
        assertEquals("Feature A", firstRead.openTabs.first().alias)
        assertEquals(mapOf("C:/repo-a" to "origin/main"), firstRead.openTabs.first().comparisonMap)
        assertEquals(0, firstRead.selectedTabIndex)
        assertEquals("feature-a", service.getSelectedTabInfo()?.branchName)

        val leakedMap = firstRead.openTabs.first().comparisonMap as? MutableMap<String, String>
        leakedMap?.set("C:/repo-a", "leaked/main")

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

    fun testSelectedTabInfoCannotMutateStoredComparisonTargets() {
        assertLookupCannotMutateStoredComparisonTargets { it.getSelectedTabInfo() }
    }

    fun testDisplayNameLookupCannotMutateStoredComparisonTargets() {
        assertLookupCannotMutateStoredComparisonTargets { it.findTabByDisplayName("Feature A") }
    }

    private fun assertLookupCannotMutateStoredComparisonTargets(lookup: (ToolWindowStateService) -> TabInfo?) {
        val service = project.service<ToolWindowStateService>()
        val expectedTargets = mapOf("C:/repo-a" to "origin/main")
        service.loadState(
            ToolWindowState(
                openTabs = listOf(TabInfo("feature-a", "Feature A", expectedTargets)),
                selectedTabIndex = 0
            )
        )

        val returnedTab = lookup(service) ?: error("Expected the comparison tab")
        (returnedTab.comparisonMap as? MutableMap<String, String>)?.set("C:/repo-a", "changed-without-refresh")
        assertEquals("Changing a lookup result must not bypass state updates", expectedTargets, service.state.openTabs.single().comparisonMap)

        returnedTab.comparisonMap = mapOf("C:/repo-b" to "release/2")
        assertEquals("Replacing a lookup result's targets must not change persisted state", expectedTargets, service.state.openTabs.single().comparisonMap)
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

    fun testUpdateTabAliasUpdatesMatchingTabAndLeavesOtherTabsUntouched() {
        val service = project.service<ToolWindowStateService>()
        service.loadState(
            ToolWindowState(
                openTabs = listOf(
                    TabInfo(branchName = "feature-a", alias = "Feature A"),
                    TabInfo(branchName = "feature-b", alias = "Feature B")
                ),
                selectedTabIndex = 0
            )
        )

        service.updateTabAlias("feature-a", "Renamed A")

        val state = service.state
        assertEquals(listOf("Renamed A", "Feature B"), state.openTabs.map { it.alias })

        service.updateTabAlias("feature-a", null)

        assertEquals(listOf(null, "Feature B"), service.state.openTabs.map { it.alias })
    }

    fun testUpdateTabAliasIgnoresMissingTabAndUnchangedAlias() {
        val service = project.service<ToolWindowStateService>()
        service.loadState(
            ToolWindowState(
                openTabs = listOf(TabInfo(branchName = "feature-a", alias = "Feature A")),
                selectedTabIndex = 0
            )
        )

        val before = service.state
        service.updateTabAlias("missing-branch", "Nope")
        service.updateTabAlias("feature-a", "Feature A")

        val after = service.state
        assertEquals(before.openTabs.map { it.branchName to it.alias }, after.openTabs.map { it.branchName to it.alias })
        assertEquals(before.selectedTabIndex, after.selectedTabIndex)
    }

    fun testUpdateTabComparisonMapIgnoresMissingTabAndUnchangedMap() {
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

        val before = service.state
        service.updateTabComparisonMap("missing-branch", mapOf("C:/repo-a" to "release/1.0"), triggerRefresh = false)
        service.updateTabComparisonMap("feature-a", mapOf("C:/repo-a" to "origin/main"), triggerRefresh = false)

        val after = service.state
        assertEquals(before.openTabs.map { it.branchName to it.comparisonMap.toMap() }, after.openTabs.map { it.branchName to it.comparisonMap.toMap() })
        assertEquals(before.selectedTabIndex, after.selectedTabIndex)
    }

    fun testUpdateTabRepoComparisonRemovesOverrideWhenTargetMatchesDefault() {
        val service = project.service<ToolWindowStateService>()
        service.loadState(
            ToolWindowState(
                openTabs = listOf(
                    TabInfo(
                        branchName = "feature-a",
                        alias = "Feature A",
                        comparisonMap = mutableMapOf(
                            "C:/repo-a" to "feature-a",
                            "C:/repo-b" to "release/1.0"
                        )
                    )
                ),
                selectedTabIndex = 0
            )
        )

        service.updateTabRepoComparison(
            branchName = "feature-a",
            repositoryRootPath = "C:/repo-a",
            targetRevision = "feature-a",
            defaultTarget = "feature-a",
            triggerRefresh = false
        )

        assertEquals(
            mapOf("C:/repo-b" to "release/1.0"),
            service.state.openTabs.first().comparisonMap
        )
    }

    fun testMissingBranchFailureDoesNotOverwriteANewerRepositoryTarget() {
        val service = project.service<ToolWindowStateService>()
        val root = com.intellij.testFramework.LightVirtualFile("repo-a")
        val loadedTab = TabInfo("feature", comparisonMap = mapOf(root.path to "missing"))
        service.loadState(ToolWindowState(openTabs = listOf(loadedTab)))
        service.updateTabRepoComparison("feature", root.path, "fixed", triggerRefresh = false)

        applyBranchFailures(service, loadedTab, mapOf(repository(root) to "missing"))

        assertEquals(mapOf(root.path to "fixed"), service.state.openTabs.single().comparisonMap)
    }

    fun testMissingBranchRepairPreservesNewerOverridesInOtherRepositories() {
        val service = project.service<ToolWindowStateService>()
        val brokenRoot = com.intellij.testFramework.LightVirtualFile("broken")
        val fixedRoot = com.intellij.testFramework.LightVirtualFile("fixed")
        val otherRoot = com.intellij.testFramework.LightVirtualFile("other")
        val loadedTab = TabInfo("feature", comparisonMap = mapOf(brokenRoot.path to "missing", fixedRoot.path to "missing"))
        service.loadState(ToolWindowState(openTabs = listOf(loadedTab)))
        service.updateTabRepoComparison("feature", fixedRoot.path, "fixed-target", triggerRefresh = false)
        service.updateTabRepoComparison("feature", otherRoot.path, "other-target", triggerRefresh = false)

        applyBranchFailures(service, loadedTab, mapOf(repository(brokenRoot) to "missing", repository(fixedRoot) to "missing"))

        assertEquals(
            mapOf(brokenRoot.path to "HEAD", fixedRoot.path to "fixed-target", otherRoot.path to "other-target"),
            service.state.openTabs.single().comparisonMap
        )
    }

    private fun applyBranchFailures(service: ToolWindowStateService, tab: TabInfo, failures: Map<git4idea.repo.GitRepository, String>) {
        ToolWindowStateService::class.java.getDeclaredMethod("handleBranchFailures", TabInfo::class.java, Map::class.java).apply {
            isAccessible = true
        }.invoke(service, tab, failures)
    }

    private fun repository(root: com.intellij.openapi.vfs.VirtualFile): git4idea.repo.GitRepository =
        java.lang.reflect.Proxy.newProxyInstance(
            git4idea.repo.GitRepository::class.java.classLoader,
            arrayOf(git4idea.repo.GitRepository::class.java)
        ) { proxy, method, args ->
            when (method.name) {
                "getRoot" -> root
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                "toString" -> root.path
                else -> null
            }
        } as git4idea.repo.GitRepository
}

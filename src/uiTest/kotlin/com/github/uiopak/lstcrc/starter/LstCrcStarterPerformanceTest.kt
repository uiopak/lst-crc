package com.github.uiopak.lstcrc.starter

import com.github.uiopak.lstcrc.starter.remote.LstCrcUiTestBridgeRemote
import com.intellij.driver.client.service
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.junit5.hyphenateWithClass
import com.intellij.ide.starter.runner.CurrentTestMethod
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

@Tag("starter")
class LstCrcStarterPerformanceTest : LstCrcStarterUiTestBase() {

    @Test
    fun testToolWindowOpenAndBranchLoadPerformance() {
        val project = LstCrcStarterProject.create(CurrentTestMethod.hyphenateWithClass())
        project.initializeGitRepository()
        project.writeFile("Main.txt", "alpha\nbeta\ngamma\n")
        project.commitAll("Initial commit")
        val defaultBranch = project.defaultBranchName()

        // Create a feature branch with several file changes for a realistic diff
        project.createBranch("perf-branch")
        for (i in 1..20) {
            project.writeFile("file$i.txt", "Feature content line $i\n")
        }
        project.writeFile("Main.txt", "alpha modified\nbeta\ngamma\n")
        project.commitAll("Feature commit with many files")
        project.checkout(defaultBranch)

        val context = createTestContext(project)

        context.runLstCrcIdeWithDriver().useDriverAndCloseIde {
            waitForIndicators(5.minutes)
            val bridge = service<LstCrcUiTestBridgeRemote>()
            val starterContext = LstCrcStarterContext(project, bridge)
            starterContext.waitForSmartMode()
            bridge.activateGitVcsIntegration()
            starterContext.waitUntil(30.seconds) { bridge.isGitVcsActive() }
            bridge.refreshProjectAfterExternalChange()

            // Measure: open the Git Changes View tool window
            val openToolWindowTime = measureTime {
                bridge.openGitChangesView()
            }
            // Sanity threshold — catches hangs, not subtle regressions
            assertTrue(
                openToolWindowTime < 10.seconds,
                "Opening the tool window took $openToolWindowTime (expected < 10s)"
            )

            // Measure: create and load a branch tab with 21 changed files
            val createTabTime = measureTime {
                bridge.createAndSelectTab("perf-branch")
                starterContext.waitUntil(60.seconds) {
                    bridge.selectedTabName() == "perf-branch"
                }
                // Wait for the full tree to settle: all 21 files (Main.txt + file1..file20)
                starterContext.waitUntil(60.seconds) {
                    val snapshot = bridge.selectedChangesTreeSnapshot()
                    snapshot.contains("Main.txt") &&
                        (1..20).all { i -> snapshot.contains("file$i.txt") }
                }
            }
            // Sanity threshold — catches hangs, not subtle regressions.
            // For real perf budgets, calibrate from observed CI p95 + margin.
            assertTrue(
                createTabTime < 60.seconds,
                "Creating branch tab and loading 21 changes took $createTabTime (expected < 60s)"
            )

            // Measure: switch between tabs
            bridge.selectTab("HEAD")
            starterContext.waitUntil(20.seconds) { bridge.selectedTabName() == "HEAD" }

            val tabSwitchTime = measureTime {
                bridge.selectTab("perf-branch")
                starterContext.waitUntil(30.seconds) {
                    bridge.selectedTabName() == "perf-branch" &&
                        bridge.selectedChangesTreeSnapshot().contains("Main.txt")
                }
            }
            assertTrue(
                tabSwitchTime < 30.seconds,
                "Switching to branch tab took $tabSwitchTime (expected < 30s)"
            )

            // Measure: refresh after external change
            project.checkout("perf-branch")
            project.writeFile("extra.txt", "Extra file\n")
            project.commitAll("Extra commit")
            project.checkout(defaultBranch)

            val refreshTime = measureTime {
                bridge.refreshProjectAfterExternalChange()
                starterContext.waitUntil(60.seconds) {
                    bridge.selectedChangesTreeSnapshot().contains("extra.txt")
                }
            }
            assertTrue(
                refreshTime < 60.seconds,
                "Refreshing after external change took $refreshTime (expected < 60s)"
            )
        }
    }
}
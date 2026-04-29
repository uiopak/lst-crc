package com.github.uiopak.lstcrc.plugin

import com.automation.remarks.junit5.Video
import com.github.uiopak.lstcrc.plugin.pages.branchSelection
import com.github.uiopak.lstcrc.plugin.pages.gitChangesView
import com.github.uiopak.lstcrc.plugin.pages.idea
import com.github.uiopak.lstcrc.plugin.steps.PluginUiTestSteps
import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.waitFor
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

@LstCrcUiTest
class LstCrcVisualUiTest : LstCrcUiTestSupport() {

    private fun gutterTimeout(): Duration {
        return if (System.getenv("GITHUB_ACTIONS") == "true") Duration.ofSeconds(60) else Duration.ofSeconds(20)
    }

    @Test
    @Video
    fun testVisualGutterMarkersForModifiedAndDeletedRanges(remoteRobot: RemoteRobot) = with(remoteRobot) {
        val uiSteps = PluginUiTestSteps(remoteRobot)

        prepareFreshProject()

        idea {
            step("Wait for smart mode") {
                dumbAware(Duration.ofMinutes(5)) {}
            }

            uiSteps.initializeGitRepository()
            resetGitChangesViewState()

            uiSteps.createNewFile("Modified.txt", "alpha\nbeta\n")
            uiSteps.createNewFile("Removed.txt", "one\ntwo\nthree\n")
            uiSteps.commitChanges("Initial gutter fixtures")
            val defaultBranch = uiSteps.defaultBranchName()

            uiSteps.createBranch("feature-gutter-all")
            uiSteps.createNewFile("BranchOnly.txt", "branch marker\n")
            uiSteps.commitChanges("Branch marker commit")
            uiSteps.checkoutBranch(defaultBranch)

            openGitChangesView()
            gitChangesView {
                addTab()
            }
            branchSelection {
                searchAndSelect("feature-gutter-all")
            }

            setGutterSettings(enableMarkers = true, enableForNewFiles = true)

            uiSteps.modifyFile("Modified.txt", "alpha local\nbeta\n")
            uiSteps.modifyFile("Removed.txt", "one\nthree\n")

            fun assertGutter(fileName: String, expectedRangeType: String) {
                openFile(fileName)
                var latestSummary = ""
                waitFor(gutterTimeout(), interval = Duration.ofMillis(500)) {
                    latestSummary = visualGutterSummaryForSelectedEditor()
                    (
                        latestSummary.contains(expectedRangeType) &&
                        latestSummary.contains("highlighters=") &&
                        !latestSummary.endsWith("highlighters=0")
                    )
                }

                val summary = visualGutterSummaryForSelectedEditor()
                assertTrue(
                    summary.contains(expectedRangeType),
                    "Expected $expectedRangeType gutter range for $fileName, got: $summary (last observed: $latestSummary)"
                )
                assertTrue(
                    summary.contains("highlighters=") && !summary.endsWith("highlighters=0"),
                    "Expected gutter highlighters for $fileName, got: $summary (last observed: $latestSummary)"
                )
            }

            assertGutter("Modified.txt", "MODIFIED")
            assertGutter("Removed.txt", "DELETED")
        }
    }

    @Test
    @Video
    fun testVisualGutterMarkersForInsertedRanges(remoteRobot: RemoteRobot) = with(remoteRobot) {
        val uiSteps = PluginUiTestSteps(remoteRobot)

        prepareFreshProject()

        idea {
            step("Wait for smart mode") {
                dumbAware(Duration.ofMinutes(5)) {}
            }

            uiSteps.initializeGitRepository()
            resetGitChangesViewState()

            uiSteps.createNewFile("Main.txt", "alpha\n")
            uiSteps.commitChanges("Initial commit")
            val defaultBranch = uiSteps.defaultBranchName()

            uiSteps.createBranch("feature-gutter-inserted")
            uiSteps.checkoutBranch(defaultBranch)

            openGitChangesView()
            gitChangesView {
                addTab()
            }
            branchSelection {
                searchAndSelect("feature-gutter-inserted")
            }

            setGutterSettings(enableMarkers = true, enableForNewFiles = true)

            uiSteps.switchToProjectView()
            uiSteps.createNewFile("LocalNew.txt", "local new file\n")

            gitChangesView {
                selectTab("feature-gutter-inserted")
                waitFor(Duration.ofSeconds(10)) {
                    changesTree.findAllText("LocalNew.txt").isNotEmpty()
                }
            }

            var latestActiveDiff = ""
            waitFor(Duration.ofSeconds(10), interval = Duration.ofMillis(500)) {
                latestActiveDiff = activeDiffSnapshot()
                latestActiveDiff.contains("created=LocalNew.txt") || latestActiveDiff.contains("created=LocalNew.txt,")
            }

            openFile("LocalNew.txt")
            waitFor(Duration.ofSeconds(10)) {
                selectedEditorDescriptor().contains("LocalNew.txt")
            }

            var latestSummary = ""
            waitFor(gutterTimeout(), interval = Duration.ofMillis(500)) {
                latestSummary = visualGutterSummaryForSelectedEditor()
                latestSummary.contains("INSERTED") &&
                    latestSummary.contains("highlighters=") &&
                    !latestSummary.endsWith("highlighters=0")
            }

            val summary = visualGutterSummaryForSelectedEditor()
            assertTrue(
                summary.contains("INSERTED"),
                "Expected inserted gutter range, got: $summary (last observed: $latestSummary, active diff: $latestActiveDiff)"
            )
            assertTrue(
                summary.contains("highlighters="),
                "Expected installed gutter highlighters, got: $summary (last observed: $latestSummary, active diff: $latestActiveDiff)"
            )
        }
    }

    @Test
    @Video
    fun testVisualGutterMarkers(remoteRobot: RemoteRobot) = with(remoteRobot) {
        val uiSteps = PluginUiTestSteps(remoteRobot)

        prepareFreshProject()

        idea {
            step("Wait for smart mode") {
                dumbAware(Duration.ofMinutes(5)) {}
            }

            uiSteps.initializeGitRepository()
            resetGitChangesViewState()

            uiSteps.createNewFile("Main.txt", "alpha\nbeta\n")
            uiSteps.commitChanges("Initial commit")
            val defaultBranch = uiSteps.defaultBranchName()

            uiSteps.createBranch("feature-gutter")
            uiSteps.modifyFile("Main.txt", "alpha changed\nbeta\n")
            uiSteps.commitChanges("Gutter commit")
            uiSteps.checkoutBranch(defaultBranch)

            openGitChangesView()
            gitChangesView {
                addTab()
            }
            branchSelection {
                searchAndSelect("feature-gutter")
            }

            uiSteps.modifyFile("Main.txt", "alpha local change\nbeta\n")
            openFile("Main.txt")
            var latestSummary = ""
            waitFor(gutterTimeout(), interval = Duration.ofMillis(500)) {
                latestSummary = visualGutterSummaryForSelectedEditor()
                latestSummary.contains("MODIFIED") && latestSummary.contains("highlighters=") && !latestSummary.endsWith("highlighters=0")
            }

            val summary = visualGutterSummaryForSelectedEditor()
            assertTrue(summary.contains("MODIFIED"), "Expected modified gutter range, got: $summary (last observed: $latestSummary)")
            assertTrue(summary.contains("highlighters="), "Expected installed gutter highlighters, got: $summary (last observed: $latestSummary)")
        }
    }
}
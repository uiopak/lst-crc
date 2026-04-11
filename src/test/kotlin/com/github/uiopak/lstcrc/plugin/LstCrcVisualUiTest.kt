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
            waitFor(Duration.ofSeconds(20), interval = Duration.ofMillis(500)) {
                latestSummary = visualGutterSummaryForSelectedEditor()
                latestSummary.contains("MODIFIED") && latestSummary.contains("highlighters=") && !latestSummary.endsWith("highlighters=0")
            }

            val summary = visualGutterSummaryForSelectedEditor()
            assertTrue(summary.contains("MODIFIED"), "Expected modified gutter range, got: $summary (last observed: $latestSummary)")
            assertTrue(summary.contains("highlighters="), "Expected installed gutter highlighters, got: $summary (last observed: $latestSummary)")
        }
    }
}
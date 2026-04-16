package com.github.uiopak.lstcrc.starter

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Tag("starter")
class LstCrcVisualStarterUiTest : LstCrcStarterUiTestBase() {

    @Test
    fun testVisualGutterMarkers() = runStarterUiTest {
        prepareLstCrc()
        initializeGitRepository()

        createNewFile("Main.txt", "alpha\nbeta\n")
        commitChanges("Initial commit")
        val defaultBranch = defaultBranchName()

        createBranch("feature-gutter")
        modifyFile("Main.txt", "alpha changed\nbeta\n")
        commitChanges("Gutter commit")
        checkoutBranch(defaultBranch)

        openGitChangesView()
        ui.createAndSelectTab("feature-gutter")
        waitForSelectedTab("feature-gutter")

        modifyFile("Main.txt", "alpha local change\nbeta\n")
        ui.openFile("Main.txt")

        var latestSummary = ""
        waitUntil(60.seconds, 500.milliseconds) {
            latestSummary = ui.visualGutterSummaryForSelectedEditor()
            latestSummary.contains("MODIFIED") && latestSummary.contains("highlighters=") && !latestSummary.endsWith("highlighters=0")
        }

        val summary = ui.visualGutterSummaryForSelectedEditor()
        assertTrue(summary.contains("MODIFIED"), "Expected modified gutter range, got: $summary (last observed: $latestSummary)")
        assertTrue(summary.contains("highlighters="), "Expected installed gutter highlighters, got: $summary (last observed: $latestSummary)")
    }
}
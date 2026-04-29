package com.github.uiopak.lstcrc.starter

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Tag("starter")
class LstCrcVisualStarterUiTest : LstCrcStarterUiTestBase() {

    private fun LstCrcStarterContext.assertGutterContains(fileName: String, expectedRangeType: String) {
        ui.openFile(fileName)

        var latestSummary = ""
        waitUntil(60.seconds, 500.milliseconds) {
            latestSummary = ui.visualGutterSummaryForSelectedEditor()
            latestSummary.contains(expectedRangeType) && latestSummary.contains("highlighters=") && !latestSummary.endsWith("highlighters=0")
        }

        val summary = ui.visualGutterSummaryForSelectedEditor()
        assertTrue(summary.contains(expectedRangeType), "Expected $expectedRangeType gutter range for $fileName, got: $summary (last observed: $latestSummary)")
        assertTrue(
            summary.contains("highlighters=") && !summary.endsWith("highlighters=0"),
            "Expected installed gutter highlighters for $fileName, got: $summary (last observed: $latestSummary)"
        )
    }

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
        assertGutterContains("Main.txt", "MODIFIED")
    }

    @Test
    fun testVisualGutterMarkersForInsertedAndDeletedRanges() = runStarterUiTest {
        prepareLstCrc()
        initializeGitRepository()

        createNewFile("Base.txt", "alpha\nbeta\n")
        createNewFile("Removed.txt", "one\ntwo\nthree\n")
        commitChanges("Initial gutter fixtures")
        val defaultBranch = defaultBranchName()

        createBranch("feature-gutter-all")
        createNewFile("BranchOnly.txt", "branch marker\n")
        commitChanges("Branch marker commit")
        checkoutBranch(defaultBranch)

        openGitChangesView()
        ui.createAndSelectTab("feature-gutter-all")
        waitForSelectedTab("feature-gutter-all")

        ui.setGutterSettings(enableMarkers = true, enableForNewFiles = true)

        modifyFile("Removed.txt", "one\nthree\n")
        createNewFile("Local.txt", "Local content\n")

        assertGutterContains("Removed.txt", "DELETED")
        assertGutterContains("Local.txt", "INSERTED")
    }
}
package com.github.uiopak.lstcrc.starter

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("starter")
class LstCrcBranchComparisonStarterUiTest : LstCrcStarterUiTestBase() {

    @Test
    fun testGitBranchComparison() = runStarterUiTest {
        prepareLstCrc()
        initializeGitRepository()

        createNewFile("Main.txt", "Initial content\n")
        commitChanges("Initial commit")
        val defaultBranch = defaultBranchName()

        createBranch("feature-branch")
        createNewFile("Feature.txt", "Feature content\n")
        modifyFile("Main.txt", "Main modified in feature\n")
        commitChanges("Feature commit")
        checkoutBranch(defaultBranch)

        openGitChangesView()
        waitForSelectedTab("HEAD")

        ui.createAndSelectTab("feature-branch")
        waitForSelectedTab("feature-branch")
        waitForTreeContains("Feature.txt", "Main.txt")

        modifyFile("Main.txt", "Modified on master\n")

        ui.selectTab("HEAD")
        waitForSelectedTab("HEAD")
        waitForTreeContains("Main.txt")

        ui.selectTab("feature-branch")
        waitForSelectedTab("feature-branch")
        waitForTreeContains("Main.txt")

        if (ui.scopeExists("LSTCRC.Modified")) {
            waitUntil { ui.scopeContains("LSTCRC.Modified", "Main.txt") }
        }
    }

    @Test
    fun testMultipleComparisonTabs() = runStarterUiTest {
        prepareLstCrc()
        initializeGitRepository()

        createNewFile("Base.txt", "Base content\n")
        commitChanges("Initial commit")
        val defaultBranch = defaultBranchName()

        createBranch("feature-1")
        createNewFile("Feature1.txt", "Feature 1 content\n")
        modifyFile("Base.txt", "Base modified in feature 1\n")
        commitChanges("Feature 1 commit")

        checkoutBranch(defaultBranch)
        createBranch("feature-2")
        createNewFile("Feature2.txt", "Feature 2 content\n")
        commitChanges("Feature 2 commit")
        checkoutBranch(defaultBranch)

        openGitChangesView()
        waitForSelectedTab("HEAD")

        ui.createAndSelectTab("feature-1")
        ui.createAndSelectTab("feature-2")

        ui.selectTab("feature-1")
        waitForSelectedTab("feature-1")
        waitForTreeContains("Feature1.txt", "Base.txt")

        ui.selectTab("feature-2")
        waitForSelectedTab("feature-2")
        waitForTreeContains("Feature2.txt")

        ui.selectTab("HEAD")
        waitForSelectedTab("HEAD")
        waitForTreeNotContains("Feature1.txt", "Feature2.txt", "Base.txt")

        createNewFile("Local.txt", "Local on master\n")

        ui.selectTab("HEAD")
        waitForTreeContains("Local.txt")

        ui.selectTab("feature-1")
        waitForTreeContains("Local.txt", "Feature1.txt")
    }
}
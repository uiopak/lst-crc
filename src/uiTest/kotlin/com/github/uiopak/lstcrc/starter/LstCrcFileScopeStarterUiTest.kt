package com.github.uiopak.lstcrc.starter

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("starter")
class LstCrcFileScopeStarterUiTest : LstCrcStarterUiTestBase() {

    @Test
    fun testFileOperations() = runStarterUiTest {
        prepareLstCrc()
        initializeGitRepository()

        createNewFile("ToMove.txt", "Original content\n")
        createNewFile("ToDelete.txt", "I'm about to disappear\n")
        commitChanges("Initial files")
        val defaultBranch = defaultBranchName()

        createBranch("base-branch")
        checkoutBranch(defaultBranch)

        renameFile("ToMove.txt", "Moved.txt")
        deleteFile("ToDelete.txt")
        createNewFile("NewFile.txt", "Brand new\n")

        openGitChangesView()
        ui.setIncludeHeadInScopes(true)
        ui.selectTab("HEAD")
        waitForSelectedTab("HEAD")
        waitForTreeContains("Moved.txt", "ToDelete.txt", "NewFile.txt")

        if (ui.scopeExists("LSTCRC.Moved")) {
            waitUntil { ui.scopeContains("LSTCRC.Moved", "Moved.txt") }
        }
        if (ui.scopeExists("LSTCRC.Created")) {
            waitUntil { ui.scopeContains("LSTCRC.Created", "NewFile.txt") }
        }
    }

    @Test
    fun testPermanentHeadTabScopesStayEmptyUntilIncludeHeadIsEnabled() = runStarterUiTest {
        prepareLstCrc()
        initializeGitRepository()

        createNewFile("Base.txt", "base\n")
        commitChanges("Initial commit")
        val defaultBranch = defaultBranchName()

        createBranch("feature-head-semantics")
        createNewFile("BranchOnly.txt", "branch only\n")
        commitChanges("Feature commit")
        checkoutBranch(defaultBranch)

        createNewFile("HeadOnly.txt", "local head file\n")

        openGitChangesView()
        ui.createAndSelectTab("feature-head-semantics")
        waitForSelectedTab("feature-head-semantics")
        assertTrue(ui.hasTab("HEAD"))

        ui.selectTab("HEAD")
        waitForSelectedTab("HEAD")

        if (ui.scopeExists("LSTCRC.Created")) {
            waitUntil { !ui.scopeContains("LSTCRC.Created", "HeadOnly.txt") }
        }
        if (ui.scopeExists("LSTCRC.Changed")) {
            waitUntil { !ui.scopeContains("LSTCRC.Changed", "HeadOnly.txt") }
        }

        ui.setIncludeHeadInScopes(true)
        if (ui.scopeExists("LSTCRC.Created")) {
            waitUntil { ui.scopeContains("LSTCRC.Created", "HeadOnly.txt") }
        }
        if (ui.scopeExists("LSTCRC.Changed")) {
            waitUntil { ui.scopeContains("LSTCRC.Changed", "HeadOnly.txt") }
        }

        ui.setIncludeHeadInScopes(false)
        if (ui.scopeExists("LSTCRC.Created")) {
            waitUntil { !ui.scopeContains("LSTCRC.Created", "HeadOnly.txt") }
        }
        if (ui.scopeExists("LSTCRC.Changed")) {
            waitUntil { !ui.scopeContains("LSTCRC.Changed", "HeadOnly.txt") }
        }
    }
}
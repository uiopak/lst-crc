package com.github.uiopak.lstcrc.starter

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
}
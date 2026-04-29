package com.github.uiopak.lstcrc.starter

import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.common.popups.findInPathPopup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
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
        if (ui.scopeExists("LSTCRC.Changed")) {
            waitUntil {
                ui.scopeContains("LSTCRC.Changed", "Moved.txt") &&
                    ui.scopeContains("LSTCRC.Changed", "NewFile.txt")
            }
        }

        val searchScopes = ui.searchScopesSnapshot()
        assertTrue(searchScopes.contains("LSTCRC: Created Files"))
        assertTrue(searchScopes.contains("LSTCRC: Moved Files"))
        assertTrue(searchScopes.contains("LSTCRC: Changed Files"))
        assertFalse(searchScopes.contains("LSTCRC: Deleted Files"))

        waitUntil { ui.searchScopeContains("LSTCRC: Created Files", "NewFile.txt") }
        waitUntil { ui.searchScopeContains("LSTCRC: Moved Files", "Moved.txt") }
        waitUntil {
            ui.searchScopeContains("LSTCRC: Changed Files", "Moved.txt") &&
                ui.searchScopeContains("LSTCRC: Changed Files", "NewFile.txt")
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
        waitUntil { !ui.searchScopeContains("LSTCRC: Created Files", "HeadOnly.txt") }
        waitUntil { !ui.searchScopeContains("LSTCRC: Changed Files", "HeadOnly.txt") }

        ui.setIncludeHeadInScopes(true)
        if (ui.scopeExists("LSTCRC.Created")) {
            waitUntil { ui.scopeContains("LSTCRC.Created", "HeadOnly.txt") }
        }
        if (ui.scopeExists("LSTCRC.Changed")) {
            waitUntil { ui.scopeContains("LSTCRC.Changed", "HeadOnly.txt") }
        }
        waitUntil { ui.searchScopeContains("LSTCRC: Created Files", "HeadOnly.txt") }
        waitUntil { ui.searchScopeContains("LSTCRC: Changed Files", "HeadOnly.txt") }

        ui.setIncludeHeadInScopes(false)
        if (ui.scopeExists("LSTCRC.Created")) {
            waitUntil { !ui.scopeContains("LSTCRC.Created", "HeadOnly.txt") }
        }
        if (ui.scopeExists("LSTCRC.Changed")) {
            waitUntil { !ui.scopeContains("LSTCRC.Changed", "HeadOnly.txt") }
        }
        waitUntil { !ui.searchScopeContains("LSTCRC: Created Files", "HeadOnly.txt") }
        waitUntil { !ui.searchScopeContains("LSTCRC: Changed Files", "HeadOnly.txt") }
    }

    @Test
    fun testIncludeHeadInScopesDoesNotAffectBranchTabScopes() = runStarterUiTest {
        prepareLstCrc()
        initializeGitRepository()

        createNewFile("Base.txt", "base\n")
        commitChanges("Initial commit")
        val defaultBranch = defaultBranchName()

        createBranch("feature-head-independent")
        createNewFile("BranchMarker.txt", "branch marker\n")
        commitChanges("Feature commit")
        checkoutBranch(defaultBranch)

        createNewFile("LocalOnly.txt", "local only\n")

        openGitChangesView()
        ui.createAndSelectTab("feature-head-independent")
        waitForSelectedTab("feature-head-independent")
        waitForTreeContains("LocalOnly.txt")

        ui.setIncludeHeadInScopes(false)
        if (ui.scopeExists("LSTCRC.Created")) {
            waitUntil { ui.scopeContains("LSTCRC.Created", "LocalOnly.txt") }
        }
        if (ui.scopeExists("LSTCRC.Changed")) {
            waitUntil { ui.scopeContains("LSTCRC.Changed", "LocalOnly.txt") }
        }
        waitUntil { ui.searchScopeContains("LSTCRC: Created Files", "LocalOnly.txt") }
        waitUntil { ui.searchScopeContains("LSTCRC: Changed Files", "LocalOnly.txt") }

        ui.setIncludeHeadInScopes(true)
        if (ui.scopeExists("LSTCRC.Created")) {
            waitUntil { ui.scopeContains("LSTCRC.Created", "LocalOnly.txt") }
        }
        if (ui.scopeExists("LSTCRC.Changed")) {
            waitUntil { ui.scopeContains("LSTCRC.Changed", "LocalOnly.txt") }
        }
        waitUntil { ui.searchScopeContains("LSTCRC: Created Files", "LocalOnly.txt") }
        waitUntil { ui.searchScopeContains("LSTCRC: Changed Files", "LocalOnly.txt") }
    }

    @Test
    fun testFindDialogShowsLstCrcSearchScopes() = runStarterUiTest {
        prepareLstCrc()
        initializeGitRepository()

        createNewFile("Base.txt", "base\n")
        commitChanges("Initial commit")
        val defaultBranch = defaultBranchName()

        createBranch("feature-find-scopes")
        createNewFile("BranchOnly.txt", "branch only\n")
        commitChanges("Feature commit")
        checkoutBranch(defaultBranch)

        createNewFile("HeadOnly.txt", "head only\n")

        openGitChangesView()
        ui.createAndSelectTab("feature-find-scopes")
        waitForSelectedTab("feature-find-scopes")

        driver.invokeAction("FindInPath", now = false)

        var scopes = emptyList<String>()
        waitUntil {
            runCatching {
                driver.ideFrame {
                    findInPathPopup {
                        focus()
                        scopeActionButton.click()
                        scopes = scopeChooserComboBox.listValues()
                    }
                }
                true
            }.getOrDefault(false) &&
                scopes.contains("LSTCRC: Created Files") &&
                scopes.contains("LSTCRC: Modified Files") &&
                scopes.contains("LSTCRC: Moved Files") &&
                scopes.contains("LSTCRC: Changed Files") &&
                !scopes.contains("LSTCRC: Deleted Files")
        }

        driver.ideFrame {
            findInPathPopup {
                close()
            }
        }
    }

    @Test
    fun testDeletedFilesUseDeletedScopeTreeColor() = runStarterUiTest {
        prepareLstCrc()
        initializeGitRepository()

        createNewFile("ToDelete.txt", "delete me\n")
        commitChanges("Initial commit")
        val defaultBranch = defaultBranchName()

        createBranch("feature-delete-color")
        createNewFile("BranchMarker.txt", "branch marker\n")
        commitChanges("Feature commit")
        checkoutBranch(defaultBranch)

        deleteFile("ToDelete.txt")

        openGitChangesView()
        ui.createAndSelectTab("feature-delete-color")
        waitForSelectedTab("feature-delete-color")
        waitForTreeContains("ToDelete.txt")
        waitUntil { ui.selectedTreeFileColor("ToDelete.txt").isNotBlank() }

        assertEquals(ui.deletedScopeColorSnapshot(), ui.selectedTreeFileColor("ToDelete.txt"))
    }

    @Test
    fun testDeletedFileColorDoesNotLeakToModifiedRows() = runStarterUiTest {
        prepareLstCrc()
        initializeGitRepository()

        createNewFile("Main.txt", "base\n")
        createNewFile("ToDelete.txt", "delete me\n")
        commitChanges("Initial commit")
        val defaultBranch = defaultBranchName()

        createBranch("feature-delete-contrast")
        createNewFile("BranchMarker.txt", "branch marker\n")
        commitChanges("Feature commit")
        checkoutBranch(defaultBranch)

        modifyFile("Main.txt", "local modified\n")
        deleteFile("ToDelete.txt")

        openGitChangesView()
        ui.createAndSelectTab("feature-delete-contrast")
        waitForSelectedTab("feature-delete-contrast")
        waitForTreeContains("Main.txt", "ToDelete.txt")

        waitUntil {
            ui.selectedTreeFileColor("ToDelete.txt").isNotBlank() &&
                ui.selectedTreeFileColor("Main.txt").isNotBlank()
        }

        val deletedColor = ui.deletedScopeColorSnapshot()
        assertEquals(deletedColor, ui.selectedTreeFileColor("ToDelete.txt"))
        assertNotEquals(deletedColor, ui.selectedTreeFileColor("Main.txt"))
    }
}
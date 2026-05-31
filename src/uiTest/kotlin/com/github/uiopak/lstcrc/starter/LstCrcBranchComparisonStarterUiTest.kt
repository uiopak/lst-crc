package com.github.uiopak.lstcrc.starter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import kotlin.time.Duration.Companion.seconds

@Tag("starter")
class LstCrcBranchComparisonStarterUiTest : LstCrcStarterUiTestBase() {

    @Test
    fun testBranchComparisonLineStatsIgnoreLineEndingOnlyChanges() = runStarterUiTest {
        prepareLstCrc()
        initializeGitRepository()

        createNewFile(".gitattributes", "*.txt -text\n")
        createNewFile("Main.txt", "alpha\r\nbeta\r\ngamma\r\n")
        commitChanges("Initial CRLF fixture")
        val defaultBranch = defaultBranchName()

        createBranch("feature-line-endings")
        modifyFile("Main.txt", "alpha changed\nbeta\ngamma\n")
        commitChanges("Feature LF fixture")
        checkoutBranch(defaultBranch)

        val rawNumstat = project.runGit("diff", "--numstat", "feature-line-endings")
        assertTrue(
            rawNumstat.lineSequence().any { it == "3\t3\tMain.txt" },
            "Expected raw git numstat to still count CRLF/LF churn, got: $rawNumstat"
        )

        openGitChangesView()
        waitForSelectedTab("HEAD")

        ui.createAndSelectTab("feature-line-endings")
        waitForSelectedTab("feature-line-endings")
        waitForTreeContains("Main.txt")

        ui.setTreeContextSettings(showSingleRepo = null, showCommits = null, showLineStats = true)

        var lastRows = ""
        waitUntil(20.seconds) {
            lastRows = ui.selectedRenderedRowsSnapshot()
            lastRows.lineSequence().any { row ->
                row.contains("Main.txt") &&
                    row.contains("+1") &&
                    row.contains("-1") &&
                    !row.contains("+3") &&
                    !row.contains("-3")
            }
        }

        assertTrue(
            lastRows.lineSequence().any { row ->
                row.contains("Main.txt") && row.contains("+1") && row.contains("-1")
            },
            "Expected rendered tree rows to show +1/-1 for Main.txt, got: $lastRows"
        )
        assertFalse(
            lastRows.lineSequence().any { row ->
                row.contains("Main.txt") && (row.contains("+3") || row.contains("-3"))
            },
            "Rendered tree rows should ignore line-ending-only churn, got: $lastRows"
        )
    }

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

    @Test
    fun testTreeStatePersistsAcrossTabSwitches() = runStarterUiTest {
        prepareLstCrc()
        initializeGitRepository()

        createNewFile("Base.txt", "Base content\n")
        commitChanges("Initial commit")
        val defaultBranch = defaultBranchName()

        createBranch("feature-tree-a")
        createNewFile("nested/featureA/OnlyA.txt", "Only in A\n")
        commitChanges("Feature A commit")

        checkoutBranch(defaultBranch)
        createBranch("feature-tree-b")
        createNewFile("nested/featureB/OnlyB.txt", "Only in B\n")
        commitChanges("Feature B commit")
        checkoutBranch(defaultBranch)

        openGitChangesView()
        waitForSelectedTab("HEAD")

        ui.createAndSelectTab("feature-tree-a")
        ui.createAndSelectTab("feature-tree-b")

        ui.selectTab("feature-tree-a")
        waitForSelectedTab("feature-tree-a")
        waitForTreeContains("OnlyA.txt")

        ui.setSelectedTreeNodeExpanded("nested", true)
        waitUntil {
            ui.selectedExpandedTreeNodesSnapshot().contains("nested")
        }

        ui.setSelectedTreeNodeExpanded("nested", false)
        waitUntil {
            val visibleRows = ui.selectedRenderedRowsSnapshot()
            val expandedNodes = ui.selectedExpandedTreeNodesSnapshot()
            !visibleRows.contains("OnlyA.txt") && !expandedNodes.contains("nested")
        }

        ui.selectTab("feature-tree-b")
        waitForSelectedTab("feature-tree-b")
        waitForTreeContains("OnlyB.txt")

        ui.selectTab("feature-tree-a")
        waitForSelectedTab("feature-tree-a")

        var lastVisibleRows = ""
        var lastExpandedNodes = ""
        var restoredCollapsedState = false
        val deadlineNanos = System.nanoTime() + 20.seconds.inWholeNanoseconds
        while (System.nanoTime() < deadlineNanos) {
            lastVisibleRows = ui.selectedRenderedRowsSnapshot()
            lastExpandedNodes = ui.selectedExpandedTreeNodesSnapshot()
            if (!lastVisibleRows.contains("OnlyA.txt") && !lastExpandedNodes.contains("nested")) {
                restoredCollapsedState = true
                break
            }
            Thread.sleep(250)
        }

        assertTrue(
            restoredCollapsedState,
            "Tree state was not restored after tab switch. visibleRows='$lastVisibleRows', expandedNodes='$lastExpandedNodes'"
        )

        ui.setSelectedTreeNodeExpanded("nested", true)
        waitForTreeContains("OnlyA.txt")
    }

    @Test
    fun testNewFileInCollapsedDirExpandsDirWhenSettingEnabled() = runStarterUiTest {
        prepareLstCrc()
        initializeGitRepository()

        createNewFile("Base.txt", "Base content\n")
        commitChanges("Initial commit")
        val defaultBranch = defaultBranchName()

        // Branch with one nested file — used as initial comparison
        createBranch("feature-expand-initial")
        createNewFile("nested/featureA/ExistingA.txt", "Existing file\n")
        commitChanges("Add existing nested file")
        checkoutBranch(defaultBranch)

        // Branch with two nested files — used as the updated comparison
        createBranch("feature-expand-updated")
        createNewFile("nested/featureA/ExistingA.txt", "Existing file\n")
        createNewFile("nested/featureA/NewA.txt", "New file\n")
        commitChanges("Add both nested files")
        checkoutBranch(defaultBranch)

        openGitChangesView()
        waitForSelectedTab("HEAD")

        // Open tab comparing against initial branch (shows only ExistingA.txt)
        ui.createAndSelectTab("feature-expand-initial")
        waitForSelectedTab("feature-expand-initial")
        waitForTreeContains("ExistingA.txt")

        // Expand then collapse the nested dir
        ui.setSelectedTreeNodeExpanded("nested", true)
        waitUntil { ui.selectedExpandedTreeNodesSnapshot().contains("nested") }
        ui.setSelectedTreeNodeExpanded("nested", false)
        waitUntil {
            !ui.selectedRenderedRowsSnapshot().contains("ExistingA.txt") &&
                !ui.selectedExpandedTreeNodesSnapshot().contains("nested")
        }

        // Setting is ON — new file in collapsed dir should cause dir to expand
        ui.setExpandNewFilesInCollapsedDirs(true)

        // Switch comparison to the updated branch; NewA.txt is a new change in the collapsed dir
        ui.setBranchAsRepoComparison("feature-expand-updated")

        var lastVisibleRows = ""
        var lastExpandedNodes = ""
        var dirExpanded = false
        val deadlineNanos = System.nanoTime() + 20.seconds.inWholeNanoseconds
        while (System.nanoTime() < deadlineNanos) {
            lastVisibleRows = ui.selectedRenderedRowsSnapshot()
            lastExpandedNodes = ui.selectedExpandedTreeNodesSnapshot()
            if (lastExpandedNodes.contains("nested") && lastVisibleRows.contains("NewA.txt")) {
                dirExpanded = true
                break
            }
            Thread.sleep(250)
        }

        assertTrue(
            dirExpanded,
            "Collapsed dir 'nested' should expand when a new file appears and the setting is enabled. " +
                "visibleRows='$lastVisibleRows', expandedNodes='$lastExpandedNodes'"
        )
    }

    @Test
    fun testNewFileInCollapsedDirKeepsDirCollapsedWhenSettingDisabled() = runStarterUiTest {
        prepareLstCrc()
        initializeGitRepository()

        createNewFile("Base.txt", "Base content\n")
        commitChanges("Initial commit")
        val defaultBranch = defaultBranchName()

        // Branch with one nested file — used as initial comparison
        createBranch("feature-collapse-initial")
        createNewFile("nested/featureB/ExistingB.txt", "Existing file\n")
        commitChanges("Add existing nested file")
        checkoutBranch(defaultBranch)

        // Branch with two nested files — used as the updated comparison
        createBranch("feature-collapse-updated")
        createNewFile("nested/featureB/ExistingB.txt", "Existing file\n")
        createNewFile("nested/featureB/NewB.txt", "New file\n")
        commitChanges("Add both nested files")
        checkoutBranch(defaultBranch)

        openGitChangesView()
        waitForSelectedTab("HEAD")

        // Open tab comparing against initial branch (shows only ExistingB.txt)
        ui.createAndSelectTab("feature-collapse-initial")
        waitForSelectedTab("feature-collapse-initial")
        waitForTreeContains("ExistingB.txt")

        // Expand then collapse the nested dir
        ui.setSelectedTreeNodeExpanded("nested", true)
        waitUntil { ui.selectedExpandedTreeNodesSnapshot().contains("nested") }
        ui.setSelectedTreeNodeExpanded("nested", false)
        waitUntil {
            !ui.selectedRenderedRowsSnapshot().contains("ExistingB.txt") &&
                !ui.selectedExpandedTreeNodesSnapshot().contains("nested")
        }

        // Setting is OFF — collapsed dir should stay collapsed even when a new file appears
        ui.setExpandNewFilesInCollapsedDirs(false)

        // Switch comparison to the updated branch; NewB.txt is a new change in the collapsed dir
        ui.setBranchAsRepoComparison("feature-collapse-updated")

        // Wait for the tree to stabilize with the new comparison
        waitUntil { ui.selectedChangesTreeSnapshot().contains("NewB.txt") || ui.selectedChangesTreeSnapshot().contains("ExistingB.txt") }

        var lastVisibleRows = ""
        var lastExpandedNodes = ""
        var dirStaysCollapsed = false
        val deadlineNanos = System.nanoTime() + 20.seconds.inWholeNanoseconds
        while (System.nanoTime() < deadlineNanos) {
            lastVisibleRows = ui.selectedRenderedRowsSnapshot()
            lastExpandedNodes = ui.selectedExpandedTreeNodesSnapshot()
            if (!lastExpandedNodes.contains("nested") && !lastVisibleRows.contains("NewB.txt")) {
                dirStaysCollapsed = true
                break
            }
            Thread.sleep(250)
        }

        assertTrue(
            dirStaysCollapsed,
            "Collapsed dir 'nested' should stay collapsed when a new file appears and the setting is disabled. " +
                "visibleRows='$lastVisibleRows', expandedNodes='$lastExpandedNodes'"
        )
    }

    @Test
    fun testUntrackedFileAppearsWhenSettingEnabled() = runStarterUiTest {
        prepareLstCrc()
        initializeGitRepository()

        createNewFile("Base.txt", "Base content\n")
        commitChanges("Initial commit")

        openGitChangesView()
        waitForSelectedTab("HEAD")

        ui.setShowUntrackedFilesAsNew(true)
        writeUntrackedFile("UntrackedEnabled.txt", "Visible when enabled\n")
        ui.refreshProjectAfterExternalChange()

        waitUntil {
            ui.selectedChangesTreeSnapshot().contains("UntrackedEnabled.txt")
        }

        assertTrue(
            ui.selectedChangesTreeSnapshot().contains("UntrackedEnabled.txt"),
            "Untracked file should be visible when 'Show Untracked Files as New Changes' is enabled"
        )
    }

    @Test
    fun testUntrackedFileStaysHiddenWhenSettingDisabled() = runStarterUiTest {
        prepareLstCrc()
        initializeGitRepository()

        createNewFile("Base.txt", "Base content\n")
        commitChanges("Initial commit")

        openGitChangesView()
        waitForSelectedTab("HEAD")

        ui.setShowUntrackedFilesAsNew(false)
        writeUntrackedFile("UntrackedDisabled.txt", "Hidden when disabled\n")
        ui.refreshProjectAfterExternalChange()

        var lastSnapshot = ""
        var hiddenForWindow = true
        val deadlineNanos = System.nanoTime() + 10.seconds.inWholeNanoseconds
        while (System.nanoTime() < deadlineNanos) {
            lastSnapshot = ui.selectedChangesTreeSnapshot()
            if (lastSnapshot.contains("UntrackedDisabled.txt")) {
                hiddenForWindow = false
                break
            }
            Thread.sleep(250)
        }

        assertTrue(
            hiddenForWindow,
            "Untracked file should stay hidden when the setting is disabled. snapshot='$lastSnapshot'"
        )

        ui.setShowUntrackedFilesAsNew(true)
        ui.refreshProjectAfterExternalChange()
        waitUntil {
            ui.selectedChangesTreeSnapshot().contains("UntrackedDisabled.txt")
        }
    }

    @Test
    fun testUntrackedFileHasUnknownFileStatus() = runStarterUiTest {
        prepareLstCrc()
        initializeGitRepository()

        createNewFile("Base.txt", "Base content\n")
        commitChanges("Initial commit")

        val defaultBranch = defaultBranchName()
        createBranch("feature-file-status")
        createNewFile("TrackedAdded.txt", "Tracked content\n")
        commitChanges("Add tracked file")
        checkoutBranch(defaultBranch)

        openGitChangesView()
        ui.createAndSelectTab("feature-file-status")
        waitForSelectedTab("feature-file-status")
        waitUntil {
            ui.selectedChangesTreeSnapshot().contains("TrackedAdded.txt")
        }

        ui.setShowUntrackedFilesAsNew(true)
        writeUntrackedFile("UntrackedStatus.txt", "Untracked content\n")
        ui.refreshProjectAfterExternalChange()
        waitUntil {
            ui.selectedChangesTreeSnapshot().contains("UntrackedStatus.txt")
        }

        val untrackedStatus = ui.fileStatusForTreeItem("UntrackedStatus.txt")
        val addedStatus = ui.fileStatusForTreeItem("TrackedAdded.txt")

        assertEquals("UNKNOWN", untrackedStatus,
            "Untracked file should have UNKNOWN file status so it renders with the native untracked text color")
        assertNotEquals(untrackedStatus, addedStatus,
            "Untracked file and added file should have different file statuses")
    }

    @Test
    fun testFileTypeFileStatuses() = runStarterUiTest {
        prepareLstCrc()
        initializeGitRepository()

        createNewFile("ToModify.txt", "original\n")
        createNewFile("ToDelete.txt", "to be deleted\n")
        createNewFile("ToRename.txt", "to be renamed\n")
        commitChanges("Initial commit")

        val defaultBranch = defaultBranchName()
        createBranch("feature-change-types")

        modifyFile("ToModify.txt", "modified content\n")
        deleteFile("ToDelete.txt")
        renameFile("ToRename.txt", "Renamed.txt")
        createNewFile("NewFile.txt", "brand new\n")
        commitChanges("All change types")

        checkoutBranch(defaultBranch)

        openGitChangesView()
        ui.createAndSelectTab("feature-change-types")
        waitForSelectedTab("feature-change-types")
        waitUntil {
            val snapshot = ui.selectedChangesTreeSnapshot()
            snapshot.contains("ToModify.txt") && snapshot.contains("NewFile.txt") &&
                snapshot.contains("ToDelete.txt") && snapshot.contains("ToRename.txt")
        }

        assertEquals("DELETED", ui.fileStatusForTreeItem("NewFile.txt"),
            "File only in comparison branch should have DELETED file status (not present in working dir)")
        assertEquals("MODIFIED", ui.fileStatusForTreeItem("ToModify.txt"),
            "Modified file should have MODIFIED file status")
        assertEquals("ADDED", ui.fileStatusForTreeItem("ToDelete.txt"),
            "File only in working dir should have ADDED file status (deleted in comparison branch)")
        val renamedStatus = ui.fileStatusForTreeItem("ToRename.txt")
        assertEquals("MODIFIED", renamedStatus,
            "Renamed file should have MODIFIED file status (rename detected)")
    }

    private fun LstCrcStarterContext.writeUntrackedFile(relativePath: String, content: String) {
        val filePath = project.path.resolve(relativePath)
        filePath.parent?.let(Files::createDirectories)
        Files.writeString(
            filePath,
            content,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )
    }
}
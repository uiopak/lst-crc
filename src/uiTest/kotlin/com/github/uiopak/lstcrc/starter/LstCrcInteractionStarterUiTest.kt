package com.github.uiopak.lstcrc.starter

import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.elements.actionButton
import com.intellij.driver.sdk.ui.components.elements.dialog
import com.intellij.driver.sdk.ui.components.elements.tree
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

@Tag("starter")
class LstCrcInteractionStarterUiTest : LstCrcStarterUiTestBase() {

    @Test
    fun testToolWindowClickActions() = runStarterUiTest {
        prepareLstCrc()
        initializeGitRepository()

        createNewFile("Main.txt", "Base line\n")
        commitChanges("Initial commit")
        val defaultBranch = defaultBranchName()

        createBranch("feature-clicks")
        modifyFile("Main.txt", "Feature line\n")
        createNewFile("Feature.txt", "Feature file\n")
        commitChanges("Feature commit")
        checkoutBranch(defaultBranch)

        openGitChangesView()
        ui.createAndSelectTab("feature-clicks")
        waitForSelectedTab("feature-clicks")

        ui.configureClickActions(
            singleClickAction = "OPEN_SOURCE",
            doubleClickAction = "NONE",
            middleClickAction = null,
            doubleMiddleClickAction = null,
            rightClickAction = "OPEN_SOURCE",
            doubleRightClickAction = null,
            showContextMenu = false
        )

        ui.triggerConfiguredChangeInteraction("Feature.txt", "LEFT", 1)
        waitUntil(15.seconds) { ui.selectedEditorDescriptor().contains("Feature.txt") }

        ui.triggerConfiguredChangeInteraction("Main.txt", "RIGHT", 1)
        waitUntil(15.seconds) { ui.selectedEditorDescriptor().contains("Main.txt") }

        ui.configureClickActions(
            singleClickAction = "NONE",
            doubleClickAction = "OPEN_DIFF",
            middleClickAction = null,
            doubleMiddleClickAction = null,
            rightClickAction = null,
            doubleRightClickAction = null,
            showContextMenu = null
        )
        ui.triggerConfiguredChangeInteraction("Main.txt", "LEFT", 2)
        waitUntil(15.seconds) { ui.hasDiffEditorOpen() }
    }

    @Test
    fun testContextMenuActionsWhenEnabled() = runStarterUiTest {
        prepareLstCrc()
        initializeGitRepository()

        createNewFile("Main.txt", "Base line\n")
        commitChanges("Initial commit")
        val defaultBranch = defaultBranchName()

        createBranch("feature-context")
        modifyFile("Main.txt", "Feature context line\n")
        commitChanges("Feature context commit")
        checkoutBranch(defaultBranch)

        openGitChangesView()
        ui.createAndSelectTab("feature-context")
        waitForSelectedTab("feature-context")
        waitForTreeContains("Main.txt")

        ui.configureClickActions(null, null, null, null, null, null, true)
        val actions = ui.contextMenuActionsForFile("Main.txt")
        assertTrue(actions.contains("Show Diff"))
        assertTrue(actions.contains("Open Source"))
        assertTrue(actions.contains("Show in Project Tree"))
    }

    @Test
    fun testStatusWidgetAndRevisionActions() = runStarterUiTest {
        prepareLstCrc()
        initializeGitRepository()

        createNewFile("Main.txt", "Base line\n")
        commitChanges("Initial commit")
        val defaultBranch = defaultBranchName()

        createBranch("feature-widget")
        modifyFile("Main.txt", "Feature widget line\n")
        commitChanges("Feature widget commit")
        val featureRevision = gitRevision("HEAD")

        checkoutBranch(defaultBranch)
        createBranch("feature-widget-2")
        createNewFile("Second.txt", "Second branch\n")
        commitChanges("Second feature commit")
        checkoutBranch(defaultBranch)

        openGitChangesView()
        ui.createAndSelectTab("feature-widget")
        waitForSelectedTab("feature-widget")
        waitUntil { ui.statusWidgetText().contains("feature-widget") }

        ui.selectStatusWidgetEntry("HEAD")
        waitForSelectedTab("HEAD")

        ui.selectStatusWidgetEntry("feature-widget")
        waitUntil { ui.selectedTabName() == "feature-widget" && ui.statusWidgetText().contains("feature-widget") }

        ui.createAndSelectTab("feature-widget-2")
        waitUntil { ui.hasTab("feature-widget-2") }

        ui.createRevisionTab(featureRevision, "feature-revision")
        waitUntil { ui.selectedTabName() == "feature-revision" || ui.selectedTabName() == featureRevision }

        ui.selectStatusWidgetEntry("feature-revision")
        waitUntil {
            (ui.selectedTabName() == "feature-revision" || ui.selectedTabName() == featureRevision) &&
                ui.statusWidgetText().contains("feature-revision")
        }

        ui.selectStatusWidgetEntry("feature-widget")
        waitUntil { ui.selectedTabName() == "feature-widget" }

        ui.setRevisionAsRepoComparison(featureRevision)
        waitUntil(15.seconds) { ui.selectedTabComparisonMap().contains(featureRevision) }
    }

    @Test
    fun testTabRenameUpdatesWidgetContext() = runStarterUiTest {
        prepareLstCrc()
        initializeGitRepository()

        createNewFile("Main.txt", "Base line\n")
        commitChanges("Initial commit")
        val defaultBranch = defaultBranchName()

        createBranch("feature-rename")
        modifyFile("Main.txt", "Renamed alias line\n")
        commitChanges("Rename alias commit")
        checkoutBranch(defaultBranch)

        openGitChangesView()
        ui.createAndSelectTab("feature-rename")
        waitUntil { ui.statusWidgetText().contains("feature-rename") }

        ui.updateTabAlias("feature-rename", "renamed-feature")
        waitUntil {
            ui.hasTab("renamed-feature") &&
                ui.selectedTabName() == "renamed-feature" &&
                ui.statusWidgetText().contains("renamed-feature")
        }

        ui.setShowWidgetContext(true)
        waitUntil { ui.statusWidgetText().startsWith("Context:") && ui.statusWidgetText().contains("renamed-feature") }

        ui.setShowWidgetContext(false)
        waitUntil { !ui.statusWidgetText().startsWith("Context:") }

        ui.updateTabAlias("feature-rename", null)
        waitUntil {
            ui.hasTab("feature-rename") &&
                ui.selectedTabName() == "feature-rename" &&
                ui.statusWidgetText().contains("feature-rename")
        }
    }

    @Test
    fun testMissingBranchComparisonTargetRecoversToHeadAndShowsWarning() = runStarterUiTest {
        prepareLstCrc()
        initializeGitRepository()

        createNewFile("Main.txt", "Base line\n")
        commitChanges("Initial commit")
        val defaultBranch = defaultBranchName()

        createBranch("feature-recovery")
        createNewFile("Feature.txt", "Feature branch file\n")
        commitChanges("Feature commit")
        checkoutBranch(defaultBranch)

        createBranch("missing-target")
        modifyFile("Main.txt", "Missing target line\n")
        commitChanges("Missing target commit")
        checkoutBranch(defaultBranch)

        modifyFile("Main.txt", "Missing target line\n")

        openGitChangesView()
        ui.createAndSelectTab("feature-recovery")
        waitForSelectedTab("feature-recovery")

        ui.setBranchAsRepoComparison("missing-target")
        waitUntil(15.seconds) { ui.selectedTabComparisonMap().contains("=missing-target") }

        deleteBranch("missing-target")

        waitUntil(20.seconds) {
            ui.selectedTabComparisonMap().contains("=HEAD") &&
                !ui.selectedTabComparisonMap().contains("missing-target") &&
                ui.selectedRenderedRowsSnapshot().contains("(vs HEAD)") &&
                ui.branchErrorNotificationsSnapshot().contains("Branch not found")
        }

        assertTrue(ui.selectedTabName() == "feature-recovery")
        assertTrue(ui.selectedChangesTreeSnapshot().contains("Main.txt"))

        val notifications = ui.branchErrorNotificationsSnapshot()
        assertTrue(notifications.contains("Branch not found"))
        assertTrue(notifications.contains("'missing-target'"))
        assertTrue(notifications.contains("feature-recovery"))
        assertTrue(notifications.contains("Change Comparison for '${project.path.fileName}'"))

        ui.triggerBranchErrorNotificationAction("Change Comparison for '${project.path.fileName}'")
        waitUntil {
            ui.visibleRepoComparisonDialogTitle() == "Select Branch for ${project.path.fileName}"
        }
        waitUntil {
            val branches = ui.visibleRepoComparisonDialogBranchesSnapshot()
            branches.contains("feature-recovery") && !branches.contains("missing-target")
        }

        ui.selectBranchInVisibleRepoComparisonDialog("feature-recovery")
        waitUntil(15.seconds) { ui.selectedTabComparisonMap().isBlank() }
    }

    @Test
    fun testMissingCommitComparisonTargetDoesNotRecoverToHeadOrWarn() = runStarterUiTest {
        prepareLstCrc()
        initializeGitRepository()

        createNewFile("Main.txt", "Base line\n")
        commitChanges("Initial commit")
        val defaultBranch = defaultBranchName()

        createBranch("feature-missing-commit")
        createNewFile("Feature.txt", "Feature branch file\n")
        commitChanges("Feature commit")
        checkoutBranch(defaultBranch)

        openGitChangesView()
        ui.createAndSelectTab("feature-missing-commit")
        waitForSelectedTab("feature-missing-commit")
        waitForTreeContains("Feature.txt")

        val missingCommitHash = "0123456789abcdef0123456789abcdef01234567"
        ui.setRevisionAsRepoComparison(missingCommitHash)

        waitUntil(15.seconds) {
            ui.selectedTabComparisonMap().contains(missingCommitHash) &&
                !ui.selectedChangesTreeSnapshot().contains("Feature.txt")
        }

        val comparisonMap = ui.selectedTabComparisonMap()
        assertTrue(comparisonMap.contains(missingCommitHash))
        assertFalse(comparisonMap.contains("=HEAD"))
        assertFalse(ui.selectedRenderedRowsSnapshot().contains("(vs HEAD)"))
        assertFalse(ui.branchErrorNotificationsSnapshot().contains("Branch not found"))
    }

    @Test
    fun testRepositoryComparisonToolbarDialogAllowsChangingComparison() = runStarterUiTest {
        prepareLstCrc()
        initializeGitRepository()

        createNewFile("Main.txt", "Base line\n")
        commitChanges("Initial commit")
        val defaultBranch = defaultBranchName()

        createBranch("feature-repo-dialog")
        modifyFile("Main.txt", "Feature line\n")
        commitChanges("Feature commit")
        checkoutBranch(defaultBranch)

        createBranch("override-target")
        createNewFile("Override.txt", "Override branch file\n")
        commitChanges("Override commit")
        checkoutBranch(defaultBranch)

        openGitChangesView()
        ui.createAndSelectTab("feature-repo-dialog")
        waitForSelectedTab("feature-repo-dialog")

        val dialogTitle = "Select Branch for ${project.path.fileName}"

        driver.ideFrame {
            actionButton { byAccessibleName("Comparison Context") }.click()
        }
        waitUntil {
            runCatching {
                var branches = emptyList<String>()
                driver.ideFrame {
                    dialog(title = dialogTitle) {
                        val branchesTree = tree()
                        branchesTree.expandAll()
                        branches = branchesTree.collectExpandedPathsAsStrings()
                    }
                }
                branches.any { it.contains("override-target") }
            }.getOrDefault(false)
        }

        driver.ideFrame {
            dialog(title = dialogTitle) {
                val branchesTree = tree()
                branchesTree.expandAll()
                branchesTree.clickPath("Local", "override-target")
            }
        }
        waitUntil(15.seconds) {
            ui.selectedTabComparisonMap().contains("=override-target") &&
                ui.selectedRenderedRowsSnapshot().contains("(vs override-target)")
        }

        driver.ideFrame {
            actionButton { byAccessibleName("Comparison Context") }.click()
        }
        waitUntil {
            runCatching {
                var dialogOpened = false
                driver.ideFrame {
                    dialog(title = dialogTitle) {
                        dialogOpened = true
                    }
                }
                dialogOpened
            }.getOrDefault(false)
        }
        driver.ideFrame {
            dialog(title = dialogTitle) {
                val branchesTree = tree()
                branchesTree.expandAll()
                branchesTree.clickPath("Local", "feature-repo-dialog")
            }
        }
        waitUntil(15.seconds) { ui.selectedTabComparisonMap().isBlank() }
    }
}
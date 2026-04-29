package com.github.uiopak.lstcrc.starter

import com.github.uiopak.lstcrc.starter.remote.LstCrcUiTestBridgeRemote
import com.intellij.driver.client.service
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.ide.starter.junit5.hyphenateWithClass
import com.intellij.ide.starter.runner.CurrentTestMethod
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Tag("starter")
class LstCrcMultiRootStarterUiTest : LstCrcStarterUiTestBase() {

    @Test
    fun testLinkedWorktreeBranchSwitchRefreshesActiveComparison() = runStarterUiTest {
        prepareLstCrc()
        initializeGitRepositoryAt(PRIMARY_WORKTREE_REPO)

        createNewFileInRepo(PRIMARY_WORKTREE_REPO, "Base.txt", "base\n")
        commitChangesInRepo(PRIMARY_WORKTREE_REPO, "Primary initial commit")
        val defaultBranch = defaultBranchNameInRepo(PRIMARY_WORKTREE_REPO)

        createBranchInRepo(PRIMARY_WORKTREE_REPO, "feature-worktree-target")
        createNewFileInRepo(PRIMARY_WORKTREE_REPO, "TargetOnly.txt", "target branch only\n")
        commitChangesInRepo(PRIMARY_WORKTREE_REPO, "Target branch commit")
        checkoutBranchInRepo(PRIMARY_WORKTREE_REPO, defaultBranch)

        createLinkedWorktree(PRIMARY_WORKTREE_REPO, LINKED_WORKTREE_REPO, "worktree-topic", "feature-worktree-target")
        createNewFileInRepo(LINKED_WORKTREE_REPO, "WorktreeOnly.txt", "linked worktree only\n")
        commitChangesInRepo(LINKED_WORKTREE_REPO, "Worktree branch commit")

        waitUntil(30.seconds) {
            val repositories = ui.knownGitRepositoriesSnapshot()
            repositories.contains(primaryWorktreeRepoPath()) && repositories.contains(linkedWorktreeRepoPath())
        }

        openGitChangesView()
        ui.createAndSelectTab("feature-worktree-target")
        waitForSelectedTab("feature-worktree-target")
        waitForTreeContains("TargetOnly.txt", "WorktreeOnly.txt")

        waitUntil(20.seconds) {
            ui.selectedChangesTreeSnapshot().contains("WorktreeOnly.txt")
        }

        checkoutBranchInRepo(LINKED_WORKTREE_REPO, "feature-worktree-target")

        waitUntil(30.seconds) {
            val tree = ui.selectedChangesTreeSnapshot()
            !tree.contains("WorktreeOnly.txt") && tree.contains("TargetOnly.txt")
        }
    }

    @Test
    fun testPrimaryWorktreeBranchSwitchPreservesLinkedWorktreeDiffContribution() = runStarterUiTest {
        prepareLstCrc()
        initializeGitRepositoryAt(PRIMARY_WORKTREE_REPO)

        createNewFileInRepo(PRIMARY_WORKTREE_REPO, "Base.txt", "base\n")
        commitChangesInRepo(PRIMARY_WORKTREE_REPO, "Primary initial commit")
        val defaultBranch = defaultBranchNameInRepo(PRIMARY_WORKTREE_REPO)

        createBranchInRepo(PRIMARY_WORKTREE_REPO, "feature-worktree-target")
        createNewFileInRepo(PRIMARY_WORKTREE_REPO, "TargetOnly.txt", "target branch only\n")
        commitChangesInRepo(PRIMARY_WORKTREE_REPO, "Target branch commit")
        checkoutBranchInRepo(PRIMARY_WORKTREE_REPO, defaultBranch)

        createLinkedWorktree(PRIMARY_WORKTREE_REPO, LINKED_WORKTREE_REPO, "worktree-topic", defaultBranch)
        createNewFileInRepo(LINKED_WORKTREE_REPO, "WorktreeOnly.txt", "linked worktree only\n")
        commitChangesInRepo(LINKED_WORKTREE_REPO, "Worktree branch commit")

        waitUntil(30.seconds) {
            val repositories = ui.knownGitRepositoriesSnapshot()
            repositories.contains(primaryWorktreeRepoPath()) && repositories.contains(linkedWorktreeRepoPath())
        }

        openGitChangesView()
        ui.createAndSelectTab("feature-worktree-target")
        waitForSelectedTab("feature-worktree-target")
        waitForTreeContains("TargetOnly.txt", "WorktreeOnly.txt")

        waitUntil(20.seconds) {
            val tree = ui.selectedChangesTreeSnapshot()
            tree.contains("TargetOnly.txt") && tree.contains("WorktreeOnly.txt")
        }

        checkoutBranchInRepo(PRIMARY_WORKTREE_REPO, "feature-worktree-target")

        waitUntil(30.seconds) {
            val tree = ui.selectedChangesTreeSnapshot()
            !tree.contains("TargetOnly.txt") && tree.contains("WorktreeOnly.txt")
        }
    }

    @Test
    fun testBranchSelectionUsesPrimaryRepositoryBranchesInMultiRootProject() = runStarterUiTest {
        prepareLstCrc()
        initializeGitRepository()

        createNewFile("Primary.txt", "primary\n")
        commitChanges("Primary initial commit")
        val primaryDefaultBranch = defaultBranchName()

        createBranch("primary-only")
        checkoutBranch(primaryDefaultBranch)

        initializeGitRepositoryAt(SECONDARY_REPO)

        createNewFileInRepo(SECONDARY_REPO, "Secondary.txt", "secondary\n")
        commitChangesInRepo(SECONDARY_REPO, "Secondary initial commit")
        val secondaryDefaultBranch = defaultBranchNameInRepo(SECONDARY_REPO)

        createBranchInRepo(SECONDARY_REPO, "secondary-only")
        checkoutBranchInRepo(SECONDARY_REPO, secondaryDefaultBranch)

        openGitChangesView()
        openBranchSelectionTab()

        waitUntil(20.seconds) {
            val branches = ui.branchSelectionTabBranchesSnapshot()
            branches.contains("primary-only") && !branches.contains("secondary-only")
        }
    }

    @Test
    fun testMultiRootComparisonOverrideAppliesOnlyToSelectedRepository() = runStarterUiTest {
        prepareLstCrc()
        initializeGitRepository()

        createNewFile("Primary.txt", "base\n")
        commitChanges("Primary initial commit")
        val primaryDefaultBranch = defaultBranchName()

        createBranch("feature-multi-root")
        modifyFile("Primary.txt", "primary feature\n")
        commitChanges("Primary feature commit")
        checkoutBranch(primaryDefaultBranch)

        initializeGitRepositoryAt(SECONDARY_REPO)

        createNewFileInRepo(SECONDARY_REPO, "Secondary.txt", "base\n")
        commitChangesInRepo(SECONDARY_REPO, "Secondary initial commit")
        val secondaryDefaultBranch = defaultBranchNameInRepo(SECONDARY_REPO)

        createBranchInRepo(SECONDARY_REPO, "feature-multi-root")
        modifyFileInRepo(SECONDARY_REPO, "Secondary.txt", "secondary feature\n")
        commitChangesInRepo(SECONDARY_REPO, "Secondary feature commit")
        checkoutBranchInRepo(SECONDARY_REPO, secondaryDefaultBranch)

        createBranchInRepo(SECONDARY_REPO, "secondary-override")
        modifyFileInRepo(SECONDARY_REPO, "Secondary.txt", "secondary override\n")
        commitChangesInRepo(SECONDARY_REPO, "Secondary override commit")
        checkoutBranchInRepo(SECONDARY_REPO, secondaryDefaultBranch)

        openGitChangesView()
        ui.createAndSelectTab("feature-multi-root")
        waitForSelectedTab("feature-multi-root")
        waitForTreeContains("Primary.txt", "Secondary.txt")

        setRepoComparisonForRoot(SECONDARY_REPO, "secondary-override")

        waitUntil(20.seconds) {
            ui.selectedTabComparisonMap().contains("${secondaryRepoPath()}=secondary-override")
        }
        waitUntil(20.seconds) {
            ui.selectedRenderedRowsSnapshot().contains("(vs secondary-override)")
        }

        assertTrue(ui.isMultiRepoTreeContextEnabled())
        ui.setMultiRepoTreeContextSetting(false)
        waitUntil(20.seconds) {
            !ui.selectedRenderedRowsSnapshot().contains("(vs secondary-override)")
        }

        ui.setMultiRepoTreeContextSetting(true)
        waitUntil(20.seconds) {
            ui.selectedRenderedRowsSnapshot().contains("(vs secondary-override)")
        }

        assertTrue(ui.selectedChangesTreeSnapshot().contains("Primary.txt"))
        assertTrue(ui.selectedChangesTreeSnapshot().contains("Secondary.txt"))
    }

    @Test
    fun testMissingBranchNotificationRepairReconfiguresOnlyBrokenRepository() = runStarterUiTest {
        prepareLstCrc()
        initializeGitRepository()

        createNewFile("Primary.txt", "base\n")
        commitChanges("Primary initial commit")
        val primaryDefaultBranch = defaultBranchName()

        createBranch("feature-repair")
        modifyFile("Primary.txt", "primary repair\n")
        commitChanges("Primary repair commit")
        checkoutBranch(primaryDefaultBranch)

        initializeGitRepositoryAt(SECONDARY_REPO)

        createNewFileInRepo(SECONDARY_REPO, "Secondary.txt", "base\n")
        commitChangesInRepo(SECONDARY_REPO, "Secondary initial commit")
        val secondaryDefaultBranch = defaultBranchNameInRepo(SECONDARY_REPO)

        createBranchInRepo(SECONDARY_REPO, "repair-target")
        modifyFileInRepo(SECONDARY_REPO, "Secondary.txt", "secondary repair\n")
        commitChangesInRepo(SECONDARY_REPO, "Secondary repair target commit")
        checkoutBranchInRepo(SECONDARY_REPO, secondaryDefaultBranch)

        openGitChangesView()
        ui.createAndSelectTab("feature-repair")
        waitForSelectedTab("feature-repair")

        waitUntil(30.seconds) {
            ui.selectedTabComparisonMap().contains("${secondaryRepoPath()}=HEAD")
        }
        waitUntil(20.seconds) {
            ui.branchErrorNotificationsSnapshot().contains("Branch not found")
        }
        assertTrue(ui.branchErrorNotificationsSnapshot().contains("nested-repo"))

        ui.triggerBranchErrorNotificationAction("Change Comparison for 'nested-repo'")
        waitUntil(20.seconds) {
            ui.visibleRepoComparisonDialogTitle() == "Select Branch for nested-repo"
        }
        waitUntil(20.seconds) {
            ui.visibleRepoComparisonDialogBranchesSnapshot().contains("repair-target")
        }

        ui.selectBranchInVisibleRepoComparisonDialog("repair-target")

        waitUntil(20.seconds) {
            ui.selectedTabComparisonMap().contains("${secondaryRepoPath()}=repair-target")
        }
        waitUntil(20.seconds) {
            ui.selectedRenderedRowsSnapshot().contains("(vs repair-target)")
        }
    }

    @Test
    fun testTabsAliasesAndRepoOverridesRestoreAfterRestart() {
        val testName = CurrentTestMethod.hyphenateWithClass()
        val project = LstCrcStarterProject.create(testName)

        runStarterSession(project, "$testName-seed") {
            prepareLstCrc()
            initializeGitRepository()

            createNewFile("Primary.txt", "base\n")
            commitChanges("Primary initial commit")
            val primaryDefaultBranch = defaultBranchName()

            createBranch("feature-persist")
            modifyFile("Primary.txt", "primary persisted\n")
            commitChanges("Primary persisted commit")
            checkoutBranch(primaryDefaultBranch)

            initializeGitRepositoryAt(SECONDARY_REPO)

            createNewFileInRepo(SECONDARY_REPO, "Secondary.txt", "base\n")
            commitChangesInRepo(SECONDARY_REPO, "Secondary initial commit")
            val secondaryDefaultBranch = defaultBranchNameInRepo(SECONDARY_REPO)

            createBranchInRepo(SECONDARY_REPO, "feature-persist")
            modifyFileInRepo(SECONDARY_REPO, "Secondary.txt", "secondary persisted\n")
            commitChangesInRepo(SECONDARY_REPO, "Secondary persisted commit")
            checkoutBranchInRepo(SECONDARY_REPO, secondaryDefaultBranch)

            createBranchInRepo(SECONDARY_REPO, "secondary-persist-override")
            modifyFileInRepo(SECONDARY_REPO, "Secondary.txt", "secondary override\n")
            commitChangesInRepo(SECONDARY_REPO, "Secondary override commit")
            checkoutBranchInRepo(SECONDARY_REPO, secondaryDefaultBranch)

            openGitChangesView()
            ui.createAndSelectTab("feature-persist")
            waitForSelectedTab("feature-persist")

            ui.updateTabAlias("feature-persist", "persisted-feature")
            waitUntil(20.seconds) {
                ui.hasTab("persisted-feature") && ui.selectedTabName() == "persisted-feature"
            }

            setRepoComparisonForRoot(SECONDARY_REPO, "secondary-persist-override")
            waitUntil(20.seconds) {
                ui.selectedTabComparisonMap().contains("${secondaryRepoPath(project)}=secondary-persist-override")
            }
        }

        runStarterSession(project, "$testName-verify") {
            waitUntil(30.seconds) { ui.isGitVcsActive() }
            openGitChangesView()

            waitUntil(30.seconds) {
                ui.hasTab("persisted-feature") && ui.selectedTabName() == "persisted-feature"
            }
            waitUntil(30.seconds) {
                ui.selectedTabComparisonMap().contains("${secondaryRepoPath(project)}=secondary-persist-override")
            }

            assertTrue(ui.hasTab("HEAD"))
        }
    }

    private fun runStarterSession(
        project: LstCrcStarterProject,
        testName: String,
        block: LstCrcStarterContext.() -> Unit
    ) {
        val context = createTestContext(project, testName)
        context.runLstCrcIdeWithDriver().useDriverAndCloseIde {
            waitForIndicators(5.minutes)
            val bridge = service<LstCrcUiTestBridgeRemote>()
            val starterContext = LstCrcStarterContext(project, bridge, this)
            starterContext.waitForSmartMode()
            block(starterContext)
        }
    }

    private fun LstCrcStarterContext.primaryWorktreeRepoPath(project: LstCrcStarterProject = this.project): String =
        project.path.resolve(PRIMARY_WORKTREE_REPO).toString().replace('\\', '/')

    private fun LstCrcStarterContext.linkedWorktreeRepoPath(project: LstCrcStarterProject = this.project): String =
        project.path.resolve(LINKED_WORKTREE_REPO).toString().replace('\\', '/')

    private fun LstCrcStarterContext.secondaryRepoPath(project: LstCrcStarterProject = this.project): String =
        project.path.resolve(SECONDARY_REPO).toString().replace('\\', '/')

    private companion object {
        const val PRIMARY_WORKTREE_REPO = "primary-repo"
        const val LINKED_WORKTREE_REPO = "linked-worktree"
        const val SECONDARY_REPO = "nested-repo"
    }
}

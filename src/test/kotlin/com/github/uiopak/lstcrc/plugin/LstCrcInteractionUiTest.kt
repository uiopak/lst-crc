package com.github.uiopak.lstcrc.plugin

import com.automation.remarks.junit5.Video
import com.github.uiopak.lstcrc.plugin.pages.actionMenuItem
import com.github.uiopak.lstcrc.plugin.pages.branchSelection
import com.github.uiopak.lstcrc.plugin.pages.gitChangesView
import com.github.uiopak.lstcrc.plugin.pages.idea
import com.github.uiopak.lstcrc.plugin.steps.PluginUiTestSteps
import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.keyboard
import com.intellij.remoterobot.utils.waitFor
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

@LstCrcUiTest
class LstCrcInteractionUiTest : LstCrcUiTestSupport() {

    @Test
    @Video
    fun testToolWindowClickActions(remoteRobot: RemoteRobot) = with(remoteRobot) {
        val uiSteps = PluginUiTestSteps(remoteRobot)

        prepareFreshProject()

        idea {
            step("Wait for smart mode") {
                dumbAware(Duration.ofMinutes(5)) {}
            }

            uiSteps.initializeGitRepository()
            resetGitChangesViewState()

            uiSteps.createNewFile("Main.txt", "Base line\n")
            uiSteps.commitChanges("Initial commit")
            val defaultBranch = uiSteps.defaultBranchName()

            uiSteps.createBranch("feature-clicks")
            uiSteps.modifyFile("Main.txt", "Feature line\n")
            uiSteps.createNewFile("Feature.txt", "Feature file\n")
            uiSteps.commitChanges("Feature commit")
            uiSteps.checkoutBranch(defaultBranch)

            openGitChangesView()
            gitChangesView {
                addTab()
            }
            branchSelection {
                searchAndSelect("feature-clicks")
            }

            configureLstCrcClickActions(
                singleClickAction = "OPEN_SOURCE",
                doubleClickAction = "NONE",
                rightClickAction = "OPEN_SOURCE",
                showContextMenu = false
            )

            gitChangesView {
                selectTab("feature-clicks")
                clickChange("Feature.txt")
            }

            waitFor(Duration.ofSeconds(15)) {
                selectedEditorDescriptor().contains("Feature.txt")
            }

            gitChangesView {
                rightClickChange("Main.txt")
            }

            waitFor(Duration.ofSeconds(15)) {
                selectedEditorDescriptor().contains("Main.txt")
            }

            configureLstCrcClickActions(
                singleClickAction = "NONE",
                doubleClickAction = "OPEN_DIFF"
            )

            gitChangesView {
                doubleClickChange("Main.txt")
            }

            waitFor(Duration.ofSeconds(15)) {
                hasDiffEditorOpen() ||
                    findAll<ComponentFixture>(
                        byXpath("//div[@class='DiffFilePathLabel' and (@text='Main.txt' or @accessiblename='Main.txt')]")
                    ).isNotEmpty() ||
                    findAll<ComponentFixture>(
                        byXpath("//div[@visible_text='1 difference' or @text='1 difference']")
                    ).isNotEmpty()
            }
        }
    }

    @Test
    @Video
    fun testContextMenuActionsWhenEnabled(remoteRobot: RemoteRobot) = with(remoteRobot) {
        val uiSteps = PluginUiTestSteps(remoteRobot)

        prepareFreshProject()

        idea {
            step("Wait for smart mode") {
                dumbAware(Duration.ofMinutes(5)) {}
            }

            uiSteps.initializeGitRepository()
            resetGitChangesViewState()

            uiSteps.createNewFile("Main.txt", "Base line\n")
            uiSteps.commitChanges("Initial commit")
            val defaultBranch = uiSteps.defaultBranchName()

            uiSteps.createBranch("feature-context")
            uiSteps.modifyFile("Main.txt", "Feature context line\n")
            uiSteps.commitChanges("Feature context commit")
            uiSteps.checkoutBranch(defaultBranch)

            openGitChangesView()
            gitChangesView {
                addTab()
            }
            branchSelection {
                searchAndSelect("feature-context")
            }

            configureLstCrcClickActions(showContextMenu = true)

            gitChangesView {
                selectTab("feature-context")
                rightClickChange("Main.txt")
            }

            waitFor(Duration.ofSeconds(10)) {
                actionMenuItem("Show Diff").isShowing &&
                    actionMenuItem("Open Source").isShowing &&
                    actionMenuItem("Show in Project Tree").isShowing
            }
        }
    }

    @Test
    @Video
    fun testStatusWidgetAndRevisionActions(remoteRobot: RemoteRobot) = with(remoteRobot) {
        val uiSteps = PluginUiTestSteps(remoteRobot)

        prepareFreshProject()

        idea {
            step("Wait for smart mode") {
                dumbAware(Duration.ofMinutes(5)) {}
            }

            uiSteps.initializeGitRepository()
            resetGitChangesViewState()

            uiSteps.createNewFile("Main.txt", "Base line\n")
            uiSteps.commitChanges("Initial commit")
            val defaultBranch = uiSteps.defaultBranchName()

            uiSteps.createBranch("feature-widget")
            uiSteps.modifyFile("Main.txt", "Feature widget line\n")
            uiSteps.commitChanges("Feature widget commit")
            val featureRevision = uiSteps.gitRevision("HEAD")

            uiSteps.checkoutBranch(defaultBranch)
            uiSteps.createBranch("feature-widget-2")
            uiSteps.createNewFile("Second.txt", "Second branch\n")
            uiSteps.commitChanges("Second feature commit")
            uiSteps.checkoutBranch(defaultBranch)

            openGitChangesView()
            gitChangesView {
                addTab()
            }
            branchSelection {
                searchAndSelect("feature-widget")
            }

            waitFor(Duration.ofSeconds(10)) {
                statusWidgetText().contains("feature-widget")
            }

            clickStatusWidget()
            waitFor(Duration.ofSeconds(10)) {
                findAll<ComponentFixture>(
                    byXpath("//div[@class='MyList' and contains(@accessiblename, 'LST-CRC Actions')]")
                ).isNotEmpty()
            }
            keyboard {
                enterText("HEAD")
                enter()
            }
            waitFor(Duration.ofSeconds(10)) {
                selectedLstCrcTabName() == "HEAD"
            }

            clickStatusWidget()
            waitFor(Duration.ofSeconds(10)) {
                findAll<ComponentFixture>(
                    byXpath("//div[@class='MyList' and contains(@accessiblename, 'LST-CRC Actions')]")
                ).isNotEmpty()
            }
            keyboard {
                enterText("feature-widget")
                enter()
            }
            waitFor(Duration.ofSeconds(10)) {
                selectedLstCrcTabName() == "feature-widget" && statusWidgetText().contains("feature-widget")
            }

            clickStatusWidget()
            waitFor(Duration.ofSeconds(10)) {
                findAll<ComponentFixture>(
                    byXpath("//div[@class='MyList' and contains(@accessiblename, 'LST-CRC Actions')]")
                ).isNotEmpty()
            }
            keyboard {
                enterText("Add Tab")
                enter()
            }
            branchSelection {
                searchAndSelect("feature-widget-2")
            }

            gitChangesView {
                waitFor(Duration.ofSeconds(10)) {
                    hasTab("feature-widget-2")
                }
            }

            invokeCreateTabFromRevisionAction(featureRevision, "feature-revision")

            waitFor(Duration.ofSeconds(10)) {
                val selectedTabName = selectedLstCrcTabName()
                selectedTabName == "feature-revision" || selectedTabName == featureRevision
            }

            clickStatusWidget()
            waitFor(Duration.ofSeconds(10)) {
                findAll<ComponentFixture>(
                    byXpath("//div[@class='MyList' and contains(@accessiblename, 'LST-CRC Actions')]")
                ).isNotEmpty()
            }
            keyboard {
                enterText("feature-widget")
                enter()
            }
            waitFor(Duration.ofSeconds(10)) {
                selectedLstCrcTabName() == "feature-widget"
            }

            invokeSetRevisionAsRepoComparisonAction(featureRevision)
            waitFor(Duration.ofSeconds(15)) {
                selectedTabComparisonMap().contains(featureRevision)
            }
        }
    }

    @Test
    @Video
    fun testTabRenameUpdatesWidgetContext(remoteRobot: RemoteRobot) = with(remoteRobot) {
        val uiSteps = PluginUiTestSteps(remoteRobot)

        prepareFreshProject()

        idea {
            step("Wait for smart mode") {
                dumbAware(Duration.ofMinutes(5)) {}
            }

            uiSteps.initializeGitRepository()
            resetGitChangesViewState()

            uiSteps.createNewFile("Main.txt", "Base line\n")
            uiSteps.commitChanges("Initial commit")
            val defaultBranch = uiSteps.defaultBranchName()

            uiSteps.createBranch("feature-rename")
            uiSteps.modifyFile("Main.txt", "Renamed alias line\n")
            uiSteps.commitChanges("Rename alias commit")
            uiSteps.checkoutBranch(defaultBranch)

            openGitChangesView()
            gitChangesView {
                addTab()
            }
            branchSelection {
                searchAndSelect("feature-rename")
            }

            waitFor(Duration.ofSeconds(10)) {
                statusWidgetText().contains("feature-rename")
            }

            updateTabAlias("feature-rename", "renamed-feature")

            waitFor(Duration.ofSeconds(10)) {
                hasLstCrcTab("renamed-feature") &&
                    selectedLstCrcTabName() == "renamed-feature" &&
                    statusWidgetText().contains("renamed-feature")
            }

            setShowWidgetContext(true)
            waitFor(Duration.ofSeconds(10)) {
                statusWidgetText().startsWith("Context:") && statusWidgetText().contains("renamed-feature")
            }

            setShowWidgetContext(false)
            waitFor(Duration.ofSeconds(10)) {
                !statusWidgetText().startsWith("Context:")
            }

            updateTabAlias("feature-rename", null)

            waitFor(Duration.ofSeconds(10)) {
                hasLstCrcTab("feature-rename") &&
                    selectedLstCrcTabName() == "feature-rename" &&
                    statusWidgetText().contains("feature-rename")
            }
        }
    }

    @Test
    @Video
    fun testRenameTabPopupRenamesSelectedTab(remoteRobot: RemoteRobot) = with(remoteRobot) {
        val uiSteps = PluginUiTestSteps(remoteRobot)

        prepareFreshProject()

        idea {
            step("Wait for smart mode") {
                dumbAware(Duration.ofMinutes(5)) {}
            }

            uiSteps.initializeGitRepository()
            resetGitChangesViewState()

            uiSteps.createNewFile("Main.txt", "Base line\n")
            uiSteps.commitChanges("Initial commit")
            val defaultBranch = uiSteps.defaultBranchName()

            uiSteps.createBranch("feature-rename-ui")
            uiSteps.modifyFile("Main.txt", "Renamed alias line\n")
            uiSteps.commitChanges("Rename alias commit")
            uiSteps.checkoutBranch(defaultBranch)

            openGitChangesView()
            gitChangesView {
                addTab()
            }
            branchSelection {
                searchAndSelect("feature-rename-ui")
            }

            waitFor(Duration.ofSeconds(10)) {
                statusWidgetText().contains("feature-rename-ui")
            }

            gitChangesView {
                invokeRenameTabAction("feature-rename-ui")
            }
            waitForFocusedTextInput()
            keyboard {
                enterText("renamed-feature-ui")
                enter()
            }

            waitFor(Duration.ofSeconds(10)) {
                hasLstCrcTab("renamed-feature-ui") &&
                    selectedLstCrcTabName() == "renamed-feature-ui" &&
                    statusWidgetText().contains("renamed-feature-ui")
            }
        }
    }

    @Test
    @Video
    fun testRenameTabContextMenuRenamesSelectedTab(remoteRobot: RemoteRobot) = with(remoteRobot) {
        val uiSteps = PluginUiTestSteps(remoteRobot)

        prepareFreshProject()

        idea {
            step("Wait for smart mode") {
                dumbAware(Duration.ofMinutes(5)) {}
            }

            uiSteps.initializeGitRepository()
            resetGitChangesViewState()

            uiSteps.createNewFile("Main.txt", "Base line\n")
            uiSteps.commitChanges("Initial commit")
            val defaultBranch = uiSteps.defaultBranchName()

            uiSteps.createBranch("feature-rename-menu")
            uiSteps.modifyFile("Main.txt", "Renamed from menu\n")
            uiSteps.commitChanges("Rename alias menu commit")
            uiSteps.checkoutBranch(defaultBranch)

            openGitChangesView()
            gitChangesView {
                addTab()
            }
            branchSelection {
                searchAndSelect("feature-rename-menu")
            }

            waitFor(Duration.ofSeconds(10)) {
                statusWidgetText().contains("feature-rename-menu")
            }

            gitChangesView {
                rightClickTab("feature-rename-menu")
            }

            waitFor(Duration.ofSeconds(10)) {
                actionMenuItem("Rename Tab...").isShowing
            }
            actionMenuItem("Rename Tab...").click()

            waitForFocusedTextInput()
            keyboard {
                enterText("renamed-feature-menu")
                enter()
            }

            waitFor(Duration.ofSeconds(10)) {
                hasLstCrcTab("renamed-feature-menu") &&
                    selectedLstCrcTabName() == "renamed-feature-menu" &&
                    statusWidgetText().contains("renamed-feature-menu")
            }
        }
    }
}
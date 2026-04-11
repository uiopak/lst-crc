package com.github.uiopak.lstcrc.plugin

import com.automation.remarks.junit5.Video
import com.github.uiopak.lstcrc.plugin.pages.branchSelection
import com.github.uiopak.lstcrc.plugin.pages.gitChangesView
import com.github.uiopak.lstcrc.plugin.pages.idea
import com.github.uiopak.lstcrc.plugin.steps.PluginUiTestSteps
import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.waitFor
import org.assertj.swing.core.MouseButton
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

@LstCrcUiTest
class LstCrcSettingsUiTest : LstCrcUiTestSupport() {

    @Test
    @Video
    fun testTreePresentationAndTitleSettings(remoteRobot: RemoteRobot) = with(remoteRobot) {
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

            uiSteps.createBranch("feature-tree")
            uiSteps.modifyFile("Main.txt", "Feature tree line\n")
            uiSteps.commitChanges("Feature tree commit")
            val featureRevision = uiSteps.gitRevision("HEAD")
            uiSteps.checkoutBranch(defaultBranch)

            openGitChangesView()
            gitChangesView {
                addTab()
            }
            branchSelection {
                searchAndSelect("feature-tree")
            }

            assertTrue(treeContextSettingsSnapshot().startsWith("true|false"), "Unexpected initial tree context settings")

            setTreeContextSettings(showSingleRepo = false)
            waitFor(Duration.ofSeconds(10)) {
                treeContextSettingsSnapshot() == "false|false"
            }

            setTreeContextSettings(showSingleRepo = true)
            waitFor(Duration.ofSeconds(10)) {
                treeContextSettingsSnapshot() == "true|false"
            }

            assertFalse(isToolWindowTitleVisible(), "Tool window title should be hidden by default")
            setShowToolWindowTitle(true)
            waitFor(Duration.ofSeconds(10)) {
                isToolWindowTitleVisible()
            }
            setShowToolWindowTitle(false)
            waitFor(Duration.ofSeconds(10)) {
                !isToolWindowTitleVisible()
            }

            invokeCreateTabFromRevisionAction(featureRevision, "feature-tree-revision")
            waitFor(Duration.ofSeconds(10)) {
                selectedLstCrcTabName() == "feature-tree-revision"
            }

            setTreeContextSettings(showCommits = false)
            waitFor(Duration.ofSeconds(10)) {
                treeContextSettingsSnapshot() == "true|false"
            }

            setTreeContextSettings(showCommits = true)
            waitFor(Duration.ofSeconds(10)) {
                treeContextSettingsSnapshot() == "true|true"
            }
        }
    }

    @Test
    @Video
    fun testGutterSettingsAndIncludeHead(remoteRobot: RemoteRobot) = with(remoteRobot) {
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
            uiSteps.createNewFile("LocalNew.txt", "Local new file\n")

            openGitChangesView()
            gitChangesView {
                selectTab("HEAD")
            }

            setGutterSettings(enableMarkers = true, enableForNewFiles = true)
            setIncludeHeadInScopes(true)
            waitFor(Duration.ofSeconds(10)) {
                gutterSettingsSnapshot() == "true|true|true"
            }

            setIncludeHeadInScopes(false)
            waitFor(Duration.ofSeconds(10)) {
                gutterSettingsSnapshot() == "true|true|false"
            }

            setIncludeHeadInScopes(true)
            waitFor(Duration.ofSeconds(10)) {
                gutterSettingsSnapshot() == "true|true|true"
            }

            setGutterSettings(enableForNewFiles = false)
            waitFor(Duration.ofSeconds(10)) {
                gutterSettingsSnapshot() == "true|false|true"
            }

            setGutterSettings(enableMarkers = false)
            waitFor(Duration.ofSeconds(10)) {
                gutterSettingsSnapshot() == "false|false|true"
            }
        }
    }

    @Test
    @Video
    fun testAdditionalClickSettings(remoteRobot: RemoteRobot) = with(remoteRobot) {
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

            uiSteps.createBranch("feature-settings-clicks")
            uiSteps.modifyFile("Main.txt", "Feature line\n")
            uiSteps.createNewFile("Feature.txt", "Feature file\n")
            uiSteps.commitChanges("Feature click commit")
            uiSteps.checkoutBranch(defaultBranch)

            openGitChangesView()
            gitChangesView {
                addTab()
            }
            branchSelection {
                searchAndSelect("feature-settings-clicks")
            }

            configureLstCrcClickActions(
                middleClickAction = "OPEN_SOURCE",
                doubleMiddleClickAction = "OPEN_DIFF",
                rightClickAction = "SHOW_IN_PROJECT_TREE",
                doubleRightClickAction = "OPEN_SOURCE",
                showContextMenu = false
            )
            setDoubleClickDelayMs(500)

            val clickSettings = clickSettingsSnapshot()
            assertTrue(clickSettings.contains("OPEN_SOURCE|OPEN_DIFF|SHOW_IN_PROJECT_TREE|OPEN_SOURCE|false|500"), "Unexpected click settings snapshot: $clickSettings")

            closeAllEditors()
            gitChangesView {
                selectTab("feature-settings-clicks")
                clickChange("Feature.txt", MouseButton.MIDDLE_BUTTON)
            }
            waitFor(Duration.ofSeconds(15)) {
                selectedEditorDescriptor().contains("Feature.txt")
            }

            configureLstCrcClickActions(
                singleClickAction = "OPEN_SOURCE",
                doubleClickAction = "OPEN_DIFF"
            )
            closeAllEditors()

            gitChangesView {
                selectTab("feature-settings-clicks")
                clickChange("Main.txt")
            }

            Thread.sleep(150)
            assertFalse(selectedEditorDescriptor().contains("Main.txt"), "Single-click action fired before configured delay elapsed")

            waitFor(Duration.ofSeconds(5)) {
                selectedEditorDescriptor().contains("Main.txt")
            }
        }
    }
}

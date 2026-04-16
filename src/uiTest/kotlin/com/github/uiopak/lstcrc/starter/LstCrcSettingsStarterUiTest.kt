package com.github.uiopak.lstcrc.starter

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

@Tag("starter")
class LstCrcSettingsStarterUiTest : LstCrcStarterUiTestBase() {

    @Test
    fun testTreePresentationAndTitleSettings() = runStarterUiTest {
        prepareLstCrc()
        initializeGitRepository()

        createNewFile("Main.txt", "Base line\n")
        commitChanges("Initial commit")
        val defaultBranch = defaultBranchName()

        createBranch("feature-tree")
        modifyFile("Main.txt", "Feature tree line\n")
        commitChanges("Feature tree commit")
        val featureRevision = gitRevision("HEAD")
        checkoutBranch(defaultBranch)

        openGitChangesView()
        ui.createAndSelectTab("feature-tree")
        waitForSelectedTab("feature-tree")

        assertTrue(ui.treeContextSettingsSnapshot().startsWith("true|false"))

        ui.setTreeContextSettings(showSingleRepo = false, showCommits = null)
        waitUntil { ui.treeContextSettingsSnapshot() == "false|false" }

        ui.setTreeContextSettings(showSingleRepo = true, showCommits = null)
        waitUntil { ui.treeContextSettingsSnapshot() == "true|false" }

        assertFalse(ui.isToolWindowTitleVisible())
        ui.setShowToolWindowTitle(true)
        waitUntil { ui.isToolWindowTitleVisible() }
        ui.setShowToolWindowTitle(false)
        waitUntil { !ui.isToolWindowTitleVisible() }

        ui.createRevisionTab(featureRevision, "feature-tree-revision")
        waitUntil { ui.selectedTabName() == "feature-tree-revision" }

        ui.setTreeContextSettings(showSingleRepo = null, showCommits = false)
        waitUntil { ui.treeContextSettingsSnapshot() == "true|false" }

        ui.setTreeContextSettings(showSingleRepo = null, showCommits = true)
        waitUntil { ui.treeContextSettingsSnapshot() == "true|true" }
    }

    @Test
    fun testGutterSettingsAndIncludeHead() = runStarterUiTest {
        prepareLstCrc()
        initializeGitRepository()

        createNewFile("Main.txt", "Base line\n")
        commitChanges("Initial commit")
        createNewFile("LocalNew.txt", "Local new file\n")

        openGitChangesView()
        ui.selectTab("HEAD")
        waitForSelectedTab("HEAD")

        ui.setGutterSettings(enableMarkers = true, enableForNewFiles = true)
        ui.setIncludeHeadInScopes(true)
        waitUntil { ui.gutterSettingsSnapshot() == "true|true|true" }

        ui.setIncludeHeadInScopes(false)
        waitUntil { ui.gutterSettingsSnapshot() == "true|true|false" }

        ui.setIncludeHeadInScopes(true)
        waitUntil { ui.gutterSettingsSnapshot() == "true|true|true" }

        ui.setGutterSettings(enableMarkers = null, enableForNewFiles = false)
        waitUntil { ui.gutterSettingsSnapshot() == "true|false|true" }

        ui.setGutterSettings(enableMarkers = false, enableForNewFiles = null)
        waitUntil { ui.gutterSettingsSnapshot() == "false|false|true" }
    }

    @Test
    fun testAdditionalClickSettings() = runStarterUiTest {
        prepareLstCrc()
        initializeGitRepository()

        createNewFile("Main.txt", "Base line\n")
        commitChanges("Initial commit")
        val defaultBranch = defaultBranchName()

        createBranch("feature-settings-clicks")
        modifyFile("Main.txt", "Feature line\n")
        createNewFile("Feature.txt", "Feature file\n")
        commitChanges("Feature click commit")
        checkoutBranch(defaultBranch)

        openGitChangesView()
        ui.createAndSelectTab("feature-settings-clicks")
        waitForSelectedTab("feature-settings-clicks")

        ui.configureClickActions(
            singleClickAction = null,
            doubleClickAction = null,
            middleClickAction = "OPEN_SOURCE",
            doubleMiddleClickAction = "OPEN_DIFF",
            rightClickAction = "SHOW_IN_PROJECT_TREE",
            doubleRightClickAction = "OPEN_SOURCE",
            showContextMenu = false
        )
        ui.setDoubleClickDelayMs(500)

        val clickSettings = ui.clickSettingsSnapshot()
        assertTrue(clickSettings.contains("OPEN_SOURCE|OPEN_DIFF|SHOW_IN_PROJECT_TREE|OPEN_SOURCE|false|500"))

        ui.closeAllEditors()
        ui.triggerConfiguredChangeInteraction("Feature.txt", "MIDDLE", 1)
        waitUntil(15.seconds) { ui.selectedEditorDescriptor().contains("Feature.txt") }

        ui.configureClickActions(
            singleClickAction = "OPEN_SOURCE",
            doubleClickAction = "OPEN_DIFF",
            middleClickAction = null,
            doubleMiddleClickAction = null,
            rightClickAction = null,
            doubleRightClickAction = null,
            showContextMenu = null
        )
        val singleClickDelayMs = 500
        ui.setDoubleClickDelayMs(singleClickDelayMs)
        ui.closeAllEditors()
        ui.triggerConfiguredChangeInteraction("Main.txt", "LEFT", 1)

        Thread.sleep(150)
        assertFalse(ui.selectedEditorDescriptor().contains("Main.txt"))
        waitUntil(5.seconds) { ui.selectedEditorDescriptor().contains("Main.txt") }
    }
}
package com.github.uiopak.lstcrc.plugin

import com.automation.remarks.junit5.Video
import com.github.uiopak.lstcrc.plugin.pages.idea
import com.github.uiopak.lstcrc.plugin.utils.RemoteRobotExtension
import com.github.uiopak.lstcrc.plugin.utils.StepsLogger
import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.utils.component
import java.awt.Point
import com.intellij.remoterobot.steps.CommonSteps
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.ContainerFixture
import com.intellij.remoterobot.search.locators.byXpath
import java.time.Duration
import com.github.uiopak.lstcrc.plugin.pages.actionMenu
import com.github.uiopak.lstcrc.plugin.pages.actionMenuItem
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.keyboard
import com.intellij.remoterobot.utils.waitFor
import com.intellij.remoterobot.utils.waitForIgnoringError
import org.assertj.swing.core.MouseButton
import com.github.uiopak.lstcrc.plugin.pages.*
import com.github.uiopak.lstcrc.plugin.steps.KotlinExampleSteps
import com.github.uiopak.lstcrc.plugin.steps.PluginUiTestSteps
//import com.github.uiopak.lstcrc.plugin.steps.JavaExampleSteps
import com.intellij.remoterobot.fixtures.HeavyWeightWindowFixture
import com.intellij.remoterobot.stepsProcessing.step
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
//import org.junit.jupiter.api.Disabled
import java.awt.event.KeyEvent.*
import java.time.Duration.ofMinutes
import java.time.Duration.ofSeconds

//======================
@ExtendWith(RemoteRobotExtension::class)
class PluginUiTest {
    init {
        StepsLogger.init()
    }

    @Test
//    @Disabled
    @Video
    fun createTestProject(remoteRobot: RemoteRobot) = with(remoteRobot) {
        val commonSteps = CommonSteps(remoteRobot)
        val uiSteps = PluginUiTestSteps(remoteRobot)

        // Create a new project with a Git repository
        welcomeFrame {
            createNewProjectLink.click()
            dialog("New Project") {
                findText("Empty Project").click()
                checkBox("Create Git repository").select()
                button("Create").click()
            }
        }

        idea {
            step("Wait for smart mode 5 minutes") {
                commonSteps.waitForSmartMode(5)
            }

            // Commit the first set of files
            uiSteps.commitChanges("initial commit")

            // Create the first set of files for Commit0
            uiSteps.createNewFile("Test0", "Line1Commit0")
            uiSteps.createNewFile("Test1", "Line1Commit0")
            uiSteps.createNewFile("Test2", "Line1Commit0")

            // Commit the first set of files
            uiSteps.commitChanges("Commit0")

            // Switch to the Project view
            uiSteps.switchToProjectView()

            // Create the second set of files for Commit1
            uiSteps.createNewFile("Test3", "Line1Commit1")
            uiSteps.createNewFile("Test4", "Line1Commit1")
            uiSteps.createNewFile("Test5", "Line1Commit1")

            // Modify existing files
            uiSteps.modifyFile("Test1", "Line2Commit1")
            uiSteps.replaceFileContent("Test2", "Line1Commit1\nLine2Commit1")

            // Commit the second set of changes
            uiSteps.commitChanges("Commit1")
        }
    }
}

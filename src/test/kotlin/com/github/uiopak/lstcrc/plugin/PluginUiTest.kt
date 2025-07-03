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
        val steps = CommonSteps(remoteRobot)
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
                steps.waitForSmartMode(5)
            }
            step("Left click on ActionButton") {
                component("//div[@accessiblename='New File or Directory…']")
                    .click()
            }
            step("Left click at text 'File' on ListPopupImpl") {
                component("//div[@class='MyList']")
                    .findText("File").click()
            }
            keyboard {
                enterText("Test0")
                enter()
            }
            step("Left click on Don't ask again") {
                component("//div[@class='SouthPanel']/div[@class='JPanel']/div[@class='JCheckBox']")
                    .click()
            }
            step("Left click on Add") {
                component("//div[@accessiblename='Add' and @class='JButton']")
                    .click()
            }
            keyboard {
                enterText("Line1Commit0")
            }
            step("Left click on ProjectViewPane") {
                component("//div[@accessiblename='Project structure tree']")
                    .click()
            }
            step("Left click on ActionButton") {
                component("//div[@accessiblename='New File or Directory…']")
                    .click()
            }
            step("Left click on ListPopupImpl") {
                component("//div[@class='MyList']")
                    .findText("File").click()
            }
            keyboard {
                enterText("Test1")
                enter()
            }
            keyboard {
                enterText("Line1Commit0")
            }
            step("Left click on ProjectViewPane") {
                component("//div[@accessiblename='Project structure tree']")
                    .click()
            }
            step("Left click on ActionButton") {
                component("//div[@accessiblename='New File or Directory…']")
                    .click()
            }
            step("Left click on ListPopupImpl") {
                component("//div[@class='MyList']")
                    .findText("File").click()
            }
            keyboard {
                enterText("Test2")
                enter()
            }
            keyboard {
                enterText("Line1Commit0")
            }
            step("Left click on SquareStripeButton") {
                component("//div[@accessiblename='Commit']")
                    .click()
            }
            keyboard {
                step("Press 'Ctrl+K', select all files from active changelist") { hotKey(17, 75) }
            }
            keyboard {
                enterText("Commit0")
            }
            step("Left click on Commit") {
                component("//div[@accessiblename='Commit' and @class='MainButton']")
                    .click()
            }
            step("Left click on SquareStripeButton") {
                component("//div[@accessiblename='Project']")
                    .click()
            }
            step("Left click on ProjectViewPane") {
                component("//div[@accessiblename='Project structure tree']")
                    .click()
            }
            step("Left click on ActionButton") {
                component("//div[@accessiblename='New File or Directory…']")
                    .click()
            }
            step("Left click on ListPopupImpl") {
                component("//div[@class='MyList']")
                    .findText("File").click()
            }
            keyboard {
                enterText("Test3")
                enter()
            }
            keyboard {
                enterText("Line1Commit1")
            }
            step("Left click on ProjectViewPane") {
                component("//div[@accessiblename='Project structure tree']")
                    .click()
            }
            step("Left click on ActionButton") {
                component("//div[@accessiblename='New File or Directory…']")
                    .click()
            }
            step("Left click on ListPopupImpl") {
                component("//div[@class='MyList']")
                    .findText("File").click()
            }
            keyboard {
                enterText("Test4")
                enter()
            }
            keyboard {
                enterText("Line1Commit1")
            }
            step("Left click on ProjectViewPane") {
                component("//div[@accessiblename='Project structure tree']")
                    .click()
            }
            step("Left click on ActionButton") {
                component("//div[@accessiblename='New File or Directory…']")
                    .click()
            }
            step("Left click on ListPopupImpl") {
                component("//div[@class='MyList']")
                    .findText("File").click()
            }
            keyboard {
                enterText("Test5")
                enter()
            }
            keyboard {
                enterText("Line1Commit1")
            }
            step("Left click on Test1") {
                component("//div[@accessiblename='Test1' and @class='SimpleColoredComponent']")
                    .click()
            }
            keyboard {
                enter()
                enterText("Line2Commit1")
            }
            step("Left click on Test2") {
                component("//div[@accessiblename='Test2' and @class='SimpleColoredComponent']")
                    .click()
            }
            keyboard {
                step("Press 'Ctrl+A'") { hotKey(17, 65) }
                enterText("Line1Commit1")
                enter()
                enterText("Line2Commit1")
            }
            step("Left click on SquareStripeButton") {
                component("//div[@accessiblename='Commit']")
                    .click()
            }
            keyboard {
                step("Press 'Ctrl+K', select all files from active changelist") { hotKey(17, 75) }
            }
            step("Left click on Commit0") {
                component("//div[@accessiblename='Commit Message']")
                    .click()
            }
            keyboard {
                step("Press 'Ctrl+A'") { hotKey(17, 65) }
                enterText("Commit1")
            }
            step("Left click on Commit") {
                component("//div[@accessiblename='Commit' and @class='MainButton']")
                    .click()
            }
        }
    }
}
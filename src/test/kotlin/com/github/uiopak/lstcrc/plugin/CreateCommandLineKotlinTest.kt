// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.github.uiopak.lstcrc.plugin

import com.automation.remarks.junit5.Video
import com.github.uiopak.lstcrc.plugin.pages.actionMenu
import com.github.uiopak.lstcrc.plugin.pages.actionMenuItem
import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.ContainerFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.steps.CommonSteps
import com.intellij.remoterobot.utils.keyboard
import com.intellij.remoterobot.utils.waitFor
import com.intellij.remoterobot.utils.waitForIgnoringError
import org.assertj.swing.core.MouseButton
import com.github.uiopak.lstcrc.plugin.pages.*
import com.github.uiopak.lstcrc.plugin.steps.KotlinExampleSteps
//import com.github.uiopak.lstcrc.plugin.steps.JavaExampleSteps
import com.github.uiopak.lstcrc.plugin.utils.RemoteRobotExtension
import com.github.uiopak.lstcrc.plugin.utils.StepsLogger
import com.intellij.remoterobot.fixtures.HeavyWeightWindowFixture
import com.intellij.remoterobot.stepsProcessing.step
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
//import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.awt.event.KeyEvent.*
import java.time.Duration.ofMinutes
import java.time.Duration.ofSeconds

@ExtendWith(RemoteRobotExtension::class)
class CreateCommandLineKotlinTest {
    init {
        StepsLogger.init()
    }

    @BeforeEach
    fun waitForIde(remoteRobot: RemoteRobot) {
        waitForIgnoringError(ofMinutes(3)) { remoteRobot.callJs("true") }
    }

    @AfterEach
    fun closeProject(remoteRobot: RemoteRobot) = with(remoteRobot) {
        CommonSteps(remoteRobot).closeProject()
    }

    @Test
//    @Disabled
    @Video
    fun createCommandLineApp(remoteRobot: RemoteRobot) = with(remoteRobot) {
        val sharedSteps = KotlinExampleSteps(this)

        welcomeFrame {
            createNewProjectLink.click()
            dialog("New Project") {
                findText("Java").click()
                checkBox("Add sample code").select()
                button("Create").click()
            }
        }
        Thread.sleep(15_000)
        idea {
            waitFor(ofMinutes(5)) { isDumbMode().not() }
            step("Create App file") {
                with(projectViewTree) {
                    if (hasText("src").not()) {
                        findText(projectName).doubleClick()
                        waitFor { hasText("src") }
                    }
                    findText("src").click(MouseButton.RIGHT_BUTTON)
                }
                actionMenu("New").click()
                actionMenuItem("Java Class").click()
                keyboard { enterText("App"); enter() }
            }
//            Thread.sleep(5_000)
            with(textEditor()) {
                step("Write a code") {
                    Thread.sleep(15_000)
                    editor.findText("App").click()
                    keyboard {
                        key(VK_END)

                        enter()
                    }
                    sharedSteps.autocomplete("main")
                    sharedSteps.autocomplete("sout")
                    keyboard { enterText("\""); enterText("Hello from UI test") }
                }
//                Thread.sleep(5_000)
                step("Launch application") {
                    waitFor(ofSeconds(20)) {
                        button(byXpath("//div[@class='TrafficLightButton']")).hasText("Analyzing...").not()
                    }
//                    button(byXpath("//div[@tooltiptext='Main Menu']")).click()
//                    menuBar.select("Build", "Build Project")
                    waitFor { gutter.getIcons().isNotEmpty() }
                    gutter.getIcons().first { it.description.contains("run.svg") }.click()
                    val find0 = this@idea.find<HeavyWeightWindowFixture>()
                    val find = this@idea.find<CommonContainerFixture>(
                        byXpath("//div[@class='HeavyWeightWindow']"), ofSeconds(4)
                    )
                    val findText = find.findText("Run 'App.main()'")
                    findText.click()
                }
            }

            val consoleLocator = byXpath("ConsoleViewImpl", "//div[@class='ConsoleViewImpl']")
            step("Wait for Console appears") {
                waitFor(ofMinutes(1)) { findAll<ContainerFixture>(consoleLocator).isNotEmpty() }
            }
            step("Check the message") {
                waitFor(ofMinutes(1)) { find<ContainerFixture>(consoleLocator).hasText("Hello from UI test") }
            }
        }
    }
}

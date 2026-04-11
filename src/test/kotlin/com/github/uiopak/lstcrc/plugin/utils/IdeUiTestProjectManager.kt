package com.github.uiopak.lstcrc.plugin.utils

import com.github.uiopak.lstcrc.plugin.pages.DialogFixture
import com.github.uiopak.lstcrc.plugin.pages.WelcomeFrame
import com.github.uiopak.lstcrc.plugin.pages.welcomeFrame
import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.waitFor
import java.time.Duration

private val welcomeFrameLocator = byXpath("Welcome frame", "//div[@class='FlatWelcomeFrame']")
private val ideaFrameLocator = byXpath("Idea frame", "//div[@class='IdeFrameImpl']")
private val newProjectButtonLocator = byXpath(
    "New Project button",
    "//div[(@class='MainButton' and @text='New Project') or (@accessiblename='New Project' and @class='JButton')]"
)

fun RemoteRobot.resetIdeToWelcomeScreen() {
    step("Reset IDE to welcome screen") {
        waitFor(Duration.ofMinutes(2), interval = Duration.ofSeconds(1)) {
            if (findAll<ComponentFixture>(welcomeFrameLocator).isNotEmpty()) {
                return@waitFor true
            }

            closeAllProjectsWithoutSaving()
            false
        }
    }
}

fun RemoteRobot.createFreshProjectFromWelcomeScreen() {
    resetIdeToWelcomeScreen()

    step("Create a fresh empty project from the welcome screen") {
        waitFor(Duration.ofSeconds(30), interval = Duration.ofMillis(500)) {
            if (findAll<ComponentFixture>(DialogFixture.byTitle("New Project")).isNotEmpty()) {
                return@waitFor true
            }

            if (findAll<ComponentFixture>(welcomeFrameLocator).isNotEmpty()) {
                runCatching {
                    find<ComponentFixture>(newProjectButtonLocator, Duration.ofSeconds(2)).click()
                }
            }

            false
        }

        find<DialogFixture>(DialogFixture.byTitle("New Project"), Duration.ofSeconds(15)).apply {
            findText("Empty Project").click()
            checkBox("Create Git repository").select()
            button("Create").click()
        }

        waitFor(Duration.ofMinutes(1), interval = Duration.ofSeconds(1)) {
            findAll<ComponentFixture>(ideaFrameLocator).isNotEmpty()
        }
    }
}

private fun RemoteRobot.closeAllProjectsWithoutSaving() {
    if (findAll<ComponentFixture>(ideaFrameLocator).isEmpty()) {
        runJs(
            """
            com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame.showIfNoProjectOpened();
            """.trimIndent(),
            true
        )
        return
    }

    runJs(
        """
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait(new java.lang.Runnable({
            run: function() {
                const projectManager = com.intellij.openapi.project.ex.ProjectManagerEx.getInstanceEx();
                const openProjects = java.util.Arrays.asList(projectManager.getOpenProjects());
                for (let i = 0; i < openProjects.size(); i++) {
                    projectManager.forceCloseProject(openProjects.get(i), false);
                }
                com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame.showIfNoProjectOpened();
            }
        }));
        """.trimIndent(),
        true
    )
}
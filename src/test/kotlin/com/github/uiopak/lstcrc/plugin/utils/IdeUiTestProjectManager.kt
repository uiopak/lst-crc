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
private val newUsersOnboardingDialogLocator = byXpath(
    "New users onboarding dialog",
    "//div[@accessiblename.key='newUiOnboarding.dialog.title' or @text.key='newUiOnboarding.dialog.title']"
)
private val newUsersOnboardingSkipLocator = byXpath(
    "New users onboarding skip",
    "//div[@class='ActionLink' and (@accessiblename.key='dialog.skip' or @text.key='dialog.skip' or @accessiblename='Skip' or @text='Skip')]"
)
fun RemoteRobot.resetIdeToWelcomeScreen() {
    step("Reset IDE to welcome screen") {
        suppressNewUsersOnboarding()

        waitFor(Duration.ofMinutes(2), interval = Duration.ofSeconds(1)) {
            val welcomeVisible = findAll<ComponentFixture>(welcomeFrameLocator).isNotEmpty()
            val ideaFrameVisible = findAll<ComponentFixture>(ideaFrameLocator).isNotEmpty()

            if (welcomeVisible && !ideaFrameVisible) {
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
        suppressNewUsersOnboarding()

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
            runCatching {
                waitFor(Duration.ofSeconds(5), interval = Duration.ofMillis(250)) {
                    findAllText("Empty Project").isNotEmpty()
                }
                findText("Empty Project").click()
            }
            button("Create").click()
        }

        waitFor(Duration.ofMinutes(1), interval = Duration.ofSeconds(1)) {
            findAll<ComponentFixture>(ideaFrameLocator).isNotEmpty()
        }

        dismissNewUsersOnboardingIfPresent()
    }
}

private fun RemoteRobot.suppressNewUsersOnboarding() {
    runJs(
        """
        try {
            com.intellij.openapi.util.registry.Registry.get("ide.newUsersOnboarding").setValue(false);
        }
        catch (ignored) {
        }

        com.intellij.ide.util.PropertiesComponent.getInstance().setValue("NEW_USERS_ONBOARDING_DIALOG_SHOWN", true);
        """.trimIndent(),
        true
    )
}

private fun RemoteRobot.dismissNewUsersOnboardingIfPresent() {
    step("Dismiss new users onboarding if present") {
        val watchWindow = if (System.getenv("GITHUB_ACTIONS") == "true") {
            Duration.ofSeconds(15)
        } else {
            Duration.ofSeconds(3)
        }
        val quietPeriodNanos = Duration.ofSeconds(2).toNanos()
        val watchDeadline = System.nanoTime() + watchWindow.toNanos()
        var lastSeenAtNanos: Long? = null

        waitFor(watchWindow.plusSeconds(2), interval = Duration.ofMillis(500)) {
            val dialogVisible = findAll<ComponentFixture>(newUsersOnboardingDialogLocator).isNotEmpty()
            if (dialogVisible) {
                lastSeenAtNanos = System.nanoTime()
                runCatching {
                    find<ComponentFixture>(newUsersOnboardingSkipLocator, Duration.ofSeconds(2)).click()
                }
                return@waitFor false
            }

            val lastSeen = lastSeenAtNanos
            if (lastSeen != null) {
                return@waitFor System.nanoTime() - lastSeen >= quietPeriodNanos
            }

            System.nanoTime() >= watchDeadline
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
package com.github.uiopak.lstcrc.plugin.utils

import com.github.uiopak.lstcrc.plugin.pages.DialogFixture
import com.github.uiopak.lstcrc.plugin.pages.WelcomeFrame
import com.github.uiopak.lstcrc.plugin.pages.welcomeFrame
import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.waitFor
import java.nio.file.Files
import java.nio.file.Path
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

        val projectDir = createFreshProjectDirectory()
        val projectDirJs = toJsStringLiteral(projectDir.toString())
        val projectNameJs = toJsStringLiteral(projectDir.fileName.toString())

        runJs(
            """
            importPackage(java.nio.file)
            importPackage(com.intellij.ide.impl)
            importPackage(com.intellij.openapi.application)
            importPackage(com.intellij.openapi.module)
            importPackage(com.intellij.openapi.roots)
            importPackage(com.intellij.openapi.vfs)
            importPackage(com.intellij.openapi.project.ex)

            ApplicationManager.getApplication().invokeLater(new java.lang.Runnable({
                run: function() {
                    const projectDir = Paths.get($projectDirJs);
                    Files.createDirectories(projectDir);

                    const projectManager = ProjectManagerEx.getInstanceEx();
                    const options = OpenProjectTask.build().asNewProject().withProjectName($projectNameJs);
                    const project = projectManager.newProject(projectDir, options);
                    if (project == null) {
                        throw new java.lang.IllegalStateException("Failed to create project at " + projectDir);
                    }

                    const openedProject = projectManager.openProject(projectDir, options.withProject(project));
                    if (openedProject == null) {
                        throw new java.lang.IllegalStateException("Failed to open project at " + projectDir);
                    }

                    ApplicationManager.getApplication().runWriteAction(new java.lang.Runnable({
                        run: function() {
                            const moduleManager = ModuleManager.getInstance(openedProject);
                            const modulesDir = projectDir.resolve(".idea").resolve("modules");
                            Files.createDirectories(modulesDir);

                            let module = moduleManager.getModules().length > 0 ? moduleManager.getModules()[0] : null;
                            if (module == null) {
                                const moduleFile = modulesDir.resolve($projectNameJs + ".iml");
                                module = moduleManager.newModule(moduleFile, EmptyModuleType.getInstance().getId());
                            }

                            const rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
                            try {
                                const normalizedPath = String(projectDir).split('\\\\').join('/');
                                const projectRoot = LocalFileSystem.getInstance().refreshAndFindFileByPath(normalizedPath);
                                if (projectRoot == null) {
                                    throw new java.lang.IllegalStateException("Failed to resolve project root at " + normalizedPath);
                                }

                                const existingEntries = rootModel.getContentEntries();
                                let hasProjectRoot = false;
                                for (let i = 0; i < existingEntries.length; i++) {
                                    const entryFile = existingEntries[i].getFile();
                                    if (entryFile != null && entryFile.equals(projectRoot)) {
                                        hasProjectRoot = true;
                                        break;
                                    }
                                }

                                if (!hasProjectRoot) {
                                    rootModel.addContentEntry(projectRoot);
                                }
                                rootModel.inheritSdk();
                            }
                            finally {
                                rootModel.commit();
                            }
                        }
                    }));
                }
            }));
            """.trimIndent(),
            true
        )

        waitFor(Duration.ofMinutes(1), interval = Duration.ofSeconds(1)) {
            findAll<ComponentFixture>(ideaFrameLocator).isNotEmpty() &&
                findAll<ComponentFixture>(welcomeFrameLocator).isEmpty()
        }

        dismissNewUsersOnboardingIfPresent()
    }
}

private fun createFreshProjectDirectory(): Path {
    val root = Path.of("build", "remote-ui-projects").toAbsolutePath().normalize()
    Files.createDirectories(root)
    return Files.createTempDirectory(root, "remote-ui-project-")
}

private fun toJsStringLiteral(value: String): String {
    return buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
        append('"')
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
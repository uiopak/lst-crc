package com.github.uiopak.lstcrc.plugin.pages

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.*
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.keyboard
import com.intellij.remoterobot.utils.waitFor
import java.time.Duration

fun IdeaFrame.branchSelection(function: BranchSelectionFixture.() -> Unit) {
    val timeout = if (System.getenv("GITHUB_ACTIONS") == "true") Duration.ofSeconds(90) else Duration.ofSeconds(20)
    val locator = byXpath("//div[@class='BranchSelectionPanel']")
    var branchSelectionFixture: BranchSelectionFixture? = null
    waitFor(timeout, interval = Duration.ofMillis(500)) {
        branchSelectionFixture = remoteRobot.findAll<BranchSelectionFixture>(locator).firstOrNull()
        branchSelectionFixture != null
    }
    branchSelectionFixture!!.apply(function)
}

@FixtureName("BranchSelection")
class BranchSelectionFixture(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) :
    CommonContainerFixture(remoteRobot, remoteComponent) {

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

    companion object {
        private val searchFieldLocator = byXpath(
            "Branch search field",
            "//div[@class='BranchSelectionPanel']//div[@accessiblename='Search' and @class='TextFieldWithProcessing']"
        )
        private val treeLocator = byXpath(
            "Branch selection tree",
            "//div[@class='BranchSelectionPanel']//div[@class='Tree']"
        )
    }

    private fun waitForSearchField(): ComponentFixture {
        val timeout = if (System.getenv("GITHUB_ACTIONS") == "true") Duration.ofSeconds(30) else Duration.ofSeconds(10)
        var searchField: ComponentFixture? = null
        waitFor(timeout, interval = Duration.ofMillis(250)) {
            searchField = remoteRobot.findAll<ComponentFixture>(searchFieldLocator).firstOrNull()
            searchField != null
        }
        return searchField!!
    }

    private fun waitForBranchTree(): ContainerFixture {
        val timeout = if (System.getenv("GITHUB_ACTIONS") == "true") Duration.ofSeconds(30) else Duration.ofSeconds(10)
        var branchTree: ContainerFixture? = null
        waitFor(timeout, interval = Duration.ofMillis(250)) {
            branchTree = remoteRobot.findAll<ContainerFixture>(treeLocator).firstOrNull()
            branchTree != null
        }
        return branchTree!!
    }

    private fun waitForPanelToClose() {
        val timeout = if (System.getenv("GITHUB_ACTIONS") == "true") Duration.ofSeconds(30) else Duration.ofSeconds(10)
        waitFor(timeout, interval = Duration.ofMillis(250)) {
            remoteRobot.findAll<ComponentFixture>(byXpath("//div[@class='BranchSelectionPanel']")).isEmpty()
        }
    }

    fun searchAndSelect(branchName: String) {
        step("Search and select branch '$branchName'") {
            remoteRobot.runJs(
                """
                const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
                if (project) {
                    const basePath = project.getBasePath();
                    if (basePath != null) {
                        const fileSystem = com.intellij.openapi.vfs.LocalFileSystem.getInstance();
                        const normalizedBasePath = String(basePath).split("\\").join("/");
                        const projectDir = fileSystem.refreshAndFindFileByPath(normalizedBasePath);
                        if (projectDir != null) {
                            projectDir.refresh(false, true);
                            const gitDir = projectDir.findChild(".git");
                            if (gitDir != null) {
                                gitDir.refresh(false, true);
                            }
                        }
                    }
                    com.intellij.openapi.vcs.changes.VcsDirtyScopeManager.getInstance(project).markEverythingDirty();
                }
                """.trimIndent(),
                false
            )

            val searchField = waitForSearchField()
            searchField.click()

            remoteRobot.runJs(
                """
                const value = ${toJsStringLiteral(branchName)};
                const focusOwner = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait(new java.lang.Runnable({
                    run: function() {
                        if (focusOwner instanceof javax.swing.text.JTextComponent) {
                            focusOwner.setText(value);
                        }
                    }
                }));
                """.trimIndent(),
                true
            )

            keyboard {
                enter()
            }

            // The tree should now show the branch. We double click it.
            val tree = waitForBranchTree()
            val timeout = if (System.getenv("GITHUB_ACTIONS") == "true") Duration.ofSeconds(60) else Duration.ofSeconds(20)
            waitFor(timeout, interval = Duration.ofMillis(500)) {
                tree.findAllText(branchName).isNotEmpty()
            }
            tree.findText(branchName).doubleClick()

            waitForPanelToClose()

            remoteRobot.runJs(
                """
                const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
                if (project) {
                    const pluginId = com.intellij.openapi.extensions.PluginId.getId("com.github.uiopak.lstcrc");
                    const plugin = com.intellij.ide.plugins.PluginManagerCore.getPlugin(pluginId);
                    if (plugin != null) {
                        const stateServiceClass = plugin.getPluginClassLoader()
                            .loadClass("com.github.uiopak.lstcrc.services.ToolWindowStateService");
                        const stateService = project.getService(stateServiceClass);
                        if (stateService != null) {
                            stateService.refreshDataForCurrentSelection().join();
                        }
                    }
                }
                """.trimIndent(),
                false
            )
        }
    }
}

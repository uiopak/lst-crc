package com.github.uiopak.lstcrc.plugin.pages

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.*
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
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

    companion object {
        private val treeLocator = byXpath(
            "Branch selection tree",
            "//div[@class='BranchSelectionPanel']//div[@class='Tree']"
        )
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

    private fun ContainerFixture.isShowingOnScreen(): Boolean {
        return runCatching {
            callJs<Boolean>("component.isShowing()", true)
        }.getOrDefault(false)
    }

    private fun findShowingBranchTree(): ContainerFixture? {
        return remoteRobot.findAll<ContainerFixture>(treeLocator)
            .firstOrNull { it.isShowingOnScreen() }
    }

    private fun waitForBranchTree(): ContainerFixture {
        val timeout = if (System.getenv("GITHUB_ACTIONS") == "true") Duration.ofSeconds(30) else Duration.ofSeconds(10)
        var branchTree: ContainerFixture? = null
        waitFor(timeout, interval = Duration.ofMillis(250)) {
            branchTree = findShowingBranchTree()
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

    fun setSearchTerm(searchTerm: String) {
        step("Set branch search term to '$searchTerm'") {
            runJs(
                """
                (function() {
                    function findComponent(root, classNameSuffix) {
                        if (!root) return null;
                        if (String(root.getClass().getName()).endsWith(classNameSuffix)) return root;
                        if (!root.getComponents) return null;
                        const children = root.getComponents();
                        for (let i = 0; i < children.length; i++) {
                            const match = findComponent(children[i], classNameSuffix);
                            if (match != null) return match;
                        }
                        return null;
                    }

                    const searchField = findComponent(component, "SearchTextField");
                    if (searchField != null) {
                        searchField.setText(${toJsStringLiteral(searchTerm)});
                    }
                })();
                """.trimIndent(),
                true
            )
        }
    }

    fun hasVisibleBranchText(text: String): Boolean {
        val tree = findShowingBranchTree() ?: return false
        return runCatching { tree.findAllText(text).isNotEmpty() }.getOrDefault(false)
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

            val timeout = if (System.getenv("GITHUB_ACTIONS") == "true") Duration.ofSeconds(60) else Duration.ofSeconds(20)
            val branchLabel = branchName.substringAfterLast('/')
            setSearchTerm(branchName)
            waitFor(timeout, interval = Duration.ofMillis(500)) {
                val tree = findShowingBranchTree() ?: return@waitFor false
                runCatching { tree.findAllText(branchLabel).isNotEmpty() }.getOrDefault(false)
            }
            val tree = waitForBranchTree()
            tree.findText(branchLabel).doubleClick()

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

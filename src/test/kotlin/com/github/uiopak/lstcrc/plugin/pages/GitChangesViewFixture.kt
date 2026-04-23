package com.github.uiopak.lstcrc.plugin.pages

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.*
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.waitFor
import org.assertj.swing.core.MouseButton
import java.time.Duration

fun IdeaFrame.gitChangesView(function: GitChangesViewFixture.() -> Unit) {
    waitFor(Duration.ofSeconds(10), interval = Duration.ofMillis(250)) {
        findAll<GitChangesViewFixture>(byXpath("//div[@class='LstCrcChangesBrowser' and @visible='true']")).isNotEmpty()
    }
    find<GitChangesViewFixture>(byXpath("//div[@class='LstCrcChangesBrowser' and @visible='true']"), Duration.ofSeconds(10)).apply(function)
}

@FixtureName("GitChangesView")
class GitChangesViewFixture(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) :
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

    private fun tabLocator(tabName: String) = byXpath(
        "Tab '$tabName'",
        "//div[@class='ContentTabLabel' and (@text='$tabName' or @accessiblename='$tabName' or @visible_text='$tabName')]"
    )

    private fun hasContentTab(tabName: String): Boolean = remoteRobot.callJs(
        """
        const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
        if (!project) {
            false;
        } else {
            const toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("GitChangesView");
            const contentManager = toolWindow ? toolWindow.getContentManager() : null;
            if (!contentManager) {
                false;
            } else {
                const tabName = ${toJsStringLiteral(tabName)};
                java.util.Arrays.stream(contentManager.getContents())
                    .anyMatch(content => tabName.equals(content.getDisplayName()));
            }
        }
        """.trimIndent(),
        true
    )

    private fun selectContentTab(tabName: String): Boolean = remoteRobot.callJs(
        """
        const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
        if (!project) {
            false;
        } else {
            const toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("GitChangesView");
            const contentManager = toolWindow ? toolWindow.getContentManager() : null;
            if (!contentManager) {
                false;
            } else {
                const tabName = ${toJsStringLiteral(tabName)};
                const content = java.util.Arrays.stream(contentManager.getContents())
                    .filter(item => tabName.equals(item.getDisplayName()))
                    .findFirst()
                    .orElse(null);
                if (content == null) {
                    false;
                } else {
                    contentManager.setSelectedContent(content, true);
                    const browser = content.getComponent();
                    const stateServiceClass = browser.getClass().getClassLoader()
                        .loadClass("com.github.uiopak.lstcrc.services.ToolWindowStateService");
                    const stateService = project.getService(stateServiceClass);
                    if (stateService != null) {
                        const selectedIndex = java.util.Arrays.stream(contentManager.getContents())
                            .filter(item => item.isCloseable())
                            .toList()
                            .indexOf(content);
                        stateService.setSelectedTab(selectedIndex);
                        stateService.refreshDataForCurrentSelection();
                    }
                    true;
                }
            }
        }
        """.trimIndent(),
        true
    )

    private fun selectedContentTabName(): String? = remoteRobot.callJs(
        """
        const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
        if (!project) {
            null;
        } else {
            const toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("GitChangesView");
            const contentManager = toolWindow ? toolWindow.getContentManager() : null;
            const selectedContent = contentManager ? contentManager.getSelectedContent() : null;
            selectedContent ? selectedContent.getDisplayName() : null;
        }
        """.trimIndent(),
        true
    )

    val tabContents: List<ComponentFixture>
        get() = remoteRobot.findAll(byXpath("//div[@class='ContentTabLabel']"))

    fun hasTab(tabName: String): Boolean {
        return hasContentTab(tabName)
    }

    fun selectTab(tabName: String) {
        step("Select tab '$tabName'") {
            waitFor(Duration.ofSeconds(10), interval = Duration.ofMillis(500)) {
                hasContentTab(tabName)
            }

            if (!selectContentTab(tabName)) {
                val visibleTabs = remoteRobot.findAll<ComponentFixture>(tabLocator(tabName))
                check(visibleTabs.isNotEmpty()) { "Could not find tab '$tabName'" }
                visibleTabs.first().click()
            }

            waitFor(Duration.ofSeconds(10), interval = Duration.ofMillis(250)) {
                selectedContentTabName() == tabName
            }

            runJs(
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

    val changesTree: ContainerFixture
        get() = remoteRobot.find(byXpath("//div[@class='LstCrcAsyncChangesTree' or @class='ChangesTree']"))

    fun clickChange(fileName: String, button: MouseButton = MouseButton.LEFT_BUTTON) {
        step("Click '$fileName' with $button") {
            waitFor(Duration.ofSeconds(10), interval = Duration.ofMillis(250)) {
                changesTree.findAllText(fileName).isNotEmpty()
            }
            changesTree.findText(fileName).click(button)
        }
    }

    fun doubleClickChange(fileName: String) {
        step("Double click '$fileName'") {
            waitFor(Duration.ofSeconds(10), interval = Duration.ofMillis(250)) {
                changesTree.findAllText(fileName).isNotEmpty()
            }
            changesTree.findText(fileName).doubleClick()
        }
    }

    fun rightClickChange(fileName: String) {
        clickChange(fileName, MouseButton.RIGHT_BUTTON)
    }

    fun addTab() {
        step("Click 'Add Tab' button") {
            val addTabLocator = byXpath("//div[@accessiblename='Add Tab']")
            waitFor(Duration.ofSeconds(10), interval = Duration.ofMillis(250)) {
                remoteRobot.findAll<ComponentFixture>(addTabLocator).isNotEmpty()
            }

            remoteRobot.find<ComponentFixture>(addTabLocator).click()

            val branchPanelLocator = byXpath("//div[@class='BranchSelectionPanel']")
            val openedFromClick = runCatching {
                waitFor(Duration.ofSeconds(5), interval = Duration.ofMillis(250)) {
                    remoteRobot.findAll<ComponentFixture>(branchPanelLocator).isNotEmpty()
                }
                true
            }.getOrDefault(false)

            if (!openedFromClick) {
                remoteRobot.runJs(
                    """
                    const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
                    if (project) {
                        const toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("GitChangesView");
                        const pluginId = com.intellij.openapi.extensions.PluginId.getId("com.github.uiopak.lstcrc");
                        const plugin = com.intellij.ide.plugins.PluginManagerCore.getPlugin(pluginId);
                        if (toolWindow != null && plugin != null) {
                            const helperClass = plugin.getPluginClassLoader()
                                .loadClass("com.github.uiopak.lstcrc.toolWindow.ToolWindowHelper");
                            const helper = helperClass.getField("INSTANCE").get(null);
                            helperClass
                                .getMethod(
                                    "openBranchSelectionTab",
                                    com.intellij.openapi.project.Project,
                                    com.intellij.openapi.wm.ToolWindow
                                )
                                .invoke(helper, project, toolWindow);
                        }
                    }
                    """.trimIndent(),
                    true
                )
            }
        }
    }
}

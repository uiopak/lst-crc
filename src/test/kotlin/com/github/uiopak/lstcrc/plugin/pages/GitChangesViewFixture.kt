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
    val timeout = if (System.getenv("GITHUB_ACTIONS") == "true") Duration.ofSeconds(30) else Duration.ofSeconds(10)
    val locator = byXpath("//div[@class='LstCrcChangesBrowser' and @visible='true']")
    waitFor(timeout, interval = Duration.ofMillis(250)) {
        findAll<GitChangesViewFixture>(locator).isNotEmpty()
    }
    findAll<GitChangesViewFixture>(locator).first().apply(function)
}

@FixtureName("GitChangesView")
class GitChangesViewFixture(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) :
    CommonContainerFixture(remoteRobot, remoteComponent) {

    private val branchSelectionPanelLocator = byXpath("//div[@class='BranchSelectionPanel']")

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

            val branchSelectionOpenTimeout = if (System.getenv("GITHUB_ACTIONS") == "true") Duration.ofSeconds(30) else Duration.ofSeconds(10)
            val openedFromClick = runCatching {
                waitFor(branchSelectionOpenTimeout, interval = Duration.ofMillis(250)) {
                    remoteRobot.findAll<ComponentFixture>(branchSelectionPanelLocator).isNotEmpty()
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

                waitFor(branchSelectionOpenTimeout, interval = Duration.ofMillis(250)) {
                    remoteRobot.findAll<ComponentFixture>(branchSelectionPanelLocator).isNotEmpty()
                }
            }
        }
    }

    fun invokeRenameTabAction(tabName: String) {
        step("Invoke rename action for tab '$tabName'") {
            waitFor(Duration.ofSeconds(10), interval = Duration.ofMillis(250)) {
                remoteRobot.findAll<ComponentFixture>(tabLocator(tabName)).isNotEmpty()
            }
            val tab = remoteRobot.findAll<ComponentFixture>(tabLocator(tabName)).first()
            tab.click()
            tab.runJs(
                """
                const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
                if (!project) {
                    throw new java.lang.IllegalStateException("No open project available for RenameTabAction");
                }

                const toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("GitChangesView");
                if (!toolWindow) {
                    throw new java.lang.IllegalStateException("GitChangesView tool window is not available");
                }

                const action = com.intellij.openapi.actionSystem.ActionManager.getInstance()
                    .getAction("com.github.uiopak.lstcrc.RenameTabAction");
                if (!action) {
                    throw new java.lang.IllegalStateException("RenameTabAction is not registered");
                }

                const dataContext = com.intellij.openapi.actionSystem.impl.SimpleDataContext.builder()
                    .add(com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT, project)
                    .add(com.intellij.openapi.actionSystem.PlatformDataKeys.TOOL_WINDOW, toolWindow)
                    .add(com.intellij.openapi.actionSystem.PlatformCoreDataKeys.CONTEXT_COMPONENT, component)
                    .build();
                const event = com.intellij.openapi.actionSystem.AnActionEvent.createFromAnAction(action, null, "test", dataContext);

                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(new java.lang.Runnable({
                    run: function() {
                        action.actionPerformed(event);
                    }
                }));
                """.trimIndent(),
                true
            )

            waitFor(Duration.ofSeconds(10), interval = Duration.ofMillis(250)) {
                remoteRobot.callJs<Boolean>(
                    """
                    (function() {
                        const focusOwner = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                        return focusOwner instanceof javax.swing.text.JTextComponent;
                    })();
                    """.trimIndent(),
                    true
                )
            }
        }
    }
}

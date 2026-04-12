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
    find<GitChangesViewFixture>(byXpath("//div[@class='LstCrcChangesBrowser']"), Duration.ofSeconds(10)).apply(function)
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

            val visibleTabs = remoteRobot.findAll<ComponentFixture>(tabLocator(tabName))
            if (visibleTabs.isNotEmpty()) {
                visibleTabs.first().click()
            } else {
                check(selectContentTab(tabName)) { "Could not select tab '$tabName' via content manager" }
            }

            waitFor(Duration.ofSeconds(10), interval = Duration.ofMillis(250)) {
                selectedContentTabName() == tabName
            }
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
            waitFor(Duration.ofSeconds(10), interval = Duration.ofMillis(250)) {
                remoteRobot.findAll<ComponentFixture>(byXpath("//div[@accessiblename='Add Tab']")).isNotEmpty()
            }
            remoteRobot.find<ComponentFixture>(byXpath("//div[@accessiblename='Add Tab']")).click()
        }
    }
}

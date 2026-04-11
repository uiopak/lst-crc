// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.github.uiopak.lstcrc.plugin.pages

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.*
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.waitFor
import java.time.Duration

fun RemoteRobot.idea(function: IdeaFrame.() -> Unit) {
    find<IdeaFrame>(timeout = Duration.ofSeconds(10)).apply(function)
}

@FixtureName("Idea frame")
@DefaultXpath("IdeFrameImpl type", "//div[@class='IdeFrameImpl']")
class IdeaFrame(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) :
    CommonContainerFixture(remoteRobot, remoteComponent) {

    val projectViewTree
        get() = find<ContainerFixture>(byXpath("MyProjectViewTree", "//div[@class='MyProjectViewTree']"))

    val projectName
        get() = step("Get project name") { return@step callJs<String>("component.getProject().getName()") }

    val menuBar: JMenuBarFixture
        get() = step("Menu...") {
            return@step remoteRobot.find(JMenuBarFixture::class.java, JMenuBarFixture.byType())
        }

    @JvmOverloads
    fun dumbAware(timeout: Duration = Duration.ofMinutes(5), function: () -> Unit) {
        step("Wait for smart mode") {
            waitFor(duration = timeout, interval = Duration.ofSeconds(5)) {
                runCatching { isDumbMode().not() }.getOrDefault(false)
            }
            function()
            step("..wait for smart mode again") {
                waitFor(duration = timeout, interval = Duration.ofSeconds(5)) {
                    isDumbMode().not()
                }
            }
        }
    }

    fun isDumbMode(): Boolean {
        return callJs(
            """
            const frameHelper = com.intellij.openapi.wm.impl.ProjectFrameHelper.getFrameHelper(component)
            if (frameHelper) {
                const project = frameHelper.getProject()
                project ? com.intellij.openapi.project.DumbService.isDumb(project) : true
            } else { 
                true 
            }
        """, true
        )
    }

    fun openFile(path: String) {
        runJs(
            """
            importPackage(com.intellij.openapi.fileEditor)
            importPackage(com.intellij.openapi.vfs)
            importPackage(com.intellij.openapi.wm.impl)
            importClass(com.intellij.openapi.application.ApplicationManager)
            
            const path = '$path'
            const frameHelper = ProjectFrameHelper.getFrameHelper(component)
            if (frameHelper) {
                const project = frameHelper.getProject()
                const projectPath = project.getBasePath()
                const file = LocalFileSystem.getInstance().findFileByPath(projectPath + '/' + path)
                const openFileFunction = new Runnable({
                    run: function() {
                        FileEditorManager.getInstance(project).openTextEditor(
                            new OpenFileDescriptor(
                                project,
                                file
                            ), true
                        )
                    }
                })
                ApplicationManager.getApplication().invokeLater(openFileFunction)
            }
        """, true
        )
    }

    fun openGitChangesView() {
        step("Open GitChangesView tool window") {
            runJs(
                """
                const frameHelper = com.intellij.openapi.wm.impl.ProjectFrameHelper.getFrameHelper(component);
                const project = frameHelper ? frameHelper.getProject() : null;
                if (project) {
                    const toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("GitChangesView");
                    if (toolWindow) {
                        toolWindow.show();
                        toolWindow.activate(null);
                    }
                }

                const actionManager = com.intellij.openapi.actionSystem.ActionManager.getInstance();
                const gitChangesViewAction = actionManager.getAction("ActivateGitChangesViewToolWindow");
                if (gitChangesViewAction) {
                    const dataContext = com.intellij.ide.DataManager.getInstance().getDataContext();
                    const event = com.intellij.openapi.actionSystem.AnActionEvent.createFromAnAction(gitChangesViewAction, null, "test", dataContext);
                    gitChangesViewAction.actionPerformed(event);
                }
            """, true
            )

            val toolWindowVisible = runCatching {
                waitFor(Duration.ofSeconds(5), interval = Duration.ofMillis(250)) {
                    remoteRobot.findAll<ComponentFixture>(byXpath("//div[@class='LstCrcChangesBrowser']")).isNotEmpty()
                }
                true
            }.getOrDefault(false)

            if (!toolWindowVisible) {
                remoteRobot.find<ComponentFixture>(
                    byXpath("//div[@class='TextPresentationComponent' and contains(@tooltiptext, 'LST-CRC')]")
                ).click()
            }
        }
    }

    fun resetGitChangesViewState() {
        step("Reset GitChangesView state") {
            runJs(
                """
                const frameHelper = com.intellij.openapi.wm.impl.ProjectFrameHelper.getFrameHelper(component);
                const project = frameHelper ? frameHelper.getProject() : null;
                if (project) {
                    com.intellij.ide.util.PropertiesComponent.getInstance().setValue(
                        "com.github.uiopak.lstcrc.app.includeHeadInScopes",
                        false,
                        false
                    );

                    const toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("GitChangesView");
                    if (toolWindow) {
                        const contentManager = toolWindow.getContentManager();
                        const contents = contentManager.getContents();
                        for (let i = contents.length - 1; i >= 0; i--) {
                            const content = contents[i];
                            if (content.isCloseable()) {
                                contentManager.removeContent(content, true);
                            }
                        }

                        const remainingContents = contentManager.getContents();
                        if (remainingContents.length > 0) {
                            contentManager.setSelectedContent(remainingContents[0], true);
                        }

                        toolWindow.hide();
                    }
                }
                """,
                true
            )
        }
    }
}
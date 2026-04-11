// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.github.uiopak.lstcrc.plugin.pages

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.*
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.waitFor
import org.assertj.swing.core.MouseButton
import java.time.Duration

fun RemoteRobot.idea(function: IdeaFrame.() -> Unit) {
    find<IdeaFrame>(timeout = Duration.ofSeconds(10)).apply(function)
}

@FixtureName("Idea frame")
@DefaultXpath("IdeFrameImpl type", "//div[@class='IdeFrameImpl']")
class IdeaFrame(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) :
    CommonContainerFixture(remoteRobot, remoteComponent) {

    companion object {
        private const val SINGLE_CLICK_ACTION_KEY = "com.github.uiopak.lstcrc.app.singleClickAction"
        private const val DOUBLE_CLICK_ACTION_KEY = "com.github.uiopak.lstcrc.app.doubleClickAction"
        private const val MIDDLE_CLICK_ACTION_KEY = "com.github.uiopak.lstcrc.app.middleClickAction"
        private const val DOUBLE_MIDDLE_CLICK_ACTION_KEY = "com.github.uiopak.lstcrc.app.doubleMiddleClickAction"
        private const val RIGHT_CLICK_ACTION_KEY = "com.github.uiopak.lstcrc.app.rightClickAction"
        private const val DOUBLE_RIGHT_CLICK_ACTION_KEY = "com.github.uiopak.lstcrc.app.doubleRightClickAction"
        private const val SHOW_CONTEXT_MENU_KEY = "com.github.uiopak.lstcrc.app.showContextMenu"
        private const val DOUBLE_CLICK_DELAY_KEY = "com.github.uiopak.lstcrc.app.userDoubleClickDelay"
        private const val INCLUDE_HEAD_IN_SCOPES_KEY = "com.github.uiopak.lstcrc.app.includeHeadInScopes"
        private const val ENABLE_GUTTER_MARKERS_KEY = "com.github.uiopak.lstcrc.app.enableGutterMarkers"
        private const val ENABLE_GUTTER_FOR_NEW_FILES_KEY = "com.github.uiopak.lstcrc.app.enableGutterForNewFiles"
        private const val SHOW_TOOL_WINDOW_TITLE_KEY = "com.github.uiopak.lstcrc.app.showToolWindowTitle"
        private const val SHOW_WIDGET_CONTEXT_KEY = "com.github.uiopak.lstcrc.app.showWidgetContext"
        private const val SHOW_CONTEXT_SINGLE_REPO_KEY = "com.github.uiopak.lstcrc.app.showContextSingleRepo"
        private const val SHOW_CONTEXT_FOR_COMMITS_KEY = "com.github.uiopak.lstcrc.app.showContextForCommits"
    }

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
                    const properties = com.intellij.ide.util.PropertiesComponent.getInstance();
                    properties.setValue("com.github.uiopak.lstcrc.app.singleClickAction", "OPEN_SOURCE");
                    properties.setValue("com.github.uiopak.lstcrc.app.doubleClickAction", "NONE");
                    properties.setValue("com.github.uiopak.lstcrc.app.middleClickAction", "SHOW_IN_PROJECT_TREE");
                    properties.setValue("com.github.uiopak.lstcrc.app.doubleMiddleClickAction", "NONE");
                    properties.setValue("com.github.uiopak.lstcrc.app.rightClickAction", "OPEN_DIFF");
                    properties.setValue("com.github.uiopak.lstcrc.app.doubleRightClickAction", "NONE");
                    properties.setValue("com.github.uiopak.lstcrc.app.showContextMenu", false, false);
                    properties.setValue("com.github.uiopak.lstcrc.app.userDoubleClickDelay", "-1");
                    properties.setValue("com.github.uiopak.lstcrc.app.includeHeadInScopes", false, false);
                    properties.setValue("com.github.uiopak.lstcrc.app.enableGutterMarkers", true, true);
                    properties.setValue("com.github.uiopak.lstcrc.app.enableGutterForNewFiles", false, false);
                    properties.setValue("com.github.uiopak.lstcrc.app.showToolWindowTitle", false, false);
                    properties.setValue("com.github.uiopak.lstcrc.app.showWidgetContext", false, false);
                    properties.setValue("com.github.uiopak.lstcrc.app.showContextSingleRepo", true, true);
                    properties.setValue("com.github.uiopak.lstcrc.app.showContextForCommits", false, false);

                    const toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("GitChangesView");
                    if (toolWindow) {
                        toolWindow.getComponent().putClientProperty(
                            com.intellij.openapi.wm.impl.content.ToolWindowContentUi.HIDE_ID_LABEL,
                            "true"
                        );
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

    fun configureLstCrcClickActions(
        singleClickAction: String? = null,
        doubleClickAction: String? = null,
        middleClickAction: String? = null,
        doubleMiddleClickAction: String? = null,
        rightClickAction: String? = null,
        doubleRightClickAction: String? = null,
        showContextMenu: Boolean? = null
    ) {
        step("Configure LST-CRC click actions") {
            val updates = buildList {
                singleClickAction?.let { add("properties.setValue('$SINGLE_CLICK_ACTION_KEY', ${toJsStringLiteral(it)});") }
                doubleClickAction?.let { add("properties.setValue('$DOUBLE_CLICK_ACTION_KEY', ${toJsStringLiteral(it)});") }
                middleClickAction?.let { add("properties.setValue('$MIDDLE_CLICK_ACTION_KEY', ${toJsStringLiteral(it)});") }
                doubleMiddleClickAction?.let { add("properties.setValue('$DOUBLE_MIDDLE_CLICK_ACTION_KEY', ${toJsStringLiteral(it)});") }
                rightClickAction?.let { add("properties.setValue('$RIGHT_CLICK_ACTION_KEY', ${toJsStringLiteral(it)});") }
                doubleRightClickAction?.let { add("properties.setValue('$DOUBLE_RIGHT_CLICK_ACTION_KEY', ${toJsStringLiteral(it)});") }
                showContextMenu?.let { add("properties.setValue('$SHOW_CONTEXT_MENU_KEY', ${if (it) "true" else "false"}, false);") }
            }

            check(updates.isNotEmpty()) { "At least one click action setting must be provided" }

            runJs(
                """
                const properties = com.intellij.ide.util.PropertiesComponent.getInstance();
                ${updates.joinToString("\n")}
                """.trimIndent(),
                true
            )
        }
    }

    fun clickSettingsSnapshot(): String {
        return step("Read LST-CRC click settings") {
            callJs<String>(
                """
                (function() {
                    const properties = com.intellij.ide.util.PropertiesComponent.getInstance();
                    const values = [
                        properties.getValue('$SINGLE_CLICK_ACTION_KEY', ''),
                        properties.getValue('$DOUBLE_CLICK_ACTION_KEY', ''),
                        properties.getValue('$MIDDLE_CLICK_ACTION_KEY', ''),
                        properties.getValue('$DOUBLE_MIDDLE_CLICK_ACTION_KEY', ''),
                        properties.getValue('$RIGHT_CLICK_ACTION_KEY', ''),
                        properties.getValue('$DOUBLE_RIGHT_CLICK_ACTION_KEY', ''),
                        String(properties.getBoolean('$SHOW_CONTEXT_MENU_KEY', false)),
                        properties.getValue('$DOUBLE_CLICK_DELAY_KEY', '')
                    ];
                    return values.join("|");
                })();
                """.trimIndent(),
                true
            )
        }
    }

    fun setDoubleClickDelayMs(delay: Int) {
        step("Set double click delay to ${delay}ms") {
            runJs(
                """
                const properties = com.intellij.ide.util.PropertiesComponent.getInstance();
                properties.setValue('$DOUBLE_CLICK_DELAY_KEY', ${toJsStringLiteral(delay.toString())});
                """.trimIndent(),
                true
            )
        }
    }

    fun selectedEditorDescriptor(): String {
        return step("Read selected editor descriptor") {
            callJs<String>(
                """
                (function() {
                    const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
                    if (!project) return "";

                    const manager = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project);
                    const selectedFiles = manager.getSelectedFiles();
                    if (selectedFiles.length === 0) return "";

                    const file = selectedFiles[0];
                    const fileType = file.getFileType();
                    return file.getName() + "|" + file.getClass().getName() + "|" + (fileType ? fileType.getName() : "");
                })();
                """.trimIndent(),
                true
            )
        }
    }

    fun closeAllEditors() {
        step("Close all editors") {
            runJs(
                """
                const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
                if (project) {
                    com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).closeAllFiles();
                }
                """.trimIndent(),
                true
            )
        }
    }

    fun selectedProjectViewDescriptor(): String {
        return step("Read selected project view item") {
            callJs<String>(
                """
                (function() {
                    const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
                    if (!project) return "";

                    const projectView = com.intellij.ide.projectView.ProjectView.getInstance(project);
                    const pane = projectView ? projectView.getCurrentProjectViewPane() : null;
                    if (!pane) return "";

                    const selected = pane.getSelectedElements ? pane.getSelectedElements() : null;
                    if (selected && selected.length > 0) {
                        const first = selected[0];
                        if (first && first.getName) return String(first.getName());
                        return String(first);
                    }

                    const tree = pane.getTree ? pane.getTree() : null;
                    const path = tree ? tree.getSelectionPath() : null;
                    if (!path) return "";

                    const node = path.getLastPathComponent();
                    return node == null ? "" : String(node);
                })();
                """.trimIndent(),
                true
            )
        }
    }

    fun hasDiffEditorOpen(): Boolean {
        return step("Check whether a diff editor is open") {
            callJs<Boolean>(
                """
                (function() {
                    const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
                    if (project) {
                        const manager = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project);
                        const allEditors = manager.getAllEditors();
                        for (let i = 0; i < allEditors.length; i++) {
                            const editor = allEditors[i];
                            if (editor.getClass().getName().toLowerCase().includes("diff")) {
                                return true;
                            }
                        }

                        const openFiles = manager.getOpenFiles();
                        for (let i = 0; i < openFiles.length; i++) {
                            const file = openFiles[i];
                            const className = file.getClass().getName();
                            const fileType = file.getFileType();
                            const fileTypeName = fileType ? fileType.getName() : "";
                            if (className.toLowerCase().includes("diff") || fileTypeName.toLowerCase().includes("diff")) {
                                return true;
                            }
                        }
                    }

                    const windows = java.awt.Window.getWindows();
                    for (let i = 0; i < windows.length; i++) {
                        const window = windows[i];
                        if (!window.isShowing()) continue;
                        if (window.getClass().getName().toLowerCase().includes("diff")) {
                            return true;
                        }
                    }
                    return false;
                })();
                """.trimIndent(),
                true
            )
        }
    }

    fun statusWidgetText(): String {
        return step("Read LST-CRC widget text") {
            callJs<String>(
                """
                (function() {
                    const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
                    if (!project) return "";

                    const statusBar = com.intellij.openapi.wm.WindowManager.getInstance().getStatusBar(project);
                    if (!statusBar) return "";

                    const widget = statusBar.getWidget("LstCrcStatusWidget");
                    if (!widget) return "";

                    const presentation = widget.getPresentation();
                    if (presentation && presentation.getText) {
                        return presentation.getText();
                    }
                    return widget.getText ? widget.getText() : "";
                })();
                """.trimIndent(),
                true
            )
        }
    }

    fun clickStatusWidget() {
        step("Click LST-CRC status widget") {
            remoteRobot.find<ComponentFixture>(
                byXpath("//div[@class='TextPresentationComponent' and contains(@tooltiptext, 'LST-CRC')]")
            ).click()
        }
    }

    fun setShowWidgetContext(show: Boolean) {
        step("Set widget context prefix to $show") {
            runJs(
                """
                const properties = com.intellij.ide.util.PropertiesComponent.getInstance();
                properties.setValue('$SHOW_WIDGET_CONTEXT_KEY', ${if (show) "true" else "false"}, false);
                """.trimIndent(),
                true
            )
        }
    }

    fun setShowToolWindowTitle(show: Boolean) {
        step("Set tool window title visibility to $show") {
            runJs(
                """
                (function() {
                    const properties = com.intellij.ide.util.PropertiesComponent.getInstance();
                    properties.setValue('$SHOW_TOOL_WINDOW_TITLE_KEY', ${if (show) "true" else "false"}, false);

                    const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
                    if (!project) return;

                    const toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("GitChangesView");
                    if (!toolWindow) return;

                    toolWindow.getComponent().putClientProperty(
                        com.intellij.openapi.wm.impl.content.ToolWindowContentUi.HIDE_ID_LABEL,
                        ${if (show) "null" else "'true'"}
                    );

                    const contentManager = toolWindow.getContentManager();
                    if (contentManager instanceof com.intellij.ui.content.impl.ContentManagerImpl) {
                        const ui = contentManager.getUI();
                        if (ui instanceof com.intellij.openapi.wm.impl.content.ToolWindowContentUi) {
                            ui.update();
                        }
                    }
                })();
                """.trimIndent(),
                true
            )
        }
    }

    fun isToolWindowTitleVisible(): Boolean {
        return step("Read tool window title visibility") {
            callJs<Boolean>(
                """
                (function() {
                    const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
                    if (!project) return false;

                    const toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("GitChangesView");
                    if (!toolWindow) return false;

                    return toolWindow.getComponent().getClientProperty(
                        com.intellij.openapi.wm.impl.content.ToolWindowContentUi.HIDE_ID_LABEL
                    ) == null;
                })();
                """.trimIndent(),
                true
            )
        }
    }

    fun setIncludeHeadInScopes(include: Boolean) {
        step("Set include HEAD in scopes to $include") {
            runJs(
                """
                (function() {
                    const properties = com.intellij.ide.util.PropertiesComponent.getInstance();
                    properties.setValue('$INCLUDE_HEAD_IN_SCOPES_KEY', ${if (include) "true" else "false"}, false);

                    const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
                    if (!project) return;

                    const toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("GitChangesView");
                    const browser = toolWindow && toolWindow.getContentManager().getSelectedContent()
                        ? toolWindow.getContentManager().getSelectedContent().getComponent()
                        : null;
                    if (browser && browser.requestRefreshData) {
                        browser.requestRefreshData();
                    }
                })();
                """.trimIndent(),
                true
            )
        }
    }

    fun setGutterSettings(enableMarkers: Boolean? = null, enableForNewFiles: Boolean? = null) {
        step("Update gutter settings") {
            check(enableMarkers != null || enableForNewFiles != null) { "At least one gutter setting must be provided" }

            val updates = buildList {
                enableMarkers?.let { add("properties.setValue('$ENABLE_GUTTER_MARKERS_KEY', ${if (it) "true" else "false"}, true);") }
                enableForNewFiles?.let { add("properties.setValue('$ENABLE_GUTTER_FOR_NEW_FILES_KEY', ${if (it) "true" else "false"}, false);") }
            }

            runJs(
                """
                (function() {
                    const properties = com.intellij.ide.util.PropertiesComponent.getInstance();
                    ${updates.joinToString("\n")}
                })();
                """.trimIndent(),
                true
            )
        }
    }

    fun setTreeContextSettings(showSingleRepo: Boolean? = null, showCommits: Boolean? = null) {
        step("Update tree context settings") {
            check(showSingleRepo != null || showCommits != null) { "At least one tree context setting must be provided" }

            val updates = buildList {
                showSingleRepo?.let { add("properties.setValue('$SHOW_CONTEXT_SINGLE_REPO_KEY', ${if (it) "true" else "false"}, true);") }
                showCommits?.let { add("properties.setValue('$SHOW_CONTEXT_FOR_COMMITS_KEY', ${if (it) "true" else "false"}, false);") }
            }

            runJs(
                """
                (function() {
                    const properties = com.intellij.ide.util.PropertiesComponent.getInstance();
                    ${updates.joinToString("\n")}

                    const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
                    if (!project) return;

                    const toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("GitChangesView");
                    const browser = toolWindow && toolWindow.getContentManager().getSelectedContent()
                        ? toolWindow.getContentManager().getSelectedContent().getComponent()
                        : null;
                    if (browser && browser.rebuildView) {
                        browser.rebuildView();
                    }
                })();
                """.trimIndent(),
                true
            )
        }
    }

    fun treeContextSettingsSnapshot(): String {
        return step("Read tree context settings") {
            callJs<String>(
                """
                (function() {
                    const properties = com.intellij.ide.util.PropertiesComponent.getInstance();
                    return String(properties.getBoolean('$SHOW_CONTEXT_SINGLE_REPO_KEY', true)) + "|" +
                        String(properties.getBoolean('$SHOW_CONTEXT_FOR_COMMITS_KEY', false));
                })();
                """.trimIndent(),
                true
            )
        }
    }

    fun gutterSettingsSnapshot(): String {
        return step("Read gutter settings") {
            callJs<String>(
                """
                (function() {
                    const properties = com.intellij.ide.util.PropertiesComponent.getInstance();
                    return String(properties.getBoolean('$ENABLE_GUTTER_MARKERS_KEY', true)) + "|" +
                        String(properties.getBoolean('$ENABLE_GUTTER_FOR_NEW_FILES_KEY', false)) + "|" +
                        String(properties.getBoolean('$INCLUDE_HEAD_IN_SCOPES_KEY', false));
                })();
                """.trimIndent(),
                true
            )
        }
    }

    fun selectedChangesTreeSnapshot(): String {
        return step("Read selected changes tree snapshot") {
            callJs<String>(
                """
                (function() {
                    const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
                    if (!project) return "";

                    const toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("GitChangesView");
                    const browser = toolWindow && toolWindow.getContentManager().getSelectedContent()
                        ? toolWindow.getContentManager().getSelectedContent().getComponent()
                        : null;
                    if (!browser || !browser.getViewer) return "";

                    const viewer = browser.getViewer();
                    const renderer = viewer.getCellRenderer();
                    const model = viewer.getModel();
                    const rows = [];

                    for (let row = 0; row < viewer.getRowCount(); row++) {
                        const path = viewer.getPathForRow(row);
                        if (!path) continue;

                        const node = path.getLastPathComponent();
                        const rendered = renderer.getTreeCellRendererComponent(
                            viewer,
                            node,
                            viewer.isRowSelected(row),
                            viewer.isExpanded(row),
                            model.isLeaf(node),
                            row,
                            false
                        );

                        let text = "";
                        const context = rendered.getAccessibleContext ? rendered.getAccessibleContext() : null;
                        if (context) {
                            text = context.getAccessibleName() || "";
                        }
                        if ((!text || text.length === 0) && rendered.getText) {
                            text = String(rendered.getText());
                        }
                        if ((!text || text.length === 0) && rendered.getCharSequence) {
                            text = String(rendered.getCharSequence(false));
                        }
                        if (text && text.length > 0) {
                            rows.push(text);
                        }
                    }

                    return rows.join("\n");
                })();
                """.trimIndent(),
                true
            )
        }
    }

    fun selectedLstCrcTabName(): String {
        return step("Read selected LST-CRC tab") {
            callJs<String>(
                """
                (function() {
                    const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
                    if (!project) return "";

                    const toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("GitChangesView");
                    if (!toolWindow) return "";

                    const content = toolWindow.getContentManager().getSelectedContent();
                    if (!content) return "";

                    const displayName = content.getDisplayName ? content.getDisplayName() : content.displayName;
                    return displayName == null ? "" : String(displayName);
                })();
                """.trimIndent(),
                true
            )
        }
    }

    fun hasLstCrcTab(tabName: String): Boolean {
        return remoteRobot.findAll<ComponentFixture>(
            byXpath("//div[@class='ContentTabLabel' and (@text='$tabName' or @accessiblename='$tabName' or @visible_text='$tabName')]")
        ).isNotEmpty()
    }

    fun selectedTabComparisonMap(): String {
        return step("Read selected tab comparison map") {
            callJs<String>(
                """
                (function() {
                    const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
                    if (!project) return "";

                    const toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("GitChangesView");
                    if (!toolWindow) return "";

                    const content = toolWindow.getContentManager().getSelectedContent();
                    if (!content) return "";

                    const displayName = content.getDisplayName ? String(content.getDisplayName()) : "";
                    const classLoader = content.getComponent().getClass().getClassLoader();
                    const stateServiceClass = java.lang.Class.forName("com.github.uiopak.lstcrc.services.ToolWindowStateService", true, classLoader);
                    const stateService = project.getService(stateServiceClass);
                    let tabInfo = stateService ? stateService.getSelectedTabInfo() : null;
                    const state = stateService ? stateService.getState() : null;
                    const openTabs = state ? state.getOpenTabs() : null;

                    if (openTabs && displayName.length > 0) {
                        const tabsIterator = openTabs.iterator();
                        while (tabsIterator.hasNext()) {
                            const candidate = tabsIterator.next();
                            if (candidate.getBranchName() === displayName || candidate.getAlias() === displayName) {
                                tabInfo = candidate;
                                break;
                            }
                        }
                    }

                    if (!tabInfo) return "";

                    const entries = [];
                    const entriesIterator = tabInfo.getComparisonMap().entrySet().iterator();
                    while (entriesIterator.hasNext()) {
                        const entry = entriesIterator.next();
                        entries.push(entry.getKey() + "=" + entry.getValue());
                    }
                    entries.sort();
                    return entries.join(";");
                })();
                """.trimIndent(),
                true
            )
        }
    }

    fun invokeCreateTabFromRevisionAction(revision: String, alias: String? = null) {
        step("Invoke CreateTabFromRevisionAction for $revision") {
            runJs(
                """
                const revision = ${toJsStringLiteral(revision)};
                const alias = ${alias?.let(::toJsStringLiteral) ?: "null"};
                const frameHelper = com.intellij.openapi.wm.impl.ProjectFrameHelper.getFrameHelper(component);
                const project = frameHelper ? frameHelper.getProject() : null;
                if (!project) {
                    throw new java.lang.IllegalStateException("No open project available for CreateTabFromRevisionAction");
                }

                const action = com.intellij.openapi.actionSystem.ActionManager.getInstance()
                    .getAction("com.github.uiopak.lstcrc.CreateTabFromRevisionAction");
                if (!action) {
                    throw new java.lang.IllegalStateException("CreateTabFromRevisionAction is not registered");
                }

                const actionClassLoader = action.getClass().getClassLoader();
                const toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("GitChangesView");
                if (!toolWindow) {
                    throw new java.lang.IllegalStateException("GitChangesView tool window is not available");
                }

                if (alias !== null) {
                    try {
                        const helperClass = java.lang.Class.forName("com.github.uiopak.lstcrc.toolWindow.ToolWindowHelper", true, actionClassLoader);
                        const helperInstance = helperClass.getField("INSTANCE").get(null);
                        const stateServiceClass = java.lang.Class.forName("com.github.uiopak.lstcrc.services.ToolWindowStateService", true, actionClassLoader);
                        const stateService = project.getService(stateServiceClass);

                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(new java.lang.Runnable({
                            run: function() {
                                helperInstance.createAndSelectTab(project, toolWindow, revision);
                                stateService.updateTabAlias(revision, alias);
                            }
                        }));
                    } catch (error) {
                        throw new java.lang.IllegalStateException("Aliased revision tab setup failed: " + error, error);
                    }
                } else {
                    const gitRevisionNumberClass = java.lang.Class.forName("git4idea.GitRevisionNumber", true, actionClassLoader);
                    let gitRevisionNumberConstructor = null;
                    const constructors = gitRevisionNumberClass.getConstructors();
                    for (let i = 0; i < constructors.length; i++) {
                        if (constructors[i].getParameterCount() === 1) {
                            gitRevisionNumberConstructor = constructors[i];
                            break;
                        }
                    }
                    if (!gitRevisionNumberConstructor) {
                        throw new java.lang.IllegalStateException("Unable to locate GitRevisionNumber(String) constructor");
                    }
                    const gitRevisionNumber = gitRevisionNumberConstructor.newInstance(revision);

                    const dataContext = com.intellij.openapi.actionSystem.impl.SimpleDataContext.builder()
                        .add(com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT, project)
                        .add(com.intellij.openapi.actionSystem.PlatformCoreDataKeys.CONTEXT_COMPONENT, component)
                        .add(
                            com.intellij.openapi.vcs.VcsDataKeys.VCS_REVISION_NUMBERS,
                            java.util.Collections.singletonList(gitRevisionNumber)
                        )
                        .build();

                    const event = com.intellij.openapi.actionSystem.AnActionEvent.createFromAnAction(action, null, "test", dataContext);
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(new java.lang.Runnable({
                        run: function() {
                            action.actionPerformed(event);
                        }
                    }));
                }
                """.trimIndent(),
                true
            )
        }
    }

    fun updateTabAlias(branchName: String, newAlias: String?) {
        step("Update tab alias for $branchName") {
            runJs(
                """
                const branchName = ${toJsStringLiteral(branchName)};
                const newAlias = ${newAlias?.let(::toJsStringLiteral) ?: "null"};
                const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
                if (!project) {
                    throw new java.lang.IllegalStateException("No open project available for tab alias update");
                }

                const toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("GitChangesView");
                const content = toolWindow ? toolWindow.getContentManager().getSelectedContent() : null;
                const classLoader = content ? content.getComponent().getClass().getClassLoader() : project.getClass().getClassLoader();
                const stateServiceClass = java.lang.Class.forName("com.github.uiopak.lstcrc.services.ToolWindowStateService", true, classLoader);
                const stateService = project.getService(stateServiceClass);
                stateService.updateTabAlias(branchName, newAlias);
                """.trimIndent(),
                true
            )
        }
    }

    fun invokeSetRevisionAsRepoComparisonAction(revision: String) {
        step("Invoke SetRevisionAsRepoComparisonAction for $revision") {
            runJs(
                """
                const revision = ${toJsStringLiteral(revision)};
                const frameHelper = com.intellij.openapi.wm.impl.ProjectFrameHelper.getFrameHelper(component);
                const project = frameHelper ? frameHelper.getProject() : null;
                if (!project) {
                    throw new java.lang.IllegalStateException("No open project available for SetRevisionAsRepoComparisonAction");
                }

                const toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("GitChangesView");
                if (!toolWindow) {
                    throw new java.lang.IllegalStateException("GitChangesView tool window is not available");
                }

                const content = toolWindow.getContentManager().getSelectedContent();
                if (!content) {
                    throw new java.lang.IllegalStateException("No selected LST-CRC tab content available for repo comparison update");
                }

                const displayName = content.getDisplayName ? String(content.getDisplayName()) : "";
                const classLoader = content.getComponent().getClass().getClassLoader();
                const repoRootPath = project.getBasePath();
                if (!repoRootPath) {
                    throw new java.lang.IllegalStateException("Project base path is not available for repo comparison update");
                }

                const stateServiceClass = java.lang.Class.forName("com.github.uiopak.lstcrc.services.ToolWindowStateService", true, classLoader);
                const stateService = project.getService(stateServiceClass);
                let selectedTabInfo = stateService ? stateService.getSelectedTabInfo() : null;
                const state = stateService ? stateService.getState() : null;
                const openTabs = state ? state.getOpenTabs() : null;

                if (openTabs && displayName.length > 0) {
                    const iterator = openTabs.iterator();
                    while (iterator.hasNext()) {
                        const candidate = iterator.next();
                        if (candidate.getBranchName() === displayName || candidate.getAlias() === displayName) {
                            selectedTabInfo = candidate;
                            break;
                        }
                    }
                }

                if (!selectedTabInfo) {
                    throw new java.lang.IllegalStateException("No selected LST-CRC tab available for repo comparison update");
                }

                const comparisonMap = new java.util.HashMap(selectedTabInfo.getComparisonMap());
                comparisonMap.put(repoRootPath, revision);
                stateService.updateTabComparisonMap(selectedTabInfo.getBranchName(), comparisonMap, true);
                """.trimIndent(),
                true
            )
        }
    }

    fun visualGutterSummaryForSelectedEditor(): String {
        return step("Read visual gutter summary for selected editor") {
            callJs<String>(
                """
                (function() {
                    const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
                    if (!project) return "";

                    const editor = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).getSelectedTextEditor();
                    if (!editor) return "";

                    const document = editor.getDocument();
                    const highlighters = editor.getMarkupModel().getAllHighlighters();
                    const highlighterParts = [];
                    let gutterHighlighterCount = 0;

                    for (let i = 0; i < highlighters.length; i++) {
                        const highlighter = highlighters[i];
                        const renderer = highlighter.getGutterIconRenderer();
                        if (!renderer) continue;

                        gutterHighlighterCount++;
                        const startOffset = highlighter.getStartOffset();
                        const endOffsetExclusive = Math.max(highlighter.getEndOffset(), startOffset + 1);
                        const startLine = document.getLineNumber(startOffset);
                        const endLine = document.getLineNumber(endOffsetExclusive - 1) + 1;
                        highlighterParts.push(startLine + "-" + endLine + ":" + renderer.getClass().getSimpleName());
                    }

                    const tracker = com.intellij.openapi.vcs.impl.LineStatusTrackerManager.getInstance(project).getLineStatusTracker(document);
                    let trackerSummary = "tracker=none";
                    if (tracker) {
                        const ranges = tracker.getRanges();
                        const rangeParts = [];
                        if (ranges != null) {
                            for (let i = 0; i < ranges.size(); i++) {
                                const range = ranges.get(i);
                                let typeName = "UNKNOWN";
                                const type = range.getType();
                                if (type === com.intellij.openapi.vcs.ex.Range.MODIFIED) typeName = "MODIFIED";
                                if (type === com.intellij.openapi.vcs.ex.Range.INSERTED) typeName = "INSERTED";
                                if (type === com.intellij.openapi.vcs.ex.Range.DELETED) typeName = "DELETED";
                                rangeParts.push(range.getLine1() + "-" + range.getLine2() + ":" + typeName);
                            }
                        }

                        const mode = tracker.getMode ? tracker.getMode() : null;
                        const visible = mode && mode.isVisible ? mode.isVisible() : "n/a";
                        trackerSummary = tracker.getClass().getSimpleName() + "|visible=" + visible + "|ranges=" + rangeParts.join(",");
                    }

                    return highlighterParts.join(",") + "|highlighters=" + gutterHighlighterCount + "|" + trackerSummary;
                })();
                """.trimIndent(),
                true
            )
        }
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
}
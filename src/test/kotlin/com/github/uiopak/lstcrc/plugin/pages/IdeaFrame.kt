// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.github.uiopak.lstcrc.plugin.pages

import com.github.uiopak.lstcrc.toolWindow.LstCrcSettingsService
import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.*
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.waitFor
import java.time.Duration

fun RemoteRobot.idea(function: IdeaFrame.() -> Unit) {
    val timeout = if (System.getenv("GITHUB_ACTIONS") == "true") Duration.ofSeconds(60) else Duration.ofSeconds(20)
    find<IdeaFrame>(timeout = timeout).apply(function)
}

@FixtureName("Idea frame")
@DefaultXpath("IdeFrameImpl type", "//div[@class='IdeFrameImpl']")
class IdeaFrame(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) :
    CommonContainerFixture(remoteRobot, remoteComponent) {

    companion object {
        private val CI_SMART_MODE_TIMEOUT: Duration = Duration.ofMinutes(10)
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
        private const val SHOW_LINE_STATS_IN_TREE_KEY = "com.github.uiopak.lstcrc.app.showLineStatsInTree"
        private const val EXPAND_NEW_FILES_IN_COLLAPSED_DIRS_KEY = "com.github.uiopak.lstcrc.app.expandNewFilesInCollapsedDirs"
        private const val SHOW_UNTRACKED_FILES_AS_NEW_KEY = "com.github.uiopak.lstcrc.app.showUntrackedFilesAsNew"
    }


    @JvmOverloads
    fun dumbAware(timeout: Duration = Duration.ofMinutes(5), function: () -> Unit) {
        val effectiveTimeout = if (System.getenv("GITHUB_ACTIONS") == "true" && timeout < CI_SMART_MODE_TIMEOUT) {
            CI_SMART_MODE_TIMEOUT
        } else {
            timeout
        }

        step("Wait for smart mode") {
            waitFor(duration = effectiveTimeout, interval = Duration.ofSeconds(5)) {
                runCatching { isDumbMode().not() }.getOrDefault(false)
            }
            function()
            step("..wait for smart mode again") {
                waitFor(duration = effectiveTimeout, interval = Duration.ofSeconds(5)) {
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

    fun waitForFocusedTextInput(timeout: Duration = Duration.ofSeconds(10)) {
        waitFor(timeout, interval = Duration.ofMillis(250)) {
            callJs(
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

    fun openFile(path: String) {
        val normalizedPath = path.replace('\\', '/')
        runJs(
            """
            importPackage(com.intellij.openapi.fileEditor)
            importPackage(com.intellij.openapi.vfs)
            importPackage(com.intellij.openapi.wm.impl)
            importClass(com.intellij.openapi.application.ApplicationManager)

            const relativePath = '$normalizedPath'
            const frameHelper = ProjectFrameHelper.getFrameHelper(component)
            if (frameHelper) {
                const project = frameHelper.getProject()
                const projectPath = project.getBasePath()
                const normalizedProjectPath = String(projectPath).split('\\').join('/')
                const absolutePath = normalizedProjectPath + '/' + relativePath
                const file = LocalFileSystem.getInstance().refreshAndFindFileByPath(absolutePath)
                if (file) {
                    const openFileFunction = new Runnable({
                        run: function() {
                            FileEditorManager.getInstance(project).openTextEditor(
                                new OpenFileDescriptor(project, file),
                                true
                            )
                        }
                    })
                    ApplicationManager.getApplication().invokeAndWait(openFileFunction)
                }
            }
        """, true
        )

        waitFor(Duration.ofSeconds(10), interval = Duration.ofMillis(250)) {
            runCatching {
                callJs<Boolean>(
                    """
                    (function() {
                        const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
                        if (!project) return false;

                        const editor = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).getSelectedTextEditor();
                        const file = editor ? editor.getVirtualFile() : null;
                        return file != null && String(file.getPath()).endsWith('/$normalizedPath');
                    })();
                    """.trimIndent(),
                    true
                )
            }.getOrDefault(false)
        }

        runJs(
            """
            const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
            if (project) {
                const pluginId = com.intellij.openapi.extensions.PluginId.getId("com.github.uiopak.lstcrc");
                const plugin = com.intellij.ide.plugins.PluginManagerCore.getPlugin(pluginId);
                if (plugin != null) {
                    const managerClass = plugin.getPluginClassLoader()
                        .loadClass("com.github.uiopak.lstcrc.gutters.VisualTrackerManager");
                    const manager = project.getService(managerClass);
                    if (manager != null) {
                        manager.settingsChanged();
                    }
                }
            }
            """.trimIndent(),
            true
        )
    }

    fun openGitChangesView() {
        step("Open GitChangesView tool window") {
            val visibleTimeout = if (System.getenv("GITHUB_ACTIONS") == "true") Duration.ofSeconds(60) else Duration.ofSeconds(10)
            val readyTimeout = if (System.getenv("GITHUB_ACTIONS") == "true") Duration.ofMinutes(3) else Duration.ofSeconds(30)

            runJs(
                """
                const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
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
                    const event = com.intellij.openapi.actionSystem.AnActionEvent.createEvent(
                        gitChangesViewAction,
                        dataContext,
                        null,
                        "test",
                        com.intellij.openapi.actionSystem.ActionUiKind.NONE,
                        null
                    );
                    gitChangesViewAction.actionPerformed(event);
                }
            """, true
            )

            val toolWindowVisible = runCatching {
                waitFor(visibleTimeout, interval = Duration.ofMillis(250)) {
                    isGitChangesViewReadyForRobot()
                }
                true
            }.getOrDefault(false)

            if (!toolWindowVisible) {
                runCatching {
                    remoteRobot.find<ComponentFixture>(
                        byXpath("//div[@class='TextPresentationComponent' and contains(@tooltiptext, 'LST-CRC')]"),
                        Duration.ofSeconds(5)
                    ).click()
                }
                waitFor(visibleTimeout, interval = Duration.ofMillis(250)) {
                    isGitChangesViewReadyForRobot()
                }
            }

            waitFor(readyTimeout, interval = Duration.ofMillis(500)) {
                val placeholderVisible = remoteRobot.findAll<ComponentFixture>(
                    byXpath("//div[contains(@visible_text,'until indexes are built')]")
                ).isNotEmpty()
                !isDumbMode() && !placeholderVisible
            }
        }
    }

    private fun isGitChangesViewReadyForRobot(): Boolean {
        val jsVisible = runCatching {
            callJs<Boolean>(
                """
                (function() {
                    const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
                    if (!project) return false;

                    const toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("GitChangesView");
                    if (!toolWindow || !toolWindow.isVisible()) return false;

                    const contentManager = toolWindow.getContentManager();
                    if (!contentManager) return false;

                    const selected = contentManager.getSelectedContent();
                    const component = selected ? selected.getComponent() : null;
                    return component != null && component.isShowing();
                })();
                """.trimIndent(),
                true
            )
        }.getOrDefault(false)

        if (jsVisible) return true

        return runCatching {
            remoteRobot.findAll<ComponentFixture>(byXpath("//div[@class='LstCrcChangesBrowser']")).isNotEmpty()
        }.getOrDefault(false)
    }

    fun resetGitChangesViewState() {
        step("Reset GitChangesView state") {
            runJs(
                """
                const frameHelper = com.intellij.openapi.wm.impl.ProjectFrameHelper.getFrameHelper(component);
                const project = frameHelper ? frameHelper.getProject() : null;
                if (project) {
                    const pluginId = com.intellij.openapi.extensions.PluginId.getId("com.github.uiopak.lstcrc");
                    const plugin = com.intellij.ide.plugins.PluginManagerCore.getPlugin(pluginId);
                    const cl = plugin ? plugin.getPluginClassLoader() : null;
                    if (cl) {
                        try {
                            const settingsClass = cl.loadClass("com.github.uiopak.lstcrc.toolWindow.LstCrcSettingsService");
                            const svc = com.intellij.openapi.application.ApplicationManager.getApplication().getService(settingsClass);
                            if (svc) {
                                svc.resetToDefaults();
                            }

                            const toolWindowStateClass = cl.loadClass("com.github.uiopak.lstcrc.state.ToolWindowState");
                            const toolWindowStateServiceClass = cl.loadClass("com.github.uiopak.lstcrc.services.ToolWindowStateService");
                            const stateService = project.getService(toolWindowStateServiceClass);
                            if (stateService) {
                                stateService.loadState(toolWindowStateClass.getDeclaredConstructor().newInstance());
                            }

                            const diffDataServiceClass = cl.loadClass("com.github.uiopak.lstcrc.services.ProjectActiveDiffDataService");
                            const diffDataService = project.getService(diffDataServiceClass);
                            if (diffDataService) {
                                diffDataService.clearActiveDiff();
                            }
                        } catch(e) {}
                    }

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
            check(
                singleClickAction != null ||
                    doubleClickAction != null ||
                    middleClickAction != null ||
                    doubleMiddleClickAction != null ||
                    rightClickAction != null ||
                    doubleRightClickAction != null ||
                    showContextMenu != null
            ) { "At least one click action setting must be provided" }

            singleClickAction?.let { setPluginStringSetting(SINGLE_CLICK_ACTION_KEY, it, "setSingleClickAction") }
            doubleClickAction?.let { setPluginStringSetting(DOUBLE_CLICK_ACTION_KEY, it, "setDoubleClickAction") }
            middleClickAction?.let { setPluginStringSetting(MIDDLE_CLICK_ACTION_KEY, it, "setMiddleClickAction") }
            doubleMiddleClickAction?.let { setPluginStringSetting(DOUBLE_MIDDLE_CLICK_ACTION_KEY, it, "setDoubleMiddleClickAction") }
            rightClickAction?.let { setPluginStringSetting(RIGHT_CLICK_ACTION_KEY, it, "setRightClickAction") }
            doubleRightClickAction?.let { setPluginStringSetting(DOUBLE_RIGHT_CLICK_ACTION_KEY, it, "setDoubleRightClickAction") }
            showContextMenu?.let { setPluginBooleanSetting(SHOW_CONTEXT_MENU_KEY, it, false) }
        }
    }

    fun clickSettingsSnapshot(): String {
        return step("Read LST-CRC click settings") {
            callJs(
                """
                (function() {
                    const pluginId = com.intellij.openapi.extensions.PluginId.getId("com.github.uiopak.lstcrc");
                    const plugin = com.intellij.ide.plugins.PluginManagerCore.getPlugin(pluginId);
                    const cl = plugin ? plugin.getPluginClassLoader() : null;
                    if (!cl) return "";

                    let service = null;
                    try {
                        const settingsClass = cl.loadClass("com.github.uiopak.lstcrc.toolWindow.LstCrcSettingsService");
                        service = com.intellij.openapi.application.ApplicationManager.getApplication().getService(settingsClass);
                    } catch(e) {}

                    const properties = com.intellij.ide.util.PropertiesComponent.getInstance();
                    const values = [
                        service ? service.getSingleClickAction() : properties.getValue('$SINGLE_CLICK_ACTION_KEY', ''),
                        service ? service.getDoubleClickAction() : properties.getValue('$DOUBLE_CLICK_ACTION_KEY', ''),
                        service ? service.getMiddleClickAction() : properties.getValue('$MIDDLE_CLICK_ACTION_KEY', ''),
                        service ? service.getDoubleMiddleClickAction() : properties.getValue('$DOUBLE_MIDDLE_CLICK_ACTION_KEY', ''),
                        service ? service.getRightClickAction() : properties.getValue('$RIGHT_CLICK_ACTION_KEY', ''),
                        service ? service.getDoubleRightClickAction() : properties.getValue('$DOUBLE_RIGHT_CLICK_ACTION_KEY', ''),
                        service ? String(service.getBoolean('$SHOW_CONTEXT_MENU_KEY', false)) : String(properties.getBoolean('$SHOW_CONTEXT_MENU_KEY', false)),
                        service ? String(service.getInt('$DOUBLE_CLICK_DELAY_KEY', -1)) : properties.getValue('$DOUBLE_CLICK_DELAY_KEY', '')
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
            setPluginIntSetting(DOUBLE_CLICK_DELAY_KEY, delay, -1)
        }
    }

    fun selectedEditorDescriptor(): String {
        return step("Read selected editor descriptor") {
            callJs(
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


    fun hasDiffEditorOpen(): Boolean {
        return step("Check whether a diff editor is open") {
            callJs(
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

    fun diffEditorCount(): Int {
        return step("Count open diff editors") {
            callJs(
                """
                (function() {
                    const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
                    const manager = project ? com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project) : null;
                    const diffEditorCount = manager
                        ? manager.getAllEditors().filter(editor => editor.getClass().getName().toLowerCase().includes("diff")).length
                        : 0;
                    const diffFileCount = manager
                        ? manager.getOpenFiles().filter(file => {
                            const className = file.getClass().getName().toLowerCase();
                            const fileType = file.getFileType();
                            const fileTypeName = fileType ? fileType.getName().toLowerCase() : "";
                            return className.includes("diff") || fileTypeName.includes("diff");
                        }).length
                        : 0;
                    const diffWindowCount = java.awt.Window.getWindows()
                        .filter(window => window.isShowing() && window.getClass().getName().toLowerCase().includes("diff"))
                        .length;
                    return Math.max(diffEditorCount, diffFileCount, diffWindowCount);
                })();
                """.trimIndent(),
                true
            )
        }
    }



    fun statusWidgetText(): String {
        return step("Read LST-CRC widget text") {
            callJs(
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
                byXpath("LST-CRC status widget", "//div[@class='TextPresentationComponent' and contains(@tooltiptext, 'LST-CRC')]"),
                Duration.ofSeconds(10)
            )
                .click()
        }
    }

    fun statusWidgetPopupSnapshot(): String {
        return step("Read LST-CRC widget popup snapshot") {
            val widgetAndStatusBar = callJs<String>(
                """
                (function() {
                    const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
                    if (!project) return "";

                    function safeText(component) {
                        try {
                            if (component.getText) {
                                const text = component.getText();
                                return text == null ? "" : String(text);
                            }
                        } catch (e) {}
                        return "";
                    }

                    function safeTooltip(component) {
                        try {
                            if (component.getToolTipText) {
                                const text = component.getToolTipText();
                                return text == null ? "" : String(text);
                            }
                        } catch (e) {}
                        return "";
                    }

                    function boundsString(component) {
                        if (!component || !component.isShowing || !component.isShowing()) return "";
                        try {
                            const location = component.getLocationOnScreen();
                            return [location.x, location.y, component.getWidth(), component.getHeight()].join(",");
                        } catch (e) {
                            return "";
                        }
                    }

                    const statusBar = com.intellij.openapi.wm.WindowManager.getInstance().getStatusBar(project);
                    if (!statusBar) return "";
                    const widget = statusBar.getWidget("LstCrcStatusWidget");
                    if (!widget) return "";

                    const presentation = widget.getPresentation ? widget.getPresentation() : widget;
                    const widgetText = presentation && presentation.getText ? String(presentation.getText()) : "";
                    const tooltipText = presentation && presentation.getTooltipText ? String(presentation.getTooltipText()) : "";
                    const statusBarComponent = statusBar.getComponent ? statusBar.getComponent() : null;

                    let widgetComponent = statusBarComponent;
                    if (statusBarComponent && statusBarComponent.isShowing && statusBarComponent.isShowing()) {
                        const probeY = Math.max(1, Math.floor(Math.max(statusBarComponent.getHeight(), 2) / 2));
                        const step = Math.max(8, Math.floor(Math.max(statusBarComponent.getWidth(), 20) / 25));
                        let bestScore = -1;
                        for (let x = Math.max(1, statusBarComponent.getWidth() - 2); x >= 1; x -= step) {
                            var widgetProbeCandidate = javax.swing.SwingUtilities.getDeepestComponentAt(statusBarComponent, x, probeY);
                            if (!widgetProbeCandidate || !widgetProbeCandidate.isShowing || !widgetProbeCandidate.isShowing()) continue;
                            const text = safeText(widgetProbeCandidate);
                            const tooltip = safeTooltip(widgetProbeCandidate);
                            let score = 0;
                            if (widgetText.length > 0 && text === widgetText) score += 100;
                            else if (widgetText.length > 0 && text.indexOf(widgetText) >= 0) score += 50;
                            if (tooltipText.length > 0 && tooltip === tooltipText) score += 25;
                            try {
                                score += Math.max(0, widgetProbeCandidate.getLocationOnScreen().x / 100);
                            } catch (e) {}
                            if (score > bestScore) {
                                bestScore = score;
                                widgetComponent = widgetProbeCandidate;
                            }
                        }
                    }

                    return [
                        "widget=" + boundsString(widgetComponent),
                        "statusBar=" + boundsString(statusBarComponent)
                    ].join("|");
                })();
                """.trimIndent(),
                true
            )

            val popupBounds = findAll<ComponentFixture>(
                byXpath("LST-CRC widget popup", "//div[@class='MyList' and contains(@accessiblename, 'LST-CRC Actions')]")
            ).firstOrNull()?.callJs<String>(
                """
                (function() {
                    if (!component || !component.isShowing || !component.isShowing()) return "";
                    const location = component.getLocationOnScreen();
                    return [location.x, location.y, component.getWidth(), component.getHeight()].join(",");
                })();
                """.trimIndent(),
                true
            ).orEmpty()

            if (widgetAndStatusBar.isBlank()) {
                "popup=$popupBounds"
            } else {
                "$widgetAndStatusBar|popup=$popupBounds"
            }
        }
    }

    fun setShowWidgetContext(show: Boolean) {
        step("Set widget context prefix to $show") {
            setPluginBooleanSetting(SHOW_WIDGET_CONTEXT_KEY, show, false)
        }
    }

    fun setShowToolWindowTitle(show: Boolean) {
        step("Set tool window title visibility to $show") {
            setPluginBooleanSetting(SHOW_TOOL_WINDOW_TITLE_KEY, show, false)
            runJs(
                """
                (function() {
                    const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
                    if (!project) return;

                    const toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("GitChangesView");
                    if (!toolWindow) return;

                    const compatibility = com.github.uiopak.lstcrc.toolWindow.ToolWindowUiCompatibility.INSTANCE;
                    compatibility.setToolWindowTitleVisible(toolWindow, ${if (show) "true" else "false"});
                })();
                """.trimIndent(),
                true
            )
        }
    }

    fun isToolWindowTitleVisible(): Boolean {
        return step("Read tool window title visibility") {
            callJs(
                """
                (function() {
                    const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
                    if (!project) return false;

                    const toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("GitChangesView");
                    if (!toolWindow) return false;

                    const compatibility = com.github.uiopak.lstcrc.toolWindow.ToolWindowUiCompatibility.INSTANCE;
                    return compatibility.isToolWindowTitleVisible(toolWindow);
                })();
                """.trimIndent(),
                true
            )
        }
    }

    fun setIncludeHeadInScopes(include: Boolean) {
        step("Set include HEAD in scopes to $include") {
            setPluginBooleanSetting(INCLUDE_HEAD_IN_SCOPES_KEY, include, false)
            runJs(
                """
                (function() {
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
            enableMarkers?.let { setPluginBooleanSetting(ENABLE_GUTTER_MARKERS_KEY, it, true) }
            enableForNewFiles?.let { setPluginBooleanSetting(ENABLE_GUTTER_FOR_NEW_FILES_KEY, it, false) }
        }
    }

    fun setTreeContextSettings(showSingleRepo: Boolean? = null, showCommits: Boolean? = null, showLineStats: Boolean? = null) {
        step("Update tree context settings") {
            check(showSingleRepo != null || showCommits != null || showLineStats != null) { "At least one tree context setting must be provided" }
            showSingleRepo?.let { setPluginBooleanSetting(SHOW_CONTEXT_SINGLE_REPO_KEY, it, true) }
            showCommits?.let { setPluginBooleanSetting(SHOW_CONTEXT_FOR_COMMITS_KEY, it, false) }
            showLineStats?.let { setPluginBooleanSetting(SHOW_LINE_STATS_IN_TREE_KEY, it, false) }
            runJs(
                """
                (function() {
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

    fun setExpandNewFilesInCollapsedDirs(enabled: Boolean) {
        step("Set expand new files in collapsed dirs to $enabled") {
            setPluginBooleanSetting(EXPAND_NEW_FILES_IN_COLLAPSED_DIRS_KEY, enabled, true)
        }
    }

    fun setShowUntrackedFilesAsNew(enabled: Boolean) {
        step("Set show untracked files as new to $enabled") {
            setPluginBooleanSetting(SHOW_UNTRACKED_FILES_AS_NEW_KEY, enabled, false)
            runJs(
                """
                (function() {
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

    fun treeContextSettingsSnapshot(): String {
        return step("Read tree context settings") {
            callJs(
                """
                (function() {
                    const properties = com.intellij.ide.util.PropertiesComponent.getInstance();
                    return String(properties.getBoolean('$SHOW_CONTEXT_SINGLE_REPO_KEY', true)) + "|" +
                        String(properties.getBoolean('$SHOW_CONTEXT_FOR_COMMITS_KEY', false)) + "|" +
                        String(properties.getBoolean('$SHOW_LINE_STATS_IN_TREE_KEY', false));
                })();
                """.trimIndent(),
                true
            )
        }
    }

    fun gutterSettingsSnapshot(): String {
        return step("Read gutter settings") {
            callJs(
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


    fun activeDiffSnapshot(): String {
        return step("Read active diff snapshot") {
            callJs(
                """
                (function() {
                    const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
                    if (!project) return "";

                    const pluginId = com.intellij.openapi.extensions.PluginId.getId("com.github.uiopak.lstcrc");
                    const plugin = com.intellij.ide.plugins.PluginManagerCore.getPlugin(pluginId);
                    if (!plugin) return "plugin=missing";

                    const classLoader = plugin.getPluginClassLoader();
                    const serviceClass = classLoader.loadClass("com.github.uiopak.lstcrc.services.ProjectActiveDiffDataService");
                    const diffDataService = project.getService(serviceClass);
                    if (!diffDataService) return "diff=missing";

                    const compCtx = diffDataService.getActiveComparisonContext ? diffDataService.getActiveComparisonContext() : null;
                    const ctxValues = [];
                    if (compCtx) {
                        const ctxIt = compCtx.values().iterator();
                        while (ctxIt.hasNext()) { ctxValues.push(String(ctxIt.next())); }
                    }
                    function namesFromPaths(paths) {
                        if (!paths) return "";
                        const items = [];
                        const it = paths.iterator();
                        while (it.hasNext()) { const p = String(it.next()); items.push(p); }
                        items.sort();
                        return items.join(",");
                    }
                    return [
                        "branch=" + String(diffDataService.getActiveBranchName() || ""),
                        "ctx=" + ctxValues.sort().join(","),
                        "created=" + namesFromPaths(diffDataService.getCreatedFilePaths()),
                        "modified=" + namesFromPaths(diffDataService.getModifiedFilePaths()),
                        "moved=" + namesFromPaths(diffDataService.getMovedFilePaths()),
                        "deleted=" + namesFromPaths(diffDataService.getDeletedFilePaths())
                    ].join("|");
                })();
                """.trimIndent(),
                true
            )
        }
    }

    fun selectedChangesTreeContains(text: String): Boolean {
        return findAll<ComponentFixture>(
            byXpath("LstCrcAsyncChangesTree with '$text'", "//div[@class='LstCrcAsyncChangesTree' and contains(@visible_text,'$text')]")
        ).isNotEmpty()
    }

    fun selectedChangesTreeItemMetadata(fileName: String): String {
        return callJs(
            """
            (function() {
                var result = new java.util.concurrent.atomic.AtomicReference("");
                var targetFileName = ${toJsStringLiteral(fileName)};

                function findTree() {
                    var windows = java.awt.Window.getWindows();
                    for (var w = 0; w < windows.length; w++) {
                        var queue = new java.util.LinkedList();
                        queue.add(windows[w]);
                        while (!queue.isEmpty()) {
                            var component = queue.poll();
                            if (component && component.getClass().getName().endsWith("LstCrcAsyncChangesTree") && component.isShowing()) {
                                return component;
                            }
                            if (!component) continue;
                            try {
                                var children = component.getComponents();
                                if (children) {
                                    for (var ci = 0; ci < children.length; ci++) {
                                        queue.add(children[ci]);
                                    }
                                }
                            } catch (ignored) {}
                        }
                    }
                    return null;
                }

                function findDeclaredField(instance, fieldName) {
                    var cls = instance.getClass();
                    while (cls) {
                        try {
                            var field = cls.getDeclaredField(fieldName);
                            field.setAccessible(true);
                            return field;
                        } catch (ignored) {
                            cls = cls.getSuperclass();
                        }
                    }
                    return null;
                }

                function fragmentText(component) {
                    if (!component) return "";
                    var fragmentsField = findDeclaredField(component, "myFragments");
                    if (!fragmentsField) return "";
                    var fragments = fragmentsField.get(component);
                    if (!fragments) return "";

                    var values = [];
                    var iterator = fragments.iterator();
                    while (iterator.hasNext()) {
                        var fragment = iterator.next();
                        var textField = findDeclaredField(fragment, "myText") || findDeclaredField(fragment, "text");
                        if (textField) {
                            values.push(String(textField.get(fragment)));
                        }
                    }
                    return values.join("");
                }

                com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait(new java.lang.Runnable({
                    run: function() {
                        var tree = findTree();
                        if (!tree) {
                            result.set("");
                            return;
                        }
                        var renderer = tree.getCellRenderer();
                        if (!renderer) {
                            result.set("");
                            return;
                        }

                        for (var row = 0; row < tree.getRowCount(); row++) {
                            var path = tree.getPathForRow(row);
                            if (!path) continue;
                            var node = path.getLastPathComponent();
                            if (!node) continue;
                            var userObject = node.getUserObject ? node.getUserObject() : null;
                            var change = userObject instanceof com.intellij.openapi.vcs.changes.Change ? userObject : null;
                            if (!change) continue;

                            var candidate = change.getAfterRevision() ? change.getAfterRevision().getFile().getName() : null;
                            if (!candidate && change.getBeforeRevision()) {
                                candidate = change.getBeforeRevision().getFile().getName();
                            }
                            if (String(candidate || "") !== targetFileName) continue;

                            renderer.getTreeCellRendererComponent(tree, node, false, tree.isExpanded(row), tree.getModel().isLeaf(node), row, false);
                            var textRendererField = findDeclaredField(renderer, "textRenderer");
                            var trailingRendererField = findDeclaredField(renderer, "trailingRenderer");
                            result.set([
                                textRendererField ? fragmentText(textRendererField.get(renderer)) : "",
                                trailingRendererField ? fragmentText(trailingRendererField.get(renderer)) : ""
                            ].join(" "));
                            return;
                        }

                        result.set("");
                    }
                }));

                return result.get();
            })();
            """.trimIndent(),
            true
        )
    }

    fun selectedChangesTreeRenderedTextSnapshot(): String {
        return callJs(
            """
            (function() {
                var result = new java.util.concurrent.atomic.AtomicReference("");

                function findTree() {
                    var windows = java.awt.Window.getWindows();
                    for (var w = 0; w < windows.length; w++) {
                        var queue = new java.util.LinkedList();
                        queue.add(windows[w]);
                        while (!queue.isEmpty()) {
                            var component = queue.poll();
                            if (component && component.getClass().getName().endsWith("LstCrcAsyncChangesTree") && component.isShowing()) {
                                return component;
                            }
                            if (!component) continue;
                            try {
                                var children = component.getComponents();
                                if (children) {
                                    for (var ci = 0; ci < children.length; ci++) {
                                        queue.add(children[ci]);
                                    }
                                }
                            } catch (ignored) {}
                        }
                    }
                    return null;
                }

                function findDeclaredField(instance, fieldName) {
                    var cls = instance.getClass();
                    while (cls) {
                        try {
                            var field = cls.getDeclaredField(fieldName);
                            field.setAccessible(true);
                            return field;
                        } catch (ignored) {
                            cls = cls.getSuperclass();
                        }
                    }
                    return null;
                }

                function fragmentText(component) {
                    if (!component) return "";
                    var fragmentsField = findDeclaredField(component, "myFragments");
                    if (!fragmentsField) return "";
                    var fragments = fragmentsField.get(component);
                    if (!fragments) return "";

                    var values = [];
                    var iterator = fragments.iterator();
                    while (iterator.hasNext()) {
                        var fragment = iterator.next();
                        var textField = findDeclaredField(fragment, "myText") || findDeclaredField(fragment, "text");
                        if (textField) {
                            values.push(String(textField.get(fragment)));
                        }
                    }
                    return values.join("");
                }

                com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait(new java.lang.Runnable({
                    run: function() {
                        var tree = findTree();
                        if (!tree) {
                            result.set("");
                            return;
                        }
                        var renderer = tree.getCellRenderer();
                        if (!renderer) {
                            result.set("");
                            return;
                        }

                        var rows = [];
                        for (var row = 0; row < tree.getRowCount(); row++) {
                            var path = tree.getPathForRow(row);
                            if (!path) continue;
                            var node = path.getLastPathComponent();
                            if (!node) continue;

                            renderer.getTreeCellRendererComponent(tree, node, false, tree.isExpanded(row), tree.getModel().isLeaf(node), row, false);
                            var textRendererField = findDeclaredField(renderer, "textRenderer");
                            var trailingRendererField = findDeclaredField(renderer, "trailingRenderer");
                            var rowText = [
                                textRendererField ? fragmentText(textRendererField.get(renderer)) : "",
                                trailingRendererField ? fragmentText(trailingRendererField.get(renderer)) : ""
                            ].join(" ").trim();
                            if (rowText.length > 0) {
                                rows.push(rowText);
                            }
                        }

                        result.set(rows.join(" || "));
                    }
                }));

                return result.get();
            })();
            """.trimIndent(),
            true
        )
    }

    fun fileStatusForTreeItem(fileName: String): String {
        return step("Read file status for '$fileName' in changes tree") {
            callJs(
                """
                (function() {
                    var result = new java.util.concurrent.atomic.AtomicReference("");
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait(new java.lang.Runnable({
                        run: function() {
                            var tree = null;
                            var windows = java.awt.Window.getWindows();
                            for (var w = 0; w < windows.length && !tree; w++) {
                                var queue = new java.util.LinkedList();
                                queue.add(windows[w]);
                                while (!queue.isEmpty()) {
                                    var c = queue.poll();
                                    if (c && c.getClass().getName().endsWith("LstCrcAsyncChangesTree") && c.isShowing()) {
                                        tree = c;
                                        break;
                                    } else if (c) {
                                        try {
                                            var children = c.getComponents();
                                            if (children) {
                                                for (var ci = 0; ci < children.length; ci++) queue.add(children[ci]);
                                            }
                                        } catch(e) {}
                                    }
                                }
                            }
                            if (!tree) return;
                            for (var row = 0; row < tree.getRowCount(); row++) {
                                var path = tree.getPathForRow(row);
                                if (!path) continue;
                                var node = path.getLastPathComponent();
                                if (!node) continue;
                                var userObj = null;
                                try { userObj = node.getUserObject(); } catch(e) { continue; }
                                if (!(userObj instanceof com.intellij.openapi.vcs.changes.Change)) continue;
                                var afterRev = userObj.getAfterRevision();
                                var beforeRev = userObj.getBeforeRevision();
                                var file = afterRev ? afterRev.getFile() : (beforeRev ? beforeRev.getFile() : null);
                                if (!file) continue;
                                var name = String(file.getName());
                                if (name === ${toJsStringLiteral(fileName)}) {
                                    result.set(String(userObj.getFileStatus().getId()));
                                    break;
                                }
                            }
                        }
                    }));
                    return result.get();
                })()
                """.trimIndent(),
                true
            )
        }
    }

    fun selectedLstCrcTabName(): String {
        return step("Read selected LST-CRC tab") {
            callJs(
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
            callJs(
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

                    const event = com.intellij.openapi.actionSystem.AnActionEvent.createEvent(
                        action,
                        dataContext,
                        null,
                        "test",
                        com.intellij.openapi.actionSystem.ActionUiKind.NONE,
                        null
                    );
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
                const repoRootPath = project.getBasePath() ? String(project.getBasePath()).replace(/\\/g, '/') : null;
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

                stateService.updateTabRepoComparison(selectedTabInfo.getBranchName(), repoRootPath, revision);
                """.trimIndent(),
                true
            )
        }
    }

    fun setBranchAsRepoComparison(branchName: String) {
        step("Set selected tab repo comparison to branch $branchName") {
            runJs(
                """
                const branchName = ${toJsStringLiteral(branchName)};
                const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
                if (!project) {
                    throw new java.lang.IllegalStateException("No open project available for repo comparison update");
                }

                const toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("GitChangesView");
                const content = toolWindow ? toolWindow.getContentManager().getSelectedContent() : null;
                if (!content) {
                    throw new java.lang.IllegalStateException("No selected LST-CRC tab content available for repo comparison update");
                }

                const displayName = content.getDisplayName ? String(content.getDisplayName()) : "";
                const classLoader = content.getComponent().getClass().getClassLoader();
                const stateServiceClass = java.lang.Class.forName("com.github.uiopak.lstcrc.services.ToolWindowStateService", true, classLoader);
                const stateService = project.getService(stateServiceClass);
                let selectedTabInfo = stateService ? stateService.getSelectedTabInfo() : null;
                const state = stateService ? stateService.getState() : null;
                const openTabs = state ? state.getOpenTabs() : null;
                let selectedTabIndex = -1;

                if (openTabs && displayName.length > 0) {
                    let index = 0;
                    const iterator = openTabs.iterator();
                    while (iterator.hasNext()) {
                        const candidate = iterator.next();
                        if (candidate.getBranchName() === displayName || candidate.getAlias() === displayName) {
                            selectedTabInfo = candidate;
                            selectedTabIndex = index;
                            break;
                        }
                        index += 1;
                    }
                }

                if (!selectedTabInfo) {
                    throw new java.lang.IllegalStateException("No selected LST-CRC tab available for repo comparison update");
                }

                if (selectedTabIndex >= 0) {
                    stateService.setSelectedTab(selectedTabIndex);
                    selectedTabInfo = stateService.getSelectedTabInfo();
                }

                // Use ProjectUtil.guessProjectDir().path — the same call used by the Starter bridge — to
                // get a VirtualFile path with forward slashes that exactly matches repo.root.path in GitService.
                const projectDir = com.intellij.openapi.project.ProjectUtil.guessProjectDir(project);
                const repoRootPath = projectDir
                    ? String(projectDir.getPath())
                    : (project.getBasePath() ? String(project.getBasePath()).replace(/\\/g, '/') : null);
                if (!repoRootPath) {
                    throw new java.lang.IllegalStateException("Project base path is not available for repo comparison update");
                }

                stateService.updateTabRepoComparison(selectedTabInfo.getBranchName(), repoRootPath, branchName);
                """.trimIndent(),
                true
            )
        }
    }

    fun visualGutterSummaryForSelectedEditor(): String {
        return step("Read visual gutter summary for selected editor") {
            callJs(
                """
                (function() {
                    const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
                    if (!project) return "";

                    const editor = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).getSelectedTextEditor();
                    if (!editor) return "";

                    const document = editor.getDocument();
                    const markupModel = com.intellij.openapi.editor.impl.DocumentMarkupModel.forDocument(document, project, true);
                    const highlighters = markupModel.getAllHighlighters();
                    const highlighterParts = [];
                    let gutterHighlighterCount = 0;

                    for (let i = 0; i < highlighters.length; i++) {
                        const highlighter = highlighters[i];
                        const iconRenderer = highlighter.getGutterIconRenderer();
                        const lineRenderer = highlighter.getLineMarkerRenderer();
                        const renderer = iconRenderer || lineRenderer;
                        if (!renderer) continue;

                        gutterHighlighterCount++;
                        const startOffset = highlighter.getStartOffset();
                        const endOffsetExclusive = Math.max(highlighter.getEndOffset(), startOffset + 1);
                        const startLine = document.getLineNumber(startOffset);
                        const endLine = document.getLineNumber(endOffsetExclusive - 1) + 1;
                        const rendererClass = renderer.getClass();
                        const rendererName = String(rendererClass.getSimpleName() || rendererClass.getName());
                        highlighterParts.push(startLine + "-" + endLine + ":" + rendererName);
                    }

                    let tracker = com.intellij.openapi.vcs.impl.LineStatusTrackerManager.getInstance(project).getLineStatusTracker(document);
                    if (!tracker) {
                        const pluginId = com.intellij.openapi.extensions.PluginId.getId("com.github.uiopak.lstcrc");
                        const plugin = com.intellij.ide.plugins.PluginManagerCore.getPlugin(pluginId);
                        if (plugin != null) {
                            const managerClass = plugin.getPluginClassLoader()
                                .loadClass("com.github.uiopak.lstcrc.gutters.VisualTrackerManager");
                            const manager = project.getService(managerClass);
                            if (manager != null) {
                                tracker = manager.findStandaloneTracker(document);
                            }
                        }
                    }

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

    /**
     * Sets a boolean plugin setting via [LstCrcSettingsService], bypassing PropertiesComponent.
     * This is needed because [LstCrcSettingsService] maintains its own in-memory cache in
     * [LstCrcSettingsService.SettingsState.values] which takes priority over PropertiesComponent.
     * Direct PropertiesComponent writes from test JS would be ignored if the cache already holds a value.
     */
    private fun setPluginBooleanSetting(key: String, value: Boolean, default: Boolean) {
        val valueStr = if (value) "true" else "false"
        val defaultStr = if (default) "true" else "false"
        runJs(
            """
            (function() {
                const pluginId = com.intellij.openapi.extensions.PluginId.getId("com.github.uiopak.lstcrc");
                const plugin = com.intellij.ide.plugins.PluginManagerCore.getPlugin(pluginId);
                const cl = plugin ? plugin.getPluginClassLoader() : null;
                if (!cl) return;
                try {
                    const settingsClass = cl.loadClass("com.github.uiopak.lstcrc.toolWindow.LstCrcSettingsService");
                    const appService = com.intellij.openapi.application.ApplicationManager.getApplication().getService(settingsClass);
                    if (appService) appService.setBoolean(${toJsStringLiteral(key)}, $valueStr, $defaultStr);
                } catch(e) {}
            })();
            """.trimIndent(),
            true
        )
    }

    private fun setPluginStringSetting(key: String, value: String, setterMethod: String) {
        runJs(
            """
            (function() {
                const pluginId = com.intellij.openapi.extensions.PluginId.getId("com.github.uiopak.lstcrc");
                const plugin = com.intellij.ide.plugins.PluginManagerCore.getPlugin(pluginId);
                const cl = plugin ? plugin.getPluginClassLoader() : null;
                const properties = com.intellij.ide.util.PropertiesComponent.getInstance();
                properties.setValue(${toJsStringLiteral(key)}, ${toJsStringLiteral(value)});
                if (!cl) return;
                try {
                    const settingsClass = cl.loadClass("com.github.uiopak.lstcrc.toolWindow.LstCrcSettingsService");
                    const appService = com.intellij.openapi.application.ApplicationManager.getApplication().getService(settingsClass);
                    const setterName = ${toJsStringLiteral(setterMethod)};
                    if (appService && typeof appService[setterName] === "function") {
                        appService[setterName](${toJsStringLiteral(value)});
                    } else {
                        properties.setValue(${toJsStringLiteral(key)}, ${toJsStringLiteral(value)});
                    }
                } catch(e) {}
            })();
            """.trimIndent(),
            true
        )
    }

    @Suppress("SameParameterValue")
    private fun setPluginIntSetting(key: String, value: Int, default: Int) {
        runJs(
            """
            (function() {
                const pluginId = com.intellij.openapi.extensions.PluginId.getId("com.github.uiopak.lstcrc");
                const plugin = com.intellij.ide.plugins.PluginManagerCore.getPlugin(pluginId);
                const cl = plugin ? plugin.getPluginClassLoader() : null;
                if (!cl) return;
                try {
                    const settingsClass = cl.loadClass("com.github.uiopak.lstcrc.toolWindow.LstCrcSettingsService");
                    const appService = com.intellij.openapi.application.ApplicationManager.getApplication().getService(settingsClass);
                    if (appService) appService.setInt(${toJsStringLiteral(key)}, $value, $default)
                } catch(e) {}
            })();
            """.trimIndent(),
            true
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
}

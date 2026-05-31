// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.github.uiopak.lstcrc.plugin.pages

import com.github.uiopak.lstcrc.toolWindow.LstCrcSettingDefinitions
import com.github.uiopak.lstcrc.toolWindow.LstCrcStatusWidget
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
        private val SINGLE_CLICK_ACTION_KEY = LstCrcSettingDefinitions.SINGLE_CLICK_ACTION.key
        private val DOUBLE_CLICK_ACTION_KEY = LstCrcSettingDefinitions.DOUBLE_CLICK_ACTION.key
        private val MIDDLE_CLICK_ACTION_KEY = LstCrcSettingDefinitions.MIDDLE_CLICK_ACTION.key
        private val DOUBLE_MIDDLE_CLICK_ACTION_KEY = LstCrcSettingDefinitions.DOUBLE_MIDDLE_CLICK_ACTION.key
        private val RIGHT_CLICK_ACTION_KEY = LstCrcSettingDefinitions.RIGHT_CLICK_ACTION.key
        private val DOUBLE_RIGHT_CLICK_ACTION_KEY = LstCrcSettingDefinitions.DOUBLE_RIGHT_CLICK_ACTION.key
        private val SHOW_CONTEXT_MENU_KEY = LstCrcSettingDefinitions.SHOW_CONTEXT_MENU.key
        private val DOUBLE_CLICK_DELAY_KEY = LstCrcSettingDefinitions.USER_DOUBLE_CLICK_DELAY.key
        private val INCLUDE_HEAD_IN_SCOPES_KEY = LstCrcSettingDefinitions.INCLUDE_HEAD_IN_SCOPES.key
        private val ENABLE_GUTTER_MARKERS_KEY = LstCrcSettingDefinitions.ENABLE_GUTTER_MARKERS.key
        private val ENABLE_GUTTER_FOR_NEW_FILES_KEY = LstCrcSettingDefinitions.ENABLE_GUTTER_FOR_NEW_FILES.key
        private val SHOW_TOOL_WINDOW_TITLE_KEY = LstCrcSettingDefinitions.SHOW_TOOL_WINDOW_TITLE.key
        private val SHOW_WIDGET_CONTEXT_KEY = LstCrcSettingDefinitions.SHOW_WIDGET_CONTEXT.key
        private val SHOW_CONTEXT_SINGLE_REPO_KEY = LstCrcSettingDefinitions.SHOW_CONTEXT_SINGLE_REPO.key
        private val SHOW_CONTEXT_FOR_COMMITS_KEY = LstCrcSettingDefinitions.SHOW_CONTEXT_FOR_COMMITS.key
        private val SHOW_LINE_STATS_IN_TREE_KEY = LstCrcSettingDefinitions.SHOW_LINE_STATS_IN_TREE.key
        private val EXPAND_NEW_FILES_IN_COLLAPSED_DIRS_KEY = LstCrcSettingDefinitions.EXPAND_NEW_FILES_IN_COLLAPSED_DIRS.key
        private val SHOW_UNTRACKED_FILES_AS_NEW_KEY = LstCrcSettingDefinitions.SHOW_UNTRACKED_FILES_AS_NEW.key
        private const val STATUS_WIDGET_ID = LstCrcStatusWidget.ID
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
            (function() {
                var frameHelper = com.intellij.openapi.wm.impl.ProjectFrameHelper.getFrameHelper(component);
                if (frameHelper) {
                    var project = frameHelper.getProject();
                    return project ? com.intellij.openapi.project.DumbService.isDumb(project) : true;
                } else { 
                    return true; 
                }
            })();
            """.trimIndent(), true
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
            (function() {
                importPackage(com.intellij.openapi.fileEditor);
                importPackage(com.intellij.openapi.vfs);
                importPackage(com.intellij.openapi.wm.impl);
                importClass(com.intellij.openapi.application.ApplicationManager);

                var relativePath = '$normalizedPath';
                var frameHelper = ProjectFrameHelper.getFrameHelper(component);
                if (frameHelper) {
                    var project = frameHelper.getProject();
                    var projectPath = project.getBasePath();
                    var normalizedProjectPath = String(projectPath).split('\\').join('/');
                    var absolutePath = normalizedProjectPath + '/' + relativePath;
                    var file = LocalFileSystem.getInstance().refreshAndFindFileByPath(absolutePath);
                    if (file) {
                        var openFileFunction = new Runnable({
                            run: function() {
                                FileEditorManager.getInstance(project).openTextEditor(
                                    new OpenFileDescriptor(project, file),
                                    true
                                );
                            }
                        });
                        ApplicationManager.getApplication().invokeAndWait(openFileFunction);
                    }
                }
            })();
        """, true
        )

        waitFor(Duration.ofSeconds(10), interval = Duration.ofMillis(250)) {
            runCatching {
                callJs<Boolean>(
                    """
                    (function() {
                        ${selectedTextEditorLookupStatements()}
                        if (!editor) return false;

                        const file = editor.getVirtualFile();
                        return file != null && String(file.getPath()).endsWith('/$normalizedPath');
                    })();
                    """.trimIndent(),
                    true
                )
            }.getOrDefault(false)
        }

        runJs(
            visualTrackerManagerScript("manager.settingsChanged();"),
            true
        )
    }

    fun openGitChangesView() {
        step("Open GitChangesView tool window") {
            val visibleTimeout = if (System.getenv("GITHUB_ACTIONS") == "true") Duration.ofSeconds(60) else Duration.ofSeconds(10)
            val readyTimeout = if (System.getenv("GITHUB_ACTIONS") == "true") Duration.ofMinutes(3) else Duration.ofSeconds(30)

            runJs(
                """
                (function() {
                    ${openProjectLookupStatements()}
                    if (project) {
                        var toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("GitChangesView");
                        if (toolWindow) {
                            toolWindow.show();
                            toolWindow.activate(null);
                        }
                    }

                    var actionManager = com.intellij.openapi.actionSystem.ActionManager.getInstance();
                    var gitChangesViewAction = actionManager.getAction("ActivateGitChangesViewToolWindow");
                    if (gitChangesViewAction) {
                        var dataContext = com.intellij.ide.DataManager.getInstance().getDataContext();
                        var event = com.intellij.openapi.actionSystem.AnActionEvent.createEvent(
                            gitChangesViewAction,
                            dataContext,
                            null,
                            "test",
                            com.intellij.openapi.actionSystem.ActionUiKind.NONE,
                            null
                        );
                        gitChangesViewAction.actionPerformed(event);
                    }
                })();
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
                    ${toolWindowLookupStatements()}
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
                (function() {
                    var frameHelper = com.intellij.openapi.wm.impl.ProjectFrameHelper.getFrameHelper(component);
                    var project = frameHelper ? frameHelper.getProject() : null;
                    if (project) {
                        ${settingsServiceLookupStatements("svc")}
                        if (cl) {
                            if (svc) {
                                svc.resetToDefaults();
                            }

                            try {
                                var toolWindowStateClass = cl.loadClass("com.github.uiopak.lstcrc.state.ToolWindowState");
                                var toolWindowStateServiceClass = cl.loadClass("com.github.uiopak.lstcrc.services.ToolWindowStateService");
                                var stateService = project.getService(toolWindowStateServiceClass);
                                if (stateService) {
                                    stateService.loadState(toolWindowStateClass.getDeclaredConstructor().newInstance());
                                }

                                var diffDataServiceClass = cl.loadClass("com.github.uiopak.lstcrc.services.ProjectActiveDiffDataService");
                                var diffDataService = project.getService(diffDataServiceClass);
                                if (diffDataService) {
                                    diffDataService.clearActiveDiff();
                                }
                            } catch(e) {}
                        }

                        var toolWindow = project
                            ? com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("GitChangesView")
                            : null;
                        if (toolWindow) {
                            if (cl) {
                                try {
                                    var compatibilityClass = cl.loadClass("com.github.uiopak.lstcrc.toolWindow.ToolWindowUiCompatibility");
                                    var compatibility = compatibilityClass.getField("INSTANCE").get(null);
                                    compatibilityClass.getMethod("setToolWindowTitleVisible", com.intellij.openapi.wm.ToolWindow, java.lang.Boolean.TYPE)
                                        .invoke(compatibility, toolWindow, false);
                                } catch (e) {}
                            }
                            var contentManager = toolWindow.getContentManager();
                            var contents = contentManager.getContents();
                            for (var i = contents.length - 1; i >= 0; i--) {
                                var content = contents[i];
                                if (content.isCloseable()) {
                                    contentManager.removeContent(content, true);
                                }
                            }

                            var remainingContents = contentManager.getContents();
                            if (remainingContents.length > 0) {
                                contentManager.setSelectedContent(remainingContents[0], true);
                            }

                            toolWindow.hide();
                        }
                    }
                })();
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
                    ${settingsServiceLookupStatements("service")}

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
                    ${fileEditorManagerLookupStatements()}
                    if (!manager) return "";
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
                (function() {
                    ${fileEditorManagerLookupStatements()}
                    if (manager) {
                        manager.closeAllFiles();
                    }
                })();
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
                    ${fileEditorManagerLookupStatements()}
                    if (manager) {
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
                    ${fileEditorManagerLookupStatements()}
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
                statusWidgetScript(
                    """
                    const presentation = widget.getPresentation();
                    if (presentation && presentation.getText) {
                        return presentation.getText();
                    }
                    return widget.getText ? widget.getText() : "";
                    """.trimIndent()
                ),
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

    fun selectStatusWidgetPopupItem(itemName: String) {
        step("Select '$itemName' in LST-CRC status widget popup") {
            val popupList = find<ComponentFixture>(
                byXpath("LST-CRC widget popup", "//div[@class='MyList' and contains(@accessiblename, 'LST-CRC Actions')]"),
                Duration.ofSeconds(10)
            )
            popupList.runJs(
                """
                var list = component;
                var model = list.getModel();
                var targetText = ${toJsStringLiteral(itemName)};
                var foundIndex = -1;
                for (var i = 0; i < model.getSize(); i++) {
                    var item = model.getElementAt(i);
                    if (!item) continue;
                    var text = "";
                    if (typeof item.getText === "function") {
                        text = String(item.getText());
                    } else {
                        text = String(item);
                    }
                    if (text === targetText) {
                        foundIndex = i;
                        break;
                    }
                }
                if (foundIndex === -1) {
                    for (var j = 0; j < model.getSize(); j++) {
                        var item2 = model.getElementAt(j);
                        if (!item2) continue;
                        var text2 = "";
                        if (typeof item2.getText === "function") {
                            text2 = String(item2.getText());
                        } else {
                            text2 = String(item2);
                        }
                        if (text2.indexOf(targetText) >= 0) {
                            foundIndex = j;
                            break;
                        }
                    }
                }
                if (foundIndex === -1) {
                    throw new java.lang.IllegalStateException("Could not find popup item with text: " + targetText);
                }
                list.setSelectedIndex(foundIndex);
                var popup = com.intellij.openapi.ui.popup.util.PopupUtil.getPopupContainerFor(list);
                if (!popup) {
                    throw new java.lang.IllegalStateException("Could not find popup container for list");
                }
                popup.handleSelect(true);
                """.trimIndent(),
                true
            )
        }
    }

    fun statusWidgetPopupSnapshot(): String {
        return step("Read LST-CRC widget popup snapshot") {
            val widgetAndStatusBar = callJs<String>(
                statusWidgetScript(
                    """

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
                    """.trimIndent()
                ),
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

    private fun statusWidgetScript(body: String): String =
        """
        (function() {
            ${openProjectLookupStatements()}
            if (!project) return "";

            const statusBar = com.intellij.openapi.wm.WindowManager.getInstance().getStatusBar(project);
            if (!statusBar) return "";

            const widget = statusBar.getWidget("$STATUS_WIDGET_ID");
            if (!widget) return "";

            $body
        })();
        """.trimIndent()

    private fun selectedBrowserScript(body: String): String =
        """
        (function() {
            ${toolWindowLookupStatements()}
            if (!project) return;

            const browser = toolWindow && toolWindow.getContentManager().getSelectedContent()
                ? toolWindow.getContentManager().getSelectedContent().getComponent()
                : null;
            if (!browser) return;

            $body
        })();
        """.trimIndent()

    private fun toolWindowLookupStatements(
        projectVariableName: String = "project",
        toolWindowVariableName: String = "toolWindow"
    ): String =
        """
        ${openProjectLookupStatements(projectVariableName)}
        var $toolWindowVariableName = $projectVariableName
            ? com.intellij.openapi.wm.ToolWindowManager.getInstance($projectVariableName).getToolWindow("GitChangesView")
            : null;
        """.trimIndent()

    private fun fileEditorManagerLookupStatements(
        projectVariableName: String = "project",
        managerVariableName: String = "manager"
    ): String =
        """
        ${openProjectLookupStatements(projectVariableName)}
        var $managerVariableName = $projectVariableName
            ? com.intellij.openapi.fileEditor.FileEditorManager.getInstance($projectVariableName)
            : null;
        """.trimIndent()

    private fun selectedTextEditorLookupStatements(
        projectVariableName: String = "project",
        managerVariableName: String = "manager",
        editorVariableName: String = "editor"
    ): String =
        """
        ${fileEditorManagerLookupStatements(projectVariableName, managerVariableName)}
        var $editorVariableName = $managerVariableName ? $managerVariableName.getSelectedTextEditor() : null;
        """.trimIndent()

    private fun changesTreeLookupFunctionsScript(): String =
        """
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
        """.trimIndent()

    private fun coloredFragmentReflectionFunctionsScript(): String =
        """
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
        """.trimIndent()

    private fun selectedContentStateServiceLookupStatements(
        projectVariableName: String = "project",
        toolWindowVariableName: String = "toolWindow",
        contentVariableName: String = "content",
        classLoaderVariableName: String = "classLoader",
        stateServiceVariableName: String = "stateService"
    ): String =
        """
        ${toolWindowLookupStatements(projectVariableName, toolWindowVariableName)}
        var $contentVariableName = $toolWindowVariableName ? $toolWindowVariableName.getContentManager().getSelectedContent() : null;
        var $classLoaderVariableName = $contentVariableName
            ? $contentVariableName.getComponent().getClass().getClassLoader()
            : $projectVariableName.getClass().getClassLoader();
        var stateServiceClass = java.lang.Class.forName(
            "com.github.uiopak.lstcrc.services.ToolWindowStateService",
            true,
            $classLoaderVariableName
        );
        var $stateServiceVariableName = $projectVariableName.getService(stateServiceClass);
        """.trimIndent()

    private fun visualTrackerManagerScript(body: String): String =
        """
        (function() {
            ${openProjectLookupStatements()}
            if (!project) return "ERROR: project is null";

            ${pluginClassLoaderLookupStatements("classLoader")}
            if (!classLoader) return "ERROR: classLoader is null";

            try {
                var managerClass = classLoader.loadClass("com.github.uiopak.lstcrc.gutters.VisualTrackerManager");
                var manager = project.getService(managerClass);
                if (!manager) return "ERROR: manager is null";
                $body
            } catch (e) {
                return "ERROR: " + e.toString() + "\n" + (e.stack || "");
            }
        })();
        """.trimIndent()

    private fun openProjectLookupStatements(projectVariableName: String = "project"): String =
        """
        var $projectVariableName = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
        """.trimIndent()

    private fun pluginClassLoaderLookupStatements(classLoaderVariableName: String = "cl"): String =
        """
        var pluginId = com.intellij.openapi.extensions.PluginId.getId("com.github.uiopak.lstcrc");
        var plugin = com.intellij.ide.plugins.PluginManagerCore.getPlugin(pluginId);
        var $classLoaderVariableName = plugin ? plugin.getPluginClassLoader() : null;
        """.trimIndent()

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
                    ${toolWindowLookupStatements()}
                    if (!project || !toolWindow) return;

                    ${pluginClassLoaderLookupStatements("cl")}
                    if (cl) {
                        try {
                            const compatibilityClass = cl.loadClass("com.github.uiopak.lstcrc.toolWindow.ToolWindowUiCompatibility");
                            const compatibility = compatibilityClass.getField("INSTANCE").get(null);
                            compatibilityClass.getMethod("setToolWindowTitleVisible", com.intellij.openapi.wm.ToolWindow, java.lang.Boolean.TYPE)
                                .invoke(compatibility, toolWindow, ${if (show) "true" else "false"});
                        } catch (e) {
                            throw new java.lang.IllegalStateException("Failed to set tool window title visibility: " + e.toString());
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
            callJs(
                """
                (function() {
                    ${toolWindowLookupStatements()}
                    if (!project || !toolWindow) return false;

                    ${pluginClassLoaderLookupStatements("cl")}
                    if (cl) {
                        try {
                            const compatibilityClass = cl.loadClass("com.github.uiopak.lstcrc.toolWindow.ToolWindowUiCompatibility");
                            const compatibility = compatibilityClass.getField("INSTANCE").get(null);
                            return compatibilityClass.getMethod("isToolWindowTitleVisible", com.intellij.openapi.wm.ToolWindow)
                                .invoke(compatibility, toolWindow);
                        } catch (e) {
                            throw new java.lang.IllegalStateException("Failed to read tool window title visibility: " + e.toString());
                        }
                    }
                    return false;
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
                selectedBrowserScript(
                    """
                    if (browser.requestRefreshData) {
                        browser.requestRefreshData();
                    }
                    """.trimIndent()
                ),
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
                selectedBrowserScript(
                    """
                    if (browser.rebuildView) {
                        browser.rebuildView();
                    }
                    """.trimIndent()
                ),
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
                selectedBrowserScript(
                    """
                    if (browser.requestRefreshData) {
                        browser.requestRefreshData();
                    }
                    """.trimIndent()
                ),
                true
            )
        }
    }

    fun treeContextSettingsSnapshot(): String {
        return step("Read tree context settings") {
            callJs(
                """
                (function() {
                    ${settingsServiceLookupStatements("service")}
                    const properties = com.intellij.ide.util.PropertiesComponent.getInstance();
                    return String(service ? service.getBoolean('$SHOW_CONTEXT_SINGLE_REPO_KEY', true) : properties.getBoolean('$SHOW_CONTEXT_SINGLE_REPO_KEY', true)) + "|" +
                        String(service ? service.getBoolean('$SHOW_CONTEXT_FOR_COMMITS_KEY', false) : properties.getBoolean('$SHOW_CONTEXT_FOR_COMMITS_KEY', false)) + "|" +
                        String(service ? service.getBoolean('$SHOW_LINE_STATS_IN_TREE_KEY', false) : properties.getBoolean('$SHOW_LINE_STATS_IN_TREE_KEY', false));
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
                    ${settingsServiceLookupStatements("service")}
                    const properties = com.intellij.ide.util.PropertiesComponent.getInstance();
                    return String(service ? service.getBoolean('$ENABLE_GUTTER_MARKERS_KEY', true) : properties.getBoolean('$ENABLE_GUTTER_MARKERS_KEY', true)) + "|" +
                        String(service ? service.getBoolean('$ENABLE_GUTTER_FOR_NEW_FILES_KEY', false) : properties.getBoolean('$ENABLE_GUTTER_FOR_NEW_FILES_KEY', false)) + "|" +
                        String(service ? service.getBoolean('$INCLUDE_HEAD_IN_SCOPES_KEY', false) : properties.getBoolean('$INCLUDE_HEAD_IN_SCOPES_KEY', false));
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
                    ${openProjectLookupStatements()}
                    if (!project) return "";

                    ${pluginClassLoaderLookupStatements("classLoader")}
                    if (!classLoader) return "plugin=missing";

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

                ${changesTreeLookupFunctionsScript()}
                ${coloredFragmentReflectionFunctionsScript()}

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

                ${changesTreeLookupFunctionsScript()}
                ${coloredFragmentReflectionFunctionsScript()}

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
                    ${changesTreeLookupFunctionsScript()}
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait(new java.lang.Runnable({
                        run: function() {
                            var tree = findTree();
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
                    ${toolWindowLookupStatements()}
                    if (!project || !toolWindow) return "";

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
                    ${selectedContentStateServiceLookupStatements()}
                    if (!project || !toolWindow) return "";
                    if (!content) return "";

                    const displayName = content.getDisplayName ? String(content.getDisplayName()) : "";
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
                (function() {
                    var revision = ${toJsStringLiteral(revision)};
                    var alias = ${alias?.let(::toJsStringLiteral) ?: "null"};
                    var frameHelper = com.intellij.openapi.wm.impl.ProjectFrameHelper.getFrameHelper(component);
                    var project = frameHelper ? frameHelper.getProject() : null;
                    if (!project) {
                        throw new java.lang.IllegalStateException("No open project available for CreateTabFromRevisionAction");
                    }

                    var action = com.intellij.openapi.actionSystem.ActionManager.getInstance()
                        .getAction("com.github.uiopak.lstcrc.CreateTabFromRevisionAction");
                    if (!action) {
                        throw new java.lang.IllegalStateException("CreateTabFromRevisionAction is not registered");
                    }

                    var actionClassLoader = action.getClass().getClassLoader();
                    var toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("GitChangesView");
                    if (!toolWindow) {
                        throw new java.lang.IllegalStateException("GitChangesView tool window is not available");
                    }

                    if (alias !== null) {
                        try {
                            var helperClass = java.lang.Class.forName("com.github.uiopak.lstcrc.toolWindow.ToolWindowHelper", true, actionClassLoader);
                            var helperInstance = helperClass.getField("INSTANCE").get(null);
                            var stateServiceClass = java.lang.Class.forName("com.github.uiopak.lstcrc.services.ToolWindowStateService", true, actionClassLoader);
                            var stateService = project.getService(stateServiceClass);

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
                        var gitRevisionNumberClass = java.lang.Class.forName("git4idea.GitRevisionNumber", true, actionClassLoader);
                        var gitRevisionNumberConstructor = null;
                        var constructors = gitRevisionNumberClass.getConstructors();
                        for (var i = 0; i < constructors.length; i++) {
                            if (constructors[i].getParameterCount() === 1) {
                                gitRevisionNumberConstructor = constructors[i];
                                break;
                            }
                        }
                        if (!gitRevisionNumberConstructor) {
                            throw new java.lang.IllegalStateException("Unable to locate GitRevisionNumber(String) constructor");
                        }
                        var gitRevisionNumber = gitRevisionNumberConstructor.newInstance(revision);

                        var dataContext = com.intellij.openapi.actionSystem.impl.SimpleDataContext.builder()
                            .add(com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT, project)
                            .add(com.intellij.openapi.actionSystem.PlatformCoreDataKeys.CONTEXT_COMPONENT, component)
                            .add(
                                com.intellij.openapi.vcs.VcsDataKeys.VCS_REVISION_NUMBERS,
                                java.util.Collections.singletonList(gitRevisionNumber)
                            )
                            .build();

                        var event = com.intellij.openapi.actionSystem.AnActionEvent.createEvent(
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
                })();
                """.trimIndent(),
                true
            )
        }

    }

    fun updateTabAlias(branchName: String, newAlias: String?) {
        step("Update tab alias for $branchName") {
            runJs(
                """
                (function() {
                    var branchName = ${toJsStringLiteral(branchName)};
                    var newAlias = ${newAlias?.let(::toJsStringLiteral) ?: "null"};
                    ${selectedContentStateServiceLookupStatements()}
                    if (!project) {
                        throw new java.lang.IllegalStateException("No open project available for tab alias update");
                    }

                    stateService.updateTabAlias(branchName, newAlias);
                })();
                """.trimIndent(),
                true
            )
        }
    }

    fun invokeSetRevisionAsRepoComparisonAction(revision: String) {
        step("Invoke SetRevisionAsRepoComparisonAction for $revision") {
            runJs(
                """
                (function() {
                    var revision = ${toJsStringLiteral(revision)};
                    var frameHelper = com.intellij.openapi.wm.impl.ProjectFrameHelper.getFrameHelper(component);
                    var project = frameHelper ? frameHelper.getProject() : null;
                    if (!project) {
                        throw new java.lang.IllegalStateException("No open project available for SetRevisionAsRepoComparisonAction");
                    }

                    var toolWindow = project
                        ? com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("GitChangesView")
                        : null;
                    var content = toolWindow ? toolWindow.getContentManager().getSelectedContent() : null;
                    var classLoader = content
                        ? content.getComponent().getClass().getClassLoader()
                        : project.getClass().getClassLoader();
                    var stateServiceClass = java.lang.Class.forName(
                        "com.github.uiopak.lstcrc.services.ToolWindowStateService",
                        true,
                        classLoader
                    );
                    var stateService = project.getService(stateServiceClass);
                    if (!toolWindow) {
                        throw new java.lang.IllegalStateException("GitChangesView tool window is not available");
                    }

                    if (!content) {
                        throw new java.lang.IllegalStateException("No selected LST-CRC tab content available for repo comparison update");
                    }

                    var displayName = content.getDisplayName ? String(content.getDisplayName()) : "";
                    var repoRootPath = project.getBasePath() ? String(project.getBasePath()).replace(/\\/g, '/') : null;
                    if (!repoRootPath) {
                        throw new java.lang.IllegalStateException("Project base path is not available for repo comparison update");
                    }

                    var selectedTabInfo = stateService ? stateService.getSelectedTabInfo() : null;
                    if (!selectedTabInfo && displayName.length > 0 && stateService) {
                        selectedTabInfo = stateService.findTabByDisplayName(displayName);
                    }

                    if (!selectedTabInfo) {
                        throw new java.lang.IllegalStateException("No selected LST-CRC tab available for repo comparison update");
                    }

                    stateService.updateTabRepoComparison(selectedTabInfo.getBranchName(), repoRootPath, revision);
                })();
                """.trimIndent(),
                true
            )
        }
    }

    fun setBranchAsRepoComparison(branchName: String) {
        step("Set selected tab repo comparison to branch $branchName") {
            runJs(
                """
                (function() {
                    var branchName = ${toJsStringLiteral(branchName)};
                    ${selectedContentStateServiceLookupStatements()}
                    if (!project) {
                        throw new java.lang.IllegalStateException("No open project available for repo comparison update");
                    }

                    if (!content) {
                        throw new java.lang.IllegalStateException("No selected LST-CRC tab content available for repo comparison update");
                    }

                    var displayName = content.getDisplayName ? String(content.getDisplayName()) : "";
                    var selectedTabInfo = stateService ? stateService.getSelectedTabInfo() : null;
                    var selectedTabIndex = -1;

                    if (displayName.length > 0 && stateService) {
                        selectedTabIndex = stateService.findTabIndexByDisplayName(displayName);
                        if (!selectedTabInfo) {
                            selectedTabInfo = stateService.findTabByDisplayName(displayName);
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
                    var projectDir = com.intellij.openapi.project.ProjectUtil.guessProjectDir(project);
                    var repoRootPath = projectDir
                        ? String(projectDir.getPath())
                        : (project.getBasePath() ? String(project.getBasePath()).replace(/\\/g, '/') : null);
                    if (!repoRootPath) {
                        throw new java.lang.IllegalStateException("Project base path is not available for repo comparison update");
                    }

                    stateService.updateTabRepoComparison(selectedTabInfo.getBranchName(), repoRootPath, branchName);
                })();
                """.trimIndent(),
                true
            )
        }
    }

    fun visualGutterSummaryForSelectedEditor(): String {
        return step("Read visual gutter summary for selected editor") {
            callJs(
                visualTrackerManagerScript(
                    """
                    var fileEditorManager = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project);
                    var editor = fileEditorManager ? fileEditorManager.getSelectedTextEditor() : null;
                    if (!editor) return "";

                    var document = editor.getDocument();
                    return String(manager.debugGutterSummaryFor(document));
                    """.trimIndent()
                ),
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
            settingsServiceScript("appService.setBoolean(${toJsStringLiteral(key)}, $valueStr, $defaultStr);"),
            true
        )
    }

    private fun setPluginStringSetting(key: String, value: String, setterMethod: String) {
        val keyLiteral = toJsStringLiteral(key)
        val valueLiteral = toJsStringLiteral(value)
        val setterLiteral = toJsStringLiteral(setterMethod)
        runJs(
            """
            (function() {
                var properties = com.intellij.ide.util.PropertiesComponent.getInstance();
                properties.setValue($keyLiteral, $valueLiteral);
                ${settingsServiceScript(
                    """
                    var setterName = $setterLiteral;
                    if (typeof appService[setterName] === "function") {
                        appService[setterName]($valueLiteral);
                    } else {
                        properties.setValue($keyLiteral, $valueLiteral);
                    }
                    """.trimIndent()
                )}
            })();
            """.trimIndent(),
            true
        )
    }

    @Suppress("SameParameterValue")
    private fun setPluginIntSetting(key: String, value: Int, default: Int) {
        runJs(
            settingsServiceScript("appService.setInt(${toJsStringLiteral(key)}, $value, $default);"),
            true
        )
    }

    private fun settingsServiceScript(body: String): String =
        """
        (function() {
            ${pluginClassLoaderLookupStatements()}
            if (!cl) return;
            try {
                var settingsClass = cl.loadClass("com.github.uiopak.lstcrc.toolWindow.LstCrcSettingsService");
                var appService = com.intellij.openapi.application.ApplicationManager.getApplication().getService(settingsClass);
                if (!appService) return;
                $body
            } catch(e) {}
        })();
        """.trimIndent()

    private fun settingsServiceLookupStatements(serviceVariableName: String): String =
        """
        ${pluginClassLoaderLookupStatements()}
        var $serviceVariableName = null;
        if (cl) {
            try {
                var settingsClass = cl.loadClass("com.github.uiopak.lstcrc.toolWindow.LstCrcSettingsService");
                $serviceVariableName = com.intellij.openapi.application.ApplicationManager.getApplication().getService(settingsClass);
            } catch(e) {}
        }
        """.trimIndent()

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

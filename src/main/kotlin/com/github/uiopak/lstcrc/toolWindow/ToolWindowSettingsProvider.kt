package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.messaging.PLUGIN_SETTINGS_CHANGED_TOPIC
import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.services.LstCrcGutterTrackerService
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.ui.content.impl.ContentManagerImpl

class ToolWindowSettingsProvider {

    // Use application-level settings so they are consistent across all projects.
    private val propertiesComponent = PropertiesComponent.getInstance()

    companion object {
        // --- Keys for Click Actions ---
        internal const val ACTION_NONE = "NONE"
        internal const val ACTION_OPEN_DIFF = "OPEN_DIFF"
        internal const val ACTION_OPEN_SOURCE = "OPEN_SOURCE"
        internal const val ACTION_SHOW_IN_PROJECT_TREE = "SHOW_IN_PROJECT_TREE"

        const val APP_SINGLE_CLICK_ACTION_KEY = "com.github.uiopak.lstcrc.app.singleClickAction"
        const val APP_DOUBLE_CLICK_ACTION_KEY = "com.github.uiopak.lstcrc.app.doubleClickAction"
        const val DEFAULT_SINGLE_CLICK_ACTION = ACTION_NONE
        const val DEFAULT_DOUBLE_CLICK_ACTION = ACTION_OPEN_DIFF

        const val APP_MIDDLE_CLICK_ACTION_KEY = "com.github.uiopak.lstcrc.app.middleClickAction"
        const val APP_DOUBLE_MIDDLE_CLICK_ACTION_KEY = "com.github.uiopak.lstcrc.app.doubleMiddleClickAction"
        const val DEFAULT_MIDDLE_CLICK_ACTION = ACTION_NONE
        const val DEFAULT_DOUBLE_MIDDLE_CLICK_ACTION = ACTION_NONE

        const val APP_RIGHT_CLICK_ACTION_KEY = "com.github.uiopak.lstcrc.app.rightClickAction"
        const val APP_DOUBLE_RIGHT_CLICK_ACTION_KEY = "com.github.uiopak.lstcrc.app.doubleRightClickAction"
        const val DEFAULT_RIGHT_CLICK_ACTION = ACTION_NONE
        const val DEFAULT_DOUBLE_RIGHT_CLICK_ACTION = ACTION_NONE

        // --- Keys for General Settings ---
        const val APP_SHOW_CONTEXT_MENU_KEY = "com.github.uiopak.lstcrc.app.showContextMenu"
        const val DEFAULT_SHOW_CONTEXT_MENU = false

        const val APP_USER_DOUBLE_CLICK_DELAY_KEY = "com.github.uiopak.lstcrc.app.userDoubleClickDelay"
        const val DELAY_OPTION_SYSTEM_DEFAULT = -1

        internal const val APP_INCLUDE_HEAD_IN_SCOPES_KEY = "com.github.uiopak.lstcrc.app.includeHeadInScopes"
        internal const val DEFAULT_INCLUDE_HEAD_IN_SCOPES = false

        const val APP_ENABLE_GUTTER_MARKERS_KEY = "com.github.uiopak.lstcrc.app.enableGutterMarkers"
        const val DEFAULT_ENABLE_GUTTER_MARKERS = true

        const val APP_SHOW_TOOL_WINDOW_TITLE_KEY = "com.github.uiopak.lstcrc.app.showToolWindowTitle"
        const val DEFAULT_SHOW_TOOL_WINDOW_TITLE = false

        const val APP_SHOW_WIDGET_CONTEXT_KEY = "com.github.uiopak.lstcrc.app.showWidgetContext"
        const val DEFAULT_SHOW_WIDGET_CONTEXT = false
    }

    private fun getSingleClickAction(): String = propertiesComponent.getValue(APP_SINGLE_CLICK_ACTION_KEY, DEFAULT_SINGLE_CLICK_ACTION)
    private fun setSingleClickAction(action: String) = propertiesComponent.setValue(APP_SINGLE_CLICK_ACTION_KEY, action)

    private fun getDoubleClickAction(): String = propertiesComponent.getValue(APP_DOUBLE_CLICK_ACTION_KEY, DEFAULT_DOUBLE_CLICK_ACTION)
    private fun setDoubleClickAction(action: String) = propertiesComponent.setValue(APP_DOUBLE_CLICK_ACTION_KEY, action)

    private fun getMiddleClickAction(): String = propertiesComponent.getValue(APP_MIDDLE_CLICK_ACTION_KEY, DEFAULT_MIDDLE_CLICK_ACTION)
    private fun setMiddleClickAction(action: String) = propertiesComponent.setValue(APP_MIDDLE_CLICK_ACTION_KEY, action)

    private fun getDoubleMiddleClickAction(): String = propertiesComponent.getValue(APP_DOUBLE_MIDDLE_CLICK_ACTION_KEY, DEFAULT_DOUBLE_MIDDLE_CLICK_ACTION)
    private fun setDoubleMiddleClickAction(action: String) = propertiesComponent.setValue(APP_DOUBLE_MIDDLE_CLICK_ACTION_KEY, action)

    private fun getRightClickAction(): String = propertiesComponent.getValue(APP_RIGHT_CLICK_ACTION_KEY, DEFAULT_RIGHT_CLICK_ACTION)
    private fun setRightClickAction(action: String) = propertiesComponent.setValue(APP_RIGHT_CLICK_ACTION_KEY, action)

    private fun getDoubleRightClickAction(): String = propertiesComponent.getValue(APP_DOUBLE_RIGHT_CLICK_ACTION_KEY, DEFAULT_DOUBLE_RIGHT_CLICK_ACTION)
    private fun setDoubleRightClickAction(action: String) = propertiesComponent.setValue(APP_DOUBLE_RIGHT_CLICK_ACTION_KEY, action)


    private fun setUserDoubleClickDelayMs(delay: Int) = propertiesComponent.setValue(APP_USER_DOUBLE_CLICK_DELAY_KEY, delay, DELAY_OPTION_SYSTEM_DEFAULT)
    private fun getIncludeHeadInScopes(): Boolean = propertiesComponent.getBoolean(APP_INCLUDE_HEAD_IN_SCOPES_KEY, DEFAULT_INCLUDE_HEAD_IN_SCOPES)
    private fun setIncludeHeadInScopes(include: Boolean) = propertiesComponent.setValue(APP_INCLUDE_HEAD_IN_SCOPES_KEY, include, DEFAULT_INCLUDE_HEAD_IN_SCOPES)

    fun createToolWindowSettingsGroup(): ActionGroup {
        val rootSettingsGroup = DefaultActionGroup(LstCrcBundle.message("settings.root.title"), true)

        // General Behavior Settings
        rootSettingsGroup.add(object : ToggleAction(LstCrcBundle.message("settings.gutter.marks")) {
            override fun isSelected(e: AnActionEvent): Boolean =
                propertiesComponent.getBoolean(APP_ENABLE_GUTTER_MARKERS_KEY, DEFAULT_ENABLE_GUTTER_MARKERS)

            override fun setSelected(e: AnActionEvent, state: Boolean) {
                propertiesComponent.setValue(APP_ENABLE_GUTTER_MARKERS_KEY, state, DEFAULT_ENABLE_GUTTER_MARKERS)
                e.project?.service<LstCrcGutterTrackerService>()?.settingsChanged()
            }
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
        })

        rootSettingsGroup.add(object : ToggleAction(LstCrcBundle.message("settings.show.tool.window.title")) {
            override fun isSelected(e: AnActionEvent): Boolean =
                propertiesComponent.getBoolean(APP_SHOW_TOOL_WINDOW_TITLE_KEY, DEFAULT_SHOW_TOOL_WINDOW_TITLE)

            override fun setSelected(e: AnActionEvent, state: Boolean) {
                propertiesComponent.setValue(APP_SHOW_TOOL_WINDOW_TITLE_KEY, state, DEFAULT_SHOW_TOOL_WINDOW_TITLE)
                val toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW) ?: return
                toolWindow.component.putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, if (state) null else "true")
                (toolWindow.contentManager as? ContentManagerImpl)?.let {
                    (it.ui as? ToolWindowContentUi)?.update()
                }
            }
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
        })

        rootSettingsGroup.add(object : ToggleAction(LstCrcBundle.message("settings.show.widget.context")) {
            override fun isSelected(e: AnActionEvent): Boolean =
                propertiesComponent.getBoolean(APP_SHOW_WIDGET_CONTEXT_KEY, DEFAULT_SHOW_WIDGET_CONTEXT)

            override fun setSelected(e: AnActionEvent, state: Boolean) {
                propertiesComponent.setValue(APP_SHOW_WIDGET_CONTEXT_KEY, state, DEFAULT_SHOW_WIDGET_CONTEXT)
                e.project?.messageBus?.syncPublisher(PLUGIN_SETTINGS_CHANGED_TOPIC)?.onSettingsChanged()
            }
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
        })

        rootSettingsGroup.add(object : ToggleAction(LstCrcBundle.message("settings.include.head.in.scopes")) {
            override fun isSelected(e: AnActionEvent): Boolean = getIncludeHeadInScopes()
            override fun setSelected(e: AnActionEvent, state: Boolean) {
                setIncludeHeadInScopes(state)
                val project = e.project ?: return
                val toolWindowStateService = project.service<ToolWindowStateService>()
                if (toolWindowStateService.getSelectedTabBranchName() == null) {
                    toolWindowStateService.refreshDataForActiveTabIfMatching("HEAD")
                }
            }
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
        })
        rootSettingsGroup.addSeparator()

        // Mouse Click Action Settings
        val mouseClickActionsGroup = DefaultActionGroup({ LstCrcBundle.message("settings.mouse.click.actions") }, true)
        rootSettingsGroup.add(mouseClickActionsGroup)

        val singleClickActionGroup = DefaultActionGroup({ LstCrcBundle.message("settings.left.click.single") }, true)
        singleClickActionGroup.add(createToggleAction(LstCrcBundle.message("settings.action.none"), { getSingleClickAction() == ACTION_NONE }, { setSingleClickAction(ACTION_NONE) }))
        singleClickActionGroup.add(createToggleAction(LstCrcBundle.message("settings.action.show.diff"), { getSingleClickAction() == ACTION_OPEN_DIFF }, { setSingleClickAction(ACTION_OPEN_DIFF) }))
        singleClickActionGroup.add(createToggleAction(LstCrcBundle.message("settings.action.show.source"), { getSingleClickAction() == ACTION_OPEN_SOURCE }, { setSingleClickAction(ACTION_OPEN_SOURCE) }))
        singleClickActionGroup.add(createToggleAction(LstCrcBundle.message("settings.action.show.project.tree"), { getSingleClickAction() == ACTION_SHOW_IN_PROJECT_TREE }, { setSingleClickAction(ACTION_SHOW_IN_PROJECT_TREE) }))
        mouseClickActionsGroup.add(singleClickActionGroup)

        val doubleClickActionGroup = DefaultActionGroup({ LstCrcBundle.message("settings.left.click.double") }, true)
        doubleClickActionGroup.add(createToggleAction(LstCrcBundle.message("settings.action.none"), { getDoubleClickAction() == ACTION_NONE }, { setDoubleClickAction(ACTION_NONE) }))
        doubleClickActionGroup.add(createToggleAction(LstCrcBundle.message("settings.action.show.diff"), { getDoubleClickAction() == ACTION_OPEN_DIFF }, { setDoubleClickAction(ACTION_OPEN_DIFF) }))
        doubleClickActionGroup.add(createToggleAction(LstCrcBundle.message("settings.action.show.source"), { getDoubleClickAction() == ACTION_OPEN_SOURCE }, { setDoubleClickAction(ACTION_OPEN_SOURCE) }))
        doubleClickActionGroup.add(createToggleAction(LstCrcBundle.message("settings.action.show.project.tree"), { getDoubleClickAction() == ACTION_SHOW_IN_PROJECT_TREE }, { setDoubleClickAction(ACTION_SHOW_IN_PROJECT_TREE) }))
        mouseClickActionsGroup.add(doubleClickActionGroup)
        mouseClickActionsGroup.addSeparator()

        val middleClickActionGroup = DefaultActionGroup({ LstCrcBundle.message("settings.middle.click.single") }, true)
        middleClickActionGroup.add(createToggleAction(LstCrcBundle.message("settings.action.none"), { getMiddleClickAction() == ACTION_NONE }, { setMiddleClickAction(ACTION_NONE) }))
        middleClickActionGroup.add(createToggleAction(LstCrcBundle.message("settings.action.show.diff"), { getMiddleClickAction() == ACTION_OPEN_DIFF }, { setMiddleClickAction(ACTION_OPEN_DIFF) }))
        middleClickActionGroup.add(createToggleAction(LstCrcBundle.message("settings.action.show.source"), { getMiddleClickAction() == ACTION_OPEN_SOURCE }, { setMiddleClickAction(ACTION_OPEN_SOURCE) }))
        middleClickActionGroup.add(createToggleAction(LstCrcBundle.message("settings.action.show.project.tree"), { getMiddleClickAction() == ACTION_SHOW_IN_PROJECT_TREE }, { setMiddleClickAction(ACTION_SHOW_IN_PROJECT_TREE) }))
        mouseClickActionsGroup.add(middleClickActionGroup)

        val doubleMiddleClickActionGroup = DefaultActionGroup({ LstCrcBundle.message("settings.middle.click.double") }, true)
        doubleMiddleClickActionGroup.add(createToggleAction(LstCrcBundle.message("settings.action.none"), { getDoubleMiddleClickAction() == ACTION_NONE }, { setDoubleMiddleClickAction(ACTION_NONE) }))
        doubleMiddleClickActionGroup.add(createToggleAction(LstCrcBundle.message("settings.action.show.diff"), { getDoubleMiddleClickAction() == ACTION_OPEN_DIFF }, { setDoubleMiddleClickAction(ACTION_OPEN_DIFF) }))
        doubleMiddleClickActionGroup.add(createToggleAction(LstCrcBundle.message("settings.action.show.source"), { getDoubleMiddleClickAction() == ACTION_OPEN_SOURCE }, { setDoubleMiddleClickAction(ACTION_OPEN_SOURCE) }))
        doubleMiddleClickActionGroup.add(createToggleAction(LstCrcBundle.message("settings.action.show.project.tree"), { getDoubleMiddleClickAction() == ACTION_SHOW_IN_PROJECT_TREE }, { setDoubleMiddleClickAction(ACTION_SHOW_IN_PROJECT_TREE) }))
        mouseClickActionsGroup.add(doubleMiddleClickActionGroup)
        mouseClickActionsGroup.addSeparator()

        val rightClickSettingsGroup = DefaultActionGroup({ LstCrcBundle.message("settings.right.click.behavior") }, true)
        rightClickSettingsGroup.add(object : ToggleAction(LstCrcBundle.message("settings.right.click.show.menu")) {
            override fun isSelected(e: AnActionEvent): Boolean =
                propertiesComponent.getBoolean(APP_SHOW_CONTEXT_MENU_KEY, DEFAULT_SHOW_CONTEXT_MENU)
            override fun setSelected(e: AnActionEvent, state: Boolean) =
                propertiesComponent.setValue(APP_SHOW_CONTEXT_MENU_KEY, state, DEFAULT_SHOW_CONTEXT_MENU)
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
        })
        rightClickSettingsGroup.add(object : ToggleAction(LstCrcBundle.message("settings.right.click.trigger.actions")) {
            override fun isSelected(e: AnActionEvent): Boolean =
                !propertiesComponent.getBoolean(APP_SHOW_CONTEXT_MENU_KEY, DEFAULT_SHOW_CONTEXT_MENU)
            override fun setSelected(e: AnActionEvent, state: Boolean) =
                propertiesComponent.setValue(APP_SHOW_CONTEXT_MENU_KEY, !state, DEFAULT_SHOW_CONTEXT_MENU)
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
        })
        mouseClickActionsGroup.add(rightClickSettingsGroup)

        val rightClickActionsConditionalGroup = object : DefaultActionGroup() {
            override fun update(e: AnActionEvent) {
                val showContextMenu = propertiesComponent.getBoolean(APP_SHOW_CONTEXT_MENU_KEY, DEFAULT_SHOW_CONTEXT_MENU)
                e.presentation.isEnabledAndVisible = !showContextMenu
            }
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
        }
        val rightClickActionGroup = DefaultActionGroup({ LstCrcBundle.message("settings.right.click.single") }, true)
        rightClickActionGroup.add(createToggleAction(LstCrcBundle.message("settings.action.none"), { getRightClickAction() == ACTION_NONE }, { setRightClickAction(ACTION_NONE) }))
        rightClickActionGroup.add(createToggleAction(LstCrcBundle.message("settings.action.show.diff"), { getRightClickAction() == ACTION_OPEN_DIFF }, { setRightClickAction(ACTION_OPEN_DIFF) }))
        rightClickActionGroup.add(createToggleAction(LstCrcBundle.message("settings.action.show.source"), { getRightClickAction() == ACTION_OPEN_SOURCE }, { setRightClickAction(ACTION_OPEN_SOURCE) }))
        rightClickActionGroup.add(createToggleAction(LstCrcBundle.message("settings.action.show.project.tree"), { getRightClickAction() == ACTION_SHOW_IN_PROJECT_TREE }, { setRightClickAction(ACTION_SHOW_IN_PROJECT_TREE) }))
        rightClickActionsConditionalGroup.add(rightClickActionGroup)

        val doubleRightClickActionGroup = DefaultActionGroup({ LstCrcBundle.message("settings.right.click.double") }, true)
        doubleRightClickActionGroup.add(createToggleAction(LstCrcBundle.message("settings.action.none"), { getDoubleRightClickAction() == ACTION_NONE }, { setDoubleRightClickAction(ACTION_NONE) }))
        doubleRightClickActionGroup.add(createToggleAction(LstCrcBundle.message("settings.action.show.diff"), { getDoubleRightClickAction() == ACTION_OPEN_DIFF }, { setDoubleRightClickAction(ACTION_OPEN_DIFF) }))
        doubleRightClickActionGroup.add(createToggleAction(LstCrcBundle.message("settings.action.show.source"), { getDoubleRightClickAction() == ACTION_OPEN_SOURCE }, { setDoubleRightClickAction(ACTION_OPEN_SOURCE) }))
        doubleRightClickActionGroup.add(createToggleAction(LstCrcBundle.message("settings.action.show.project.tree"), { getDoubleRightClickAction() == ACTION_SHOW_IN_PROJECT_TREE }, { setDoubleRightClickAction(ACTION_SHOW_IN_PROJECT_TREE) }))
        rightClickActionsConditionalGroup.add(doubleRightClickActionGroup)
        mouseClickActionsGroup.add(rightClickActionsConditionalGroup)
        mouseClickActionsGroup.addSeparator()

        val delaySpeedGroup = DefaultActionGroup({ LstCrcBundle.message("settings.double.click.speed") }, true)
        delaySpeedGroup.add(createToggleAction(LstCrcBundle.message("settings.speed.default"),
            { propertiesComponent.getInt(APP_USER_DOUBLE_CLICK_DELAY_KEY, DELAY_OPTION_SYSTEM_DEFAULT) == DELAY_OPTION_SYSTEM_DEFAULT },
            { setUserDoubleClickDelayMs(DELAY_OPTION_SYSTEM_DEFAULT) }
        ))
        val predefinedDelays = listOf(
            Pair(LstCrcBundle.message("settings.speed.faster"), 200),
            Pair(LstCrcBundle.message("settings.speed.fast"), 250),
            Pair(LstCrcBundle.message("settings.speed.medium"), 300),
            Pair(LstCrcBundle.message("settings.speed.slow"), 500)
        )
        predefinedDelays.forEach { (label, value) ->
            delaySpeedGroup.add(createToggleAction(label,
                { propertiesComponent.getInt(APP_USER_DOUBLE_CLICK_DELAY_KEY, DELAY_OPTION_SYSTEM_DEFAULT) == value },
                { setUserDoubleClickDelayMs(value) }
            ))
        }
        mouseClickActionsGroup.add(delaySpeedGroup)

        return rootSettingsGroup
    }

    private fun createToggleAction(text: String, isSelected: (AnActionEvent) -> Boolean, onSelected: () -> Unit): ToggleAction {
        return object : ToggleAction(text) {
            override fun isSelected(e: AnActionEvent): Boolean = isSelected(e)
            override fun setSelected(e: AnActionEvent, state: Boolean) {
                if (state) {
                    onSelected()
                }
            }
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
        }
    }
}
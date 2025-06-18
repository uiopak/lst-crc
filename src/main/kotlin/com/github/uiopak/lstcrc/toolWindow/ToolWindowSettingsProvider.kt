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
import javax.swing.UIManager

/**
 * Provides the actions for the tool window's "gear" (options) menu. This class centralizes
 * all user-configurable settings, which are stored at the application level in [PropertiesComponent].
 */
object ToolWindowSettingsProvider {

    // Use application-level settings so they are consistent across all projects.
    private val propertiesComponent = PropertiesComponent.getInstance()

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

    // --- Public Getters for Settings ---
    fun getSingleClickAction(): String = propertiesComponent.getValue(APP_SINGLE_CLICK_ACTION_KEY, DEFAULT_SINGLE_CLICK_ACTION)
    fun getDoubleClickAction(): String = propertiesComponent.getValue(APP_DOUBLE_CLICK_ACTION_KEY, DEFAULT_DOUBLE_CLICK_ACTION)
    fun getMiddleClickAction(): String = propertiesComponent.getValue(APP_MIDDLE_CLICK_ACTION_KEY, DEFAULT_MIDDLE_CLICK_ACTION)
    fun getDoubleMiddleClickAction(): String = propertiesComponent.getValue(APP_DOUBLE_MIDDLE_CLICK_ACTION_KEY, DEFAULT_DOUBLE_MIDDLE_CLICK_ACTION)
    fun getRightClickAction(): String = propertiesComponent.getValue(APP_RIGHT_CLICK_ACTION_KEY, DEFAULT_RIGHT_CLICK_ACTION)
    fun getDoubleRightClickAction(): String = propertiesComponent.getValue(APP_DOUBLE_RIGHT_CLICK_ACTION_KEY, DEFAULT_DOUBLE_RIGHT_CLICK_ACTION)
    fun isContextMenuEnabled(): Boolean = propertiesComponent.getBoolean(APP_SHOW_CONTEXT_MENU_KEY, DEFAULT_SHOW_CONTEXT_MENU)

    fun getUserDoubleClickDelayMs(): Int {
        val storedValue = propertiesComponent.getInt(APP_USER_DOUBLE_CLICK_DELAY_KEY, DELAY_OPTION_SYSTEM_DEFAULT)
        if (storedValue > 0) {
            return storedValue
        }
        val systemValue = UIManager.get("Tree.doubleClickTimeout") as? Int
        return systemValue?.takeIf { it > 0 } ?: 300
    }

    // --- Private Setters used by Actions ---
    private fun setSingleClickAction(action: String) = propertiesComponent.setValue(APP_SINGLE_CLICK_ACTION_KEY, action)
    private fun setDoubleClickAction(action: String) = propertiesComponent.setValue(APP_DOUBLE_CLICK_ACTION_KEY, action)
    private fun setMiddleClickAction(action: String) = propertiesComponent.setValue(APP_MIDDLE_CLICK_ACTION_KEY, action)
    private fun setDoubleMiddleClickAction(action: String) = propertiesComponent.setValue(APP_DOUBLE_MIDDLE_CLICK_ACTION_KEY, action)
    private fun setRightClickAction(action: String) = propertiesComponent.setValue(APP_RIGHT_CLICK_ACTION_KEY, action)
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
                // Refresh data if the currently selected tab is HEAD, to apply the setting change immediately.
                if (project.service<ToolWindowStateService>().getSelectedTabBranchName() == null) {
                    project.service<ToolWindowStateService>().refreshDataForCurrentSelection()
                }
            }
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
        })
        rootSettingsGroup.addSeparator()

        // Mouse Click Action Settings
        val mouseClickActionsGroup = DefaultActionGroup({ LstCrcBundle.message("settings.mouse.click.actions") }, true)
        rootSettingsGroup.add(mouseClickActionsGroup)

        mouseClickActionsGroup.add(createClickActionChoiceGroup("settings.left.click.single", ::getSingleClickAction, ::setSingleClickAction))
        mouseClickActionsGroup.add(createClickActionChoiceGroup("settings.left.click.double", ::getDoubleClickAction, ::setDoubleClickAction))
        mouseClickActionsGroup.addSeparator()
        mouseClickActionsGroup.add(createClickActionChoiceGroup("settings.middle.click.single", ::getMiddleClickAction, ::setMiddleClickAction))
        mouseClickActionsGroup.add(createClickActionChoiceGroup("settings.middle.click.double", ::getDoubleMiddleClickAction, ::setDoubleMiddleClickAction))
        mouseClickActionsGroup.addSeparator()

        // --- Right-Click Behavior ---
        val rightClickSettingsGroup = DefaultActionGroup({ LstCrcBundle.message("settings.right.click.behavior") }, true)
        rightClickSettingsGroup.add(createToggleAction(LstCrcBundle.message("settings.right.click.show.menu"),
            { isContextMenuEnabled() },
            { propertiesComponent.setValue(APP_SHOW_CONTEXT_MENU_KEY, true, DEFAULT_SHOW_CONTEXT_MENU) })
        )
        rightClickSettingsGroup.add(createToggleAction(LstCrcBundle.message("settings.right.click.trigger.actions"),
            { !isContextMenuEnabled() },
            { propertiesComponent.setValue(APP_SHOW_CONTEXT_MENU_KEY, false, DEFAULT_SHOW_CONTEXT_MENU) })
        )
        mouseClickActionsGroup.add(rightClickSettingsGroup)

        val rightClickActionsConditionalGroup = object : DefaultActionGroup() {
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabledAndVisible = !isContextMenuEnabled()
            }
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
        }
        rightClickActionsConditionalGroup.add(createClickActionChoiceGroup("settings.right.click.single", ::getRightClickAction, ::setRightClickAction))
        rightClickActionsConditionalGroup.add(createClickActionChoiceGroup("settings.right.click.double", ::getDoubleRightClickAction, ::setDoubleRightClickAction))
        mouseClickActionsGroup.add(rightClickActionsConditionalGroup)
        mouseClickActionsGroup.addSeparator()

        // --- Double-Click Speed ---
        val delaySpeedGroup = DefaultActionGroup({ LstCrcBundle.message("settings.double.click.speed") }, true)
        val predefinedDelays = listOf(
            Pair(LstCrcBundle.message("settings.speed.default"), DELAY_OPTION_SYSTEM_DEFAULT),
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

    /**
     * Helper to create a radio-button style group for choosing a click action.
     */
    private fun createClickActionChoiceGroup(titleKey: String, getter: () -> String, setter: (String) -> Unit): ActionGroup {
        val group = DefaultActionGroup({ LstCrcBundle.message(titleKey) }, true)
        val actions = mapOf(
            "settings.action.none" to ACTION_NONE,
            "settings.action.show.diff" to ACTION_OPEN_DIFF,
            "settings.action.show.source" to ACTION_OPEN_SOURCE,
            "settings.action.show.project.tree" to ACTION_SHOW_IN_PROJECT_TREE
        )
        actions.forEach { (textKey, actionValue) ->
            group.add(createToggleAction(
                LstCrcBundle.message(textKey),
                { getter() == actionValue },
                { setter(actionValue) }
            ))
        }
        return group
    }

    /**
     * Helper to create a [ToggleAction] for the settings menu.
     */
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
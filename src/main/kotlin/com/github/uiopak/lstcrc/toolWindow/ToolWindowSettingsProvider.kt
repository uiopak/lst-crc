package com.github.uiopak.lstcrc.toolWindow

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

class ToolWindowSettingsProvider() {

    // Use application-level settings so they are consistent across projects.
    private val propertiesComponent = PropertiesComponent.getInstance()

    companion object {
        // --- Keys ---
        private const val ACTION_NONE = "NONE"
        private const val ACTION_OPEN_DIFF = "OPEN_DIFF"
        private const val ACTION_OPEN_SOURCE = "OPEN_SOURCE"

        // Left Click Keys & Defaults
        const val APP_SINGLE_CLICK_ACTION_KEY = "com.github.uiopak.lstcrc.app.singleClickAction"
        const val APP_DOUBLE_CLICK_ACTION_KEY = "com.github.uiopak.lstcrc.app.doubleClickAction"
        const val DEFAULT_SINGLE_CLICK_ACTION = ACTION_NONE
        const val DEFAULT_DOUBLE_CLICK_ACTION = ACTION_OPEN_DIFF

        // Middle Click Keys & Defaults
        const val APP_MIDDLE_CLICK_ACTION_KEY = "com.github.uiopak.lstcrc.app.middleClickAction"
        const val APP_DOUBLE_MIDDLE_CLICK_ACTION_KEY = "com.github.uiopak.lstcrc.app.doubleMiddleClickAction"
        const val DEFAULT_MIDDLE_CLICK_ACTION = ACTION_NONE
        const val DEFAULT_DOUBLE_MIDDLE_CLICK_ACTION = ACTION_NONE

        // Right Click Keys & Defaults
        const val APP_RIGHT_CLICK_ACTION_KEY = "com.github.uiopak.lstcrc.app.rightClickAction"
        const val APP_DOUBLE_RIGHT_CLICK_ACTION_KEY = "com.github.uiopak.lstcrc.app.doubleRightClickAction"
        const val DEFAULT_RIGHT_CLICK_ACTION = ACTION_NONE
        const val DEFAULT_DOUBLE_RIGHT_CLICK_ACTION = ACTION_NONE

        // Context Menu Key & Default
        const val APP_SHOW_CONTEXT_MENU_KEY = "com.github.uiopak.lstcrc.app.showContextMenu"
        const val DEFAULT_SHOW_CONTEXT_MENU = false

        // Delay Keys & Defaults
        const val APP_USER_DOUBLE_CLICK_DELAY_KEY = "com.github.uiopak.lstcrc.app.userDoubleClickDelay"
        const val DELAY_OPTION_SYSTEM_DEFAULT = -1

        // Scope Behavior Key & Default
        internal const val APP_INCLUDE_HEAD_IN_SCOPES_KEY = "com.github.uiopak.lstcrc.app.includeHeadInScopes"
        internal const val DEFAULT_INCLUDE_HEAD_IN_SCOPES = false

        // Gutter Marker Key & Default
        const val APP_ENABLE_GUTTER_MARKERS_KEY = "com.github.uiopak.lstcrc.app.enableGutterMarkers"
        const val DEFAULT_ENABLE_GUTTER_MARKERS = true

        // Tool Window Title Key & Default
        const val APP_SHOW_TOOL_WINDOW_TITLE_KEY = "com.github.uiopak.lstcrc.app.showToolWindowTitle"
        const val DEFAULT_SHOW_TOOL_WINDOW_TITLE = false
    }

    // --- Getters and Setters ---
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
        val rootSettingsGroup = DefaultActionGroup("Git Changes View Options", true)

        // --- Gutter Markers ---
        rootSettingsGroup.add(object : ToggleAction("Show Gutter Marks for Active Branch") {
            override fun isSelected(e: AnActionEvent): Boolean =
                propertiesComponent.getBoolean(APP_ENABLE_GUTTER_MARKERS_KEY, DEFAULT_ENABLE_GUTTER_MARKERS)

            override fun setSelected(e: AnActionEvent, state: Boolean) {
                propertiesComponent.setValue(APP_ENABLE_GUTTER_MARKERS_KEY, state, DEFAULT_ENABLE_GUTTER_MARKERS)
                // Notify the service that the setting has changed so it can update trackers
                e.project?.service<LstCrcGutterTrackerService>()?.settingsChanged()
            }

            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
        })
        rootSettingsGroup.addSeparator()

        // --- Tool Window Title ---
        rootSettingsGroup.add(object : ToggleAction("Show Tool Window Title") {
            override fun isSelected(e: AnActionEvent): Boolean =
                propertiesComponent.getBoolean(APP_SHOW_TOOL_WINDOW_TITLE_KEY, DEFAULT_SHOW_TOOL_WINDOW_TITLE)

            override fun setSelected(e: AnActionEvent, state: Boolean) {
                propertiesComponent.setValue(APP_SHOW_TOOL_WINDOW_TITLE_KEY, state, DEFAULT_SHOW_TOOL_WINDOW_TITLE)

                // The logic to update the UI live, mirroring JetBrains' internal behavior.
                val toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW) ?: return
                // `null` shows the label, "true" hides it.
                val hideIdLabelValue = if (state) null else "true"

                toolWindow.component.putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, hideIdLabelValue)
                // Force the UI to re-evaluate the property and update its layout.
                // We must cast to the implementation class `ContentManagerImpl` to access the `ui` property.
                val contentManager = toolWindow.contentManager
                if (contentManager is ContentManagerImpl) {
                    (contentManager.ui as? ToolWindowContentUi)?.update()
                }
            }

            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
        })

        // --- Scope Behavior ---
        rootSettingsGroup.add(object : ToggleAction("Include HEAD tab changes in file scopes") {
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

        // --- Left Click Actions ---
        val singleClickActionGroup = DefaultActionGroup("Action on Left Single Click:", true)
        singleClickActionGroup.add(createToggleAction("None", { getSingleClickAction() == ACTION_NONE }, { setSingleClickAction(ACTION_NONE) }))
        singleClickActionGroup.add(createToggleAction("Show Diff", { getSingleClickAction() == ACTION_OPEN_DIFF }, { setSingleClickAction(ACTION_OPEN_DIFF) }))
        singleClickActionGroup.add(createToggleAction("Show Source File", { getSingleClickAction() == ACTION_OPEN_SOURCE }, { setSingleClickAction(ACTION_OPEN_SOURCE) }))
        rootSettingsGroup.add(singleClickActionGroup)
        rootSettingsGroup.addSeparator()

        val doubleClickActionGroup = DefaultActionGroup("Action on Left Double Click:", true)
        doubleClickActionGroup.add(createToggleAction("None", { getDoubleClickAction() == ACTION_NONE }, { setDoubleClickAction(ACTION_NONE) }))
        doubleClickActionGroup.add(createToggleAction("Show Diff", { getDoubleClickAction() == ACTION_OPEN_DIFF }, { setDoubleClickAction(ACTION_OPEN_DIFF) }))
        doubleClickActionGroup.add(createToggleAction("Show Source File", { getDoubleClickAction() == ACTION_OPEN_SOURCE }, { setDoubleClickAction(ACTION_OPEN_SOURCE) }))
        rootSettingsGroup.add(doubleClickActionGroup)
        rootSettingsGroup.addSeparator()

        // --- Middle Click Actions ---
        val middleClickActionGroup = DefaultActionGroup("Action on Middle Single Click:", true)
        middleClickActionGroup.add(createToggleAction("None", { getMiddleClickAction() == ACTION_NONE }, { setMiddleClickAction(ACTION_NONE) }))
        middleClickActionGroup.add(createToggleAction("Show Diff", { getMiddleClickAction() == ACTION_OPEN_DIFF }, { setMiddleClickAction(ACTION_OPEN_DIFF) }))
        middleClickActionGroup.add(createToggleAction("Show Source File", { getMiddleClickAction() == ACTION_OPEN_SOURCE }, { setMiddleClickAction(ACTION_OPEN_SOURCE) }))
        rootSettingsGroup.add(middleClickActionGroup)
        rootSettingsGroup.addSeparator()

        val doubleMiddleClickActionGroup = DefaultActionGroup("Action on Middle Double Click:", true)
        doubleMiddleClickActionGroup.add(createToggleAction("None", { getDoubleMiddleClickAction() == ACTION_NONE }, { setDoubleMiddleClickAction(ACTION_NONE) }))
        doubleMiddleClickActionGroup.add(createToggleAction("Show Diff", { getDoubleMiddleClickAction() == ACTION_OPEN_DIFF }, { setDoubleMiddleClickAction(ACTION_OPEN_DIFF) }))
        doubleMiddleClickActionGroup.add(createToggleAction("Show Source File", { getDoubleMiddleClickAction() == ACTION_OPEN_SOURCE }, { setDoubleMiddleClickAction(ACTION_OPEN_SOURCE) }))
        rootSettingsGroup.add(doubleMiddleClickActionGroup)
        rootSettingsGroup.addSeparator()

        // --- Right Click Behavior ---
        val rightClickSettingsGroup = DefaultActionGroup("Right-Click Behavior:", true)
        rightClickSettingsGroup.add(object : ToggleAction("Show Context Menu") {
            override fun isSelected(e: AnActionEvent): Boolean =
                propertiesComponent.getBoolean(APP_SHOW_CONTEXT_MENU_KEY, DEFAULT_SHOW_CONTEXT_MENU)
            override fun setSelected(e: AnActionEvent, state: Boolean) =
                propertiesComponent.setValue(APP_SHOW_CONTEXT_MENU_KEY, state, DEFAULT_SHOW_CONTEXT_MENU)
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
        })
        rightClickSettingsGroup.add(object : ToggleAction("Trigger Click Actions") {
            override fun isSelected(e: AnActionEvent): Boolean =
                !propertiesComponent.getBoolean(APP_SHOW_CONTEXT_MENU_KEY, DEFAULT_SHOW_CONTEXT_MENU)
            override fun setSelected(e: AnActionEvent, state: Boolean) =
                propertiesComponent.setValue(APP_SHOW_CONTEXT_MENU_KEY, !state, DEFAULT_SHOW_CONTEXT_MENU)
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
        })
        rootSettingsGroup.add(rightClickSettingsGroup)
        rootSettingsGroup.addSeparator()

        val rightClickActionGroup = DefaultActionGroup("Action on Right Single Click:", true)
        rightClickActionGroup.add(createToggleAction("None", { getRightClickAction() == ACTION_NONE }, { setRightClickAction(ACTION_NONE) }))
        rightClickActionGroup.add(createToggleAction("Show Diff", { getRightClickAction() == ACTION_OPEN_DIFF }, { setRightClickAction(ACTION_OPEN_DIFF) }))
        rightClickActionGroup.add(createToggleAction("Show Source File", { getRightClickAction() == ACTION_OPEN_SOURCE }, { setRightClickAction(ACTION_OPEN_SOURCE) }))
        rootSettingsGroup.add(rightClickActionGroup)
        rootSettingsGroup.addSeparator()

        val doubleRightClickActionGroup = DefaultActionGroup("Action on Right Double Click:", true)
        doubleRightClickActionGroup.add(createToggleAction("None", { getDoubleRightClickAction() == ACTION_NONE }, { setDoubleRightClickAction(ACTION_NONE) }))
        doubleRightClickActionGroup.add(createToggleAction("Show Diff", { getDoubleRightClickAction() == ACTION_OPEN_DIFF }, { setDoubleRightClickAction(ACTION_OPEN_DIFF) }))
        doubleRightClickActionGroup.add(createToggleAction("Show Source File", { getDoubleRightClickAction() == ACTION_OPEN_SOURCE }, { setDoubleRightClickAction(ACTION_OPEN_SOURCE) }))
        rootSettingsGroup.add(doubleRightClickActionGroup)
        rootSettingsGroup.addSeparator()

        // --- Double Click Speed ---
        val delaySpeedGroup = DefaultActionGroup("Double-Click Speed:", true)
        delaySpeedGroup.add(createToggleAction("Default",
            { propertiesComponent.getInt(APP_USER_DOUBLE_CLICK_DELAY_KEY, DELAY_OPTION_SYSTEM_DEFAULT) == DELAY_OPTION_SYSTEM_DEFAULT },
            { setUserDoubleClickDelayMs(DELAY_OPTION_SYSTEM_DEFAULT) }
        ))
        val predefinedDelays = listOf(Pair("Faster (200ms)", 200), Pair("Fast (250ms)", 250), Pair("Medium (300ms)", 300), Pair("Slow (500ms)", 500))
        predefinedDelays.forEach { (label, value) ->
            delaySpeedGroup.add(createToggleAction(label,
                { propertiesComponent.getInt(APP_USER_DOUBLE_CLICK_DELAY_KEY, DELAY_OPTION_SYSTEM_DEFAULT) == value },
                { setUserDoubleClickDelayMs(value) }
            ))
        }
        rootSettingsGroup.add(delaySpeedGroup)

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
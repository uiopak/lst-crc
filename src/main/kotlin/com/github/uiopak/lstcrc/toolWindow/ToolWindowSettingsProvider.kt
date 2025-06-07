package com.github.uiopak.lstcrc.toolWindow

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction

class ToolWindowSettingsProvider(private val propertiesComponent: PropertiesComponent) {

    companion object {
        // Actions
        private const val ACTION_NONE = "NONE"
        private const val ACTION_OPEN_DIFF = "OPEN_DIFF"
        private const val ACTION_OPEN_SOURCE = "OPEN_SOURCE"

        // Left Click Keys & Defaults
        private const val APP_SINGLE_CLICK_ACTION_KEY = "com.github.uiopak.lstcrc.app.singleClickAction"
        private const val APP_DOUBLE_CLICK_ACTION_KEY = "com.github.uiopak.lstcrc.app.doubleClickAction"
        private const val DEFAULT_SINGLE_CLICK_ACTION = ACTION_NONE
        private const val DEFAULT_DOUBLE_CLICK_ACTION = ACTION_OPEN_DIFF

        // Right Click Keys & Defaults
        private const val APP_RIGHT_CLICK_ACTION_KEY = "com.github.uiopak.lstcrc.app.rightClickAction"
        private const val APP_DOUBLE_RIGHT_CLICK_ACTION_KEY = "com.github.uiopak.lstcrc.app.doubleRightClickAction"
        private const val DEFAULT_RIGHT_CLICK_ACTION = ACTION_NONE
        private const val DEFAULT_DOUBLE_RIGHT_CLICK_ACTION = ACTION_NONE

        // Delay Keys & Defaults
        private const val APP_USER_DOUBLE_CLICK_DELAY_KEY = "com.github.uiopak.lstcrc.app.userDoubleClickDelay"
        private const val DELAY_OPTION_SYSTEM_DEFAULT = -1 // Special value to signify using system/default logic
    }

    // --- Getters and Setters ---
    private fun getSingleClickAction(): String = propertiesComponent.getValue(APP_SINGLE_CLICK_ACTION_KEY, DEFAULT_SINGLE_CLICK_ACTION)
    private fun setSingleClickAction(action: String) = propertiesComponent.setValue(APP_SINGLE_CLICK_ACTION_KEY, action)

    private fun getDoubleClickAction(): String = propertiesComponent.getValue(APP_DOUBLE_CLICK_ACTION_KEY, DEFAULT_DOUBLE_CLICK_ACTION)
    private fun setDoubleClickAction(action: String) = propertiesComponent.setValue(APP_DOUBLE_CLICK_ACTION_KEY, action)

    private fun getRightClickAction(): String = propertiesComponent.getValue(APP_RIGHT_CLICK_ACTION_KEY, DEFAULT_RIGHT_CLICK_ACTION)
    private fun setRightClickAction(action: String) = propertiesComponent.setValue(APP_RIGHT_CLICK_ACTION_KEY, action)

    private fun getDoubleRightClickAction(): String = propertiesComponent.getValue(APP_DOUBLE_RIGHT_CLICK_ACTION_KEY, DEFAULT_DOUBLE_RIGHT_CLICK_ACTION)
    private fun setDoubleRightClickAction(action: String) = propertiesComponent.setValue(APP_DOUBLE_RIGHT_CLICK_ACTION_KEY, action)

    private fun setUserDoubleClickDelayMs(delay: Int) {
        propertiesComponent.setValue(APP_USER_DOUBLE_CLICK_DELAY_KEY, delay, DELAY_OPTION_SYSTEM_DEFAULT)
    }

    // --- UI Group Creation ---
    fun createToolWindowSettingsGroup(): ActionGroup {
        val rootSettingsGroup = DefaultActionGroup("Git Changes View Options", true)

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

        // --- Right Click Actions ---
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
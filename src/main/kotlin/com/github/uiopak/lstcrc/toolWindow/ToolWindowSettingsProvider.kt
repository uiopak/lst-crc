package com.github.uiopak.lstcrc.toolWindow

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction

class ToolWindowSettingsProvider(private val propertiesComponent: PropertiesComponent) {

    companion object {
        // Copied from GitChangesToolWindow for direct use here
        private const val APP_SINGLE_CLICK_ACTION_KEY = "com.github.uiopak.lstcrc.app.singleClickAction"
        private const val APP_DOUBLE_CLICK_ACTION_KEY = "com.github.uiopak.lstcrc.app.doubleClickAction"
        private const val ACTION_NONE = "NONE"
        private const val ACTION_OPEN_DIFF = "OPEN_DIFF"
        private const val ACTION_OPEN_SOURCE = "OPEN_SOURCE"
        private const val DEFAULT_SINGLE_CLICK_ACTION = ACTION_NONE
        private const val DEFAULT_DOUBLE_CLICK_ACTION = ACTION_OPEN_DIFF

        private const val APP_USER_DOUBLE_CLICK_DELAY_KEY = "com.github.uiopak.lstcrc.app.userDoubleClickDelay"
        private const val DELAY_OPTION_SYSTEM_DEFAULT = -1 // Special value to signify using system/default logic
        // Fallback if system is 0 and user hasn't set one (UIManager.getInt("Tree.doubleClickTimeout") might be 0)
        // Note: DEFAULT_USER_DELAY_MS is used by ChangesTreePanel internally if system default is inadequate.

        private const val TAB_COLORING_ENABLED_KEY = "com.github.uiopak.lstcrc.app.tabColoringEnabled"
        private const val TAB_COLORING_STYLE_KEY = "com.github.uiopak.lstcrc.app.tabColoringStyle"
        private const val TAB_COLORING_COLOR_KEY = "com.github.uiopak.lstcrc.app.tabColoringColor"

        private const val DEFAULT_TAB_COLORING_ENABLED = true
        private const val DEFAULT_TAB_COLORING_STYLE = "BACKGROUND"
        private const val DEFAULT_TAB_COLORING_COLOR = "Default"
    }

    private fun getSingleClickAction(): String =
        propertiesComponent.getValue(APP_SINGLE_CLICK_ACTION_KEY, DEFAULT_SINGLE_CLICK_ACTION)

    private fun setSingleClickAction(action: String) =
        propertiesComponent.setValue(APP_SINGLE_CLICK_ACTION_KEY, action)

    private fun getDoubleClickAction(): String =
        propertiesComponent.getValue(APP_DOUBLE_CLICK_ACTION_KEY, DEFAULT_DOUBLE_CLICK_ACTION)

    private fun setDoubleClickAction(action: String) =
        propertiesComponent.setValue(APP_DOUBLE_CLICK_ACTION_KEY, action)

    private fun setUserDoubleClickDelayMs(delay: Int) {
        propertiesComponent.setValue(APP_USER_DOUBLE_CLICK_DELAY_KEY, delay, DELAY_OPTION_SYSTEM_DEFAULT)
    }

    private fun isTabColoringEnabled(): Boolean =
        propertiesComponent.getBoolean(TAB_COLORING_ENABLED_KEY, DEFAULT_TAB_COLORING_ENABLED)

    private fun setTabColoringEnabled(enabled: Boolean) =
        propertiesComponent.setValue(TAB_COLORING_ENABLED_KEY, enabled, DEFAULT_TAB_COLORING_ENABLED)

    private fun getTabColoringStyle(): String =
        propertiesComponent.getValue(TAB_COLORING_STYLE_KEY, DEFAULT_TAB_COLORING_STYLE)

    private fun setTabColoringStyle(style: String) =
        propertiesComponent.setValue(TAB_COLORING_STYLE_KEY, style, DEFAULT_TAB_COLORING_STYLE)

    private fun getTabColoringColor(): String =
        propertiesComponent.getValue(TAB_COLORING_COLOR_KEY, DEFAULT_TAB_COLORING_COLOR)

    private fun setTabColoringColor(color: String) =
        propertiesComponent.setValue(TAB_COLORING_COLOR_KEY, color, DEFAULT_TAB_COLORING_COLOR)

    fun createToolWindowSettingsGroup(): ActionGroup {
        val rootSettingsGroup = DefaultActionGroup("Git Changes View Options", true)

        val singleClickActionGroup = DefaultActionGroup("Action on Single Click:", true)
        singleClickActionGroup.add(object : ToggleAction("None") {
            override fun isSelected(ev: AnActionEvent) = getSingleClickAction() == ACTION_NONE
            override fun setSelected(ev: AnActionEvent, state: Boolean) {
                if (state) setSingleClickAction(ACTION_NONE)
            }
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
        })
        singleClickActionGroup.add(object : ToggleAction("Show Diff") {
            override fun isSelected(ev: AnActionEvent) = getSingleClickAction() == ACTION_OPEN_DIFF
            override fun setSelected(ev: AnActionEvent, state: Boolean) {
                if (state) setSingleClickAction(ACTION_OPEN_DIFF)
            }
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
        })
        singleClickActionGroup.add(object : ToggleAction("Show Source File") {
            override fun isSelected(ev: AnActionEvent) = getSingleClickAction() == ACTION_OPEN_SOURCE
            override fun setSelected(ev: AnActionEvent, state: Boolean) {
                if (state) setSingleClickAction(ACTION_OPEN_SOURCE)
            }
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
        })
        rootSettingsGroup.add(singleClickActionGroup)
        rootSettingsGroup.addSeparator()

        val doubleClickActionGroup = DefaultActionGroup("Action on Double Click:", true)
        doubleClickActionGroup.add(object : ToggleAction("None") {
            override fun isSelected(ev: AnActionEvent) = getDoubleClickAction() == ACTION_NONE
            override fun setSelected(ev: AnActionEvent, state: Boolean) {
                if (state) setDoubleClickAction(ACTION_NONE)
            }
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
        })
        doubleClickActionGroup.add(object : ToggleAction("Show Diff") {
            override fun isSelected(ev: AnActionEvent) = getDoubleClickAction() == ACTION_OPEN_DIFF
            override fun setSelected(ev: AnActionEvent, state: Boolean) {
                if (state) setDoubleClickAction(ACTION_OPEN_DIFF)
            }
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
        })
        doubleClickActionGroup.add(object : ToggleAction("Show Source File") {
            override fun isSelected(ev: AnActionEvent) = getDoubleClickAction() == ACTION_OPEN_SOURCE
            override fun setSelected(ev: AnActionEvent, state: Boolean) {
                if (state) setDoubleClickAction(ACTION_OPEN_SOURCE)
            }
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
        })
        rootSettingsGroup.add(doubleClickActionGroup)

        rootSettingsGroup.addSeparator()
        val delaySpeedGroup = DefaultActionGroup("Double-Click Speed:", true)

        delaySpeedGroup.add(object : ToggleAction("Default") {
            override fun isSelected(e: AnActionEvent) = propertiesComponent.getInt(APP_USER_DOUBLE_CLICK_DELAY_KEY, DELAY_OPTION_SYSTEM_DEFAULT) == DELAY_OPTION_SYSTEM_DEFAULT
            override fun setSelected(e: AnActionEvent, state: Boolean) {
                if (state) setUserDoubleClickDelayMs(DELAY_OPTION_SYSTEM_DEFAULT)
            }
            override fun getActionUpdateThread() = ActionUpdateThread.BGT
        })

        val predefinedDelays = listOf(Pair("Faster (200ms)", 200),Pair("Fast (250ms)", 250), Pair("Medium (300ms)", 300), Pair("Slow (500ms)", 500))
        predefinedDelays.forEach { (label, value) ->
            delaySpeedGroup.add(object : ToggleAction(label) {
                override fun isSelected(e: AnActionEvent) = propertiesComponent.getInt(APP_USER_DOUBLE_CLICK_DELAY_KEY, DELAY_OPTION_SYSTEM_DEFAULT) == value
                override fun setSelected(e: AnActionEvent, state: Boolean) {
                    if (state) setUserDoubleClickDelayMs(value)
                }
                override fun getActionUpdateThread() = ActionUpdateThread.BGT
            })
        }
        rootSettingsGroup.add(delaySpeedGroup)

        rootSettingsGroup.addSeparator()

        // Tab Coloring Settings
        rootSettingsGroup.add(object : ToggleAction("Enable Tab Coloring") {
            override fun isSelected(e: AnActionEvent): Boolean = isTabColoringEnabled()
            override fun setSelected(e: AnActionEvent, state: Boolean) {
                setTabColoringEnabled(state)
            }
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
        })

        val coloringStyleGroup = DefaultActionGroup("Tab Coloring Style", true)
        val styles = listOf("BACKGROUND", "BORDER_TOP", "BORDER_LEFT", "BORDER_RIGHT", "BORDER_BOTTOM")
        styles.forEach { style ->
            coloringStyleGroup.add(object : ToggleAction(style.replace("_", " ").split(' ').joinToString(" ") { it.lowercase().replaceFirstChar(Char::titlecase) }) {
                override fun isSelected(e: AnActionEvent): Boolean = getTabColoringStyle() == style
                override fun setSelected(e: AnActionEvent, state: Boolean) {
                    if (state) setTabColoringStyle(style)
                }
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
            })
        }
        rootSettingsGroup.add(coloringStyleGroup)

        val coloringColorGroup = DefaultActionGroup("Tab Color", true)
        val colors = listOf("Default", "Red", "Green", "Blue", "Yellow") // Predefined list of colors
        colors.forEach { color ->
            coloringColorGroup.add(object : ToggleAction(color) {
                override fun isSelected(e: AnActionEvent): Boolean = getTabColoringColor() == color
                override fun setSelected(e: AnActionEvent, state: Boolean) {
                    if (state) setTabColoringColor(color)
                }
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
            })
        }
        rootSettingsGroup.add(coloringColorGroup)

        return rootSettingsGroup
    }
}

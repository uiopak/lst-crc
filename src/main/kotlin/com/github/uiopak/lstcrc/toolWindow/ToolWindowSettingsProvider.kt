package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

class ToolWindowSettingsProvider(private val project: Project) {

    // Use application-level settings so they are consistent across projects.
    private val propertiesComponent = PropertiesComponent.getInstance()

    companion object {
        // --- Enums ---
        enum class SortType { NAME, DIFF_TYPE, FILE_TYPE, MODIFICATION_TIME }

        // --- Keys ---
        private const val ACTION_NONE = "NONE"
        private const val ACTION_OPEN_DIFF = "OPEN_DIFF"
        private const val ACTION_OPEN_SOURCE = "OPEN_SOURCE"

        // Left Click Keys & Defaults
        const val APP_SINGLE_CLICK_ACTION_KEY = "com.github.uiopak.lstcrc.app.singleClickAction"
        const val APP_DOUBLE_CLICK_ACTION_KEY = "com.github.uiopak.lstcrc.app.doubleClickAction"
        const val DEFAULT_SINGLE_CLICK_ACTION = ACTION_NONE
        const val DEFAULT_DOUBLE_CLICK_ACTION = ACTION_OPEN_DIFF

        // Right Click Keys & Defaults
        const val APP_RIGHT_CLICK_ACTION_KEY = "com.github.uiopak.lstcrc.app.rightClickAction"
        const val APP_DOUBLE_RIGHT_CLICK_ACTION_KEY = "com.github.uiopak.lstcrc.app.doubleRightClickAction"
        const val DEFAULT_RIGHT_CLICK_ACTION = ACTION_NONE
        const val DEFAULT_DOUBLE_RIGHT_CLICK_ACTION = ACTION_NONE

        // Delay Keys & Defaults
        const val APP_USER_DOUBLE_CLICK_DELAY_KEY = "com.github.uiopak.lstcrc.app.userDoubleClickDelay"
        const val DELAY_OPTION_SYSTEM_DEFAULT = -1

        // Scope Behavior Key & Default
        internal const val APP_INCLUDE_HEAD_IN_SCOPES_KEY = "com.github.uiopak.lstcrc.app.includeHeadInScopes"
        internal const val DEFAULT_INCLUDE_HEAD_IN_SCOPES = false

        // Sorting Keys & Defaults
        internal const val SORT_TYPE_KEY = "com.github.uiopak.lstcrc.app.sortType"
        internal const val SORT_ASCENDING_KEY = "com.github.uiopak.lstcrc.app.sortAscending"
        internal const val KEEP_FOLDERS_ON_TOP_KEY = "com.github.uiopak.lstcrc.app.keepFoldersOnTop"
        internal val DEFAULT_SORT_TYPE = SortType.NAME
        internal const val DEFAULT_SORT_ASCENDING = true
        internal const val DEFAULT_KEEP_FOLDERS_ON_TOP = true
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

    private fun setUserDoubleClickDelayMs(delay: Int) = propertiesComponent.setValue(APP_USER_DOUBLE_CLICK_DELAY_KEY, delay, DELAY_OPTION_SYSTEM_DEFAULT)
    private fun getIncludeHeadInScopes(): Boolean = propertiesComponent.getBoolean(APP_INCLUDE_HEAD_IN_SCOPES_KEY, DEFAULT_INCLUDE_HEAD_IN_SCOPES)
    private fun setIncludeHeadInScopes(include: Boolean) = propertiesComponent.setValue(APP_INCLUDE_HEAD_IN_SCOPES_KEY, include, DEFAULT_INCLUDE_HEAD_IN_SCOPES)

    // --- Sorting Getters/Setters ---
    private fun getSortType(): SortType = SortType.valueOf(propertiesComponent.getValue(SORT_TYPE_KEY, DEFAULT_SORT_TYPE.name))
    private fun setSortType(sortType: SortType) = propertiesComponent.setValue(SORT_TYPE_KEY, sortType.name, DEFAULT_SORT_TYPE.name)
    private fun isSortAscending(): Boolean = propertiesComponent.getBoolean(SORT_ASCENDING_KEY, DEFAULT_SORT_ASCENDING)
    private fun setSortAscending(ascending: Boolean) = propertiesComponent.setValue(SORT_ASCENDING_KEY, ascending, DEFAULT_SORT_ASCENDING)
    private fun isKeepFoldersOnTop(): Boolean = propertiesComponent.getBoolean(KEEP_FOLDERS_ON_TOP_KEY, DEFAULT_KEEP_FOLDERS_ON_TOP)
    private fun setKeepFoldersOnTop(keepOnTop: Boolean) = propertiesComponent.setValue(KEEP_FOLDERS_ON_TOP_KEY, keepOnTop, DEFAULT_KEEP_FOLDERS_ON_TOP)

    private fun getActiveChangesTreePanel(project: Project): ChangesTreePanel? {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("GitChangesView")
        return toolWindow?.contentManager?.selectedContent?.component as? ChangesTreePanel
    }

    private fun createSortingSettingsGroup(): ActionGroup {
        val sortingGroup = DefaultActionGroup("Sorting", true)

        val sortByGroup = DefaultActionGroup("Sort by", true)
        sortByGroup.add(createSortTypeAction("Name", SortType.NAME))
        sortByGroup.add(createSortTypeAction("Diff Type", SortType.DIFF_TYPE))
        sortByGroup.add(createSortTypeAction("File Type", SortType.FILE_TYPE))
        sortByGroup.add(createSortTypeAction("Modification Time", SortType.MODIFICATION_TIME))
        sortingGroup.add(sortByGroup)

        sortingGroup.add(object : ToggleAction("Sort Ascending") {
            override fun isSelected(e: AnActionEvent) = isSortAscending()
            override fun setSelected(e: AnActionEvent, state: Boolean) {
                setSortAscending(state)
                getActiveChangesTreePanel(project)?.reSortTree()
            }
            override fun getActionUpdateThread() = ActionUpdateThread.BGT
        })

        sortingGroup.add(object : ToggleAction("Group Directories First") {
            override fun isSelected(e: AnActionEvent) = isKeepFoldersOnTop()
            override fun setSelected(e: AnActionEvent, state: Boolean) {
                setKeepFoldersOnTop(state)
                getActiveChangesTreePanel(project)?.reSortTree()
            }
            override fun getActionUpdateThread() = ActionUpdateThread.BGT
        })

        return sortingGroup
    }

    private fun createSortTypeAction(text: String, sortType: SortType): ToggleAction {
        return object : ToggleAction(text) {
            override fun isSelected(e: AnActionEvent): Boolean = getSortType() == sortType
            override fun setSelected(e: AnActionEvent, state: Boolean) {
                if (state) {
                    setSortType(sortType)
                    getActiveChangesTreePanel(project)?.reSortTree()
                }
            }
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
        }
    }


    fun createToolWindowSettingsGroup(): ActionGroup {
        val rootSettingsGroup = DefaultActionGroup("Git Changes View Options", true)

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

        // --- Sorting ---
        rootSettingsGroup.add(createSortingSettingsGroup())
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
package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.messaging.PLUGIN_SETTINGS_CHANGED_TOPIC
import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.services.GitService
import com.github.uiopak.lstcrc.gutters.VisualTrackerManager
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import javax.swing.UIManager

/**
 * Provides the actions for the tool window's "gear" (options) menu. This class centralizes
 * all user-configurable settings. Values are read and written through [LstCrcSettingsService],
 * which persists them in application-level state and mirrors legacy PropertiesComponent keys
 * during the staged migration.
 */
object ToolWindowSettingsProvider {

    private fun settingsService(): LstCrcSettingsService =
        ApplicationManager.getApplication().service()

    // --- Keys for Click Actions ---
    internal const val ACTION_NONE = "NONE"
    internal const val ACTION_OPEN_DIFF = "OPEN_DIFF"
    internal const val ACTION_OPEN_SOURCE = "OPEN_SOURCE"
    internal const val ACTION_SHOW_IN_PROJECT_TREE = "SHOW_IN_PROJECT_TREE"

    internal const val APP_SINGLE_CLICK_ACTION_KEY: String = "com.github.uiopak.lstcrc.app.singleClickAction"
    internal const val APP_DOUBLE_CLICK_ACTION_KEY = "com.github.uiopak.lstcrc.app.doubleClickAction"
    internal const val DEFAULT_SINGLE_CLICK_ACTION = ACTION_OPEN_SOURCE
    internal const val DEFAULT_DOUBLE_CLICK_ACTION = ACTION_NONE

    internal const val APP_MIDDLE_CLICK_ACTION_KEY = "com.github.uiopak.lstcrc.app.middleClickAction"
    internal const val APP_DOUBLE_MIDDLE_CLICK_ACTION_KEY = "com.github.uiopak.lstcrc.app.doubleMiddleClickAction"
    internal const val DEFAULT_MIDDLE_CLICK_ACTION = ACTION_SHOW_IN_PROJECT_TREE
    internal const val DEFAULT_DOUBLE_MIDDLE_CLICK_ACTION = ACTION_NONE

    internal const val APP_RIGHT_CLICK_ACTION_KEY = "com.github.uiopak.lstcrc.app.rightClickAction"
    internal const val APP_DOUBLE_RIGHT_CLICK_ACTION_KEY = "com.github.uiopak.lstcrc.app.doubleRightClickAction"
    internal const val DEFAULT_RIGHT_CLICK_ACTION = ACTION_OPEN_DIFF
    internal const val DEFAULT_DOUBLE_RIGHT_CLICK_ACTION = ACTION_NONE

    // --- Keys for General Settings ---
    internal const val APP_SHOW_CONTEXT_MENU_KEY = "com.github.uiopak.lstcrc.app.showContextMenu"
    internal const val DEFAULT_SHOW_CONTEXT_MENU = false

    internal const val APP_USER_DOUBLE_CLICK_DELAY_KEY = "com.github.uiopak.lstcrc.app.userDoubleClickDelay"
    internal const val DELAY_OPTION_SYSTEM_DEFAULT = -1

    internal const val APP_INCLUDE_HEAD_IN_SCOPES_KEY = "com.github.uiopak.lstcrc.app.includeHeadInScopes"
    internal const val DEFAULT_INCLUDE_HEAD_IN_SCOPES = false

    const val APP_ENABLE_GUTTER_MARKERS_KEY = "com.github.uiopak.lstcrc.app.enableGutterMarkers"
    const val DEFAULT_ENABLE_GUTTER_MARKERS = true

    const val APP_ENABLE_GUTTER_FOR_NEW_FILES_KEY = "com.github.uiopak.lstcrc.app.enableGutterForNewFiles"
    const val DEFAULT_ENABLE_GUTTER_FOR_NEW_FILES = false

    const val APP_SHOW_TOOL_WINDOW_TITLE_KEY = "com.github.uiopak.lstcrc.app.showToolWindowTitle"
    const val DEFAULT_SHOW_TOOL_WINDOW_TITLE = false

    const val APP_SHOW_WIDGET_CONTEXT_KEY = "com.github.uiopak.lstcrc.app.showWidgetContext"
    const val DEFAULT_SHOW_WIDGET_CONTEXT = false

    const val APP_EXPAND_NEW_FILES_IN_COLLAPSED_DIRS_KEY = "com.github.uiopak.lstcrc.app.expandNewFilesInCollapsedDirs"
    const val DEFAULT_EXPAND_NEW_FILES_IN_COLLAPSED_DIRS = true

    const val APP_SHOW_UNTRACKED_FILES_AS_NEW_KEY = "com.github.uiopak.lstcrc.app.showUntrackedFilesAsNew"
    const val DEFAULT_SHOW_UNTRACKED_FILES_AS_NEW = false

    const val APP_SHOW_LINE_STATS_IN_TREE_KEY = "com.github.uiopak.lstcrc.app.showLineStatsInTree"
    const val DEFAULT_SHOW_LINE_STATS_IN_TREE = false

    const val APP_SHOW_CONTEXT_SINGLE_REPO_KEY = "com.github.uiopak.lstcrc.app.showContextSingleRepo"
    const val DEFAULT_SHOW_CONTEXT_SINGLE_REPO = true
    const val APP_SHOW_CONTEXT_MULTI_REPO_KEY = "com.github.uiopak.lstcrc.app.showContextMultiRepo"
    const val DEFAULT_SHOW_CONTEXT_MULTI_REPO = true
    const val APP_SHOW_CONTEXT_FOR_COMMITS_KEY = "com.github.uiopak.lstcrc.app.showContextForCommits"
    const val DEFAULT_SHOW_CONTEXT_FOR_COMMITS = false


    // --- Public Getters for Settings ---
    fun getSingleClickAction(): String = settingsService().getSingleClickAction()
    fun getDoubleClickAction(): String = settingsService().getDoubleClickAction()
    fun getMiddleClickAction(): String = settingsService().getMiddleClickAction()
    fun getDoubleMiddleClickAction(): String = settingsService().getDoubleMiddleClickAction()
    fun getRightClickAction(): String = settingsService().getRightClickAction()
    fun getDoubleRightClickAction(): String = settingsService().getDoubleRightClickAction()
    fun isContextMenuEnabled(): Boolean = settingsService().getBoolean(APP_SHOW_CONTEXT_MENU_KEY, DEFAULT_SHOW_CONTEXT_MENU)
    fun isShowContextForSingleRepoEnabled(): Boolean = settingsService().getBoolean(APP_SHOW_CONTEXT_SINGLE_REPO_KEY, DEFAULT_SHOW_CONTEXT_SINGLE_REPO)
    fun isShowContextForMultiRepoEnabled(): Boolean = settingsService().getBoolean(APP_SHOW_CONTEXT_MULTI_REPO_KEY, DEFAULT_SHOW_CONTEXT_MULTI_REPO)
    fun isShowContextForCommitsEnabled(): Boolean = settingsService().getBoolean(APP_SHOW_CONTEXT_FOR_COMMITS_KEY, DEFAULT_SHOW_CONTEXT_FOR_COMMITS)
    fun isGutterMarkersEnabled(): Boolean = settingsService().getBoolean(APP_ENABLE_GUTTER_MARKERS_KEY, DEFAULT_ENABLE_GUTTER_MARKERS)
    fun isGutterForNewFilesEnabled(): Boolean = settingsService().getBoolean(APP_ENABLE_GUTTER_FOR_NEW_FILES_KEY, DEFAULT_ENABLE_GUTTER_FOR_NEW_FILES)
    fun isIncludeHeadInScopes(): Boolean = settingsService().getBoolean(APP_INCLUDE_HEAD_IN_SCOPES_KEY, DEFAULT_INCLUDE_HEAD_IN_SCOPES)
    fun isShowWidgetContext(): Boolean = settingsService().getBoolean(APP_SHOW_WIDGET_CONTEXT_KEY, DEFAULT_SHOW_WIDGET_CONTEXT)
    fun isExpandNewFilesInCollapsedDirs(): Boolean = settingsService().getBoolean(APP_EXPAND_NEW_FILES_IN_COLLAPSED_DIRS_KEY, DEFAULT_EXPAND_NEW_FILES_IN_COLLAPSED_DIRS)
    fun isShowUntrackedFilesAsNew(): Boolean = settingsService().getBoolean(APP_SHOW_UNTRACKED_FILES_AS_NEW_KEY, DEFAULT_SHOW_UNTRACKED_FILES_AS_NEW)
    fun isShowLineStatsInTree(): Boolean = settingsService().getBoolean(APP_SHOW_LINE_STATS_IN_TREE_KEY, DEFAULT_SHOW_LINE_STATS_IN_TREE)


    fun getUserDoubleClickDelayMs(): Int {
        val storedValue = settingsService().getInt(APP_USER_DOUBLE_CLICK_DELAY_KEY, DELAY_OPTION_SYSTEM_DEFAULT)
        if (storedValue > 0) {
            return storedValue
        }
        val systemValue = UIManager.get("Tree.doubleClickTimeout") as? Int
        return systemValue?.takeIf { it > 0 } ?: 300
    }

    // --- Private Setters used by Actions ---
    private fun setSingleClickAction(action: String) = settingsService().setSingleClickAction(action)
    private fun setDoubleClickAction(action: String) = settingsService().setDoubleClickAction(action)
    private fun setMiddleClickAction(action: String) = settingsService().setMiddleClickAction(action)
    private fun setDoubleMiddleClickAction(action: String) = settingsService().setDoubleMiddleClickAction(action)
    private fun setRightClickAction(action: String) = settingsService().setRightClickAction(action)
    private fun setDoubleRightClickAction(action: String) = settingsService().setDoubleRightClickAction(action)
    private fun setUserDoubleClickDelayMs(delay: Int) = settingsService().setInt(APP_USER_DOUBLE_CLICK_DELAY_KEY, delay, DELAY_OPTION_SYSTEM_DEFAULT)


    fun createToolWindowSettingsGroup(): ActionGroup {
        val rootSettingsGroup = DefaultActionGroup(LstCrcBundle.message("settings.root.title"), true)

        // --- Gutter Marks Submenu ---
        val gutterSettingsGroup = DefaultActionGroup({ LstCrcBundle.message("settings.gutter.group.title") }, true)
        rootSettingsGroup.add(gutterSettingsGroup)

        gutterSettingsGroup.add(createBooleanSettingToggle(
            LstCrcBundle.message("settings.gutter.enable"),
            APP_ENABLE_GUTTER_MARKERS_KEY, DEFAULT_ENABLE_GUTTER_MARKERS,
            onChanged = { e, _ -> e.project?.service<VisualTrackerManager>()?.settingsChanged() }
        ))

        gutterSettingsGroup.add(createBooleanSettingToggle(
            LstCrcBundle.message("settings.gutter.for.new.files"),
            APP_ENABLE_GUTTER_FOR_NEW_FILES_KEY, DEFAULT_ENABLE_GUTTER_FOR_NEW_FILES,
            onChanged = { e, _ -> e.project?.service<VisualTrackerManager>()?.settingsChanged() },
            updateCheck = { e ->
                e.presentation.isEnabled = settingsService()
                    .getBoolean(APP_ENABLE_GUTTER_MARKERS_KEY, DEFAULT_ENABLE_GUTTER_MARKERS)
            }
        ))
        rootSettingsGroup.addSeparator()

        // --- Tree View Submenu ---
        val treeViewSettingsGroup = DefaultActionGroup({ LstCrcBundle.message("settings.tree.view.group.title") }, true).apply {
            add(createBooleanSettingToggle(
                LstCrcBundle.message("settings.tree.view.show.context.multi.repo"),
                APP_SHOW_CONTEXT_MULTI_REPO_KEY, DEFAULT_SHOW_CONTEXT_MULTI_REPO,
                onChanged = { e, _ -> rebuildActiveView(e) },
                updateCheck = { e ->
                    e.presentation.isEnabledAndVisible =
                        (e.project?.service<GitService>()?.getRepositories()?.size ?: 0) > 1
                }
            ))

            add(createBooleanSettingToggle(
                LstCrcBundle.message("settings.tree.view.show.context.single.repo"),
                APP_SHOW_CONTEXT_SINGLE_REPO_KEY, DEFAULT_SHOW_CONTEXT_SINGLE_REPO,
                onChanged = { e, _ -> rebuildActiveView(e) },
                updateCheck = { e ->
                    e.presentation.isEnabledAndVisible =
                        (e.project?.service<GitService>()?.getRepositories()?.size ?: 0) <= 1
                }
            ))

            add(createBooleanSettingToggle(
                LstCrcBundle.message("settings.tree.view.show.context.for.commits"),
                APP_SHOW_CONTEXT_FOR_COMMITS_KEY, DEFAULT_SHOW_CONTEXT_FOR_COMMITS,
                onChanged = { e, _ -> rebuildActiveView(e) },
                updateCheck = { e ->
                    e.presentation.isEnabled = isShowContextForSingleRepoEnabled() || isShowContextForMultiRepoEnabled()
                }
            ))

            add(createBooleanSettingToggle(
                LstCrcBundle.message("settings.tree.view.expand.new.files.in.collapsed.dirs"),
                APP_EXPAND_NEW_FILES_IN_COLLAPSED_DIRS_KEY, DEFAULT_EXPAND_NEW_FILES_IN_COLLAPSED_DIRS
            ))

            add(createBooleanSettingToggle(
                LstCrcBundle.message("settings.tree.view.show.untracked.files.as.new"),
                APP_SHOW_UNTRACKED_FILES_AS_NEW_KEY, DEFAULT_SHOW_UNTRACKED_FILES_AS_NEW,
                onChanged = { e, _ -> e.project?.service<ToolWindowStateService>()?.refreshDataForCurrentSelection() }
            ))

            add(createBooleanSettingToggle(
                LstCrcBundle.message("settings.tree.view.show.line.stats"),
                APP_SHOW_LINE_STATS_IN_TREE_KEY, DEFAULT_SHOW_LINE_STATS_IN_TREE,
                onChanged = { e, _ -> rebuildActiveView(e) }
            ))
        }
        rootSettingsGroup.add(treeViewSettingsGroup)
        rootSettingsGroup.addSeparator()

        // --- Other General Settings ---
        rootSettingsGroup.add(createBooleanSettingToggle(
            LstCrcBundle.message("settings.show.tool.window.title"),
            APP_SHOW_TOOL_WINDOW_TITLE_KEY, DEFAULT_SHOW_TOOL_WINDOW_TITLE,
            onChanged = { e, state -> updateToolWindowTitleVisibility(e, state) }
        ))

        rootSettingsGroup.add(createBooleanSettingToggle(
            LstCrcBundle.message("settings.show.widget.context"),
            APP_SHOW_WIDGET_CONTEXT_KEY, DEFAULT_SHOW_WIDGET_CONTEXT,
            onChanged = { e, _ -> e.project?.messageBus?.syncPublisher(PLUGIN_SETTINGS_CHANGED_TOPIC)?.onSettingsChanged() }
        ))

        rootSettingsGroup.add(createBooleanSettingToggle(
            LstCrcBundle.message("settings.include.head.in.scopes"),
            APP_INCLUDE_HEAD_IN_SCOPES_KEY, DEFAULT_INCLUDE_HEAD_IN_SCOPES,
            onChanged = { e, _ ->
                val project = e.project ?: return@createBooleanSettingToggle
                if (project.service<ToolWindowStateService>().getSelectedTabBranchName() == null) {
                    project.service<ToolWindowStateService>().refreshDataForCurrentSelection()
                }
            }
        ))
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
            { settingsService().setBoolean(APP_SHOW_CONTEXT_MENU_KEY, true, DEFAULT_SHOW_CONTEXT_MENU) })
        )
        rightClickSettingsGroup.add(createToggleAction(LstCrcBundle.message("settings.right.click.trigger.actions"),
            { !isContextMenuEnabled() },
            { settingsService().setBoolean(APP_SHOW_CONTEXT_MENU_KEY, false, DEFAULT_SHOW_CONTEXT_MENU) })
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
                { settingsService().getInt(APP_USER_DOUBLE_CLICK_DELAY_KEY, DELAY_OPTION_SYSTEM_DEFAULT) == value },
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
            LstCrcBundle.message("settings.action.none") to ACTION_NONE,
            LstCrcBundle.message("settings.action.show.diff") to ACTION_OPEN_DIFF,
            LstCrcBundle.message("settings.action.show.source") to ACTION_OPEN_SOURCE,
            LstCrcBundle.message("settings.action.show.project.tree") to ACTION_SHOW_IN_PROJECT_TREE
        )
        actions.forEach { (textKey, actionValue) ->
            group.add(createToggleAction(
                textKey,
                { getter() == actionValue },
                { setter(actionValue) }
            ))
        }
        return group
    }

    /**
     * Helper to create a [ToggleAction] for a boolean setting stored in [LstCrcSettingsService].
     *
     * @param text The display text for the action.
     * @param key The settings key.
     * @param default The default value.
     * @param onChanged Optional callback invoked after the value changes. Receives the event and new state.
     * @param updateCheck Optional predicate to control enabled/visible state in [ToggleAction.update].
     */
    private fun createBooleanSettingToggle(
        text: String,
        key: String,
        default: Boolean,
        onChanged: ((AnActionEvent, Boolean) -> Unit)? = null,
        updateCheck: ((AnActionEvent) -> Unit)? = null
    ): ToggleAction {
        return object : ToggleAction(text) {
            override fun update(e: AnActionEvent) {
                super.update(e)
                updateCheck?.invoke(e)
            }

            override fun isSelected(e: AnActionEvent): Boolean =
                settingsService().getBoolean(key, default)

            override fun setSelected(e: AnActionEvent, state: Boolean) {
                settingsService().setBoolean(key, state, default)
                onChanged?.invoke(e, state)
            }

            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
        }
    }

    /**
     * Rebuilds the tree view of the currently active [LstCrcChangesBrowser] in the tool window.
     */
    private fun rebuildActiveView(e: AnActionEvent) {
        val toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW) ?: return
        val browser = toolWindow.contentManager.selectedContent?.component as? LstCrcChangesBrowser
        browser?.rebuildView()
    }

    /**
     * Updates the tool window's ID label visibility and refreshes the header UI.
     * Delegates the impl-package details to ToolWindowUiCompatibility so this
     * settings provider stays isolated from internal tool-window UI classes.
     */
    private fun updateToolWindowTitleVisibility(e: AnActionEvent, showTitle: Boolean) {
        val toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW) ?: return
        ToolWindowUiCompatibility.setToolWindowTitleVisible(toolWindow, showTitle)
    }

    /**
     * Helper to create a radio-style [ToggleAction] for the settings menu.
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
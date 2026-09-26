package com.github.uiopak.lstcrc.toolWindow

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
 * which persists them in application-level state.
 */
object ToolWindowSettingsProvider {

    private data class ClickActionSettingDefinition(val titleKey: String, val setting: StringSettingDefinition)

    private data class ClickActionChoice(val labelKey: String, val actionValue: String)
    private data class DelayChoice(val labelKey: String, val delayMs: Int)
    private data class RightClickModeChoice(val labelKey: String, val contextMenuEnabled: Boolean)

    private fun settingsService(): LstCrcSettingsService =
        ApplicationManager.getApplication().service()

    // --- Keys for Click Actions ---
    internal const val ACTION_NONE = "NONE"
    internal const val ACTION_OPEN_DIFF = "OPEN_DIFF"
    internal const val ACTION_OPEN_SOURCE = "OPEN_SOURCE"
    internal const val ACTION_SHOW_IN_PROJECT_TREE = "SHOW_IN_PROJECT_TREE"

    private val clickActionChoices = listOf(
        ClickActionChoice("settings.action.none", ACTION_NONE),
        ClickActionChoice("settings.action.show.diff", ACTION_OPEN_DIFF),
        ClickActionChoice("settings.action.show.source", ACTION_OPEN_SOURCE),
        ClickActionChoice("settings.action.show.project.tree", ACTION_SHOW_IN_PROJECT_TREE)
    )

    private val leftClickSettings = listOf(
        ClickActionSettingDefinition("settings.left.click.single", LstCrcSettingDefinitions.SINGLE_CLICK_ACTION),
        ClickActionSettingDefinition("settings.left.click.double", LstCrcSettingDefinitions.DOUBLE_CLICK_ACTION)
    )

    private val middleClickSettings = listOf(
        ClickActionSettingDefinition("settings.middle.click.single", LstCrcSettingDefinitions.MIDDLE_CLICK_ACTION),
        ClickActionSettingDefinition("settings.middle.click.double", LstCrcSettingDefinitions.DOUBLE_MIDDLE_CLICK_ACTION)
    )

    private val rightClickSettings = listOf(
        ClickActionSettingDefinition("settings.right.click.single", LstCrcSettingDefinitions.RIGHT_CLICK_ACTION),
        ClickActionSettingDefinition("settings.right.click.double", LstCrcSettingDefinitions.DOUBLE_RIGHT_CLICK_ACTION)
    )

    private val doubleClickDelayChoices = listOf(
        DelayChoice("settings.speed.default", LstCrcSettingDefinitions.USER_DOUBLE_CLICK_DELAY.defaultValue),
        DelayChoice("settings.speed.faster", 200),
        DelayChoice("settings.speed.fast", 250),
        DelayChoice("settings.speed.medium", 300),
        DelayChoice("settings.speed.slow", 500)
    )

    private val rightClickModeChoices = listOf(
        RightClickModeChoice("settings.right.click.show.menu", true),
        RightClickModeChoice("settings.right.click.trigger.actions", false)
    )

    // --- Public Getters for Settings ---
    fun getSingleClickAction(): String = settingsService()[LstCrcSettingDefinitions.SINGLE_CLICK_ACTION]
    fun getDoubleClickAction(): String = settingsService()[LstCrcSettingDefinitions.DOUBLE_CLICK_ACTION]
    fun getMiddleClickAction(): String = settingsService()[LstCrcSettingDefinitions.MIDDLE_CLICK_ACTION]
    fun getDoubleMiddleClickAction(): String = settingsService()[LstCrcSettingDefinitions.DOUBLE_MIDDLE_CLICK_ACTION]
    fun getRightClickAction(): String = settingsService()[LstCrcSettingDefinitions.RIGHT_CLICK_ACTION]
    fun getDoubleRightClickAction(): String = settingsService()[LstCrcSettingDefinitions.DOUBLE_RIGHT_CLICK_ACTION]
    fun isContextMenuEnabled(): Boolean = settingsService()[LstCrcSettingDefinitions.SHOW_CONTEXT_MENU]
    fun isShowContextForSingleRepoEnabled(): Boolean = settingsService()[LstCrcSettingDefinitions.SHOW_CONTEXT_SINGLE_REPO]
    fun isShowContextForMultiRepoEnabled(): Boolean = settingsService()[LstCrcSettingDefinitions.SHOW_CONTEXT_MULTI_REPO]
    fun isShowContextForCommitsEnabled(): Boolean = settingsService()[LstCrcSettingDefinitions.SHOW_CONTEXT_FOR_COMMITS]
    fun isGutterMarkersEnabled(): Boolean = settingsService()[LstCrcSettingDefinitions.ENABLE_GUTTER_MARKERS]
    fun isGutterForNewFilesEnabled(): Boolean = settingsService()[LstCrcSettingDefinitions.ENABLE_GUTTER_FOR_NEW_FILES]
    fun isIncludeHeadInScopes(): Boolean = settingsService()[LstCrcSettingDefinitions.INCLUDE_HEAD_IN_SCOPES]
    fun isShowToolWindowTitleEnabled(): Boolean = settingsService()[LstCrcSettingDefinitions.SHOW_TOOL_WINDOW_TITLE]
    fun isShowWidgetContext(): Boolean = settingsService()[LstCrcSettingDefinitions.SHOW_WIDGET_CONTEXT]
    fun isExpandNewFilesInCollapsedDirs(): Boolean = settingsService()[LstCrcSettingDefinitions.EXPAND_NEW_FILES_IN_COLLAPSED_DIRS]
    fun isShowUntrackedFilesAsNew(): Boolean = settingsService()[LstCrcSettingDefinitions.SHOW_UNTRACKED_FILES_AS_NEW]
    fun isShowLineStatsInTree(): Boolean = settingsService()[LstCrcSettingDefinitions.SHOW_LINE_STATS_IN_TREE]

    fun getUserDoubleClickDelayMs(): Int {
        val storedValue = settingsService()[LstCrcSettingDefinitions.USER_DOUBLE_CLICK_DELAY]
        if (storedValue > 0) {
            return storedValue
        }
        val systemValue = UIManager.get("Tree.doubleClickTimeout") as? Int
        return systemValue?.takeIf { it > 0 } ?: 300
    }


    fun createToolWindowSettingsGroup(): ActionGroup {
        val rootSettingsGroup = DefaultActionGroup(LstCrcBundle.message("settings.root.title"), true)

        rootSettingsGroup.add(createGutterSettingsGroup())
        rootSettingsGroup.addSeparator()

        rootSettingsGroup.add(createTreeViewSettingsGroup())
        rootSettingsGroup.addSeparator()

        addGeneralSettingsActions(rootSettingsGroup)
        rootSettingsGroup.addSeparator()

        rootSettingsGroup.add(createMouseClickActionsGroup())

        return rootSettingsGroup
    }

    private fun createMouseClickActionsGroup(): ActionGroup {
        val mouseClickActionsGroup = DefaultActionGroup({ LstCrcBundle.message("settings.mouse.click.actions") }, true)

        addClickActionSettings(mouseClickActionsGroup, leftClickSettings)
        mouseClickActionsGroup.addSeparator()
        addClickActionSettings(mouseClickActionsGroup, middleClickSettings)
        mouseClickActionsGroup.addSeparator()

        val rightClickSettingsGroup = DefaultActionGroup({ LstCrcBundle.message("settings.right.click.behavior") }, true)
        rightClickModeChoices.forEach { choice ->
            rightClickSettingsGroup.add(createToggleAction(
                LstCrcBundle.message(choice.labelKey),
                { isContextMenuEnabled() == choice.contextMenuEnabled },
                { settingsService()[LstCrcSettingDefinitions.SHOW_CONTEXT_MENU] = choice.contextMenuEnabled }
            ))
        }
        mouseClickActionsGroup.add(rightClickSettingsGroup)

        val rightClickActionsConditionalGroup = object : DefaultActionGroup() {
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabledAndVisible = !isContextMenuEnabled()
            }

            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
        }
        addClickActionSettings(rightClickActionsConditionalGroup, rightClickSettings)
        mouseClickActionsGroup.add(rightClickActionsConditionalGroup)
        mouseClickActionsGroup.addSeparator()

        val delaySpeedGroup = DefaultActionGroup({ LstCrcBundle.message("settings.double.click.speed") }, true)
        doubleClickDelayChoices.forEach { choice ->
            delaySpeedGroup.add(createToggleAction(LstCrcBundle.message(choice.labelKey),
                { settingsService()[LstCrcSettingDefinitions.USER_DOUBLE_CLICK_DELAY] == choice.delayMs },
                { settingsService()[LstCrcSettingDefinitions.USER_DOUBLE_CLICK_DELAY] = choice.delayMs }
            ))
        }
        mouseClickActionsGroup.add(delaySpeedGroup)

        return mouseClickActionsGroup
    }

    private fun createTreeViewSettingsGroup(): ActionGroup {
        return DefaultActionGroup({ LstCrcBundle.message("settings.tree.view.group.title") }, true).apply {
            add(createBooleanSettingToggle(
                LstCrcBundle.message("settings.tree.view.show.context.multi.repo"),
                LstCrcSettingDefinitions.SHOW_CONTEXT_MULTI_REPO,
                onChanged = { e, _ -> rebuildActiveView(e) },
                updateCheck = { e ->
                    e.presentation.isEnabledAndVisible =
                        (e.project?.service<GitService>()?.getRepositories()?.size ?: 0) > 1
                }
            ))

            add(createBooleanSettingToggle(
                LstCrcBundle.message("settings.tree.view.show.context.single.repo"),
                LstCrcSettingDefinitions.SHOW_CONTEXT_SINGLE_REPO,
                onChanged = { e, _ -> rebuildActiveView(e) },
                updateCheck = { e ->
                    e.presentation.isEnabledAndVisible =
                        (e.project?.service<GitService>()?.getRepositories()?.size ?: 0) <= 1
                }
            ))

            add(createBooleanSettingToggle(
                LstCrcBundle.message("settings.tree.view.show.context.for.commits"),
                LstCrcSettingDefinitions.SHOW_CONTEXT_FOR_COMMITS,
                onChanged = { e, _ -> rebuildActiveView(e) },
                updateCheck = { e ->
                    e.presentation.isEnabled = isShowContextForSingleRepoEnabled() || isShowContextForMultiRepoEnabled()
                }
            ))

            add(createBooleanSettingToggle(
                LstCrcBundle.message("settings.tree.view.expand.new.files.in.collapsed.dirs"),
                LstCrcSettingDefinitions.EXPAND_NEW_FILES_IN_COLLAPSED_DIRS
            ))

            add(createBooleanSettingToggle(
                LstCrcBundle.message("settings.tree.view.show.untracked.files.as.new"),
                LstCrcSettingDefinitions.SHOW_UNTRACKED_FILES_AS_NEW,
                onChanged = { e, _ -> e.project?.service<ToolWindowStateService>()?.refreshDataForCurrentSelection() }
            ))

            add(createBooleanSettingToggle(
                LstCrcBundle.message("settings.tree.view.show.line.stats"),
                LstCrcSettingDefinitions.SHOW_LINE_STATS_IN_TREE,
                onChanged = { e, _ -> rebuildActiveView(e) }
            ))
        }
    }

    private fun createGutterSettingsGroup(): ActionGroup {
        return DefaultActionGroup({ LstCrcBundle.message("settings.gutter.group.title") }, true).apply {
            add(createBooleanSettingToggle(
                LstCrcBundle.message("settings.gutter.enable"),
                LstCrcSettingDefinitions.ENABLE_GUTTER_MARKERS,
                onChanged = { e, _ -> notifyVisualTrackerSettingsChanged(e) }
            ))

            add(createBooleanSettingToggle(
                LstCrcBundle.message("settings.gutter.for.new.files"),
                LstCrcSettingDefinitions.ENABLE_GUTTER_FOR_NEW_FILES,
                onChanged = { e, _ -> notifyVisualTrackerSettingsChanged(e) },
                updateCheck = { e ->
                    e.presentation.isEnabled = settingsService()[LstCrcSettingDefinitions.ENABLE_GUTTER_MARKERS]
                }
            ))
        }
    }

    private fun addGeneralSettingsActions(rootSettingsGroup: DefaultActionGroup) {
        rootSettingsGroup.add(createBooleanSettingToggle(
            LstCrcBundle.message("settings.show.tool.window.title"),
            LstCrcSettingDefinitions.SHOW_TOOL_WINDOW_TITLE,
            onChanged = { e, state -> updateToolWindowTitleVisibility(e, state) }
        ))

        rootSettingsGroup.add(createBooleanSettingToggle(
            LstCrcBundle.message("settings.show.widget.context"),
            LstCrcSettingDefinitions.SHOW_WIDGET_CONTEXT,
            onChanged = { e, _ -> e.project?.let(LstCrcStatusWidget::refresh) }
        ))

        rootSettingsGroup.add(createBooleanSettingToggle(
            LstCrcBundle.message("settings.include.head.in.scopes"),
            LstCrcSettingDefinitions.INCLUDE_HEAD_IN_SCOPES,
            onChanged = { e, _ ->
                val project = e.project ?: return@createBooleanSettingToggle
                if (project.service<ToolWindowStateService>().getSelectedTabBranchName() == null) {
                    project.service<ToolWindowStateService>().refreshDataForCurrentSelection()
                }
            }
        ))
    }

    /**
     * Helper to create a radio-button style group for choosing a click action.
     */
    private fun addClickActionSettings(group: DefaultActionGroup, definitions: Iterable<ClickActionSettingDefinition>) {
        definitions.forEach { definition ->
            group.add(createClickActionChoiceGroup(definition))
        }
    }

    private fun createClickActionChoiceGroup(definition: ClickActionSettingDefinition): ActionGroup {
        val group = DefaultActionGroup({ LstCrcBundle.message(definition.titleKey) }, true)
        clickActionChoices.forEach { choice ->
            group.add(createToggleAction(
                LstCrcBundle.message(choice.labelKey),
                { settingsService()[definition.setting] == choice.actionValue },
                { settingsService()[definition.setting] = choice.actionValue }
            ))
        }
        return group
    }

    /**
     * A [ToggleAction] for a boolean [setting].
     *
     * @param onChanged Called after the value changes, with the event and the new value.
     * @param updateCheck Controls enabled/visible state in [ToggleAction.update].
     */
    private fun createBooleanSettingToggle(
        text: String,
        setting: BooleanSettingDefinition,
        onChanged: ((AnActionEvent, Boolean) -> Unit)? = null,
        updateCheck: ((AnActionEvent) -> Unit)? = null
    ): ToggleAction {
        return object : ToggleAction(text) {
            override fun update(e: AnActionEvent) {
                super.update(e)
                updateCheck?.invoke(e)
            }

            override fun isSelected(e: AnActionEvent): Boolean = settingsService()[setting]

            override fun setSelected(e: AnActionEvent, state: Boolean) {
                settingsService()[setting] = state
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

    private fun notifyVisualTrackerSettingsChanged(e: AnActionEvent) {
        e.project?.service<VisualTrackerManager>()?.settingsChanged()
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
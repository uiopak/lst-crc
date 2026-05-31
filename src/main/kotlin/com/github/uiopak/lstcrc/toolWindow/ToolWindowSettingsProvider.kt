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
 * which persists them in application-level state and mirrors legacy PropertiesComponent keys
 * during the staged migration.
 */
object ToolWindowSettingsProvider {

    private data class ClickActionSettingDefinition(
        val titleKey: String,
        val getter: () -> String,
        val setter: (String) -> Unit
    )

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
        ClickActionSettingDefinition("settings.left.click.single", ::getSingleClickAction) { settingsService().setSingleClickAction(it) },
        ClickActionSettingDefinition("settings.left.click.double", ::getDoubleClickAction) { settingsService().setDoubleClickAction(it) }
    )

    private val middleClickSettings = listOf(
        ClickActionSettingDefinition("settings.middle.click.single", ::getMiddleClickAction) { settingsService().setMiddleClickAction(it) },
        ClickActionSettingDefinition("settings.middle.click.double", ::getDoubleMiddleClickAction) { settingsService().setDoubleMiddleClickAction(it) }
    )

    private val rightClickSettings = listOf(
        ClickActionSettingDefinition("settings.right.click.single", ::getRightClickAction) { settingsService().setRightClickAction(it) },
        ClickActionSettingDefinition("settings.right.click.double", ::getDoubleRightClickAction) { settingsService().setDoubleRightClickAction(it) }
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
    fun getSingleClickAction(): String = settingsService().getSingleClickAction()
    fun getDoubleClickAction(): String = settingsService().getDoubleClickAction()
    fun getMiddleClickAction(): String = settingsService().getMiddleClickAction()
    fun getDoubleMiddleClickAction(): String = settingsService().getDoubleMiddleClickAction()
    fun getRightClickAction(): String = settingsService().getRightClickAction()
    fun getDoubleRightClickAction(): String = settingsService().getDoubleRightClickAction()
    fun isContextMenuEnabled(): Boolean = settingsService().isContextMenuEnabled()
    fun isShowContextForSingleRepoEnabled(): Boolean = settingsService().isShowContextForSingleRepo()
    fun isShowContextForMultiRepoEnabled(): Boolean = settingsService().isShowContextForMultiRepo()
    fun isShowContextForCommitsEnabled(): Boolean = settingsService().isShowContextForCommits()
    fun isGutterMarkersEnabled(): Boolean = settingsService().isGutterMarkersEnabled()
    fun isGutterForNewFilesEnabled(): Boolean = settingsService().isGutterForNewFilesEnabled()
    fun isIncludeHeadInScopes(): Boolean = settingsService().isIncludeHeadInScopes()
    fun isShowToolWindowTitleEnabled(): Boolean = settingsService().isShowToolWindowTitle()
    fun isShowWidgetContext(): Boolean = settingsService().isShowWidgetContext()
    fun isExpandNewFilesInCollapsedDirs(): Boolean = settingsService().isExpandNewFilesInCollapsedDirs()
    fun isShowUntrackedFilesAsNew(): Boolean = settingsService().isShowUntrackedFilesAsNew()
    fun isShowLineStatsInTree(): Boolean = settingsService().isShowLineStatsInTree()

    fun getUserDoubleClickDelayMs(): Int {
        val storedValue = settingsService().getUserDoubleClickDelay()
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
                { settingsService().setContextMenuEnabled(choice.contextMenuEnabled) }
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
                { settingsService().getUserDoubleClickDelay() == choice.delayMs },
                { settingsService().setUserDoubleClickDelay(choice.delayMs) }
            ))
        }
        mouseClickActionsGroup.add(delaySpeedGroup)

        return mouseClickActionsGroup
    }

    private fun createTreeViewSettingsGroup(): ActionGroup {
        return DefaultActionGroup({ LstCrcBundle.message("settings.tree.view.group.title") }, true).apply {
            add(createBooleanSettingToggle(
                LstCrcBundle.message("settings.tree.view.show.context.multi.repo"),
                ::isShowContextForMultiRepoEnabled,
                { settingsService().setShowContextForMultiRepo(it) },
                onChanged = { e, _ -> rebuildActiveView(e) },
                updateCheck = { e ->
                    e.presentation.isEnabledAndVisible =
                        (e.project?.service<GitService>()?.getRepositories()?.size ?: 0) > 1
                }
            ))

            add(createBooleanSettingToggle(
                LstCrcBundle.message("settings.tree.view.show.context.single.repo"),
                ::isShowContextForSingleRepoEnabled,
                { settingsService().setShowContextForSingleRepo(it) },
                onChanged = { e, _ -> rebuildActiveView(e) },
                updateCheck = { e ->
                    e.presentation.isEnabledAndVisible =
                        (e.project?.service<GitService>()?.getRepositories()?.size ?: 0) <= 1
                }
            ))

            add(createBooleanSettingToggle(
                LstCrcBundle.message("settings.tree.view.show.context.for.commits"),
                ::isShowContextForCommitsEnabled,
                { settingsService().setShowContextForCommits(it) },
                onChanged = { e, _ -> rebuildActiveView(e) },
                updateCheck = { e ->
                    e.presentation.isEnabled = isShowContextForSingleRepoEnabled() || isShowContextForMultiRepoEnabled()
                }
            ))

            add(createBooleanSettingToggle(
                LstCrcBundle.message("settings.tree.view.expand.new.files.in.collapsed.dirs"),
                ::isExpandNewFilesInCollapsedDirs,
                { settingsService().setExpandNewFilesInCollapsedDirs(it) }
            ))

            add(createBooleanSettingToggle(
                LstCrcBundle.message("settings.tree.view.show.untracked.files.as.new"),
                ::isShowUntrackedFilesAsNew,
                { settingsService().setShowUntrackedFilesAsNew(it) },
                onChanged = { e, _ -> e.project?.service<ToolWindowStateService>()?.refreshDataForCurrentSelection() }
            ))

            add(createBooleanSettingToggle(
                LstCrcBundle.message("settings.tree.view.show.line.stats"),
                ::isShowLineStatsInTree,
                { settingsService().setShowLineStatsInTree(it) },
                onChanged = { e, _ -> rebuildActiveView(e) }
            ))
        }
    }

    private fun createGutterSettingsGroup(): ActionGroup {
        return DefaultActionGroup({ LstCrcBundle.message("settings.gutter.group.title") }, true).apply {
            add(createBooleanSettingToggle(
                LstCrcBundle.message("settings.gutter.enable"),
                ::isGutterMarkersEnabled,
                { settingsService().setGutterMarkersEnabled(it) },
                onChanged = { e, _ -> notifyVisualTrackerSettingsChanged(e) }
            ))

            add(createBooleanSettingToggle(
                LstCrcBundle.message("settings.gutter.for.new.files"),
                ::isGutterForNewFilesEnabled,
                { settingsService().setGutterForNewFilesEnabled(it) },
                onChanged = { e, _ -> notifyVisualTrackerSettingsChanged(e) },
                updateCheck = { e ->
                    e.presentation.isEnabled = settingsService().isGutterMarkersEnabled()
                }
            ))
        }
    }

    private fun addGeneralSettingsActions(rootSettingsGroup: DefaultActionGroup) {
        rootSettingsGroup.add(createBooleanSettingToggle(
            LstCrcBundle.message("settings.show.tool.window.title"),
            ::isShowToolWindowTitleEnabled,
            { settingsService().setShowToolWindowTitle(it) },
            onChanged = { e, state -> updateToolWindowTitleVisibility(e, state) }
        ))

        rootSettingsGroup.add(createBooleanSettingToggle(
            LstCrcBundle.message("settings.show.widget.context"),
            ::isShowWidgetContext,
            { settingsService().setShowWidgetContext(it) },
            onChanged = { e, _ -> e.project?.let(LstCrcStatusWidget::refresh) }
        ))

        rootSettingsGroup.add(createBooleanSettingToggle(
            LstCrcBundle.message("settings.include.head.in.scopes"),
            ::isIncludeHeadInScopes,
            { settingsService().setIncludeHeadInScopes(it) },
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
                { definition.getter() == choice.actionValue },
                { definition.setter(choice.actionValue) }
            ))
        }
        return group
    }

    /**
     * Helper to create a [ToggleAction] for a boolean setting stored in [LstCrcSettingsService].
     *
     * @param text The display text for the action.
     * @param isSelected Reads the current value from the typed settings service accessor.
     * @param setSelected Persists the value through the typed settings service accessor.
     * @param onChanged Optional callback invoked after the value changes. Receives the event and new state.
     * @param updateCheck Optional predicate to control enabled/visible state in [ToggleAction.update].
     */
    private fun createBooleanSettingToggle(
        text: String,
        isSelected: () -> Boolean,
        setSelected: (Boolean) -> Unit,
        onChanged: ((AnActionEvent, Boolean) -> Unit)? = null,
        updateCheck: ((AnActionEvent) -> Unit)? = null
    ): ToggleAction {
        return object : ToggleAction(text) {
            override fun update(e: AnActionEvent) {
                super.update(e)
                updateCheck?.invoke(e)
            }

            override fun isSelected(e: AnActionEvent): Boolean =
                isSelected()

            override fun setSelected(e: AnActionEvent, state: Boolean) {
                setSelected(state)
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
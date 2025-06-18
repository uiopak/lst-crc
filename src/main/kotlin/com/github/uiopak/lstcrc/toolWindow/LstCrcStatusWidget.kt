package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.messaging.PLUGIN_SETTINGS_CHANGED_TOPIC
import com.github.uiopak.lstcrc.messaging.PluginSettingsChangedListener
import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.services.GitService
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.github.uiopak.lstcrc.state.ToolWindowState
import com.github.uiopak.lstcrc.utils.LstCrcKeys
import com.intellij.ide.DataManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import com.intellij.util.Consumer
import com.intellij.util.messages.MessageBusConnection
import java.awt.Component
import java.awt.Point
import java.awt.event.MouseEvent


class LstCrcStatusWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = LstCrcStatusWidget.ID
    override fun getDisplayName(): String = LstCrcBundle.message("widget.display.name")
    override fun isAvailable(project: Project): Boolean = true
    override fun createWidget(project: Project): StatusBarWidget = LstCrcStatusWidget(project)
    override fun disposeWidget(widget: StatusBarWidget) {
        if (widget is LstCrcStatusWidget) {
            widget.dispose()
        }
    }
    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

class LstCrcStatusWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.TextPresentation {

    private var statusBar: StatusBar? = null
    private var messageBusConnection: MessageBusConnection? = null
    private var currentText: String = LstCrcBundle.message("plugin.name.short")
    private val logger = thisLogger()

    companion object {
        const val ID = "LstCrcStatusWidget"
        private const val GIT_CHANGES_TOOL_WINDOW_ID = "GitChangesView"
    }

    override fun ID(): String = ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    private fun updateWidgetText(state: ToolWindowState?) {
        val properties = PropertiesComponent.getInstance()
        val showContext = properties.getBoolean(
            ToolWindowSettingsProvider.APP_SHOW_WIDGET_CONTEXT_KEY,
            ToolWindowSettingsProvider.DEFAULT_SHOW_WIDGET_CONTEXT
        )
        val prefix = if (showContext) LstCrcBundle.message("widget.context.prefix") else ""

        val currentServiceState = state ?: ToolWindowStateService.getInstance(project).state
        val selectedIndex = currentServiceState.selectedTabIndex
        val openTabs = currentServiceState.openTabs

        val branchDisplayText = when {
            selectedIndex == -1 || openTabs.isEmpty() -> LstCrcBundle.message("tab.name.head")
            selectedIndex >= 0 && selectedIndex < openTabs.size -> {
                val tabInfo = openTabs[selectedIndex]
                (tabInfo.alias ?: tabInfo.branchName).take(20)
            }
            else -> LstCrcBundle.message("plugin.name.short")
        }
        currentText = "$prefix$branchDisplayText"
    }

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        messageBusConnection = project.messageBus.connect(this)
        messageBusConnection?.subscribe(ToolWindowStateService.TOPIC, object : ToolWindowStateService.Companion.ToolWindowStateListener {
            override fun stateChanged(newState: ToolWindowState) {
                updateWidgetText(newState)
                this@LstCrcStatusWidget.statusBar?.updateWidget(ID())
            }
        })
        messageBusConnection?.subscribe(PLUGIN_SETTINGS_CHANGED_TOPIC, object : PluginSettingsChangedListener {
            override fun onSettingsChanged() {
                updateWidgetText(null)
                this@LstCrcStatusWidget.statusBar?.updateWidget(ID())
            }
        })
        updateWidgetText(null)
        this.statusBar?.updateWidget(ID())
    }

    override fun dispose() {
        messageBusConnection?.disconnect()
        messageBusConnection = null
        statusBar = null
    }

    override fun getText(): String = currentText

    override fun getTooltipText(): String = LstCrcBundle.message("widget.tooltip")

    override fun getAlignment(): Float {
        return Component.CENTER_ALIGNMENT
    }

    override fun getClickConsumer(): Consumer<MouseEvent> {
        return Consumer { mouseEvent ->
            val service = ToolWindowStateService.getInstance(project)
            val currentServiceState = service.state
            val openTabs = currentServiceState.openTabs
            val actions = mutableListOf<AnAction>()

            actions.add(object : AnAction(LstCrcBundle.message("tab.name.head")) {
                override fun actionPerformed(e: AnActionEvent) {
                    val toolWindowManager = ToolWindowManager.getInstance(project)
                    val toolWindow = toolWindowManager.getToolWindow(GIT_CHANGES_TOOL_WINDOW_ID) ?: return

                    toolWindow.activate({
                        val contentManager = toolWindow.contentManager
                        // The HEAD tab is identified by being the non-closable tab.
                        val headContent = contentManager.contents.find { !it.isCloseable }

                        if (headContent != null) {
                            contentManager.setSelectedContent(headContent, true)
                            logger.info("Requested UI tab selection for 'HEAD' from status widget.")
                        } else {
                            logger.warn("Could not find HEAD content (non-closable tab) in tool window to select from widget.")
                        }
                    }, true, true)
                }
            })

            openTabs.forEachIndexed { index, tabInfo ->
                val displayName = tabInfo.alias ?: tabInfo.branchName
                actions.add(object : AnAction(displayName) {
                    override fun actionPerformed(e: AnActionEvent) {
                        val branchNameToSelect = tabInfo.branchName
                        val toolWindowManager = ToolWindowManager.getInstance(project)
                        val toolWindow = toolWindowManager.getToolWindow(GIT_CHANGES_TOOL_WINDOW_ID) ?: return

                        toolWindow.activate({
                            val contentManager = toolWindow.contentManager
                            val contentToSelect = contentManager.contents.find { it.getUserData(LstCrcKeys.BRANCH_NAME_KEY) == branchNameToSelect }

                            if (contentToSelect != null) {
                                contentManager.setSelectedContent(contentToSelect, true)
                                logger.info("Requested UI tab selection for '$branchNameToSelect' from status widget.")
                            } else {
                                logger.warn("Could not find content for tab '$branchNameToSelect' in tool window to select from widget.")
                            }
                        }, true, true)
                    }
                })
            }

            actions.add(Separator.getInstance())

            actions.add(object : AnAction(LstCrcBundle.message("widget.action.add.tab")) {
                override fun actionPerformed(e: AnActionEvent) {
                    val toolWindowManager = ToolWindowManager.getInstance(project)
                    val toolWindow = toolWindowManager.getToolWindow(GIT_CHANGES_TOOL_WINDOW_ID) ?: return

                    toolWindow.activate({
                        val selectionTabName = LstCrcBundle.message("tab.name.select.branch")
                        val contentManager: ContentManager = toolWindow.contentManager

                        val existingContent = contentManager.findContent(selectionTabName)
                        if (existingContent != null) {
                            contentManager.setSelectedContent(existingContent, true)
                            logger.info("Add Tab (from widget): Found existing '$selectionTabName' tab and selected it.")
                            return@activate
                        }

                        val uiProvider = GitChangesToolWindow(project, toolWindow.disposable)
                        val stateService = ToolWindowStateService.getInstance(project)
                        val contentFactory = ContentFactory.getInstance()

                        val branchSelectionUi = BranchSelectionPanel(project, project.service<GitService>()) { selectedBranchName: String ->
                            logger.info("Add Tab (from widget callback): Branch '$selectedBranchName' selected.")
                            if (selectedBranchName.isBlank()) {
                                logger.error("Add Tab (from widget callback): selectedBranchName is blank.")
                                return@BranchSelectionPanel
                            }

                            val manager: ContentManager = toolWindow.contentManager
                            val selectionTabContent = manager.findContent(selectionTabName)
                                ?: return@BranchSelectionPanel

                            val existingBranchTab = manager.contents.find { it.getUserData(LstCrcKeys.BRANCH_NAME_KEY) == selectedBranchName }
                            if (existingBranchTab != null) {
                                manager.setSelectedContent(existingBranchTab, true)
                                manager.removeContent(selectionTabContent, true)
                            } else {
                                selectionTabContent.displayName = selectedBranchName
                                selectionTabContent.putUserData(LstCrcKeys.BRANCH_NAME_KEY, selectedBranchName)
                                val newBranchContentView = uiProvider.createBranchContentView(selectedBranchName)
                                selectionTabContent.component = newBranchContentView
                                (newBranchContentView as? LstCrcChangesBrowser)?.requestRefreshData()

                                manager.setSelectedContent(selectionTabContent, true)
                                stateService.addTab(selectedBranchName)

                                val closableTabs = manager.contents.filter { it.isCloseable }.mapNotNull { it.getUserData(LstCrcKeys.BRANCH_NAME_KEY) }
                                val newTabIndex = closableTabs.indexOf(selectedBranchName)
                                if (newTabIndex != -1) {
                                    stateService.setSelectedTab(newTabIndex)
                                }
                            }
                        }

                        val newContent = contentFactory.createContent(branchSelectionUi.getPanel(), selectionTabName, true).apply {
                            isCloseable = true
                        }
                        contentManager.addContent(newContent)
                        contentManager.setSelectedContent(newContent, true)

                    }, true, true)
                }
            })

            val actionGroup = DefaultActionGroup(actions)
            val dataContext = DataManager.getInstance().getDataContext(mouseEvent.component)
            val popup = JBPopupFactory.getInstance().createActionGroupPopup(
                LstCrcBundle.message("widget.popup.title"),
                actionGroup,
                dataContext,
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                true
            )

            // This logic calculates the correct position to show the popup above the status bar widget.
            val component = mouseEvent.component
            val popupSize = popup.content.preferredSize
            val point = Point(0, -popupSize.height)
            popup.show(RelativePoint(component, point))
        }
    }
}
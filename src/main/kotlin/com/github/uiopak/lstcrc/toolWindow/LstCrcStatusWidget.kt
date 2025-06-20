package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.messaging.PLUGIN_SETTINGS_CHANGED_TOPIC
import com.github.uiopak.lstcrc.messaging.PluginSettingsChangedListener
import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.github.uiopak.lstcrc.state.ToolWindowState
import com.github.uiopak.lstcrc.utils.LstCrcKeys
import com.intellij.ide.DataManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.awt.RelativePoint
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

/**
 * A status bar widget that displays the currently active LST-CRC comparison context (e.g., "HEAD"
 * or a branch name). It provides a popup menu for quickly switching between tabs or adding a new one.
 *
 * This widget is designed to be "stateless" regarding its text. The `getText()` method computes the
 * text on-demand by fetching the latest state from the `ToolWindowStateService`.
 */
class LstCrcStatusWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.TextPresentation {

    private var statusBar: StatusBar? = null
    private var messageBusConnection: MessageBusConnection? = null
    private val logger = thisLogger()

    companion object {
        const val ID = "LstCrcStatusWidget"
        private const val GIT_CHANGES_TOOL_WINDOW_ID = "GitChangesView"
    }

    override fun ID(): String = ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        messageBusConnection = project.messageBus.connect(this)

        // The listener's only job is to tell the status bar to re-query our presentation.
        // It does not need to manage any internal state itself.
        messageBusConnection?.subscribe(ToolWindowStateService.TOPIC, object : ToolWindowStateService.Companion.ToolWindowStateListener {
            override fun stateChanged(newState: ToolWindowState) {
                this@LstCrcStatusWidget.statusBar?.updateWidget(ID())
            }
        })
        messageBusConnection?.subscribe(PLUGIN_SETTINGS_CHANGED_TOPIC, object : PluginSettingsChangedListener {
            override fun onSettingsChanged() {
                this@LstCrcStatusWidget.statusBar?.updateWidget(ID())
            }
        })
    }

    override fun dispose() {
        messageBusConnection?.disconnect()
        messageBusConnection = null
        statusBar = null
    }

    /**
     * Computes the widget text on demand. This is the core of the "lazy" or "pull" model,
     * ensuring the displayed text is always up-to-date when the IDE asks for it.
     */
    override fun getText(): String {
        if (project.isDisposed) return ""
        val state = ToolWindowStateService.getInstance(project).state

        val properties = PropertiesComponent.getInstance()
        val showContext = properties.getBoolean(
            ToolWindowSettingsProvider.APP_SHOW_WIDGET_CONTEXT_KEY,
            ToolWindowSettingsProvider.DEFAULT_SHOW_WIDGET_CONTEXT
        )
        val prefix = if (showContext) LstCrcBundle.message("widget.context.prefix") else ""

        val selectedIndex = state.selectedTabIndex
        val openTabs = state.openTabs

        return when {
            selectedIndex == -1 || openTabs.isEmpty() -> LstCrcBundle.message("tab.name.head")
            selectedIndex >= 0 && selectedIndex < openTabs.size -> {
                val tabInfo = openTabs[selectedIndex]
                val displayName = (tabInfo.alias ?: tabInfo.branchName).take(20)
                "$prefix$displayName"
            }
            else -> LstCrcBundle.message("plugin.name.short")
        }
    }


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
                    ToolWindowHelper.openBranchSelectionTab(project, toolWindow)
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
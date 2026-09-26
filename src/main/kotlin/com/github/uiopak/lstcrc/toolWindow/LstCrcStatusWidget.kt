package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.messaging.TOOL_WINDOW_STATE_TOPIC
import com.github.uiopak.lstcrc.messaging.ToolWindowStateListener
import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.github.uiopak.lstcrc.state.TabInfo
import com.github.uiopak.lstcrc.state.ToolWindowState
import com.github.uiopak.lstcrc.state.displayName
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Consumer
import java.awt.Component
import java.awt.Point
import java.awt.event.MouseEvent


class LstCrcStatusWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = LstCrcStatusWidget.ID
    override fun getDisplayName(): String = LstCrcBundle.message("widget.display.name")
    override fun isAvailable(project: Project): Boolean = true
    override fun createWidget(project: Project): StatusBarWidget = LstCrcStatusWidget(project)
    // disposeWidget and canBeEnabledOn use default implementations from StatusBarWidgetFactory
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

    companion object {
        const val ID = "LstCrcStatusWidget"

        fun refresh(project: Project) {
            WindowManager.getInstance().getStatusBar(project)?.updateWidget(ID)
        }
    }

    override fun ID(): String = ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar

        // The listener's only job is to tell the status bar to re-query our presentation.
        // The connection is disposed together with the widget.
        project.messageBus.connect(this).subscribe(TOOL_WINDOW_STATE_TOPIC, object : ToolWindowStateListener {
            override fun stateChanged(newState: ToolWindowState) {
                this@LstCrcStatusWidget.statusBar?.updateWidget(ID())
            }
        })
    }

    override fun dispose() {
        statusBar = null
    }

    /**
     * Computes the widget text on demand. This is the core of the "lazy" or "pull" model,
     * ensuring the displayed text is always up to date when the IDE asks for it.
     */
    override fun getText(): String {
        if (project.isDisposed) return ""
        // Read the live selection rather than `state`, which deep-copies all tabs on every status bar repaint.
        val stateService = project.service<ToolWindowStateService>()
        val selectedTab = stateService.getSelectedTabInfo() ?: return LstCrcBundle.message(
            if (stateService.isHeadSelected()) "tab.name.head" else "plugin.name.short"
        )
        val prefix = if (ToolWindowSettingsProvider.isShowWidgetContext()) LstCrcBundle.message("widget.context.prefix") else ""
        return prefix + selectedTab.displayName.take(20)
    }


    override fun getTooltipText(): String = LstCrcBundle.message("widget.tooltip")

    override fun getAlignment(): Float {
        return Component.CENTER_ALIGNMENT
    }

    override fun getClickConsumer(): Consumer<MouseEvent> {
        return Consumer { mouseEvent ->
            val service = project.service<ToolWindowStateService>()
            val currentServiceState = service.state
            val actionGroup = DefaultActionGroup(createPopupActions(currentServiceState.openTabs))
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

    private fun createPopupActions(openTabs: List<TabInfo>): List<AnAction> = buildList {
        add(popupAction(LstCrcBundle.message("tab.name.head")) { selectToolWindowContent(ToolWindowHelper::findHeadContent) })
        openTabs.forEach { tabInfo ->
            add(popupAction(tabInfo.displayName) {
                selectToolWindowContent { ToolWindowHelper.findContentByBranchName(it, tabInfo.branchName) }
            })
        }
        add(Separator.getInstance())
        add(popupAction(LstCrcBundle.message("widget.action.add.tab")) {
            ToolWindowHelper.activateToolWindow(project) { toolWindow -> ToolWindowHelper.openBranchSelectionTab(project, toolWindow) }
        })
    }

    private fun popupAction(text: String, perform: () -> Unit): AnAction = object : AnAction(text) {
        override fun actionPerformed(e: AnActionEvent) = perform()
    }

    /** Activates the tool window and selects the tab [findContent] returns, if any. */
    private fun selectToolWindowContent(findContent: (ContentManager) -> Content?) {
        ToolWindowHelper.activateToolWindow(project) { toolWindow ->
            val contentManager = toolWindow.contentManager
            findContent(contentManager)?.let { contentManager.setSelectedContent(it, true) }
        }
    }
}

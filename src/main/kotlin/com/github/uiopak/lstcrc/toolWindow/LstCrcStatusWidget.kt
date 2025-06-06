package com.github.uiopak.lstcrc.toolWindow

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.content.ContentFactory
import com.intellij.util.Consumer
import java.awt.event.MouseEvent
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.github.uiopak.lstcrc.state.TabInfo // Actual TabInfo
import com.github.uiopak.lstcrc.state.ToolWindowState // Actual ToolWindowState
import com.intellij.util.messages.MessageBusConnection
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.ide.DataManager
import java.awt.Component // Import for Component.CENTER_ALIGNMENT
import com.github.uiopak.lstcrc.utils.LstCrcKeys
// Assuming OpenBranchSelectionTabAction is in this package or needs specific import
// If it's in the same package, direct usage is fine. If not, add:
// import com.github.uiopak.lstcrc.toolWindow.OpenBranchSelectionTabAction


class LstCrcStatusWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = "LstCrcStatusWidgetFactory" // Potentially use LstCrcStatusWidget.ID constant
    override fun getDisplayName(): String = "LST-CRC Status Widget"
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
    private var currentText: String = "LST-CRC" // Initialize with default
    private val logger = thisLogger()

    companion object {
        const val ID = "LstCrcStatusWidget"
        // Tool Window ID - should match the one in plugin.xml and where OpenBranchSelectionTabAction is added
        private const val GIT_CHANGES_TOOL_WINDOW_ID = "GitChangesView"
    }

    override fun ID(): String = ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    private fun updateWidgetText(state: ToolWindowState?) {
        val currentServiceState = state ?: ToolWindowStateService.getInstance(project).state
        val selectedIndex = currentServiceState.selectedTabIndex
        val openTabs = currentServiceState.openTabs

        currentText = when {
            openTabs.isEmpty() -> "LST-CRC"
            selectedIndex == -1 -> "HEAD"
            selectedIndex >= 0 && selectedIndex < openTabs.size -> openTabs[selectedIndex].branchName.take(20) // Truncate for display
            else -> "LST-CRC" // Default or error case
        }
    }

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        messageBusConnection = project.messageBus.connect(this) // `this` is the disposable
        messageBusConnection?.subscribe(ToolWindowStateService.TOPIC, object : ToolWindowStateService.Companion.ToolWindowStateListener {
            override fun stateChanged(newState: ToolWindowState) {
                updateWidgetText(newState)
                this@LstCrcStatusWidget.statusBar?.updateWidget(ID())
            }
        })
        updateWidgetText(null) // Initial text update
        this.statusBar?.updateWidget(ID())
    }

    override fun dispose() {
        messageBusConnection?.disconnect()
        messageBusConnection = null
        statusBar = null
    }

    override fun getText(): String = currentText

    override fun getTooltipText(): String = "LST-CRC: Click to switch or open tab"

    override fun getAlignment(): Float {
        return Component.CENTER_ALIGNMENT
    }

    override fun getClickConsumer(): Consumer<MouseEvent> {
        return Consumer { mouseEvent ->
            val service = ToolWindowStateService.getInstance(project)
            // Get the latest state directly from the service for the popup
            val currentServiceState = service.state
            val openTabs = currentServiceState.openTabs
            val actions = mutableListOf<AnAction>()

            openTabs.forEachIndexed { index, tabInfo ->
                actions.add(object : AnAction(tabInfo.branchName) { // tabInfo is now actual TabInfo
                    override fun actionPerformed(e: AnActionEvent) {
                        service.setSelectedTab(index) // Service method handles state update and publishing
                    }
                })
            }

            actions.add(Separator.getInstance())

            actions.add(object : AnAction("+ Add Tab") {
                override fun actionPerformed(e: AnActionEvent) {
                    // 'project' is a constructor parameter of LstCrcStatusWidget
                    // 'statusBar' is a member property initialized in install()

                    val toolWindowManager = ToolWindowManager.getInstance(project)
                    val toolWindow = toolWindowManager.getToolWindow(GIT_CHANGES_TOOL_WINDOW_ID)
                    if (toolWindow == null) {
                        logger.error("Could not find ToolWindow: $GIT_CHANGES_TOOL_WINDOW_ID")
                        return // Abort if tool window not found
                    }

                    toolWindow.activate(null, true, true) // Activate and focus the tool window

                    // Explicit version for diagnostics as requested
                    val key: com.intellij.openapi.util.Key<com.github.uiopak.lstcrc.toolWindow.OpenBranchSelectionTabAction> = LstCrcKeys.OPEN_BRANCH_SELECTION_ACTION_KEY
                    val retrievedAction: com.github.uiopak.lstcrc.toolWindow.OpenBranchSelectionTabAction? =
                        (toolWindow as com.intellij.openapi.util.UserDataHolder).getUserData(key)
                    val openBranchAction = retrievedAction
                    // val openBranchAction = toolWindow.getUserData(LstCrcKeys.OPEN_BRANCH_SELECTION_ACTION_KEY) // Original simpler call


                    if (openBranchAction != null) {
                        val contextComponent = statusBar?.component ?: toolWindow.component
                        val dataContext = DataManager.getInstance().getDataContext(contextComponent)
                        val event = AnActionEvent.createFromDataContext(ActionPlaces.STATUS_BAR_PLACE, null, dataContext)

                        openBranchAction.actionPerformed(event) // This should now work as openBranchAction is correctly typed
                        logger.info("Triggered OpenBranchSelectionTabAction from status bar widget via UserData.")
                    } else {
                        logger.warn("OpenBranchSelectionTabAction not found in tool window UserData for key LSTCRC.OpenBranchSelectionAction. Cannot open 'Add Tab' UI from status bar widget.")
                    }
                }
            })

            val actionGroup = DefaultActionGroup(actions)
            // Pass the mouse event's data context to the popup factory
            val popup = JBPopupFactory.getInstance().createActionGroupPopup(
                "LST-CRC Actions", // Title of the popup
                actionGroup,
                DataManager.getInstance().getDataContext(mouseEvent.component), // Use component from mouse event for DataContext
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                true // showSeparators
            )
            popup.show(RelativePoint(mouseEvent))
        }
    }
}

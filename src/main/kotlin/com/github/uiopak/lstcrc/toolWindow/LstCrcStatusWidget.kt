package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.services.GitService
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.github.uiopak.lstcrc.state.TabInfo
import com.github.uiopak.lstcrc.state.ToolWindowState
import com.intellij.ide.DataManager
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
import java.awt.event.MouseEvent


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
            selectedIndex == -1 || openTabs.isEmpty() -> "HEAD"
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

            // --- START FIX: Manually add the "HEAD" action ---
            actions.add(object : AnAction("HEAD") {
                override fun actionPerformed(e: AnActionEvent) {
                    val toolWindowManager = ToolWindowManager.getInstance(project)
                    val toolWindow = toolWindowManager.getToolWindow(GIT_CHANGES_TOOL_WINDOW_ID)

                    if (toolWindow == null) {
                        logger.error("Could not find ToolWindow: $GIT_CHANGES_TOOL_WINDOW_ID when trying to switch to HEAD tab from widget.")
                        return
                    }

                    toolWindow.activate({
                        val contentManager = toolWindow.contentManager
                        // The HEAD tab is the one that is NOT closable.
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
            // --- END FIX ---

            openTabs.forEachIndexed { index, tabInfo ->
                actions.add(object : AnAction(tabInfo.branchName) { // tabInfo is now actual TabInfo
                    override fun actionPerformed(e: AnActionEvent) {
                        val branchNameToSelect = tabInfo.branchName
                        val toolWindowManager = ToolWindowManager.getInstance(project)
                        val toolWindow = toolWindowManager.getToolWindow(GIT_CHANGES_TOOL_WINDOW_ID)

                        if (toolWindow == null) {
                            logger.error("Could not find ToolWindow: $GIT_CHANGES_TOOL_WINDOW_ID when trying to switch tab from widget.")
                            return
                        }

                        // Activate the tool window, which will create it if needed.
                        toolWindow.activate({
                            // This runnable runs after activation is complete
                            val contentManager = toolWindow.contentManager
                            val contentToSelect = contentManager.contents.find { it.displayName == branchNameToSelect }

                            if (contentToSelect != null) {
                                contentManager.setSelectedContent(contentToSelect, true) // true for focus
                                logger.info("Requested UI tab selection for '$branchNameToSelect' from status widget.")
                            } else {
                                logger.warn("Could not find content for tab '$branchNameToSelect' in tool window to select from widget.")
                            }
                        }, true, true)
                    }
                })
            }

            actions.add(Separator.getInstance())

            // THIS IS THE CORRECTED ACTION
            actions.add(object : AnAction("+ Add Tab") {
                override fun actionPerformed(e: AnActionEvent) {
                    val toolWindowManager = ToolWindowManager.getInstance(project)
                    val toolWindow = toolWindowManager.getToolWindow(GIT_CHANGES_TOOL_WINDOW_ID)
                    if (toolWindow == null) {
                        logger.error("Could not find ToolWindow: $GIT_CHANGES_TOOL_WINDOW_ID")
                        return
                    }

                    // Ensure the tool window is visible and ready.
                    toolWindow.activate({
                        // The logic from OpenBranchSelectionTabAction is now replicated here,
                        // removing the fragile dependency on UserData.
                        val selectionTabName = "Select Branch"
                        val contentManager: ContentManager = toolWindow.contentManager

                        val existingContent = contentManager.findContent(selectionTabName)
                        if (existingContent != null) {
                            contentManager.setSelectedContent(existingContent, true)
                            logger.info("Add Tab (from widget): Found existing '$selectionTabName' tab and selected it.")
                            return@activate
                        }

                        // We need a GitChangesToolWindow instance to create the final view.
                        // We can create it on-the-fly here.
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

                            val existingBranchTab = manager.contents.find { it.displayName == selectedBranchName }
                            if (existingBranchTab != null) {
                                manager.setSelectedContent(existingBranchTab, true)
                                manager.removeContent(selectionTabContent, true)
                            } else {
                                selectionTabContent.displayName = selectedBranchName
                                val newBranchContentView = uiProvider.createBranchContentView(selectedBranchName)
                                selectionTabContent.component = newBranchContentView
                                (newBranchContentView as? ChangesTreePanel)?.requestRefreshData()

                                manager.setSelectedContent(selectionTabContent, true)
                                stateService.addTab(selectedBranchName)

                                val closableTabs = manager.contents.filter { it.isCloseable }.map { it.displayName }
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
            val popup = JBPopupFactory.getInstance().createActionGroupPopup(
                "LST-CRC Actions",
                actionGroup,
                DataManager.getInstance().getDataContext(mouseEvent.component),
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                true
            )
            popup.show(RelativePoint(mouseEvent))
        }
    }
}
package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.services.CategorizedChanges
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffAction
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.SimpleAsyncChangesBrowser
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiManager
import com.intellij.ui.JBColor
import com.intellij.ui.PopupHandler
import com.intellij.util.ui.JBUI
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import java.awt.BorderLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * The main UI component for displaying the tree of file changes for a specific branch comparison.
 * It extends [SimpleAsyncChangesBrowser] but provides highly customized mouse click handling
 * based on user settings, and integrates with the plugin's data refresh lifecycle.
 */
class LstCrcChangesBrowser(
    private val project: Project,
    private val targetBranchToCompare: String,
    parentDisposable: Disposable
) : SimpleAsyncChangesBrowser(project, false, true), Disposable, GitRepositoryChangeListener {

    private val logger = thisLogger()
    private var refreshDebounceTimer: Timer? = null
    private var isInitialLoad = true

    /** Helper class to manage the state for detecting single vs. double clicks. */
    private class ClickState {
        var timer: Timer? = null
        var pendingChange: Change? = null
        var pendingPath: javax.swing.tree.TreePath? = null
        var actionHasFiredForPath: javax.swing.tree.TreePath? = null

        fun clear() {
            timer?.stop()
            timer = null
            pendingChange = null
            pendingPath = null
            actionHasFiredForPath = null
        }
    }
    private val leftClickState = ClickState()
    private val middleClickState = ClickState()
    private val rightClickState = ClickState()

    init {
        viewer.emptyText.text = LstCrcBundle.message("changes.browser.loading")
        project.messageBus.connect(this).subscribe(GitRepository.GIT_REPO_CHANGE, this)
        com.intellij.openapi.util.Disposer.register(parentDisposable, this)

        // The base class adds a border to its scroll pane, and the tool window content manager also adds one,
        // creating a "double border" effect. Removing the inner border lets the tool window manage it correctly.
        setViewerBorder(JBUI.Borders.empty())

        // Disable default click/key handlers to install our own custom configurable versions.
        viewer.setDoubleClickAndEnterKeyHandler {}

        // Remove the default popup handler installed by the base class. This is critical to preventing
        // an empty context menu on right-click, as simply overriding createPopupMenuActions() is not enough.
        viewer.mouseListeners.filterIsInstance<PopupHandler>().forEach {
            viewer.removeMouseListener(it)
            logger.debug("Removed a default PopupHandler to prevent empty context menu.")
        }

        // Install a single master listener to gain full control over all mouse clicks.
        viewer.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val row = viewer.getClosestRowForLocation(e.x, e.y)
                if (row == -1) return
                val bounds = viewer.getRowBounds(row)
                if (bounds == null || e.y < bounds.y || e.y >= bounds.y + bounds.height) {
                    return
                }

                val path = viewer.getPathForRow(row) ?: return
                val change = (path.lastPathComponent as? ChangesBrowserNode<*>)?.userObject as? Change ?: return

                when {
                    SwingUtilities.isLeftMouseButton(e) -> {
                        middleClickState.clear()
                        rightClickState.clear()
                        handleGenericClick(e, change, path, getSingleClickAction(), getDoubleClickAction(), leftClickState)
                    }
                    SwingUtilities.isMiddleMouseButton(e) -> {
                        leftClickState.clear()
                        rightClickState.clear()
                        handleGenericClick(e, change, path, getMiddleClickAction(), getDoubleMiddleClickAction(), middleClickState)
                    }
                    SwingUtilities.isRightMouseButton(e) -> {
                        if (isContextMenuEnabled()) {
                            showContextMenu(e)
                        } else {
                            leftClickState.clear()
                            middleClickState.clear()
                            handleGenericClick(e, change, path, getRightClickAction(), getDoubleRightClickAction(), rightClickState)
                        }
                    }
                }
            }
        })

        // Setup toolbar border to appear on scroll, which is the idiomatic UI for tool windows.
        val scrollPane = viewerScrollPane

        // To find the correct component to apply the border to, we must navigate the layout of the base class.
        // The full toolbar is inside a top panel, which is at the NORTH position of the main layout.
        val mainLayout = this.layout as? BorderLayout
        val topPanel = mainLayout?.getLayoutComponent(BorderLayout.NORTH) as? JPanel

        // Inside the top panel, the full toolbar (TreeActionsToolbarPanel) is at the CENTER position.
        val fullToolbarComponent = topPanel?.let {
            (it.layout as? BorderLayout)?.getLayoutComponent(BorderLayout.CENTER) as? JComponent
        }


        if (fullToolbarComponent != null) {
            // This is the standard 1px separator border used across the IDE for toolbars.
            val bottomBorder = JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0)

            // This function checks the scroll position and applies or removes the border.
            val updateToolbarBorder = {
                val verticalScrollBar = scrollPane.verticalScrollBar
                // A border is needed if the scrollbar is visible and not at the very top.
                val needsBorder = verticalScrollBar.isVisible && verticalScrollBar.value > 0
                fullToolbarComponent.border = if (needsBorder) bottomBorder else JBUI.Borders.empty()
            }

            // Set the initial border state. Using invokeLater ensures the layout is complete
            // and scrollbar visibility is correctly determined.
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed) {
                    updateToolbarBorder()
                }
            }

            // Listen for scroll events to update the border dynamically.
            scrollPane.verticalScrollBar.addAdjustmentListener {
                updateToolbarBorder()
            }

            // Also listen to component resize events, as this can affect scrollbar visibility.
            scrollPane.addComponentListener(object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent?) {
                    updateToolbarBorder()
                }
            })
        } else {
            logger.warn("Could not find full toolbar component; cannot apply dynamic toolbar border.")
        }
    }

    /**
     * Override to return an empty list, completely disabling the default right-click context menu.
     * This is a secondary measure; the primary is removing the `PopupHandler` listener in the init block.
     */
    override fun createPopupMenuActions(): MutableList<AnAction> {
        return mutableListOf()
    }

    private fun handleGenericClick(
        e: MouseEvent,
        change: Change,
        path: javax.swing.tree.TreePath,
        singleClickAction: String,
        doubleClickAction: String,
        clickState: ClickState
    ) {
        if (viewer.selectionPath != path) {
            viewer.selectionPath = path
        }
        viewer.requestFocusInWindow()

        // If double-click is disabled, we can fire the single-click action immediately.
        if (doubleClickAction == "NONE") {
            clickState.clear()
            if (e.clickCount == 1 && singleClickAction != "NONE") {
                performConfiguredAction(change, singleClickAction)
            }
            return
        }

        // This logic handles the case where double-click is enabled.
        // On the first click, we start a timer. If it expires, we fire the single-click action.
        if (e.clickCount == 1) {
            if (clickState.pendingPath != path || clickState.actionHasFiredForPath != null) {
                clickState.clear()
            }
            clickState.pendingChange = change
            clickState.pendingPath = path
            clickState.timer?.stop()
            // If the timer expires, it was a single click.
            clickState.timer = Timer(ToolWindowSettingsProvider.getUserDoubleClickDelayMs()) {
                val sChange = clickState.pendingChange
                val sPath = clickState.pendingPath
                clickState.clear()

                if (sChange != null && sPath != null && singleClickAction != "NONE") {
                    performConfiguredAction(sChange, singleClickAction)
                    clickState.actionHasFiredForPath = sPath
                }
            }.apply { isRepeats = false; start() }
        } else if (e.clickCount >= 2) {
            // This is a double-click. Cancel any pending single-click action and fire the double-click one.
            if (clickState.actionHasFiredForPath == path) {
                clickState.actionHasFiredForPath = null
                clickState.timer?.stop()
                return
            }
            if (clickState.pendingPath == path) {
                clickState.clear()
            }
            performConfiguredAction(change, doubleClickAction)
            clickState.actionHasFiredForPath = null
        }
    }

    private fun performConfiguredAction(change: Change, actionType: String) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            when (actionType) {
                ToolWindowSettingsProvider.ACTION_OPEN_DIFF -> openDiff(listOf(change))
                ToolWindowSettingsProvider.ACTION_OPEN_SOURCE -> openSource(change)
                ToolWindowSettingsProvider.ACTION_SHOW_IN_PROJECT_TREE -> {
                    if (change.type != Change.Type.DELETED) {
                        showInProjectTree(change)
                    }
                }
            }
        }
    }

    private fun openDiff(changes: List<Change>) {
        if (changes.isNotEmpty()) {
            ShowDiffAction.showDiffForChange(project, changes)
        }
    }

    private fun getFileFromChange(change: Change): VirtualFile? {
        // For deleted files, `before` is the only valid revision. For all others, `after` is preferred.
        return change.afterRevision?.file?.virtualFile ?: change.beforeRevision?.file?.virtualFile
    }

    private fun showInProjectTree(change: Change) {
        if (change.type == Change.Type.DELETED) return

        val fileToSelect = getFileFromChange(change)
        if (fileToSelect != null && fileToSelect.isValid) {
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW)
            toolWindow?.activate({
                val projectView = ProjectView.getInstance(project)
                val psiFile = PsiManager.getInstance(project).findFile(fileToSelect)
                val elementToSelect: Any = psiFile ?: fileToSelect

                projectView.select(elementToSelect, fileToSelect, true)
            }, true)
        } else {
            val pathForMessage = (change.afterRevision?.file ?: change.beforeRevision?.file)?.path ?: LstCrcBundle.message("changes.browser.open.source.error.unknown.path")
            Messages.showWarningDialog(project, LstCrcBundle.message("changes.browser.select.file.error.message", pathForMessage), LstCrcBundle.message("changes.browser.select.file.error.title"))
        }
    }

    private fun openSource(change: Change) {
        val fileToOpen = getFileFromChange(change)
        if (fileToOpen != null && fileToOpen.isValid && !fileToOpen.isDirectory) {
            OpenFileDescriptor(project, fileToOpen).navigate(true)
        } else {
            val pathForMessage = (change.afterRevision?.file ?: change.beforeRevision?.file)?.path ?: LstCrcBundle.message("changes.browser.open.source.error.unknown.path")
            Messages.showWarningDialog(project, LstCrcBundle.message("changes.browser.open.source.error.message", pathForMessage), LstCrcBundle.message("changes.browser.open.source.error.title"))
        }
    }

    /**
     * Updates the browser with a new set of changes, preserving the user's scroll and expansion state.
     */
    fun displayChanges(categorizedChanges: CategorizedChanges?, forBranchName: String) {
        if (forBranchName != targetBranchToCompare) {
            return
        }
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater

            val changes = categorizedChanges?.allChanges ?: emptyList()
            val hasChanges = changes.isNotEmpty()

            viewer.emptyText.text = when {
                categorizedChanges == null -> LstCrcBundle.message("changes.browser.error.loading", forBranchName)
                !hasChanges -> LstCrcBundle.message("changes.browser.no.changes", forBranchName)
                else -> LstCrcBundle.message("changes.browser.no.changes.filtered")
            }

            // On the very first load, reset the tree to a default expanded state.
            // On subsequent refreshes, use our custom strategy to preserve state but expand new nodes.
            val strategy = if (isInitialLoad && hasChanges) {
                isInitialLoad = false
                ChangesTree.ALWAYS_RESET
            } else {
                ExpandNewNodesStateStrategy()
            }
            setChangesToDisplay(changes, strategy)
        }
    }

    /**
     * Initiates a refresh of the data for this browser's target branch.
     */
    fun requestRefreshData() {
        logger.debug("UI_REFRESH: Browser for '$targetBranchToCompare' is requesting a data refresh.")
        project.service<ToolWindowStateService>().refreshDataForCurrentSelection()
    }



    override fun repositoryChanged(repository: GitRepository) {
        if (repository.project == this.project) {
            logger.debug("GIT_REPO_CHANGE: repositoryChanged event received in browser, triggering debounced refresh.")
            triggerDebouncedDataRefresh()
        }
    }

    private fun triggerDebouncedDataRefresh() {
        refreshDebounceTimer?.stop()
        refreshDebounceTimer = Timer(100, null).apply {
            addActionListener {
                ApplicationManager.getApplication().invokeLater {
                    if (!project.isDisposed) {
                        requestRefreshData()
                    }
                }
            }
            isRepeats = false
        }
        refreshDebounceTimer?.start()
    }

    override fun dispose() {
        shutdown()
        refreshDebounceTimer?.stop()
        leftClickState.clear()
        middleClickState.clear()
        rightClickState.clear()
        logger.info("LstCrcChangesBrowser for branch '$targetBranchToCompare' disposed.")
    }

    private fun createContextMenuAction(
        titleKey: String,
        action: (List<Change>) -> Unit,
        enabledCondition: (List<Change>) -> Boolean = { it.isNotEmpty() }
    ): AnAction {
        return object : DumbAwareAction(LstCrcBundle.message(titleKey)) {
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = enabledCondition(selectedChanges)
            }
            override fun actionPerformed(e: AnActionEvent) = action(selectedChanges)
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        }
    }

    private fun showContextMenu(e: MouseEvent) {
        val selectedChanges = this.selectedChanges
        if (selectedChanges.isEmpty()) return

        val group = DefaultActionGroup()
        group.add(createContextMenuAction("context.menu.show.diff", this::openDiff))

        group.add(createContextMenuAction("context.menu.open.source",
            action = { changes -> openSource(changes.first()) },
            enabledCondition = { it.size == 1 }
        ))

        group.add(createContextMenuAction("context.menu.show.project.tree",
            action = { changes -> showInProjectTree(changes.first()) },
            enabledCondition = { it.size == 1 && it.first().type != Change.Type.DELETED }
        ))

        val popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.TOOLWINDOW_POPUP, group)
        popupMenu.component.show(e.component, e.x, e.y)
    }


    private fun isContextMenuEnabled(): Boolean = ToolWindowSettingsProvider.isContextMenuEnabled()
    private fun getSingleClickAction(): String = ToolWindowSettingsProvider.getSingleClickAction()
    private fun getDoubleClickAction(): String = ToolWindowSettingsProvider.getDoubleClickAction()
    private fun getMiddleClickAction(): String = ToolWindowSettingsProvider.getMiddleClickAction()
    private fun getDoubleMiddleClickAction(): String = ToolWindowSettingsProvider.getDoubleMiddleClickAction()
    private fun getRightClickAction(): String = ToolWindowSettingsProvider.getRightClickAction()
    private fun getDoubleRightClickAction(): String = ToolWindowSettingsProvider.getDoubleRightClickAction()
}
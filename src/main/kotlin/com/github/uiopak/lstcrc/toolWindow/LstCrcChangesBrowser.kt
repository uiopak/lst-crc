package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.services.CategorizedChanges
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.github.uiopak.lstcrc.utils.getTreePathForMouseCoordinates
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffAction
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.openapi.vcs.changes.ui.AsyncChangesTreeModel
import com.intellij.openapi.vcs.vfs.ContentRevisionVirtualFile
import java.awt.Color
import com.intellij.ui.FileColorManager
import com.intellij.ui.JBColor
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.PopupHandler
import com.intellij.util.ui.JBUI
import com.intellij.util.Alarm
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

/**
 * The main UI part for displaying the tree of file changes for a specific branch comparison.
 * It extends [AsyncChangesBrowserBase] to provide a fully custom asynchronous tree model, and
 * highly customized mouse click handling based on user settings.
 */
class LstCrcChangesBrowser(
    private val project: Project,
    private val targetBranchToCompare: String,
    parentDisposable: Disposable
) : AsyncChangesBrowserBase(project, false, true), Disposable, GitRepositoryChangeListener {

    private val logger = thisLogger()
    private val refreshDebounceAlarm = Alarm(this)

    // This field will hold the changes and context for the async tree model builder.
    private var currentChanges: CategorizedChanges? = null

    private val selectedChanges: List<Change>
        get() = VcsTreeModelData.selected(viewer).userObjects(Change::class.java)

    /** Helper class to manage the state for detecting single vs. double clicks. */
    private class ClickState(parentDisposable: Disposable) {
        private val alarm = Alarm(parentDisposable)
        var pendingChange: Change? = null
        var pendingPath: javax.swing.tree.TreePath? = null
        var actionHasFiredForPath: javax.swing.tree.TreePath? = null

        fun schedule(delayMs: Int, action: () -> Unit) {
            alarm.cancelAllRequests()
            alarm.addRequest(action, delayMs)
        }

        fun cancelPending() {
            alarm.cancelAllRequests()
        }

        fun clear() {
            alarm.cancelAllRequests()
            pendingChange = null
            pendingPath = null
            actionHasFiredForPath = null
        }
    }
    private val leftClickState = ClickState(this)
    private val middleClickState = ClickState(this)
    private val rightClickState = ClickState(this)

    init {
        // This is CRITICAL. Unlike SimpleAsyncChangesBrowser, AsyncChangesBrowserBase does not call
        // init() in its constructor, so we must do it to build the component layout.
        init()

        // Set the custom strategy to preserve the tree state while expanding new nodes.
        viewer.treeStateStrategy = ExpandNewNodesStateStrategy()

        viewer.setCellRenderer(
            RepoNodeRenderer(
                project,
                { viewer.isShowFlatten },
                viewer.isHighlightProblems
            )
        )

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

        // Install a single primary listener to gain full control over all mouse clicks.
        viewer.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val path = viewer.getTreePathForMouseCoordinates(e) ?: return
                val change = (path.lastPathComponent as? ChangesBrowserNode<*>)?.userObject as? Change ?: return

                when {
                    SwingUtilities.isRightMouseButton(e) && ToolWindowSettingsProvider.isContextMenuEnabled() -> showContextMenu(e)
                    else -> dispatchClickAction(e, change, path)
                }
            }
        })

        // Set up the toolbar border to appear on scroll, which is the idiomatic UI for tool windows.
        val scrollPane = viewerScrollPane

        // To find the correct component to apply the border to, we must navigate the layout of the base class.
        // The full toolbar is inside the top panel, which is at the NORTH position of the main layout.
        val mainLayout = this.layout as? BorderLayout
        val topPanel = mainLayout?.getLayoutComponent(BorderLayout.NORTH) as? JPanel

        // Inside the top panel, the full toolbar (TreeActionsToolbarPanel) is at the CENTER position.
        val fullToolbarComponent = topPanel?.let {
            (it.layout as? BorderLayout)?.getLayoutComponent(BorderLayout.CENTER) as? JComponent
        }


        if (fullToolbarComponent != null) {
            // This is the standard 1 px separator border used across the IDE for toolbars.
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

    override fun createToolbarActions(): MutableList<AnAction> {
        val actions = super.createToolbarActions().toMutableList()
        // Find the "Group by" action and insert our action right after it.
        val groupByActionIndex = actions.indexOfFirst { it.javaClass.simpleName == "GroupByActionGroup" }

        val configureAction = ShowRepoComparisonInfoAction()

        if (groupByActionIndex != -1) {
            actions.add(groupByActionIndex + 1, configureAction)
        } else {
            actions.add(configureAction)
        }
        return actions
    }


    /**
     * Override to return an empty list, completely disabling the default right-click context menu.
     * This is a secondary measure; the primary is removing the `PopupHandler` listener in the init block.
     */
    override fun createPopupMenuActions(): MutableList<AnAction> {
        return mutableListOf()
    }

    override fun createTreeList(project: Project, showCheckboxes: Boolean, highlightProblems: Boolean): AsyncChangesTree {
        return LstCrcAsyncChangesTree(project, showCheckboxes, highlightProblems)
    }

    /**
     * Custom AsyncChangesTree that enables deleted file background coloring.
     * Extends the standard tree to provide custom colors for deleted files while
     * preserving all native coloring for other file types.
     */
    private inner class LstCrcAsyncChangesTree(
        project: Project,
        showCheckboxes: Boolean,
        highlightProblems: Boolean
    ) : AsyncChangesTree(project, showCheckboxes, highlightProblems) {

        override val changesTreeModel: AsyncChangesTreeModel
            get() = this@LstCrcChangesBrowser.changesTreeModel

        override fun isFileColorsEnabled(): Boolean = true

        override fun getFileColorForPath(path: javax.swing.tree.TreePath): Color? {
            // First try native pipeline for existing files (which have VirtualFiles)
            val defaultColor = super.getFileColorForPath(path)
            if (defaultColor != null) return defaultColor

            // Custom logic for deleted files (which don't have VirtualFiles)
            val node = path.lastPathComponent as? ChangesBrowserNode<*> ?: return null
            val change = node.userObject as? Change ?: return null
            if (change.type == Change.Type.DELETED) {
                // Use scope-based coloring for deleted files
                val scopeColorForDeletedFile = getScopeColorForDeletedFile(project)
                return scopeColorForDeletedFile
            }
            return null
        }

        private fun getScopeColorForDeletedFile(project: Project): Color {
            // Use the existing DeletedFilesScope directly for zero memory overhead
            val fileColorManager = FileColorManager.getInstance(project)

            // Get the color configured for the "LSTCRC.Deleted" scope
            // This uses your existing DeletedFilesScope infrastructure
            val deletedScopeColor = fileColorManager.getScopeColor("LSTCRC.Deleted")
            if (deletedScopeColor != null) {
                return deletedScopeColor
            }

            // Fallback to default rose color if scope color is not configured
            return JBColor.namedColor("FileColor.Rose", JBColor(Color(255, 235, 236), Color(71, 43, 43)))
        }


    }

    override val changesTreeModel: AsyncChangesTreeModel =
        SimpleAsyncChangesTreeModel.create { userSelectedGroupingFactory ->
            // Revert to the standard TreeModelBuilder. Let it handle all grouping logic.
            // Our custom RepoNodeRenderer will decorate the nodes when the "Group by Repository"
            // policy is active.
            val builder = TreeModelBuilder(project, userSelectedGroupingFactory)
            val changes = currentChanges?.allChanges ?: emptyList()

            if (changes.isNotEmpty()) {
                builder.insertChanges(changes, builder.myRoot)
            }
            builder.build()
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
            clickState.cancelPending()
            // If the timer expires, it was a single click.
            clickState.schedule(ToolWindowSettingsProvider.getUserDoubleClickDelayMs()) {
                val sChange = clickState.pendingChange
                val sPath = clickState.pendingPath
                clickState.clear()

                if (sChange != null && sPath != null && singleClickAction != "NONE") {
                    performConfiguredAction(sChange, singleClickAction)
                    clickState.actionHasFiredForPath = sPath
                }
            }
        } else if (e.clickCount >= 2) {
            // This is a double click. Cancel any pending single-click action and fire the double-click one.
            if (clickState.actionHasFiredForPath == path) {
                clickState.actionHasFiredForPath = null
                clickState.cancelPending()
                return
            }
            if (clickState.pendingPath == path) {
                clickState.clear()
            }
            performConfiguredAction(change, doubleClickAction)
            clickState.actionHasFiredForPath = null
        }
    }

    /**
     * Routes a click event to the correct [handleGenericClick] call based on the mouse button.
     * Shared by the real [MouseAdapter] listener and the test-bridge [invokeConfiguredActionForFile].
     */
    private fun dispatchClickAction(e: MouseEvent, change: Change, path: javax.swing.tree.TreePath) {
        when {
            SwingUtilities.isLeftMouseButton(e) -> {
                middleClickState.clear()
                rightClickState.clear()
                handleGenericClick(e, change, path, ToolWindowSettingsProvider.getSingleClickAction(), ToolWindowSettingsProvider.getDoubleClickAction(), leftClickState)
            }
            SwingUtilities.isMiddleMouseButton(e) -> {
                leftClickState.clear()
                rightClickState.clear()
                handleGenericClick(e, change, path, ToolWindowSettingsProvider.getMiddleClickAction(), ToolWindowSettingsProvider.getDoubleMiddleClickAction(), middleClickState)
            }
            SwingUtilities.isRightMouseButton(e) -> {
                leftClickState.clear()
                middleClickState.clear()
                handleGenericClick(e, change, path, ToolWindowSettingsProvider.getRightClickAction(), ToolWindowSettingsProvider.getDoubleRightClickAction(), rightClickState)
            }
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
        if (change.type == Change.Type.DELETED) {
            val beforeRevision = change.beforeRevision
            if (beforeRevision != null) {
                try {
                    openRevisionSource(beforeRevision)
                } catch (e: Exception) {
                    Messages.showErrorDialog(project, LstCrcBundle.message("changes.browser.open.source.error.message", beforeRevision.file.path), "Error")
                }
            }
            return
        }

        val fileToOpen = getFileFromChange(change)
        if (fileToOpen != null && fileToOpen.isValid && !fileToOpen.isDirectory) {
            OpenFileDescriptor(project, fileToOpen).navigate(true)
            return
        }

        val revisionToOpen = change.afterRevision ?: change.beforeRevision
        if (revisionToOpen != null) {
            try {
                openRevisionSource(revisionToOpen)
                return
            } catch (e: Exception) {
                val pathForMessage = revisionToOpen.file.path
                Messages.showWarningDialog(project, LstCrcBundle.message("changes.browser.open.source.error.message", pathForMessage), LstCrcBundle.message("changes.browser.open.source.error.title"))
                return
            }
        } else {
            val pathForMessage = (change.afterRevision?.file ?: change.beforeRevision?.file)?.path ?: LstCrcBundle.message("changes.browser.open.source.error.unknown.path")
            Messages.showWarningDialog(project, LstCrcBundle.message("changes.browser.open.source.error.message", pathForMessage), LstCrcBundle.message("changes.browser.open.source.error.title"))
        }
    }

    private fun openRevisionSource(revision: ContentRevision) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                ChangesUtil.loadContentRevision(revision)

                ApplicationManager.getApplication().invokeLater {
                    if (project.isDisposed) return@invokeLater
                    val virtualFile = ContentRevisionVirtualFile.create(revision)
                    FileEditorManager.getInstance(project).openFile(virtualFile, true, true)
                }
            } catch (e: Exception) {
                logger.warn("Failed to preload revision-backed file '${revision.file.path}'.", e)
                ApplicationManager.getApplication().invokeLater {
                    if (project.isDisposed) return@invokeLater
                    Messages.showWarningDialog(
                        project,
                        LstCrcBundle.message("changes.browser.open.source.error.message", revision.file.path),
                        LstCrcBundle.message("changes.browser.open.source.error.title")
                    )
                }
            }
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

            val hasChanges = categorizedChanges?.allChanges?.isNotEmpty() ?: false

            viewer.emptyText.text = when {
                categorizedChanges == null -> LstCrcBundle.message("changes.browser.error.loading", forBranchName)
                !hasChanges -> LstCrcBundle.message("changes.browser.no.changes", forBranchName)
                else -> LstCrcBundle.message("changes.browser.no.changes.filtered")
            }

            // Store the changes and trigger an asynchronous rebuild
            currentChanges = categorizedChanges
            viewer.rebuildTree()
        }
    }

    /**
     * Initiates a refresh of the data for this browser's target branch.
     */
    fun requestRefreshData() {
        logger.debug("UI_REFRESH: Browser for '$targetBranchToCompare' is requesting a data refresh.")
        project.service<ToolWindowStateService>().refreshDataForCurrentSelection()
    }

    /**
     * Rebuilds the tree view. Called when a display setting (like showing comparison context) is changed.
     */
    fun rebuildView() {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                viewer.rebuildTree()
            }
        }
    }

    // -- Test bridge helpers: used only by LstCrcUiTestBridge for IDE Starter tests --

    @org.jetbrains.annotations.ApiStatus.Internal
    internal fun debugRenderedRowsSnapshot(): String {
        val renderer = viewer.cellRenderer
        val model = viewer.model
        val rows = mutableListOf<String>()

        for (row in 0 until viewer.rowCount) {
            val path = viewer.getPathForRow(row) ?: continue
            val node = path.lastPathComponent
            val rendered = renderer.getTreeCellRendererComponent(
                viewer,
                node,
                viewer.isRowSelected(row),
                viewer.isExpanded(row),
                model.isLeaf(node),
                row,
                false
            )

            val text = rendered.accessibleContext?.accessibleName
                ?: (rendered as? javax.swing.JLabel)?.text
                ?: rendered.name

            if (!text.isNullOrBlank()) {
                rows.add(text)
            }
        }

        return rows.joinToString("\n")
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    internal fun debugChangeFileNamesSnapshot(): String {
        return currentChanges?.allChanges
            ?.mapNotNull { change ->
                change.afterRevision?.file?.name ?: change.beforeRevision?.file?.name
            }
            ?.distinct()
            ?.joinToString("\n")
            .orEmpty()
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    internal fun invokeConfiguredActionForFile(fileName: String, button: String, clickCount: Int) {
        val path = findPathByFileName(fileName)
            ?: error("Could not find change for file '$fileName' in '$targetBranchToCompare'.")
        val change = ((path.lastPathComponent as? ChangesBrowserNode<*>)?.userObject as? Change)
            ?: error("Could not find change for file '$fileName' in '$targetBranchToCompare'.")

        if (button.equals("RIGHT", ignoreCase = true) && ToolWindowSettingsProvider.isContextMenuEnabled()) {
            error("Context menu is enabled for right click. Query contextMenuActionTitlesForFile() instead of invoking a configured action.")
        }

        val awtButton = when {
            button.equals("LEFT", ignoreCase = true) -> MouseEvent.BUTTON1
            button.equals("MIDDLE", ignoreCase = true) -> MouseEvent.BUTTON2
            button.equals("RIGHT", ignoreCase = true) -> MouseEvent.BUTTON3
            else -> error("Unsupported mouse button '$button'.")
        }
        val event = MouseEvent(
            viewer,
            MouseEvent.MOUSE_CLICKED,
            System.currentTimeMillis(),
            0,
            1,
            1,
            clickCount,
            false,
            awtButton
        )

        dispatchClickAction(event, change, path)
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    internal fun contextMenuActionTitlesForFile(fileName: String): String {
        val change = findChangeByFileName(fileName)
            ?: error("Could not find change for file '$fileName' in '$targetBranchToCompare'.")

        val titles = mutableListOf(
            LstCrcBundle.message("context.menu.show.diff"),
            LstCrcBundle.message("context.menu.open.source")
        )
        if (change.type != Change.Type.DELETED) {
            titles.add(LstCrcBundle.message("context.menu.show.project.tree"))
        }
        return titles.joinToString("|")
    }


    override fun repositoryChanged(repository: GitRepository) {
        if (repository.project == project) {
            logger.debug("GIT_REPO_CHANGE: repositoryChanged event received in browser, triggering debounced refresh.")
            triggerDebouncedDataRefresh()
        }
    }

    private fun triggerDebouncedDataRefresh() {
        refreshDebounceAlarm.cancelAllRequests()
        refreshDebounceAlarm.addRequest({
            if (!project.isDisposed) {
                requestRefreshData()
            }
        }, 100)
    }

    override fun dispose() {
        shutdown()
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
        val changes = this.selectedChanges
        if (changes.isEmpty()) return

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


    private fun findChangeByFileName(fileName: String): Change? {
        return currentChanges?.allChanges?.firstOrNull { change ->
            change.afterRevision?.file?.name == fileName || change.beforeRevision?.file?.name == fileName
        }
    }

    private fun findPathByFileName(fileName: String): javax.swing.tree.TreePath? {
        for (row in 0 until viewer.rowCount) {
            val path = viewer.getPathForRow(row) ?: continue
            val change = (path.lastPathComponent as? ChangesBrowserNode<*>)?.userObject as? Change ?: continue
            if (change.afterRevision?.file?.name == fileName || change.beforeRevision?.file?.name == fileName) {
                return path
            }
        }
        return null
    }
}

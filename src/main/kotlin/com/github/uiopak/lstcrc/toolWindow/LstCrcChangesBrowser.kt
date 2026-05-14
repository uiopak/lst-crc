package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.services.CategorizedChanges
import com.github.uiopak.lstcrc.services.ProjectActiveDiffDataService
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.github.uiopak.lstcrc.messaging.DIFF_DATA_CHANGED_TOPIC
import com.github.uiopak.lstcrc.messaging.ActiveDiffDataChangedListener
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.ide.projectView.ProjectView
import com.intellij.diff.editor.ChainDiffVirtualFile
import com.intellij.diff.editor.DiffEditorTabFilesManager
import com.intellij.openapi.ListSelection
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.EDT
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
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffAction
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain
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
import com.intellij.ui.render.RenderingHelper
import com.intellij.util.ui.JBUI
import javax.swing.plaf.basic.BasicTreeUI
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import java.awt.BorderLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * The main UI part for displaying the tree of file changes for a specific branch comparison.
 * It extends [AsyncChangesBrowserBase] to provide a fully custom asynchronous tree model, and
 * highly customized mouse click handling based on user settings.
 */
@Suppress("JComponentDataProvider")
@OptIn(FlowPreview::class)
class LstCrcChangesBrowser(
    private val project: Project,
    private val targetBranchToCompare: String,
    parentDisposable: Disposable
) : AsyncChangesBrowserBase(project, false, true), Disposable, GitRepositoryChangeListener, UiDataProvider {


    private companion object {
        const val OPEN_SOURCE_ERROR_TITLE_KEY = "changes.browser.open.source.error.title"
        const val OPEN_SOURCE_ERROR_MESSAGE_KEY = "changes.browser.open.source.error.message"
    }

    private data class DiffChangeKey(
        val type: Change.Type,
        val beforePath: String?,
        val beforeRevision: String?,
        val afterPath: String?,
        val afterRevision: String?
    )

    private data class DiffSelectionKey(
        val comparisonTarget: String,
        val changes: List<DiffChangeKey>
    )

    private class ReusableChangeDiffVirtualFile(
        chain: ChangeDiffRequestChain,
        private val diffKey: DiffSelectionKey,
        name: String
    ) : ChainDiffVirtualFile(chain, name) {
        fun matches(otherKey: DiffSelectionKey): Boolean = diffKey == otherKey

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ReusableChangeDiffVirtualFile) return false
            return diffKey == other.diffKey
        }

        override fun hashCode(): Int = diffKey.hashCode()
    }

    private val logger = thisLogger()
    private val debounceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val repositoryChangeSignals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // This field will hold the changes and context for the async tree model builder.
    private var currentChanges: CategorizedChanges? = null

    private val selectedChanges: List<Change>
        get() = VcsTreeModelData.selected(viewer).userObjects(Change::class.java)


    init {
        // This is CRITICAL. Unlike SimpleAsyncChangesBrowser, AsyncChangesBrowserBase does not call
        // init() in its constructor, so we must do it to build the component layout.
        init()

        // Preserve user expansion/collapse state while still revealing newly added nodes.
        viewer.treeStateStrategy = ExpandNewNodesStateStrategy()

        debounceScope.launch {
            repositoryChangeSignals
                .debounce(100.milliseconds)
                .collectLatest {
                    if (!project.isDisposed) {
                        withContext(Dispatchers.EDT) {
                            if (!project.isDisposed) {
                                requestRefreshData()
                            }
                        }
                    }
                }
        }

        viewer.setCellRenderer(
            RepoNodeRenderer(
                project,
                { currentChanges },
                { viewer.isShowFlatten },
                viewer.isHighlightProblems
            )
        )

        viewer.emptyText.text = LstCrcBundle.message("changes.browser.loading")
        
        val connection = project.messageBus.connect(this)
        connection.subscribe(GitRepository.GIT_REPO_CHANGE, this)
        connection.subscribe(DIFF_DATA_CHANGED_TOPIC, object : ActiveDiffDataChangedListener {
            override fun onDiffDataChanged() {
                if (project.isDisposed) return
                val diffDataService = project.service<ProjectActiveDiffDataService>()
                val branchName = diffDataService.activeBranchName ?: "HEAD"
                if (branchName == targetBranchToCompare) {
                    displayChanges(diffDataService.categorizedChanges, branchName)
                }
            }
        })
        
        com.intellij.openapi.util.Disposer.register(parentDisposable, this)

        // The base class adds a border to its scroll pane, and the tool window content manager also adds one,
        // creating a "double border" effect. Removing the inner border lets the tool window manage it correctly.
        setViewerBorder(JBUI.Borders.empty())

        // Custom Enter-key behavior: open diff.
        viewer.addKeyListener(object : java.awt.event.KeyAdapter() {
            override fun keyPressed(e: java.awt.event.KeyEvent) {
                if (e.keyCode == java.awt.event.KeyEvent.VK_ENTER) {
                    val changes = selectedChanges
                    if (changes.isNotEmpty()) {
                        openDiff(changes)
                        e.consume()
                    }
                }
            }
        })

        installConfigurableMouseHandler()
        installContextMenuHandler()
        configureRendererWidthCacheReset()
        configureDynamicToolbarBorder()
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
    @Suppress("JComponentDataProvider")
    private inner class LstCrcAsyncChangesTree(
        project: Project,
        showCheckboxes: Boolean,
        highlightProblems: Boolean
    ) : AsyncChangesTree(project, showCheckboxes, highlightProblems), UiDataProvider {

        init {
            putClientProperty(RenderingHelper.SHRINK_LONG_RENDERER, true)
            putClientProperty(RenderingHelper.SHRINK_LONG_SELECTION, true)
        }


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


    private fun openDiff(changes: List<Change>) {
        if (changes.isEmpty()) return

        val diffKey = DiffSelectionKey(targetBranchToCompare, changes.map { it.toDiffChangeKey() })
        findOpenReusableDiffFile(diffKey)?.let { openDiffFile(it); return }

        val producers = changes.mapNotNull { ChangeDiffRequestProducer.create(project, it) }
        if (producers.size != changes.size) {
            ShowDiffAction.showDiffForChange(project, changes)
            return
        }

        val chain = ChangeDiffRequestChain(ListSelection.createAt(producers, 0))
        val diffFile = ReusableChangeDiffVirtualFile(
            chain = chain,
            diffKey = diffKey,
            name = changes.first().diffFileDisplayName()
        )
        openDiffFile(diffFile)
    }

    private fun findOpenReusableDiffFile(diffKey: DiffSelectionKey): ReusableChangeDiffVirtualFile? {
        return FileEditorManager.getInstance(project).openFiles
            .filterIsInstance<ReusableChangeDiffVirtualFile>()
            .firstOrNull { it.matches(diffKey) }
    }

    private fun openDiffFile(diffFile: ChainDiffVirtualFile) {
        DiffEditorTabFilesManager.getInstance(project).showDiffFile(diffFile, true)
    }

    private fun Change.toDiffChangeKey(): DiffChangeKey {
        return DiffChangeKey(
            type = type,
            beforePath = beforeRevision?.file?.path,
            beforeRevision = beforeRevision?.revisionNumber?.asString(),
            afterPath = afterRevision?.file?.path,
            afterRevision = afterRevision?.revisionNumber?.asString()
        )
    }

    private fun Change.diffFileDisplayName(): String {
        val path = afterRevision?.file?.path ?: beforeRevision?.file?.path
        return path?.substringAfterLast('/')?.substringAfterLast('\\')
            ?: LstCrcBundle.message("context.menu.show.diff")
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
                } catch (_: Exception) {
                    showOpenSourceError(beforeRevision.file.path)
                }
            }
            return
        }

        val fileToOpen = getFileFromChange(change)
        if (fileToOpen != null && fileToOpen.isValid && !fileToOpen.isDirectory) {
            FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, fileToOpen), true)
            return
        }

        val revisionToOpen = change.afterRevision ?: change.beforeRevision
        if (revisionToOpen != null) {
            try {
                openRevisionSource(revisionToOpen)
                return
            } catch (_: Exception) {
                showOpenSourceWarning(revisionToOpen.file.path)
                return
            }
        } else {
            val pathForMessage = (change.afterRevision?.file ?: change.beforeRevision?.file)?.path ?: LstCrcBundle.message("changes.browser.open.source.error.unknown.path")
            showOpenSourceWarning(pathForMessage)
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
                    showOpenSourceWarning(revision.file.path)
                }
            }
        }
    }

    private fun showOpenSourceWarning(path: String) {
        Messages.showWarningDialog(project, LstCrcBundle.message(OPEN_SOURCE_ERROR_MESSAGE_KEY, path), openSourceErrorTitle())
    }

    private fun showOpenSourceError(path: String) {
        Messages.showErrorDialog(project, LstCrcBundle.message(OPEN_SOURCE_ERROR_MESSAGE_KEY, path), openSourceErrorTitle())
    }

    private fun openSourceErrorTitle(): String = LstCrcBundle.message(OPEN_SOURCE_ERROR_TITLE_KEY)

    @Suppress("unused")
    fun currentChangeFileNamesSnapshot(): List<String> {
        return currentChanges?.allChanges
            ?.asSequence()
            ?.mapNotNull { change -> change.afterRevision?.file ?: change.beforeRevision?.file }
            ?.map { it.name }
            ?.distinct()
            ?.toList()
            ?: emptyList()
    }

    @Suppress("unused")
    fun currentLineStatsSnapshot(): List<String> {
        return currentChanges?.lineStatsByChange
            ?.entries
            ?.asSequence()
            ?.map { (key, stats) ->
                val path = key.afterPath ?: key.beforePath ?: ""
                "$path:+${stats.addedLines}/-${stats.removedLines}"
            }
            ?.sorted()
            ?.toList()
            ?: emptyList()
    }

    @Suppress("unused")
    fun invokeTestContextMenuAction(change: Change, actionTitle: String) {
        when (actionTitle) {
            LstCrcBundle.message("context.menu.show.diff") -> openDiff(listOf(change))
            LstCrcBundle.message("context.menu.open.source") -> openSource(change)
            LstCrcBundle.message("context.menu.show.project.tree") -> showInProjectTree(change)
            else -> error("Unsupported context menu action '$actionTitle'.")
        }
    }

    /**
     * Updates the browser with a new set of changes, preserving the user's scroll and expansion state.
     */
    private fun displayChanges(categorizedChanges: CategorizedChanges?, forBranchName: String) {
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

    override fun repositoryChanged(repository: GitRepository) {
        if (repository.project == project) {
            logger.debug("GIT_REPO_CHANGE: repositoryChanged event received in browser, triggering debounced refresh.")
            triggerDebouncedDataRefresh()
        }
    }

    private fun triggerDebouncedDataRefresh() {
        repositoryChangeSignals.tryEmit(Unit)
    }

    override fun dispose() {
        pendingClickJob?.cancel()
        debounceScope.cancel()
        shutdown()
        logger.info("LstCrcChangesBrowser for branch '$targetBranchToCompare' disposed.")
    }

    private var pendingClickJob: kotlinx.coroutines.Job? = null

    private fun installConfigurableMouseHandler() {
        viewer.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                handleMouseClick(e)
            }
        })
    }

    private fun handleMouseClick(e: java.awt.event.MouseEvent) {
        val clickCount = e.clickCount
        val button = e.button

        if (javax.swing.SwingUtilities.isRightMouseButton(e) && ToolWindowSettingsProvider.isContextMenuEnabled()) {
            return
        }

        val path = TreeUtil.getPathForLocation(viewer, e.x, e.y) ?: return
        val change = (path.lastPathComponent as? ChangesBrowserNode<*>)?.userObject as? Change ?: return

        selectPathAndFocus(path)

        val singleAction = when (button) {
            java.awt.event.MouseEvent.BUTTON1 -> ToolWindowSettingsProvider.getSingleClickAction()
            java.awt.event.MouseEvent.BUTTON2 -> ToolWindowSettingsProvider.getMiddleClickAction()
            java.awt.event.MouseEvent.BUTTON3 -> ToolWindowSettingsProvider.getRightClickAction()
            else -> ToolWindowSettingsProvider.ACTION_NONE
        }

        val doubleAction = when (button) {
            java.awt.event.MouseEvent.BUTTON1 -> ToolWindowSettingsProvider.getDoubleClickAction()
            java.awt.event.MouseEvent.BUTTON2 -> ToolWindowSettingsProvider.getDoubleMiddleClickAction()
            java.awt.event.MouseEvent.BUTTON3 -> ToolWindowSettingsProvider.getDoubleRightClickAction()
            else -> ToolWindowSettingsProvider.ACTION_NONE
        }

        if (clickCount == 1) {
            if (singleAction == ToolWindowSettingsProvider.ACTION_NONE) return
            
            // If double action is NONE, fire immediately
            if (doubleAction == ToolWindowSettingsProvider.ACTION_NONE) {
                performConfiguredAction(change, singleAction)
                return
            }

            // Otherwise, delay to see if a double click comes
            val delayMs = ToolWindowSettingsProvider.getUserDoubleClickDelayMs().toLong()
            pendingClickJob?.cancel()
            pendingClickJob = debounceScope.launch {
                kotlinx.coroutines.delay(delayMs.milliseconds)
                withContext(Dispatchers.EDT) {
                    performConfiguredAction(change, singleAction)
                }
            }
        } else if (clickCount == 2) {
            pendingClickJob?.cancel()
            if (doubleAction != ToolWindowSettingsProvider.ACTION_NONE) {
                performConfiguredAction(change, doubleAction)
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

    private fun installContextMenuHandler() {
        // Remove the default empty popup handler that the base class installs.
        viewer.mouseListeners.filterIsInstance<PopupHandler>().forEach {
            viewer.removeMouseListener(it)
            logger.debug("Removed a default PopupHandler to prevent empty context menu.")
        }

        // Install our custom context menu handler
        viewer.addMouseListener(object : PopupHandler() {
            override fun invokePopup(comp: java.awt.Component?, x: Int, y: Int) {
                    if (!ToolWindowSettingsProvider.isContextMenuEnabled()) return

                    val path = TreeUtil.getPathForLocation(viewer, x, y) ?: return
                    if ((path.lastPathComponent as? ChangesBrowserNode<*>)?.userObject as? Change == null) return

                    selectPathAndFocus(path)
                    val changes = selectedChanges
                    if (changes.isEmpty()) return

                    val group = DefaultActionGroup()
                    group.add(createContextMenuAction("context.menu.show.diff", this@LstCrcChangesBrowser::openDiff))
                    group.add(createContextMenuAction("context.menu.open.source",
                        action = { changes -> openSource(changes.first()) },
                        enabledCondition = { it.size == 1 }
                    ))
                    group.add(createContextMenuAction("context.menu.show.project.tree",
                        action = { changes -> showInProjectTree(changes.first()) },
                        enabledCondition = { it.size == 1 && it.first().type != Change.Type.DELETED }
                    ))

                    val popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.TOOLWINDOW_POPUP, group)
                    popupMenu.component.show(comp, x, y)
                }
            })
    }

    private fun configureDynamicToolbarBorder() {
        val fullToolbarComponent = findToolbarComponent()
        if (fullToolbarComponent == null) {
            logger.warn("Could not find full toolbar component; cannot apply dynamic toolbar border.")
            return
        }

        val scrollPane = viewerScrollPane
        val bottomBorder = JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0)
        val updateToolbarBorder = {
            val verticalScrollBar = scrollPane.verticalScrollBar
            val needsBorder = verticalScrollBar.isVisible && verticalScrollBar.value > 0
            fullToolbarComponent.border = if (needsBorder) bottomBorder else JBUI.Borders.empty()
        }

        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                updateToolbarBorder()
            }
        }
        scrollPane.verticalScrollBar.addAdjustmentListener { updateToolbarBorder() }
        scrollPane.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent?) {
                updateToolbarBorder()
            }
        })
    }

    private fun configureRendererWidthCacheReset() {
        val scrollPane = viewerScrollPane
        val resetRendererWidthCache = {
            val treeUI = viewer.ui
            if (treeUI is BasicTreeUI) {
                treeUI.setLeftChildIndent(treeUI.leftChildIndent)
            }
            viewer.revalidate()
            viewer.repaint()
        }

        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                resetRendererWidthCache()
            }
        }

        scrollPane.viewport.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent?) {
                resetRendererWidthCache()
            }

            override fun componentShown(e: ComponentEvent?) {
                resetRendererWidthCache()
            }
        })
    }

    private fun findToolbarComponent(): JComponent? {
        val mainLayout = this.layout as? BorderLayout ?: return null
        val topPanel = mainLayout.getLayoutComponent(BorderLayout.NORTH) as? JPanel ?: return null
        return (topPanel.layout as? BorderLayout)?.getLayoutComponent(BorderLayout.CENTER) as? JComponent
    }

    private fun selectPathAndFocus(path: javax.swing.tree.TreePath) {
        if (viewer.selectionPath != path) {
            viewer.selectionPath = path
        }
        viewer.requestFocusInWindow()
    }

}

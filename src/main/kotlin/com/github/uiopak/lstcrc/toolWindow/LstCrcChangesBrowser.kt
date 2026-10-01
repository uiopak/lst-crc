package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.LstCrcConstants.HEAD
import com.github.uiopak.lstcrc.messaging.ActiveDiffDataChangedListener
import com.github.uiopak.lstcrc.messaging.DIFF_DATA_CHANGED_TOPIC
import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.scopes.DELETED_SCOPE_ID
import com.github.uiopak.lstcrc.services.CategorizedChanges
import com.github.uiopak.lstcrc.services.GitService
import com.github.uiopak.lstcrc.services.ProjectActiveDiffDataService
import com.github.uiopak.lstcrc.services.TextContentRevision
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.github.uiopak.lstcrc.services.resolveCommitHash
import com.intellij.diff.editor.ChainDiffVirtualFile
import com.intellij.diff.editor.DiffEditorTabFilesManager
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.Disposable
import com.intellij.openapi.ListSelection
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffAction
import com.intellij.openapi.vcs.changes.ui.AsyncChangesBrowserBase
import com.intellij.openapi.vcs.changes.ui.AsyncChangesTree
import com.intellij.openapi.vcs.changes.ui.AsyncChangesTreeModel
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.SimpleAsyncChangesTreeModel
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.openapi.vcs.vfs.ContentRevisionVirtualFile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiManager
import com.intellij.ui.FileColorManager
import com.intellij.ui.JBColor
import com.intellij.ui.render.RenderingHelper
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeModelAdapter
import kotlinx.coroutines.CancellationException
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Point
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.util.concurrent.Future
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JViewport
import javax.swing.event.TreeModelListener
import javax.swing.plaf.basic.BasicTreeUI
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

/**
 * The main UI part for displaying the tree of file changes for a specific branch comparison.
 * It extends [AsyncChangesBrowserBase] to provide a fully custom asynchronous tree model, and
 * highly customized mouse click handling based on user settings.
 */
class LstCrcChangesBrowser(
    private val project: Project,
    private val targetBranchToCompare: String,
    parentDisposable: Disposable
) : AsyncChangesBrowserBase(project, false, true), Disposable, UiDataProvider {

    private class ReusableChangeDiffVirtualFile(
        chain: ChangeDiffRequestChain,
        val diffKey: DiffSelectionKey,
        name: String
    ) : ChainDiffVirtualFile(chain, name) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ReusableChangeDiffVirtualFile) return false
            return diffKey == other.diffKey
        }

        override fun hashCode(): Int = diffKey.hashCode()
    }

    private val logger = thisLogger()

    // This field will hold the changes and context for the async tree model builder.
    private var currentChanges: CategorizedChanges? = null
    @Volatile
    private var disposed = false

    private fun isAlive(): Boolean = !disposed && !project.isDisposed

    private val browserChangeActions: List<BrowserChangeActionDefinition> by lazy(LazyThreadSafetyMode.NONE) {
        listOf(
            BrowserChangeActionDefinition(
                settingValue = ToolWindowSettingsProvider.ACTION_OPEN_DIFF,
                titleKey = "context.menu.show.diff",
                action = ::openDiff
            ),
            BrowserChangeActionDefinition(
                settingValue = ToolWindowSettingsProvider.ACTION_OPEN_SOURCE,
                titleKey = "context.menu.open.source",
                isEnabled = { it.size == 1 },
                action = { changes -> openSource(changes.first()) }
            ),
            BrowserChangeActionDefinition(
                settingValue = ToolWindowSettingsProvider.ACTION_SHOW_IN_PROJECT_TREE,
                titleKey = "context.menu.show.project.tree",
                isEnabled = { it.size == 1 && it.first().type != Change.Type.DELETED },
                action = { changes -> showInProjectTree(changes.first()) }
            )
        )
    }

    // Mouse, Enter and context-menu handling; disposed with this browser.
    private val clickHandler = ChangesTreeClickHandler(project, viewer, browserChangeActions, ::openDiff).also {
        Disposer.register(this, it)
    }

    init {
        // This is CRITICAL. Unlike SimpleAsyncChangesBrowser, AsyncChangesBrowserBase does not call
        // init() in its constructor, so we must do it to build the component layout.
        init()

        // Preserve user expansion/collapse state while still revealing newly added nodes.
        viewer.treeStateStrategy = ExpandNewNodesStateStrategy()

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
        connection.subscribe(DIFF_DATA_CHANGED_TOPIC, ActiveDiffDataChangedListener {
            if (!isAlive()) return@ActiveDiffDataChangedListener
            val diffDataService = project.service<ProjectActiveDiffDataService>()
            // Cleared data (a failed load) has no branch; the error belongs to the selected tab.
            val branchName = diffDataService.activeBranchName
                ?: project.service<ToolWindowStateService>().getSelectedTabBranchName()
                ?: HEAD
            if (branchName == targetBranchToCompare) {
                displayChanges(diffDataService.categorizedChanges, branchName)
            }
        })
        
        Disposer.register(parentDisposable, this)

        // The base class adds a border to its scroll pane, and the tool window content manager also adds one,
        // creating a "double border" effect. Removing the inner border lets the tool window manage it correctly.
        setViewerBorder(JBUI.Borders.empty())

        clickHandler.install()
        configureRendererWidthCacheReset()
        configureDynamicToolbarBorder()
    }

    /** The base toolbar, with the repository comparison action right after "Group By" (or at the end). */
    override fun createToolbarActions(): MutableList<AnAction> {
        val actions = super.createToolbarActions().toMutableList()
        val groupByActionIndex = actions.indexOfFirst { it.javaClass.simpleName == "GroupByActionGroup" }
        actions.add(if (groupByActionIndex >= 0) groupByActionIndex + 1 else actions.size, ShowRepoComparisonInfoAction())
        return actions
    }

    /**
     * Override to return an empty list, completely disabling the default right-click context menu.
     * This is a secondary measure; the primary is [ChangesTreeClickHandler] removing the `PopupHandler` listener.
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
    ) : AsyncChangesTree(project, showCheckboxes, highlightProblems), UiDataProvider {

        init {
            putClientProperty(RenderingHelper.SHRINK_LONG_RENDERER, true)
            putClientProperty(RenderingHelper.SHRINK_LONG_SELECTION, true)
        }

        override val changesTreeModel: AsyncChangesTreeModel
            get() = this@LstCrcChangesBrowser.changesTreeModel

        override fun isFileColorsEnabled(): Boolean = true

        override fun getFileColorForPath(path: TreePath): Color? {
            // First try native pipeline for existing files (which have VirtualFiles)
            val defaultColor = super.getFileColorForPath(path)
            if (defaultColor != null) return defaultColor

            // Custom logic for deleted files (which don't have VirtualFiles)
            val node = path.lastPathComponent as? ChangesBrowserNode<*> ?: return null
            val change = node.userObject as? Change ?: return null
            if (change.type != Change.Type.DELETED) return null
            // The colour configured for the deleted-files scope, or the default rose if none is set.
            return FileColorManager.getInstance(project).getScopeColor(DELETED_SCOPE_ID)
                ?: JBColor.namedColor("FileColor.Rose", JBColor(Color(255, 235, 236), Color(71, 43, 43)))
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

        val diffKey = DiffSelectionKey(targetBranchToCompare, changes.map { it.toDiffChangeKey() }, currentTargetCommits())
        val diffFilesManager = DiffEditorTabFilesManager.getInstance(project)
        val fileEditorManager = FileEditorManager.getInstance(project)
        val openDiffs = fileEditorManager.openFiles.filterIsInstance<ReusableChangeDiffVirtualFile>()
        openDiffs.firstOrNull { it.diffKey == diffKey }?.let { diffFilesManager.showDiffFile(it, true); return }
        // The same selection against an older commit of the target (a fetch or commit moved it) shows old content.
        openDiffs.filter { it.diffKey.isOlderVersionOf(diffKey) }.forEach(fileEditorManager::closeFile)

        val producers = changes.mapNotNull { ChangeDiffRequestProducer.create(project, it) }
        if (producers.size != changes.size) {
            ShowDiffAction.showDiffForChange(project, changes)
            return
        }

        val chain = ChangeDiffRequestChain(ListSelection.createAt(producers, 0))
        diffFilesManager.showDiffFile(ReusableChangeDiffVirtualFile(chain, diffKey, changes.first().diffFileDisplayName()), true)
    }

    /** The commit each repository's comparison target points to now, from Git4Idea's in-memory state. */
    private fun currentTargetCommits(): Map<String, String?> {
        val targets = project.service<ProjectActiveDiffDataService>().activeComparisonContext
        return project.service<GitService>().getRepositories()
            .mapNotNull { repo -> targets[repo.root.path]?.let { repo.root.path to resolveCommitHash(repo, it) } }
            .toMap()
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

    /** Only offered for changes that are not deletions (see [browserChangeActions]). */
    private fun showInProjectTree(change: Change) {
        val fileToSelect = getFileFromChange(change)
        if (fileToSelect != null && fileToSelect.isValid) {
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW)
            toolWindow?.activate({
                if (!isAlive()) return@activate
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

    /** Opens the local file, or for deleted and missing files the content of the revision. */
    private fun openSource(change: Change) {
        val fileToOpen = getFileFromChange(change)
        if (change.type != Change.Type.DELETED && fileToOpen != null && fileToOpen.isValid && !fileToOpen.isDirectory) {
            FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, fileToOpen), true)
            return
        }
        openRevisionSource(change.afterRevision ?: change.beforeRevision ?: return)
    }

    private fun openRevisionSource(revision: ContentRevision): Future<*> =
        ApplicationManager.getApplication().executeOnPooledThread {
            if (!isAlive()) return@executeOnPooledThread
            try {
                ChangesUtil.loadContentRevision(revision)

                ApplicationManager.getApplication().invokeLater {
                    if (!isAlive()) return@invokeLater
                    val virtualFile = ContentRevisionVirtualFile.create(revision)
                    FileEditorManager.getInstance(project).openFile(virtualFile, true, true)
                }
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (e: CancellationException) {
                // This task runs in the platform pool, which logs unhandled coroutine cancellation as an error.
                return@executeOnPooledThread
            } catch (e: Exception) {
                logger.warn("Failed to preload revision-backed file '${revision.file.path}'.", e)
                ApplicationManager.getApplication().invokeLater {
                    if (!isAlive()) return@invokeLater
                    Messages.showWarningDialog(
                        project,
                        LstCrcBundle.message("changes.browser.open.source.error.message", revision.file.path),
                        LstCrcBundle.message("changes.browser.open.source.error.title")
                    )
                }
            }
        }

    @Suppress("unused")
    fun viewerTree(): Tree = viewer

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
        val changes = listOf(change)
        browserChangeActions.firstOrNull { LstCrcBundle.message(it.titleKey) == actionTitle }
            ?.takeIf { it.isEnabled(changes) }
            ?.action
            ?.invoke(changes)
            ?: error("Unsupported context menu action '$actionTitle'.")
    }

    @Suppress("unused")
    fun availableContextMenuActionTitlesForTest(change: Change): List<String> {
        return browserChangeActions.filter { it.isEnabled(listOf(change)) }.map { LstCrcBundle.message(it.titleKey) }
    }

    @Suppress("unused")
    fun configuredActionForClickForTest(button: Int, doubleClick: Boolean): String {
        return clickHandler.configuredActionForButton(button, doubleClick)
    }

    @Suppress("unused")
    fun toolbarActionSimpleNamesForTest(): List<String> {
        return createToolbarActions().map { it.javaClass.simpleName }
    }

    @Suppress("unused")
    fun fileColorForPathForTest(path: TreePath): Color? {
        return (viewer as? LstCrcAsyncChangesTree)?.getFileColorForPath(path)
    }

    @Suppress("unused")
    fun visibleRowTextsForTest(): List<String> {
        return visibleRowPaths()
            .mapNotNull { (row, path) -> renderedRowTextForTest(path, row) }
            .filter(String::isNotBlank)
    }

    @Suppress("unused")
    fun expandedNodeTextsForTest(): List<String> {
        return visibleRowPaths()
            .mapNotNull { (row, path) ->
                val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return@mapNotNull null
                if (node.isLeaf || !viewer.isExpanded(path)) {
                    return@mapNotNull null
                }
                renderedRowTextForTest(path, row)
            }
            .filter(String::isNotBlank)
    }

    @Suppress("unused")
    fun setExpandedForVisibleNodeTextForTest(nodeText: String, expanded: Boolean): Boolean {
        val targetPath = visibleRowPaths()
            .firstOrNull { (row, path) ->
                val userObjectText = (path.lastPathComponent as? DefaultMutableTreeNode)
                    ?.userObject
                    ?.toString()
                    .orEmpty()
                val renderedText = renderedRowTextForTest(path, row).orEmpty()
                renderedText == nodeText || renderedText.contains(nodeText) || userObjectText.contains(nodeText)
            }
            ?.second
            ?: return false

        if (expanded) {
            viewer.expandPath(targetPath)
        } else {
            viewer.collapsePath(targetPath)
        }
        return true
    }

    @Suppress("unused")
    fun scrollVisibleFileIntoViewForTest(fileName: String): Boolean {
        val row = visibleRowForFileName(fileName) ?: return false
        val path = viewer.getPathForRow(row) ?: return false
        viewer.scrollPathToVisible(path)
        return true
    }

    @Suppress("unused")
    fun selectVisibleFileForTest(fileName: String): Boolean {
        val row = visibleRowForFileName(fileName) ?: return false
        viewer.setSelectionRow(row)
        return true
    }

    private fun visibleRowPaths(): List<Pair<Int, TreePath>> {
        return (0 until viewer.rowCount)
            .mapNotNull { row -> viewer.getPathForRow(row)?.let { row to it } }
    }

    private fun visibleRowForFileName(fileName: String): Int? = visibleRowPaths().firstOrNull { (_, path) ->
        val change = (path.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? Change
        val file = change?.afterRevision?.file ?: change?.beforeRevision?.file
        file != null && (file.name == fileName || file.path.replace('\\', '/').endsWith("/$fileName"))
    }?.first

    private fun renderedRowTextForTest(path: TreePath, row: Int): String? {
        val renderer = viewer.cellRenderer as? RepoNodeRenderer ?: return null
        val model = viewer.model
        return renderer.renderedTextForTest(
            viewer,
            path.lastPathComponent,
            viewer.isRowSelected(row),
            viewer.isExpanded(row),
            model.isLeaf(path.lastPathComponent),
            row,
            false
        ).ifBlank { null }
    }

    /**
     * Updates the browser with a new set of changes, preserving the user's scroll and expansion state.
     */
    private fun displayChanges(categorizedChanges: CategorizedChanges?, forBranchName: String) {
        ApplicationManager.getApplication().invokeLater {
            if (!isAlive()) return@invokeLater

            val hasChanges = categorizedChanges?.allChanges?.isNotEmpty() ?: false

            viewer.emptyText.text = when {
                categorizedChanges == null -> LstCrcBundle.message("changes.browser.error.loading", forBranchName)
                !hasChanges -> LstCrcBundle.message("changes.browser.no.changes", forBranchName)
                else -> LstCrcBundle.message("changes.browser.no.changes.filtered")
            }

            // Store the changes and trigger an asynchronous rebuild
            currentChanges = categorizedChanges
            rebuildTreePreservingViewport()
        }
    }

    /**
     * Initiates a refresh of the data for this browser's target branch.
     */
    fun requestRefreshData() {
        if (!isAlive()) return
        logger.debug { "UI_REFRESH: Browser for '$targetBranchToCompare' is requesting a data refresh." }
        project.service<ToolWindowStateService>().refreshDataForCurrentSelection()
    }

    /**
     * Rebuilds the tree view. Called when a display setting (like showing comparison context) is changed.
     */
    fun rebuildView() {
        if (!isAlive()) return
        // Line stats are only computed while shown; after switching them on, reload the data first.
        if (ToolWindowSettingsProvider.isShowLineStatsInTree() && currentChanges?.lineStatsIncluded == false) {
            requestRefreshData()
        }
        ApplicationManager.getApplication().invokeLater {
            if (isAlive()) {
                rebuildTreePreservingViewport()
            }
        }
    }

    private fun rebuildTreePreservingViewport() {
        val viewPosition = (viewer.parent as? JViewport)?.viewPosition
        if (viewPosition == null) {
            viewer.rebuildTree()
            return
        }

        val model = viewer.model
        var restoreScheduled = false
        lateinit var listener: TreeModelListener

        fun restoreViewportOnce() {
            if (restoreScheduled) return
            restoreScheduled = true
            model?.removeTreeModelListener(listener)
            ApplicationManager.getApplication().invokeLater {
                if (isAlive()) {
                    restoreTreeViewport(viewPosition)
                }
            }
        }

        listener = TreeModelAdapter.create { _, _ -> restoreViewportOnce() }

        model?.addTreeModelListener(listener)
        viewer.rebuildTree()

        ApplicationManager.getApplication().invokeLater { restoreViewportOnce() }
    }

    private fun restoreTreeViewport(viewPosition: Point) {
        val viewport = viewer.parent as? JViewport ?: return
        val view = viewport.view ?: return
        val maxX = (view.width - viewport.extentSize.width).coerceAtLeast(0)
        val maxY = (view.height - viewport.extentSize.height).coerceAtLeast(0)
        val clampedPosition = Point(
            viewPosition.x.coerceIn(0, maxX),
            viewPosition.y.coerceIn(0, maxY)
        )
        if (viewport.viewPosition != clampedPosition) {
            viewport.viewPosition = clampedPosition
        }
    }

    override fun dispose() {
        disposed = true
        shutdown()
        logger.debug { "LstCrcChangesBrowser for branch '$targetBranchToCompare' disposed." }
    }

    private fun configureDynamicToolbarBorder() {
        val topPanel = (layout as? BorderLayout)?.getLayoutComponent(BorderLayout.NORTH) as? JPanel
        val fullToolbarComponent = (topPanel?.layout as? BorderLayout)?.getLayoutComponent(BorderLayout.CENTER) as? JComponent
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
            if (isAlive()) {
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
            if (isAlive()) {
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

}

/**
 * Identifies an open diff tab, so opening the same selection again reuses it. [targetCommits] (repository root to the
 * commit its target pointed to, or null when that is unknown) makes a tab opened before the target moved not match.
 */
internal data class DiffSelectionKey(
    val comparisonTarget: String,
    val changes: List<DiffChangeKey>,
    val targetCommits: Map<String, String?>
) {
    /** The same selection, opened while the target pointed to another commit. */
    fun isOlderVersionOf(other: DiffSelectionKey): Boolean =
        this != other && comparisonTarget == other.comparisonTarget && changes == other.changes
}

/**
 * Identifies the diff of one change, so an open diff tab for the same selection is reused. Unsaved edits
 * carry their text ([TextContentRevision]) and are always labeled `LOCAL`, so their text is part of the key:
 * after more typing, the diff opens with the new text instead of the tab showing the old one.
 */
internal data class DiffChangeKey(
    val type: Change.Type,
    val beforePath: String?,
    val beforeRevision: String?,
    val afterPath: String?,
    val afterRevision: String?,
    val loadedTexts: List<String?>
)

internal fun Change.toDiffChangeKey(): DiffChangeKey = DiffChangeKey(
    type = type,
    beforePath = beforeRevision?.file?.path,
    beforeRevision = beforeRevision?.revisionNumber?.asString(),
    afterPath = afterRevision?.file?.path,
    afterRevision = afterRevision?.revisionNumber?.asString(),
    loadedTexts = listOf(beforeRevision, afterRevision).map { (it as? TextContentRevision)?.content }
)

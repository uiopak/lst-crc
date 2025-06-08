package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.messaging.FILE_CHANGES_TOPIC
import com.github.uiopak.lstcrc.messaging.FileChangeListener
import com.github.uiopak.lstcrc.services.CategorizedChanges
import com.github.uiopak.lstcrc.services.GitService
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListListener
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffAction
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import git4idea.repo.GitRepository
import java.awt.event.MouseEvent
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.UIManager
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class ChangesTreePanel(
    private val project: Project,
    private val gitService: GitService, // Used for gitService.getCurrentRepository() in buildTreeFromChanges
    private val propertiesComponent: PropertiesComponent,
    private val targetBranchToCompare: String,
    parentDisposable: Disposable
) : JBScrollPane(), Disposable, ChangeListListener, git4idea.repo.GitRepositoryChangeListener {

    private val logger = thisLogger()
    private var refreshDebounceTimer: Timer? = null
    private var isVfsChangeRefreshPending = false
    val tree: Tree

    private class ClickState {
        var timer: Timer? = null
        var pendingChange: Change? = null
        var pendingNodePath: TreePath? = null
        var actionHasFiredForPath: TreePath? = null

        fun clear() {
            timer?.stop()
            timer = null
            pendingChange = null
            pendingNodePath = null
            actionHasFiredForPath = null
        }
    }

    private val leftClickState = ClickState()
    private val rightClickState = ClickState()

    companion object {
        private const val ACTION_NONE = "NONE"
        private const val ACTION_OPEN_DIFF = "OPEN_DIFF"
        private const val ACTION_OPEN_SOURCE = "OPEN_SOURCE"
        private const val DEFAULT_USER_DELAY_MS = 300

        private const val APP_SINGLE_CLICK_ACTION_KEY = "com.github.uiopak.lstcrc.app.singleClickAction"
        private const val APP_DOUBLE_CLICK_ACTION_KEY = "com.github.uiopak.lstcrc.app.doubleClickAction"
        private const val DEFAULT_SINGLE_CLICK_ACTION = ACTION_NONE
        private const val DEFAULT_DOUBLE_CLICK_ACTION = ACTION_OPEN_DIFF

        private const val APP_RIGHT_CLICK_ACTION_KEY = "com.github.uiopak.lstcrc.app.rightClickAction"
        private const val APP_DOUBLE_RIGHT_CLICK_ACTION_KEY = "com.github.uiopak.lstcrc.app.doubleRightClickAction"
        private const val DEFAULT_RIGHT_CLICK_ACTION = ACTION_NONE
        private const val DEFAULT_DOUBLE_RIGHT_CLICK_ACTION = ACTION_NONE

        private const val APP_USER_DOUBLE_CLICK_DELAY_KEY = "com.github.uiopak.lstcrc.app.userDoubleClickDelay"
        private const val DELAY_OPTION_SYSTEM_DEFAULT = -1
    }

    init {
        tree = createChangesTreeInternal(targetBranchToCompare)
        this.setViewportView(tree)
        this.border = null

        project.messageBus.connect(this).subscribe(FILE_CHANGES_TOPIC, object : FileChangeListener {
            override fun onFilesChanged() {
                isVfsChangeRefreshPending = true
            }
        })

        val changeListManager = ChangeListManager.getInstance(project)
        changeListManager.addChangeListListener(this, this)
        project.messageBus.connect(this).subscribe(GitRepository.GIT_REPO_CHANGE, this)
        com.intellij.openapi.util.Disposer.register(parentDisposable, this)
    }

    override fun repositoryChanged(repository: git4idea.repo.GitRepository) {
        if (repository.project == this.project) {
            triggerDebouncedDataRefresh()
        }
    }

    private fun triggerDebouncedDataRefresh() {
        refreshDebounceTimer?.stop()
        refreshDebounceTimer = Timer(100, null).apply {
            actionListeners.forEach { removeActionListener(it) }
            addActionListener {
                ApplicationManager.getApplication().invokeLater {
                    if (!project.isDisposed) {
                        project.service<ToolWindowStateService>().refreshDataForActiveTabIfMatching(targetBranchToCompare)
                    }
                }
            }
            isRepeats = false
        }
        refreshDebounceTimer?.start()
    }

    override fun changeListChanged(changeList: com.intellij.openapi.vcs.changes.ChangeList) {
        triggerDebouncedDataRefresh()
        isVfsChangeRefreshPending = false
    }

    override fun changesAdded(changes: Collection<Change>, changeList: com.intellij.openapi.vcs.changes.ChangeList?) {
        if (changes.isNotEmpty()) triggerDebouncedDataRefresh()
        isVfsChangeRefreshPending = false
    }

    override fun changesRemoved(changes: Collection<Change>, changeList: com.intellij.openapi.vcs.changes.ChangeList?) {
        if (changes.isNotEmpty()) triggerDebouncedDataRefresh()
        isVfsChangeRefreshPending = false
    }

    override fun unchangedFileStatusChanged() {
        triggerDebouncedDataRefresh()
        isVfsChangeRefreshPending = false
    }

    override fun dispose() {
        refreshDebounceTimer?.stop()
        leftClickState.clear()
        rightClickState.clear()
    }

    private fun getSingleClickAction(): String = propertiesComponent.getValue(APP_SINGLE_CLICK_ACTION_KEY, DEFAULT_SINGLE_CLICK_ACTION)
    private fun getDoubleClickAction(): String = propertiesComponent.getValue(APP_DOUBLE_CLICK_ACTION_KEY, DEFAULT_DOUBLE_CLICK_ACTION)
    private fun getRightClickAction(): String = propertiesComponent.getValue(APP_RIGHT_CLICK_ACTION_KEY, DEFAULT_RIGHT_CLICK_ACTION)
    private fun getDoubleRightClickAction(): String = propertiesComponent.getValue(APP_DOUBLE_RIGHT_CLICK_ACTION_KEY, DEFAULT_DOUBLE_RIGHT_CLICK_ACTION)

    private fun getUserDoubleClickDelayMs(): Int {
        val storedValue = propertiesComponent.getInt(APP_USER_DOUBLE_CLICK_DELAY_KEY, DELAY_OPTION_SYSTEM_DEFAULT)
        return if (storedValue > 0) storedValue else UIManager.getInt("Tree.doubleClickTimeout").takeIf { it > 0 } ?: DEFAULT_USER_DELAY_MS
    }

    internal fun reSortTree() {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            val root = tree.model.root as? DefaultMutableTreeNode ?: return@invokeLater
            val expansionState = TreeUtil.collectExpandedPaths(tree)
            sortTreeRecursively(root)
            (tree.model as DefaultTreeModel).reload(root)
            TreeUtil.restoreExpandedPaths(tree, expansionState)
        }
    }

    private fun sortTreeRecursively(node: DefaultMutableTreeNode) {
        if (node.isLeaf) return

        val children = node.children().asSequence().mapNotNull { it as? DefaultMutableTreeNode }.toMutableList()
        if (children.isEmpty()) return

        children.sortWith(getNodeComparator())

        node.removeAllChildren()
        children.forEach { node.add(it) }

        // Recurse down for child directories
        children.forEach { if (it.userObject is String) sortTreeRecursively(it) }
    }

    private fun getNodeComparator(): Comparator<DefaultMutableTreeNode> {
        val props = PropertiesComponent.getInstance(project)
        val currentSortTypeName = props.getValue(
            ToolWindowSettingsProvider.SORT_TYPE_KEY,
            ToolWindowSettingsProvider.DEFAULT_SORT_TYPE.name
        )
        val currentSortType = try {
            ToolWindowSettingsProvider.Companion.SortType.valueOf(currentSortTypeName)
        } catch (e: IllegalArgumentException) {
            ToolWindowSettingsProvider.DEFAULT_SORT_TYPE
        }
        val isSortAscending = props.getBoolean(
            ToolWindowSettingsProvider.SORT_ASCENDING_KEY,
            ToolWindowSettingsProvider.DEFAULT_SORT_ASCENDING
        )
        val keepFoldersOnTop = props.getBoolean(
            ToolWindowSettingsProvider.KEEP_FOLDERS_ON_TOP_KEY,
            ToolWindowSettingsProvider.DEFAULT_KEEP_FOLDERS_ON_TOP
        )

        val mainComparator = Comparator<DefaultMutableTreeNode> { n1, n2 ->
            val u1 = n1.userObject
            val u2 = n2.userObject

            when {
                u1 is String && u2 is String -> { // Both are directories
                    if (isSortAscending) u1.compareTo(u2, ignoreCase = true) else u2.compareTo(u1, ignoreCase = true)
                }
                u1 is Change && u2 is Change -> { // Both are files (Changes)
                    val comparisonResult = when (currentSortType) {
                        ToolWindowSettingsProvider.Companion.SortType.NAME -> {
                            val name1 = u1.afterRevision?.file?.name ?: ""
                            val name2 = u2.afterRevision?.file?.name ?: ""
                            name1.compareTo(name2, ignoreCase = true)
                        }
                        ToolWindowSettingsProvider.Companion.SortType.DIFF_TYPE -> {
                            u1.type.ordinal.compareTo(u2.type.ordinal)
                        }
                        ToolWindowSettingsProvider.Companion.SortType.FILE_TYPE -> {
                            val name1 = u1.afterRevision?.file?.name ?: ""
                            val name2 = u2.afterRevision?.file?.name ?: ""
                            val ext1 = name1.substringAfterLast('.', "")
                            val ext2 = name2.substringAfterLast('.', "")
                            val extCompare = ext1.compareTo(ext2, ignoreCase = true)
                            if (extCompare != 0) extCompare else name1.compareTo(name2, ignoreCase = true)
                        }
                        ToolWindowSettingsProvider.Companion.SortType.MODIFICATION_TIME -> {
                            val time1 = u1.afterRevision?.file?.virtualFile?.timeStamp ?: 0L
                            val time2 = u2.afterRevision?.file?.virtualFile?.timeStamp ?: 0L
                            time1.compareTo(time2)
                        }
                    }
                    if (isSortAscending) comparisonResult else -comparisonResult
                }
                else -> 0
            }
        }

        if (!keepFoldersOnTop) {
            return mainComparator
        }

        return Comparator { n1, n2 ->
            val isFolder1 = n1.userObject is String
            val isFolder2 = n2.userObject is String

            when {
                isFolder1 && !isFolder2 -> -1
                !isFolder1 && isFolder2 -> 1
                else -> mainComparator.compare(n1, n2)
            }
        }
    }


    private fun createChangesTreeInternal(initialTarget: String): Tree {
        val root = DefaultMutableTreeNode("Changes")
        val treeModel = DefaultTreeModel(root)
        // By subclassing Tree, we can override processMouseEvent for ultimate control.
        val newTree = object : Tree(treeModel) {
            override fun getScrollableTracksViewportWidth(): Boolean = true

            override fun processMouseEvent(e: MouseEvent) {
                // We only care about MOUSE_CLICKED for our custom actions.
                if (e.id != MouseEvent.MOUSE_CLICKED) {
                    super.processMouseEvent(e)
                    return
                }

                val clickPoint = e.point
                var path: TreePath? = null
                val row = getClosestRowForLocation(clickPoint.x, clickPoint.y)

                if (row != -1) {
                    val rowBounds = getRowBounds(row)
                    if (rowBounds != null) {
                        val yWithinRow = clickPoint.y >= rowBounds.y && clickPoint.y < (rowBounds.y + rowBounds.height)
                        val xWithinTreeVisible = clickPoint.x >= visibleRect.x && clickPoint.x < (visibleRect.x + visibleRect.width)
                        if (yWithinRow && xWithinTreeVisible) {
                            path = getPathForRow(row)
                        }
                    }
                }

                if (path == null) {
                    super.processMouseEvent(e) // Allow clicks on empty space to deselect.
                    return
                }

                val node = path.lastPathComponent as? DefaultMutableTreeNode ?: run { super.processMouseEvent(e); return }
                val change = node.userObject as? Change ?: run { super.processMouseEvent(e); return }

                // The logic to handle the click. Returns true if the event was consumed.
                val consumed = if (SwingUtilities.isLeftMouseButton(e)) {
                    rightClickState.clear()
                    handleGenericClick(e, change, path, getSingleClickAction(), getDoubleClickAction(), leftClickState)
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    leftClickState.clear()
                    handleGenericClick(e, change, path, getRightClickAction(), getDoubleRightClickAction(), rightClickState)
                } else {
                    false
                }

                // If our logic did not consume the event, pass it to the default handler.
                if (!consumed) {
                    super.processMouseEvent(e)
                }
            }
        }

        TreeSpeedSearch(newTree)

        newTree.setCellRenderer(object : ColoredTreeCellRenderer() {
            override fun customizeCellRenderer(jTree: JTree, value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean) {
                if (value !is DefaultMutableTreeNode) { append(value?.toString() ?: ""); return }
                when (val userObject = value.userObject) {
                    is Change -> {
                        val filePath = userObject.afterRevision?.file ?: userObject.beforeRevision?.file
                        val fileName = filePath?.name ?: "Unknown File"
                        val fgColor = if (selected) UIManager.getColor("Tree.selectionForeground") else when (userObject.type) {
                            Change.Type.NEW -> JBColor.namedColor("VersionControl.FileStatus.Added", JBColor.GREEN)
                            Change.Type.DELETED -> JBColor.namedColor("VersionControl.FileStatus.Deleted", JBColor.RED)
                            Change.Type.MOVED, Change.Type.MODIFICATION -> JBColor.namedColor("VersionControl.FileStatus.Modified", JBColor.BLUE)
                            else -> UIManager.getColor("Tree.foreground")
                        } ?: UIManager.getColor("Tree.foreground")
                        append(fileName, SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, fgColor))
                        icon = filePath?.virtualFile?.fileType?.icon ?: FileTypeManager.getInstance().getFileTypeByFileName(fileName).icon
                    }
                    is String -> { append(userObject, SimpleTextAttributes.REGULAR_ATTRIBUTES); icon = AllIcons.Nodes.Folder }
                    else -> append(value.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES)
                }
            }
        })
        root.removeAllChildren()
        root.add(DefaultMutableTreeNode("Loading..."))
        treeModel.reload(root)
        return newTree
    }

    private fun handleGenericClick(
        e: MouseEvent,
        change: Change,
        path: TreePath,
        singleClickAction: String,
        doubleClickAction: String,
        clickState: ClickState
    ): Boolean { // Return true if the event was consumed
        if (tree.selectionPath != path) {
            tree.selectionPath = path
        }
        tree.requestFocusInWindow()

        if (doubleClickAction == ACTION_NONE) {
            clickState.clear()
            if (e.clickCount == 1 && singleClickAction != ACTION_NONE) {
                performConfiguredAction(change, singleClickAction)
                return true
            }
            return false // No action, don't consume
        }

        if (e.clickCount == 1) {
            if (clickState.pendingNodePath != path || clickState.actionHasFiredForPath != null) {
                clickState.clear()
            }
            clickState.pendingChange = change
            clickState.pendingNodePath = path
            clickState.timer?.stop()
            clickState.timer = Timer(getUserDoubleClickDelayMs()) {
                val sChange = clickState.pendingChange
                val sPath = clickState.pendingNodePath
                clickState.timer = null
                clickState.pendingChange = null
                clickState.pendingNodePath = null

                if (sChange != null && sPath != null && singleClickAction != ACTION_NONE) {
                    performConfiguredAction(sChange, singleClickAction)
                    clickState.actionHasFiredForPath = sPath
                }
            }.apply { isRepeats = false }
            clickState.timer?.start()
            // Even if we start a timer, we might not consume the event,
            // allowing selection to happen immediately.
            // If the single-click action is NONE, we don't need to consume.
            return singleClickAction != ACTION_NONE
        } else if (e.clickCount >= 2) {
            if (clickState.actionHasFiredForPath == path) {
                clickState.actionHasFiredForPath = null
                clickState.timer?.stop()
                return true
            }
            if (clickState.pendingNodePath == path) {
                clickState.clear()
            }
            if (doubleClickAction != ACTION_NONE) {
                performConfiguredAction(change, doubleClickAction)
                return true
            }
            clickState.actionHasFiredForPath = null
        }
        return false
    }

    private fun performConfiguredAction(change: Change, actionType: String) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            when (actionType) {
                ACTION_OPEN_DIFF -> openDiff(change)
                ACTION_OPEN_SOURCE -> openSource(change)
            }
        }
    }

    private fun openSource(change: Change) {
        val fileToOpen: VirtualFile? = when (change.type) {
            Change.Type.DELETED -> change.beforeRevision?.file?.virtualFile
            else -> change.afterRevision?.file?.virtualFile ?: change.beforeRevision?.file?.virtualFile
        }
        if (fileToOpen != null && fileToOpen.isValid && !fileToOpen.isDirectory) {
            OpenFileDescriptor(project, fileToOpen).navigate(true)
        } else {
            val pathForMessage = (change.afterRevision?.file ?: change.beforeRevision?.file)?.path ?: "Unknown path"
            Messages.showWarningDialog(project, "Could not open source file (it may no longer exist, is not accessible, or is a directory): $pathForMessage", "Open Source Error")
        }
    }

    fun showLoadingStateAndPrepareForData() {
        val rootModelNode = tree.model.root as DefaultMutableTreeNode
        rootModelNode.removeAllChildren()
        rootModelNode.add(DefaultMutableTreeNode("Loading..."))
        (tree.model as DefaultTreeModel).reload(rootModelNode)
    }

    fun displayChanges(categorizedChanges: CategorizedChanges?, forBranchName: String) {
        if (forBranchName != targetBranchToCompare) {
            return
        }
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            val rootModelNode = tree.model.root as DefaultMutableTreeNode
            rootModelNode.removeAllChildren()

            if (categorizedChanges == null) {
                rootModelNode.add(DefaultMutableTreeNode("Error loading changes for $forBranchName"))
            } else if (categorizedChanges.allChanges.isEmpty()) {
                rootModelNode.add(DefaultMutableTreeNode("No changes found for $forBranchName"))
            } else {
                buildTreeFromChanges(rootModelNode, categorizedChanges.allChanges)
                sortTreeRecursively(rootModelNode) // Sort the newly built tree
            }
            (tree.model as DefaultTreeModel).reload(rootModelNode)
            TreeUtil.expandAll(tree)
        }
    }

    private fun buildTreeFromChanges(rootNode: DefaultMutableTreeNode, changes: List<Change>) {
        val commonRepositoryRoot = gitService.getCurrentRepository()?.root

        for (changeItem in changes) {
            val currentFilePathObj = changeItem.afterRevision?.file ?: changeItem.beforeRevision?.file
            val rawPath = currentFilePathObj?.path
            var displayPath: String? = null
            val vf = currentFilePathObj?.virtualFile

            if (vf != null && commonRepositoryRoot != null) {
                displayPath = VfsUtilCore.getRelativePath(vf, commonRepositoryRoot, '/')
            } else if (rawPath != null && commonRepositoryRoot != null) {
                val repoRootPathString = commonRepositoryRoot.path
                if (rawPath.startsWith(repoRootPathString + "/")) {
                    displayPath = rawPath.substring(repoRootPathString.length + 1)
                } else if (rawPath.startsWith(repoRootPathString)) {
                    displayPath = rawPath.substring(repoRootPathString.length).let { if (it.startsWith("/")) it.substring(1) else it }
                } else {
                    displayPath = rawPath
                }
            }

            if (displayPath == null) displayPath = rawPath
            if (displayPath == null) continue

            val pathComponents = displayPath.replace('\\', '/').split('/').filter { it.isNotEmpty() }
            var currentNode = rootNode
            if (pathComponents.isEmpty() && currentFilePathObj != null) {
                var fileNodeExists = false
                for (j in 0 until currentNode.childCount) {
                    val existingChild = currentNode.getChildAt(j) as DefaultMutableTreeNode
                    if ((existingChild.userObject as? Change)?.afterRevision?.file?.path == rawPath) {
                        fileNodeExists = true; break
                    }
                }
                if (!fileNodeExists) currentNode.add(DefaultMutableTreeNode(changeItem))
                continue
            }
            for (i in pathComponents.indices) {
                val componentName = pathComponents[i]
                val isLastComponent = i == pathComponents.size - 1
                var childNode: DefaultMutableTreeNode? = null
                for (j in 0 until currentNode.childCount) {
                    val existingChild = currentNode.getChildAt(j) as DefaultMutableTreeNode
                    if (isLastComponent && (existingChild.userObject as? Change)?.afterRevision?.file?.path == rawPath) {
                        childNode = existingChild; break
                    } else if (!isLastComponent && existingChild.userObject is String && existingChild.userObject == componentName) {
                        childNode = existingChild; break
                    }
                }
                if (childNode == null) {
                    childNode = if (isLastComponent) DefaultMutableTreeNode(changeItem) else DefaultMutableTreeNode(componentName)
                    currentNode.add(childNode)
                }
                currentNode = childNode
            }
        }
    }

    private fun openDiff(change: Change) {
        try {
            ShowDiffAction.showDiffForChange(project, listOf(change))
        } catch (e: Exception) {
            Messages.showErrorDialog(project, "Error opening diff: ${e.message}", "Error")
        }
    }

    fun requestRefreshData() {
        showLoadingStateAndPrepareForData()
        project.service<ToolWindowStateService>().refreshDataForActiveTabIfMatching(targetBranchToCompare)
    }
}
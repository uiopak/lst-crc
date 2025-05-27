package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.services.GitService
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffAction
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.Timer
import javax.swing.UIManager
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class ChangesTreePanel(
    private val project: Project,
    private val gitService: GitService,
    private val propertiesComponent: PropertiesComponent,
    private val branchName: String
) : JBScrollPane() { // Inherit from JBScrollPane to be a JComponent

    private val logger = thisLogger()
    val tree: Tree

    // Click handling state variables
    private var singleClickTimer: Timer? = null
    private var pendingSingleClickChange: Change? = null
    private var pendingSingleClickNodePath: TreePath? = null
    private var singleClickActionHasFiredForPath: TreePath? = null
    
    // Constants for click actions and preferences (must match GitChangesToolWindow and ToolWindowSettingsProvider)
    companion object {
        private const val ACTION_NONE = "NONE"
        private const val ACTION_OPEN_DIFF = "OPEN_DIFF"
        private const val ACTION_OPEN_SOURCE = "OPEN_SOURCE"
        private const val DEFAULT_USER_DELAY_MS = 300

        private const val APP_SINGLE_CLICK_ACTION_KEY = "com.github.uiopak.lstcrc.app.singleClickAction"
        private const val APP_DOUBLE_CLICK_ACTION_KEY = "com.github.uiopak.lstcrc.app.doubleClickAction"
        private const val APP_USER_DOUBLE_CLICK_DELAY_KEY = "com.github.uiopak.lstcrc.app.userDoubleClickDelay"
        
        private const val DEFAULT_SINGLE_CLICK_ACTION = ACTION_NONE
        private const val DEFAULT_DOUBLE_CLICK_ACTION = ACTION_OPEN_DIFF
        private const val DELAY_OPTION_SYSTEM_DEFAULT = -1
    }

    init {
        tree = createChangesTreeInternal(branchName)
        this.setViewportView(tree)
        this.border = null
    }
    
    private fun getSingleClickAction(): String =
        propertiesComponent.getValue(APP_SINGLE_CLICK_ACTION_KEY, DEFAULT_SINGLE_CLICK_ACTION)

    private fun getDoubleClickAction(): String =
        propertiesComponent.getValue(APP_DOUBLE_CLICK_ACTION_KEY, DEFAULT_DOUBLE_CLICK_ACTION)

    private fun getUserDoubleClickDelayMs(): Int {
        val storedValue = propertiesComponent.getInt(APP_USER_DOUBLE_CLICK_DELAY_KEY, DELAY_OPTION_SYSTEM_DEFAULT)
        if (storedValue == DELAY_OPTION_SYSTEM_DEFAULT) {
            val systemTimeout = UIManager.getInt("Tree.doubleClickTimeout")
            return if (systemTimeout > 0) systemTimeout else DEFAULT_USER_DELAY_MS
        }
        return if (storedValue > 0) storedValue else DEFAULT_USER_DELAY_MS
    }

    private fun createChangesTreeInternal(branchNameForInitialRefresh: String): Tree {
        val root = DefaultMutableTreeNode("Changes")
        val treeModel = DefaultTreeModel(root)
        val newTree = object : Tree(treeModel) {
            override fun getScrollableTracksViewportWidth(): Boolean = true
        }

        newTree.setCellRenderer(object : ColoredTreeCellRenderer() {
            override fun customizeCellRenderer(
                jTree: JTree, value: Any?, selected: Boolean, expanded: Boolean,
                leaf: Boolean, row: Int, hasFocus: Boolean
            ) {
                if (value !is DefaultMutableTreeNode) {
                    append(value?.toString() ?: ""); return
                }
                val userObject = value.userObject
                when (userObject) {
                    is Change -> {
                        val change = userObject
                        val filePath = change.afterRevision?.file ?: change.beforeRevision?.file
                        val fileName = filePath?.name ?: "Unknown File"
                        val fgColor = if (selected) UIManager.getColor("Tree.selectionForeground")
                        else when (change.type) {
                            Change.Type.NEW -> JBColor.namedColor("VersionControl.FileStatus.Added", JBColor.GREEN)
                            Change.Type.DELETED -> JBColor.namedColor("VersionControl.FileStatus.Deleted", JBColor.RED)
                            Change.Type.MOVED, Change.Type.MODIFICATION -> JBColor.namedColor("VersionControl.FileStatus.Modified", JBColor.BLUE)
                            else -> UIManager.getColor("Tree.foreground")
                        } ?: UIManager.getColor("Tree.foreground")
                        append(fileName, SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, fgColor))
                        var fileIcon: Icon? = null
                        if (filePath != null) {
                            filePath.virtualFile?.let { vf -> fileIcon = vf.fileType.icon }
                            if (fileIcon == null) fileIcon = FileTypeManager.getInstance().getFileTypeByFileName(filePath.name).icon
                        }
                        icon = fileIcon ?: AllIcons.FileTypes.Unknown
                    }
                    is String -> { append(userObject, SimpleTextAttributes.REGULAR_ATTRIBUTES); icon = AllIcons.Nodes.Folder }
                    else -> append(value.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES)
                }
            }
        })

        newTree.addMouseListener(object : MouseAdapter() {
            private fun logState(event: String, e: MouseEvent?, currentPath: TreePath? = null) {
                // Logging removed as per previous subtask
            }

            override fun mouseClicked(e: MouseEvent) {
                logState("mouseClicked ENTER", e)
                val clickPoint = e.point
                var currentTargetPath: TreePath? = null
                val row = newTree.getClosestRowForLocation(clickPoint.x, clickPoint.y)
                if (row != -1) {
                    val contentBoundsForRow = newTree.getRowBounds(row)
                    if (contentBoundsForRow != null) {
                        val yWithinRowContent = clickPoint.y >= contentBoundsForRow.y && clickPoint.y < (contentBoundsForRow.y + contentBoundsForRow.height)
                        val xWithinTreeVisible = clickPoint.x >= newTree.visibleRect.x && clickPoint.x < (newTree.visibleRect.x + newTree.visibleRect.width)
                        if (yWithinRowContent && xWithinTreeVisible) {
                            currentTargetPath = newTree.getPathForRow(row)
                        }
                    }
                }
                logState("Path Determined", e, currentTargetPath)

                if (currentTargetPath == null) {
                    logState("No Valid Target Path", e)
                    singleClickTimer?.stop()
                    pendingSingleClickChange = null
                    pendingSingleClickNodePath = null
                    singleClickActionHasFiredForPath = null
                    return
                }

                val node = currentTargetPath.lastPathComponent as? DefaultMutableTreeNode
                (node?.userObject as? Change)?.let { currentChange ->
                    logState("Processing Change Node", e, currentTargetPath)
                    if (newTree.selectionPath != currentTargetPath) {
                        newTree.selectionPath = currentTargetPath
                        logState("  Node Selected", e, currentTargetPath)
                    }

                    val singleClickConfiguredAction = getSingleClickAction()
                    val doubleClickConfiguredAction = getDoubleClickAction()

                    if (e.clickCount == 1) {
                        logState("Click Count == 1", e, currentTargetPath)
                        if (pendingSingleClickNodePath != currentTargetPath || singleClickActionHasFiredForPath != null) {
                            logState("  Resetting for new single click seq", e, currentTargetPath)
                            singleClickTimer?.stop()
                            pendingSingleClickChange = null
                            pendingSingleClickNodePath = null
                            singleClickActionHasFiredForPath = null
                        }

                        if (doubleClickConfiguredAction == ACTION_NONE) {
                            logState("  Optimization: No DblClick", e, currentTargetPath)
                            singleClickTimer?.stop()
                            pendingSingleClickChange = null
                            pendingSingleClickNodePath = null
                            singleClickActionHasFiredForPath = null
                            if (singleClickConfiguredAction != ACTION_NONE) {
                                logState("    Executing Single (Optimized)", e, currentTargetPath)
                                performConfiguredAction(currentChange, singleClickConfiguredAction)
                            }
                            return@let
                        }

                        logState("  Setting up Timer", e, currentTargetPath)
                        pendingSingleClickChange = currentChange
                        pendingSingleClickNodePath = currentTargetPath
                        singleClickTimer?.stop()
                        val userConfiguredDelay = getUserDoubleClickDelayMs()
                        singleClickTimer = Timer(userConfiguredDelay) {
                            logState("TIMER ACTION LISTENER FIRED", null, pendingSingleClickNodePath)
                            val sChange = pendingSingleClickChange
                            val sPath = pendingSingleClickNodePath
                            pendingSingleClickChange = null
                            pendingSingleClickNodePath = null
                            if (sChange != null && sPath != null) {
                                if (singleClickConfiguredAction != ACTION_NONE) {
                                    logState("    TIMER: Performing SingleClick", null, sPath)
                                    performConfiguredAction(sChange, singleClickConfiguredAction)
                                    singleClickActionHasFiredForPath = sPath
                                    logState("    TIMER: FiredPath SET", null, sPath)
                                }
                            }
                        }
                        singleClickTimer?.isRepeats = false
                        singleClickTimer?.start()
                        logState("  Timer Started", e, currentTargetPath)

                    } else if (e.clickCount >= 2) {
                        logState("Click Count >= 2", e, currentTargetPath)
                        if (singleClickActionHasFiredForPath == currentTargetPath) {
                            logState("  DblClick: IGNORED (Single Fired for this path)", e, currentTargetPath)
                            singleClickActionHasFiredForPath = null
                            singleClickTimer?.stop()
                            return@let
                        }
                        if (pendingSingleClickNodePath == currentTargetPath) {
                            logState("  DblClick: Cancelling PENDING Timer", e, currentTargetPath)
                            singleClickTimer?.stop()
                            pendingSingleClickChange = null
                            pendingSingleClickNodePath = null
                            logState("    Pending Single Cleared (timer cancelled)", e)
                        } else {
                            logState("  DblClick: Path different or no PENDING single", e, currentTargetPath)
                        }
                        if (doubleClickConfiguredAction != ACTION_NONE) {
                            logState("  DblClick: Performing Action", e, currentTargetPath)
                            performConfiguredAction(currentChange, doubleClickConfiguredAction)
                        }
                        singleClickActionHasFiredForPath = null
                        logState("  DblClick: FiredPath Reset (post-action/ignore)", e, currentTargetPath)
                    }
                } ?: run {
                    logState("Non-Change Node or Invalid Path", e, currentTargetPath)
                    singleClickTimer?.stop()
                    pendingSingleClickChange = null
                    pendingSingleClickNodePath = null
                    singleClickActionHasFiredForPath = null
                    logState("  State Reset", e)
                }
                logState("mouseClicked EXIT", e, currentTargetPath)
            }
        })
        refreshChangesTree(newTree, branchNameForInitialRefresh)
        return newTree
    }

    private fun performConfiguredAction(change: Change, actionType: String) {
        when (actionType) {
            ACTION_OPEN_DIFF -> openDiff(change)
            ACTION_OPEN_SOURCE -> openSource(change)
            ACTION_NONE -> { /* Do nothing */ }
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
            if (fileToOpen?.isDirectory == true) {
                Messages.showWarningDialog(project, "Cannot open a directory as source. Please select a file.", "Open Source Error")
            } else {
                Messages.showWarningDialog(project, "Could not open source file (it may no longer exist or is not accessible): $pathForMessage", "Open Source Error")
            }
        }
    }

    private fun refreshChangesTree(treeToRefresh: JTree, branch: String) {
        val changes = gitService.getChanges(branch)
        val rootModelNode = treeToRefresh.model.root as DefaultMutableTreeNode
        rootModelNode.removeAllChildren()
        buildTreeFromChanges(rootModelNode, changes)
        (treeToRefresh.model as DefaultTreeModel).reload(rootModelNode)
        TreeUtil.expandAll(treeToRefresh)
    }

    private fun buildTreeFromChanges(rootNode: DefaultMutableTreeNode, changes: List<Change>) {
        val repositoryRoot = gitService.getCurrentRepository()?.root
        for (changeItem in changes) {
            val currentFilePathObj = changeItem.afterRevision?.file ?: changeItem.beforeRevision?.file
            val rawPath = currentFilePathObj?.path
            var displayPath: String? = null
            if (currentFilePathObj != null && repositoryRoot != null) {
                val vf = currentFilePathObj.virtualFile
                if (vf != null) {
                    displayPath = VfsUtilCore.getRelativePath(vf, repositoryRoot, '/')
                } else if (rawPath != null) {
                    val repoRootPathString = repositoryRoot.path
                    if (rawPath.startsWith(repoRootPathString + "/")) {
                        displayPath = rawPath.substring(repoRootPathString.length + 1)
                    } else if (rawPath.startsWith(repoRootPathString)) {
                        displayPath = rawPath.substring(repoRootPathString.length).let { if (it.startsWith("/")) it.substring(1) else it }
                    } else {
                        displayPath = rawPath
                    }
                }
            }
            if (displayPath == null) displayPath = rawPath
            if (displayPath == null) continue
            val normalizedPath = displayPath.replace('\\', '/')
            val pathComponents = normalizedPath.split('/').filter { it.isNotEmpty() }
            var currentNode = rootNode
            if (pathComponents.isEmpty() && currentFilePathObj != null) {
                var fileNodeExists = false
                for (j in 0 until currentNode.childCount) {
                    val existingChild = currentNode.getChildAt(j) as DefaultMutableTreeNode
                    if (existingChild.userObject is Change) {
                        val existingChange = existingChild.userObject as Change
                        val existingChangePath = existingChange.afterRevision?.file?.path ?: existingChange.beforeRevision?.file?.path
                        if (existingChangePath == rawPath) {
                            fileNodeExists = true; break
                        }
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
                    if (isLastComponent && existingChild.userObject is Change) {
                        val existingChange = existingChild.userObject as Change
                        val existingChangeOriginalPath = existingChange.afterRevision?.file?.path ?: existingChange.beforeRevision?.file?.path
                        if (existingChangeOriginalPath == rawPath) {
                            childNode = existingChild; break
                        }
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

    // Public method to refresh the tree if needed from outside
    fun refreshTreeForBranch(newBranchName: String) {
        refreshChangesTree(tree, newBranchName)
    }
}

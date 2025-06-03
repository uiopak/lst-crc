package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.services.CategorizedChanges
import com.github.uiopak.lstcrc.services.GitService // gitService is used in buildTreeFromChanges
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
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
import git4idea.repo.GitRepository
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffAction
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.github.uiopak.lstcrc.messaging.FILE_CHANGES_TOPIC
import com.github.uiopak.lstcrc.messaging.FileChangeListener
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JTree
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
    parentDisposable: com.intellij.openapi.Disposable
) : JBScrollPane(), com.intellij.openapi.Disposable, ChangeListListener, git4idea.repo.GitRepositoryChangeListener {

    private val logger = thisLogger()
    val tree: Tree

    // targetBranchToCompare dictates the branch this panel is responsible for displaying.
    // Listeners will use this name to tell ToolWindowStateService which branch's data to potentially refresh.

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
        tree = createChangesTreeInternal(targetBranchToCompare) // This will now set "Loading..."
        this.setViewportView(tree)
        this.border = null

        project.messageBus.connect(this).subscribe(FILE_CHANGES_TOPIC, object : FileChangeListener {
            override fun onFilesChanged() {
                logger.debug("onFilesChanged event received for target: $targetBranchToCompare. Requesting refresh.")
                project.service<ToolWindowStateService>().requestRefreshForBranch(targetBranchToCompare)
            }
        })
        // In init block
        val changeListManager = ChangeListManager.getInstance(project)
        changeListManager.addChangeListListener(this, this) // 'this' as the listener, 'this' as the Disposable
        project.messageBus.connect(this).subscribe(GitRepository.GIT_REPO_CHANGE, this)
        com.intellij.openapi.util.Disposer.register(parentDisposable, this)
    }

    // GitRepositoryChangeListener implementation
    override fun repositoryChanged(repository: git4idea.repo.GitRepository) {
        // Check if the changed repository is relevant to the current project,
        // though for a project-specific panel, any repository change in that project is likely relevant.
        if (repository.project == this.project) {
            logger.debug("GitRepositoryChangeListener.repositoryChanged invoked for repo: ${repository.root.presentableUrl}. Requesting refresh for $targetBranchToCompare.")
            ApplicationManager.getApplication().invokeLater {
                project.service<ToolWindowStateService>().requestRefreshForBranch(targetBranchToCompare)
            }
        } else {
            logger.debug("GitRepositoryChangeListener.repositoryChanged invoked for repo: ${repository.root.presentableUrl}, but it's not for the current project (${this.project.name}). Skipping request.")
        }
    }

    // ChangeListListener implementations
    override fun changeListChanged(changeList: com.intellij.openapi.vcs.changes.ChangeList) {
        logger.debug("changeListChanged invoked for list: ${changeList.name}. Requesting refresh for $targetBranchToCompare.")
        project.service<ToolWindowStateService>().requestRefreshForBranch(targetBranchToCompare)
    }

    override fun changesAdded(changes: Collection<Change>, changeList: com.intellij.openapi.vcs.changes.ChangeList?) {
        logger.debug("changesAdded invoked. Number of changes: ${changes.size}. Requesting refresh for $targetBranchToCompare.")
        project.service<ToolWindowStateService>().requestRefreshForBranch(targetBranchToCompare)
    }

    override fun changesRemoved(changes: Collection<Change>, changeList: com.intellij.openapi.vcs.changes.ChangeList?) {
        logger.debug("changesRemoved invoked. Number of changes: ${changes.size}. Requesting refresh for $targetBranchToCompare.")
        project.service<ToolWindowStateService>().requestRefreshForBranch(targetBranchToCompare)
    }

    override fun unchangedFileStatusChanged() {
        logger.debug("unchangedFileStatusChanged invoked. Requesting refresh for $targetBranchToCompare.")
        project.service<ToolWindowStateService>().requestRefreshForBranch(targetBranchToCompare)
    }

    // This method is from Disposable (indirectly via ChangeListListener).
    override fun dispose() {
        // Any other disposables specific to ChangesTreePanel can be cleaned up here.
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

    // Changed parameter type to non-null
    private fun createChangesTreeInternal(initialTarget: String): Tree {
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
            // Removed logState function and its calls.
            override fun mouseClicked(e: MouseEvent) {
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

                if (currentTargetPath == null) {
                    singleClickTimer?.stop()
                    pendingSingleClickChange = null
                    pendingSingleClickNodePath = null
                    singleClickActionHasFiredForPath = null
                    return
                }

                val node = currentTargetPath.lastPathComponent as? DefaultMutableTreeNode
                (node?.userObject as? Change)?.let { currentChange ->
                    if (newTree.selectionPath != currentTargetPath) {
                        newTree.selectionPath = currentTargetPath
                    }

                    val singleClickConfiguredAction = getSingleClickAction()
                    val doubleClickConfiguredAction = getDoubleClickAction()

                    if (e.clickCount == 1) {
                        if (pendingSingleClickNodePath != currentTargetPath || singleClickActionHasFiredForPath != null) {
                            singleClickTimer?.stop()
                            pendingSingleClickChange = null
                            pendingSingleClickNodePath = null
                            singleClickActionHasFiredForPath = null
                        }

                        if (doubleClickConfiguredAction == ACTION_NONE) {
                            singleClickTimer?.stop()
                            pendingSingleClickChange = null
                            pendingSingleClickNodePath = null
                            singleClickActionHasFiredForPath = null
                            if (singleClickConfiguredAction != ACTION_NONE) {
                                performConfiguredAction(currentChange, singleClickConfiguredAction)
                            }
                            return@let
                        }

                        pendingSingleClickChange = currentChange
                        pendingSingleClickNodePath = currentTargetPath
                        singleClickTimer?.stop()
                        val userConfiguredDelay = getUserDoubleClickDelayMs()
                        singleClickTimer = Timer(userConfiguredDelay) {
                            val sChange = pendingSingleClickChange
                            val sPath = pendingSingleClickNodePath
                            pendingSingleClickChange = null
                            pendingSingleClickNodePath = null
                            if (sChange != null && sPath != null) {
                                if (singleClickConfiguredAction != ACTION_NONE) {
                                    performConfiguredAction(sChange, singleClickConfiguredAction)
                                    singleClickActionHasFiredForPath = sPath
                                }
                            }
                        }
                        singleClickTimer?.isRepeats = false
                        singleClickTimer?.start()

                    } else if (e.clickCount >= 2) {
                        if (singleClickActionHasFiredForPath == currentTargetPath) {
                            singleClickActionHasFiredForPath = null
                            singleClickTimer?.stop()
                            return@let
                        }
                        if (pendingSingleClickNodePath == currentTargetPath) {
                            singleClickTimer?.stop()
                            pendingSingleClickChange = null
                            pendingSingleClickNodePath = null
                        }
                        if (doubleClickConfiguredAction != ACTION_NONE) {
                            performConfiguredAction(currentChange, doubleClickConfiguredAction)
                        }
                        singleClickActionHasFiredForPath = null
                    }
                } ?: run {
                    singleClickTimer?.stop()
                    pendingSingleClickChange = null
                    pendingSingleClickNodePath = null
                    singleClickActionHasFiredForPath = null
                }
            }
        })
        // Set initial "Loading..." state instead of immediate refresh
        root.removeAllChildren()
        root.add(DefaultMutableTreeNode("Loading..."))
        treeModel.reload(root)
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

    // Renamed from refreshChangesTree. Its role is now just to set the loading state.
    fun showLoadingStateAndPrepareForData() {
        logger.debug("showLoadingStateAndPrepareForData for target $targetBranchToCompare")
        val rootModelNode = tree.model.root as DefaultMutableTreeNode
        // Show loading state
        rootModelNode.removeAllChildren()
        rootModelNode.add(DefaultMutableTreeNode("Loading..."))
        (tree.model as DefaultTreeModel).reload(rootModelNode)
    }

    // New method to be called by ToolWindowStateService with fetched data
    fun displayChanges(categorizedChanges: CategorizedChanges?, forBranchName: String) {
        logger.debug("displayChanges called for branch '$forBranchName'. Current panel target: '$targetBranchToCompare'. Has data: ${categorizedChanges != null}")

        // Ensure this update is for the branch this panel is currently displaying
        if (forBranchName != targetBranchToCompare) {
            logger.warn("displayChanges received data for '$forBranchName', but panel is for '$targetBranchToCompare'. Ignoring.")
            return
        }

        ApplicationManager.getApplication().invokeLater {
            val rootModelNode = tree.model.root as DefaultMutableTreeNode
            rootModelNode.removeAllChildren() // Clear "Loading..." or old content

            if (categorizedChanges == null) {
                logger.warn("No categorized changes provided (possibly due to error) for $forBranchName.")
                rootModelNode.add(DefaultMutableTreeNode("Error loading changes for $forBranchName"))
            } else if (categorizedChanges.allChanges.isEmpty()) {
                logger.debug("No changes found for $forBranchName.")
                rootModelNode.add(DefaultMutableTreeNode("No changes found for $forBranchName"))
            } else {
                logger.debug("Building tree from ${categorizedChanges.allChanges.size} changes for $forBranchName.")
                buildTreeFromChanges(rootModelNode, categorizedChanges.allChanges)
            }

            // Reload the tree model and expand
            (tree.model as DefaultTreeModel).reload(rootModelNode)
            TreeUtil.expandAll(tree)
            logger.debug("Tree display updated for $forBranchName.")
        }
    }

    private fun buildTreeFromChanges(rootNode: DefaultMutableTreeNode, changes: List<Change>) {
        // project is a class field
        val commonRepositoryRoot = gitService.getCurrentRepository()?.root // Get root once

        for (changeItem in changes) {
            val currentFilePathObj = changeItem.afterRevision?.file ?: changeItem.beforeRevision?.file
            val rawPath = currentFilePathObj?.path
            var displayPath: String? = null
            val vf = currentFilePathObj?.virtualFile

            if (vf != null && commonRepositoryRoot != null) {
                displayPath = VfsUtilCore.getRelativePath(vf, commonRepositoryRoot, '/')
            } else if (rawPath != null && commonRepositoryRoot != null) {
                // Existing string manipulation fallback
                val repoRootPathString = commonRepositoryRoot.path
                if (rawPath.startsWith(repoRootPathString + "/")) {
                    displayPath = rawPath.substring(repoRootPathString.length + 1)
                } else if (rawPath.startsWith(repoRootPathString)) {
                    displayPath = rawPath.substring(repoRootPathString.length).let { if (it.startsWith("/")) it.substring(1) else it }
                } else {
                     displayPath = rawPath // Fallback if not under commonRepositoryRoot
                }
            }

            if (displayPath == null) displayPath = rawPath // Final fallback

            if (displayPath == null) continue // Should not happen if rawPath exists, but good for safety
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

    // Renamed from refreshTreeForBranch. Now signals ToolWindowStateService to fetch data.
    fun requestRefreshData() {
        logger.debug("requestRefreshData called for target: $targetBranchToCompare. Signaling ToolWindowStateService.")
        showLoadingStateAndPrepareForData() // Show loading state immediately
        project.service<ToolWindowStateService>().requestRefreshForBranch(targetBranchToCompare)
    }
}

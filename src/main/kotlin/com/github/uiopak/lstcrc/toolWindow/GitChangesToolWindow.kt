package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.services.GitService
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffAction
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.ui.JBColor
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import java.awt.*
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class GitChangesToolWindow(private val project: Project) {
    private val gitService = project.service<GitService>()

    fun createBranchContentView(branchName: String): JComponent {
        val tree = createChangesTree()
        refreshChangesTree(tree, branchName)

        val panel = JBPanel<JBPanel<*>>(BorderLayout())
        panel.add(JBScrollPane(tree), BorderLayout.CENTER)
        return panel
    }

    private fun createChangesTree(): Tree {
        val root = DefaultMutableTreeNode("Changes")
        val treeModel = DefaultTreeModel(root)

        val tree = object : Tree(treeModel) {
            override fun getScrollableTracksViewportWidth(): Boolean {
                return true
            }
        }

        tree.setCellRenderer(object : ColoredTreeCellRenderer() {
            override fun customizeCellRenderer(
                jTree: javax.swing.JTree,
                value: Any?,
                selected: Boolean,
                expanded: Boolean,
                leaf: Boolean,
                row: Int,
                hasFocus: Boolean
            ) {
                if (value !is DefaultMutableTreeNode) {
                    append(value?.toString() ?: "")
                    return
                }

                val userObject = value.userObject

                when (userObject) {
                    is Change -> {
                        val change = userObject
                        val filePath = change.afterRevision?.file ?: change.beforeRevision?.file
                        val fileName = filePath?.name ?: "Unknown File"

                        val fgColor: Color = if (selected) {
                            UIManager.getColor("Tree.selectionForeground")
                        } else {
                            when (change.type) {
                                Change.Type.NEW -> JBColor.namedColor("VersionControl.FileStatus.Added", JBColor.GREEN)
                                Change.Type.DELETED -> JBColor.namedColor("VersionControl.FileStatus.Deleted", JBColor.RED)
                                Change.Type.MOVED -> JBColor.namedColor("VersionControl.FileStatus.Modified", JBColor.BLUE)
                                Change.Type.MODIFICATION -> JBColor.namedColor("VersionControl.FileStatus.Modified", JBColor.BLUE)
                                else -> UIManager.getColor("Tree.foreground")
                            } ?: UIManager.getColor("Tree.foreground") // Fallback
                        }

                        val attributes = SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, fgColor)
                        append(fileName, attributes)

                        var fileIcon: Icon? = null
                        if (filePath != null) {
                            val virtualFile = filePath.virtualFile
                            if (virtualFile != null) {
                                fileIcon = virtualFile.fileType.icon
                            }
                            if (fileIcon == null) {
                                fileIcon = FileTypeManager.getInstance().getFileTypeByFileName(filePath.name).icon
                            }
                        }
                        icon = fileIcon ?: AllIcons.FileTypes.Unknown
                    }
                    is String -> { // Directory node
                        append(userObject, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                        icon = AllIcons.Nodes.Folder
                    }
                    else -> { // Root node or other
                        append(value.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    }
                }
            }
        })

        // --- REVERTED MOUSE ADAPTER LOGIC ---
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val clickPoint = e.point
                    var pathToOpen: javax.swing.tree.TreePath? = null

                    System.err.println("------------------- MOUSE CLICKED (Reverted Full Row Logic / File Icons) -------------------")
                    System.err.println("Click at: $clickPoint")
                    System.err.println("Tree component: ${tree.javaClass.name}, Size: ${tree.size}, VisibleRect: ${tree.visibleRect}")

                    // Strategy: Use getClosestRowForLocation to find the target row index.
                    // Then, verify the click's Y is within that row's Y-bounds.
                    // If so, consider it a hit on that row, as long as X is within tree's visible area.
                    val row = tree.getClosestRowForLocation(clickPoint.x, clickPoint.y)
                    System.err.println("1. Row from tree.getClosestRowForLocation(x, y): $row")

                    if (row != -1) {
                        // tree.getRowBounds(row) gives the bounds of the rendered content (icon + text),
                        // which is correct for checking the Y-coordinate.
                        val contentBoundsForRow = tree.getRowBounds(row)
                        System.err.println("2. Content bounds for row $row (tree.getRowBounds): $contentBoundsForRow")

                        if (contentBoundsForRow != null) {
                            // Check if Y-coordinate of click is within the Y-span of this row's content
                            val yWithinRowContent = clickPoint.y >= contentBoundsForRow.y && clickPoint.y < (contentBoundsForRow.y + contentBoundsForRow.height)
                            System.err.println("3. Is click Y (${clickPoint.y}) within row content Y-bounds ([${contentBoundsForRow.y} - ${contentBoundsForRow.y + contentBoundsForRow.height -1}])? $yWithinRowContent")

                            // Check if X-coordinate is within the tree's actual display width (its visible part)
                            // This allows clicking anywhere horizontally across the selected row.
                            val xWithinTreeVisible = clickPoint.x >= tree.visibleRect.x && clickPoint.x < (tree.visibleRect.x + tree.visibleRect.width)
                            System.err.println("4. Is click X (${clickPoint.x}) within tree's visible X-rect ([${tree.visibleRect.x} - ${tree.visibleRect.x + tree.visibleRect.width -1}])? $xWithinTreeVisible")

                            if (yWithinRowContent && xWithinTreeVisible) {
                                pathToOpen = tree.getPathForRow(row)
                                System.err.println("5. Conditions met (Y within content, X within tree visible). Path to open for row $row: $pathToOpen")
                            } else {
                                System.err.println("5. Conditions NOT met.")
                                if (!yWithinRowContent) System.err.println("   - Reason: Click Y ${clickPoint.y} is outside row content Y-bounds [${contentBoundsForRow.y}, ${contentBoundsForRow.y + contentBoundsForRow.height -1}].")
                                if (!xWithinTreeVisible) System.err.println("   - Reason: Click X ${clickPoint.x} is outside tree visible X-rect [${tree.visibleRect.x}, ${tree.visibleRect.x + tree.visibleRect.width-1}].")
                            }
                        } else {
                            System.err.println("2a. contentBoundsForRow for row $row is null. Cannot determine path this way.")
                        }
                    } else {
                        System.err.println("1a. No closest row found (getClosestRowForLocation returned -1). Click likely outside any row's vertical span.")
                    }
                    System.err.println("----------------------------------------------------------------------------------------------------")

                    if (pathToOpen != null) {
                        val node = pathToOpen.lastPathComponent as? DefaultMutableTreeNode
                        val userObject = node?.userObject
                        if (userObject is Change) {
                            if (tree.selectionPath != pathToOpen) {
                                tree.selectionPath = pathToOpen
                            }
                            openDiff(userObject)
                        } else {
                            System.err.println("ERROR: Path $pathToOpen resolved, but userObject is not a Change: $userObject")
                        }
                    } else {
                        System.err.println("No valid path for action determined for click at $clickPoint.")
                    }
                }
            }
        })
        // --- END REVERTED MOUSE ADAPTER LOGIC ---
        return tree
    }

    private fun refreshChangesTree(tree: javax.swing.JTree, branchName: String) {
        val changes = gitService.getChanges(branchName)
        val rootModelNode = tree.model.root as DefaultMutableTreeNode
        rootModelNode.removeAllChildren()
        buildTreeFromChanges(rootModelNode, changes)
        (tree.model as DefaultTreeModel).reload(rootModelNode)
        TreeUtil.expandAll(tree)
    }

    private fun buildTreeFromChanges(rootNode: DefaultMutableTreeNode, changes: List<Change>) {
        val repositoryRoot = gitService.getCurrentRepository()?.root
        for (change in changes) {
            val currentFilePathObj = change.afterRevision?.file ?: change.beforeRevision?.file
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
                if (!fileNodeExists) currentNode.add(DefaultMutableTreeNode(change))
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
                    childNode = if (isLastComponent) DefaultMutableTreeNode(change) else DefaultMutableTreeNode(componentName)
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

    fun showBranchSelectionDialog(onBranchSelected: (branchName: String) -> Unit) {
        val dialog = object : DialogWrapper(project, true) {
            private val searchTextField = SearchTextField()
            private var listPopup: JBPopup? = null
            private val allBranches = gitService.getAllBranches().sorted()
            private val filteredListModel = DefaultListModel<String>()
            init {
                title = "Select Branch to Compare with HEAD"
                init()
                searchTextField.addDocumentListener(object : DocumentListener {
                    override fun insertUpdate(e: DocumentEvent?) = filterAndShowPopup()
                    override fun removeUpdate(e: DocumentEvent?) = filterAndShowPopup()
                    override fun changedUpdate(e: DocumentEvent?) = filterAndShowPopup()
                })
                SwingUtilities.invokeLater { filterAndShowPopup() }
            }
            private fun filterAndShowPopup() {
                val searchText = searchTextField.text.trim()
                filteredListModel.clear()
                val sourceList = if (searchText.isEmpty()) allBranches else allBranches.filter { it.contains(searchText, ignoreCase = true) }
                sourceList.forEach { filteredListModel.addElement(it) }
                if (listPopup?.isDisposed == false) listPopup?.cancel()
                if (filteredListModel.isEmpty && searchText.isNotEmpty()) return
                if (filteredListModel.size > 0) {
                    val jbList = JBList(filteredListModel)
                    jbList.visibleRowCount = JBUI.scale(10).coerceAtMost(filteredListModel.size).coerceAtLeast(1)
                    listPopup = JBPopupFactory.getInstance().createListPopupBuilder(jbList)
                        .setMovable(false).setResizable(false).setRequestFocus(false)
                        .setItemChoosenCallback { jbList.selectedValue?.let { onBranchSelected(it); close(OK_EXIT_CODE) } }
                        .createPopup()
                    jbList.addKeyListener(object : KeyAdapter() {
                        override fun keyPressed(e: KeyEvent) {
                            if (e.keyCode == KeyEvent.VK_ENTER) {
                                jbList.selectedValue?.let { onBranchSelected(it); close(OK_EXIT_CODE); e.consume() }
                            }
                        }
                    })
                    if (searchTextField.isShowing) listPopup?.showUnderneathOf(searchTextField)
                }
            }
            override fun createCenterPanel(): JComponent {
                val panel = JBPanel<JBPanel<*>>(BorderLayout(0, JBUI.scale(5)))
                panel.add(JBLabel("Search for branch to compare with current HEAD:"), BorderLayout.NORTH)
                panel.add(searchTextField, BorderLayout.CENTER)
                panel.preferredSize = JBUI.size(450, 60)
                return panel
            }
            override fun getPreferredFocusedComponent(): JComponent? = searchTextField
            override fun doOKAction() {
                if (listPopup?.isVisible == true) {
                    (listPopup?.content as? JBList<*>)?.selectedValue?.let { onBranchSelected(it as String); super.doOKAction(); return }
                }
                if (!filteredListModel.isEmpty) {
                    onBranchSelected(filteredListModel.getElementAt(0)); super.doOKAction()
                } else {
                    if (searchTextField.text.isNotBlank() && filteredListModel.isEmpty) {
                        Messages.showWarningDialog(project, "No branch found matching '${searchTextField.text}'.", "Branch Not Found")
                        return
                    }
                    super.doCancelAction()
                }
            }
            override fun dispose() { listPopup?.cancel(); super.dispose() }
        }
        dialog.show()
    }
}
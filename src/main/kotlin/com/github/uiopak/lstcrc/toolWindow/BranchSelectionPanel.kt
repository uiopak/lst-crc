package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.services.GitService
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

/**
 * A UI panel that displays local and remote Git branches in a filterable tree,
 * allowing the user to select one.
 *
 * @param onBranchSelected A callback invoked with the name of the branch when the user selects it.
 */
class BranchSelectionPanel(
    project: Project,
    private val gitService: GitService,
    private val onBranchSelected: (branchName: String) -> Unit
) : JBPanel<BranchSelectionPanel>(BorderLayout(0, JBUI.scale(5))) {

    private val searchTextField = SearchTextField(false)
    private val tree: Tree

    init {
        tree = createBranchSelectionTree()

        searchTextField.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) { filterTree() }
            override fun removeUpdate(e: DocumentEvent?) { filterTree() }
            override fun changedUpdate(e: DocumentEvent?) { filterTree() }

            private fun filterTree() {
                val searchTerm = searchTextField.text.trim()
                tree.model = buildBranchTreeModel(searchTerm)
                TreeUtil.expandAll(tree)
            }
        })

        this.add(searchTextField, BorderLayout.NORTH)
        val scrollPane = JBScrollPane(tree)
        this.add(scrollPane, BorderLayout.CENTER)
    }

    private fun createBranchSelectionTree(): Tree {
        val newTree = object : Tree() {
            override fun getScrollableTracksViewportWidth(): Boolean = true
        }
        newTree.isRootVisible = false
        newTree.showsRootHandles = true

        newTree.setCellRenderer(object : ColoredTreeCellRenderer() {
            override fun customizeCellRenderer(
                jTree: JTree, value: Any?, selected: Boolean, expanded: Boolean,
                leaf: Boolean, row: Int, hasFocus: Boolean
            ) {
                if (value !is DefaultMutableTreeNode) {
                    append(value?.toString() ?: "", SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    return
                }
                val userObject = value.userObject
                when (userObject) {
                    is BranchCategory -> {
                        append(userObject.displayName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                        icon = if (userObject.type == BranchCategoryType.LOCAL) AllIcons.Nodes.Folder else AllIcons.Nodes.WebFolder
                    }
                    is String -> {
                        append(userObject, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                        icon = AllIcons.Vcs.Branch
                    }
                    else -> append(value.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES)
                }
            }
        })

        newTree.model = buildBranchTreeModel("")
        TreeUtil.expandAll(newTree)

        newTree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 1) {
                    val clickPoint = e.point
                    var currentTargetPath: TreePath? = null
                    val row = newTree.getClosestRowForLocation(clickPoint.x, clickPoint.y)
                    if (row != -1) {
                        val rowBounds = newTree.getRowBounds(row)
                        if (rowBounds != null) {
                            val yWithinRowContent = clickPoint.y >= rowBounds.y && clickPoint.y < (rowBounds.y + rowBounds.height)
                            val xWithinTreeVisible = clickPoint.x >= newTree.visibleRect.x && clickPoint.x < (newTree.visibleRect.x + newTree.visibleRect.width)
                            if (yWithinRowContent && xWithinTreeVisible) {
                                currentTargetPath = newTree.getPathForRow(row)
                            }
                        }
                    }
                    if (currentTargetPath != null) {
                        val node = currentTargetPath.lastPathComponent as? DefaultMutableTreeNode
                        if (node != null && node.isLeaf) {
                            val parentNode = node.parent as? DefaultMutableTreeNode
                            if (parentNode?.userObject is BranchCategory) {
                                (node.userObject as? String)?.let { branchName ->
                                    onBranchSelected(branchName)
                                }
                            }
                        }
                    }
                }
            }
        })
        return newTree
    }

    private enum class BranchCategoryType { LOCAL, REMOTE }
    private data class BranchCategory(val type: BranchCategoryType, val displayName: String)

    private fun buildBranchTreeModel(searchTerm: String): DefaultTreeModel {
        val rootNode = DefaultMutableTreeNode("Root")
        val localCategory = BranchCategory(BranchCategoryType.LOCAL, LstCrcBundle.message("branch.type.local"))
        val remoteCategory = BranchCategory(BranchCategoryType.REMOTE, LstCrcBundle.message("branch.type.remote"))
        val localBranchesNode = DefaultMutableTreeNode(localCategory)
        val remoteBranchesNode = DefaultMutableTreeNode(remoteCategory)

        val localBranches = gitService.getLocalBranches().sorted()
        val remoteBranches = gitService.getRemoteBranches().sorted()

        localBranches.filter { it.contains(searchTerm, ignoreCase = true) }
            .forEach { localBranchesNode.add(DefaultMutableTreeNode(it)) }

        remoteBranches.filter { it.contains(searchTerm, ignoreCase = true) }
            .forEach { remoteBranchesNode.add(DefaultMutableTreeNode(it)) }

        if (localBranchesNode.childCount > 0) {
            rootNode.add(localBranchesNode)
        }
        if (remoteBranchesNode.childCount > 0) {
            rootNode.add(remoteBranchesNode)
        }
        return DefaultTreeModel(rootNode)
    }

    fun getPanel(): JComponent = this
}
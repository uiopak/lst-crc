package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.services.GitService
import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.thisLogger
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

class BranchSelectionPanel(
    project: Project, // Kept for consistency, though not directly used by this panel's current methods
    private val gitService: GitService,
    private val onBranchSelected: (branchName: String) -> Unit
) : JBPanel<BranchSelectionPanel>(BorderLayout(0, JBUI.scale(5))) { // Inherit from JBPanel

    private val logger = thisLogger()
    private val searchTextField = SearchTextField(false) // false for history disabled
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
                    "Local" -> {
                        append(userObject.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES)
                        icon = AllIcons.Nodes.Folder
                    }
                    "Remote" -> {
                        append(userObject.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES)
                        icon = AllIcons.Nodes.WebFolder
                    }
                    is String -> {
                        append(userObject, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                        icon = AllIcons.Vcs.Branch
                    }
                    else -> append(value.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES)
                }
            }
        })

        newTree.model = buildBranchTreeModel("") // Initial model
        TreeUtil.expandAll(newTree)

        newTree.addMouseListener(object : MouseAdapter() {
            private fun logBranchTreeClick(message: String, additionalInfo: String = "") {
                // Logging removed as per previous subtask
            }

            override fun mouseClicked(e: MouseEvent) {
                logBranchTreeClick("START", "- Point(${e.x}, ${e.y}), ClickCount=${e.clickCount}")
                if (e.clickCount == 1) {
                    val clickPoint = e.point
                    var currentTargetPath: TreePath? = null
                    val row = newTree.getClosestRowForLocation(clickPoint.x, clickPoint.y)
                    logBranchTreeClick("Row (from getClosestRowForLocation)=$row")
                    if (row != -1) {
                        val rowBounds = newTree.getRowBounds(row)
                        logBranchTreeClick("RowBounds=$rowBounds")
                        if (rowBounds != null) {
                            val yWithinRowContent = clickPoint.y >= rowBounds.y && clickPoint.y < (rowBounds.y + rowBounds.height)
                            val xWithinTreeVisible = clickPoint.x >= newTree.visibleRect.x && clickPoint.x < (newTree.visibleRect.x + newTree.visibleRect.width)
                            logBranchTreeClick("yWithinRowContent=$yWithinRowContent, xWithinTreeVisible=$xWithinTreeVisible, newTree.visibleRect=${newTree.visibleRect}")
                            if (yWithinRowContent && xWithinTreeVisible) {
                                currentTargetPath = newTree.getPathForRow(row)
                            }
                        }
                    }
                    logBranchTreeClick("CurrentTargetPathDetermined=$currentTargetPath")
                    if (currentTargetPath != null) {
                        val node = currentTargetPath.lastPathComponent as? DefaultMutableTreeNode
                        logBranchTreeClick("NodeUserObject=${node?.userObject}, IsNodeLeaf=${node?.isLeaf}")
                        if (node != null && node.isLeaf) {
                            val parentNode = node.parent as? DefaultMutableTreeNode
                            logBranchTreeClick("ParentNodeUserObject=${parentNode?.userObject}")
                            if (parentNode != null && (parentNode.userObject == "Local" || parentNode.userObject == "Remote")) {
                                (node.userObject as? String)?.let { branchName ->
                                    logBranchTreeClick("INVOKING onBranchSelected with '$branchName'")
                                    onBranchSelected(branchName)
                                }
                            } else {
                                logBranchTreeClick("Did NOT invoke onBranchSelected (parent check failed or not a String userObject).")
                            }
                        } else {
                            logBranchTreeClick("Did NOT invoke onBranchSelected (node was null or not a leaf).")
                        }
                    } else {
                        logBranchTreeClick("Did NOT invoke onBranchSelected (currentTargetPath was null).")
                    }
                }
                logBranchTreeClick("END")
            }
        })
        return newTree
    }

    private fun buildBranchTreeModel(searchTerm: String): DefaultTreeModel {
        val rootNode = DefaultMutableTreeNode("Root")
        val localBranchesNode = DefaultMutableTreeNode("Local")
        val remoteBranchesNode = DefaultMutableTreeNode("Remote")

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

    fun getPanel(): JComponent = this // Expose the panel itself
}

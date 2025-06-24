package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.services.GitService
import com.intellij.icons.AllIcons
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * A UI panel that displays local and remote Git branches in a filterable, hierarchical tree,
 * allowing the user to select one.
 *
 * @param onBranchSelected A callback invoked with the name of the branch when the user selects it.
 */
class BranchSelectionPanel(
    private val gitService: GitService,
    private val onBranchSelected: (branchName: String) -> Unit
) : JBPanel<BranchSelectionPanel>(BorderLayout()) {

    private val searchTextField = SearchTextField(false)
    private val tree: Tree
    private val fullTreeModel: DefaultTreeModel

    // Data classes to represent nodes in the tree clearly.
    private data class BranchCategory(val type: BranchCategoryType, val displayName: String)
    private data class BranchInfo(val displayName: String, val fullBranchName: String)
    private enum class BranchCategoryType { LOCAL, REMOTE }

    init {
        fullTreeModel = buildFullBranchTreeModel()
        tree = createBranchSelectionTree()

        searchTextField.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = filterTree()
            override fun removeUpdate(e: DocumentEvent?) = filterTree()
            override fun changedUpdate(e: DocumentEvent?) = filterTree()
        })

        this.add(searchTextField, BorderLayout.NORTH)
        val scrollPane = JBScrollPane(tree).apply {
            border = JBUI.Borders.empty()
        }
        this.add(scrollPane, BorderLayout.CENTER)
    }

    private fun filterTree() {
        val searchTerm = searchTextField.text
        tree.putClientProperty("search.term", searchTerm) // Pass term to renderer for highlighting

        val newModel = if (searchTerm.isBlank()) {
            fullTreeModel
        } else {
            DefaultTreeModel(buildFilteredRoot(searchTerm))
        }
        tree.model = newModel
        TreeUtil.expandAll(tree)
    }

    private fun buildFilteredRoot(searchTerm: String): DefaultMutableTreeNode {
        val originalRoot = fullTreeModel.root as DefaultMutableTreeNode
        val filteredRoot = originalRoot.clone() as DefaultMutableTreeNode
        filteredRoot.removeAllChildren()

        for (child in originalRoot.children()) {
            val originalCategoryNode = child as DefaultMutableTreeNode
            val filteredCategoryNode = cloneNodeIfMatching(originalCategoryNode, searchTerm)
            if (filteredCategoryNode != null) {
                filteredRoot.add(filteredCategoryNode)
            }
        }
        return filteredRoot
    }

    /**
     * Clones a node if it or any of its descendants match the search term.
     */
    private fun cloneNodeIfMatching(originalNode: DefaultMutableTreeNode, searchTerm: String): DefaultMutableTreeNode? {
        val userObject = originalNode.userObject

        // Get the text representation of the node for searching.
        val textToSearch = when (userObject) {
            is BranchInfo -> userObject.fullBranchName
            is String -> userObject
            is BranchCategory -> userObject.displayName
            else -> ""
        }

        // Case 1: The node's text itself matches. Clone the entire subtree.
        if (textToSearch.contains(searchTerm, ignoreCase = true)) {
            return deepCloneNode(originalNode)
        }

        // Case 2: The node's text doesn't match, but a descendant might.
        // Recursively check children.
        val matchingChildren = mutableListOf<DefaultMutableTreeNode>()
        if (!originalNode.isLeaf) {
            for (child in originalNode.children()) {
                val matchingChild = cloneNodeIfMatching(child as DefaultMutableTreeNode, searchTerm)
                if (matchingChild != null) {
                    matchingChildren.add(matchingChild)
                }
            }
        }

        // If any children matched, create a clone of the current node and add only the matching children.
        if (matchingChildren.isNotEmpty()) {
            val clonedNode = DefaultMutableTreeNode(userObject)
            matchingChildren.forEach { clonedNode.add(it) }
            return clonedNode
        }

        // No match for this node or any of its descendants.
        return null
    }

    /**
     * Helper function to create a deep copy of a tree node and its children.
     */
    private fun deepCloneNode(node: DefaultMutableTreeNode): DefaultMutableTreeNode {
        val newNode = node.clone() as DefaultMutableTreeNode
        newNode.removeAllChildren() // `clone()` is shallow, so we clear and re-add deep-cloned children.
        for (child in node.children()) {
            newNode.add(deepCloneNode(child as DefaultMutableTreeNode))
        }
        return newNode
    }


    fun requestFocusOnSearchField() {
        UIUtil.invokeLaterIfNeeded {
            searchTextField.requestFocusInWindow()
        }
    }

    private fun createBranchSelectionTree(): Tree {
        val newTree = Tree(fullTreeModel)
        newTree.isRootVisible = false
        newTree.showsRootHandles = true

        newTree.cellRenderer = object : ColoredTreeCellRenderer() {
            override fun customizeCellRenderer(
                jtree: JTree, value: Any?, selected: Boolean, expanded: Boolean,
                leaf: Boolean, row: Int, hasFocus: Boolean
            ) {
                val node = value as? DefaultMutableTreeNode ?: return
                val searchTerm = jtree.getClientProperty("search.term") as? String

                val (text, icon) = when (val userObject = node.userObject) {
                    is BranchCategory -> userObject.displayName to if (userObject.type == BranchCategoryType.LOCAL) AllIcons.Nodes.Folder else AllIcons.Nodes.WebFolder
                    is BranchInfo -> userObject.displayName to AllIcons.Vcs.Branch
                    is String -> userObject to AllIcons.Nodes.Folder
                    else -> return
                }

                this.icon = icon
                val attributes = SimpleTextAttributes.REGULAR_ATTRIBUTES
                val highlightAttributes = SimpleTextAttributes(attributes.style or SimpleTextAttributes.STYLE_SEARCH_MATCH, attributes.fgColor)

                if (searchTerm.isNullOrBlank()) {
                    append(text, attributes)
                } else {
                    var lastIndex = 0
                    var matchIndex = text.indexOf(searchTerm, ignoreCase = true)
                    while (matchIndex >= 0) {
                        if (matchIndex > lastIndex) {
                            append(text.substring(lastIndex, matchIndex), attributes)
                        }
                        append(text.substring(matchIndex, matchIndex + searchTerm.length), highlightAttributes)
                        lastIndex = matchIndex + searchTerm.length
                        matchIndex = text.indexOf(searchTerm, lastIndex, ignoreCase = true)
                    }
                    if (lastIndex < text.length) {
                        append(text.substring(lastIndex), attributes)
                    }
                }
            }
        }
        TreeUtil.expandAll(newTree)

        // Combined mouse and key listener for better usability.
        newTree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                // Handle single and double clicks to select a branch.
                if (e.clickCount < 1) return

                val row = newTree.getClosestRowForLocation(e.x, e.y)
                if (row == -1) return

                // This is a more robust way to check if the click is within the row's vertical bounds.
                val bounds = newTree.getRowBounds(row)
                if (bounds == null || e.y < bounds.y || e.y >= bounds.y + bounds.height) {
                    return
                }

                val path = newTree.getPathForRow(row) ?: return
                val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                if (node.isLeaf) {
                    (node.userObject as? BranchInfo)?.let {
                        onBranchSelected(it.fullBranchName)
                    }
                }
            }
        })

        newTree.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                // Allow selection with the Enter key.
                if (e.keyCode == KeyEvent.VK_ENTER) {
                    val path = newTree.selectionPath ?: return
                    val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                    if (node.isLeaf) {
                        (node.userObject as? BranchInfo)?.let {
                            onBranchSelected(it.fullBranchName)
                            e.consume() // Prevent the event from being processed further.
                        }
                    }
                }
            }

            override fun keyTyped(e: KeyEvent) {
                // Speed-search functionality: typing redirects focus to the search field.
                if (searchTextField.textEditor.hasFocus()) return
                if (e.keyChar == KeyEvent.CHAR_UNDEFINED || e.keyChar < ' ' || e.isControlDown || e.isMetaDown || e.isAltDown) {
                    return
                }
                searchTextField.requestFocusInWindow()
                searchTextField.text = searchTextField.text + e.keyChar
            }
        })

        return newTree
    }

    private fun buildFullBranchTreeModel(): DefaultTreeModel {
        val rootNode = DefaultMutableTreeNode("Root")
        val localCategory = BranchCategory(BranchCategoryType.LOCAL, LstCrcBundle.message("branch.type.local"))
        val remoteCategory = BranchCategory(BranchCategoryType.REMOTE, LstCrcBundle.message("branch.type.remote"))
        val localBranchesNode = DefaultMutableTreeNode(localCategory)
        addBranchNodes(localBranchesNode, gitService.getLocalBranches())
        if (localBranchesNode.childCount > 0) {
            rootNode.add(localBranchesNode)
        }
        val remoteBranchesNode = DefaultMutableTreeNode(remoteCategory)
        addBranchNodes(remoteBranchesNode, gitService.getRemoteBranches())
        if (remoteBranchesNode.childCount > 0) {
            rootNode.add(remoteBranchesNode)
        }
        return DefaultTreeModel(rootNode)
    }

    private fun addBranchNodes(parentNode: DefaultMutableTreeNode, branches: List<String>) {
        val branchNodes = mutableMapOf<String, DefaultMutableTreeNode>()
        for (branchName in branches.sorted()) {
            val parts = branchName.split('/')
            var currentParent = parentNode
            var currentPath = ""
            for (i in parts.indices) {
                val part = parts[i]
                currentPath = if (currentPath.isEmpty()) part else "$currentPath/$part"
                var node = branchNodes[currentPath]
                if (node == null) {
                    val isLeaf = (i == parts.size - 1)
                    val userObject: Any = if (isLeaf) {
                        BranchInfo(part, branchName)
                    } else {
                        part
                    }
                    node = DefaultMutableTreeNode(userObject)
                    branchNodes[currentPath] = node
                    currentParent.add(node)
                }
                currentParent = node
            }
        }
    }

    fun getPanel(): JComponent = this
}
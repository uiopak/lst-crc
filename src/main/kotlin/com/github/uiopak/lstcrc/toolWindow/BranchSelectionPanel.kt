package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.services.BranchSnapshot
import com.github.uiopak.lstcrc.services.GitService
import com.intellij.openapi.Disposable
import com.intellij.icons.AllIcons
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import git4idea.repo.GitRepository
import java.awt.BorderLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JTree
import javax.swing.Icon
import javax.swing.event.DocumentEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

/**
 * A UI panel that displays Git branches in a filterable, hierarchical tree, allowing the user to select one.
 * It can be scoped to a single repository or show branches from all repositories in the project.
 *
 * @param gitService The service used to fetch repository information.
 * @param repository An optional specific repository to scope the branch list to. If null, branches from the primary repository are shown.
 * @param onBranchSelected A callback invoked with the name of the branch when the user selects it.
 */
class BranchSelectionPanel(
    gitService: GitService,
    repository: GitRepository?,
    branchSnapshot: BranchSnapshot? = null,
    private val onBranchSelected: (branchName: String) -> Unit
) : JBPanel<BranchSelectionPanel>(BorderLayout()), Disposable {

    private val searchTextField = SearchTextField(false)
    private val localBranches: List<String>
    private val remoteBranches: List<String>
    private val tree: Tree
    private val filterDocumentListener = object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) = filterTree()
    }

    // Data classes to represent nodes in the tree clearly.
    private data class BranchCategory(val type: BranchCategoryType, val displayName: String)
    private data class BranchInfo(val displayName: String, val fullBranchName: String)
    private enum class BranchCategoryType { LOCAL, REMOTE }

    init {
        // Use the pre-fetched snapshot if available; otherwise fall back to the
        // Git4Idea repository model which is already cached in memory (no I/O).
        // This constructor may run on the EDT, so it must never run git commands.
        val targetRepo = repository ?: gitService.getPrimaryRepository()
        localBranches = branchSnapshot?.localBranches?.takeIf { it.isNotEmpty() }
            ?: targetRepo?.branches?.localBranches?.map { it.name }.orEmpty()
        remoteBranches = branchSnapshot?.remoteBranches?.takeIf { it.isNotEmpty() }
            ?: targetRepo?.branches?.remoteBranches?.map { it.name }.orEmpty()
        tree = createBranchSelectionTree()

        searchTextField.addDocumentListener(filterDocumentListener)

        add(searchTextField, BorderLayout.NORTH)

        val scrollPane = JBScrollPane(tree).apply {
            border = JBUI.Borders.empty()
        }
        this.add(scrollPane, BorderLayout.CENTER)
    }

    private fun filterTree() {
        val searchTerm = searchTextField.text
        tree.putClientProperty("search.term", searchTerm)

        tree.model = buildBranchTreeModel(searchTerm)
        TreeUtil.expandAll(tree)
        refreshSearchSelection(searchTerm)
    }

    /** Selects the first node (pre-order) whose text contains [searchTerm], or clears the selection. */
    private fun refreshSearchSelection(searchTerm: String) {
        val root = tree.model.root as? DefaultMutableTreeNode
        val match = root.takeIf { searchTerm.isNotBlank() }?.let { rootNode ->
            TreeUtil.findNode(rootNode) { node ->
                val text = when (val userObject = node.userObject) {
                    is BranchInfo -> userObject.fullBranchName
                    is String -> userObject
                    is BranchCategory -> userObject.displayName
                    else -> ""
                }
                text.contains(searchTerm, ignoreCase = true)
            }
        }
        if (match == null) {
            tree.clearSelection()
            return
        }
        val path = TreePath(match.path)
        tree.selectionPath = path
        tree.scrollPathToVisible(path)
    }

    fun requestFocusOnSearchField() {
        UIUtil.invokeLaterIfNeeded {
            searchTextField.requestFocusInWindow()
        }
    }

    @Suppress("unused")
    fun visibleLeafTextsForTest(): List<String> {
        return (0 until tree.rowCount)
            .mapNotNull { row -> tree.getPathForRow(row)?.lastPathComponent as? DefaultMutableTreeNode }
            .filter(DefaultMutableTreeNode::isLeaf)
            .mapNotNull { resolveBranchNodePresentation(it)?.first }
    }

    @Suppress("unused")
    fun selectVisibleBranchForTest(branchName: String): Boolean {
        for (row in 0 until tree.rowCount) {
            val path = tree.getPathForRow(row) ?: continue
            val node = path.lastPathComponent as? DefaultMutableTreeNode ?: continue
            val branchInfo = node.userObject as? BranchInfo ?: continue
            if (branchInfo.fullBranchName != branchName) {
                continue
            }

            tree.selectionPath = path
            tree.scrollPathToVisible(path)
            return selectBranchNode(node)
        }
        return false
    }

    private fun createBranchSelectionTree(): Tree {
        return Tree(buildBranchTreeModel()).apply {
            isRootVisible = false
            showsRootHandles = true
            cellRenderer = createBranchTreeCellRenderer()
            TreeUtil.expandAll(this)
            addMouseListener(createBranchTreeMouseListener(this))
            addKeyListener(createBranchTreeKeyListener(this))
        }
    }

    private fun createBranchTreeCellRenderer(): ColoredTreeCellRenderer {
        return object : ColoredTreeCellRenderer() {
            override fun customizeCellRenderer(
                jtree: JTree, value: Any?, selected: Boolean, expanded: Boolean,
                leaf: Boolean, row: Int, hasFocus: Boolean
            ) {
                val node = value as? DefaultMutableTreeNode ?: return
                val (text, nodeIcon) = resolveBranchNodePresentation(node) ?: return
                val searchTerm = jtree.getClientProperty("search.term") as? String
                icon = nodeIcon

                if (searchTerm.isNullOrBlank()) {
                    append(text, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                } else {
                    appendSearchAwareText(text, searchTerm)
                }
            }
        }
    }

    private fun ColoredTreeCellRenderer.appendSearchAwareText(text: String, searchTerm: String) {
        val attributes = SimpleTextAttributes.REGULAR_ATTRIBUTES
        val highlightAttributes = SimpleTextAttributes(
            attributes.style or SimpleTextAttributes.STYLE_SEARCH_MATCH,
            attributes.fgColor
        )

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

    private fun resolveBranchNodePresentation(node: DefaultMutableTreeNode): Pair<String, Icon>? {
        return when (val userObject = node.userObject) {
            is BranchCategory -> userObject.displayName to when (userObject.type) {
                BranchCategoryType.LOCAL -> AllIcons.Nodes.Folder
                BranchCategoryType.REMOTE -> AllIcons.Nodes.WebFolder
            }
            is BranchInfo -> userObject.displayName to AllIcons.Vcs.Branch
            is String -> userObject to AllIcons.Nodes.Folder
            else -> null
        }
    }

    private fun createBranchTreeMouseListener(tree: Tree): MouseAdapter {
        return object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount < 1) return

                val node = TreeUtil.getPathForLocation(tree, e.x, e.y)?.lastPathComponent as? DefaultMutableTreeNode ?: return
                selectBranchNode(node)
            }
        }
    }

    private fun createBranchTreeKeyListener(tree: Tree): KeyAdapter {
        return object : KeyAdapter() {
            override fun keyTyped(e: KeyEvent) {
                if (!shouldRedirectKeyTypedToSearch(e)) return

                searchTextField.requestFocusInWindow()
                searchTextField.text += e.keyChar
                e.consume()
            }

            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode != KeyEvent.VK_ENTER) return

                val node = tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode ?: return
                if (selectBranchNode(node)) {
                    e.consume()
                }
            }
        }
    }

    private fun shouldRedirectKeyTypedToSearch(e: KeyEvent): Boolean {
        if (searchTextField.textEditor.hasFocus()) return false
        if (e.keyChar == KeyEvent.CHAR_UNDEFINED || e.keyChar < ' ') return false
        return !e.isControlDown && !e.isMetaDown && !e.isAltDown
    }

    private fun selectBranchNode(node: DefaultMutableTreeNode): Boolean {
        if (!node.isLeaf) return false

        val branchInfo = node.userObject as? BranchInfo ?: return false
        onBranchSelected(branchInfo.fullBranchName)
        return true
    }

    /**
     * Builds the branch tree, keeping only branches that match [searchTerm]: every branch of a category
     * whose name matches, otherwise branches whose full name matches (which covers folder matches, as a
     * folder is part of the full name of every branch below it).
     */
    private fun buildBranchTreeModel(searchTerm: String = ""): DefaultTreeModel {
        val rootNode = DefaultMutableTreeNode("Root")
        val localCategory = BranchCategory(BranchCategoryType.LOCAL, LstCrcBundle.message("branch.type.local"))
        val remoteCategory = BranchCategory(BranchCategoryType.REMOTE, LstCrcBundle.message("branch.type.remote"))
        addBranchCategoryNode(rootNode, localCategory, localBranches, searchTerm)
        addBranchCategoryNode(rootNode, remoteCategory, remoteBranches, searchTerm)
        return DefaultTreeModel(rootNode)
    }

    private fun addBranchCategoryNode(
        rootNode: DefaultMutableTreeNode,
        category: BranchCategory,
        branches: List<String>,
        searchTerm: String
    ) {
        val matchingBranches = if (searchTerm.isBlank() || category.displayName.contains(searchTerm, ignoreCase = true)) {
            branches
        } else {
            branches.filter { it.contains(searchTerm, ignoreCase = true) }
        }
        val categoryNode = DefaultMutableTreeNode(category)
        addBranchNodes(categoryNode, matchingBranches)
        if (categoryNode.childCount > 0) {
            rootNode.add(categoryNode)
        }
    }

    private fun addBranchNodes(parentNode: DefaultMutableTreeNode, branches: List<String>) {
        val branchNodes = mutableMapOf<String, DefaultMutableTreeNode>()
        for (branchName in branches.sorted()) {
            val parts = branchName.split('/')
            var currentParent = parentNode
            var currentPath = ""
            parts.forEachIndexed { i, part ->
                currentPath = if (currentPath.isEmpty()) part else "$currentPath/$part"
                val parent = currentParent
                currentParent = branchNodes.getOrPut(currentPath) {
                    // The last part is the branch itself; the parts before it are folders.
                    DefaultMutableTreeNode(if (i == parts.lastIndex) BranchInfo(part, branchName) else part).also(parent::add)
                }
            }
        }
    }

    override fun dispose() {
        searchTextField.removeDocumentListener(filterDocumentListener)
    }
}
package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.services.BranchSnapshot
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
import git4idea.repo.GitRepository
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.intellij.openapi.application.EDT

/**
 * A UI panel that displays Git branches in a filterable, hierarchical tree, allowing the user to select one.
 * It can be scoped to a single repository or show branches from all repositories in the project.
 *
 * @param gitService The service used to fetch repository information.
 * @param repository An optional specific repository to scope the branch list to. If null, branches from the primary repository are shown.
 * @param onBranchSelected A callback invoked with the name of the branch when the user selects it.
 */
class BranchSelectionPanel(
    private val gitService: GitService,
    private val repository: GitRepository?,
    private val branchSnapshot: BranchSnapshot? = null,
    private val onBranchSelected: (branchName: String) -> Unit
) : JBPanel<BranchSelectionPanel>(BorderLayout()) {

    private val searchTextField = SearchTextField(false)
    private val tree: Tree
    private val fullTreeModel: DefaultTreeModel
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var filterJob: Job? = null

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

        add(searchTextField, BorderLayout.NORTH)

        val scrollPane = JBScrollPane(tree).apply {
            border = JBUI.Borders.empty()
        }
        this.add(scrollPane, BorderLayout.CENTER)
    }

    private fun filterTree() {
        val searchTerm = searchTextField.text
        tree.putClientProperty("search.term", searchTerm)

        filterJob?.cancel()
        filterJob = scope.launch {
            val newModel = if (searchTerm.isBlank()) {
                fullTreeModel
            } else {
                DefaultTreeModel(buildFilteredRoot(searchTerm))
            }
            
            withContext(Dispatchers.EDT) {
                tree.model = newModel
                TreeUtil.expandAll(tree)
                refreshSearchSelection(searchTerm)
            }
        }
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

    private fun cloneNodeIfMatching(originalNode: DefaultMutableTreeNode, searchTerm: String): DefaultMutableTreeNode? {
        val textToSearch = when (val userObject = originalNode.userObject) {
            is BranchInfo -> userObject.fullBranchName
            is String -> userObject
            is BranchCategory -> userObject.displayName
            else -> ""
        }

        if (textToSearch.contains(searchTerm, ignoreCase = true)) {
            return deepCloneNode(originalNode)
        }

        val matchingChildren = mutableListOf<DefaultMutableTreeNode>()
        if (!originalNode.isLeaf) {
            for (child in originalNode.children()) {
                val matchingChild = cloneNodeIfMatching(child as DefaultMutableTreeNode, searchTerm)
                if (matchingChild != null) {
                    matchingChildren.add(matchingChild)
                }
            }
        }

        if (matchingChildren.isNotEmpty()) {
            return DefaultMutableTreeNode(originalNode.userObject).apply {
                matchingChildren.forEach(::add)
            }
        }

        return null
    }

    private fun deepCloneNode(node: DefaultMutableTreeNode): DefaultMutableTreeNode {
        val newNode = node.clone() as DefaultMutableTreeNode
        newNode.removeAllChildren()
        for (child in node.children()) {
            newNode.add(deepCloneNode(child as DefaultMutableTreeNode))
        }
        return newNode
    }

    private fun refreshSearchSelection(searchTerm: String) {
        if (searchTerm.isBlank()) {
            tree.clearSelection()
            return
        }

        val firstMatchingPath = findFirstMatchingPath(tree.model.root as? DefaultMutableTreeNode, searchTerm)
        if (firstMatchingPath != null) {
            tree.selectionPath = firstMatchingPath
            tree.scrollPathToVisible(firstMatchingPath)
        } else {
            tree.clearSelection()
        }
    }

    private fun findFirstMatchingPath(node: DefaultMutableTreeNode?, searchTerm: String): javax.swing.tree.TreePath? {
        if (node == null) return null

        val textToSearch = when (val userObject = node.userObject) {
            is BranchInfo -> userObject.fullBranchName
            is String -> userObject
            is BranchCategory -> userObject.displayName
            else -> ""
        }
        if (textToSearch.contains(searchTerm, ignoreCase = true)) {
            return javax.swing.tree.TreePath(node.path)
        }

        for (child in node.children()) {
            val matchingPath = findFirstMatchingPath(child as DefaultMutableTreeNode, searchTerm)
            if (matchingPath != null) {
                return matchingPath
            }
        }

        return null
    }

    fun requestFocusOnSearchField() {
        UIUtil.invokeLaterIfNeeded {
            searchTextField.requestFocusInWindow()
        }
    }

    private fun createBranchSelectionTree(): Tree {
        return Tree(fullTreeModel).apply {
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

    private fun resolveBranchNodePresentation(node: DefaultMutableTreeNode): Pair<String, javax.swing.Icon>? {
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

                val path = TreeUtil.getPathForLocation(tree, e.x, e.y) ?: return
                val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
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

                val path = tree.selectionPath ?: return
                val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
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

    private fun buildFullBranchTreeModel(): DefaultTreeModel {
        val rootNode = DefaultMutableTreeNode("Root")
        val localCategory = BranchCategory(BranchCategoryType.LOCAL, LstCrcBundle.message("branch.type.local"))
        val remoteCategory = BranchCategory(BranchCategoryType.REMOTE, LstCrcBundle.message("branch.type.remote"))

        // Use the pre-fetched snapshot if available; otherwise fall back to the
        // Git4Idea repository model which is already cached in memory (no I/O).
        // This method may be called on the EDT, so it must never run git commands.
        val targetRepo = repository ?: gitService.getPrimaryRepository()
        val localBranches = branchSnapshot?.localBranches?.takeIf { it.isNotEmpty() }
            ?: targetRepo?.branches?.localBranches?.map { it.name }
            ?: emptyList()
        val remoteBranches = branchSnapshot?.remoteBranches?.takeIf { it.isNotEmpty() }
            ?: targetRepo?.branches?.remoteBranches?.map { it.name }
            ?: emptyList()

        val localBranchesNode = DefaultMutableTreeNode(localCategory)
        addBranchNodes(localBranchesNode, localBranches)
        if (localBranchesNode.childCount > 0) {
            rootNode.add(localBranchesNode)
        }
        val remoteBranchesNode = DefaultMutableTreeNode(remoteCategory)
        addBranchNodes(remoteBranchesNode, remoteBranches)
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
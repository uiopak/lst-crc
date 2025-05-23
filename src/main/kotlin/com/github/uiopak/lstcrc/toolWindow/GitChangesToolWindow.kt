package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.services.GitService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffAction
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.ui.SearchTextField
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel

class GitChangesToolWindow(private val project: Project) {
    private val gitService = project.service<GitService>()
    private val tabbedPane = JBTabbedPane()
    private val tabsMap = mutableMapOf<String, Component>()

    fun getContent(): JComponent {
        val panel = JBPanel<JBPanel<*>>(BorderLayout())
        
        // Create the tabbed pane
        panel.add(tabbedPane, BorderLayout.CENTER)
        
        // Add the "+" button to add new tabs
        val addButton = JButton("+")
        addButton.addActionListener { showBranchSelectionDialog() }
        
        val buttonPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT))
        buttonPanel.add(addButton)
        panel.add(buttonPanel, BorderLayout.NORTH)
        
        // Add a default tab for the current branch
        val currentBranch = gitService.getCurrentBranch() ?: "HEAD"
        addTab(currentBranch)
        
        return panel
    }
    
    private fun addTab(branchName: String) {
        if (tabsMap.containsKey(branchName)) {
            tabbedPane.selectedIndex = tabbedPane.indexOfTab(branchName)
            return
        }
        
        val tabContent = createTabContent(branchName)
        tabsMap[branchName] = tabContent
        
        // Add the tab with a close button
        val tabPanel = JBPanel<JBPanel<*>>(BorderLayout())
        tabPanel.add(JBLabel(branchName), BorderLayout.CENTER)
        
        val closeButton = JButton("x")
        closeButton.preferredSize = Dimension(16, 16)
        closeButton.addActionListener { closeTab(branchName) }
        tabPanel.add(closeButton, BorderLayout.EAST)
        
        tabbedPane.addTab(branchName, tabContent) // Use branchName as the tab title
        tabbedPane.setTabComponentAt(tabbedPane.tabCount - 1, tabPanel)
        tabbedPane.selectedIndex = tabbedPane.tabCount - 1
        
        // Refresh the tab content
        refreshTabContent(branchName)
    }
    
    private fun closeTab(branchName: String) {
        val index = tabbedPane.indexOfTab(branchName)
        if (index >= 0) {
            tabbedPane.removeTabAt(index)
            tabsMap.remove(branchName)
        }
    }
    
    private fun createTabContent(branchName: String): JComponent {
        val panel = JBPanel<JBPanel<*>>(BorderLayout())
        
        // Create a tree to display changes
        val tree = createChangesTree()
        panel.add(JBScrollPane(tree), BorderLayout.CENTER)
        
        return panel
    }
    
    private fun createChangesTree(): JTree {
        val root = DefaultMutableTreeNode("Changes")
        val treeModel = DefaultTreeModel(root)
        val tree = Tree(treeModel)
        
        // Set custom renderer for coloring
        tree.cellRenderer = object : DefaultTreeCellRenderer() {
            override fun getTreeCellRendererComponent(
                tree: JTree,
                value: Any?,
                selected: Boolean,
                expanded: Boolean,
                leaf: Boolean,
                row: Int,
                hasFocus: Boolean
            ): Component {
                super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
                
                if (value is DefaultMutableTreeNode) {
                    when (val userObject = value.userObject) {
                        is Change -> { // File node
                            // Color based on change type
                            foreground = when (userObject.type) {
                                Change.Type.NEW -> JBColor.GREEN
                                Change.Type.DELETED -> JBColor.RED
                                Change.Type.MOVED -> JBColor.BLUE 
                                else -> JBColor.BLUE // MODIFICATION
                            }
                            
                            // Get file name
                            text = userObject.afterRevision?.file?.name 
                                ?: userObject.beforeRevision?.file?.name 
                                ?: "Unknown File"
                            
                            // Set icon based on type (optional, but good for UX)
                            // icon = AllIcons.FileTypes.Text // Example
                        }
                        is String -> { // Directory node
                            text = userObject
                            // icon = AllIcons.Nodes.Folder // Example
                        }
                        else -> {
                            // Root node or other unexpected type
                            text = value.toString()
                        }
                    }
                }
                return this
            }
        }
        
        // Add double-click listener to open diff
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val path = tree.getPathForLocation(e.x, e.y)
                    path?.let {
                        val node = it.lastPathComponent as? DefaultMutableTreeNode
                        val userObject = node?.userObject
                        if (userObject is Change) {
                            openDiff(userObject)
                        }
                    }
                }
            }
        })
        
        return tree
    }
    
    private fun refreshTabContent(branchName: String) {
        val component = tabsMap[branchName] ?: return
        val tree = (component as JBPanel<*>).components.firstOrNull { it is JBScrollPane }
            ?.let { (it as JBScrollPane).viewport.view as? JTree } ?: return
        
        // Get changes between HEAD and selected branch
        val changes = gitService.getChanges(branchName)
        
        // Update tree model
        val rootModelNode = tree.model.root as DefaultMutableTreeNode
        rootModelNode.removeAllChildren() // Clear previous changes

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
                val vf = currentFilePathObj.virtualFile // Attempt to get VirtualFile
                if (vf != null) {
                    displayPath = VfsUtilCore.getRelativePath(vf, repositoryRoot, '/')
                } else if (rawPath != null) {
                    // Fallback for files not available as VirtualFile (e.g. deleted in HEAD)
                    // Attempt string manipulation if rawPath is absolute and starts with repo root path
                    val repoRootPathString = repositoryRoot.path
                    if (rawPath.startsWith(repoRootPathString + "/")) {
                        displayPath = rawPath.substring(repoRootPathString.length + 1)
                    } else if (rawPath.startsWith(repoRootPathString)) { // Handle case where repoRootPathString might not have trailing slash
                        displayPath = rawPath.substring(repoRootPathString.length).let { if (it.startsWith("/")) it.substring(1) else it }
                    } else {
                        displayPath = rawPath // Fallback if not clearly under repo root
                    }
                }
            }

            // If displayPath is still null (e.g. repoRoot was null, or currentFilePathObj was null), use rawPath as fallback
            if (displayPath == null) {
                displayPath = rawPath
            }

            if (displayPath == null) continue // Skip if no path could be determined

            // Normalize path separators (important if string manipulation fallback was used)
            val normalizedPath = displayPath.replace('\\', '/')
            val pathComponents = normalizedPath.split('/').filter { it.isNotEmpty() }
            var currentNode = rootNode

            // Check if pathComponents is empty, meaning the change is at the repository root itself
            // This can happen for example if a file at the root is modified.
            // In such cases, we should directly add the change to the rootNode.
            if (pathComponents.isEmpty() && currentFilePathObj != null) {
                 // Ensure that we don't add a directory node if it's a file at root.
                 // The existing logic for finding/creating child nodes might handle this,
                 // but this explicit check makes it clearer for root-level files.
                 var fileNodeExists = false
                 for (j in 0 until currentNode.childCount) {
                     val existingChild = currentNode.getChildAt(j) as DefaultMutableTreeNode
                     if (existingChild.userObject is Change) {
                         val existingChange = existingChild.userObject as Change
                         val existingChangePath = existingChange.afterRevision?.file?.path ?: existingChange.beforeRevision?.file?.path
                         if (existingChangePath == rawPath) { // rawPath is the original full path here
                             fileNodeExists = true
                             break
                         }
                     }
                 }
                 if (!fileNodeExists) {
                     currentNode.add(DefaultMutableTreeNode(change))
                 }
                 continue // Move to the next change
            }


            for (i in pathComponents.indices) {
                val componentName = pathComponents[i]
                val isLastComponent = i == pathComponents.size - 1

                var childNode: DefaultMutableTreeNode? = null
                for (j in 0 until currentNode.childCount) {
                    val existingChild = currentNode.getChildAt(j) as DefaultMutableTreeNode
                    if (isLastComponent && existingChild.userObject is Change) {
                        val existingChange = existingChild.userObject as Change
                        // Match based on the original full path (rawPath) to ensure uniqueness if multiple
                        // changes could theoretically have the same relative path (e.g. submodule changes - though less likely here)
                        val existingChangeOriginalPath = existingChange.afterRevision?.file?.path ?: existingChange.beforeRevision?.file?.path
                        if (existingChangeOriginalPath == rawPath) { // Compare with rawPath for uniqueness
                            childNode = existingChild
                            break
                        }
                    } else if (!isLastComponent && existingChild.userObject is String && existingChild.userObject == componentName) {
                        childNode = existingChild
                        break
                    }
                }
                
                if (childNode == null) {
                    childNode = if (isLastComponent) {
                        DefaultMutableTreeNode(change) // File node, userObject is Change
                    } else {
                        DefaultMutableTreeNode(componentName) // Directory node, userObject is directory name
                    }
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
    
    private fun showBranchSelectionDialog() {
        val dialog = object : DialogWrapper(project, true) { // true for canBeParent
            private val searchTextField = SearchTextField()
            private var listPopup: JBPopup? = null
            private val allBranches = gitService.getAllBranches().sorted()
            private val filteredListModel = DefaultListModel<String>()

            init {
                title = "Select Branch to Compare with HEAD"
                init() // Important to call init() for DialogWrapper

                searchTextField.addDocumentListener(object : DocumentListener {
                    override fun insertUpdate(e: DocumentEvent?) {
                        filterAndShowPopup()
                    }

                    override fun removeUpdate(e: DocumentEvent?) {
                        filterAndShowPopup()
                    }

                    override fun changedUpdate(e: DocumentEvent?) {
                        filterAndShowPopup()
                    }
                })
            }

            private fun filterAndShowPopup() {
                val searchText = searchTextField.text.trim()
                filteredListModel.clear()
                allBranches.filter { it.contains(searchText, ignoreCase = true) }
                    .forEach { filteredListModel.addElement(it) }

                if (listPopup?.isDisposed == false) {
                    listPopup?.cancel() // Close previous popup
                }

                if (filteredListModel.isEmpty && searchText.isNotEmpty()) {
                    // Optionally show "no results" or hide popup
                    return
                }
                if (filteredListModel.isEmpty && searchText.isEmpty()) {
                     // Show all branches if search text is empty
                    allBranches.forEach { filteredListModel.addElement(it) }
                }


                if (filteredListModel.size > 0) {
                    val jbList = JBList(filteredListModel)
                    jbList.visibleRowCount = JBUI.scale(10).coerceAtMost(filteredListModel.size)


                    listPopup = JBPopupFactory.getInstance()
                        .createListPopupBuilder(jbList)
                        .setTitle("Matching Branches")
                        .setMovable(false)
                        .setResizable(false)
                        .setRequestFocus(false) // Keep focus on searchTextField initially
                        .setItemChoosenCallback {
                            val selectedValue = jbList.selectedValue
                            if (selectedValue != null) {
                                addTab(selectedValue)
                                close(OK_EXIT_CODE)
                            }
                        }
                        .createPopup()

                    // Handle Enter key on JBList
                    jbList.addKeyListener(object : KeyAdapter() {
                        override fun keyPressed(e: KeyEvent) {
                            if (e.keyCode == KeyEvent.VK_ENTER) {
                                val selectedValue = jbList.selectedValue
                                if (selectedValue != null) {
                                    addTab(selectedValue)
                                    close(OK_EXIT_CODE)
                                }
                            }
                        }
                    })
                    
                    // Show popup under the search field
                    listPopup?.showUnderneathOf(searchTextField)
                }
            }

            override fun createCenterPanel(): JComponent {
                val panel = JBPanel<JBPanel<*>>(BorderLayout(0, JBUI.scale(5)))
                panel.add(JBLabel("Select branch to compare with HEAD:"), BorderLayout.NORTH)
                panel.add(searchTextField, BorderLayout.CENTER)
                panel.preferredSize = JBUI.size(400, 60) // Adjust size as needed, popup will be separate
                return panel
            }

            override fun getPreferredFocusedComponent(): JComponent? {
                return searchTextField
            }
            
            // We handle action on popup item selection, so default OK might not be needed
            // or could be triggered programmatically. For now, let popup handle it.
            override fun doOKAction() {
                // This might be triggered if user presses Enter in the search field
                // without an active popup or a selection in popup.
                // We can try to select the first item in filtered list if available.
                if (listPopup?.isVisible == false && filteredListModel.size > 0) {
                    addTab(filteredListModel.getElementAt(0))
                    super.doOKAction()
                } else if (listPopup?.isVisible == true) {
                    // Let popup handle it or simulate enter on list
                } else {
                    // If no results or popup not shown, maybe do nothing or close
                    super.doCancelAction() // Or provide feedback
                }
            }
        }
        dialog.show()
    }
}
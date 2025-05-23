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

// Imports for ActionButton
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import javax.swing.UIManager // Added for UIManager.getColor
import java.awt.Color // Added for transparent color
import javax.swing.SwingUtilities // Added for event conversion
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
// import com.intellij.ui.components.JBTabbedPane // No longer used
import com.intellij.ui.tabs.JBTabs
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.openapi.actionSystem.DefaultActionGroup
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

class GitChangesToolWindow(private val project: Project) { // project is Disposable
    private val gitService = project.service<GitService>()
    private val jbTabs: JBTabsImpl = JBTabsImpl(project, project) // project as parentDisposable, type changed to JBTabsImpl

    fun getContent(): JComponent {
        val panel = JBPanel<JBPanel<*>>(BorderLayout())

        // Create the AddTabAnAction
        val addTabAction = AddTabAnAction()
        // Wrap it in a DefaultActionGroup
        val addActionGroup = DefaultActionGroup(addTabAction)
        // Set this group as extra actions for jbTabs
        jbTabs.setExtraActions(addActionGroup)

        // Add JBTabs component to the main panel
        panel.add(jbTabs.component, BorderLayout.CENTER)

        // Restore initial tab addition
        val currentBranch = gitService.getCurrentBranch() ?: "HEAD"
        addTab(currentBranch)

        return panel
    }
    
    private fun addTab(branchName: String) {
        // Check for Existing Tab
        val existingTab = jbTabs.tabs.find { it.text == branchName }
        if (existingTab != null) {
            jbTabs.select(existingTab, true)
            return
        }

        // Create Content Component
        val tabContentComponent = createTabContent(branchName)

        // Create TabInfo
        val tabInfo = TabInfo(tabContentComponent)
        tabInfo.setText(branchName) // Use setter method

        // Set Close Action on TabInfo
        val closeAction = object : AnAction("Close Tab", "Close this tab", AllIcons.Actions.Close) {
            override fun actionPerformed(e: AnActionEvent) {
                jbTabs.removeTab(tabInfo)
            }
        }
        val actionGroup = DefaultActionGroup(closeAction)
        // Using FQN for ActionPlaces, corrected to EDITOR_TAB
        tabInfo.setTabLabelActions(actionGroup, com.intellij.openapi.actionSystem.ActionPlaces.EDITOR_TAB)
        
        // Add and Select Tab
        jbTabs.addTab(tabInfo)
        jbTabs.select(tabInfo, true)

        // Call refreshTabContent
        refreshTabContent(branchName, tabInfo)
    }
    
    // Removed the old closeTab(branchName: String) method
    
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
    
    private fun refreshTabContent(branchName: String, currentTabInfo: TabInfo? = null) {
        val actualTabInfo = currentTabInfo ?: jbTabs.tabs.find { it.text == branchName }
        if (actualTabInfo == null) {
            println("Error: TabInfo not found for $branchName in refreshTabContent")
            return
        }

        val scrollPane = actualTabInfo.component as? JBScrollPane
        val tree = scrollPane?.viewport?.view as? JTree
        
        if (tree == null) {
            println("Error: JTree not found in tab $branchName")
            return
        }
        
        // Get changes between HEAD and selected branch
        val changes = gitService.getChanges(branchName)
        
        // Update tree model
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

    // private inner class CloseTabAction(...) // Removed

    private inner class AddTabAnAction : AnAction("Add New Tab", "Open dialog to select a branch for comparison", AllIcons.General.Add) {
        override fun actionPerformed(e: AnActionEvent) {
            showBranchSelectionDialog()
        }
    }
}
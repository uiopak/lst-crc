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

// Imports for ActionButton & Toolbar
// import com.intellij.icons.AllIcons // Removed
// import com.intellij.openapi.actionSystem.ActionManager // Removed
// import com.intellij.openapi.actionSystem.AnAction // Removed
// import com.intellij.openapi.actionSystem.AnActionEvent // Removed
// import com.intellij.openapi.actionSystem.ActionPlaces // Removed as no longer directly used
// import com.intellij.openapi.actionSystem.ActionToolbar // Removed
// import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl // Removed
// import com.intellij.openapi.actionSystem.impl.ActionButton // No longer directly used for add button
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import javax.swing.UIManager // Added for UIManager.getColor
import java.awt.Color // Added for transparent color
import javax.swing.SwingUtilities // Added for event conversion
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
// import com.intellij.ui.components.JBTabbedPane // No longer used
// import com.intellij.ui.tabs.JBTabs // Removed
// import com.intellij.ui.tabs.TabInfo // Removed
// import com.intellij.ui.tabs.impl.JBTabsImpl // Removed
// import com.intellij.openapi.actionSystem.DefaultActionGroup // Removed
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
// import java.awt.FlowLayout // Removed
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

    fun createBranchContentView(branchName: String): JComponent {
        val tree = createChangesTree()
        refreshChangesTree(tree, branchName) // Populate the tree with initial data

        val panel = JBPanel<JBPanel<*>>(BorderLayout())
        panel.add(JBScrollPane(tree), BorderLayout.CENTER)
        return panel
    }
    
    private fun createChangesTree(): JTree {
        val root = DefaultMutableTreeNode("Changes")
        val treeModel = DefaultTreeModel(root)
        val tree = Tree(treeModel)

        // Explicitly set tree background and opacity
        tree.background = javax.swing.UIManager.getColor("Tree.background")
        tree.isOpaque = true
        
        // Set custom renderer for coloring
        // tree.cellRenderer = object : DefaultTreeCellRenderer() {
        //     override fun getTreeCellRendererComponent(
        //         tree: JTree,
        //         value: Any?,
        //         selected: Boolean,
        //         expanded: Boolean,
        //         leaf: Boolean,
        //         row: Int,
        //         hasFocus: Boolean
        //     ): Component {
        //         // 1. Call super method FIRST.
        //         val component = super.getTreeCellRendererComponent(
        //             tree, value, selected, expanded, leaf, row, hasFocus
        //         ) as JLabel
        //
        //         // 2. Explicitly manage opacity and L&F default colors.
        //         if (selected) {
        //             component.isOpaque = true // Make sure selected item is opaque
        //             // The super() call should have already set these, but to be absolutely sure:
        //             component.background = javax.swing.UIManager.getColor("Tree.selectionBackground")
        //             component.foreground = javax.swing.UIManager.getColor("Tree.selectionForeground")
        //         } else {
        //             component.isOpaque = false // Make non-selected items transparent
        //             // Background for transparent component doesn't matter.
        //             // Ensure foreground is the default for non-selected items.
        //             component.foreground = javax.swing.UIManager.getColor("Tree.foreground")
        //         }
        //
        //         // 3. Apply custom text, icon, and conditional foreground for specific node types.
        //         if (value is DefaultMutableTreeNode) {
        //             val userObject = value.userObject
        //             when (userObject) {
        //                 is Change -> {
        //                     component.text = userObject.afterRevision?.file?.name 
        //                         ?: userObject.beforeRevision?.file?.name 
        //                         ?: "Unknown File"
        //                     
        //                     // Apply custom foreground color ONLY if NOT selected.
        //                     if (!selected) { 
        //                         component.foreground = when (userObject.type) {
        //                             Change.Type.NEW -> com.intellij.ui.JBColor.GREEN
        //                             Change.Type.DELETED -> com.intellij.ui.JBColor.RED
        //                             Change.Type.MOVED -> com.intellij.ui.JBColor.BLUE 
        //                             else -> com.intellij.ui.JBColor.BLUE // MODIFICATION
        //                         }
        //                     }
        //                     // Optionally set component.icon here if needed
        //                 }
        //                 is String -> { // Directory node
        //                     component.text = userObject
        //                     // Foreground already set by selected/non-selected block above.
        //                     // Optionally set component.icon here if needed (e.g., AllIcons.Nodes.Folder)
        //                 }
        //                 else -> { // Root node or other
        //                     component.text = value.toString()
        //                     // Foreground already set by selected/non-selected block above.
        //                 }
        //             }
        //         } else {
        //             component.text = value?.toString() ?: ""
        //             // Foreground for non-nodes already set by selected/non-selected block.
        //         }
        //
        //         return component
        //     }
        // }
        
        // Add double-click listener to open diff
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val row = tree.getRowForLocation(e.x, e.y) // Get row first
                    if (row != -1) { // Check if click was on a valid row
                        val path = tree.getPathForRow(row) // Get path for that row
                        path?.let { // it refers to path
                            val node = it.lastPathComponent as? DefaultMutableTreeNode
                            val userObject = node?.userObject
                            if (userObject is Change) {
                                openDiff(userObject)
                            }
                        }
                    }
                }
            }
        })
        
        return tree
    }
    
    private fun refreshChangesTree(tree: JTree, branchName: String) {
        // Logic from old refreshTabContent, but tree is passed directly.
        // No longer need to find TabInfo or extract tree from component hierarchy.

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
    
    // Modified to accept a callback
    fun showBranchSelectionDialog(onBranchSelected: (branchName: String) -> Unit) {
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
                                onBranchSelected(selectedValue) // Call the callback
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
                                    onBranchSelected(selectedValue) // Call the callback
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
                    val selectedValue = filteredListModel.getElementAt(0)
                    onBranchSelected(selectedValue) // Call the callback
                    super.doOKAction() // This will close the dialog with OK_EXIT_CODE
                } else if (listPopup?.isVisible == true) {
                    // Let popup handle it or simulate enter on list
                    // If a list item is selected, Enter on the list should trigger its KeyListener above.
                    // If not, and user presses Enter on search field, this doOKAction might be triggered.
                    // We could try to take list.selectedValue if any.
                    val currentListSelection = (listPopup?.content as? JBList<*>)?.selectedValue as? String
                    if (currentListSelection != null) {
                        onBranchSelected(currentListSelection)
                        super.doOKAction()
                    } else {
                        // If no selection, and Enter pressed, perhaps do nothing or cancel.
                        super.doCancelAction() // Or provide feedback that a selection is needed
                    }
                } else {
                    // If no results or popup not shown, maybe do nothing or close
                    super.doCancelAction() // Or provide feedback
                }
            }
        }
        dialog.show()
    }

    // private inner class CloseTabAction(...) // Removed

    // AddTabAnAction is no longer needed here, it will be in MyToolWindowFactory
    // DefaultActionGroup import was also removed as it was only for AddTabAnAction
}
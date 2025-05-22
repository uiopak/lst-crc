package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.services.GitService
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.changes.Change
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
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
        
        tabbedPane.addTab(null, tabContent)
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
                val component = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
                
                if (value is DefaultMutableTreeNode) {
                    val userObject = value.userObject
                    if (userObject is Change) {
                        // Color based on change type
                        val foregroundColor = when (userObject.type) {
                            Change.Type.NEW -> JBColor.GREEN
                            Change.Type.DELETED -> JBColor.RED
                            Change.Type.MOVED -> JBColor.ORANGE
                            else -> JBColor.BLUE
                        }
                        
                        component.foreground = foregroundColor
                        
                        // Get file path
                        val filePath = when {
                            userObject.afterRevision != null -> userObject.afterRevision?.file?.path ?: "Unknown"
                            userObject.beforeRevision != null -> userObject.beforeRevision?.file?.path ?: "Unknown"
                            else -> "Unknown"
                        }
                        
                        text = filePath
                    }
                }
                
                return component
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
        val root = DefaultMutableTreeNode("Changes")
        for (change in changes) {
            root.add(DefaultMutableTreeNode(change))
        }
        
        val model = DefaultTreeModel(root)
        tree.model = model
        TreeUtil.expandAll(tree)
    }
    
    private fun openDiff(change: Change) {
        try {
            // Get the virtual file
            val file = when {
                change.afterRevision != null -> change.afterRevision?.file?.virtualFile
                change.beforeRevision != null -> change.beforeRevision?.file?.virtualFile
                else -> null
            }
            
            // Simply open the file in the editor
            file?.let {
                FileEditorManager.getInstance(project).openFile(it, true)
            }
        } catch (e: Exception) {
            Messages.showErrorDialog(project, "Error opening file: ${e.message}", "Error")
        }
    }
    
    private fun showBranchSelectionDialog() {
        val dialog = object : DialogWrapper(project) {
            private val branchComboBox = ComboBox<String>()
            
            init {
                title = "Select Branch"
                init()
                
                // Populate branch list
                val branches = gitService.getAllBranches()
                for (branch in branches) {
                    branchComboBox.addItem(branch)
                }
            }
            
            override fun createCenterPanel(): JComponent {
                val panel = JBPanel<JBPanel<*>>(BorderLayout())
                panel.add(JBLabel("Select branch to compare with HEAD:"), BorderLayout.NORTH)
                panel.add(branchComboBox, BorderLayout.CENTER)
                panel.preferredSize = JBUI.size(300, 100)
                return panel
            }
            
            override fun doOKAction() {
                super.doOKAction()
                val selectedBranch = branchComboBox.selectedItem as? String
                selectedBranch?.let { addTab(it) }
            }
        }
        
        dialog.show()
    }
}
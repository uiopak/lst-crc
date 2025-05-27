package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.services.GitService
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup // Import ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
// ContentManagerUtil might not be needed anymore if OpenBranchSelectionTabAction is fully removed
// import com.intellij.ui.content.ContentManagerUtil // For findContent and removeContent 
import javax.swing.JComponent
// Removed JPanel, FlowLayout, JButton, ContentManagerListener, ContentManagerEvent, ContentOperation imports
// ContentManagerUtil might be needed if findContent is used within the Action.
// Re-adding for safety, can be removed if not used by findContent or other ContentManager methods.
import com.intellij.ui.content.ContentManagerUtil 

class MyToolWindowFactory : ToolWindowFactory {

    private inner class OpenBranchSelectionTabAction(
        private val project: Project, // project is not directly used in actionPerformed but good practice to have if needed later
        private val toolWindow: ToolWindow,
        private val uiProvider: GitChangesToolWindow
    ) : AnAction("Open Branch Selection", "Open a tab to select a branch for comparison", AllIcons.General.Add) {
        override fun actionPerformed(e: AnActionEvent) {
            val selectionTabName = "Select Branch"
            val contentManager = toolWindow.contentManager

            val existingContent = contentManager.findContent(selectionTabName)
            if (existingContent != null) {
                contentManager.setSelectedContent(existingContent, true)
                return
            }

            // If the tab does not exist, create a new one
            val contentFactory = ContentFactory.getInstance()
            val branchSelectionUi = uiProvider.createBranchSelectionView { selectedBranchName: String ->
                // This is the onBranchSelected lambda
                val manager = toolWindow.contentManager // or contentManager from outer scope
                val selTabName = "Select Branch" 

                val selectionTabContent = manager.findContent(selTabName)

                if (selectionTabContent == null) {
                    println("Error: Could not find the '${selTabName}' tab.")
                    return@createBranchSelectionView // Important: return from lambda
                }

                var existingBranchTab: com.intellij.ui.content.Content? = null
                for (content in manager.contents) {
                    if (content.displayName == selectedBranchName && content != selectionTabContent) {
                        existingBranchTab = content
                        break
                    }
                }

                if (existingBranchTab != null) {
                    manager.setSelectedContent(existingBranchTab, true)
                    manager.removeContent(selectionTabContent, true)
                } else {
                    selectionTabContent.displayName = selectedBranchName
                    selectionTabContent.component = uiProvider.createBranchContentView(selectedBranchName)
                    manager.setSelectedContent(selectionTabContent, true)
                }
            }

            val newContent = contentFactory.createContent(branchSelectionUi, selectionTabName, true) // true for focusable
            newContent.isCloseable = true
            contentManager.addContent(newContent)
            contentManager.setSelectedContent(newContent, true)
        }
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val gitChangesUiProvider = GitChangesToolWindow(project)
        val contentFactory = ContentFactory.getInstance() 

        val gitService = project.service<GitService>()
        val initialBranchName = gitService.getCurrentBranch() ?: "HEAD"
        val initialBranchUi = gitChangesUiProvider.createBranchContentView(initialBranchName)
        val initialContent = contentFactory.createContent(initialBranchUi, initialBranchName, false)
        initialContent.isCloseable = true 
        initialContent.isPinned = false
        toolWindow.contentManager.addContent(initialContent)
        // Select the initial content immediately
        toolWindow.contentManager.setSelectedContent(initialContent, true)

        // Instantiate and set the action
        val openSelectionTabAction = OpenBranchSelectionTabAction(project, toolWindow, gitChangesUiProvider)
        toolWindow.setTitleActions(listOf(openSelectionTabAction))

        // --- ADD SETTINGS GROUP DIRECTLY TO THE TOOL WINDOW'S "GEAR" MENU ---
        // Get the ActionGroup that represents your settings section (it's already a popup group)
        val pluginSettingsSubMenu: ActionGroup = gitChangesUiProvider.createToolWindowSettingsGroup()

        // This is the group whose children will appear directly in the gear menu.
        val allGearActionsGroup = DefaultActionGroup()
        allGearActionsGroup.add(pluginSettingsSubMenu) // Add your settings sub-menu as an item
        // If you had other top-level actions for the gear menu, you'd add them here.
        // e.g., allGearActionsGroup.add(Separator.getInstance())
        // e.g., allGearActionsGroup.add(SomeOtherAction())

        toolWindow.setAdditionalGearActions(allGearActionsGroup)
        // --- END SETTINGS ACTION ---
    }

    override fun shouldBeAvailable(project: Project) = true
}
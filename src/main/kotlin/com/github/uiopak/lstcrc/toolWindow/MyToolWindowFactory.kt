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
import com.intellij.ui.content.ContentManagerUtil // For findContent and removeContent
import javax.swing.JComponent

class MyToolWindowFactory : ToolWindowFactory {

    private inner class OpenBranchSelectionTabAction(
        private val project: Project,
        private val toolWindow: ToolWindow,
        private val uiProvider: GitChangesToolWindow
    ) : AnAction("Open Branch Selection Tab", "Select a branch to open as a new tab", AllIcons.General.Add) {
        override fun actionPerformed(e: AnActionEvent) {
            val selectionTabName = "Select Branch to Compare"
            val contentManager = toolWindow.contentManager

            val existingContent = contentManager.findContent(selectionTabName)
            if (existingContent != null) {
                contentManager.setSelectedContent(existingContent, true)
                return
            }

            // If the tab does not exist, create a new one
            val contentFactory = ContentFactory.getInstance()
            val branchSelectionUi = uiProvider.createBranchSelectionView { selectedBranchName ->
                val contentManager = toolWindow.contentManager
                val selectionTabName = "Select Branch to Compare" // Must match the name used when creating the tab

                // Find the "Select Branch to Compare" tab itself.
                val selectionTabContent = contentManager.findContent(selectionTabName)

                if (selectionTabContent == null) {
                    // This case should ideally not happen if the UI flow is correct.
                    println("Error: Could not find the '${selectionTabName}' tab.")
                    return@createBranchSelectionView
                }

                // Check if a tab for the selectedBranchName already exists (excluding the selectionTabContent itself).
                var existingBranchTab: com.intellij.ui.content.Content? = null
                for (content in contentManager.contents) {
                    if (content.displayName == selectedBranchName && content != selectionTabContent) {
                        existingBranchTab = content
                        break
                    }
                }

                if (existingBranchTab != null) {
                    // If the branch tab already exists, select it and close the selection tab.
                    contentManager.setSelectedContent(existingBranchTab, true)
                    contentManager.removeContent(selectionTabContent, true) // removeContent also disposes it
                } else {
                    // If the branch tab does not exist, transform the selectionTabContent.
                    selectionTabContent.displayName = selectedBranchName
                    selectionTabContent.component = uiProvider.createBranchContentView(selectedBranchName)
                    // selectionTabContent.isCloseable is already true (set when created).
                    // Ensure it's still selected (it should be, as it was the active tab).
                    contentManager.setSelectedContent(selectionTabContent, true)
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
        initialContent.isCloseable = true // Initial main branch tab can be closed
        initialContent.isPinned = false
        toolWindow.contentManager.addContent(initialContent)
        toolWindow.contentManager.setSelectedContent(initialContent, true)

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
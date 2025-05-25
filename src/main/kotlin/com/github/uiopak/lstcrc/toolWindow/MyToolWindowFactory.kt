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

class MyToolWindowFactory : ToolWindowFactory {

    private class AddNewBranchTabAction(
        private val project: Project,
        private val toolWindow: ToolWindow,
        private val uiProvider: GitChangesToolWindow
    ) : AnAction("Add Branch Tab", "Select a branch to open as a new tab", AllIcons.General.Add) {
        override fun actionPerformed(e: AnActionEvent) {
            uiProvider.showBranchSelectionDialog { selectedBranchName ->
                val existingContent = toolWindow.contentManager.findContent(selectedBranchName)
                if (existingContent != null) {
                    toolWindow.contentManager.setSelectedContent(existingContent, true)
                    return@showBranchSelectionDialog
                }
                val branchUi = uiProvider.createBranchContentView(selectedBranchName)
                val contentFactory = ContentFactory.getInstance()
                val content = contentFactory.createContent(branchUi, selectedBranchName, false)
                content.isCloseable = true
                content.isPinned = false
                toolWindow.contentManager.addContent(content)
                toolWindow.contentManager.setSelectedContent(content, true)
            }
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

        val addTabAction = AddNewBranchTabAction(project, toolWindow, gitChangesUiProvider)
        toolWindow.setTitleActions(listOf(addTabAction))

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
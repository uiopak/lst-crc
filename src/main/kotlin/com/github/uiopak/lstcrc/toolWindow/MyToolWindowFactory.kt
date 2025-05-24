package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.services.GitService
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class MyToolWindowFactory : ToolWindowFactory {

    // Nested class for the "Add New Tab" action
    private class AddNewBranchTabAction(
        private val project: Project,
        private val toolWindow: ToolWindow,
        private val uiProvider: GitChangesToolWindow // To call createBranchContentView and showBranchSelectionDialog
    ) : AnAction("Add Branch Tab", "Select a branch to open as a new tab", AllIcons.General.Add) {

        override fun actionPerformed(e: AnActionEvent) {
            uiProvider.showBranchSelectionDialog { selectedBranchName ->
                // Check if a tab with this name already exists
                val existingContent = toolWindow.contentManager.findContent(selectedBranchName)
                if (existingContent != null) {
                    toolWindow.contentManager.setSelectedContent(existingContent, true)
                    return@showBranchSelectionDialog
                }

                val branchUi = uiProvider.createBranchContentView(selectedBranchName)
                val contentFactory = ContentFactory.getInstance()
                val content = contentFactory.createContent(branchUi, selectedBranchName, true) // Changed to true
                content.isCloseable = true // Make the tab closeable
                content.isPinned = false // Added this line
                toolWindow.contentManager.addContent(content)
                toolWindow.contentManager.setSelectedContent(content, true) // Select the new tab
            }
        }
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.canCloseContents = true // Added this line

        val gitChangesUiProvider = GitChangesToolWindow(project)
        val contentFactory = ContentFactory.getInstance()

        // Initial Tab Creation
        val gitService = project.service<GitService>()
        val initialBranchName = gitService.getCurrentBranch() ?: "HEAD"
        val initialBranchUi = gitChangesUiProvider.createBranchContentView(initialBranchName)
        val initialContent = contentFactory.createContent(initialBranchUi, initialBranchName, true) // Changed to true
        initialContent.isCloseable = true // Make the initial tab closeable
        initialContent.isPinned = false // Added this line
        toolWindow.contentManager.addContent(initialContent)

        // Set "Plus" button action for the tool window title
        toolWindow.setTitleActions(listOf(AddNewBranchTabAction(project, toolWindow, gitChangesUiProvider)))
    }

    override fun shouldBeAvailable(project: Project) = true
}

package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.services.GitService
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import javax.swing.JComponent

class MyToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val gitChangesUiProvider = GitChangesToolWindow(project)
        val contentFactory = ContentFactory.getInstance()
        val gitService = project.service<GitService>()

        // Create Permanent "HEAD" Tab (now shows current branch/commit vs. working tree)
        val headTabTargetName = gitService.getCurrentBranch() ?: "HEAD"
        val headView = gitChangesUiProvider.createBranchContentView(headTabTargetName)
        val headContent = contentFactory.createContent(headView, "HEAD", false)
        headContent.isCloseable = false
        headContent.isPinned = true // Optional but good for a permanent tab
        toolWindow.contentManager.addContent(headContent)

        // Create Initial Closable Tab for Current Branch (Conditional)
        val currentActualBranchName = gitService.getCurrentBranch()

        if (currentActualBranchName != null && currentActualBranchName != "HEAD") {
            val initialBranchView = gitChangesUiProvider.createBranchContentView(currentActualBranchName)
            val initialBranchContent = contentFactory.createContent(initialBranchView, currentActualBranchName, false)
            initialBranchContent.isCloseable = true
            toolWindow.contentManager.addContent(initialBranchContent)
            toolWindow.contentManager.setSelectedContent(initialBranchContent, true)
        } else {
            // Select the "HEAD" tab if no specific branch tab is created
            toolWindow.contentManager.setSelectedContent(headContent, true)
        }

        // Existing actions
        val openSelectionTabAction = OpenBranchSelectionTabAction(project, toolWindow, gitChangesUiProvider)
        toolWindow.setTitleActions(listOf(openSelectionTabAction))

        val propertiesComponent = com.intellij.ide.util.PropertiesComponent.getInstance()
        val settingsProvider = ToolWindowSettingsProvider(propertiesComponent)
        val pluginSettingsSubMenu: ActionGroup = settingsProvider.createToolWindowSettingsGroup()
        
        val allGearActionsGroup = DefaultActionGroup()
        allGearActionsGroup.add(pluginSettingsSubMenu)
        toolWindow.setAdditionalGearActions(allGearActionsGroup)
    }

    override fun shouldBeAvailable(project: Project) = true
}

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
// ContentManagerUtil is not used in the modified version.
import javax.swing.JComponent

class MyToolWindowFactory : ToolWindowFactory {
    // The logger from the inner class is not needed here if OpenBranchSelectionTabAction handles its own logging.
    // private val logger = thisLogger() // Assuming OpenBranchSelectionTabAction has its own logger if needed.

    // OpenBranchSelectionTabAction inner class has been extracted to its own file.

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
        toolWindow.contentManager.setSelectedContent(initialContent, true)
        val openSelectionTabAction = OpenBranchSelectionTabAction(project, toolWindow, gitChangesUiProvider)
        toolWindow.setTitleActions(listOf(openSelectionTabAction))

        // Use ToolWindowSettingsProvider to create the settings group
        val propertiesComponent = com.intellij.ide.util.PropertiesComponent.getInstance()
        val settingsProvider = ToolWindowSettingsProvider(propertiesComponent)
        val pluginSettingsSubMenu: ActionGroup = settingsProvider.createToolWindowSettingsGroup()
        
        val allGearActionsGroup = DefaultActionGroup()
        allGearActionsGroup.add(pluginSettingsSubMenu)
        toolWindow.setAdditionalGearActions(allGearActionsGroup)
    }

    override fun shouldBeAvailable(project: Project) = true
}

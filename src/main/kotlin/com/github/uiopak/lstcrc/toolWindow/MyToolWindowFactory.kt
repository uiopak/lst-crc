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
import com.intellij.ui.content.ContentManagerUtil
import javax.swing.JComponent

class MyToolWindowFactory : ToolWindowFactory {
    private val logger = thisLogger()

    private inner class OpenBranchSelectionTabAction(
        private val project: Project,
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

            val contentFactory = ContentFactory.getInstance()
            val branchSelectionUi = uiProvider.createBranchSelectionView { selectedBranchName: String ->
                val manager = toolWindow.contentManager
                val selectionTabContent = manager.findContent(selectionTabName)

                if (selectionTabContent == null) {
                    logger.error("Could not find the '$selectionTabName' tab.")
                    return@createBranchSelectionView
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

            val newContent = contentFactory.createContent(branchSelectionUi, selectionTabName, true)
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
        toolWindow.contentManager.setSelectedContent(initialContent, true)
        val openSelectionTabAction = OpenBranchSelectionTabAction(project, toolWindow, gitChangesUiProvider)
        toolWindow.setTitleActions(listOf(openSelectionTabAction))

        val pluginSettingsSubMenu: ActionGroup = gitChangesUiProvider.createToolWindowSettingsGroup()
        val allGearActionsGroup = DefaultActionGroup()
        allGearActionsGroup.add(pluginSettingsSubMenu)
        toolWindow.setAdditionalGearActions(allGearActionsGroup)
    }

    override fun shouldBeAvailable(project: Project) = true
}

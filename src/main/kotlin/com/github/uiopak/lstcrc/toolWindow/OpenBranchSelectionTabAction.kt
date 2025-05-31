package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.services.GitService
import com.intellij.icons.AllIcons
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.thisLogger // Added for logging
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.ContentFactory

class OpenBranchSelectionTabAction(
    private val project: Project,
    private val toolWindow: ToolWindow,
    private val uiProvider: GitChangesToolWindow // Still needed to call createBranchContentView
) : AnAction("Open Branch Selection", "Open a tab to select a branch for comparison", AllIcons.General.Add) {

    private val logger = thisLogger()

    override fun actionPerformed(e: AnActionEvent) {
        val selectionTabName = "Select Branch"
        val contentManager = toolWindow.contentManager

        val existingContent = contentManager.findContent(selectionTabName)
        if (existingContent != null) {
            contentManager.setSelectedContent(existingContent, true)
            return
        }

        val contentFactory = ContentFactory.getInstance()
        val branchSelectionUi = BranchSelectionPanel(project, project.service<GitService>()) { selectedBranchName: String ->
            val manager = toolWindow.contentManager
            val selectionTabContent = manager.findContent(selectionTabName)

            if (selectionTabContent == null) {
                logger.error("Could not find the '$selectionTabName' tab.")
                return@BranchSelectionPanel
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

        val newContent = contentFactory.createContent(branchSelectionUi.getPanel(), selectionTabName, true)
        newContent.isCloseable = true
        contentManager.addContent(newContent)
        contentManager.setSelectedContent(newContent, true)
    }
}

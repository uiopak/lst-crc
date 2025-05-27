package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.services.GitService
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
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
        // Uses BranchSelectionPanel for the UI of the "Select Branch" tab
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
                manager.removeContent(selectionTabContent, true) // Close the "Select Branch" tab
            } else {
                // Reuse the "Select Branch" tab to display the branch content
                selectionTabContent.displayName = selectedBranchName
                // uiProvider is an instance of GitChangesToolWindow, used to create the actual branch content view
                selectionTabContent.component = uiProvider.createBranchContentView(selectedBranchName)
                manager.setSelectedContent(selectionTabContent, true)
            }
        }

        val newContent = contentFactory.createContent(branchSelectionUi.getPanel(), selectionTabName, true)
        newContent.isCloseable = true // Allow closing the "Select Branch" tab
        contentManager.addContent(newContent)
        contentManager.setSelectedContent(newContent, true)
    }
}

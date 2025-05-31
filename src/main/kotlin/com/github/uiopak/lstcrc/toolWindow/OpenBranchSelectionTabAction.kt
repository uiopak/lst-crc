package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.services.GitService
import com.intellij.icons.AllIcons
import com.github.uiopak.lstcrc.services.ToolWindowStateService // Ensure this import is present
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service // Ensure this import is present
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager // Added for type safety

class OpenBranchSelectionTabAction(
    private val project: Project,
    private val toolWindow: ToolWindow,
    private val uiProvider: GitChangesToolWindow
) : AnAction("Open Branch Selection", "Open a tab to select a branch for comparison", AllIcons.General.Add) {

    private val logger = thisLogger()

    override fun actionPerformed(e: AnActionEvent) {
        val selectionTabName = "Select Branch"
        val contentManager: ContentManager = toolWindow.contentManager // Use ContentManager type

        val existingContent = contentManager.findContent(selectionTabName)
        if (existingContent != null) {
            contentManager.setSelectedContent(existingContent, true)
            logger.info("OpenBranchSelectionTabAction: Found existing '$selectionTabName' tab and selected it.")
            return
        }

        val stateService = ToolWindowStateService.getInstance(project) // Get service instance

        val contentFactory = ContentFactory.getInstance()
        val branchSelectionUi = BranchSelectionPanel(project, project.service<GitService>()) { selectedBranchName: String ->
            // This is the callback when a branch is selected from the BranchSelectionPanel
            logger.info("OpenBranchSelectionTabAction (Callback): Branch '$selectedBranchName' selected from panel.")

            val manager: ContentManager = toolWindow.contentManager // Use ContentManager type
            val selectionTabContent = manager.findContent(selectionTabName)

            if (selectionTabContent == null) {
                logger.error("OpenBranchSelectionTabAction (Callback): Could not find the '$selectionTabName' tab to repurpose/remove.")
                return@BranchSelectionPanel
            }

            var existingBranchTabForSelectedName: com.intellij.ui.content.Content? = null
            for (content in manager.contents) {
                if (content.displayName == selectedBranchName && content != selectionTabContent) {
                    existingBranchTabForSelectedName = content
                    break
                }
            }

            if (existingBranchTabForSelectedName != null) {
                logger.info("OpenBranchSelectionTabAction (Callback): Tab for '$selectedBranchName' already exists. Selecting it and removing '$selectionTabName' tab.")
                manager.setSelectedContent(existingBranchTabForSelectedName, true)
                manager.removeContent(selectionTabContent, true) // Remove the "Select Branch" tab

                // Update state for selecting an existing tab
                val closableTabs = manager.contents.filter { it.isCloseable }.map { it.displayName }
                val selectedIndex = closableTabs.indexOf(selectedBranchName)
                if (selectedIndex != -1) {
                    logger.info("OpenBranchSelectionTabAction (Callback): Calling stateService.setSelectedTab($selectedIndex) for existing branch '$selectedBranchName'.")
                    stateService.setSelectedTab(selectedIndex)
                } else {
                    logger.warn("OpenBranchSelectionTabAction (Callback): Could not find existing branch '$selectedBranchName' in closable tabs for setSelectedTab after selecting it.")
                }
            } else {
                // Repurpose the "Select Branch" tab
                logger.info("OpenBranchSelectionTabAction (Callback): Repurposing '$selectionTabName' tab to '$selectedBranchName'.")
                selectionTabContent.displayName = selectedBranchName
                selectionTabContent.component = uiProvider.createBranchContentView(selectedBranchName)
                // selectionTabContent is already closable and added to contentManager

                manager.setSelectedContent(selectionTabContent, true) // Ensure it's selected

                // Update state for the newly repurposed/created tab
                logger.info("OpenBranchSelectionTabAction (Callback): Calling stateService.addTab('$selectedBranchName').")
                stateService.addTab(selectedBranchName) // Add the new branch name to state

                val closableTabs = manager.contents.filter { it.isCloseable }.map { it.displayName }
                val newTabIndex = closableTabs.indexOf(selectedBranchName)
                if (newTabIndex != -1) {
                    logger.info("OpenBranchSelectionTabAction (Callback): Calling stateService.setSelectedTab($newTabIndex) for new/repurposed branch '$selectedBranchName'.")
                    stateService.setSelectedTab(newTabIndex)
                } else {
                    logger.warn("OpenBranchSelectionTabAction (Callback): Could not find new/repurposed branch '$selectedBranchName' in closable tabs for setSelectedTab.")
                }
            }
        }

        logger.info("OpenBranchSelectionTabAction: Creating and adding new '$selectionTabName' tab to UI.")
        val newContent = contentFactory.createContent(branchSelectionUi.getPanel(), selectionTabName, true)
        // The boolean parameter for createContent is `isLockable`.
        // Previous version had `true` for `newContent.isCloseable = true`.
        // Assuming the "Select Branch" tab should be closable.
        newContent.isCloseable = true // The "Select Branch" tab itself should be closable
        contentManager.addContent(newContent)
        contentManager.setSelectedContent(newContent, true)
        // DO NOT add "Select Branch" to stateService here, as it's temporary.
    }
}

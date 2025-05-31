package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.services.GitService
import com.intellij.icons.AllIcons
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager

class OpenBranchSelectionTabAction(
    private val project: Project,
    private val toolWindow: ToolWindow,
    private val uiProvider: GitChangesToolWindow
) : AnAction("Open Branch Selection", "Open a tab to select a branch for comparison", AllIcons.General.Add) {

    private val logger = thisLogger()

    override fun actionPerformed(e: AnActionEvent) {
        val selectionTabName = "Select Branch"
        val contentManager: ContentManager = toolWindow.contentManager

        val existingContent = contentManager.findContent(selectionTabName)
        if (existingContent != null) {
            contentManager.setSelectedContent(existingContent, true)
            logger.info("OpenBranchSelectionTabAction: Found existing '$selectionTabName' tab and selected it.")
            return
        }

        val stateService = ToolWindowStateService.getInstance(project)

        val contentFactory = ContentFactory.getInstance()
        val branchSelectionUi = BranchSelectionPanel(project, project.service<GitService>()) { selectedBranchName: String ->
            logger.info("OpenBranchSelectionTabAction (Callback): Branch '$selectedBranchName' selected from panel.")

            // CRITICAL CHECK: Ensure selectedBranchName is not empty or null
            if (selectedBranchName.isBlank()) {
                logger.error("OpenBranchSelectionTabAction (Callback): selectedBranchName is blank. Cannot proceed.")
                // Optionally, remove the "Select Branch" tab or show an error in its place
                val tempSelectionTab = contentManager.findContent(selectionTabName)
                if (tempSelectionTab != null) {
                    contentManager.removeContent(tempSelectionTab, true)
                }
                return@BranchSelectionPanel
            }

            val manager: ContentManager = toolWindow.contentManager
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
                manager.removeContent(selectionTabContent, true)

                val closableTabs = manager.contents.filter { it.isCloseable }.map { it.displayName }
                val selectedIndex = closableTabs.indexOf(selectedBranchName)
                if (selectedIndex != -1) {
                    logger.info("OpenBranchSelectionTabAction (Callback): Calling stateService.setSelectedTab($selectedIndex) for existing branch '$selectedBranchName'.")
                    stateService.setSelectedTab(selectedIndex)
                } else {
                    logger.warn("OpenBranchSelectionTabAction (Callback): Could not find existing branch '$selectedBranchName' in closable tabs for setSelectedTab after selecting it.")
                }
            } else {
                logger.info("OpenBranchSelectionTabAction (Callback): Repurposing '$selectionTabName' tab to '$selectedBranchName'.")
                selectionTabContent.displayName = selectedBranchName

                // Create the new component (ChangesTreePanel)
                val newBranchContentView = uiProvider.createBranchContentView(selectedBranchName)
                selectionTabContent.component = newBranchContentView

                // Explicitly refresh the new ChangesTreePanel
                if (newBranchContentView is ChangesTreePanel) {
                    logger.info("OpenBranchSelectionTabAction (Callback): Explicitly calling refreshTreeForBranch('$selectedBranchName') on new ChangesTreePanel.")
                    newBranchContentView.refreshTreeForBranch(selectedBranchName)
                } else {
                    logger.warn("OpenBranchSelectionTabAction (Callback): newBranchContentView is not a ChangesTreePanel. Cannot call refreshTreeForBranch. Type: ${newBranchContentView::class.java.name}")
                }

                manager.setSelectedContent(selectionTabContent, true)

                // Log the branch name *just before* adding to state service
                logger.info("OpenBranchSelectionTabAction (Callback): Preparing to add to state. selectedBranchName = '$selectedBranchName'")
                stateService.addTab(selectedBranchName)

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
        newContent.isCloseable = true
        contentManager.addContent(newContent)
        contentManager.setSelectedContent(newContent, true)
    }
}

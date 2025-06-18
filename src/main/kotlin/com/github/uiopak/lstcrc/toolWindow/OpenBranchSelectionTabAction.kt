package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.services.GitService
import com.intellij.icons.AllIcons
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.github.uiopak.lstcrc.utils.LstCrcKeys
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager

/**
 * An action (the "+" button in the tool window header) that opens a temporary tab
 * containing the [BranchSelectionPanel], allowing the user to add a new comparison tab.
 */
class OpenBranchSelectionTabAction(
    private val project: Project,
    private val toolWindow: ToolWindow,
    private val uiProvider: GitChangesToolWindow
) : AnAction(
    LstCrcBundle.message("action.open.branch.selection.text"),
    LstCrcBundle.message("action.open.branch.selection.description"),
    AllIcons.General.Add
) {

    private val logger = thisLogger()

    override fun actionPerformed(e: AnActionEvent) {
        logger.info("OpenBranchSelectionTabAction: actionPerformed called.")
        val selectionTabName = LstCrcBundle.message("tab.name.select.branch")
        val contentManager: ContentManager = toolWindow.contentManager

        val existingContent = contentManager.findContent(selectionTabName)
        if (existingContent != null) {
            contentManager.setSelectedContent(existingContent, true)
            logger.info("OpenBranchSelectionTabAction: Found existing '$selectionTabName' tab and selected it.")
            return
        }

        val stateService = ToolWindowStateService.getInstance(project)
        val contentFactory = ContentFactory.getInstance()

        val branchSelectionUi = BranchSelectionPanel(project, project.service<GitService>()) { selectedBranchName ->
            logger.info("OpenBranchSelectionTabAction (Callback): Branch '$selectedBranchName' selected from panel.")
            if (selectedBranchName.isBlank()) {
                logger.error("OpenBranchSelectionTabAction (Callback): selectedBranchName is blank.")
                toolWindow.contentManager.findContent(selectionTabName)?.let {
                    toolWindow.contentManager.removeContent(it, true)
                }
                return@BranchSelectionPanel
            }

            val manager: ContentManager = toolWindow.contentManager
            val selectionTabContent = manager.findContent(selectionTabName) ?: return@BranchSelectionPanel

            val existingBranchTab = manager.contents.find { it.getUserData(LstCrcKeys.BRANCH_NAME_KEY) == selectedBranchName }

            if (existingBranchTab != null) {
                logger.info("OpenBranchSelectionTabAction (Callback): Tab for '$selectedBranchName' already exists. Selecting it.")
                manager.setSelectedContent(existingBranchTab, true)
                manager.removeContent(selectionTabContent, true)

                val closableTabs = manager.contents.filter { it.isCloseable }.mapNotNull { it.getUserData(LstCrcKeys.BRANCH_NAME_KEY) }
                val selectedIndex = closableTabs.indexOf(selectedBranchName)
                if (selectedIndex != -1) {
                    stateService.setSelectedTab(selectedIndex)
                }
            } else {
                logger.info("OpenBranchSelectionTabAction (Callback): Repurposing '$selectionTabName' tab to '$selectedBranchName'.")
                selectionTabContent.displayName = selectedBranchName
                selectionTabContent.putUserData(LstCrcKeys.BRANCH_NAME_KEY, selectedBranchName)

                val newBranchContentView = uiProvider.createBranchContentView(selectedBranchName)
                selectionTabContent.component = newBranchContentView
                (newBranchContentView as? LstCrcChangesBrowser)?.requestRefreshData()

                manager.setSelectedContent(selectionTabContent, true)
                stateService.addTab(selectedBranchName)

                val closableTabs = manager.contents.filter { it.isCloseable }.mapNotNull { it.getUserData(LstCrcKeys.BRANCH_NAME_KEY) }
                val newTabIndex = closableTabs.indexOf(selectedBranchName)
                if (newTabIndex != -1) {
                    stateService.setSelectedTab(newTabIndex)
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
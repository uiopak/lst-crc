package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.services.GitService
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.github.uiopak.lstcrc.utils.LstCrcKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager

/**
 * A helper object for common tool window UI operations.
 */
object ToolWindowHelper {
    private val logger = thisLogger()

    /**
     * Opens a temporary "Select Branch" tab in the tool window.
     * If such a tab already exists, it is selected. Otherwise, a new one is created.
     * The tab contains a [BranchSelectionPanel] to choose a branch. Upon selection,
     * the temporary tab is replaced by a permanent comparison tab for the selected branch.
     *
     * @param project The current project.
     * @param toolWindow The LST-CRC tool window instance.
     */
    fun openBranchSelectionTab(project: Project, toolWindow: ToolWindow) {
        toolWindow.activate({
            logger.info("HELPER: openBranchSelectionTab called.")
            val selectionTabName = LstCrcBundle.message("tab.name.select.branch")
            val contentManager: ContentManager = toolWindow.contentManager

            val existingContent = contentManager.findContent(selectionTabName)
            if (existingContent != null) {
                contentManager.setSelectedContent(existingContent, true)
                logger.info("HELPER: Found existing '$selectionTabName' tab and selected it.")
                return@activate
            }

            val uiProvider = GitChangesToolWindow(project, toolWindow.disposable)
            val stateService = project.service<ToolWindowStateService>()
            val contentFactory = ContentFactory.getInstance()

            val branchSelectionUi = BranchSelectionPanel(project, project.service<GitService>()) { selectedBranchName ->
                logger.info("HELPER (Callback): Branch '$selectedBranchName' selected from panel.")
                if (selectedBranchName.isBlank()) {
                    logger.error("HELPER (Callback): selectedBranchName is blank.")
                    toolWindow.contentManager.findContent(selectionTabName)?.let {
                        toolWindow.contentManager.removeContent(it, true)
                    }
                    return@BranchSelectionPanel
                }

                val manager: ContentManager = toolWindow.contentManager
                val selectionTabContent = manager.findContent(selectionTabName) ?: return@BranchSelectionPanel

                val existingBranchTab = manager.contents.find { it.getUserData(LstCrcKeys.BRANCH_NAME_KEY) == selectedBranchName }

                if (existingBranchTab != null) {
                    logger.info("HELPER (Callback): Tab for '$selectedBranchName' already exists. Selecting it.")
                    manager.setSelectedContent(existingBranchTab, true)
                    manager.removeContent(selectionTabContent, true)

                    val selectedIndex = stateService.state.openTabs.indexOfFirst { it.branchName == selectedBranchName }
                    if (selectedIndex != -1) {
                        stateService.setSelectedTab(selectedIndex)
                    }
                } else {
                    logger.info("HELPER (Callback): Repurposing '$selectionTabName' tab to '$selectedBranchName'.")
                    val newBranchContentView = uiProvider.createBranchContentView(selectedBranchName)
                    selectionTabContent.displayName = selectedBranchName
                    selectionTabContent.component = newBranchContentView
                    selectionTabContent.putUserData(LstCrcKeys.BRANCH_NAME_KEY, selectedBranchName)
                    (newBranchContentView as? LstCrcChangesBrowser)?.requestRefreshData()

                    manager.setSelectedContent(selectionTabContent, true)
                    stateService.addTab(selectedBranchName)

                    val newTabIndex = stateService.state.openTabs.indexOfFirst { it.branchName == selectedBranchName }
                    if (newTabIndex != -1) {
                        stateService.setSelectedTab(newTabIndex)
                    }
                }
            }

            logger.info("HELPER: Creating and adding new '$selectionTabName' tab to UI.")
            val newContent = contentFactory.createContent(branchSelectionUi.getPanel(), selectionTabName, true).apply {
                isCloseable = true
            }
            contentManager.addContent(newContent)
            contentManager.setSelectedContent(newContent, true)

        }, true, true)
    }
}
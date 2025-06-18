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
     * Creates and selects a new comparison tab for the given branch/revision name.
     * If a tab for this name already exists, it simply selects it.
     *
     * @param project The current project.
     * @param toolWindow The LST-CRC tool window instance.
     * @param branchName The branch or revision identifier for the new tab.
     */
    fun createAndSelectTab(project: Project, toolWindow: ToolWindow, branchName: String) {
        logger.info("HELPER: createAndSelectTab called for '$branchName'")
        val contentManager = toolWindow.contentManager
        val stateService = project.service<ToolWindowStateService>()

        val existingContent = contentManager.contents.find { it.getUserData(LstCrcKeys.BRANCH_NAME_KEY) == branchName }

        if (existingContent != null) {
            logger.info("HELPER: Tab for '$branchName' already exists. Selecting it.")
            contentManager.setSelectedContent(existingContent, true)
            // The ContentManagerListener will trigger stateService.setSelectedTab
        } else {
            logger.info("HELPER: Creating new tab for '$branchName'")
            val uiProvider = GitChangesToolWindow(project, toolWindow.disposable)
            val newContentView = uiProvider.createBranchContentView(branchName)

            val contentFactory = ContentFactory.getInstance()
            val newContent = contentFactory.createContent(newContentView, branchName, false).apply {
                isCloseable = true
                putUserData(LstCrcKeys.BRANCH_NAME_KEY, branchName)
            }

            contentManager.addContent(newContent)
            contentManager.setSelectedContent(newContent, true)

            // Sync state service. The ContentManagerListener will handle selection change, but we need to add the tab.
            stateService.addTab(branchName)
            val newIndex = stateService.state.openTabs.indexOfFirst { it.branchName == branchName }
            if (newIndex != -1) {
                // Manually set index here, as the listener might race or not have the updated tab list yet.
                stateService.setSelectedTab(newIndex)
            } else {
                // Failsafe: if tab not found in state, still trigger a refresh for the content.
                (newContentView as? LstCrcChangesBrowser)?.requestRefreshData()
            }
        }
    }


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
                val manager: ContentManager = toolWindow.contentManager
                val selectionTabContent = manager.findContent(selectionTabName)

                if (selectedBranchName.isBlank() || selectionTabContent == null) {
                    logger.error("HELPER (Callback): selectedBranchName is blank or selection tab disappeared.")
                    selectionTabContent?.let { manager.removeContent(it, true) }
                    return@BranchSelectionPanel
                }

                // Instead of creating a new tab from scratch, repurpose the "Select Branch" tab.
                // This is a smoother user experience than closing one and opening another.
                val existingBranchTab = manager.contents.find { it.getUserData(LstCrcKeys.BRANCH_NAME_KEY) == selectedBranchName }
                if (existingBranchTab != null) {
                    manager.setSelectedContent(existingBranchTab, true)
                    manager.removeContent(selectionTabContent, true) // Remove the temporary selection tab
                } else {
                    logger.info("HELPER (Callback): Repurposing '$selectionTabName' tab to '$selectedBranchName'.")
                    val newBranchContentView = uiProvider.createBranchContentView(selectedBranchName)
                    selectionTabContent.displayName = selectedBranchName
                    selectionTabContent.component = newBranchContentView
                    selectionTabContent.putUserData(LstCrcKeys.BRANCH_NAME_KEY, selectedBranchName)

                    manager.setSelectedContent(selectionTabContent, true)
                    stateService.addTab(selectedBranchName)
                }
                // The selectionChanged listener on ContentManager will handle the stateService.setSelectedTab call.
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
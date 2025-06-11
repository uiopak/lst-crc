package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.github.uiopak.lstcrc.utils.LstCrcKeys
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory

class CreateTabFromRevisionAction : AnAction("LST-CRC: Compare with Revision") {

    private val logger = thisLogger()

    override fun update(e: AnActionEvent) {
        val project = e.project
        val revisions = e.getData(VcsDataKeys.VCS_REVISION_NUMBERS)

        // Enable the action only if there's a project and exactly one revision is selected.
        e.presentation.isEnabledAndVisible = project != null && revisions?.size == 1
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val revisionNumber = e.getData(VcsDataKeys.VCS_REVISION_NUMBERS)?.firstOrNull() ?: return

        val revisionString = revisionNumber.asString()
        logger.info("Action performed: Create tab for revision '$revisionString'")

        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow("GitChangesView") ?: run {
            logger.error("Could not find ToolWindow 'GitChangesView'")
            return
        }

        // Activate the tool window, and run the tab creation logic after it's visible.
        toolWindow.activate {
            val contentManager = toolWindow.contentManager
            val stateService = ToolWindowStateService.getInstance(project)

            // Check if a tab for this revision already exists using the branch name key.
            val existingContent = contentManager.contents.find {
                it.getUserData(LstCrcKeys.BRANCH_NAME_KEY) == revisionString
            }

            if (existingContent != null) {
                // If it exists, just select it.
                logger.info("Tab for revision '$revisionString' already exists. Selecting it.")
                contentManager.setSelectedContent(existingContent, true)
            } else {
                // If it doesn't exist, create it.
                logger.info("Creating new tab for revision '$revisionString'")
                val uiProvider = GitChangesToolWindow(project, toolWindow.disposable)
                val newContentView = uiProvider.createBranchContentView(revisionString)

                val contentFactory = ContentFactory.getInstance()
                // The revision hash itself will be the tab name initially. It can be renamed later.
                val newContent = contentFactory.createContent(newContentView, revisionString, false).apply {
                    isCloseable = true
                    // Use the branch name key to store the revision hash. This is consistent with how we treat branches.
                    putUserData(LstCrcKeys.BRANCH_NAME_KEY, revisionString)
                }

                contentManager.addContent(newContent)
                contentManager.setSelectedContent(newContent, true)

                // Update the state service
                stateService.addTab(revisionString)

                // After adding, find its index in the state and set it as selected.
                val newIndex = stateService.state.openTabs.indexOfFirst { it.branchName == revisionString }
                if (newIndex != -1) {
                    stateService.setSelectedTab(newIndex)
                }

                // Refresh data for the new panel
                (newContentView as? LstCrcChangesBrowser)?.requestRefreshData()

                // NEW: Automatically trigger the rename dialog
                RenameTabAction.invokeRenameDialog(project, revisionString)
            }
        }
    }
}
package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.github.uiopak.lstcrc.utils.LstCrcKeys
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory

/**
 * An action available in the Git Log context menu to create a new LST-CRC comparison tab
 * for the selected revision. This allows comparing the current working directory against
 * any commit or tag.
 */
class CreateTabFromRevisionAction : AnAction() {

    private val logger = thisLogger()

    override fun update(e: AnActionEvent) {
        val project = e.project
        val revisions = e.getData(VcsDataKeys.VCS_REVISION_NUMBERS)
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

        toolWindow.activate {
            val contentManager = toolWindow.contentManager
            val stateService = ToolWindowStateService.getInstance(project)

            val existingContent = contentManager.contents.find {
                it.getUserData(LstCrcKeys.BRANCH_NAME_KEY) == revisionString
            }

            if (existingContent != null) {
                logger.info("Tab for revision '$revisionString' already exists. Selecting it.")
                contentManager.setSelectedContent(existingContent, true)
            } else {
                logger.info("Creating new tab for revision '$revisionString'")
                val uiProvider = GitChangesToolWindow(project, toolWindow.disposable)
                val newContentView = uiProvider.createBranchContentView(revisionString)

                val contentFactory = ContentFactory.getInstance()
                val newContent = contentFactory.createContent(newContentView, revisionString, false).apply {
                    isCloseable = true
                    // Use the branch name key to store the revision hash, consistent with how branches are handled.
                    putUserData(LstCrcKeys.BRANCH_NAME_KEY, revisionString)
                }

                contentManager.addContent(newContent)
                contentManager.setSelectedContent(newContent, true)

                stateService.addTab(revisionString)

                val newIndex = stateService.state.openTabs.indexOfFirst { it.branchName == revisionString }
                if (newIndex != -1) {
                    stateService.setSelectedTab(newIndex)
                }

                (newContentView as? LstCrcChangesBrowser)?.requestRefreshData()

                // Automatically trigger the rename dialog for the new revision tab.
                RenameTabAction.invokeRenameDialog(project, revisionString)
            }
        }
    }
}
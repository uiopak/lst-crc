package com.github.uiopak.lstcrc.toolWindow

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.wm.ToolWindowManager

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
            // Use the helper to handle tab creation and selection logic.
            ToolWindowHelper.createAndSelectTab(project, toolWindow, revisionString)
            // After the tab is created, trigger the rename dialog.
            RenameTabAction.invokeRenameDialog(project, revisionString)
        }
    }
}
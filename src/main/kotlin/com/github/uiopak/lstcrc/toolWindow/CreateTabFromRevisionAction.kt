package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.LstCrcConstants
import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.ui.Messages

/**
 * An action available in the Git Log context menu to create a new LST-CRC comparison tab
 * for the selected revision. This allows comparing the current working directory against
 * any commit or tag.
 */
class CreateTabFromRevisionAction : AnAction() {

    private val logger = thisLogger()

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null && singleSelectedRevisionString(e) != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val revisionString = singleSelectedRevisionString(e) ?: return
        logger.debug { "Action performed: Create tab for revision '$revisionString'" }

        val newAlias = Messages.showInputDialog(
            project,
            LstCrcBundle.message("dialog.rename.tab.message"),
            LstCrcBundle.message("dialog.rename.tab.title"),
            Messages.getQuestionIcon(),
            revisionString,
            null
        )
        if (newAlias == null) {
            logger.debug { "User cancelled alias selection. Aborting tab creation." }
            return
        }

        val activated = ToolWindowHelper.activateToolWindow(project) { toolWindow ->
            ToolWindowHelper.createAndSelectTab(project, toolWindow, revisionString)
            ToolWindowHelper.updateNormalizedTabAlias(project, revisionString, newAlias)
        }
        if (!activated) {
            logger.error("Could not find ToolWindow '${LstCrcConstants.TOOL_WINDOW_ID}'")
        }
    }
}
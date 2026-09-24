package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.LstCrcConstants
import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow

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
        logger.info("Action performed: Create tab for revision '$revisionString'")

        val newAlias = promptForAlias(project, revisionString)

        // If the user cancels the dialog, do nothing.
        if (newAlias == null) {
            logger.info("User cancelled alias selection. Aborting tab creation.")
            return
        }

        val activated = ToolWindowHelper.activateToolWindow(project) { toolWindow ->
            createRevisionTab(project, toolWindow, revisionString, newAlias)
        }
        if (!activated) {
            logger.error("Could not find ToolWindow '${LstCrcConstants.TOOL_WINDOW_ID}'")
            return
        }
    }

    private fun createRevisionTab(
        project: Project,
        toolWindow: ToolWindow,
        revisionString: String,
        newAlias: String
    ) {
        ToolWindowHelper.createAndSelectTab(project, toolWindow, revisionString)
        ToolWindowHelper.updateNormalizedTabAlias(project, revisionString, newAlias)
    }

    private fun promptForAlias(project: Project, revisionString: String): String? {
        return Messages.showInputDialog(
            project,
            LstCrcBundle.message("dialog.rename.tab.message"),
            LstCrcBundle.message("dialog.rename.tab.title"),
            Messages.getQuestionIcon(),
            revisionString,
            null
        )
    }
}
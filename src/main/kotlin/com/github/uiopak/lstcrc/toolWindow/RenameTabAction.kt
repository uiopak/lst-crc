package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.github.uiopak.lstcrc.utils.LstCrcKeys
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages

class RenameTabAction : AnAction("Rename Tab...") {

    override fun update(e: AnActionEvent) {
        val project = e.project
        val toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW)
        if (project == null || toolWindow == null || toolWindow.id != "GitChangesView") {
            e.presentation.isEnabledAndVisible = false
            return
        }

        // This checks the currently *active* tab, not necessarily the right-clicked one.
        // We are reverting to this logic because it compiles in your environment.
        val content = toolWindow.contentManager.selectedContent
        e.presentation.isEnabledAndVisible = content != null && content.isCloseable
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project!!
        val toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW)!!
        val content = toolWindow.contentManager.selectedContent!!

        // Get the branch name from the UserData we stored on the Content object.
        val branchName = content.getUserData(LstCrcKeys.BRANCH_NAME_KEY)
        if (branchName == null) {
            Messages.showErrorDialog(project, "Could not determine the branch for this tab.", "Rename Error")
            return
        }

        val stateService = project.service<ToolWindowStateService>()
        val tabInfo = stateService.state.openTabs.find { it.branchName == branchName }
        val currentDisplayName = tabInfo?.alias ?: branchName

        val newAlias = Messages.showInputDialog(
            project,
            "Enter new name for tab. Leave blank to reset to branch name.",
            "Rename Tab",
            Messages.getQuestionIcon(),
            currentDisplayName,
            null // No validator
        )

        // newAlias is null if user presses Cancel
        if (newAlias != null) {
            // If user clicks OK with an empty or blank string, we reset the alias by setting it to null.
            val finalAlias = newAlias.trim().ifEmpty { null }
            stateService.updateTabAlias(branchName, finalAlias)
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
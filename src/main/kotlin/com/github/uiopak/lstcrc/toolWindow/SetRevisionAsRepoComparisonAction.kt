package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.vcs.log.VcsLogDataKeys
import git4idea.repo.GitRepositoryManager

/**
 * An action available in the Git Log context menu to set the selected revision as the
 * comparison point for its repository within the currently active LST-CRC tab.
 */
class SetRevisionAsRepoComparisonAction : AnAction() {

    override fun update(e: AnActionEvent) {
        val project = e.project

        e.presentation.isEnabledAndVisible = project != null &&
                hasSingleCommitSelection(e) &&
                selectedTabInfo(project) != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selection = e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION) ?: return
        val commitId = selection.commits.firstOrNull() ?: return

        val repo = GitRepositoryManager.getInstance(project).getRepositoryForRoot(commitId.root) ?: return
        val stateService = project.service<ToolWindowStateService>()
        val selectedTabInfo = selectedTabInfo(project) ?: return

        val revisionString = commitId.hash.asString()
        stateService.updateTabRepoComparison(selectedTabInfo.branchName, repo.root.path, revisionString)
    }

    private fun hasSingleCommitSelection(e: AnActionEvent): Boolean {
        return e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION)?.commits?.size == 1
    }

    private fun selectedTabInfo(project: com.intellij.openapi.project.Project) =
        project.service<ToolWindowStateService>().getSelectedTabInfo()
}
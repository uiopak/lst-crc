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
        val selection = e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION)
        val selectedTabInfo = project?.service<ToolWindowStateService>()?.getSelectedTabInfo()

        e.presentation.isEnabledAndVisible = project != null &&
                selection?.commits?.size == 1 &&
                selectedTabInfo != null
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
        val selectedTabInfo = stateService.getSelectedTabInfo() ?: return

        val revisionString = commitId.hash.asString()
        val newMap = selectedTabInfo.comparisonMap.toMutableMap()
        newMap[repo.root.path] = revisionString

        stateService.updateTabComparisonMap(selectedTabInfo.branchName, newMap)
    }
}
package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.VcsLogDataKeys
import git4idea.repo.GitRepository
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

        // Enabled if one commit is selected and a closable tab is currently active.
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

        // Perform repository lookup in a background task to avoid blocking the EDT.
        object : Task.Backgroundable(project, LstCrcBundle.message("git.task.repo.info"), false) {
            var repository: GitRepository? = null

            override fun run(indicator: ProgressIndicator) {
                // This is safe to run on a BGT
                repository = GitRepositoryManager.getInstance(project).getRepositoryForRoot(commitId.root)
            }

            override fun onSuccess() {
                // Back on the EDT for state updates
                val repo = repository
                if (project.isDisposed || repo == null) return

                val stateService = project.service<ToolWindowStateService>()
                val selectedTabInfo = stateService.getSelectedTabInfo() ?: return // Re-check in case it changed

                val revisionString = commitId.hash.asString()
                val newMap = selectedTabInfo.comparisonMap.toMutableMap()
                newMap[repo.root.path] = revisionString

                stateService.updateTabComparisonMap(selectedTabInfo.branchName, newMap)
            }
        }.queue()
    }
}
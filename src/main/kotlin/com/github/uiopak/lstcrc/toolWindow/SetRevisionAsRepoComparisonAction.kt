package com.github.uiopak.lstcrc.toolWindow

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import git4idea.repo.GitRepositoryManager

/**
 * An action available in the Git Log context menu to set the selected revision as the
 * comparison point for its repository within the currently active LST-CRC tab.
 */
class SetRevisionAsRepoComparisonAction : AnAction() {

    override fun update(e: AnActionEvent) {
        val project = e.project

        e.presentation.isEnabledAndVisible = project != null &&
                hasSingleSelectedCommit(e) &&
                selectedLstCrcTab(project) != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val commitId = singleSelectedCommit(e) ?: return

        val repo = GitRepositoryManager.getInstance(project).getRepositoryForRoot(commitId.root) ?: return
        val selectedTabInfo = selectedLstCrcTab(project) ?: return

        val revisionString = commitId.hash.asString()
        project.service<com.github.uiopak.lstcrc.services.ToolWindowStateService>()
            .updateTabRepoComparison(selectedTabInfo.branchName, repo.root.path, revisionString)
    }
}
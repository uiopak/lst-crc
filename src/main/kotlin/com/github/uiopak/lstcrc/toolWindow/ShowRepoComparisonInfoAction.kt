@file:Suppress("DialogTitleCapitalization")

package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.services.GitService
import com.github.uiopak.lstcrc.state.TabInfo
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import git4idea.repo.GitRepository

/**
 * Action to open a popup showing the current comparison context for each repository
 * and allowing the user to change it.
 */
internal class ShowRepoComparisonInfoAction : DumbAwareAction(
    LstCrcBundle.message("action.configure.repos.text"),
    LstCrcBundle.message("action.configure.repos.description"),
    AllIcons.General.GearPlain
) {
    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        e.presentation.isEnabledAndVisible = selectedLstCrcTab(project) != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val gitService = project.service<GitService>()
        val tabInfo = selectedLstCrcTab(project) ?: return

        val repositories = gitService.getRepositories()

        if (repositories.size == 1) {
            showRepoComparisonDialog(project, repositories.first(), tabInfo)
            return
        }

        showMultiRepoComparisonPopup(project, repositories, tabInfo, e)
    }

    private fun showMultiRepoComparisonPopup(
        project: Project,
        repositories: List<GitRepository>,
        tabInfo: TabInfo,
        event: AnActionEvent
    ) {
        val actionGroup = createRepoSelectionActionGroup(project, repositories, tabInfo)

        val dataContext = DataManager.getInstance().getDataContext(event.inputEvent?.component)
        val popup = JBPopupFactory.getInstance().createActionGroupPopup(
            LstCrcBundle.message("action.configure.repos.popup.title"),
            actionGroup,
            dataContext,
            JBPopupFactory.ActionSelectionAid.MNEMONICS,
            true
        )
        popup.showInBestPositionFor(dataContext)
    }

    private fun createRepoSelectionActionGroup(
        project: Project,
        repositories: List<GitRepository>,
        tabInfo: TabInfo
    ): DefaultActionGroup {
        val actionGroup = DefaultActionGroup()
        for (repo in repositories.sortedBy { it.root.name }) {
            actionGroup.add(repoSelectionAction(project, repo, tabInfo))
        }
        return actionGroup
    }

    private fun repoSelectionAction(
        project: Project,
        repo: GitRepository,
        tabInfo: TabInfo
    ): AnAction {
        val currentTarget = tabInfo.comparisonMap[repo.root.path] ?: tabInfo.branchName
        val actionText = LstCrcBundle.message("changes.browser.repo.node.full.comparison.text", repo.root.name, currentTarget)

        return object : AnAction(actionText) {
            override fun actionPerformed(e: AnActionEvent) {
                showRepoComparisonDialog(project, repo, tabInfo)
            }
        }
    }

    private fun showRepoComparisonDialog(
        project: Project,
        repo: GitRepository,
        tabInfo: TabInfo
    ) {
        SingleRepoBranchSelectionDialog(project, repo, tabInfo).show()
    }
}

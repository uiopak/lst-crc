@file:Suppress("DialogTitleCapitalization")

package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.services.GitService
import com.github.uiopak.lstcrc.state.TabInfo
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
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
        e.presentation.isEnabledAndVisible = project != null && selectedLstCrcTab(project) != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val gitService = project.service<GitService>()
        val tabInfo = selectedLstCrcTab(project) ?: return
        val repositories = gitService.getRepositories()

        if (repositories.size == 1) {
            SingleRepoBranchSelectionDialog(project, repositories.first(), tabInfo).show()
            return
        }

        // Several repositories: pick one from a popup listing each with its current target.
        val actionGroup = DefaultActionGroup(repoComparisonItems(project, tabInfo, repositories))

        val dataContext = DataManager.getInstance().getDataContext(e.inputEvent?.component)
        JBPopupFactory.getInstance().createActionGroupPopup(
            LstCrcBundle.message("action.configure.repos.popup.title"),
            actionGroup,
            dataContext,
            JBPopupFactory.ActionSelectionAid.MNEMONICS,
            true
        ).showInBestPositionFor(dataContext)
    }
}

/** The popup items of [ShowRepoComparisonInfoAction]: each repository, by name, with its current target in [tabInfo]. */
internal fun repoComparisonItems(project: Project, tabInfo: TabInfo, repositories: List<GitRepository>): List<AnAction> {
    val gitService = project.service<GitService>()
    return repositories.sortedBy { it.root.name }.map { repo ->
        val currentTarget = gitService.resolveComparisonTarget(repo, tabInfo)
        val text = LstCrcBundle.message("changes.browser.repo.node.full.comparison.text", repo.root.name, currentTarget)
        plainTextAction(text) { SingleRepoBranchSelectionDialog(project, repo, tabInfo).show() }
    }
}

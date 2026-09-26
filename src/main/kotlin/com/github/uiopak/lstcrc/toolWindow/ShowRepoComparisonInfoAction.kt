@file:Suppress("DialogTitleCapitalization")

package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.services.GitService
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory

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
        val actionGroup = DefaultActionGroup()
        for (repo in repositories.sortedBy { it.root.name }) {
            val currentTarget = gitService.resolveComparisonTarget(repo, tabInfo)
            val actionText = LstCrcBundle.message("changes.browser.repo.node.full.comparison.text", repo.root.name, currentTarget)
            actionGroup.add(object : AnAction(actionText) {
                override fun actionPerformed(e: AnActionEvent) = SingleRepoBranchSelectionDialog(project, repo, tabInfo).show()
            })
        }

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

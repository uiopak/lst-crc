@file:Suppress("DialogTitleCapitalization")

package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.services.GitService
import com.github.uiopak.lstcrc.services.ToolWindowStateService
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
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        val stateService = project.service<ToolWindowStateService>()
        e.presentation.isEnabledAndVisible = stateService.getSelectedTabInfo() != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val stateService = project.service<ToolWindowStateService>()
        val gitService = project.service<GitService>()
        val tabInfo = stateService.getSelectedTabInfo() ?: return

        val repositories = gitService.getRepositories()

        if (repositories.size == 1) {
            SingleRepoBranchSelectionDialog(project, repositories.first(), tabInfo).show()
            return
        }

        val actionGroup = DefaultActionGroup()

        for (repo in repositories.sortedBy { it.root.name }) {
            val currentTarget = tabInfo.comparisonMap[repo.root.path] ?: tabInfo.branchName
            val actionText = LstCrcBundle.message("changes.browser.repo.node.full.comparison.text", repo.root.name, currentTarget)

            actionGroup.add(object : AnAction(actionText) {
                override fun actionPerformed(e: AnActionEvent) {
                    SingleRepoBranchSelectionDialog(project, repo, tabInfo).show()
                }
            })
        }

        val dataContext = DataManager.getInstance().getDataContext(e.inputEvent?.component)
        val popup = JBPopupFactory.getInstance().createActionGroupPopup(
            LstCrcBundle.message("action.configure.repos.popup.title"),
            actionGroup,
            dataContext,
            JBPopupFactory.ActionSelectionAid.MNEMONICS,
            true
        )
        popup.showInBestPositionFor(dataContext)
    }
}

package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.services.GitService
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.github.uiopak.lstcrc.state.TabInfo
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.util.ui.JBUI
import git4idea.repo.GitRepository
import java.awt.Dimension
import javax.swing.Action
import javax.swing.JComponent

class SingleRepoBranchSelectionDialog(
    private val project: Project,
    private val repository: GitRepository,
    private val tabInfo: TabInfo
) : DialogWrapper(project, true) {

    private var selectedBranchName: String? = null

    init {
        title = LstCrcBundle.message("dialog.select.branch.for.repo.title", repository.root.name)
        init()
    }

    override fun getInitialSize(): Dimension {
        return JBUI.size(350, 500)
    }

    override fun createCenterPanel(): JComponent =
        BranchSelectionPanel(project.service<GitService>(), repository) { branchName ->
            selectedBranchName = branchName
            doOKAction()
        }.apply { requestFocusOnSearchField() }

    /**
     * We handle closing via the branch selection callback, so we don't need explicit OK/Cancel buttons.
     */
    override fun createActions(): Array<Action> = emptyArray()

    override fun doOKAction() {
        if (isOK) { // Prevent multiple executions
            return
        }
        selectedBranchName?.let { branchToSet ->
            project.service<ToolWindowStateService>().updateTabRepoComparison(
                branchName = tabInfo.branchName,
                repositoryRootPath = repository.root.path,
                targetRevision = branchToSet,
                defaultTarget = tabInfo.branchName
            )
        }
        super.doOKAction()
    }
}
package com.github.uiopak.lstcrc.toolWindow

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
        title = "Select Branch for ${repository.root.name}"
        init()
    }

    override fun getInitialSize(): Dimension {
        return JBUI.size(350, 500)
    }

    override fun createCenterPanel(): JComponent {
        val gitService = project.service<GitService>()
        // Pass the specific repository to the panel so it shows the correct branch list.
        val panel = BranchSelectionPanel(gitService, repository) { branchName ->
            // When a branch is selected in the panel, store it and close the dialog with OK.
            this.selectedBranchName = branchName
            this.doOKAction()
        }
        panel.requestFocusOnSearchField()
        return panel.getPanel()
    }

    /**
     * We handle closing via the branch selection callback, so we don't need explicit OK/Cancel buttons.
     */
    override fun createActions(): Array<Action> = emptyArray()

    override fun doOKAction() {
        if (isOK) { // Prevent multiple executions
            return
        }
        val branchToSet = selectedBranchName
        if (branchToSet != null) {
            val stateService = project.service<ToolWindowStateService>()
            val newMap = tabInfo.comparisonMap.toMutableMap()

            // Determine if we need to store an override or can remove one.
            val defaultTarget = if (repository.branches.findBranchByName(tabInfo.branchName) != null) tabInfo.branchName else "HEAD"

            if (branchToSet == defaultTarget) {
                // If the user selected the default, we can remove the override from the map.
                newMap.remove(repository.root.path)
            } else {
                // Otherwise, store the explicit override.
                newMap[repository.root.path] = branchToSet
            }
            stateService.updateTabComparisonMap(tabInfo.branchName, newMap)
        }
        super.doOKAction()
    }
}
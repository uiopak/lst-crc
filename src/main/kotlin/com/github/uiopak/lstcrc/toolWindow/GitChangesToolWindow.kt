package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.services.GitService
import com.github.uiopak.lstcrc.services.GitService
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import javax.swing.JComponent


class GitChangesToolWindow(
    private val project: Project,
    private val toolWindowDisposable: com.intellij.openapi.Disposable // New parameter
) {
    private val gitService = project.service<GitService>()
    private val propertiesComponent = PropertiesComponent.getInstance()

    fun createBranchContentView(branchName: String): JComponent {
        // Pass new disposable
        return ChangesTreePanel(project, gitService, propertiesComponent, branchName, toolWindowDisposable)
    }

    fun showBranchSelectionDialog(onBranchSelected: (branchName: String) -> Unit) {
        BranchSelectionDialogWrapper(project, gitService, onBranchSelected).show()
    }

    fun createBranchSelectionView(onBranchSelected: (branchName: String) -> Unit): JComponent {
        return BranchSelectionPanel(project, gitService, onBranchSelected).getPanel()
    }
}

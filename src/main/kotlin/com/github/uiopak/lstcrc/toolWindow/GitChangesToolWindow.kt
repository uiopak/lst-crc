package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.services.GitService
// Removed AllIcons
// Removed DataManager
// Removed actionSystem.*
import com.intellij.ide.util.PropertiesComponent // First instance
import com.intellij.openapi.components.service // First instance
// Removed thisLogger
// Removed OpenFileDescriptor
// Removed FileTypeManager
// Removed DumbAwareAction
import com.intellij.openapi.project.Project
// Removed Messages
// Removed Change (both instances)
// Removed ShowDiffAction
// Removed second PropertiesComponent import
// Removed second service import
import javax.swing.JComponent


class GitChangesToolWindow(private val project: Project) {
    private val gitService = project.service<GitService>()
    private val propertiesComponent = PropertiesComponent.getInstance()

    fun createBranchContentView(branchName: String): JComponent {
        return ChangesTreePanel(project, gitService, propertiesComponent, branchName)
    }

    fun showBranchSelectionDialog(onBranchSelected: (branchName: String) -> Unit) {
        BranchSelectionDialogWrapper(project, gitService, onBranchSelected).show()
    }

    fun createBranchSelectionView(onBranchSelected: (branchName: String) -> Unit): JComponent {
        // Now instantiates BranchSelectionPanel
        return BranchSelectionPanel(project, gitService, onBranchSelected).getPanel()
    }
}

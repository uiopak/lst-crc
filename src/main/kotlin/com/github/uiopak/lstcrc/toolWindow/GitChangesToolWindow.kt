package com.github.uiopak.lstcrc.toolWindow

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import javax.swing.JComponent


class GitChangesToolWindow(
    private val project: Project,
    private val toolWindowDisposable: com.intellij.openapi.Disposable // New parameter
) {
    // Use application-level settings so the tree panel gets the correct instance.
    private val propertiesComponent = PropertiesComponent.getInstance()

    fun createBranchContentView(branchName: String): JComponent {
        // Pass new disposable
        return LstCrcChangesBrowser(project, propertiesComponent, branchName, toolWindowDisposable)
    }
}
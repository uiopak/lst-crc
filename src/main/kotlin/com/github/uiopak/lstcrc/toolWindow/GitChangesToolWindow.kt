package com.github.uiopak.lstcrc.toolWindow

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import javax.swing.JComponent


/**
 * A factory class responsible for creating the main content view ([LstCrcChangesBrowser])
 * for a given branch or revision tab within the tool window.
 */
class GitChangesToolWindow(
    private val project: Project,
    private val toolWindowDisposable: com.intellij.openapi.Disposable
) {
    private val propertiesComponent = PropertiesComponent.getInstance()

    fun createBranchContentView(branchName: String): JComponent {
        return LstCrcChangesBrowser(project, propertiesComponent, branchName, toolWindowDisposable)
    }
}
package com.github.uiopak.lstcrc.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

@Service(Service.Level.PROJECT)
class SelectedBranchService {

    fun getSelectedBranchName(project: Project): String? {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow("GitChangesView")
        val contentManager = toolWindow?.contentManager
        val selectedContent = contentManager?.selectedContent

        return if (selectedContent != null && selectedContent.displayName != "Select Branch") {
            selectedContent.displayName
        } else {
            null
        }
    }
}

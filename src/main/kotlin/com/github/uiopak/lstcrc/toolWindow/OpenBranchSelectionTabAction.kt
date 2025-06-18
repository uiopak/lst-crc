package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow

/**
 * An action (the "+" button in the tool window header) that opens a temporary tab
 * containing the [BranchSelectionPanel], allowing the user to add a new comparison tab.
 */
class OpenBranchSelectionTabAction(
    private val project: Project,
    private val toolWindow: ToolWindow
) : AnAction(
    LstCrcBundle.message("action.open.branch.selection.text"),
    LstCrcBundle.message("action.open.branch.selection.description"),
    AllIcons.General.Add
) {

    private val logger = thisLogger()

    override fun actionPerformed(e: AnActionEvent) {
        logger.info("OpenBranchSelectionTabAction: actionPerformed called.")
        ToolWindowHelper.openBranchSelectionTab(project, toolWindow)
    }
}
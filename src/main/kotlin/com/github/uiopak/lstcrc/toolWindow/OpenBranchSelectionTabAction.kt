package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow

/**
 * An action (the "+" button in the tool window header) that opens a temporary tab
 * containing the [BranchSelectionPanel], allowing the user to add a new comparison tab.
 * This action automatically hides itself if the "Select Branch" tab is already open.
 */
class OpenBranchSelectionTabAction(
    private val project: Project,
    private val toolWindow: ToolWindow
) : DumbAwareAction( // Changed to DumbAwareAction for better responsiveness
    LstCrcBundle.message("action.open.branch.selection.text"),
    LstCrcBundle.message("action.open.branch.selection.description"),
    AllIcons.General.Add
) {

    private val logger = thisLogger()

    /**
     * Controls the visibility of the action. It is hidden if the "Select Branch" tab is already open.
     */
    override fun update(e: AnActionEvent) {
        val selectionTabName = LstCrcBundle.message("tab.name.select.branch")
        val contentManager = toolWindow.contentManager
        val selectionTabExists = contentManager.contents.any { it.displayName == selectionTabName }

        e.presentation.isEnabledAndVisible = !selectionTabExists
    }

    /**
     * This action is safe to run on a background thread as it only reads UI properties.
     */
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        logger.info("OpenBranchSelectionTabAction: actionPerformed called.")
        ToolWindowHelper.openBranchSelectionTab(project, toolWindow)
    }
}
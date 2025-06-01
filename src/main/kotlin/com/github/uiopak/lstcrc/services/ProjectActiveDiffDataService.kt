package com.github.uiopak.lstcrc.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
// Explicitly ensure ToolWindowStateService is imported if not in same package
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change

@Service(Service.Level.PROJECT)
class ProjectActiveDiffDataService(private val project: Project) : Disposable {
    private val logger = thisLogger()

    var activeBranchName: String? = null
        private set // Allow external read, internal write
    var activeChanges: List<Change> = emptyList()
        private set // Allow external read, internal write

    fun updateActiveDiff(branchNameFromEvent: String, changesFromEvent: List<Change>) {
        val currentToolWindowBranch = project.service<ToolWindowStateService>().getSelectedTabBranchName()

        // Only update and refresh if the incoming data is for the currently selected branch in the tool window.
        // This prevents updates for a previously active branch (from a delayed async operation)
        // from overriding the data for the truly current branch.
        if (branchNameFromEvent == currentToolWindowBranch) {
            logger.info("Updating active diff for currently selected tool window branch: $branchNameFromEvent with ${changesFromEvent.size} changes.")
            this.activeBranchName = branchNameFromEvent
            this.activeChanges = changesFromEvent
            triggerEditorTabColorRefresh()
        } else {
            logger.info("Received diff update for branch '$branchNameFromEvent', but tool window is currently on '$currentToolWindowBranch'. Ignoring stale update.")
        }
    }

    fun clearActiveDiff() {
        logger.info("Clearing active diff data.")
        this.activeBranchName = null
        this.activeChanges = emptyList()
        triggerEditorTabColorRefresh()
    }

    private fun triggerEditorTabColorRefresh() {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) {
                logger.info("Project is disposed, skipping editor tab color refresh.")
                return@invokeLater
            }
            val fileEditorManager = FileEditorManager.getInstance(project)
            fileEditorManager.openFiles.forEach { vf ->
                if (vf.isValid) {
                    logger.debug("Requesting presentation update for file: ${vf.path}")
                    fileEditorManager.updateFilePresentation(vf)
                }
            }
        }
    }

    fun refreshCurrentColorings() {
        logger.info("Explicitly refreshing current editor tab colorings for branch: $activeBranchName")
        triggerEditorTabColorRefresh() // This is the existing private method
    }

    override fun dispose() {
        logger.info("Disposing ProjectActiveDiffDataService, clearing changes.")
        activeChanges = emptyList()
        activeBranchName = null
    }
}

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
import com.intellij.openapi.vfs.VirtualFile

@Service(Service.Level.PROJECT)
class ProjectActiveDiffDataService(private val project: Project) : Disposable {
    private val logger = thisLogger()

    var activeBranchName: String? = null
        private set // Allow external read, internal write
    var activeChanges: List<Change> = emptyList()
        private set // Allow external read, internal write
    var createdFiles: List<VirtualFile> = emptyList()
        private set
    var modifiedFiles: List<VirtualFile> = emptyList()
        private set
    var movedFiles: List<VirtualFile> = emptyList()
        private set

    fun updateActiveDiff(
        branchNameFromEvent: String,
        changesFromEvent: List<Change>,
        createdFilesFromEvent: List<VirtualFile>,
        modifiedFilesFromEvent: List<VirtualFile>,
        movedFilesFromEvent: List<VirtualFile>
    ) {
        val currentToolWindowBranch = project.service<ToolWindowStateService>().getSelectedTabBranchName()
        logger.info("SERVICE_UPDATE_DIFF: updateActiveDiff called. Event branch: '$branchNameFromEvent' (${changesFromEvent.size} changes, ${createdFilesFromEvent.size} created, ${modifiedFilesFromEvent.size} modified, ${movedFilesFromEvent.size} moved). Current tool window branch: '$currentToolWindowBranch'.")

        if (branchNameFromEvent == currentToolWindowBranch) {
            logger.info("SERVICE_UPDATE_DIFF: Event branch matches current tool window branch. Updating active data. Created: ${createdFilesFromEvent.size}, Modified: ${modifiedFilesFromEvent.size}, Moved: ${movedFilesFromEvent.size}")
            this.activeBranchName = branchNameFromEvent
            this.activeChanges = changesFromEvent
            this.createdFiles = createdFilesFromEvent
            this.modifiedFiles = modifiedFilesFromEvent
            this.movedFiles = movedFilesFromEvent
            triggerEditorTabColorRefresh()
        } else {
            logger.info("SERVICE_UPDATE_DIFF: Event branch '$branchNameFromEvent' does NOT match current tool window branch '$currentToolWindowBranch'. Ignoring stale update.")
        }
    }

    fun clearActiveDiff() {
        logger.info("SERVICE_CLEAR_DIFF: clearActiveDiff called. Clearing activeBranchName, activeChanges, createdFiles, modifiedFiles, and movedFiles.")
        this.activeBranchName = null
        this.activeChanges = emptyList()
        this.createdFiles = emptyList()
        this.modifiedFiles = emptyList()
        this.movedFiles = emptyList()
        triggerEditorTabColorRefresh()
    }

    private fun triggerEditorTabColorRefresh() {
        logger.info("SERVICE_REFRESH_TRIGGER: triggerEditorTabColorRefresh() called.")
        ApplicationManager.getApplication().invokeLater {
            logger.info("SERVICE_REFRESH_TRIGGER: invokeLater running. Project disposed: ${project.isDisposed}")
            if (project.isDisposed) {
                logger.info("Project is disposed, skipping editor tab color refresh.") // This log was already here, good.
                return@invokeLater
            }
            val fileEditorManager = FileEditorManager.getInstance(project)
            val openFiles = fileEditorManager.openFiles
            logger.info("SERVICE_REFRESH_TRIGGER: Found ${openFiles.size} open files to update.")
            openFiles.forEach { vf ->
                if (vf.isValid) {
                    logger.info("SERVICE_REFRESH_TRIGGER: Requesting presentation update for file: ${vf.path}")
                    fileEditorManager.updateFilePresentation(vf)
                } else {
                    logger.info("SERVICE_REFRESH_TRIGGER: File ${vf.path} is invalid, skipping update.")
                }
            }
            logger.info("SERVICE_REFRESH_TRIGGER: updateFilePresentation requests sent for all valid open files.")
        }
    }

    fun refreshCurrentColorings() {
        logger.info("SERVICE_REFRESH: refreshCurrentColorings() called. Active branch: $activeBranchName, Changes count: ${activeChanges.size}")
        triggerEditorTabColorRefresh()
    }

    override fun dispose() {
        logger.info("Disposing ProjectActiveDiffDataService, clearing changes and file lists.")
        activeChanges = emptyList()
        activeBranchName = null
        createdFiles = emptyList()
        modifiedFiles = emptyList()
        movedFiles = emptyList()
    }
}

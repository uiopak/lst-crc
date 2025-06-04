package com.github.uiopak.lstcrc.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.VirtualFile

@Service(Service.Level.PROJECT)
class ProjectActiveDiffDataService(private val project: Project) : Disposable {
    private val logger = thisLogger()

    var activeBranchName: String? = null
        private set
    var activeChanges: List<Change> = emptyList()
        private set
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
        logger.debug("updateActiveDiff called. Event branch: '$branchNameFromEvent' (${changesFromEvent.size} changes, ${createdFilesFromEvent.size} created, ${modifiedFilesFromEvent.size} modified, ${movedFilesFromEvent.size} moved). Current tool window branch: '$currentToolWindowBranch'.")

        if (branchNameFromEvent == currentToolWindowBranch) {
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) {
                    logger.info("Project ${project.name} is disposed (in updateActiveDiff invokeLater), skipping update.")
                    return@invokeLater
                }
                logger.debug("EDT: Updating active data for '$branchNameFromEvent'. Created: ${createdFilesFromEvent.size}, Modified: ${modifiedFilesFromEvent.size}, Moved: ${movedFilesFromEvent.size}")

                val oldCreatedFiles = this.createdFiles.toSet()
                val oldModifiedFiles = this.modifiedFiles.toSet()
                val oldMovedFiles = this.movedFiles.toSet()

                this.activeBranchName = branchNameFromEvent
                this.activeChanges = changesFromEvent
                this.createdFiles = createdFilesFromEvent
                this.modifiedFiles = modifiedFilesFromEvent
                this.movedFiles = movedFilesFromEvent

                val newCreatedFiles = createdFilesFromEvent.toSet()
                val newModifiedFiles = modifiedFilesFromEvent.toSet()
                val newMovedFiles = movedFilesFromEvent.toSet()

                // Combine all files whose status might have changed
                val affectedFiles = oldCreatedFiles + oldModifiedFiles + oldMovedFiles +
                        newCreatedFiles + newModifiedFiles + newMovedFiles

                if (affectedFiles.isNotEmpty()) {
                    val fileStatusManager = FileStatusManager.getInstance(project)
                    logger.debug("EDT: Notifying FileStatusManager for ${affectedFiles.size} affected files after updateActiveDiff.")

                    // Step 1: General notification to invalidate caches
                    fileStatusManager.fileStatusesChanged()
                    logger.debug("EDT: Called fileStatusesChanged() (plural).")

                    // Step 2: Specific notifications for each affected file
                    affectedFiles.forEach { file ->
                        if (file.isValid) {
                            logger.trace("EDT: Calling fileStatusChanged (singular) for: ${file.path}")
                            fileStatusManager.fileStatusChanged(file)
                        } else {
                            logger.trace("EDT: Skipping fileStatusChanged (singular) for invalid file: ${file.path}")
                        }
                    }
                }
                triggerEditorTabColorRefresh() // This method already handles its own invokeLater
            }
        } else {
            logger.debug("Event branch '$branchNameFromEvent' does NOT match current tool window branch '$currentToolWindowBranch'. Ignoring stale update.")
        }
    }

    fun clearActiveDiff() {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) {
                logger.info("Project ${project.name} is disposed (in clearActiveDiff invokeLater), skipping clear.")
                return@invokeLater
            }
            logger.debug("EDT: clearActiveDiff called. Clearing activeBranchName, activeChanges, and file lists.")
            val affectedFiles = (this.createdFiles + this.modifiedFiles + this.movedFiles).toSet()

            this.activeBranchName = null
            this.activeChanges = emptyList()
            this.createdFiles = emptyList()
            this.modifiedFiles = emptyList()
            this.movedFiles = emptyList()

            if (affectedFiles.isNotEmpty()) {
                val fileStatusManager = FileStatusManager.getInstance(project)
                logger.debug("EDT: Notifying FileStatusManager for ${affectedFiles.size} affected files after clearActiveDiff.")

                // Step 1: General notification
                fileStatusManager.fileStatusesChanged()
                logger.debug("EDT: Called fileStatusesChanged() (plural).")

                // Step 2: Specific notifications
                affectedFiles.forEach { file ->
                    if (file.isValid) {
                        logger.trace("EDT: Calling fileStatusChanged (singular) for: ${file.path}")
                        fileStatusManager.fileStatusChanged(file)
                    } else {
                        logger.trace("EDT: Skipping fileStatusChanged (singular) for invalid file: ${file.path}")
                    }
                }
            }
            triggerEditorTabColorRefresh() // This method already handles its own invokeLater
        }
    }

    private fun triggerEditorTabColorRefresh() {
        logger.debug("triggerEditorTabColorRefresh() called.")
        ApplicationManager.getApplication().invokeLater {
            logger.debug("invokeLater for triggerEditorTabColorRefresh running. Project disposed: ${project.isDisposed}")
            if (project.isDisposed) {
                logger.info("Project is disposed, skipping editor tab color refresh.")
                return@invokeLater
            }
            val fileEditorManager = FileEditorManager.getInstance(project)
            val openFiles = fileEditorManager.openFiles
            logger.debug("Found ${openFiles.size} open files to update for tab color refresh.")
            openFiles.forEach { vf ->
                if (vf.isValid) {
                    logger.trace("Requesting presentation update for file: ${vf.path}")
                    fileEditorManager.updateFilePresentation(vf)
                } else {
                    logger.debug("File ${vf.path} is invalid, skipping presentation update.")
                }
            }
            logger.debug("updateFilePresentation requests sent for all valid open files.")
        }
    }

    fun refreshCurrentColorings() {
        logger.debug("refreshCurrentColorings() called. Active branch: $activeBranchName, Changes count: ${activeChanges.size}")
        // This method primarily triggers tab color refresh.
        // If we want it to also attempt to refresh project tree colors,
        // we might need to re-notify FileStatusManager for all currently known changed files.
        // For now, let's assume normal updates to ProjectActiveDiffDataService handle this.
        triggerEditorTabColorRefresh()

        // Forcing a broader refresh for project tree (use with caution, can be expensive):
        // ApplicationManager.getApplication().invokeLater {
        //     if (project.isDisposed) return@invokeLater
        //     val currentAffectedFiles = (this.createdFiles + this.modifiedFiles + this.movedFiles).toSet()
        //     if (currentAffectedFiles.isNotEmpty()) {
        //         val fileStatusManager = FileStatusManager.getInstance(project)
        //         fileStatusManager.fileStatusesChanged()
        //         currentAffectedFiles.forEach { file ->
        //             if (file.isValid) fileStatusManager.fileStatusChanged(file)
        //         }
        //         logger.debug("Forced FileStatusManager notifications for ${currentAffectedFiles.size} files in refreshCurrentColorings.")
        //     }
        // }
    }

    override fun dispose() {
        logger.info("Disposing ProjectActiveDiffDataService for project ${project.name}, clearing changes and file lists.")
        // No need for invokeLater here as it's a cleanup.
        activeChanges = emptyList()
        activeBranchName = null
        createdFiles = emptyList()
        modifiedFiles = emptyList()
        movedFiles = emptyList()
    }
}
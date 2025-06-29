package com.github.uiopak.lstcrc.services

import com.github.uiopak.lstcrc.messaging.DIFF_DATA_CHANGED_TOPIC
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import java.util.*

/**
 * Caches the diff data ([CategorizedChanges]) for the currently selected branch comparison.
 * This service acts as the single source of truth for diff data for other components like
 * file scopes and gutter markers. When its data is updated or cleared, it broadcasts a
 * [DIFF_DATA_CHANGED_TOPIC] message and triggers UI refreshes.
 */
@Service(Service.Level.PROJECT)
class ProjectActiveDiffDataService(private val project: Project) : Disposable {
    private val logger = thisLogger()

    /** A unique ID for the current diff session, used to robustly trigger tracker updates. */
    var diffSessionId: UUID = UUID.randomUUID()
        private set

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
    var activeComparisonContext: Map<String, String> = emptyMap()
        private set

    fun updateActiveDiff(
        branchNameFromEvent: String,
        changesFromEvent: List<Change>,
        createdFilesFromEvent: List<VirtualFile>,
        modifiedFilesFromEvent: List<VirtualFile>,
        movedFilesFromEvent: List<VirtualFile>,
        comparisonContextFromEvent: Map<String, String>
    ) {
        val currentToolWindowBranch = project.service<ToolWindowStateService>().getSelectedTabBranchName()
        logger.debug("updateActiveDiff called. Event branch: '$branchNameFromEvent'. Current tool window branch: '$currentToolWindowBranch'.")

        // The event is valid only if it matches the currently selected tab to prevent stale updates.
        val isHeadSelectedInToolWindow = currentToolWindowBranch == null
        val isEventForHead = branchNameFromEvent == "HEAD"
        val isDirectBranchMatch = branchNameFromEvent == currentToolWindowBranch

        if (isDirectBranchMatch || (isEventForHead && isHeadSelectedInToolWindow)) {
            logger.debug("updateActiveDiff - Update ACCEPTED. Proceeding to update service state.")
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) {
                    logger.info("Project ${project.name} is disposed (in updateActiveDiff invokeLater), skipping update.")
                    return@invokeLater
                }
                logger.debug("EDT: Updating active data for '$branchNameFromEvent'.")

                val oldCreatedFiles = this.createdFiles.toSet()
                val oldModifiedFiles = this.modifiedFiles.toSet()
                val oldMovedFiles = this.movedFiles.toSet()

                this.activeBranchName = branchNameFromEvent
                this.activeChanges = changesFromEvent
                this.createdFiles = createdFilesFromEvent
                this.modifiedFiles = modifiedFilesFromEvent
                this.movedFiles = movedFilesFromEvent
                this.activeComparisonContext = comparisonContextFromEvent
                this.diffSessionId = UUID.randomUUID() // Generate new session ID to force tracker updates

                val newCreatedFiles = createdFilesFromEvent.toSet()
                val newModifiedFiles = modifiedFilesFromEvent.toSet()
                val newMovedFiles = movedFilesFromEvent.toSet()

                // Combine all files whose status might have changed to notify the IDE.
                val affectedFiles = oldCreatedFiles + oldModifiedFiles + oldMovedFiles +
                        newCreatedFiles + newModifiedFiles + newMovedFiles

                if (affectedFiles.isNotEmpty()) {
                    val fileStatusManager = FileStatusManager.getInstance(project)
                    logger.debug("EDT: Notifying FileStatusManager for ${affectedFiles.size} affected files after updateActiveDiff.")

                    fileStatusManager.fileStatusesChanged()
                    affectedFiles.forEach { file ->
                        if (file.isValid) {
                            fileStatusManager.fileStatusChanged(file)
                        }
                    }
                }

                // Announce that the data is ready for consumers (scopes, gutters, etc.).
                project.messageBus.syncPublisher(DIFF_DATA_CHANGED_TOPIC).onDiffDataChanged()
                triggerEditorTabColorRefresh()
            }
        } else {
            logger.debug("updateActiveDiff - Update REJECTED as stale. Event branch '$branchNameFromEvent' does NOT match current tool window branch '$currentToolWindowBranch'.")
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
            this.activeComparisonContext = emptyMap()
            this.diffSessionId = UUID.randomUUID() // Generate new session ID to force tracker updates

            if (affectedFiles.isNotEmpty()) {
                val fileStatusManager = FileStatusManager.getInstance(project)
                logger.debug("EDT: Notifying FileStatusManager for ${affectedFiles.size} affected files after clearActiveDiff.")
                fileStatusManager.fileStatusesChanged()
                affectedFiles.forEach { file ->
                    if (file.isValid) {
                        fileStatusManager.fileStatusChanged(file)
                    }
                }
            }
            project.messageBus.syncPublisher(DIFF_DATA_CHANGED_TOPIC).onDiffDataChanged()
            triggerEditorTabColorRefresh()
        }
    }

    private fun triggerEditorTabColorRefresh() {
        logger.debug("triggerEditorTabColorRefresh() called.")
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) {
                logger.info("Project is disposed, skipping editor tab color refresh.")
                return@invokeLater
            }
            val fileEditorManager = FileEditorManager.getInstance(project)
            fileEditorManager.openFiles.forEach { vf ->
                if (vf.isValid) {
                    fileEditorManager.updateFilePresentation(vf)
                }
            }
            logger.debug("updateFilePresentation requests sent for all valid open files.")
        }
    }

    fun refreshCurrentColorings() {
        logger.debug("refreshCurrentColorings() called. Active branch: $activeBranchName, Changes count: ${activeChanges.size}")
        triggerEditorTabColorRefresh()
    }

    override fun dispose() {
        logger.info("Disposing ProjectActiveDiffDataService for project ${project.name}, clearing data.")
        activeChanges = emptyList()
        activeBranchName = null
        createdFiles = emptyList()
        modifiedFiles = emptyList()
        movedFiles = emptyList()
        activeComparisonContext = emptyMap()
    }
}
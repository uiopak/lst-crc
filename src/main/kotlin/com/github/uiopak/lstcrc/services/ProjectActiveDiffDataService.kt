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
    var createdFiles: List<VirtualFile> = emptyList()
        private set
    var modifiedFiles: List<VirtualFile> = emptyList()
        private set
    var movedFiles: List<VirtualFile> = emptyList()
        private set
    var deletedFiles: List<VirtualFile> = emptyList()
        private set
    var activeComparisonContext: Map<String, String> = emptyMap()
        private set

    fun updateActiveDiff(
        branchNameFromEvent: String,
        createdFilesFromEvent: List<VirtualFile>,
        modifiedFilesFromEvent: List<VirtualFile>,
        movedFilesFromEvent: List<VirtualFile>,
        deletedFilesFromEvent: List<VirtualFile>,
        comparisonContextFromEvent: Map<String, String>
    ) {
        val currentToolWindowBranch = project.service<ToolWindowStateService>().getSelectedTabBranchName()
        logger.debug("updateActiveDiff called. Event branch: '$branchNameFromEvent'. Current tool window branch: '$currentToolWindowBranch'.")

        val isHeadSelectedInToolWindow = currentToolWindowBranch == null
        val isEventForHead = branchNameFromEvent == "HEAD"
        val isDirectBranchMatch = branchNameFromEvent == currentToolWindowBranch

        if (isDirectBranchMatch || (isEventForHead && isHeadSelectedInToolWindow)) {
            logger.debug("updateActiveDiff - Update ACCEPTED. Proceeding to update service state.")
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                logger.debug("EDT: Updating active data for '$branchNameFromEvent'.")

                val previousFiles = collectAllFiles()

                this.activeBranchName = branchNameFromEvent
                this.createdFiles = createdFilesFromEvent
                this.modifiedFiles = modifiedFilesFromEvent
                this.movedFiles = movedFilesFromEvent
                this.deletedFiles = deletedFilesFromEvent
                this.activeComparisonContext = comparisonContextFromEvent
                this.diffSessionId = UUID.randomUUID()

                notifyAffectedFiles(previousFiles + collectAllFiles())
                project.messageBus.syncPublisher(DIFF_DATA_CHANGED_TOPIC).onDiffDataChanged()
                triggerEditorTabColorRefresh()
            }
        } else {
            logger.debug("updateActiveDiff - Update REJECTED as stale. Event branch '$branchNameFromEvent' does NOT match current tool window branch '$currentToolWindowBranch'.")
        }
    }

    fun clearActiveDiff() {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            logger.debug("EDT: clearActiveDiff called. Clearing activeBranchName and file lists.")

            val previousFiles = collectAllFiles()

            this.activeBranchName = null
            this.createdFiles = emptyList()
            this.modifiedFiles = emptyList()
            this.movedFiles = emptyList()
            this.deletedFiles = emptyList()
            this.activeComparisonContext = emptyMap()
            this.diffSessionId = UUID.randomUUID()

            notifyAffectedFiles(previousFiles)
            project.messageBus.syncPublisher(DIFF_DATA_CHANGED_TOPIC).onDiffDataChanged()
            triggerEditorTabColorRefresh()
        }
    }

    private fun collectAllFiles(): Set<VirtualFile> =
        (createdFiles + modifiedFiles + movedFiles + deletedFiles).toSet()

    private fun notifyAffectedFiles(affectedFiles: Set<VirtualFile>) {
        if (affectedFiles.isEmpty()) return
        val fileStatusManager = FileStatusManager.getInstance(project)
        logger.debug("EDT: Notifying FileStatusManager for ${affectedFiles.size} affected files.")
        fileStatusManager.fileStatusesChanged()
        affectedFiles.forEach { file ->
            if (file.isValid) {
                fileStatusManager.fileStatusChanged(file)
            }
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
        logger.debug("refreshCurrentColorings() called. Active branch: $activeBranchName")
        triggerEditorTabColorRefresh()
    }

    override fun dispose() {
        logger.info("Disposing ProjectActiveDiffDataService for project ${project.name}, clearing data.")
        activeBranchName = null
        createdFiles = emptyList()
        modifiedFiles = emptyList()
        movedFiles = emptyList()
        deletedFiles = emptyList()
        activeComparisonContext = emptyMap()
    }
}
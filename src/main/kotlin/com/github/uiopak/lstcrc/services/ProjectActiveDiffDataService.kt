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

    private data class ActiveDiffSnapshot(
        val diffSessionId: UUID,
        val activeBranchName: String?,
        val createdFiles: List<VirtualFile>,
        val modifiedFiles: List<VirtualFile>,
        val movedFiles: List<VirtualFile>,
        val deletedFiles: List<VirtualFile>,
        val activeComparisonContext: Map<String, String>,
        val createdFilesSet: Set<VirtualFile>,
        val createdFilePaths: Set<String>,
        val modifiedFilesSet: Set<VirtualFile>,
        val modifiedFilePaths: Set<String>,
        val movedFilesSet: Set<VirtualFile>,
        val movedFilePaths: Set<String>,
        val deletedFilePaths: Set<String>,
        val changedFilesSet: Set<VirtualFile>,
        val changedFilePaths: Set<String>
    ) {
        val allFiles: Set<VirtualFile>
            get() = createdFilesSet + modifiedFilesSet + movedFilesSet + deletedFiles.toSet()

        companion object {
            fun from(
                activeBranchName: String?,
                createdFiles: List<VirtualFile>,
                modifiedFiles: List<VirtualFile>,
                movedFiles: List<VirtualFile>,
                deletedFiles: List<VirtualFile>,
                activeComparisonContext: Map<String, String>
            ): ActiveDiffSnapshot {
                val createdSet = createdFiles.toSet()
                val modifiedSet = modifiedFiles.toSet()
                val movedSet = movedFiles.toSet()
                val createdPaths = createdFiles.mapTo(HashSet()) { it.path }
                val modifiedPaths = modifiedFiles.mapTo(HashSet()) { it.path }
                val movedPaths = movedFiles.mapTo(HashSet()) { it.path }
                val deletedPaths = deletedFiles.mapTo(HashSet()) { it.path }
                return ActiveDiffSnapshot(
                    diffSessionId = UUID.randomUUID(),
                    activeBranchName = activeBranchName,
                    createdFiles = createdFiles,
                    modifiedFiles = modifiedFiles,
                    movedFiles = movedFiles,
                    deletedFiles = deletedFiles,
                    activeComparisonContext = activeComparisonContext,
                    createdFilesSet = createdSet,
                    createdFilePaths = createdPaths,
                    modifiedFilesSet = modifiedSet,
                    modifiedFilePaths = modifiedPaths,
                    movedFilesSet = movedSet,
                    movedFilePaths = movedPaths,
                    deletedFilePaths = deletedPaths,
                    changedFilesSet = createdSet + modifiedSet + movedSet,
                    changedFilePaths = createdPaths + modifiedPaths + movedPaths
                )
            }

            fun empty(): ActiveDiffSnapshot = from(
                activeBranchName = null,
                createdFiles = emptyList(),
                modifiedFiles = emptyList(),
                movedFiles = emptyList(),
                deletedFiles = emptyList(),
                activeComparisonContext = emptyMap()
            )
        }
    }

    private var snapshot: ActiveDiffSnapshot = ActiveDiffSnapshot.empty()

    val diffSessionId: UUID
        get() = snapshot.diffSessionId
    val activeBranchName: String?
        get() = snapshot.activeBranchName
    val createdFiles: List<VirtualFile>
        get() = snapshot.createdFiles
    val modifiedFiles: List<VirtualFile>
        get() = snapshot.modifiedFiles
    val movedFiles: List<VirtualFile>
        get() = snapshot.movedFiles
    val deletedFiles: List<VirtualFile>
        get() = snapshot.deletedFiles
    val activeComparisonContext: Map<String, String>
        get() = snapshot.activeComparisonContext
    val createdFilesSet: Set<VirtualFile>
        get() = snapshot.createdFilesSet
    val createdFilePaths: Set<String>
        get() = snapshot.createdFilePaths
    val modifiedFilesSet: Set<VirtualFile>
        get() = snapshot.modifiedFilesSet
    val modifiedFilePaths: Set<String>
        get() = snapshot.modifiedFilePaths
    val movedFilesSet: Set<VirtualFile>
        get() = snapshot.movedFilesSet
    val movedFilePaths: Set<String>
        get() = snapshot.movedFilePaths
    val deletedFilePaths: Set<String>
        get() = snapshot.deletedFilePaths
    val changedFilesSet: Set<VirtualFile>
        get() = snapshot.changedFilesSet
    val changedFilePaths: Set<String>
        get() = snapshot.changedFilePaths

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

                val previousFiles = snapshot.allFiles
                snapshot = ActiveDiffSnapshot.from(
                    activeBranchName = branchNameFromEvent,
                    createdFiles = createdFilesFromEvent,
                    modifiedFiles = modifiedFilesFromEvent,
                    movedFiles = movedFilesFromEvent,
                    deletedFiles = deletedFilesFromEvent,
                    activeComparisonContext = comparisonContextFromEvent
                )

                notifyAffectedFiles(previousFiles + snapshot.allFiles)
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

            val previousFiles = snapshot.allFiles
            snapshot = ActiveDiffSnapshot.empty()

            notifyAffectedFiles(previousFiles)
            project.messageBus.syncPublisher(DIFF_DATA_CHANGED_TOPIC).onDiffDataChanged()
            triggerEditorTabColorRefresh()
        }
    }

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

    /** Must be called on EDT. */
    private fun triggerEditorTabColorRefresh() {
        if (project.isDisposed) return
        logger.debug("triggerEditorTabColorRefresh() called.")
        val fileEditorManager = FileEditorManager.getInstance(project)
        fileEditorManager.openFiles.forEach { vf ->
            if (vf.isValid) {
                fileEditorManager.updateFilePresentation(vf)
            }
        }
        logger.debug("updateFilePresentation requests sent for all valid open files.")
    }

    fun refreshCurrentColorings() {
        logger.debug("refreshCurrentColorings() called. Active branch: $activeBranchName")
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) triggerEditorTabColorRefresh()
        }
    }

    override fun dispose() {
        logger.info("Disposing ProjectActiveDiffDataService for project ${project.name}, clearing data.")
        snapshot = ActiveDiffSnapshot.empty()
    }
}
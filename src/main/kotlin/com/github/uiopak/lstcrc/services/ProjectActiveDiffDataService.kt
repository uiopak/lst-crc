package com.github.uiopak.lstcrc.services

import com.github.uiopak.lstcrc.messaging.DIFF_DATA_CHANGED_TOPIC
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.VirtualFile

/**
 * Caches the diff data ([CategorizedChanges]) for the currently selected branch comparison.
 * This service acts as the single source of truth for diff data for other components like
 * file scopes and gutter markers. When its data is updated or cleared, it broadcasts a
 * [DIFF_DATA_CHANGED_TOPIC] message and triggers UI refreshes.
 */
@Service(Service.Level.PROJECT)
class ProjectActiveDiffDataService(private val project: Project) : Disposable {
    private val logger = thisLogger()

    /**
     * The active comparison plus path sets derived from it. Scopes and gutters query these on every
     * file-status/colour lookup, so they are computed once per update. Paths are used rather than
     * [VirtualFile]s because deleted and revision-backed files have no stable local instance.
     */
    private data class ActiveDiffSnapshot(
        val activeBranchName: String?,
        val categorizedChanges: CategorizedChanges
    ) {
        val createdFilePaths: Set<String> = categorizedChanges.createdFiles.pathSet()
        val modifiedFilePaths: Set<String> = categorizedChanges.modifiedFiles.pathSet()
        val movedFilePaths: Set<String> = categorizedChanges.movedFiles.pathSet()
        val deletedFilePaths: Set<String> = categorizedChanges.deletedFiles.pathSet()
        val changedFilePaths: Set<String> = createdFilePaths + modifiedFilePaths + movedFilePaths

        /** True when replacing this snapshot can change some file's status (it lists at least one file). */
        fun hasFiles(): Boolean = with(categorizedChanges) {
            createdFiles.isNotEmpty() || modifiedFiles.isNotEmpty() || movedFiles.isNotEmpty() || deletedFiles.isNotEmpty()
        }

        private fun List<VirtualFile>.pathSet(): Set<String> = mapTo(HashSet(size)) { it.path }

        companion object {
            val EMPTY = ActiveDiffSnapshot(
                activeBranchName = null,
                categorizedChanges = CategorizedChanges(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyMap(), emptyMap())
            )
        }
    }

    @Volatile
    private var snapshot: ActiveDiffSnapshot = ActiveDiffSnapshot.EMPTY

    val activeBranchName: String?
        get() = snapshot.activeBranchName
    val categorizedChanges: CategorizedChanges?
        get() = snapshot.categorizedChanges.takeIf { it.allChanges.isNotEmpty() || snapshot.activeBranchName != null }

    // The file lists are read by UI tests through Remote Robot.
    val createdFiles: List<VirtualFile>
        get() = snapshot.categorizedChanges.createdFiles
    @get:Suppress("unused")
    val modifiedFiles: List<VirtualFile>
        get() = snapshot.categorizedChanges.modifiedFiles
    @get:Suppress("unused")
    val movedFiles: List<VirtualFile>
        get() = snapshot.categorizedChanges.movedFiles
    @get:Suppress("unused")
    val deletedFiles: List<VirtualFile>
        get() = snapshot.categorizedChanges.deletedFiles
    val activeComparisonContext: Map<String, String>
        get() = snapshot.categorizedChanges.comparisonContext
    val lineStatsByChange: Map<ChangeLineStatsKey, ChangeLineStats>
        get() = snapshot.categorizedChanges.lineStatsByChange
    val createdFilePaths: Set<String>
        get() = snapshot.createdFilePaths
    val modifiedFilePaths: Set<String>
        get() = snapshot.modifiedFilePaths
    val movedFilePaths: Set<String>
        get() = snapshot.movedFilePaths
    val deletedFilePaths: Set<String>
        get() = snapshot.deletedFilePaths
    val changedFilePaths: Set<String>
        get() = snapshot.changedFilePaths

    fun updateActiveDiff(
        branchNameFromEvent: String,
        categorizedChanges: CategorizedChanges
    ) {
        // A null selection is the HEAD tab, whose loads are reported as "HEAD".
        val currentToolWindowBranch = project.service<ToolWindowStateService>().getSelectedTabBranchName() ?: "HEAD"
        if (branchNameFromEvent != currentToolWindowBranch) {
            logger.debug { "updateActiveDiff - Update REJECTED as stale. Event branch '$branchNameFromEvent' does NOT match current tool window branch '$currentToolWindowBranch'." }
            return
        }

        onEdt {
            // Most edit-only refreshes return the same data; compare before building the path sets.
            val current = snapshot
            if (current.activeBranchName != branchNameFromEvent || current.categorizedChanges != categorizedChanges) {
                replaceSnapshot(ActiveDiffSnapshot(branchNameFromEvent, categorizedChanges))
            }
        }
    }

    fun clearActiveDiff() {
        onEdt { replaceSnapshot(ActiveDiffSnapshot.EMPTY) }
    }

    /**
     * Runs [action] now when called on the EDT (the normal case: refreshes apply their result there),
     * otherwise on the next EDT turn. Skipped once the project is disposed.
     */
    private fun onEdt(action: () -> Unit) {
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) {
            if (!project.isDisposed) action()
        } else {
            application.invokeLater { if (!project.isDisposed) action() }
        }
    }

    /**
     * Must be called on EDT. Publishes the new data and refreshes file statuses (only when some file's
     * status can change; `fileStatusesChanged()` invalidates every cached status) and editor tab colours.
     */
    private fun replaceSnapshot(newSnapshot: ActiveDiffSnapshot) {
        val anyFileAffected = snapshot.hasFiles() || newSnapshot.hasFiles()
        snapshot = newSnapshot
        if (anyFileAffected) FileStatusManager.getInstance(project).fileStatusesChanged()
        project.messageBus.syncPublisher(DIFF_DATA_CHANGED_TOPIC).onDiffDataChanged()
        triggerEditorTabColorRefresh()
    }

    /** Must be called on EDT. */
    private fun triggerEditorTabColorRefresh() {
        if (project.isDisposed) return
        logger.debug { "triggerEditorTabColorRefresh() called." }
        val fileEditorManager = FileEditorManager.getInstance(project)
        fileEditorManager.openFiles.forEach { vf ->
            if (vf.isValid) {
                fileEditorManager.updateFilePresentation(vf)
            }
        }
        logger.debug { "updateFilePresentation requests sent for all valid open files." }
    }

    fun refreshCurrentColorings() {
        logger.debug { "refreshCurrentColorings() called. Active branch: $activeBranchName" }
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) triggerEditorTabColorRefresh()
        }
    }

    override fun dispose() {
        logger.debug { "Disposing ProjectActiveDiffDataService for project ${project.name}, clearing data." }
        snapshot = ActiveDiffSnapshot.EMPTY
    }
}
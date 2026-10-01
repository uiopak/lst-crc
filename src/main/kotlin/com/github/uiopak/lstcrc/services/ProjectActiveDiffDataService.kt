package com.github.uiopak.lstcrc.services

import com.github.uiopak.lstcrc.LstCrcConstants.HEAD
import com.github.uiopak.lstcrc.messaging.DIFF_DATA_CHANGED_TOPIC
import com.github.uiopak.lstcrc.toolWindow.ToolWindowSettingsProvider
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
        /** The old path of each moved file, by its new path: the path its content has in the target. */
        val movedSourcePaths: Map<String, String> =
            movedSourcePaths(categorizedChanges.allChanges).mapValues { (_, before) -> before.path }

        /**
         * True when replacing this snapshot with [other] can change a file's status or colour: they come from the
         * scopes, which depend on path sets and whether HEAD participates. Target names and unsaved text do not
         * affect membership when these stay the same.
         */
        fun scopesDifferFrom(other: ActiveDiffSnapshot): Boolean {
            val includeHead = ToolWindowSettingsProvider.isIncludeHeadInScopes()
            val active = activeBranchName != null && (activeBranchName != HEAD || includeHead)
            val otherActive = other.activeBranchName != null && (other.activeBranchName != HEAD || includeHead)
            if (active != otherActive) {
                val included = if (active) this else other
                return included.changedFilePaths.isNotEmpty() || included.deletedFilePaths.isNotEmpty()
            }
            return active && (createdFilePaths != other.createdFilePaths ||
                modifiedFilePaths != other.modifiedFilePaths ||
                movedFilePaths != other.movedFilePaths ||
                deletedFilePaths != other.deletedFilePaths)
        }

        private fun List<VirtualFile>.pathSet(): Set<String> = mapTo(HashSet(size)) { it.path }

        companion object {
            val EMPTY = ActiveDiffSnapshot(activeBranchName = null, categorizedChanges = CategorizedChanges.EMPTY)
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

    /** The path [path] had in the comparison target: its old path when it was moved, otherwise [path]. */
    fun pathInTarget(path: String): String = snapshot.movedSourcePaths[path] ?: path

    fun updateActiveDiff(
        branchNameFromEvent: String,
        categorizedChanges: CategorizedChanges
    ) {
        onEdt {
            // Checked where the result is applied: the selection may change before a later EDT turn.
            // A null selection is the HEAD tab, whose loads are reported as "HEAD".
            val selectedTab = project.service<ToolWindowStateService>().getSelectedTabInfo()
            val currentToolWindowBranch = selectedTab?.branchName ?: HEAD
            if (branchNameFromEvent != currentToolWindowBranch) {
                logger.debug { "updateActiveDiff - Update REJECTED as stale. Event branch '$branchNameFromEvent' does NOT match current tool window branch '$currentToolWindowBranch'." }
                return@onEdt
            }
            if (categorizedChanges.comparisonContext.any { (root, target) ->
                    target != (selectedTab?.comparisonMap?.get(root) ?: currentToolWindowBranch)
                }) {
                logger.debug { "updateActiveDiff - Rejected stale repository targets: ${categorizedChanges.comparisonContext}" }
                return@onEdt
            }
            // Most edit-only refreshes return the same data; compare before building the path sets.
            val current = snapshot
            if (current.activeBranchName != branchNameFromEvent || !current.categorizedChanges.sameAs(categorizedChanges)) {
                replaceSnapshot(ActiveDiffSnapshot(branchNameFromEvent, categorizedChanges))
            }
        }
    }

    /**
     * `Change.equals` only compares paths, so this also compares revisions and statuses: an unsaved edit
     * keeps its paths but carries new content, and the tree must show (and diff) the new `Change`.
     */
    private fun CategorizedChanges.sameAs(other: CategorizedChanges): Boolean {
        if (this === other) return true
        return this == other && allChanges.indices.all { i ->
            val change = allChanges[i]
            val otherChange = other.allChanges[i]
            change.beforeRevision == otherChange.beforeRevision &&
                change.afterRevision == otherChange.afterRevision &&
                change.fileStatus == otherChange.fileStatus
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
     * Must be called on EDT. Publishes the new data and, when the scopes changed, refreshes file statuses
     * (`fileStatusesChanged()` invalidates every cached status in the project) and editor tab colours.
     */
    private fun replaceSnapshot(newSnapshot: ActiveDiffSnapshot) {
        val scopesChanged = newSnapshot.scopesDifferFrom(snapshot)
        snapshot = newSnapshot
        if (scopesChanged) FileStatusManager.getInstance(project).fileStatusesChanged()
        project.messageBus.syncPublisher(DIFF_DATA_CHANGED_TOPIC).onDiffDataChanged()
        if (scopesChanged) triggerEditorTabColorRefresh()
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

package com.github.uiopak.lstcrc.listeners

import com.github.uiopak.lstcrc.services.GitService
import com.github.uiopak.lstcrc.services.ProjectActiveDiffDataService
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import javax.swing.Timer // Make sure this is javax.swing.Timer

class TabColorVfsListener(private val project: Project) : BulkFileListener {
    private val logger = thisLogger()
    private var debounceTimer: Timer? = null
    private val DEBOUNCE_DELAY_MS = 750 // milliseconds

    override fun after(events: List<VFileEvent>) {
        if (project.isDisposed) return

        val relevantEvent = events.any { event ->
            // Only consider events within the project boundaries and of relevant types
            val file = event.file
            // Ensure project.basePath is not null before using it in startsWith
            val projectBasePath = project.basePath
            file != null && projectBasePath != null && file.path.startsWith(projectBasePath) &&
            (event is VFileContentChangeEvent || event is VFileCreateEvent || event is VFileDeleteEvent || event is VFileMoveEvent || event is VFileCopyEvent)
        }

        if (relevantEvent) {
            logger.info("Relevant VFS event detected. Debouncing refresh of active diff for tab colors.")
            debounceTimer?.stop()
            debounceTimer = Timer(DEBOUNCE_DELAY_MS) {
                performRefresh()
            }
            debounceTimer?.isRepeats = false
            debounceTimer?.start()
        }
    }

    private fun performRefresh() {
        if (project.isDisposed) {
            logger.info("Project disposed, skipping debounced VFS refresh.")
            return
        }
        logger.info("Debounce timer fired. Performing refresh of active diff for tab colors.")

        val toolWindowStateService = project.service<ToolWindowStateService>()
        val activeBranchName = toolWindowStateService.getSelectedTabBranchName()

        if (activeBranchName != null) {
            val gitService = project.service<GitService>()
            val diffDataService = project.service<ProjectActiveDiffDataService>()

            logger.info("VFS Change: Refreshing changes for active tool window branch: $activeBranchName")
            gitService.getChanges(activeBranchName).whenCompleteAsync { changes, throwable ->
                if (project.isDisposed) { // Check project disposed state again inside async block
                    logger.info("Project disposed during async git operation, skipping update.")
                    return@whenCompleteAsync
                }
                if (throwable != null) {
                    logger.error("VFS Change: Error getting changes for branch $activeBranchName: ${throwable.message}", throwable)
                    // Optionally clear or update service state to reflect error
                    // For example, if current branch is still activeBranchName, clear its data:
                    // val stillActiveBranchForError = project.service<ToolWindowStateService>().getSelectedTabBranchName()
                    // if (stillActiveBranchForError == activeBranchName) {
                    //    diffDataService.clearActiveDiff() // Or a method to clear for a specific branch if that's desired
                    // }
                } else if (changes != null) {
                    // Check if the branch is still the active one, in case it changed during async op
                    val stillActiveBranch = project.service<ToolWindowStateService>().getSelectedTabBranchName()
                    if (stillActiveBranch == activeBranchName) {
                        logger.info("VFS Change: Successfully fetched ${changes.size} changes for $activeBranchName. Updating ProjectActiveDiffDataService.")
                        diffDataService.updateActiveDiff(activeBranchName, changes)
                    } else {
                        logger.info("VFS Change: Branch changed from $activeBranchName to $stillActiveBranch during async git operation. Not updating diff data for $activeBranchName.")
                    }
                } else {
                     logger.warn("VFS Change: Fetched changes for $activeBranchName but the list was null.")
                     // Similar to error case, consider clearing if this branch is still active
                     // val stillActiveBranchForNull = project.service<ToolWindowStateService>().getSelectedTabBranchName()
                     // if (stillActiveBranchForNull == activeBranchName) {
                     //    diffDataService.clearActiveDiff()
                     // }
                }
            }
        } else {
            logger.info("VFS Change: No active tool window branch selected. Not refreshing diff data.")
        }
    }
}

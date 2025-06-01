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
        if (project.isDisposed) {
            // logger.info("VFS_LISTENER: Project is disposed in after(). Skipping.") // Optional: can be noisy
            return
        }

        logger.info("VFS_LISTENER: after() called with ${events.size} VFS events.")
        var eventDetails = events.joinToString { "Path: ${it.path}, Type: ${it.javaClass.simpleName}, IsDirectory: ${it.file?.isDirectory}" }
        if (eventDetails.length > 500) eventDetails = eventDetails.substring(0, 500) + "..." // Truncate long event lists
        logger.info("VFS_LISTENER: Event details (first 500 chars): $eventDetails")


        val relevantEventFound = events.any { event ->
            val file = event.file
            val projectBasePath = project.basePath // Get basePath once
            val isRelevantType = event is VFileContentChangeEvent || event is VFileCreateEvent || event is VFileDeleteEvent || event is VFileMoveEvent || event is VFileCopyEvent
            val isInProject = file != null && projectBasePath != null && file.path.startsWith(projectBasePath)

            if (isRelevantType && isInProject) {
                logger.info("VFS_LISTENER: Found relevant event: Path: ${file?.path}, Type: ${event.javaClass.simpleName}")
                return@any true
            }
            return@any false
        }

        if (relevantEventFound) {
            logger.info("VFS_LISTENER: Relevant VFS event(s) detected. Debouncing refresh action (delay: ${DEBOUNCE_DELAY_MS}ms).")
            debounceTimer?.stop()
            debounceTimer = Timer(DEBOUNCE_DELAY_MS) {
                logger.info("VFS_LISTENER: Debounce Timer Fired. Calling performRefresh().")
                performRefresh()
            }
            debounceTimer?.isRepeats = false
            debounceTimer?.start()
        } else {
            logger.info("VFS_LISTENER: No relevant VFS events detected in this batch for project content.")
        }
    }

    private fun performRefresh() {
        if (project.isDisposed) {
            logger.info("VFS_LISTENER_REFRESH: Project disposed, skipping debounced VFS refresh.")
            return
        }
        logger.info("VFS_LISTENER_REFRESH: performRefresh() called.")

        val toolWindowStateService = project.service<ToolWindowStateService>()
        val activeBranchName = toolWindowStateService.getSelectedTabBranchName()
        logger.info("VFS_LISTENER_REFRESH: Current active tool window branch: '$activeBranchName'.")

        if (activeBranchName != null) {
            val gitService = project.service<GitService>()
            val diffDataService = project.service<ProjectActiveDiffDataService>()

            logger.info("VFS_LISTENER_REFRESH: Fetching changes for branch '$activeBranchName'.")
            gitService.getChanges(activeBranchName).whenCompleteAsync { changes, throwable ->
                if (project.isDisposed) {
                    logger.info("VFS_LISTENER_REFRESH: Project disposed during getChanges for '$activeBranchName'. Aborting update.")
                    return@whenCompleteAsync
                }
                logger.info("VFS_LISTENER_REFRESH: getChanges for '$activeBranchName' completed. Error: ${throwable != null}, Changes count: ${changes?.size ?: "null"}")

                if (throwable != null) {
                    logger.error("VFS_LISTENER_REFRESH: Error getting changes for branch '$activeBranchName': ${throwable.message}", throwable)
                } else if (changes != null) {
                    logger.info("VFS_LISTENER_REFRESH: Successfully fetched ${changes.size} changes for '$activeBranchName'. Calling diffDataService.updateActiveDiff.")
                    diffDataService.updateActiveDiff(activeBranchName, changes)
                } else {
                    logger.warn("VFS_LISTENER_REFRESH: Fetched changes for '$activeBranchName' but the list was null.")
                }
            }
        } else {
            logger.info("VFS_LISTENER_REFRESH: No active tool window branch selected. Not refreshing diff data.")
        }
        logger.info("VFS_LISTENER_REFRESH: performRefresh() COMPLETED.")
    }
}

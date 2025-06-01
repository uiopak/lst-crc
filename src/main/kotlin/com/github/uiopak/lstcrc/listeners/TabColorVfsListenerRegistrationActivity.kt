package com.github.uiopak.lstcrc.listeners

// Keep other necessary imports for the class structure, like Project, ProjectActivity, thisLogger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
// Remove other imports like ApplicationManager, service, VFS listeners, kotlinx.coroutines.delay, DumbService for now if they are not used by the single log line.
// For example, these are no longer directly needed by the simplified execute method:
// import com.github.uiopak.lstcrc.services.ProjectActiveDiffDataService
// import com.intellij.openapi.application.ApplicationManager
// import com.intellij.openapi.components.service
// import com.intellij.openapi.vfs.VirtualFileManager
// import com.intellij.openapi.Disposable
// import com.github.uiopak.lstcrc.services.GitService
// import com.github.uiopak.lstcrc.services.ToolWindowStateService
// import com.intellij.openapi.project.DumbService


class TabColorVfsListenerRegistrationActivity : ProjectActivity {
    private val logger = thisLogger() // Ensure logger is initialized
    // private val INITIAL_DIFF_LOAD_DELAY_MS = 5000L // Commented out as part of simplification

    override suspend fun execute(project: Project) {
        logger.info("YYYY_TEST_LOG STARTUP: TabColorVfsListenerRegistrationActivity.execute for project: ${project.name} - SIMPLIFIED TEST")

        // All other logic previously in this method should be commented out for this test:
        // logger.info("STARTUP: TabColorVfsListenerRegistrationActivity.execute for project: ${project.name}")
        // val disposable = project as Disposable
        // logger.info("STARTUP: Connecting to message bus for VFS_CHANGES.")
        // project.messageBus.connect(disposable).subscribe(VirtualFileManager.VFS_CHANGES, TabColorVfsListener(project))
        // logger.info("STARTUP: TabColorVfsListener registered for project: ${project.name}")

        // logger.info("STARTUP: Scheduling invokeLater for initial application of current colors.")
        // ApplicationManager.getApplication().invokeLater {
        //     if (project.isDisposed) {
        //         logger.info("STARTUP: Project ${project.name} is disposed (in invokeLater), skipping initial tab color refresh.")
        //         return@invokeLater
        //     }
        //     logger.info("STARTUP: invokeLater running for initial tab color refresh in project: ${project.name}")
        //     val diffDataService = project.service<ProjectActiveDiffDataService>()
        //     logger.info("STARTUP: Calling diffDataService.refreshCurrentColorings().")
        //     diffDataService.refreshCurrentColorings()
        //     logger.info("STARTUP: Explicit initial refresh for editor tab colors triggered for project: ${project.name}")
        // }

        // logger.info("STARTUP: Scheduling DELAYED ( ${INITIAL_DIFF_LOAD_DELAY_MS}ms) task for robust initial diff load for project: ${project.name}")

        // DumbService.getInstance(project).runWhenSmart {
        //     ApplicationManager.getApplication().executeOnPooledThread { // Offload Git operations
        //         Thread.sleep(INITIAL_DIFF_LOAD_DELAY_MS) // Simple delay on pooled thread

        //         ApplicationManager.getApplication().invokeLater { // Switch back to EDT for service interaction
        //             if (project.isDisposed) {
        //                 logger.info("STARTUP_DELAYED: Project ${project.name} is disposed, skipping delayed initial diff load.")
        //                 return@invokeLater
        //             }
        //             logger.info("STARTUP_DELAYED: Executing delayed task for initial diff load for project: ${project.name}")

        //             val gitService = project.service<GitService>()
        //             val currentRepo = gitService.getCurrentRepository() // This will use the enhanced logging

        //             if (currentRepo == null) {
        //                 logger.warn("STARTUP_DELAYED: Git repository still not found after delay for project: ${project.name}. Tab coloring may not function correctly.")
        //                 return@invokeLater
        //             }
        //             logger.info("STARTUP_DELAYED: Git repository found after delay: ${currentRepo.root.path}. Proceeding with initial diff load.")

        //             val toolWindowStateService = project.service<ToolWindowStateService>()
        //             val diffDataService = project.service<ProjectActiveDiffDataService>()
        //             val selectedBranchName = toolWindowStateService.getSelectedTabBranchName()

        //             if (selectedBranchName != null) {
        //                 if (diffDataService.activeBranchName != selectedBranchName || diffDataService.activeChanges.isEmpty()) {
        //                     logger.info("STARTUP_DELAYED: Attempting to load changes for initially selected tool window branch: '$selectedBranchName' because current activeBranchName is '${diffDataService.activeBranchName}' or activeChanges is empty.")
        //                     gitService.getChanges(selectedBranchName).whenCompleteAsync { changes, throwable ->
        //                         if (project.isDisposed) return@whenCompleteAsync
        //                         logger.info("STARTUP_DELAYED: getChanges for '$selectedBranchName' completed. Error: ${throwable != null}, Changes count: ${changes?.size ?: "null"}")
        //                         if (throwable != null) {
        //                             logger.error("STARTUP_DELAYED: Error getting changes for branch '$selectedBranchName': ${throwable.message}", throwable)
        //                         } else if (changes != null) {
        //                             logger.info("STARTUP_DELAYED: Successfully fetched ${changes.size} changes for '$selectedBranchName'. Updating ProjectActiveDiffDataService.")
        //                             diffDataService.updateActiveDiff(selectedBranchName, changes)
        //                         } else {
        //                             logger.warn("STARTUP_DELAYED: Fetched changes for '$selectedBranchName' but list was null.")
        //                         }
        //                     }
        //                 } else {
        //                     logger.info("STARTUP_DELAYED: Diff data for branch '$selectedBranchName' seems already loaded in ProjectActiveDiffDataService. Skipping redundant load.")
        //                     diffDataService.refreshCurrentColorings()
        //                 }
        //             } else {
        //                 logger.info("STARTUP_DELAYED: No branch selected in tool window. Clearing active diff data.")
        //                 diffDataService.clearActiveDiff()
        //             }
        //             logger.info("STARTUP_DELAYED: Delayed initial diff load task finished for project: ${project.name}")
        //         }
        //     }
        // }
        // logger.info("STARTUP: TabColorVfsListenerRegistrationActivity.execute COMPLETED (after scheduling delayed task) for project: ${project.name}")
    }
}

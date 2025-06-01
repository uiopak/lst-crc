package com.github.uiopak.lstcrc.listeners

import com.github.uiopak.lstcrc.services.GitService
import com.github.uiopak.lstcrc.services.ProjectActiveDiffDataService
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.vfs.VirtualFileManager
// Required for project.messageBus.connect(projectDisposable) -> projectDisposable needs to be Disposable
import com.intellij.openapi.Disposable

class ProjectOpenCloseListener : ProjectManagerListener {
    private val logger = thisLogger()
    private val INITIAL_DIFF_LOAD_DELAY_MS = 5000L // 5 seconds

    override fun projectOpened(project: Project) {
        logger.info("MMMM_TEST_LOG ProjectManagerListener.projectOpened IS RUNNING for project: ${project.name}")

        // Logic copied from TabColorVfsListenerRegistrationActivity.execute()
        logger.info("MMMM_STARTUP_LOGIC: Initializing from ProjectOpenCloseListener for project: ${project.name}")

        // Register VFS listener
        // Using project itself as disposable for the message bus connection.
        val projectDisposable = project as Disposable
        project.messageBus.connect(projectDisposable).subscribe(VirtualFileManager.VFS_CHANGES, TabColorVfsListener(project))
        logger.info("MMMM_STARTUP_LOGIC: TabColorVfsListener registered for project: ${project.name}")

        logger.info("MMMM_STARTUP_LOGIC: Scheduling invokeLater for initial application of current colors.")
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) {
                logger.info("MMMM_STARTUP_LOGIC: Project ${project.name} is disposed (in invokeLater), skipping initial tab color refresh.")
                return@invokeLater
            }
            logger.info("MMMM_STARTUP_LOGIC: invokeLater running for initial tab color refresh in project: ${project.name}")
            val diffDataService = project.service<ProjectActiveDiffDataService>()
            logger.info("MMMM_STARTUP_LOGIC: Calling diffDataService.refreshCurrentColorings().")
            diffDataService.refreshCurrentColorings()
            logger.info("MMMM_STARTUP_LOGIC: Explicit initial refresh for editor tab colors triggered for project: ${project.name}")
        }

        logger.info("MMMM_STARTUP_LOGIC: Scheduling DELAYED ( ${INITIAL_DIFF_LOAD_DELAY_MS}ms) task for robust initial diff load for project: ${project.name}")

        DumbService.getInstance(project).runWhenSmart {
             ApplicationManager.getApplication().executeOnPooledThread {
                 try {
                     Thread.sleep(INITIAL_DIFF_LOAD_DELAY_MS)
                 } catch (e: InterruptedException) {
                     logger.warn("MMMM_STARTUP_LOGIC_DELAYED: Thread.sleep interrupted", e)
                     Thread.currentThread().interrupt()
                     return@executeOnPooledThread
                 }

                 ApplicationManager.getApplication().invokeLater {
                     if (project.isDisposed) {
                         logger.info("MMMM_STARTUP_LOGIC_DELAYED: Project ${project.name} is disposed, skipping delayed initial diff load.")
                         return@invokeLater
                     }
                     logger.info("MMMM_STARTUP_LOGIC_DELAYED: Executing delayed task for initial diff load for project: ${project.name}")

                     val gitService = project.service<GitService>()
                     val currentRepo = gitService.getCurrentRepository()

                     if (currentRepo == null) {
                         logger.warn("MMMM_STARTUP_LOGIC_DELAYED: Git repository still not found after delay for project: ${project.name}. Tab coloring may not function correctly.")
                         return@invokeLater
                     }
                     logger.info("MMMM_STARTUP_LOGIC_DELAYED: Git repository found after delay: ${currentRepo.root.path}. Proceeding with initial diff load.")

                     val toolWindowStateService = project.service<ToolWindowStateService>()
                     val diffDataService = project.service<ProjectActiveDiffDataService>()
                     val selectedBranchName = toolWindowStateService.getSelectedTabBranchName()

                     if (selectedBranchName != null) {
                         if (diffDataService.activeBranchName != selectedBranchName || diffDataService.activeChanges.isEmpty()) {
                             logger.info("MMMM_STARTUP_LOGIC_DELAYED: Attempting to load changes for initially selected tool window branch: '$selectedBranchName' because current activeBranchName is '${diffDataService.activeBranchName}' or activeChanges is empty.")
                             gitService.getChanges(selectedBranchName).whenCompleteAsync { changes, throwable ->
                                 if (project.isDisposed) {
                                     logger.info("MMMM_STARTUP_LOGIC_DELAYED: Project ${project.name} disposed during getChanges for '$selectedBranchName'.")
                                     return@whenCompleteAsync
                                 }
                                 logger.info("MMMM_STARTUP_LOGIC_DELAYED: getChanges for '$selectedBranchName' completed. Error: ${throwable != null}, Changes count: ${changes?.size ?: "null"}")
                                 if (throwable != null) {
                                     logger.error("MMMM_STARTUP_LOGIC_DELAYED: Error getting changes for branch '$selectedBranchName': ${throwable.message}", throwable)
                                 } else if (changes != null) {
                                     logger.info("MMMM_STARTUP_LOGIC_DELAYED: Successfully fetched ${changes.size} changes for '$selectedBranchName'. Updating ProjectActiveDiffDataService.")
                                     diffDataService.updateActiveDiff(selectedBranchName, changes)
                                 } else {
                                     logger.warn("MMMM_STARTUP_LOGIC_DELAYED: Fetched changes for '$selectedBranchName' but list was null.")
                                 }
                             }
                         } else {
                             logger.info("MMMM_STARTUP_LOGIC_DELAYED: Diff data for branch '$selectedBranchName' seems already loaded in ProjectActiveDiffDataService. Refreshing colors anyway.")
                             diffDataService.refreshCurrentColorings()
                         }
                     } else {
                         logger.info("MMMM_STARTUP_LOGIC_DELAYED: No branch selected in tool window. Clearing active diff data.")
                         diffDataService.clearActiveDiff()
                     }
                     logger.info("MMMM_STARTUP_LOGIC_DELAYED: Delayed initial diff load task finished for project: ${project.name}")
                 }
             }
        }
        logger.info("MMMM_STARTUP_LOGIC: ProjectOpenCloseListener.projectOpened COMPLETED for project: ${project.name}")
    }

    override fun projectClosed(project: Project) {
        logger.info("MMMM_TEST_LOG ProjectManagerListener.projectClosed for project: ${project.name}")
    }
}

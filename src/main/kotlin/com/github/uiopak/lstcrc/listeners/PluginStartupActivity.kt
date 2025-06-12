package com.github.uiopak.lstcrc.listeners

import com.github.uiopak.lstcrc.services.GitService
import com.github.uiopak.lstcrc.services.ProjectActiveDiffDataService
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.github.uiopak.lstcrc.toolWindow.ToolWindowSettingsProvider
import com.github.uiopak.lstcrc.utils.await
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Replaces the deprecated `ProjectManagerListener`.
 * This activity runs once per project after it has been opened and initial indexing is complete.
 * Its primary responsibility is to perform the initial load of Git diff data based on the
 * plugin's persisted state, so that features like file scopes and tab colors are available
 * as early as possible.
 */
class PluginStartupActivity : ProjectActivity {
    private val logger = thisLogger()
    private val _initialDiffLoadDelayMs = 5000L // 5 seconds

    override suspend fun execute(project: Project) {
        logger.info("STARTUP_LOGIC: ProjectActivity executing for project: ${project.name}")

        // This logic was previously in ProjectOpenCloseListener.
        // It's responsible for loading the initial diff data based on the persisted state
        // of the tool window, ensuring that features like tab colors and scopes work
        // immediately upon project open, even before the tool window is visible.

        // Initial, quick refresh for tab colors of already open files.
        withContext(Dispatchers.EDT) {
            if (project.isDisposed) {
                logger.info("STARTUP_LOGIC: Project ${project.name} is disposed, skipping initial tab color refresh.")
                return@withContext
            }
            logger.info("STARTUP_LOGIC: Running initial tab color refresh for project: ${project.name}")
            project.service<ProjectActiveDiffDataService>().refreshCurrentColorings()
        }

        logger.info("STARTUP_LOGIC: Scheduling delayed task for robust initial diff load.")

        // This delay can help ensure that Git processes and repositories are fully initialized by Git4Idea
        // before we attempt to query them, avoiding race conditions on project startup.
        delay(_initialDiffLoadDelayMs)

        if (project.isDisposed) {
            logger.info("STARTUP_LOGIC_DELAYED: Project ${project.name} is disposed, skipping delayed initial diff load.")
            return
        }
        logger.info("STARTUP_LOGIC_DELAYED: Executing delayed task for initial diff load for project: ${project.name}")

        val gitService = project.service<GitService>()
        val currentRepo = gitService.getCurrentRepository()
        val toolWindowStateService = project.service<ToolWindowStateService>()
        val diffDataService = project.service<ProjectActiveDiffDataService>()

        if (currentRepo == null) {
            logger.warn("STARTUP_LOGIC_DELAYED: Git repository still not found after delay for project: ${project.name}. Tab coloring may not function correctly.")
            // Even if git isn't ready, we should still broadcast the state to update the widget from "LST-CRC" to whatever is persisted (e.g. "HEAD")
            withContext(Dispatchers.EDT) {
                logger.info("STARTUP_LOGIC_DELAYED: Broadcasting ToolWindowState to sync UI components even though Git repo was not found.")
                toolWindowStateService.broadcastCurrentState()
            }
            return
        }
        logger.info("STARTUP_LOGIC_DELAYED: Git repository found after delay: ${currentRepo.root.path}. Proceeding with initial diff load.")

        val selectedBranchName = toolWindowStateService.getSelectedTabBranchName()

        if (selectedBranchName != null) {
            if (diffDataService.activeBranchName != selectedBranchName || diffDataService.activeChanges.isEmpty()) {
                logger.info("STARTUP_LOGIC_DELAYED: Attempting to load changes for initially selected tool window branch: '$selectedBranchName'.")
                try {
                    val categorizedChanges = gitService.getChanges(selectedBranchName).await()
                    logger.info("STARTUP_LOGIC_DELAYED: Successfully fetched changes, updating ProjectActiveDiffDataService for '$selectedBranchName'.")
                    withContext(Dispatchers.EDT) {
                        if (project.isDisposed) return@withContext
                        diffDataService.updateActiveDiff(
                            selectedBranchName,
                            categorizedChanges.allChanges,
                            categorizedChanges.createdFiles,
                            categorizedChanges.modifiedFiles,
                            categorizedChanges.movedFiles
                        )
                    }
                } catch (throwable: Throwable) {
                    logger.error("STARTUP_LOGIC_DELAYED: Error getting changes for branch '$selectedBranchName': ${throwable.message}", throwable)
                    withContext(Dispatchers.EDT) {
                        if (project.isDisposed) return@withContext
                        diffDataService.updateActiveDiff(selectedBranchName, emptyList(), emptyList(), emptyList(), emptyList())
                    }
                }
            } else {
                logger.info("STARTUP_LOGIC_DELAYED: Diff data for branch '$selectedBranchName' seems already loaded. Refreshing colors anyway.")
                withContext(Dispatchers.EDT) {
                    if (project.isDisposed) return@withContext
                    diffDataService.refreshCurrentColorings()
                }
            }
        } else {
            logger.info("STARTUP_LOGIC_DELAYED: No branch selected in tool window (HEAD is active). Checking setting.")
            val properties = PropertiesComponent.getInstance()
            val includeHeadInScopes = properties.getBoolean(
                ToolWindowSettingsProvider.APP_INCLUDE_HEAD_IN_SCOPES_KEY,
                ToolWindowSettingsProvider.DEFAULT_INCLUDE_HEAD_IN_SCOPES
            )
            val headBranchName = "HEAD"

            if (includeHeadInScopes) {
                logger.info("STARTUP_LOGIC_DELAYED: 'Include HEAD in Scopes' is ON. Loading HEAD changes.")
                try {
                    val categorizedChanges = gitService.getChanges(headBranchName).await()
                    logger.info("STARTUP_LOGIC_DELAYED: Successfully fetched changes for HEAD. Updating service.")
                    withContext(Dispatchers.EDT) {
                        if (project.isDisposed) return@withContext
                        diffDataService.updateActiveDiff(
                            headBranchName,
                            categorizedChanges.allChanges,
                            categorizedChanges.createdFiles,
                            categorizedChanges.modifiedFiles,
                            categorizedChanges.movedFiles
                        )
                    }
                } catch (throwable: Throwable) {
                    logger.error("STARTUP_LOGIC_DELAYED: Error getting changes for HEAD: ${throwable.message}", throwable)
                    withContext(Dispatchers.EDT) {
                        if (project.isDisposed) return@withContext
                        diffDataService.clearActiveDiff()
                    }
                }
            } else {
                logger.info("STARTUP_LOGIC_DELAYED: 'Include HEAD in Scopes' is OFF. Clearing active diff data.")
                withContext(Dispatchers.EDT) {
                    if (project.isDisposed) return@withContext
                    diffDataService.clearActiveDiff()
                }
            }
        }
        logger.info("STARTUP_LOGIC_DELAYED: Delayed initial diff load task finished for project: ${project.name}")

        withContext(Dispatchers.EDT) {
            // After all initial data loading, broadcast the current tool window state
            // to ensure components like the status bar widget are up-to-date with the loaded state.
            logger.info("STARTUP_LOGIC_DELAYED: Broadcasting final ToolWindowState to sync all UI components.")
            if (project.isDisposed) return@withContext
            toolWindowStateService.broadcastCurrentState()
        }
    }
}
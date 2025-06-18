package com.github.uiopak.lstcrc.listeners

import com.github.uiopak.lstcrc.services.GitService
import com.github.uiopak.lstcrc.services.LstCrcGutterTrackerService
import com.github.uiopak.lstcrc.services.ProjectActiveDiffDataService
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.github.uiopak.lstcrc.services.VfsListenerService
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * This activity runs once per project after it has been opened and initial indexing is complete.
 * Its primary responsibility is to perform the initial load of Git diff data based on the
 * plugin's persisted state. This ensures that features like file scopes, gutter markers, and
 * tab colors are available as early as possible, even before the tool window is opened.
 * It also eagerly initializes background services.
 */
class PluginStartupActivity : ProjectActivity {
    private val logger = thisLogger()
    private val _initialDiffLoadDelayMs = 5000L

    override suspend fun execute(project: Project) {
        logger.info("STARTUP_LOGIC: ProjectActivity executing for project: ${project.name}")

        // Eagerly initialize services that need to listen to events from the start.
        // This ensures features like gutter markers and VFS-triggered refreshes work correctly.
        logger.info("STARTUP_LOGIC: Eagerly initializing background services.")
        project.service<VfsListenerService>()
        project.service<LstCrcGutterTrackerService>()
        project.service<VcsChangeListener>()
        logger.info("STARTUP_LOGIC: Background services initialized.")

        // Perform a quick initial refresh for tab colors of already open files.
        withContext(Dispatchers.EDT) {
            if (project.isDisposed) {
                logger.info("STARTUP_LOGIC: Project ${project.name} is disposed, skipping initial tab color refresh.")
                return@withContext
            }
            logger.info("STARTUP_LOGIC: Running initial tab color refresh for project: ${project.name}")
            project.service<ProjectActiveDiffDataService>().refreshCurrentColorings()
        }

        logger.info("STARTUP_LOGIC: Scheduling delayed task for robust initial diff load.")

        // This delay helps ensure that Git processes and repositories are fully initialized by Git4Idea
        // before we attempt to query them, avoiding potential race conditions on project startup.
        delay(_initialDiffLoadDelayMs)

        if (project.isDisposed) {
            logger.info("STARTUP_LOGIC_DELAYED: Project ${project.name} is disposed, skipping delayed initial diff load.")
            return
        }
        logger.info("STARTUP_LOGIC_DELAYED: Executing delayed task for initial diff load for project: ${project.name}")

        val gitService = project.service<GitService>()
        val currentRepo = gitService.getCurrentRepository()
        val toolWindowStateService = project.service<ToolWindowStateService>()

        if (currentRepo == null) {
            logger.warn("STARTUP_LOGIC_DELAYED: Git repository still not found after delay for project: ${project.name}. Tab coloring may not function correctly.")
            // If git isn't ready, still broadcast the state to update the widget from "LST-CRC"
            // to whatever is persisted (e.g., "HEAD").
            withContext(Dispatchers.EDT) {
                if (project.isDisposed) return@withContext
                logger.info("STARTUP_LOGIC_DELAYED: Broadcasting ToolWindowState to sync UI components even though Git repo was not found.")
                toolWindowStateService.broadcastCurrentState()
            }
            return
        }
        logger.info("STARTUP_LOGIC_DELAYED: Git repository found after delay: ${currentRepo.root.path}. Proceeding with initial diff load.")

        // This single call orchestrates fetching data, updating ProjectActiveDiffDataService,
        // and updating the UI (if the tool window is open) for the persisted selected tab.
        toolWindowStateService.refreshDataForCurrentSelection()

        logger.info("STARTUP_LOGIC_DELAYED: Delayed initial diff load task finished for project: ${project.name}")

        withContext(Dispatchers.EDT) {
            // After all initial data loading, broadcast the current tool window state again
            // to ensure components like the status bar widget are up-to-date.
            if (project.isDisposed) return@withContext
            logger.info("STARTUP_LOGIC_DELAYED: Broadcasting final ToolWindowState to sync all UI components.")
            toolWindowStateService.broadcastCurrentState()
        }
    }
}
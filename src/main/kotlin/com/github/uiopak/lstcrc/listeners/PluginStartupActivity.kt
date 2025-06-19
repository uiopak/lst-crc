package com.github.uiopak.lstcrc.listeners

import com.github.uiopak.lstcrc.services.GitService
import com.github.uiopak.lstcrc.services.LstCrcGutterTrackerService
import com.github.uiopak.lstcrc.services.ProjectActiveDiffDataService
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.github.uiopak.lstcrc.services.VfsListenerService
import com.github.uiopak.lstcrc.toolWindow.LstCrcStatusWidget
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Runs on project startup to perform the initial load of Git diff data based on the plugin's
 * persisted state. This ensures features like file scopes and gutter markers are available
 * early. It also eagerly initializes background services.
 */
class PluginStartupActivity : ProjectActivity {
    private val logger = thisLogger()

    /**
     * Suspends the coroutine until the project is in smart mode, using the stable, public API.
     */
    private suspend fun awaitSmartMode(project: Project) {
        suspendCancellableCoroutine { continuation ->
            DumbService.getInstance(project).runWhenSmart {
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
    }

    /**
     * Helper extension to bridge CompletableFuture with coroutines.
     */
    private suspend fun <T> CompletableFuture<T>.await(): T =
        suspendCancellableCoroutine { cont ->
            whenComplete { result, exception ->
                if (exception == null) {
                    if (cont.isActive) cont.resume(result)
                } else {
                    cont.resumeWithException(exception)
                }
            }
            cont.invokeOnCancellation {
                cancel(true)
            }
        }


    override suspend fun execute(project: Project) {
        logger.info("STARTUP_LOGIC: ProjectActivity executing for project: ${project.name}")

        // Eagerly initialize services that need to listen to events from the start to ensure
        // features like gutter markers and VFS-triggered refreshes work correctly.
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

        logger.info("STARTUP_LOGIC: Waiting for smart mode before initial diff load to ensure Git is ready.")
        // Wait for the IDE to finish indexing and other startup activities. This is a robust way
        // to avoid race conditions with Git4Idea initialization, replacing a fixed-time delay.
        awaitSmartMode(project)
        logger.info("STARTUP_LOGIC: Project is in smart mode. Executing initial diff load for project: ${project.name}")

        if (project.isDisposed) {
            logger.info("STARTUP_LOGIC: Project ${project.name} is disposed after smart mode, skipping initial diff load.")
            return
        }

        val gitService = project.service<GitService>()
        val currentRepo = gitService.getCurrentRepository()
        val toolWindowStateService = project.service<ToolWindowStateService>()

        if (currentRepo == null) {
            logger.warn("STARTUP_LOGIC: Git repository still not found after smart mode for project: ${project.name}. Tab coloring may not function correctly.")
            // If git isn't ready, still broadcast the state to update the widget from "LST-CRC"
            // to whatever is persisted (e.g., "HEAD").
            withContext(Dispatchers.EDT) {
                if (project.isDisposed) return@withContext
                logger.info("STARTUP_LOGIC: Broadcasting ToolWindowState to sync UI components even though Git repo was not found.")
                toolWindowStateService.broadcastCurrentState()
            }
            return
        }
        logger.info("STARTUP_LOGIC: Git repository found after smart mode: ${currentRepo.root.path}. Proceeding with initial diff load.")

        // This single call orchestrates fetching data and updating services. We now await its completion.
        try {
            toolWindowStateService.refreshDataForCurrentSelection().await()
            logger.info("STARTUP_LOGIC: Initial diff load task finished for project: ${project.name}")
        } catch (e: Exception) {
            logger.warn("STARTUP_LOGIC: Initial diff load failed.", e)
        }


        withContext(Dispatchers.EDT) {
            // After all initial data loading is confirmed complete, ensure all UI components are synced.
            if (project.isDisposed) return@withContext
            logger.info("STARTUP_LOGIC: Broadcasting final ToolWindowState to sync all UI components.")
            toolWindowStateService.broadcastCurrentState()

            // Also send a direct update request to the status bar for our widget.
            // This is a more robust way to ensure it refreshes on startup.
            val statusBar = WindowManager.getInstance().getStatusBar(project)
            statusBar?.updateWidget(LstCrcStatusWidget.ID)
            logger.info("STARTUP_LOGIC: Sent direct update request to status bar widget '${LstCrcStatusWidget.ID}'.")
        }
    }
}
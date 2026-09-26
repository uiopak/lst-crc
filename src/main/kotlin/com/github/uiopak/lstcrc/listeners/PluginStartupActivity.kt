package com.github.uiopak.lstcrc.listeners

import com.github.uiopak.lstcrc.services.GitService
import com.github.uiopak.lstcrc.services.ProjectActiveDiffDataService
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Runs on project startup to perform the initial load of Git diff data based on the plugin's
 * persisted state. This ensures features like file scopes and gutter markers are available
 * early. It also eagerly initializes background services.
 */
class PluginStartupActivity : ProjectActivity {
    private val logger = thisLogger()

    /** Broadcasts the tab state, which also updates the status bar widget (it listens to the topic). */
    private suspend fun syncUiAfterRefresh(project: Project, stateService: ToolWindowStateService) {
        withContext(Dispatchers.EDT) {
            if (project.isDisposed) return@withContext
            logger.debug { "STARTUP_LOGIC: Broadcasting ToolWindowState to sync all UI components." }
            stateService.broadcastCurrentState()
        }
    }

    /**
     * Suspends until the VCS subsystem is initialized, which includes the initial detection of Git
     * repositories. (`ProjectLevelVcsManager.awaitInitialization` does this directly, from 2025.3.)
     * The manager is looked up as a service: from 2025.3 `getInstance` is on a Kotlin companion,
     * which 2025.1/2025.2 do not have.
     */
    private suspend fun awaitVcsInitialization(project: Project) {
        suspendCancellableCoroutine { continuation ->
            project.service<ProjectLevelVcsManager>().runAfterInitialization {
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
    }

    override suspend fun execute(project: Project) {
        logger.debug { "STARTUP_LOGIC: ProjectActivity executing for project: ${project.name}" }

        project.service<VcsChangeListener>()
        project.service<com.github.uiopak.lstcrc.gutters.VisualTrackerManager>().init()

        // Perform a quick initial refresh for tab colors of already open files.
        withContext(Dispatchers.EDT) {
            if (!project.isDisposed) project.service<ProjectActiveDiffDataService>().refreshCurrentColorings()
        }

        // The diff load only needs Git repositories, not indexes, so it does not wait for indexing
        // (which can take minutes on a large project).
        awaitVcsInitialization(project)
        if (project.isDisposed) return

        val toolWindowStateService = project.service<ToolWindowStateService>()
        if (project.service<GitService>().getPrimaryRepository() == null) {
            // No repository was detected; still sync the persisted state to the UI.
            logger.debug { "STARTUP_LOGIC: No Git repository for project: ${project.name}. Skipping startup diff load." }
        } else {
            try {
                toolWindowStateService.refreshDataForCurrentSelection().await()
                logger.debug { "STARTUP_LOGIC: Initial diff load task finished for project: ${project.name}" }
            } catch (e: Exception) {
                logger.warn("STARTUP_LOGIC: Initial diff load failed.", e)
            }
        }

        syncUiAfterRefresh(project, toolWindowStateService)
    }
}
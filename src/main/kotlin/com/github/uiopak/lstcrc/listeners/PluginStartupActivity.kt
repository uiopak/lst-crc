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
import git4idea.repo.GitRepositoryManager
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
        logger.debug { "STARTUP_LOGIC: Background services initialized." }

        // Perform a quick initial refresh for tab colors of already open files.
        withContext(Dispatchers.EDT) {
            if (project.isDisposed) {
                logger.debug { "STARTUP_LOGIC: Project ${project.name} is disposed, skipping initial tab color refresh." }
                return@withContext
            }
            logger.debug { "STARTUP_LOGIC: Running initial tab color refresh for project: ${project.name}" }
            project.service<ProjectActiveDiffDataService>().refreshCurrentColorings()
        }

        // The diff load only needs Git repositories, not indexes, so it does not wait for indexing
        // (which can take minutes on a large project).
        logger.debug { "STARTUP_LOGIC: Waiting for VCS initialization before initial diff load." }
        awaitVcsInitialization(project)
        logger.debug { "STARTUP_LOGIC: VCS initialized. Executing initial diff load for project: ${project.name}" }

        if (project.isDisposed) {
            logger.debug { "STARTUP_LOGIC: Project ${project.name} is disposed after VCS initialization, skipping initial diff load." }
            return
        }

        val gitService = project.service<GitService>()
        val currentRepo = gitService.getPrimaryRepository()
        val toolWindowStateService = project.service<ToolWindowStateService>()

        if (currentRepo == null) {
            val hasAnyGitRepositories = GitRepositoryManager.getInstance(project).repositories.isNotEmpty()
            if (hasAnyGitRepositories) {
                logger.warn("STARTUP_LOGIC: Git repository still not found after VCS initialization for project: ${project.name}. Tab coloring may not function correctly.")
            } else {
                logger.debug { "STARTUP_LOGIC: No Git repository configured for project: ${project.name}. Skipping startup diff load." }
            }
            // If git isn't ready, still sync persisted state to UI.
            syncUiAfterRefresh(project, toolWindowStateService)
            return
        }
        logger.debug { "STARTUP_LOGIC: Git repository found after VCS initialization: ${currentRepo.root.path}. Proceeding with initial diff load." }

        // This single call orchestrates fetching data and updating services. We now await its completion.
        try {
            toolWindowStateService.refreshDataForCurrentSelection().await()
            logger.debug { "STARTUP_LOGIC: Initial diff load task finished for project: ${project.name}" }
        } catch (e: Exception) {
            logger.warn("STARTUP_LOGIC: Initial diff load failed.", e)
        }

        syncUiAfterRefresh(project, toolWindowStateService)
    }
}
package com.github.uiopak.lstcrc.listeners

import com.github.uiopak.lstcrc.services.ProjectActiveDiffDataService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.Disposable // Keep for the cast, good practice.


class TabColorVfsListenerRegistrationActivity : ProjectActivity {
    private val logger = thisLogger()

    override suspend fun execute(project: Project) {
        logger.info("Executing TabColorVfsListenerRegistrationActivity for project: ${project.name}")

        // Register VFS listener
        val disposable = project as Disposable
        project.messageBus.connect(disposable).subscribe(VirtualFileManager.VFS_CHANGES, TabColorVfsListener(project))
        logger.info("TabColorVfsListener registered for project: ${project.name}")

        // Schedule a refresh of editor tab colors to run after startup processes
        ApplicationManager.getApplication().invokeLater {
            // Consider if a small delay is needed, e.g. SwingUtilities.invokeLater or a short Timer
            // For now, direct invokeLater.
            if (project.isDisposed) {
                logger.info("Project ${project.name} is disposed, skipping startup tab color refresh.")
                return@invokeLater
            }
            logger.info("Attempting to trigger startup refresh for editor tab colors in project: ${project.name}")
            val diffDataService = project.service<ProjectActiveDiffDataService>()

            diffDataService.refreshCurrentColorings() // Call the new public method
            logger.info("Explicit startup refresh for editor tab colors triggered for project: ${project.name}")
        }
    }
}

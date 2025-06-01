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
        logger.info("STARTUP: TabColorVfsListenerRegistrationActivity.execute for project: ${project.name}")

        // Register VFS listener
        val disposable = project as Disposable
        logger.info("STARTUP: Connecting to message bus for VFS_CHANGES.")
        project.messageBus.connect(disposable).subscribe(VirtualFileManager.VFS_CHANGES, TabColorVfsListener(project))
        logger.info("STARTUP: TabColorVfsListener registered for project: ${project.name}")

        logger.info("STARTUP: Scheduling invokeLater for initial tab color refresh.")
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) {
                logger.info("STARTUP: Project ${project.name} is disposed (in invokeLater), skipping initial tab color refresh.")
                return@invokeLater
            }
            logger.info("STARTUP: invokeLater running for initial tab color refresh in project: ${project.name}")
            val diffDataService = project.service<ProjectActiveDiffDataService>()
            logger.info("STARTUP: Calling diffDataService.refreshCurrentColorings().")
            diffDataService.refreshCurrentColorings()
            logger.info("STARTUP: Explicit initial refresh for editor tab colors triggered for project: ${project.name}")
        }
        logger.info("STARTUP: TabColorVfsListenerRegistrationActivity.execute COMPLETED for project: ${project.name}")
    }
}

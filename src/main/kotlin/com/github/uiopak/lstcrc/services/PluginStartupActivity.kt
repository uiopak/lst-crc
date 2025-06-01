package com.github.uiopak.lstcrc.services

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener // Required for VFS_CHANGES topic type
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile

class PluginStartupActivity : ProjectActivity {
    private val logger = thisLogger() // Added logger instance

    override suspend fun execute(project: Project) {
        logger.info("PluginStartupActivity running for project: ${project.name}")

        // Existing VFS listener setup - keep if needed for other functionality
        val vfsChangeListenerInstance = VfsChangeListener()
        val connection = project.messageBus.connect(project) // project is a Disposable
        connection.subscribe(VirtualFileManager.VFS_CHANGES, vfsChangeListenerInstance as BulkFileListener)
        logger.info("VFS Change listener subscribed.")

        // Color existing open tabs
        val fileEditorManager = FileEditorManager.getInstance(project)
        val openFiles: Array<VirtualFile> = fileEditorManager.openFiles

        if (openFiles.isEmpty()) {
            logger.info("No files open in editors at startup.")
        } else {
            logger.info("Processing ${openFiles.size} initially open files for tab coloring.")
            for (file in openFiles) {
                logger.info("Requesting presentation update for initially open file: ${file.name}")
                fileEditorManager.updateFilePresentation(file)
            }
            logger.info("Finished processing initially open files.")
        }
    }
}

package com.github.uiopak.lstcrc.listeners

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.github.uiopak.lstcrc.services.GitService

class TabColorFileEditorManagerListener : FileEditorManagerListener {

    private val logger = thisLogger()

    override fun selectionChanged(event: FileEditorManagerEvent) {
        logger.info("Tab selection changed")
        val project: Project? = event.manager.project
        val newFile: VirtualFile? = event.newFile

        if (project == null || newFile == null) {
            logger.info("Project or newFile is null, cannot process tab color.")
            return
        }

        logger.info("Project: ${project.name}, New file: ${newFile.path}")

        val gitService = project.getService(GitService::class.java)
        if (gitService == null) {
            logger.warn("GitService not found for project ${project.name}")
            return
        }

        val colorHex = gitService.calculateEditorTabColor(newFile.path)
        logger.info("Calculated color for ${newFile.name}: '$colorHex'")

        // Trigger a UI update for the tab.
        // This will cause IntelliJ to query registered EditorTabColorProviders.
        logger.info("Requesting file presentation update for ${newFile.name} to apply potential color changes.")
        FileEditorManager.getInstance(project).updateFilePresentation(newFile)
    }

    // Potentially override fileOpened and fileClosed if needed
    // For example, fileOpened could also call updateFilePresentation if needed for initially opened tabs.
    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        // Also update presentation when a file is first opened.
        logger.info("File opened: ${file.name}. Requesting file presentation update.")
        source.project.let { project ->
            FileEditorManager.getInstance(project).updateFilePresentation(file)
        }
    }
    // override fun fileClosed(source: FileEditorManager, file: VirtualFile) {}
}

package com.github.uiopak.lstcrc.listeners

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.github.uiopak.lstcrc.services.GitService
import com.github.uiopak.lstcrc.settings.TabColorSettingsState

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

        // We don't need to calculate the color or get GitService here.
        // The provider (GitStatusBasedTabColorProvider) will be called due to updateFilePresentation.
        // The provider will fetch settings and call GitService.
        logger.info("Requesting presentation update for file: ${newFile.path}.")

        // Trigger a UI update for the tab.
        // This will cause IntelliJ to query registered EditorTabColorProviders.
        FileEditorManager.getInstance(project).updateFilePresentation(newFile)
    }

    // Potentially override fileOpened and fileClosed if needed
    // For example, fileOpened could also call updateFilePresentation if needed for initially opened tabs.
    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        val project = source.project
        // The provider will be invoked and will fetch settings itself.
        logger.info("File opened: ${file.name} in project ${project.name}. Requesting presentation update.")
        FileEditorManager.getInstance(project).updateFilePresentation(file)
    }
    // override fun fileClosed(source: FileEditorManager, file: VirtualFile) {}
}

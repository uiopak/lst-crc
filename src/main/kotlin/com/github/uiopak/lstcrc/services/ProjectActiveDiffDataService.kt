package com.github.uiopak.lstcrc.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change

@Service(Service.Level.PROJECT)
class ProjectActiveDiffDataService(private val project: Project) : Disposable {
    private val logger = thisLogger()

    var activeBranchName: String? = null
        private set // Allow external read, internal write
    var activeChanges: List<Change> = emptyList()
        private set // Allow external read, internal write

    fun updateActiveDiff(branchName: String, changes: List<Change>) {
        logger.info("Updating active diff for branch: $branchName with ${changes.size} changes.")
        this.activeBranchName = branchName
        this.activeChanges = changes
        triggerEditorTabColorRefresh()
    }

    fun clearActiveDiff() {
        logger.info("Clearing active diff data.")
        this.activeBranchName = null
        this.activeChanges = emptyList()
        triggerEditorTabColorRefresh()
    }

    private fun triggerEditorTabColorRefresh() {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) {
                logger.info("Project is disposed, skipping editor tab color refresh.")
                return@invokeLater
            }
            val fileEditorManager = FileEditorManager.getInstance(project)
            fileEditorManager.openFiles.forEach { vf ->
                if (vf.isValid) {
                    logger.debug("Requesting presentation update for file: ${vf.path}")
                    fileEditorManager.updateFilePresentation(vf)
                }
            }
        }
    }

    override fun dispose() {
        logger.info("Disposing ProjectActiveDiffDataService, clearing changes.")
        activeChanges = emptyList()
        activeBranchName = null
    }
}

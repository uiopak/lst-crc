package com.github.uiopak.lstcrc.services

import com.github.uiopak.lstcrc.messaging.ActiveDiffDataChangedListener
import com.github.uiopak.lstcrc.messaging.DIFF_DATA_CHANGED_TOPIC
import com.github.uiopak.lstcrc.toolWindow.ToolWindowSettingsProvider
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.intellij.openapi.vfs.VirtualFile
import java.util.concurrent.CompletableFuture

@Service(Service.Level.PROJECT)
class LstCrcGutterTrackerService(private val project: Project) : Disposable {

    private val logger = thisLogger()
    private val properties = PropertiesComponent.getInstance()
    private var lastKnownTrackedFiles: Set<VirtualFile> = emptySet()

    init {
        logger.info("LstCrcGutterTrackerService initialized")
        val connection = project.messageBus.connect(this)

        connection.subscribe(DIFF_DATA_CHANGED_TOPIC, object : ActiveDiffDataChangedListener {
            override fun onDiffDataChanged() {
                logger.debug("Received onDiffDataChanged event, updating all gutter trackers.")
                updateAllTrackers()
            }
        })

        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                applyTrackerStateToFile(file)
            }
        })

        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                logger.debug("Performing initial gutter tracker update.")
                updateAllTrackers()
            }
        }
    }

    fun settingsChanged() {
        logger.debug("Gutter tracker setting changed, updating all trackers.")
        updateAllTrackers()
    }

    private fun updateAllTrackers() {
        if (project.isDisposed) return
        logger.debug("Updating all gutter trackers based on current state.")

        val isEnabled = properties.getBoolean(
            ToolWindowSettingsProvider.APP_ENABLE_GUTTER_MARKERS_KEY,
            ToolWindowSettingsProvider.DEFAULT_ENABLE_GUTTER_MARKERS
        )

        val diffDataService = project.service<ProjectActiveDiffDataService>()
        val activeBranch = diffDataService.activeBranchName

        val currentFilesToTrack = if (isEnabled && activeBranch != null) {
            (diffDataService.createdFiles + diffDataService.modifiedFiles + diffDataService.movedFiles).toSet()
        } else {
            emptySet()
        }

        val filesToReset = lastKnownTrackedFiles - currentFilesToTrack
        val filesToSet = currentFilesToTrack

        filesToReset.forEach { resetTracker(it) }

        if (isEnabled && activeBranch != null) {
            val openFiles = FileEditorManager.getInstance(project).openFiles.toSet()
            filesToSet.intersect(openFiles).forEach { file ->
                setTracker(file, activeBranch)
            }
        }

        lastKnownTrackedFiles = currentFilesToTrack
        logger.debug("Gutter tracker update complete. Now tracking ${lastKnownTrackedFiles.size} files.")
    }

    private fun applyTrackerStateToFile(file: VirtualFile) {
        if (project.isDisposed || !file.isValid) return

        val isEnabled = properties.getBoolean(
            ToolWindowSettingsProvider.APP_ENABLE_GUTTER_MARKERS_KEY,
            ToolWindowSettingsProvider.DEFAULT_ENABLE_GUTTER_MARKERS
        )

        if (!isEnabled) return

        val diffDataService = project.service<ProjectActiveDiffDataService>()
        val activeBranch = diffDataService.activeBranchName
        val filesToTrack = (diffDataService.createdFiles + diffDataService.modifiedFiles + diffDataService.movedFiles).toSet()

        if (activeBranch != null && file in filesToTrack) {
            logger.debug("File opened and is part of the active diff set: ${file.path}. Setting custom tracker.")
            setTracker(file, activeBranch)
        }
    }

    private fun setTracker(file: VirtualFile, branchName: String) {
        if (project.isDisposed || !file.isValid) return

        val gitService = project.service<GitService>()
        // **CORRECTED TYPO**
        val diffDataService = project.service<ProjectActiveDiffDataService>()
        val isCreated = file in diffDataService.createdFiles.toSet()

        val vcsContentFuture = if (isCreated) {
            CompletableFuture.completedFuture("")
        } else {
            gitService.getFileContentForRevision(branchName, file)
        }

        vcsContentFuture.whenCompleteAsync { content, throwable ->
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater

                val document = FileDocumentManager.getInstance().getDocument(file)
                if (document == null) {
                    logger.warn("Could not get Document for file ${file.path} to offer tracker content.")
                    return@invokeLater
                }
                val manager = LineStatusTrackerManager.getInstanceImpl(project)

                if (throwable != null) {
                    // **FIXED**: Check if the exception is the specific "file not found" case.
                    // If so, treat it as a success with empty content. Otherwise, it's a real error.
                    val isFileNotFoundError = throwable is VcsException &&
                            throwable.message?.contains("exists on disk, but not in", ignoreCase = true) == true

                    if (isFileNotFoundError) {
                        logger.debug("File ${file.path} not found in revision '$branchName'. Treating as new file for gutter diff.")
                        manager.offerTrackerContent(document, "") // Offer empty content
                    } else {
                        // It's a different, unexpected error.
                        logger.warn("Failed to get content for revision '$branchName' of file ${file.path}, resetting tracker.", throwable)
                        resetTracker(file)
                    }
                } else {
                    // Success case: content was found or the future completed with null.
                    manager.offerTrackerContent(document, content ?: "")
                    logger.debug("Offered VCS content from branch '$branchName' for file tracker: ${file.path}")
                }
            }
        }
    }

    private fun resetTracker(file: VirtualFile) {
        if (project.isDisposed || !file.isValid) return
        VcsDirtyScopeManager.getInstance(project).fileDirty(file)
        logger.debug("Requested tracker reset for file by marking as dirty: ${file.path}")
    }

    override fun dispose() {
        logger.info("Disposing LstCrcGutterTrackerService. Resetting all known tracked files.")
        ApplicationManager.getApplication().invokeLater { lastKnownTrackedFiles.forEach { resetTracker(it) } }
    }
}
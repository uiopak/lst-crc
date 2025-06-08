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
        logger.info("GUTTER_TRACKER: Initializing for project '${project.name}'")
        val connection = project.messageBus.connect(this)

        connection.subscribe(DIFF_DATA_CHANGED_TOPIC, object : ActiveDiffDataChangedListener {
            override fun onDiffDataChanged() {
                logger.debug("GUTTER_TRACKER: Received onDiffDataChanged event. Scheduling gutter tracker update.")
                updateAllTrackers("onDiffDataChanged")
            }
        })

        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                logger.debug("GUTTER_TRACKER: File opened: ${file.path}. Applying tracker state.")
                applyTrackerStateToFile(file)
            }
        })

        // Initial update for already-open files
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                logger.debug("GUTTER_TRACKER: Performing initial gutter tracker update for already-open files.")
                updateAllTrackers("initialization")
            }
        }
    }

    fun settingsChanged() {
        logger.info("GUTTER_TRACKER: Gutter marker setting changed. Scheduling gutter tracker update.")
        updateAllTrackers("settingsChanged")
    }

    private fun updateAllTrackers(reason: String) {
        if (project.isDisposed) {
            logger.warn("GUTTER_TRACKER: updateAllTrackers($reason) called, but project is disposed. Aborting.")
            return
        }
        logger.info("GUTTER_TRACKER: Updating all gutter trackers. Reason: $reason")

        val isEnabled = properties.getBoolean(
            ToolWindowSettingsProvider.APP_ENABLE_GUTTER_MARKERS_KEY,
            ToolWindowSettingsProvider.DEFAULT_ENABLE_GUTTER_MARKERS
        )

        val diffDataService = project.service<ProjectActiveDiffDataService>()
        val activeBranch = diffDataService.activeBranchName

        logger.debug("GUTTER_TRACKER: Feature enabled: $isEnabled. Active branch: '$activeBranch'.")

        val currentFilesToTrack = if (isEnabled && activeBranch != null) {
            (diffDataService.createdFiles + diffDataService.modifiedFiles + diffDataService.movedFiles).toSet()
        } else {
            emptySet()
        }

        logger.debug("""
            GUTTER_TRACKER: State evaluation:
            - Last known tracked files: ${lastKnownTrackedFiles.size}
            - Current files to track: ${currentFilesToTrack.size}
        """.trimIndent())


        val filesToReset = lastKnownTrackedFiles - currentFilesToTrack
        // We only need to explicitly set trackers for files that are newly tracked AND open.
        // Files that were already tracked and remain tracked don't need to be touched unless the branch changes,
        // which is handled by this full refresh anyway.
        val filesToSet = currentFilesToTrack

        if (filesToReset.isNotEmpty()) {
            logger.info("GUTTER_TRACKER: Found ${filesToReset.size} files to reset tracker for.")
            filesToReset.forEach {
                logger.debug("GUTTER_TRACKER: --> Resetting tracker for: ${it.path}")
                resetTracker(it)
            }
        }

        if (isEnabled && activeBranch != null) {
            val openFiles = FileEditorManager.getInstance(project).openFiles.toSet()
            val filesToSetThatAreOpen = filesToSet.intersect(openFiles)

            if (filesToSetThatAreOpen.isNotEmpty()) {
                logger.info("GUTTER_TRACKER: Found ${filesToSetThatAreOpen.size} open files to set tracker for (out of ${filesToSet.size} total).")
                filesToSetThatAreOpen.forEach { file ->
                    logger.debug("GUTTER_TRACKER: --> Setting tracker for open file: ${file.path}")
                    setTracker(file, activeBranch)
                }
            } else {
                logger.info("GUTTER_TRACKER: No currently open files need their trackers set or updated.")
            }
        }

        lastKnownTrackedFiles = currentFilesToTrack
        logger.info("GUTTER_TRACKER: Update complete. Now tracking ${lastKnownTrackedFiles.size} files.")
    }

    private fun applyTrackerStateToFile(file: VirtualFile) {
        if (project.isDisposed || !file.isValid) {
            logger.warn("GUTTER_TRACKER: applyTrackerStateToFile called but project disposed or file invalid: ${file.path}")
            return
        }

        val isEnabled = properties.getBoolean(
            ToolWindowSettingsProvider.APP_ENABLE_GUTTER_MARKERS_KEY,
            ToolWindowSettingsProvider.DEFAULT_ENABLE_GUTTER_MARKERS
        )

        if (!isEnabled) {
            logger.debug("GUTTER_TRACKER: applyTrackerStateToFile for ${file.path}, but feature is disabled. Ignoring.")
            return
        }

        val diffDataService = project.service<ProjectActiveDiffDataService>()
        val activeBranch = diffDataService.activeBranchName
        val filesToTrack = (diffDataService.createdFiles + diffDataService.modifiedFiles + diffDataService.movedFiles).toSet()

        if (activeBranch != null && file in filesToTrack) {
            logger.info("GUTTER_TRACKER: Opened file ${file.path} is part of the active diff set for branch '$activeBranch'. Setting custom tracker.")
            setTracker(file, activeBranch)
        } else {
            logger.debug("GUTTER_TRACKER: Opened file ${file.path} is not part of the active diff set. No custom tracker applied.")
        }
    }

    private fun setTracker(file: VirtualFile, branchName: String) {
        if (project.isDisposed || !file.isValid) {
            logger.warn("GUTTER_TRACKER: setTracker called but project disposed or file invalid: ${file.path}")
            return
        }

        logger.info("GUTTER_TRACKER: Setting tracker for file '${file.path}' against branch '$branchName'.")

        val gitService = project.service<GitService>()
        val diffDataService = project.service<ProjectActiveDiffDataService>()
        val isCreated = file in diffDataService.createdFiles.toSet()

        val vcsContentFuture = if (isCreated) {
            logger.debug("GUTTER_TRACKER: File is marked as CREATED. Using empty string as base content.")
            CompletableFuture.completedFuture("")
        } else {
            logger.debug("GUTTER_TRACKER: File is marked as MODIFIED/MOVED. Fetching content for revision '$branchName'.")
            gitService.getFileContentForRevision(branchName, file)
        }

        vcsContentFuture.whenCompleteAsync { content, throwable ->
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed || !file.isValid) {
                    logger.warn("GUTTER_TRACKER: Callback for setTracker on '${file.path}' invoked, but project/file is now invalid. Aborting.")
                    return@invokeLater
                }

                val document = FileDocumentManager.getInstance().getDocument(file)
                if (document == null) {
                    logger.error("GUTTER_TRACKER: Could not get Document for file ${file.path} to offer tracker content. Gutter marks will not appear.")
                    return@invokeLater
                }
                val manager = LineStatusTrackerManager.getInstanceImpl(project)

                if (throwable != null) {
                    logger.warn("GUTTER_TRACKER: Failed to get content for '${file.path}' in revision '$branchName'. Exception: ${throwable.javaClass.simpleName}, Message: ${throwable.message}")

                    // Check if the exception indicates the file doesn't exist in the target revision.
                    // This is expected for moved files or files that were added locally and not part of the base branch.
                    val isFileNotFoundError = throwable is VcsException &&
                            (throwable.message?.contains("does not exist in", ignoreCase = true) == true ||
                                    throwable.message?.contains("exists on disk, but not in", ignoreCase = true) == true) // Keep both for safety

                    if (isFileNotFoundError) {
                        logger.info("GUTTER_TRACKER: File ${file.path} not found in revision '$branchName'. Treating as a new file (offering empty content for diff). This is expected for moved/new files.")
                        manager.offerTrackerContent(document, "")
                    } else {
                        // It's a different, unexpected error (e.g., git command failed).
                        logger.error("GUTTER_TRACKER: An unexpected error occurred fetching file content. Resetting tracker for safety.", throwable)
                        resetTracker(file)
                    }
                } else {
                    // Success case: content was found, or the future completed with null (e.g., deleted file).
                    val finalContent = content ?: ""
                    logger.info("GUTTER_TRACKER: Successfully fetched VCS content for '${file.path}' from branch '$branchName'. Offering content of size ${finalContent.length} to tracker.")
                    manager.offerTrackerContent(document, finalContent)
                }
            }
        }
    }

    private fun resetTracker(file: VirtualFile) {
        if (project.isDisposed || !file.isValid) {
            logger.warn("GUTTER_TRACKER: resetTracker called but project disposed or file invalid: ${file.path}")
            return
        }
        logger.info("GUTTER_TRACKER: Resetting tracker for file by marking as dirty: ${file.path}")
        // Marking the file as dirty tells the VCS system to re-calculate its status from the default source (e.g., git index),
        // which will overwrite our custom-provided tracker content.
        VcsDirtyScopeManager.getInstance(project).fileDirty(file)
    }

    override fun dispose() {
        logger.info("GUTTER_TRACKER: Disposing for project '${project.name}'. Resetting all ${lastKnownTrackedFiles.size} known tracked files.")
        // On disposal, try to clean up by resetting all trackers we might have set.
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                lastKnownTrackedFiles.forEach { resetTracker(it) }
            }
        }
    }
}
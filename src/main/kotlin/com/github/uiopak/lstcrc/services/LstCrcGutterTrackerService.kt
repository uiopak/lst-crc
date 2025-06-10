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
import com.intellij.openapi.vcs.VcsApplicationSettings
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.ex.SimpleLocalLineStatusTracker
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.intellij.openapi.vfs.VirtualFile
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class LstCrcGutterTrackerService(private val project: Project) : Disposable {

    private val logger = thisLogger()
    private val properties = PropertiesComponent.getInstance()
    private val vcsAppSettings = VcsApplicationSettings.getInstance()
    private var wasNativeGutterEnabled: Boolean = true

    // The core of the new implementation: a map to hold and manage our custom trackers.
    private val managedTrackers = ConcurrentHashMap<VirtualFile, SimpleLocalLineStatusTracker>()

    init {
        logger.info("GUTTER_TRACKER: Initializing for project '${project.name}'")

        // Store the original state of the native gutter markers setting.
        wasNativeGutterEnabled = vcsAppSettings.SHOW_LST_GUTTER_MARKERS
        logger.info("GUTTER_TRACKER: Native VCS gutter marker initial state is: $wasNativeGutterEnabled")

        // If our feature is enabled by default, disable the native one to prevent conflicts.
        if (properties.getBoolean(ToolWindowSettingsProvider.APP_ENABLE_GUTTER_MARKERS_KEY, ToolWindowSettingsProvider.DEFAULT_ENABLE_GUTTER_MARKERS)) {
            logger.info("GUTTER_TRACKER: LST-CRC markers are enabled, disabling native VCS markers.")
            vcsAppSettings.SHOW_LST_GUTTER_MARKERS = false
        }

        val connection = project.messageBus.connect(this)

        // Listen for data changes to update trackers.
        connection.subscribe(DIFF_DATA_CHANGED_TOPIC, object : ActiveDiffDataChangedListener {
            override fun onDiffDataChanged() {
                logger.debug("GUTTER_TRACKER: Received onDiffDataChanged event. Scheduling tracker update for all open files.")
                updateAllOpenFiles("onDiffDataChanged")
            }
        })

        // Listen for file open/close events to manage tracker lifecycle.
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                logger.debug("GUTTER_TRACKER: File opened: ${file.path}. Applying tracker state.")
                applyTrackerStateToFile(file, "fileOpened")
            }

            override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
                logger.debug("GUTTER_TRACKER: File closed: ${file.path}. Releasing tracker.")
                releaseTracker(file)
            }
        })

        // Initial update for any files that are already open when the project starts.
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                logger.debug("GUTTER_TRACKER: Performing initial gutter tracker update for already-open files.")
                updateAllOpenFiles("initialization")
            }
        }
    }

    /**
     * Called from settings panel when the user toggles the gutter marker feature.
     */
    fun settingsChanged() {
        logger.info("GUTTER_TRACKER: Gutter marker setting changed. Updating native setting and all trackers.")
        val isOurFeatureEnabled = properties.getBoolean(ToolWindowSettingsProvider.APP_ENABLE_GUTTER_MARKERS_KEY, ToolWindowSettingsProvider.DEFAULT_ENABLE_GUTTER_MARKERS)

        if (isOurFeatureEnabled) {
            // Our feature is ON. If native is on, disable it.
            if (vcsAppSettings.SHOW_LST_GUTTER_MARKERS) {
                wasNativeGutterEnabled = true // Remember it was on
                vcsAppSettings.SHOW_LST_GUTTER_MARKERS = false
                logger.info("GUTTER_TRACKER: Turned ON. Disabling native gutter markers.")
            }
        } else {
            // Our feature is OFF. Restore native to its original state.
            if (!vcsAppSettings.SHOW_LST_GUTTER_MARKERS) {
                vcsAppSettings.SHOW_LST_GUTTER_MARKERS = wasNativeGutterEnabled
                logger.info("GUTTER_TRACKER: Turned OFF. Restoring native gutter markers to: $wasNativeGutterEnabled")
            }
        }

        updateAllOpenFiles("settingsChanged")
    }

    /**
     * Iterates all currently open files and applies the correct tracker state to them.
     */
    private fun updateAllOpenFiles(reason: String) {
        if (project.isDisposed) return
        logger.debug("GUTTER_TRACKER: Updating all open files, reason: $reason")
        FileEditorManager.getInstance(project).openFiles.forEach {
            applyTrackerStateToFile(it, reason)
        }
    }

    /**
     * The core logic: for a given file, decide if it should have our custom tracker or not,
     * and apply that state.
     */
    private fun applyTrackerStateToFile(file: VirtualFile, reason: String) {
        if (project.isDisposed || !file.isValid) return

        val isOurFeatureEnabled = properties.getBoolean(ToolWindowSettingsProvider.APP_ENABLE_GUTTER_MARKERS_KEY, ToolWindowSettingsProvider.DEFAULT_ENABLE_GUTTER_MARKERS)
        val diffDataService = project.service<ProjectActiveDiffDataService>()
        val activeBranch = diffDataService.activeBranchName

        val filesThatShouldHaveOurTracker = if (isOurFeatureEnabled && activeBranch != null) {
            (diffDataService.createdFiles + diffDataService.modifiedFiles + diffDataService.movedFiles).toSet()
        } else {
            emptySet()
        }

        if (file in filesThatShouldHaveOurTracker) {
            // This file should be tracked by us.
            logger.debug("GUTTER_TRACKER: [${reason}] File ${file.path} requires custom tracker for branch '$activeBranch'.")
            getOrCreateTracker(file, activeBranch!!)
        } else {
            // This file should not be tracked by us. Release our tracker if it exists.
            logger.debug("GUTTER_TRACKER: [${reason}] File ${file.path} does not require custom tracker. Releasing if present.")
            releaseTracker(file)
        }
    }

    /**
     * Ensures a custom tracker exists for the file and updates its base revision content.
     */
    private fun getOrCreateTracker(file: VirtualFile, branchName: String) {
        val existingTracker = managedTrackers[file]
        if (existingTracker != null) {
            // Tracker exists, just update it with potentially new content.
            updateBaseRevision(existingTracker, file, branchName)
        } else {
            // Creation must be on EDT as it interacts with Document.
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed || !file.isValid || managedTrackers.containsKey(file)) return@invokeLater

                val document = FileDocumentManager.getInstance().getDocument(file)
                if (document == null) {
                    logger.warn("GUTTER_TRACKER: Could not get document for ${file.path}. Cannot create tracker.")
                    return@invokeLater
                }

                logger.info("GUTTER_TRACKER: Creating new SimpleLocalLineStatusTracker for ${file.path}.")
                try {
                    val newTracker = SimpleLocalLineStatusTracker.createTracker(project, document, file)
                    managedTrackers[file] = newTracker
                    updateBaseRevision(newTracker, file, branchName)
                } catch (e: Exception) {
                    logger.error("GUTTER_TRACKER: Failed to create SimpleLocalLineStatusTracker for ${file.path}. This API might not be available. Disabling feature to prevent further errors.", e)
                    properties.setValue(ToolWindowSettingsProvider.APP_ENABLE_GUTTER_MARKERS_KEY, false, ToolWindowSettingsProvider.DEFAULT_ENABLE_GUTTER_MARKERS)
                    settingsChanged()
                }
            }
        }
    }

    /**
     * Fetches the file content from the target branch and applies it to the tracker's base revision.
     */
    private fun updateBaseRevision(tracker: SimpleLocalLineStatusTracker, file: VirtualFile, branchName: String) {
        val gitService = project.service<GitService>()
        val diffDataService = project.service<ProjectActiveDiffDataService>()
        val isCreated = file in diffDataService.createdFiles.toSet()

        val vcsContentFuture = if (isCreated) {
            logger.debug("GUTTER_TRACKER: File '${file.path}' is new, using empty base content.")
            CompletableFuture.completedFuture("")
        } else {
            logger.debug("GUTTER_TRACKER: Fetching base content for '${file.path}' from revision '$branchName'.")
            gitService.getFileContentForRevision(branchName, file)
        }

        vcsContentFuture.whenComplete { content, throwable ->
            // setBaseRevision must be called on the EDT.
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed || !file.isValid || tracker.isReleased) {
                    logger.warn("GUTTER_TRACKER: updateBaseRevision callback invoked, but state is now invalid. Aborting.")
                    return@invokeLater
                }

                val finalContent: String
                if (throwable != null) {
                    val isFileNotFoundError = throwable is VcsException &&
                            (throwable.message?.contains("does not exist in", ignoreCase = true) == true ||
                                    throwable.message?.contains("exists on disk, but not in", ignoreCase = true) == true)

                    finalContent = if (isFileNotFoundError) {
                        logger.info("GUTTER_TRACKER: File ${file.path} not found in revision '$branchName'. Treating as a new file (empty base content).")
                        ""
                    } else {
                        logger.error("GUTTER_TRACKER: Failed to get content for '${file.path}', revision '$branchName'.", throwable)
                        "" // Use empty content on unexpected error to be safe.
                    }
                } else {
                    finalContent = content ?: ""
                    logger.info("GUTTER_TRACKER: Successfully fetched content (size ${finalContent.length}) for '${file.path}'. Setting base revision.")
                }
                tracker.setBaseRevision(finalContent)
            }
        }
    }

    /**
     * Removes and disposes our custom tracker for a file, allowing the default VCS tracker to take over.
     */
    private fun releaseTracker(file: VirtualFile) {
        val tracker = managedTrackers.remove(file)
        if (tracker != null) {
            // Disposal and manager interaction must be on EDT.
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                logger.info("GUTTER_TRACKER: Releasing custom tracker for ${file.path}")
                tracker.release()

                // After disposing our tracker, we must tell the manager to re-evaluate the file.
                // This will cause it to create a default tracker if the native feature is enabled.
                val document = FileDocumentManager.getInstance().getDocument(file)
                if (document != null && file.isValid) {
                    LineStatusTrackerManager.getInstance(project).requestTrackerFor(document, file)
                }
            }
        }
    }

    override fun dispose() {
        logger.info("GUTTER_TRACKER: Disposing for project '${project.name}'.")
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater

            // Release all managed trackers.
            managedTrackers.keys.toList().forEach { releaseTracker(it) }
            managedTrackers.clear()

            // Restore the native VCS setting to its original state.
            if (!vcsAppSettings.SHOW_LST_GUTTER_MARKERS) {
                vcsAppSettings.SHOW_LST_GUTTER_MARKERS = wasNativeGutterEnabled
                logger.info("GUTTER_TRACKER: Native LST gutter markers restored to '$wasNativeGutterEnabled' on dispose.")
            }
        }
    }
}
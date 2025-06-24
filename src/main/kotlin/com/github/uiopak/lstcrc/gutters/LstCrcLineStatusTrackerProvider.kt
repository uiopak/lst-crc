package com.github.uiopak.lstcrc.gutters

import com.github.uiopak.lstcrc.services.GitService
import com.github.uiopak.lstcrc.services.ProjectActiveDiffDataService
import com.github.uiopak.lstcrc.toolWindow.ToolWindowSettingsProvider
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.ex.LocalLineStatusTracker
import com.intellij.openapi.vcs.ex.SimpleLocalLineStatusTracker
import com.intellij.openapi.vcs.impl.LineStatusTrackerContentLoader
import com.intellij.openapi.vfs.VirtualFile
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.ExecutionException

/**
 * Provides a custom [LocalLineStatusTracker] for files affected by the current LST-CRC comparison.
 * This is the idiomatic way to integrate custom gutter markers with the IDE's VCS subsystem.
 * It's registered as an extension point and queried by the [com.intellij.openapi.vcs.impl.LineStatusTrackerManager].
 */
class LstCrcLineStatusTrackerProvider : LineStatusTrackerContentLoader {
    private val logger = thisLogger()
    private val properties = PropertiesComponent.getInstance()

    private fun isOurGutterMarkersEnabled(): Boolean =
        properties.getBoolean(ToolWindowSettingsProvider.APP_ENABLE_GUTTER_MARKERS_KEY, ToolWindowSettingsProvider.DEFAULT_ENABLE_GUTTER_MARKERS)

    /**
     * The main gatekeeper. It decides if our provider should handle a file at all.
     */
    override fun isTrackedFile(project: Project, file: VirtualFile): Boolean {
        // 1. If our entire feature is turned off, do nothing.
        if (!isOurGutterMarkersEnabled()) return false

        val diffDataService = project.service<ProjectActiveDiffDataService>()
        val branchName = diffDataService.activeBranchName
        val isOnHeadTab = (branchName == "HEAD" || branchName == null) // HEAD tab or cleared state

        val includeHeadInScopes = properties.getBoolean(
            ToolWindowSettingsProvider.APP_INCLUDE_HEAD_IN_SCOPES_KEY,
            ToolWindowSettingsProvider.DEFAULT_INCLUDE_HEAD_IN_SCOPES
        )

        // 2. The critical "yield" condition: If we are on the HEAD tab and the setting to include
        //    HEAD changes is OFF, we must return false to let the native VCS tracker take over.
        if (isOnHeadTab && !includeHeadInScopes) {
            return false
        }

        // 3. In all other cases (on a custom branch tab, or on HEAD tab with the setting enabled),
        //    we are the authority. We claim any versioned file to either show our diff or suppress the native one.
        val status = FileStatusManager.getInstance(project).getStatus(file)
        return status != FileStatus.UNKNOWN && status != FileStatus.IGNORED
    }

    /**
     * We identify trackers created by our provider by checking if they are an instance of
     * the platform's SimpleLocalLineStatusTracker, which we create in createTracker.
     */
    override fun isMyTracker(tracker: LocalLineStatusTracker<*>): Boolean = tracker is SimpleLocalLineStatusTracker

    /**
     * Creates an instance of the platform's [SimpleLocalLineStatusTracker]. The IntelliJ
     * LineStatusTrackerManager will associate this tracker instance with our provider.
     * Note: This uses an internal, but necessary, platform class.
     */
    override fun createTracker(project: Project, file: VirtualFile): LocalLineStatusTracker<*>? {
        val document: Document = FileDocumentManager.getInstance().getDocument(file) ?: return null
        logger.debug("GUTTER_PROVIDER: Creating tracker for ${file.path}")
        return SimpleLocalLineStatusTracker(project, document, file)
    }

    // --- LineStatusTrackerContentLoader implementation ---

    private data class LstCrcContentInfo(
        val diffSessionId: UUID,
        val branchName: String?,
        val charset: Charset,
        val virtualFile: VirtualFile,
        val shouldShowOurDiff: Boolean
    ) : LineStatusTrackerContentLoader.ContentInfo

    private data class LstCrcTrackerContent(val content: CharSequence) : LineStatusTrackerContentLoader.TrackerContent

    /**
     * Provides information about the base revision content we need. Here we decide if the file
     * is part of our diff and pass that information to `loadContent`.
     */
    override fun getContentInfo(project: Project, file: VirtualFile): LineStatusTrackerContentLoader.ContentInfo? {
        val diffDataService = project.service<ProjectActiveDiffDataService>()
        val branchName = diffDataService.activeBranchName
        val sessionId = diffDataService.diffSessionId

        val showForNewFiles = properties.getBoolean(
            ToolWindowSettingsProvider.APP_ENABLE_GUTTER_FOR_NEW_FILES_KEY,
            ToolWindowSettingsProvider.DEFAULT_ENABLE_GUTTER_FOR_NEW_FILES
        )
        val trackedFiles = (diffDataService.modifiedFiles + diffDataService.movedFiles).toMutableSet()
        if (showForNewFiles) {
            trackedFiles.addAll(diffDataService.createdFiles)
        }
        val shouldShowOurDiff = branchName != null && file in trackedFiles

        return LstCrcContentInfo(sessionId, branchName, file.charset, file, shouldShowOurDiff)
    }

    /**
     * Force a refresh if any relevant property of our content info has changed.
     * Relying on the data class `equals` method is a clean and robust way to check this.
     */
    override fun shouldBeUpdated(oldInfo: LineStatusTrackerContentLoader.ContentInfo?, newInfo: LineStatusTrackerContentLoader.ContentInfo): Boolean {
        if (newInfo !is LstCrcContentInfo) return true // Should not happen
        if (oldInfo == null || oldInfo !is LstCrcContentInfo) return true

        // Let the auto-generated `equals` of the data class determine if an update is needed.
        // This will correctly compare diffSessionId, branchName, AND shouldShowOurDiff.
        return oldInfo != newInfo
    }

    /**
     * Loads the base revision content. This is run on a background thread.
     */
    override fun loadContent(project: Project, info: LineStatusTrackerContentLoader.ContentInfo): LineStatusTrackerContentLoader.TrackerContent? {
        info as LstCrcContentInfo
        val rawContent: CharSequence?

        // Case 1: The file is part of our active diff. Load content from the target Git revision.
        if (info.shouldShowOurDiff) {
            logger.debug("GUTTER_PROVIDER: File ${info.virtualFile.path} is in our diff. Loading from Git revision '${info.branchName}'.")
            val isNewFile = project.service<ProjectActiveDiffDataService>().createdFiles.contains(info.virtualFile)

            if (isNewFile) {
                logger.debug("GUTTER_PROVIDER: File is new, using empty base content.")
                rawContent = ""
            } else {
                val gitService = project.service<GitService>()
                rawContent = try {
                    val contentFuture = gitService.getFileContentForRevision(info.branchName!!, info.virtualFile)
                    contentFuture.get() ?: ""
                } catch (e: ExecutionException) {
                    val cause = e.cause
                    if (cause is VcsException && cause.message.contains("does not exist in", ignoreCase = true)) {
                        logger.info("GUTTER_PROVIDER: File ${info.virtualFile.path} not found in revision '${info.branchName}'. Treating as a new file (empty base content).")
                        ""
                    } else {
                        logger.warn("GUTTER_PROVIDER: Failed to load content for ${info.virtualFile.path} from revision ${info.branchName}", e)
                        return null // This will trigger handleError.
                    }
                } catch (e: Exception) {
                    if (e is InterruptedException) Thread.currentThread().interrupt()
                    logger.error("GUTTER_PROVIDER: Unexpected error loading Git content for ${info.virtualFile.path}", e)
                    return null
                }
            }
        }
        // Case 2: The file is NOT in our active diff. Load its current content to produce an empty diff.
        else {
            logger.debug("GUTTER_PROVIDER: File ${info.virtualFile.path} is NOT in our diff. Suppressing native markers.")
            rawContent = try {
                // This call requires read access.
                runReadAction {
                    FileDocumentManager.getInstance().getDocument(info.virtualFile)?.text
                }
            } catch (e: Exception) {
                if (e is InterruptedException) Thread.currentThread().interrupt()
                logger.error("GUTTER_PROVIDER: Failed to read current content for ${info.virtualFile.path} to suppress markers.", e)
                return null
            }
        }

        if (rawContent == null) {
            logger.warn("GUTTER_PROVIDER: Could not get content for ${info.virtualFile.path}")
            return null
        }

        // CRITICAL: Always normalize line separators before returning.
        return LstCrcTrackerContent(StringUtil.convertLineSeparators(rawContent))
    }

    override fun setLoadedContent(tracker: LocalLineStatusTracker<*>, content: LineStatusTrackerContentLoader.TrackerContent) {
        // Cast to the concrete internal type to access the necessary 'setBaseRevision' method.
        (tracker as? SimpleLocalLineStatusTracker)?.setBaseRevision((content as LstCrcTrackerContent).content)
        logger.debug("GUTTER_PROVIDER: Successfully set base revision for ${tracker.virtualFile.path}")
    }

    override fun handleLoadingError(tracker: LocalLineStatusTracker<*>) {
        // To clear the diff, we set the base revision to the current document content.
        // We must cast to the concrete type to access the method.
        val currentContent = runReadAction { tracker.document.text }
        (tracker as? SimpleLocalLineStatusTracker)?.setBaseRevision(StringUtil.convertLineSeparators(currentContent))
        logger.warn("GUTTER_PROVIDER: Cleared diff for ${tracker.virtualFile.path} due to loading error by setting empty diff.")
    }
}
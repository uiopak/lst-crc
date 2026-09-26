package com.github.uiopak.lstcrc.gutters

import com.github.uiopak.lstcrc.messaging.ActiveDiffDataChangedListener
import com.github.uiopak.lstcrc.messaging.DIFF_DATA_CHANGED_TOPIC
import com.github.uiopak.lstcrc.services.GitService
import com.github.uiopak.lstcrc.services.ProjectActiveDiffDataService
import com.github.uiopak.lstcrc.toolWindow.ToolWindowSettingsProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project

import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.ex.LineStatusTracker
import com.intellij.openapi.vcs.ex.LocalLineStatusTracker
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker
import com.intellij.openapi.vcs.ex.Range
import com.intellij.openapi.vcs.ex.SimpleLocalLineStatusTracker
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.intellij.openapi.vfs.VirtualFile
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import java.util.concurrent.ConcurrentHashMap

private val VISIBLE_MODE = LocalLineStatusTracker.Mode(
    isVisible = true,
    showErrorStripeMarkers = true,
    detectWhitespaceChangedLines = true
)

data class VisualTrackerDispatchers(
    val background: CoroutineDispatcher = Dispatchers.Default,
    val io: CoroutineDispatcher = Dispatchers.IO,
    val ui: CoroutineContext = Dispatchers.EDT
)

@Service(Service.Level.PROJECT)
class VisualTrackerManager(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
    private val dispatchers: VisualTrackerDispatchers
) : Disposable {

    @Suppress("unused")
    constructor(project: Project, coroutineScope: CoroutineScope) : this(
        project,
        coroutineScope,
        VisualTrackerDispatchers()
    )

    private val logger = thisLogger()
    private val visualTrackers = ConcurrentHashMap<Document, SimpleLocalLineStatusTracker>()
    private val updateJobs = ConcurrentHashMap<Document, Job>()

    /**
     * The target revision each visual tracker's base content was loaded for (or is loading).
     * Lets tab switches and edit-driven refreshes skip `git show` when nothing relevant changed.
     * Cleared on any repository change, because a branch or HEAD may now point elsewhere.
     */
    private val loadedRevisions = ConcurrentHashMap<Document, String>()

    private fun isExpectedMissingFileInRevision(message: String?): Boolean =
        message != null && (
            message.contains("does not exist in", ignoreCase = true) ||
                message.contains("exists on disk, but not in", ignoreCase = true)
            )

    fun init() {
        val busConnection = project.messageBus.connect(this)

        // Listen for new native trackers
        busConnection.subscribe(LineStatusTrackerManager.TOPIC, object : LineStatusTrackerManager.Listener {
            override fun onTrackerAdded(tracker: LineStatusTracker<*>) {
                if (tracker !is LocalLineStatusTracker<*>) return
                maybeInterceptTracker(tracker)
            }

            override fun onTrackerRemoved(tracker: LineStatusTracker<*>) {
                if (releaseVisualTracker(tracker.document)) {
                    logger.debug { "VISUAL_TRACKER: Native tracker removed for ${tracker.virtualFile.name}. Released visual tracker." }
                }
            }
        })

        busConnection.subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener {
            loadedRevisions.clear()
        })

        // Listen for Diff Data changes (Tab switching)
        busConnection.subscribe(DIFF_DATA_CHANGED_TOPIC, object : ActiveDiffDataChangedListener {
            override fun onDiffDataChanged() {
                refreshAllTrackers()
            }
        })

        busConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            // Only the editors on screen need a check now; others are checked when they are selected.
            override fun selectionChanged(event: FileEditorManagerEvent) {
                refreshAllTrackers(visibleOnly = true)
            }
        })

    }

    /**
     * Called from settings when the user toggles any gutter marker feature.
     * Re-evaluates all trackers (to install/remove visual overlays) and
     * triggers a global file status refresh so the IDE updates its UI.
     */
    fun settingsChanged() {
        logger.debug { "VISUAL_TRACKER: Gutter marker setting changed. Refreshing trackers and file statuses." }
        refreshAllTrackers()
        refreshFileStatuses()
    }

    @Suppress("unused")
    fun findStandaloneTracker(document: Document): LocalLineStatusTracker<*>? = visualTrackers[document]

    /** Gutter highlighters and tracker ranges of [document], for the UI tests (both suites parse this string). */
    @Suppress("unused")
    fun debugGutterSummaryFor(document: Document): String {
        val markupModel = DocumentMarkupModel.forDocument(document, project, true) as MarkupModelEx
        val highlighters = markupModel.allHighlighters.mapNotNull { highlighter ->
            val renderer: Any = highlighter.gutterIconRenderer ?: highlighter.lineMarkerRenderer ?: return@mapNotNull null
            val startOffset = highlighter.startOffset
            val endLine = document.getLineNumber(maxOf(highlighter.endOffset, startOffset + 1) - 1) + 1
            "${document.getLineNumber(startOffset)}-$endLine:${renderer.javaClass.simpleName.ifEmpty { renderer.javaClass.name }}"
        }
        val tracker = LineStatusTrackerManager.getInstance(project).getLineStatusTracker(document) as? LocalLineStatusTracker<*>
            ?: visualTrackers[document]
        val trackerSummary = tracker?.let {
            val ranges = runCatching { it.getRanges().orEmpty().map { range -> "${range.line1}-${range.line2}:${trackerRangeTypeName(range.type)}" } }
                .getOrDefault(emptyList())
            "${it.javaClass.simpleName}|visible=${it.mode.isVisible}|ranges=${ranges.joinToString(",")}"
        } ?: "tracker=none"
        return "${highlighters.joinToString(",")}|highlighters=${highlighters.size}|$trackerSummary"
    }

    private fun refreshFileStatuses() {
        if (project.isDisposed) return
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            FileStatusManager.getInstance(project).fileStatusesChanged()
        }
    }

    private fun trackerRangeTypeName(type: Byte): String = when (type) {
        Range.MODIFIED -> "MODIFIED"
        Range.INSERTED -> "INSERTED"
        Range.DELETED -> "DELETED"
        else -> "UNKNOWN"
    }

    /**
     * Re-evaluates all active native trackers to decide if we should Intercept or Yield.
     * Handles transitions:
     * - Native -> Visual (Create Visual, Hide Native)
     * - Visual -> Visual (Update Content)
     * - Visual -> Native (Dispose Visual, Restore Native)
     *
     * With [visibleOnly], only the selected editor of each split is checked.
     */
    private fun refreshAllTrackers(visibleOnly: Boolean = false) {
        if (project.isDisposed) return

        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater

            val editors = if (visibleOnly) {
                FileEditorManager.getInstance(project).selectedEditors.filterIsInstance<TextEditor>().map { it.editor }
            } else {
                EditorFactory.getInstance().allEditors.toList()
            }
            val documents = editors.map { it.document }.distinct()
            val gutterEnabled = ToolWindowSettingsProvider.isGutterMarkersEnabled()
            coroutineScope.launch(dispatchers.background) {
                documents.forEach { document ->
                    refreshTracker(document, gutterEnabled)
                }
            }
        }
    }

    private fun maybeInterceptTracker(nativeTracker: LocalLineStatusTracker<*>) {
        if (!ToolWindowSettingsProvider.isGutterMarkersEnabled()) return

        val file = nativeTracker.virtualFile
        if (nativeTracker !is PartialLocalLineStatusTracker) return

        coroutineScope.launch(dispatchers.background) {
            val targetRevision = resolveTargetRevision(file) ?: return@launch
            withContext(dispatchers.ui) {
                if (!project.isDisposed) {
                    performInterception(nativeTracker, targetRevision)
                }
            }
        }
    }

    private suspend fun refreshTracker(
        document: Document,
        gutterEnabled: Boolean
    ) {
        val nativeTracker = LineStatusTrackerManager.getInstance(project).getLineStatusTracker(document) as? LocalLineStatusTracker<*>
        val file = nativeTracker?.virtualFile ?: FileDocumentManager.getInstance().getFile(document) ?: return
        val targetRevision = if (gutterEnabled) resolveTargetRevision(file) else null

        withContext(dispatchers.ui) {
            if (project.isDisposed) return@withContext

            if (targetRevision != null) {
                if (nativeTracker != null) {
                    performInterception(nativeTracker, targetRevision)
                } else {
                    ensureVisualTracker(document, file, targetRevision)
                }
            } else {
                if (nativeTracker != null) {
                    restoreNativeTracker(nativeTracker)
                } else if (releaseVisualTracker(document)) {
                    logger.debug { "VISUAL_TRACKER: Released standalone visual tracker for ${file.name}." }
                }
            }
        }
    }

    private fun restoreNativeTracker(nativeTracker: LocalLineStatusTracker<*>) {
        // Only act if we actually had a visual tracker for this document.
        if (releaseVisualTracker(nativeTracker.document)) {
            logger.debug { "VISUAL_TRACKER: Restored native tracker for ${nativeTracker.virtualFile.name}." }
            nativeTracker.mode = VISIBLE_MODE
        }
    }

    /** Cancels any pending load and releases the visual tracker for [document]. Returns true if one existed. */
    private fun releaseVisualTracker(document: Document): Boolean {
        updateJobs.remove(document)?.cancel()
        loadedRevisions.remove(document)
        val visualTracker = visualTrackers.remove(document) ?: return false
        visualTracker.release()
        return true
    }

    /** The revision [file]'s visual tracker compares against, or null to leave it to the native tracker. */
    private fun resolveTargetRevision(file: VirtualFile): String? {
        val diffDataService = project.service<ProjectActiveDiffDataService>()
        val repository = project.service<GitService>().getRepositoryForFile(file)
        val targetRevision = repository?.let { diffDataService.activeComparisonContext[it.root.path] ?: diffDataService.activeBranchName }
        if (repository == null || targetRevision == null) {
            logger.debug { "VISUAL_TRACKER: Yielding. Target revision is null for ${file.name}." }
            return null
        }

        if (shouldSkipTrackerForCurrentRevision(repository, targetRevision)) {
            return null
        }

        if (shouldSkipTrackerForNewFile(diffDataService, file)) {
            return null
        }

        return targetRevision
    }

    private fun shouldSkipTrackerForCurrentRevision(repository: GitRepository, targetRevision: String): Boolean {
        val isTargetSameAsCurrent = targetRevision == repository.currentBranchName ||
            targetRevision == repository.currentRevision ||
            targetRevision == "HEAD"
        return isTargetSameAsCurrent && !ToolWindowSettingsProvider.isIncludeHeadInScopes()
    }

    private fun shouldSkipTrackerForNewFile(
        diffDataService: ProjectActiveDiffDataService,
        file: VirtualFile
    ): Boolean = !ToolWindowSettingsProvider.isGutterForNewFilesEnabled() &&
        file.path in diffDataService.createdFilePaths

    private fun createVisualTracker(document: Document, file: VirtualFile): SimpleLocalLineStatusTracker {
        val tracker = SimpleLocalLineStatusTracker.createTracker(project, document, file)
        val stableTracker: LocalLineStatusTracker<*> = tracker
        stableTracker.mode = VISIBLE_MODE
        return tracker
    }

    private fun performInterception(nativeTracker: LocalLineStatusTracker<*>, targetRevision: String) {
        val file = nativeTracker.virtualFile

        // 1. Hide Native Tracker (Idempotent)
        nativeTracker.mode = LocalLineStatusTracker.Mode(
            isVisible = false,
            showErrorStripeMarkers = false,
            detectWhitespaceChangedLines = false
        )

        ensureVisualTracker(nativeTracker.document, file, targetRevision)
    }

    /**
     * Makes sure [document] has a visual tracker whose base content is [targetRevision].
     * Skips the git lookup when that revision is already loaded or loading.
     */
    private fun ensureVisualTracker(document: Document, file: VirtualFile, targetRevision: String) {
        val visualTracker = visualTrackers.computeIfAbsent(document) {
            logger.debug { "VISUAL_TRACKER: Creating visual tracker for ${file.name}" }
            createVisualTracker(document, file)
        }
        if (loadedRevisions.put(document, targetRevision) == targetRevision) return

        updateJobs.remove(document)?.cancel()
        updateJobs[document] = coroutineScope.launch {
            try {
                val content = loadTargetContent(file, targetRevision)
                if (content == null) loadedRevisions.remove(document, targetRevision)
                val baseContent = content ?: readActionBlocking { document.text }
                withContext(dispatchers.ui) {
                    if (visualTrackers[document] === visualTracker) {
                        visualTracker.setBaseRevision(baseContent)
                    }
                }
            } finally {
                updateJobs.remove(document, coroutineContext[Job])
            }
        }
    }

    /**
     * Loads [file]'s content at [revision]. Returns "" when the file does not exist there,
     * or null when loading failed (the caller then shows no changes and retries next refresh).
     */
    private suspend fun loadTargetContent(file: VirtualFile, revision: String): CharSequence? {
        // A file that is new in the comparison has no content in the target revision.
        if (file.path in project.service<ProjectActiveDiffDataService>().createdFilePaths) {
            return ""
        }

        val gitService = project.service<GitService>()
        return withContext(dispatchers.io) {
            try {
                gitService.getFileContentForRevision(revision, file)
            } catch (e: Exception) {
                val vcsError = generateSequence<Throwable>(e) { it.cause }.firstOrNull { it is VcsException }
                if (isExpectedMissingFileInRevision(vcsError?.message)) {
                    ""
                } else {
                    logger.warn("VISUAL_TRACKER: Failed to load content for ${file.path}: ${(vcsError ?: e).message}")
                    null
                }
            }
        }
    }

    override fun dispose() {
        updateJobs.values.forEach { it.cancel() }
        updateJobs.clear()
        loadedRevisions.clear()
        visualTrackers.values.forEach { tracker ->
            try {
                tracker.release()
            } catch (e: Exception) {
                logger.warn("VISUAL_TRACKER: Failed to release tracker during dispose.", e)
            }
        }
        visualTrackers.clear()
    }
}

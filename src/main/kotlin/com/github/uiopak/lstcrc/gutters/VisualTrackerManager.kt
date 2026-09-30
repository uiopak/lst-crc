package com.github.uiopak.lstcrc.gutters

import com.github.uiopak.lstcrc.LstCrcConstants.HEAD
import com.github.uiopak.lstcrc.messaging.ActiveDiffDataChangedListener
import com.github.uiopak.lstcrc.messaging.DIFF_DATA_CHANGED_TOPIC
import com.github.uiopak.lstcrc.services.GitService
import com.github.uiopak.lstcrc.services.ProjectActiveDiffDataService
import com.github.uiopak.lstcrc.services.isFileMissingInRevision
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
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.text.StringUtil

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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

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
    private val disposed = AtomicBoolean(false)
    private val refreshGeneration = AtomicLong()
    // A newer visible-only request must carry any unfinished full refresh; identity protects newer requests.
    private val pendingFullRefresh = AtomicReference<Any?>()

    // Identity keeps an older failed load from clearing a newer load for the same revision and path.
    private class RevisionLoad(val key: Pair<String, String>, val generation: Long) {
        @Volatile var loaded = false
    }

    /**
     * The target revision and path each visual tracker's base content was loaded for (or is loading).
     * Lets tab switches and edit-driven refreshes skip `git show` when nothing relevant changed.
     * Cleared on any repository change, because a branch or HEAD may now point elsewhere.
     */
    private val loadedRevisions = ConcurrentHashMap<Document, RevisionLoad>()

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

        busConnection.subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener { onRepositoryChanged() })

        // Listen for Diff Data changes (Tab switching)
        busConnection.subscribe(DIFF_DATA_CHANGED_TOPIC, ActiveDiffDataChangedListener { refreshAllTrackers() })

        busConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            // Only the editors on screen need a check now; others are checked when they are selected.
            override fun selectionChanged(event: FileEditorManagerEvent) {
                // Keep a pending comparison/settings refresh of the other open editors valid.
                refreshAllTrackers(visibleOnly = true, invalidatePending = false)
            }

            // A standalone tracker has no native tracker whose removal would release it.
            override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
                if (!source.isFileOpen(file)) releaseStandaloneTracker(file)
            }
        })

    }

    /**
     * A branch or HEAD may now point elsewhere, so forget which revisions were loaded and re-check the editors on
     * screen (others are re-checked when selected). The refresh that follows only re-checks trackers when the diff
     * data changed, which it may not (same files, same line stats) even though the target moved.
     */
    internal fun onRepositoryChanged() {
        refreshGeneration.incrementAndGet()
        loadedRevisions.clear()
        refreshAllTrackers(visibleOnly = true, invalidatePending = false)
    }

    /**
     * Called from settings when the user toggles any gutter marker feature.
     * Re-evaluates all trackers (to install/remove visual overlays) and
     * triggers a global file status refresh so the IDE updates its UI.
     */
    fun settingsChanged() {
        logger.debug { "VISUAL_TRACKER: Gutter marker setting changed. Refreshing trackers and file statuses." }
        refreshAllTrackers()
        val generation = refreshGeneration.get()
        ApplicationManager.getApplication().invokeLater {
            if (isCurrentRefresh(generation)) FileStatusManager.getInstance(project).fileStatusesChanged()
        }
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
    private fun refreshAllTrackers(visibleOnly: Boolean = false, invalidatePending: Boolean = true) {
        if (disposed.get() || project.isDisposed) return
        if (!visibleOnly) pendingFullRefresh.set(Any())
        val generation = if (invalidatePending) refreshGeneration.incrementAndGet() else refreshGeneration.get()

        ApplicationManager.getApplication().invokeLater {
            if (!isCurrentRefresh(generation)) return@invokeLater

            val fullRefresh = pendingFullRefresh.get()
            val fileEditorManager = FileEditorManager.getInstance(project)
            val editors = if (visibleOnly && fullRefresh == null) {
                fileEditorManager.selectedEditors
            } else {
                fileEditorManager.allEditors
            }
            val documents = editors.filterIsInstance<TextEditor>().filterNot { it.editor.isDisposed }
                .map { it.editor.document }.distinct()
            val gutterEnabled = ToolWindowSettingsProvider.isGutterMarkersEnabled()
            coroutineScope.launch(dispatchers.background) {
                documents.forEach { document ->
                    if (!isCurrentRefresh(generation)) return@launch
                    refreshTracker(document, gutterEnabled, generation)
                }
                if (fullRefresh != null && isCurrentRefresh(generation)) {
                    pendingFullRefresh.compareAndSet(fullRefresh, null)
                }
            }
        }
    }

    private fun maybeInterceptTracker(nativeTracker: LocalLineStatusTracker<*>) {
        if (disposed.get() || !ToolWindowSettingsProvider.isGutterMarkersEnabled()) return

        val file = nativeTracker.virtualFile
        if (nativeTracker !is PartialLocalLineStatusTracker) return
        val generation = refreshGeneration.get()

        coroutineScope.launch(dispatchers.background) {
            val targetRevision = resolveTargetRevision(file) ?: return@launch
            withContext(dispatchers.ui) {
                if (canApplyRefresh(file, generation) &&
                    LineStatusTrackerManager.getInstance(project).getLineStatusTracker(nativeTracker.document) === nativeTracker) {
                    performInterception(nativeTracker, targetRevision, generation)
                }
            }
        }
    }

    private suspend fun refreshTracker(
        document: Document,
        gutterEnabled: Boolean,
        generation: Long
    ) {
        val file = FileDocumentManager.getInstance().getFile(document) ?: return
        val targetRevision = if (gutterEnabled) resolveTargetRevision(file) else null

        withContext(dispatchers.ui) {
            if (!canApplyRefresh(file, generation)) return@withContext
            // The native tracker may have been replaced while the target was resolved off the EDT.
            val nativeTracker = LineStatusTrackerManager.getInstance(project).getLineStatusTracker(document) as? LocalLineStatusTracker<*>

            if (targetRevision != null) {
                if (nativeTracker != null) {
                    performInterception(nativeTracker, targetRevision, generation)
                } else {
                    ensureVisualTracker(document, file, targetRevision, generation)
                }
            } else {
                if (nativeTracker != null) {
                    // Only restore the native tracker if we actually had a visual tracker for this document.
                    if (releaseVisualTracker(document)) {
                        logger.debug { "VISUAL_TRACKER: Restored native tracker for ${file.name}." }
                        nativeTracker.mode = VISIBLE_MODE
                    }
                } else if (releaseVisualTracker(document)) {
                    logger.debug { "VISUAL_TRACKER: Released standalone visual tracker for ${file.name}." }
                }
            }
        }
    }

    private fun isCurrentRefresh(generation: Long): Boolean =
        !disposed.get() && !project.isDisposed && refreshGeneration.get() == generation

    /** Checked on the EDT, immediately before installing, releasing or hiding a tracker. */
    private fun canApplyRefresh(file: VirtualFile, generation: Long): Boolean =
        isCurrentRefresh(generation) && file.isValid && FileEditorManager.getInstance(project).isFileOpen(file)

    /** Releases [file]'s visual tracker when the platform does not track the file (after its last editor closed). */
    internal fun releaseStandaloneTracker(file: VirtualFile) {
        val document = FileDocumentManager.getInstance().getCachedDocument(file) ?: return
        if (LineStatusTrackerManager.getInstance(project).getLineStatusTracker(document) != null) return
        if (releaseVisualTracker(document)) {
            logger.debug { "VISUAL_TRACKER: Released standalone visual tracker for closed ${file.name}." }
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

        // The native tracker already compares against the current revision, unless HEAD is included in scopes.
        val isTargetSameAsCurrent = targetRevision == repository.currentBranchName ||
            targetRevision == repository.currentRevision ||
            targetRevision == HEAD
        if (isTargetSameAsCurrent && !ToolWindowSettingsProvider.isIncludeHeadInScopes()) return null

        if (!ToolWindowSettingsProvider.isGutterForNewFilesEnabled() && file.path in diffDataService.createdFilePaths) return null

        return targetRevision
    }

    private fun performInterception(nativeTracker: LocalLineStatusTracker<*>, targetRevision: String, generation: Long) {
        val file = nativeTracker.virtualFile

        // 1. Hide Native Tracker (Idempotent)
        nativeTracker.mode = LocalLineStatusTracker.Mode(
            isVisible = false,
            showErrorStripeMarkers = false,
            detectWhitespaceChangedLines = false
        )

        ensureVisualTracker(nativeTracker.document, file, targetRevision, generation)
    }

    /**
     * Makes sure [document] has a visual tracker whose base content is [targetRevision].
     * Skips the git lookup when that revision is already loaded or loading.
     */
    internal fun ensureVisualTracker(document: Document, file: VirtualFile, targetRevision: String) {
        ensureVisualTracker(document, file, targetRevision, refreshGeneration.get())
    }

    private fun ensureVisualTracker(
        document: Document,
        file: VirtualFile,
        targetRevision: String,
        generation: Long
    ) {
        if (!isCurrentRefresh(generation)) return
        val visualTracker = visualTrackers.computeIfAbsent(document) {
            logger.debug { "VISUAL_TRACKER: Creating visual tracker for ${file.name}" }
            val tracker = SimpleLocalLineStatusTracker.createTracker(project, document, file)
            val localTracker: LocalLineStatusTracker<*> = tracker // `mode` is set through the base type
            localTracker.mode = VISIBLE_MODE
            tracker
        }
        // A moved file is compared with its old path, which is where the target has it.
        val pathInTarget = project.service<ProjectActiveDiffDataService>().pathInTarget(file.path)
        val loadKey = targetRevision to pathInTarget
        val revisionLoad = RevisionLoad(loadKey, generation)
        val activeLoad = loadedRevisions.compute(document) { _, current ->
            current?.takeIf { it.key == loadKey && (it.loaded || it.generation == generation) } ?: revisionLoad
        }
        if (activeLoad !== revisionLoad) return

        updateJobs.remove(document)?.cancel()
        val job = coroutineScope.launch {
            val content = loadTargetContent(file, targetRevision, pathInTarget)
            if (content == null) loadedRevisions.remove(document, revisionLoad)
            val baseContent = content ?: readActionBlocking { document.text }
            withContext(dispatchers.ui) {
                if (isCurrentRefresh(generation) && visualTrackers[document] === visualTracker) {
                    // Setting even identical text freezes the tracker and refreshes its highlighters.
                    if (!visualTracker.isInitialized || !StringUtil.equals(visualTracker.vcsDocument.immutableCharSequence, baseContent)) {
                        visualTracker.setBaseRevision(baseContent)
                    }
                    revisionLoad.loaded = content != null
                }
            }
        }
        updateJobs[document] = job
        // Also runs when cancellation happens before the coroutine body starts, or it completes before assignment.
        job.invokeOnCompletion { failure ->
            if (failure != null || !revisionLoad.loaded) loadedRevisions.remove(document, revisionLoad)
            updateJobs.remove(document, job)
        }
    }

    /**
     * Loads [file]'s content at [revision], where it is at [pathInTarget]. Returns "" when the file does not
     * exist there, or null when loading failed (the caller then shows no changes and retries next refresh).
     */
    private suspend fun loadTargetContent(file: VirtualFile, revision: String, pathInTarget: String): CharSequence? {
        // A file that is new in the comparison has no content in the target revision.
        if (file.path in project.service<ProjectActiveDiffDataService>().createdFilePaths) {
            return ""
        }

        val gitService = project.service<GitService>()
        return withContext(dispatchers.io) {
            try {
                gitService.getFileContentForRevision(revision, file, pathInTarget)
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The file does not exist in the target revision: compare against empty content.
                if (isFileMissingInRevision(e)) {
                    ""
                } else {
                    val vcsError = generateSequence<Throwable>(e) { it.cause }.firstOrNull { it is VcsException }
                    logger.warn("VISUAL_TRACKER: Failed to load content for ${file.path}: ${(vcsError ?: e).message}")
                    null
                }
            }
        }
    }

    override fun dispose() {
        if (!disposed.compareAndSet(false, true)) return
        refreshGeneration.incrementAndGet()
        pendingFullRefresh.set(null)
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

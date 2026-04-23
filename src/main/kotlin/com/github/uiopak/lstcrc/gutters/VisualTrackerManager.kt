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
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer

import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.ex.LineStatusTracker
import com.intellij.openapi.vcs.ex.LocalLineStatusTracker
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker
import com.intellij.openapi.vcs.ex.SimpleLocalLineStatusTracker
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.intellij.openapi.vfs.VirtualFile
import git4idea.repo.GitRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import java.util.concurrent.ConcurrentHashMap

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

    private data class TargetRevisionContext(
        val diffDataService: ProjectActiveDiffDataService,
        val repository: GitRepository,
        val targetRevision: String
    )

    fun init() {
        val busConnection = project.messageBus.connect(this)

        // Listen for new native trackers
        busConnection.subscribe(LineStatusTrackerManager.TOPIC, object : LineStatusTrackerManager.Listener {
            override fun onTrackerAdded(tracker: LineStatusTracker<*>) {
                if (tracker !is LocalLineStatusTracker<*>) return
                maybeInterceptTracker(tracker)
            }
        })

        // Listen for Diff Data changes (Tab switching)
        busConnection.subscribe(DIFF_DATA_CHANGED_TOPIC, object : ActiveDiffDataChangedListener {
            override fun onDiffDataChanged() {
                refreshAllTrackers()
            }
        })

        busConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun selectionChanged(event: FileEditorManagerEvent) {
                refreshAllTrackers()
            }
        })

    }

    private fun isOurGutterMarkersEnabled(): Boolean =
        ToolWindowSettingsProvider.isGutterMarkersEnabled()

    /**
     * Called from settings when the user toggles any gutter marker feature.
     * Re-evaluates all trackers (to install/remove visual overlays) and
     * triggers a global file status refresh so the IDE updates its UI.
     */
    fun settingsChanged() {
        logger.info("VISUAL_TRACKER: Gutter marker setting changed. Refreshing trackers and file statuses.")
        refreshAllTrackers()
        refreshFileStatuses()
    }

    private fun refreshFileStatuses() {
        if (project.isDisposed) return
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            FileStatusManager.getInstance(project).fileStatusesChanged()
        }
    }

    /**
     * Re-evaluates all active native trackers to decide if we should Intercept or Yield.
     * Handles transitions:
     * - Native -> Visual (Create Visual, Hide Native)
     * - Visual -> Visual (Update Content)
     * - Visual -> Native (Dispose Visual, Restore Native)
     */
    private fun refreshAllTrackers() {
        if (project.isDisposed) return

        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater

            val documents = EditorFactory.getInstance().allEditors.map { it.document }.distinct()
            val gutterEnabled = isOurGutterMarkersEnabled()
            coroutineScope.launch(dispatchers.background) {
                documents.forEach { document ->
                    refreshTracker(document, gutterEnabled)
                }
            }
        }
    }

    private fun maybeInterceptTracker(nativeTracker: LocalLineStatusTracker<*>) {
        if (!isOurGutterMarkersEnabled()) return

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
                    performStandaloneInterception(document, file, targetRevision)
                }
            } else {
                if (nativeTracker != null) {
                    restoreNativeTracker(nativeTracker)
                } else {
                    restoreStandaloneTracker(document, file)
                }
            }
        }
    }

    private suspend fun applyTrackerState(tracker: LocalLineStatusTracker<*>, targetRevision: String?) {
        withContext(dispatchers.ui) {
            if (project.isDisposed) return@withContext
            if (targetRevision != null) {
                performInterception(tracker, targetRevision)
            } else {
                restoreNativeTracker(tracker)
            }
        }
    }

    private fun restoreNativeTracker(nativeTracker: LocalLineStatusTracker<*>) {
        val document = nativeTracker.document
        
        // Only act if we actually have something to clean up
        if (visualTrackers.containsKey(document)) {
            val file = nativeTracker.virtualFile
            logger.debug("VISUAL_TRACKER: Restoring Native Tracker for ${file.name}. Removing visual markers.")

            // 1. Remove Visual Tracker
            val visualTracker = visualTrackers.remove(document)
            visualTracker?.release()

            // 2. Un-hide Native Tracker
            // Assuming standard trackers are visible by default. 
            // We set the mode back to a state where it shows gutters.
            nativeTracker.mode = LocalLineStatusTracker.Mode(
                isVisible = true,
                showErrorStripeMarkers = true,
                detectWhitespaceChangedLines = true
            )
        }
    }

    private fun resolveTargetRevision(file: VirtualFile): String? {
        val context = resolveTargetRevisionContext(file) ?: run {
            logger.debug("VISUAL_TRACKER: Yielding. Target revision is null for ${file.name}.")
            return null
        }

        if (shouldSkipTrackerForCurrentRevision(context.repository, context.targetRevision)) {
            return null
        }

        if (shouldSkipTrackerForNewFile(context.diffDataService, file)) {
            return null
        }

        return context.targetRevision
    }

    private fun resolveTargetRevisionContext(file: VirtualFile): TargetRevisionContext? {
        val diffDataService = project.service<ProjectActiveDiffDataService>()
        val branchName = diffDataService.activeBranchName
        val gitService = project.service<GitService>()
        val repository = gitService.getRepositoryForFile(file) ?: return null
        val comparisonContext = diffDataService.activeComparisonContext
        val targetRevision = comparisonContext[repository.root.path] ?: branchName ?: return null
        return TargetRevisionContext(diffDataService, repository, targetRevision)
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
    ): Boolean {
        if (ToolWindowSettingsProvider.isGutterForNewFilesEnabled()) {
            return false
        }

        return diffDataService.createdFiles.contains(file)
    }

    private fun performInterception(nativeTracker: LocalLineStatusTracker<*>, targetRevision: String) {
        val file = nativeTracker.virtualFile
        
        // 1. Hide Native Tracker (Idempotent)
        nativeTracker.mode = LocalLineStatusTracker.Mode(
            isVisible = false,
            showErrorStripeMarkers = false,
            detectWhitespaceChangedLines = false
        )

        val document = nativeTracker.document
        
        // 2. Ensure Visual Tracker Exists
        val visualTracker = visualTrackers.computeIfAbsent(document) {
            logger.debug("VISUAL_TRACKER: Creating Visual Tracker for ${file.name}")
            val tracker = SimpleLocalLineStatusTracker.createTracker(project, document, file)
            tracker.mode = LocalLineStatusTracker.Mode(
                isVisible = true,
                showErrorStripeMarkers = true,
                detectWhitespaceChangedLines = true
            )
            Disposer.register(this) { tracker.release() }
            tracker
        }

        // 3. Update Content using the PRE-RESOLVED targetRevision
        coroutineScope.launch {
            val content = loadTargetContent(file, targetRevision)
            withContext(dispatchers.ui) {
                // Double-check tracker is still valid/needed?
                // We rely on the fact that if it became invalid, refreshAllTrackers would have been called again 
                // and might conflict, but `visualTracker` is the same instance.
                visualTracker.setBaseRevision(content)
            }
        }
    }

    private fun performStandaloneInterception(document: Document, file: VirtualFile, targetRevision: String) {
        val visualTracker = visualTrackers.computeIfAbsent(document) {
            logger.debug("VISUAL_TRACKER: Creating standalone visual tracker for ${file.name}")
            val tracker = SimpleLocalLineStatusTracker.createTracker(project, document, file)
            tracker.mode = LocalLineStatusTracker.Mode(
                isVisible = true,
                showErrorStripeMarkers = true,
                detectWhitespaceChangedLines = true
            )
            Disposer.register(this) { tracker.release() }
            tracker
        }

        coroutineScope.launch {
            val content = loadTargetContent(file, targetRevision)
            withContext(dispatchers.ui) {
                visualTracker.setBaseRevision(content)
            }
        }
    }

    private fun restoreStandaloneTracker(document: Document, file: VirtualFile) {
        if (visualTrackers.containsKey(document)) {
            logger.debug("VISUAL_TRACKER: Releasing standalone visual tracker for ${file.name}.")
            visualTrackers.remove(document)?.release()
        }
    }

    private suspend fun loadTargetContent(file: VirtualFile, revision: String?): CharSequence {
        suspend fun fallbackContent(): CharSequence =
            readActionBlocking { FileDocumentManager.getInstance().getDocument(file)?.text ?: "" }

        if (revision == null) {
            return fallbackContent()
        }

        // Optimization: If a file is explicitly new in our diff data, return empty immediately.
        // This avoids an unnecessary Git lookup that would throw/fail anyway.
        val isNewFile = project.service<ProjectActiveDiffDataService>().createdFiles.contains(file)
        if (isNewFile) {
            return ""
        }

        val gitService = project.service<GitService>()
        return withContext(dispatchers.io) {
            try {
                gitService.getFileContentForRevision(revision, file)
                    ?: return@withContext fallbackContent()
            } catch (e: VcsException) {
                if (e.message.contains("does not exist in", ignoreCase = true)) {
                    ""
                } else {
                    logger.warn("VISUAL_TRACKER: Failed to load content for ${file.path}. VcsException Error: ${e.message}")
                    fallbackContent()
                }
            } catch (e: Exception) {
                // Defensive fallback for unexpected non-VcsException errors
                val rootCause = generateSequence<Throwable>(e) { it.cause }
                    .firstOrNull { it is VcsException } as? VcsException
                if (rootCause != null && rootCause.message.contains("does not exist in", ignoreCase = true)) {
                    ""
                } else {
                    logger.warn("VISUAL_TRACKER: Failed to load content for ${file.path}. Unexpected Error: ${(rootCause ?: e).message}")
                    fallbackContent()
                }
            }
        }
    }

    override fun dispose() {
        visualTrackers.clear()
    }
}

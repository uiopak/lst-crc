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
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.ex.LineStatusTracker
import com.intellij.openapi.vcs.ex.LocalLineStatusTracker
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker
import com.intellij.openapi.vcs.ex.SimpleLocalLineStatusTracker
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class VisualTrackerManager(private val project: Project, private val coroutineScope: CoroutineScope) : Disposable {
    private val logger = thisLogger()
    private val visualTrackers = ConcurrentHashMap<Document, SimpleLocalLineStatusTracker>()

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
            
            val editors = EditorFactory.getInstance().allEditors
            val documents = editors.map { it.document }.distinct()
            val trackerManager = LineStatusTrackerManager.getInstance(project)
            val gutterEnabled = isOurGutterMarkersEnabled()

            coroutineScope.launch(Dispatchers.Default) {
                for (doc in documents) {
                     val tracker = trackerManager.getLineStatusTracker(doc)
                     if (tracker is LocalLineStatusTracker<*>) {
                         // If gutters are globally disabled, always restore native trackers
                         val targetRevision = if (gutterEnabled) resolveTargetRevision(tracker.virtualFile) else null

                         withContext(Dispatchers.EDT) {
                             if (project.isDisposed) return@withContext
                             if (targetRevision != null) {
                                 performInterception(tracker, targetRevision)
                             } else {
                                 restoreNativeTracker(tracker)
                             }
                         }
                     }
                }
            }
        }
    }

    private fun maybeInterceptTracker(nativeTracker: LocalLineStatusTracker<*>) {
        if (!isOurGutterMarkersEnabled()) return

        val file = nativeTracker.virtualFile
        if (nativeTracker !is PartialLocalLineStatusTracker) return

        coroutineScope.launch(Dispatchers.Default) {
             val targetRevision = resolveTargetRevision(file)
             if (targetRevision != null) {
                 withContext(Dispatchers.EDT) {
                     performInterception(nativeTracker, targetRevision)
                 }
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

    private fun resolveTargetRevision(file: com.intellij.openapi.vfs.VirtualFile): String? {
        val diffDataService = project.service<ProjectActiveDiffDataService>()
        val branchName = diffDataService.activeBranchName
        val gitService = project.service<GitService>()
        val repository = gitService.getRepositoryForFile(file) ?: return null

        val comparisonContext = diffDataService.activeComparisonContext
        val targetRevision = repository.let { comparisonContext[it.root.path] } ?: branchName

        if (targetRevision == null) {
            logger.debug("VISUAL_TRACKER: Yielding. Target revision is null for ${file.name}.")
            return null
        }
        
        // Check "Same as Current"
        val currentBranchName = repository.currentBranchName
        val currentRevision = repository.currentRevision

        val isTargetSameAsCurrent = (targetRevision == currentBranchName) ||
                (targetRevision == currentRevision) ||
                (targetRevision == "HEAD")

        val includeHeadInScopes = ToolWindowSettingsProvider.isIncludeHeadInScopes()

        if (isTargetSameAsCurrent && !includeHeadInScopes) return null

        // Check "Show Gutter for New Files" setting
        val showForNewFiles = ToolWindowSettingsProvider.isGutterForNewFilesEnabled()

        if (!showForNewFiles) {
            val isNewFile = diffDataService.createdFiles.contains(file)
            if (isNewFile) {
                return null
            }
        }
        
        return targetRevision
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
            Disposer.register(this) { tracker.release() }
            tracker
        }

        // 3. Update Content using the PRE-RESOLVED targetRevision
        coroutineScope.launch {
            val content = loadTargetContent(file, targetRevision)
            withContext(Dispatchers.EDT) {
                // Double-check tracker is still valid/needed?
                // We rely on the fact that if it became invalid, refreshAllTrackers would have been called again 
                // and might conflict, but `visualTracker` is the same instance.
                visualTracker.setBaseRevision(content)
            }
        }
    }

    private suspend fun loadTargetContent(file: com.intellij.openapi.vfs.VirtualFile, revision: String?): CharSequence {
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
        return withContext(Dispatchers.IO) {
            try {
                val future = gitService.getFileContentForRevision(revision, file)
                val content = future.get() ?: return@withContext fallbackContent()
                StringUtil.convertLineSeparators(content)
            } catch (e: Exception) {
                val rootCause = if (e is java.util.concurrent.ExecutionException) e.cause ?: e else e

                if (rootCause is VcsException && rootCause.message.contains("does not exist in", ignoreCase = true)) {
                    ""
                } else {
                    logger.warn("VISUAL_TRACKER: Failed to load content for ${file.path}. Error: ${rootCause.message}")
                    fallbackContent()
                }
            }
        }
    }

    override fun dispose() {
        visualTrackers.clear()
    }
}

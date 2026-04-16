package com.github.uiopak.lstcrc.gutters

import com.github.uiopak.lstcrc.messaging.ActiveDiffDataChangedListener
import com.github.uiopak.lstcrc.messaging.DIFF_DATA_CHANGED_TOPIC
import com.github.uiopak.lstcrc.services.GitService
import com.github.uiopak.lstcrc.services.ProjectActiveDiffDataService
import com.github.uiopak.lstcrc.toolWindow.ToolWindowSettingsProvider
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.MarkupModel
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.ex.LineStatusTracker
import com.intellij.openapi.vcs.ex.LocalLineStatusTracker
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
    private val properties = PropertiesComponent.getInstance()
    private val visualTrackers = ConcurrentHashMap<Document, SimpleLocalLineStatusTracker>()
    private val installedHighlighters = ConcurrentHashMap<Document, MutableList<RangeHighlighter>>()

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

        // Listen for Editor creation
        EditorFactory.getInstance().addEditorFactoryListener(object : EditorFactoryListener {
            override fun editorCreated(event: EditorFactoryEvent) {
                val doc = event.editor.document
                if (visualTrackers.containsKey(doc)) {
                    installVisualRenderer(event.editor.markupModel, doc)
                }
            }
        }, this)
    }

    private fun isOurGutterMarkersEnabled(): Boolean =
        properties.getBoolean(ToolWindowSettingsProvider.APP_ENABLE_GUTTER_MARKERS_KEY, ToolWindowSettingsProvider.DEFAULT_ENABLE_GUTTER_MARKERS)

    /**
     * Re-evaluates all active native trackers to decide if we should Intercept or Yield.
     * Handles transitions:
     * - Native -> Visual (Create Visual, Hide Native)
     * - Visual -> Visual (Update Content)
     * - Visual -> Native (Dispose Visual, Restore Native)
     */
    /**
     * Re-evaluates all active native trackers to decide if we should Intercept or Yield.
     * Handles transitions:
     * - Native -> Visual (Create Visual, Hide Native)
     * - Visual -> Visual (Update Content)
     * - Visual -> Native (Dispose Visual, Restore Native)
     */
    private fun refreshAllTrackers() {
        if (project.isDisposed) return

        // Must access allEditors on EDT or read lock, but usually safe to access property.
        // To be safe and because we launch coroutine anyway:
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            
            val editors = EditorFactory.getInstance().allEditors
            val documents = editors.map { it.document }.distinct()
            val trackerManager = LineStatusTrackerManager.getInstance(project)

            // We need to process this on a background thread to avoid checking Git on EDT for every file
            coroutineScope.launch(Dispatchers.Default) {
                for (doc in documents) {
                     val tracker = trackerManager.getLineStatusTracker(doc)
                     if (tracker is LocalLineStatusTracker<*>) {
                         val file = tracker.virtualFile
                         val targetRevision = resolveTargetRevision(file)

                         withContext(Dispatchers.EDT) {
                             if (project.isDisposed) return@withContext
                             if (targetRevision != null) {
                                 performInterception(tracker, targetRevision) // Updates content with specific revision
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
        val className = nativeTracker.javaClass.name
        if (!className.contains("ChangelistsLocalLineStatusTracker")) return

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

            // 2. Remove Highlighters
            val highlighters = installedHighlighters.remove(document)
            highlighters?.forEach { it.dispose() }

            // 3. Un-hide Native Tracker
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

        val includeHeadInScopes = properties.getBoolean(
            ToolWindowSettingsProvider.APP_INCLUDE_HEAD_IN_SCOPES_KEY,
            ToolWindowSettingsProvider.DEFAULT_INCLUDE_HEAD_IN_SCOPES
        )

        if (isTargetSameAsCurrent && !includeHeadInScopes) return null

        // Check "Show Gutter for New Files" setting
        val showForNewFiles = properties.getBoolean(
            ToolWindowSettingsProvider.APP_ENABLE_GUTTER_FOR_NEW_FILES_KEY,
            ToolWindowSettingsProvider.DEFAULT_ENABLE_GUTTER_FOR_NEW_FILES
        )

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

                // 4. Install into all open editors (Idempotent-ish)
                EditorFactory.getInstance().allEditors.forEach { editor ->
                    if (editor.document == document) {
                        installVisualRenderer(editor.markupModel, document)
                    }
                }
            }
        }
    }

    private suspend fun loadTargetContent(file: com.intellij.openapi.vfs.VirtualFile, revision: String?): CharSequence {
         val fallbackContent by lazy {
             runReadActionBlocking { FileDocumentManager.getInstance().getDocument(file)?.text ?: "" }
         }

         if (revision == null) {
             return fallbackContent
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
                 val content = future.get() ?: return@withContext fallbackContent
                 StringUtil.convertLineSeparators(content)
             } catch (e: Exception) {
                 val rootCause = if (e is java.util.concurrent.ExecutionException) e.cause ?: e else e
                 
                 if (rootCause is VcsException && rootCause.message.contains("does not exist in", ignoreCase = true)) {
                     ""
                 } else {
                     logger.warn("VISUAL_TRACKER: Failed to load content for ${file.path}. Error: ${rootCause.message}")
                     fallbackContent
                 }
             }
         }
    }

    private fun installVisualRenderer(markupModel: MarkupModel, document: Document) {
        val tracker = visualTrackers[document] ?: return

        val list = installedHighlighters.computeIfAbsent(document) { mutableListOf() }
        // Clean up invalid highlighters first
        list.removeIf { !it.isValid }
        
        if (list.any { it.gutterIconRenderer != null }) return 

        try {
            val rendererFn = getProtectedRenderer(tracker)
            if (rendererFn != null) {
                val highlighter = markupModel.addRangeHighlighter(
                    0,
                    document.textLength,
                    com.intellij.openapi.editor.markup.HighlighterLayer.FIRST - 1,
                    null,
                    HighlighterTargetArea.LINES_IN_RANGE
                )
                highlighter.gutterIconRenderer = rendererFn
                list.add(highlighter)
                logger.debug("VISUAL_TRACKER: Installed visual renderer for ${tracker.virtualFile.name}")
            }
        } catch (e: Exception) {
            logger.error("VISUAL_TRACKER: Failed to install renderer", e)
        }
    }

    private fun getProtectedRenderer(instance: Any): com.intellij.openapi.editor.markup.GutterIconRenderer? {
        var clazz: Class<*>? = instance.javaClass
        while (clazz != null) {
            try {
                val method = clazz.getDeclaredMethod("getRenderer")
                method.isAccessible = true
                return method.invoke(instance) as? com.intellij.openapi.editor.markup.GutterIconRenderer
            } catch (ignored: NoSuchMethodException) {
            }
            clazz = clazz.superclass
        }
        return null
    }

    override fun dispose() {
        visualTrackers.clear()
        installedHighlighters.forEach { (_, highlighters) ->
            highlighters.forEach { it.dispose() }
        }
        installedHighlighters.clear()
    }
}

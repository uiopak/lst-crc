package com.github.uiopak.lstcrc.listeners

import com.github.uiopak.lstcrc.services.GitService
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListListener
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VirtualFile
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

/**
 * Quiet period before a burst of edits or VCS events turns into a refresh. Every refresh runs
 * several git processes, so typing must not trigger one per keystroke.
 */
private val REFRESH_DEBOUNCE = 300.milliseconds

/**
 * The single source of automatic refreshes. Listens for `ChangeListManager` updates (local edits,
 * reverts, Undo), repository changes (commit, checkout, fetch), document saves and unsaved document
 * edits, and triggers one debounced data refresh for the whole plugin.
 *
 * A burst made only of unsaved edits calls [refreshAfterDocumentEdit], which lets `GitService` reuse
 * the last `git diff` result, because nothing changed on disk. Anything else in the burst (a
 * changelist update, a repository change, a save) calls [refreshCurrentSelection] for a full reload.
 */
@OptIn(FlowPreview::class)
@Service(Service.Level.PROJECT)
class VcsChangeListener internal constructor(
    private val project: Project,
    coroutineScope: CoroutineScope,
    private val refreshCurrentSelection: () -> Unit,
    private val isRepositoryFile: (VirtualFile) -> Boolean,
    private val refreshAfterDocumentEdit: () -> Unit = refreshCurrentSelection
) : ChangeListListener, DocumentListener, GitRepositoryChangeListener, FileDocumentManagerListener, Disposable {

    @Suppress("unused")
    constructor(project: Project, coroutineScope: CoroutineScope) : this(
        project = project,
        coroutineScope = coroutineScope,
        refreshCurrentSelection = { project.service<ToolWindowStateService>().refreshDataForCurrentSelection() },
        isRepositoryFile = { file -> project.service<GitService>().getRepositoryForFile(file) != null },
        refreshAfterDocumentEdit = { project.service<ToolWindowStateService>().refreshAfterDocumentEdit() }
    )

    private val logger = thisLogger()
    // Only wakeups may be dropped. The work they describe is accumulated separately until checked.
    private val refreshSignals = MutableSharedFlow<Unit>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val pendingFileSignals = linkedMapOf<VirtualFile, Boolean>()
    private var pendingVcsRefresh = false // guarded by pendingFileSignals

    /** True once the debounced collector below listens. For unit tests. */
    internal val isCollectingSignals: Boolean
        get() = refreshSignals.subscriptionCount.value > 0

    /** Set when the current burst contains anything but unsaved edits; read and cleared by the refresh. */
    private val fullRefreshPending = AtomicBoolean(false)

    init {
        logger.debug { "VCS_CHANGE_LISTENER: Initializing for project ${project.name}" }
        ChangeListManager.getInstance(project).addChangeListListener(this, this)
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(this, this)
        project.messageBus.connect(this).subscribe(GitRepository.GIT_REPO_CHANGE, this)
        ApplicationManager.getApplication().messageBus.connect(this).subscribe(FileDocumentManagerListener.TOPIC, this)

        coroutineScope.launch {
            refreshSignals
                .filter { collectPendingSignals() }
                .debounce(REFRESH_DEBOUNCE)
                .collect {
                    if (project.isDisposed) return@collect
                    val full = fullRefreshPending.getAndSet(false)
                    logger.debug { "VCS_CHANGE_LISTENER: Refresh executing (full=$full)." }
                    if (full) refreshCurrentSelection() else refreshAfterDocumentEdit()
                }
        }
    }

    override fun repositoryChanged(repository: GitRepository) {
        logger.debug { "VCS_CHANGE_LISTENER: repositoryChanged() detected for '${repository.root.name}', triggering refresh." }
        queueRefresh(null, full = true)
    }

    override fun changeListUpdateDone() {
        logger.debug { "VCS_CHANGE_LISTENER: changeListUpdateDone() detected, triggering refresh." }
        queueRefresh(null, full = true)
    }

    /** A save writes the document to disk, so the next refresh must run `git diff` again. */
    override fun beforeDocumentSaving(document: Document) {
        val file = FileDocumentManager.getInstance().getFile(document) ?: return
        queueRefresh(file, full = true)
    }

    override fun documentChanged(event: DocumentEvent) {
        handleDocumentChange(FileDocumentManager.getInstance().getFile(event.document))
    }

    internal fun handleDocumentChange(file: VirtualFile?) {
        file ?: return

        logger.debug { "VCS_CHANGE_LISTENER: documentChanged() detected for '${file.path}', queueing refresh." }
        queueRefresh(file, full = false)
    }

    /** Keeps every affected file and remembers saves even when later edits share the same file. */
    private fun queueRefresh(file: VirtualFile?, full: Boolean) {
        synchronized(pendingFileSignals) {
            if (file == null) {
                pendingVcsRefresh = true
            } else {
                pendingFileSignals[file] = full || pendingFileSignals[file] == true
            }
        }
        refreshSignals.tryEmit(Unit)
    }

    /** Checks each distinct pending file off the editing thread; foreign project saves cannot force a reload. */
    private suspend fun collectPendingSignals(): Boolean {
        val (files, full) = synchronized(pendingFileSignals) {
            val batch = pendingFileSignals.toMap() to pendingVcsRefresh
            pendingFileSignals.clear()
            pendingVcsRefresh = false
            batch
        }
        var needsRefresh = full
        if (full) fullRefreshPending.set(true)
        if (files.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                files.forEach { (file, saved) ->
                    if (isRepositoryFile(file)) {
                        needsRefresh = true
                        if (saved) fullRefreshPending.set(true)
                    }
                }
            }
        }
        return needsRefresh
    }

    override fun dispose() {
        logger.debug { "VCS_CHANGE_LISTENER: Disposing for project ${project.name}" }
    }
}

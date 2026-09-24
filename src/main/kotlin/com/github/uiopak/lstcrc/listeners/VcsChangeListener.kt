package com.github.uiopak.lstcrc.listeners

import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
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
import kotlin.time.Duration.Companion.milliseconds

/**
 * Quiet period before a burst of edits or VCS events turns into a refresh. Every refresh runs
 * several git processes, so typing must not trigger one per keystroke.
 */
private val REFRESH_DEBOUNCE = 300.milliseconds

/**
 * The single source of automatic refreshes. Listens for `ChangeListManager` updates (local edits,
 * reverts, Undo), repository changes (commit, checkout, fetch) and unsaved document edits, and
 * triggers one debounced data refresh for the whole plugin.
 */
@OptIn(FlowPreview::class)
@Service(Service.Level.PROJECT)
class VcsChangeListener internal constructor(
    private val project: Project,
    coroutineScope: CoroutineScope,
    private val refreshCurrentSelection: () -> Unit,
    private val isRepositoryFile: (VirtualFile) -> Boolean
) : ChangeListListener, DocumentListener, GitRepositoryChangeListener, Disposable {

    companion object {
        @JvmStatic
        fun createForTest(
            project: Project,
            coroutineScope: CoroutineScope,
            refreshCurrentSelection: () -> Unit,
            isRepositoryFile: (VirtualFile) -> Boolean
        ): VcsChangeListener = VcsChangeListener(project, coroutineScope, refreshCurrentSelection, isRepositoryFile)
    }

    @Suppress("unused")
    constructor(project: Project, coroutineScope: CoroutineScope) : this(
        project = project,
        coroutineScope = coroutineScope,
        refreshCurrentSelection = { project.service<ToolWindowStateService>().refreshDataForCurrentSelection() },
        isRepositoryFile = { file -> project.service<com.github.uiopak.lstcrc.services.GitService>().getRepositoryForFile(file) != null }
    )

    private val logger = thisLogger()
    private val refreshSignals = MutableSharedFlow<VirtualFile?>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    init {
        logger.info("VCS_CHANGE_LISTENER: Initializing for project ${project.name}")
        ChangeListManager.getInstance(project).addChangeListListener(this, this)
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(this, this)
        project.messageBus.connect(this).subscribe(GitRepository.GIT_REPO_CHANGE, this)

        coroutineScope.launch {
            refreshSignals
                .filter { file -> file == null || withContext(Dispatchers.IO) { isRepositoryFile(file) } }
                .debounce(REFRESH_DEBOUNCE)
                .collect {
                    if (project.isDisposed) return@collect
                    logger.debug("VCS_CHANGE_LISTENER: Refresh executing.")
                    refreshCurrentSelection()
                }
        }
    }

    override fun repositoryChanged(repository: GitRepository) {
        logger.debug("VCS_CHANGE_LISTENER: repositoryChanged() detected for '${repository.root.name}', triggering refresh.")
        triggerRefresh(null)
    }

    override fun changeListUpdateDone() {
        logger.debug("VCS_CHANGE_LISTENER: changeListUpdateDone() detected, triggering refresh.")
        triggerRefresh(null)
    }

    override fun documentChanged(event: DocumentEvent) {
        handleDocumentChange(FileDocumentManager.getInstance().getFile(event.document))
    }

    internal fun handleDocumentChange(file: VirtualFile?) {
        file ?: return

        logger.debug("VCS_CHANGE_LISTENER: documentChanged() detected for '${file.path}', queueing refresh.")
        triggerRefresh(file)
    }

    private fun triggerRefresh(file: VirtualFile?) {
        refreshSignals.tryEmit(file)
    }

    override fun dispose() {
        logger.info("VCS_CHANGE_LISTENER: Disposing for project ${project.name}")
    }
}
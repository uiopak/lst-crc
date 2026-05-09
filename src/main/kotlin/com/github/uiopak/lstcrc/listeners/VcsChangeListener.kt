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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Listens for `ChangeListManager` updates to detect when VCS state changes (e.g., local edits,
 * reverts, Undo). It triggers a coalesced data refresh to update all plugin components,
 * decoupling them from the direct source of VCS events.
 */
@Service(Service.Level.PROJECT)
class VcsChangeListener internal constructor(
    private val project: Project,
    coroutineScope: CoroutineScope,
    private val refreshCurrentSelection: () -> Unit,
    private val isRepositoryFile: (VirtualFile) -> Boolean
) : ChangeListListener, DocumentListener, Disposable {

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
    private val refreshSignals = MutableSharedFlow<VirtualFile?>(extraBufferCapacity = 1)

    init {
        logger.info("VCS_CHANGE_LISTENER: Initializing for project ${project.name}")
        ChangeListManager.getInstance(project).addChangeListListener(this, this)
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(this, this)

        coroutineScope.launch {
            refreshSignals
                .collectLatest { file ->
                    if (project.isDisposed) return@collectLatest

                    if (file != null && !isRepositoryFile(file)) {
                        logger.debug("VCS_CHANGE_LISTENER: Skipping refresh for non-repository file '${file.path}'.")
                        return@collectLatest
                    }

                    logger.info("VCS_CHANGE_LISTENER: Refresh executing.")
                    refreshCurrentSelection()
                }
        }
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
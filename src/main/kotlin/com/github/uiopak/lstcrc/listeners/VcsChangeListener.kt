package com.github.uiopak.lstcrc.listeners

import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListListener
import com.intellij.openapi.vcs.changes.ChangeListManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Listens for `ChangeListManager` updates to detect when VCS state changes (e.g., local edits,
 * reverts, Undo). It triggers a debounced data refresh to update all plugin components,
 * decoupling them from the direct source of VCS events.
 */
@Service(Service.Level.PROJECT)
@OptIn(FlowPreview::class)
class VcsChangeListener(
    private val project: Project,
    coroutineScope: CoroutineScope
) : ChangeListListener, Disposable {

    private val logger = thisLogger()
    private val refreshSignals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        logger.info("VCS_CHANGE_LISTENER: Initializing for project ${project.name}")
        ChangeListManager.getInstance(project).addChangeListListener(this, this)

        coroutineScope.launch {
            refreshSignals
                .debounce(250.milliseconds)
                .collect {
                    if (!project.isDisposed) {
                        logger.info("VCS_CHANGE_LISTENER: Debounced refresh executing.")
                        project.service<ToolWindowStateService>().refreshDataForCurrentSelection()
                    }
                }
        }
    }

    override fun changeListUpdateDone() {
        logger.debug("VCS_CHANGE_LISTENER: changeListUpdateDone() detected, triggering debounced refresh.")
        triggerDebouncedRefresh()
    }

    private fun triggerDebouncedRefresh() {
        refreshSignals.tryEmit(Unit)
    }

    override fun dispose() {
        logger.info("VCS_CHANGE_LISTENER: Disposing for project ${project.name}")
    }
}
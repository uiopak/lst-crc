package com.github.uiopak.lstcrc.listeners

import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListListener
import com.intellij.openapi.vcs.changes.ChangeListManager
import javax.swing.Timer

/**
 * Listens for `ChangeListManager` updates to detect when VCS state changes (e.g., local edits,
 * reverts, Undo). It triggers a debounced data refresh to update all plugin components,
 * decoupling them from the direct source of VCS events.
 */
@Service(Service.Level.PROJECT)
class VcsChangeListener(private val project: Project) : ChangeListListener, Disposable {

    private val logger = thisLogger()
    private var refreshDebounceTimer: Timer? = null

    init {
        logger.info("VCS_CHANGE_LISTENER: Initializing for project ${project.name}")
        // The listener is removed automatically when this service (which is a Disposable) is disposed.
        ChangeListManager.getInstance(project).addChangeListListener(this, this)
    }

    /**
     * This is the most comprehensive callback from the VCS system after it has finished processing a batch
     * of file status updates (modified, new, deleted, unversioned, etc.). This event is ideal for
     * triggering a refresh of our diff data, as it ensures we act on a complete state. It covers
     * everything from local edits and 'Undo' actions to branch switches.
     */
    override fun changeListUpdateDone() {
        logger.debug("VCS_CHANGE_LISTENER: changeListUpdateDone() detected, triggering debounced refresh.")
        triggerDebouncedRefresh()
    }

    private fun triggerDebouncedRefresh() {
        refreshDebounceTimer?.stop()
        // A short delay to batch successive VCS events.
        refreshDebounceTimer = Timer(250, null).apply {
            addActionListener {
                ApplicationManager.getApplication().invokeLater {
                    if (!project.isDisposed) {
                        logger.info("VCS_CHANGE_LISTENER: Debounced refresh executing.")
                        // This single call correctly refreshes data for whatever is currently selected.
                        project.service<ToolWindowStateService>().refreshDataForCurrentSelection()
                    }
                }
            }
            isRepeats = false
        }
        refreshDebounceTimer?.start()
    }

    override fun dispose() {
        refreshDebounceTimer?.stop()
        logger.info("VCS_CHANGE_LISTENER: Disposing for project ${project.name}")
    }
}
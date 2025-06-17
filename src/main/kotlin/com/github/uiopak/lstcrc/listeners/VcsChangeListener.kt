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
 * A project-level service that listens for changes in the IDE's active changelists.
 * This is the primary mechanism for detecting when a file is modified, reverted, or otherwise
 * has its VCS status changed without a direct file system event (e.g., an in-memory Undo).
 *
 * When a change is detected, it triggers a debounced refresh of the plugin's data, ensuring that
 * all components (gutter markers, file scopes, tool window) are updated accordingly. This service
 * decouples background VCS change detection from any UI components.
 */
@Service(Service.Level.PROJECT)
class VcsChangeListener(private val project: Project) : ChangeListListener, Disposable {

    private val logger = thisLogger()
    private var refreshDebounceTimer: Timer? = null

    init {
        logger.info("VCS_CHANGE_LISTENER: Initializing for project ${project.name}")
        // The listener is removed automatically when the service (disposable) is disposed.
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
        // Use a short delay. 250ms is reasonable to batch quick successive events.
        refreshDebounceTimer = Timer(250, null).apply {
            addActionListener {
                ApplicationManager.getApplication().invokeLater {
                    if (!project.isDisposed) {
                        logger.info("VCS_CHANGE_LISTENER: Debounced refresh executing.")
                        val toolWindowStateService = project.service<ToolWindowStateService>()
                        // This single call correctly refreshes data for whatever is currently selected,
                        // be it HEAD or a specific branch tab.
                        toolWindowStateService.refreshDataForCurrentSelection()
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
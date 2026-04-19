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
import com.intellij.util.Alarm

/**
 * Listens for `ChangeListManager` updates to detect when VCS state changes (e.g., local edits,
 * reverts, Undo). It triggers a debounced data refresh to update all plugin components,
 * decoupling them from the direct source of VCS events.
 */
@Service(Service.Level.PROJECT)
class VcsChangeListener(private val project: Project) : ChangeListListener, Disposable {

    private val logger = thisLogger()
    private val debounceAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    init {
        logger.info("VCS_CHANGE_LISTENER: Initializing for project ${project.name}")
        ChangeListManager.getInstance(project).addChangeListListener(this, this)
    }

    override fun changeListUpdateDone() {
        logger.debug("VCS_CHANGE_LISTENER: changeListUpdateDone() detected, triggering debounced refresh.")
        triggerDebouncedRefresh()
    }

    private fun triggerDebouncedRefresh() {
        debounceAlarm.cancelAllRequests()
        debounceAlarm.addRequest({
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed) {
                    logger.info("VCS_CHANGE_LISTENER: Debounced refresh executing.")
                    project.service<ToolWindowStateService>().refreshDataForCurrentSelection()
                }
            }
        }, 250)
    }

    override fun dispose() {
        logger.info("VCS_CHANGE_LISTENER: Disposing for project ${project.name}")
    }
}
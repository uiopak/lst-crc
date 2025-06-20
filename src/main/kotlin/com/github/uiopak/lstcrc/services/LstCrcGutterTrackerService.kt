package com.github.uiopak.lstcrc.services

import com.github.uiopak.lstcrc.messaging.ActiveDiffDataChangedListener
import com.github.uiopak.lstcrc.messaging.DIFF_DATA_CHANGED_TOPIC
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatusManager

/**
 * A bridge service that listens for plugin-specific events and triggers the IDE's native
 * line status tracking mechanism to refresh. It no longer manages trackers directly, but instead
 * ensures that the [com.intellij.openapi.vcs.impl.LineStatusTrackerManager] is notified when it
 * needs to re-evaluate files with our custom [com.github.uiopak.lstcrc.gutters.LstCrcLineStatusTrackerProvider].
 */
@Service(Service.Level.PROJECT)
class LstCrcGutterTrackerService(private val project: Project) : Disposable {

    private val logger = thisLogger()

    init {
        logger.info("GUTTER_TRACKER: Initializing for project '${project.name}'")

        val connection = project.messageBus.connect(this)

        // When our diff data changes, we need to tell the IDE's VCS system
        // to re-evaluate file statuses. This will trigger the LineStatusTrackerManager
        // to re-query all its providers, including ours.
        connection.subscribe(DIFF_DATA_CHANGED_TOPIC, object : ActiveDiffDataChangedListener {
            override fun onDiffDataChanged() {
                logger.debug("GUTTER_TRACKER: Received onDiffDataChanged. Triggering file status refresh.")
                refreshFileStatuses()
            }
        })

        // Also perform an initial refresh to set up trackers for any already-open files.
        refreshFileStatuses()
    }

    /**
     * Called from the settings menu when the user toggles the gutter marker feature.
     * This simply triggers a global refresh, letting the provider re-evaluate `isTrackedFile`.
     */
    fun settingsChanged() {
        logger.info("GUTTER_TRACKER: Gutter marker setting changed. Triggering file status refresh.")
        refreshFileStatuses()
    }

    /**
     * Notifies the IDE that file statuses may have changed. This is the standard way to
     * ask the [com.intellij.openapi.vcs.impl.LineStatusTrackerManager] to re-evaluate its trackers.
     */
    private fun refreshFileStatuses() {
        if (project.isDisposed) return
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            logger.debug("GUTTER_TRACKER: Requesting file status update for all files.")
            FileStatusManager.getInstance(project).fileStatusesChanged()
        }
    }

    override fun dispose() {
        logger.info("GUTTER_TRACKER: Disposing for project '${project.name}'.")
    }
}
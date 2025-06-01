package com.github.uiopak.lstcrc.services

import com.github.uiopak.lstcrc.messaging.FILE_CHANGES_TOPIC
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent

class VfsChangeListener : BulkFileListener {
    private val logger = thisLogger()

    init {
        logger.info("VfsChangeListener INSTANTIATED")
    }

    override fun before(events: MutableList<out VFileEvent>) {
        // logger.warn("DIAGNOSTIC: VfsChangeListener (BulkFileListener).before CALLED. Events: ${events.size}") // Optional
    }

    override fun after(events: MutableList<out VFileEvent>) {
        logger.info("VfsChangeListener.after() CALLED with ${events.size} events. First event path (if any): ${events.firstOrNull()?.path}")

        logger.warn("DIAGNOSTIC: VfsChangeListener (BulkFileListener).after CALLED. Number of events: ${events.size}")

        val projectsToRefresh = mutableSetOf<Project>()

        for (event in events) {
            logger.warn("DIAGNOSTIC: Processing event: ${event::class.java.simpleName}, path: ${event.path}, file: ${event.file?.path}, oldPath: ${(event as? VFileMoveEvent)?.oldPath}")

            if (!(event is VFileCreateEvent || event is VFileDeleteEvent ||
                  event is VFileContentChangeEvent || event is VFileMoveEvent || event is VFileCopyEvent)) {
                logger.debug("Ignoring event type: ${event::class.java.simpleName}") // Keep as debug for less relevant events
                continue
            }

            val file = event.file
            var projectForEvent: Project? = null

            if (file != null) {
                projectForEvent = ProjectLocator.getInstance().guessProjectForFile(file)
            } else {
                val pathForEventLookup: String? = when (event) {
                    is VFileDeleteEvent -> event.path
                    is VFileMoveEvent -> event.oldPath
                    else -> event.path
                }

                if (pathForEventLookup != null) {
                    val openProjects = ProjectManager.getInstance().openProjects
                    for (p in openProjects) {
                        if (p.isDisposed) continue
                        val projectBasePath = p.basePath
                        if (projectBasePath != null && pathForEventLookup.startsWith(projectBasePath + "/")) {
                            projectForEvent = p
                            logger.debug("Guessed project ${p.name} for path $pathForEventLookup by checking open projects.") // Keep as debug
                            break
                        } else if (projectBasePath != null && pathForEventLookup == projectBasePath) {
                            projectForEvent = p
                            logger.debug("Guessed project ${p.name} for path $pathForEventLookup (equals base path).") // Keep as debug
                            break
                        }
                    }
                }
            }

            logger.warn("DIAGNOSTIC: Determined project for event: ${projectForEvent?.name ?: "null"}")

            if (projectForEvent == null || projectForEvent.isDisposed) {
                val pathInfo = file?.path ?: event.path
                logger.debug("Could not determine project for event (file: ${pathInfo}), or project is disposed. Skipping event.") // Keep as debug
                continue
            }

            val currentProject = projectForEvent
            var isRelevant = false
            val fileForRelevanceCheck = event.file

            if (fileForRelevanceCheck != null && fileForRelevanceCheck.isValid) {
                val isInContent = ProjectFileIndex.getInstance(currentProject).isInContent(fileForRelevanceCheck)
                logger.warn("DIAGNOSTIC: File ${fileForRelevanceCheck.path} isInContent for project ${currentProject.name}: $isInContent")
                if (isInContent) {
                    isRelevant = true
                }
            } else if (event is VFileDeleteEvent || (event is VFileMoveEvent && fileForRelevanceCheck == null) ) {
                isRelevant = true
                // No specific isInContent check possible here, relevance is assumed if project is found by path
            }

            logger.warn("DIAGNOSTIC: Event for path ${event.path} (currentProject: ${currentProject.name}) - isRelevant: $isRelevant")

            if (isRelevant) {
                if (currentProject != null) {
                    val eventType = event::class.java.simpleName
                    var shouldMarkDirty = false

                    when (event) {
                        is VFileCreateEvent,
                        is VFileDeleteEvent,
                        is VFileMoveEvent,
                        is VFileCopyEvent,
                        is VFileContentChangeEvent -> shouldMarkDirty = true
                    }

                    if (shouldMarkDirty) {
                        logger.warn("DIAGNOSTIC: Calling VcsDirtyScopeManager.markEverythingDirty() for project ${currentProject.name} due to $eventType for ${event.path}")
                        VcsDirtyScopeManager.getInstance(currentProject).markEverythingDirty()
                    }
                    projectsToRefresh.add(currentProject)
                } else {
                    // Log if currentProject is null but isRelevant was true, as this is unexpected
                    logger.warn("DIAGNOSTIC: Event for path ${event.path} was relevant but currentProject is null. Skipping markDirty and project refresh for this event.")
                }
            }
        }

        projectsToRefresh.forEach { project ->
            if (!project.isDisposed) {
                logger.warn("DIAGNOSTIC: Attempting to publish FILE_CHANGES_TOPIC for project: ${project.name}")
                project.messageBus.syncPublisher(FILE_CHANGES_TOPIC).onFilesChanged()
            } else {
                logger.warn("DIAGNOSTIC: Project ${project.name} was disposed before publishing, skipping.") // Kept this as warn
            }
        }
    }
}

package com.github.uiopak.lstcrc.services

import com.github.uiopak.lstcrc.messaging.FILE_CHANGES_TOPIC
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.project.ProjectManager // Added import
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent

class VfsChangeListener : AsyncFileListener {
    private val logger = thisLogger()

    override fun prepareChange(events: MutableList<out VFileEvent>): AsyncFileListener.ChangeApplier {
        return object : AsyncFileListener.ChangeApplier {
            override fun beforeVfsChange() { /* Nothing specific to do before changes are applied */ }

            override fun afterVfsChange() { // Renamed from afterProcessing
                logger.error("DIAGNOSTIC: VfsChangeListener.afterVfsChange CALLED. Number of events: ${events.size}")
                // logger.info("VFS Change: afterVfsChange invoked with ${events.size} events.") // Original info log
                val projectsToRefresh = mutableSetOf<Project>()

                for (event in events) {
                    logger.error("DIAGNOSTIC: Processing event: ${event::class.java.simpleName}, path: ${event.path}, file: ${event.file?.path}, oldPath: ${(event as? VFileMoveEvent)?.oldPath}")

                    if (!(event is VFileCreateEvent || event is VFileDeleteEvent ||
                          event is VFileContentChangeEvent || event is VFileMoveEvent || event is VFileCopyEvent)) {
                        logger.debug("Ignoring event type: ${event::class.java.simpleName}")
                        continue
                    }

                    val file = event.file // event.file can be null (e.g. for VFileDeleteEvent)
                    var projectForEvent: Project? = null

                    if (file != null) {
                        projectForEvent = ProjectLocator.getInstance().guessProjectForFile(file)
                    } else {
                        // Try to guess by path for events where file might be null (e.g., delete, move)
                        val pathForEventLookup: String? = when (event) { // Renamed to avoid conflict with outer scope 'pathForEvent' if any
                            is VFileDeleteEvent -> event.path
                            is VFileMoveEvent -> event.oldPath // For move, the source path might be more relevant for project determination
                            else -> event.path 
                        }

                        if (pathForEventLookup != null) {
                            val openProjects = ProjectManager.getInstance().openProjects
                            for (p in openProjects) {
                                if (p.isDisposed) continue
                                val projectBasePath = p.basePath
                                if (projectBasePath != null && pathForEventLookup.startsWith(projectBasePath + "/")) {
                                    projectForEvent = p
                                    logger.debug("Guessed project ${p.name} for path $pathForEventLookup by checking open projects.")
                                    break
                                } else if (projectBasePath != null && pathForEventLookup == projectBasePath) {
                                    projectForEvent = p
                                    logger.debug("Guessed project ${p.name} for path $pathForEventLookup (equals base path).")
                                    break
                                }
                            }
                        }
                    }
                    
                    logger.error("DIAGNOSTIC: Determined project for event: ${projectForEvent?.name ?: "null"}")

                    if (projectForEvent == null || projectForEvent.isDisposed) {
                        val pathInfo = file?.path ?: event.path
                        logger.debug("Could not determine project for event (file: ${pathInfo}), or project is disposed. Skipping event.")
                        continue
                    }
                    
                    val currentProject = projectForEvent
                    var isRelevant = false
                    val fileForRelevanceCheck = event.file 

                    // Specific logging for isInContent check
                    if (fileForRelevanceCheck != null && fileForRelevanceCheck.isValid) { // Check file validity
                        // projectForEvent is already checked for null and not disposed
                        val isInContent = ProjectFileIndex.getInstance(currentProject).isInContent(fileForRelevanceCheck)
                        logger.error("DIAGNOSTIC: File ${fileForRelevanceCheck.path} isInContent for project ${currentProject.name}: $isInContent")
                        if (isInContent) {
                            isRelevant = true
                            // logger.info("Relevant VFS event in project ${currentProject.name} for file ${fileForRelevanceCheck.path}.") // Original info log
                        } else {
                            // logger.debug("File ${fileForRelevanceCheck.path} is not in project content for ${currentProject.name}.") // Original debug log
                        }
                    } else if (event is VFileDeleteEvent || (event is VFileMoveEvent && fileForRelevanceCheck == null) ) { 
                        isRelevant = true
                        // val pathInfo = if (event is VFileMoveEvent) event.oldPath else event.path // Original info log variable
                        // logger.info("Relevant VFS ${event::class.java.simpleName} event by path in project ${currentProject.name} for path $pathInfo (file object was null or invalid).") // Original info log
                    } else {
                        // logger.debug("Event for file ${file?.path ?: event.path} not considered relevant, or file is invalid and not a delete/move-by-path case.") // Original debug log
                    }
                    
                    logger.error("DIAGNOSTIC: Event for path ${event.path} (currentProject: ${currentProject.name}) - isRelevant: $isRelevant")

                    if (isRelevant) {
                        projectsToRefresh.add(currentProject)
                    }
                }

                projectsToRefresh.forEach { project ->
                    if (!project.isDisposed) { 
                        logger.error("DIAGNOSTIC: Attempting to publish FILE_CHANGES_TOPIC for project: ${project.name}")
                        // logger.info("Publishing file change event for project ${project.name}") // Original info log
                        project.messageBus.syncPublisher(FILE_CHANGES_TOPIC).onFilesChanged()
                    } else {
                        logger.warn("Project ${project.name} was disposed before publishing message, skipping.")
                    }
                }
            }
        }
    }
}

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
                logger.info("VFS Change: afterVfsChange invoked with ${events.size} events.")
                val projectsToRefresh = mutableSetOf<Project>()

                for (event in events) {
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
                        val pathForEvent: String? = when (event) {
                            is VFileDeleteEvent -> event.path
                            is VFileMoveEvent -> event.oldPath // For move, the source path might be more relevant for project determination
                            // For VFileCopyEvent, event.file is new file, event.oldPath is source path.
                            // For other events like Create, ContentChange, file should ideally not be null.
                            else -> event.path 
                        }

                        if (pathForEvent != null) {
                            val openProjects = ProjectManager.getInstance().openProjects
                            for (p in openProjects) {
                                if (p.isDisposed) continue
                                val projectBasePath = p.basePath
                                // Ensure projectBasePath is not null and pathForEvent starts with it.
                                // Adding a directory separator to avoid partial name matches (e.g. /foo/bar matching /foo/barbaz)
                                if (projectBasePath != null && pathForEvent.startsWith(projectBasePath + "/")) {
                                    projectForEvent = p
                                    logger.debug("Guessed project ${p.name} for path $pathForEvent by checking open projects.")
                                    break
                                } else if (projectBasePath != null && pathForEvent == projectBasePath) {
                                    // case where pathForEvent is the project base path itself
                                    projectForEvent = p
                                    logger.debug("Guessed project ${p.name} for path $pathForEvent (equals base path).")
                                    break
                                }
                            }
                        }
                    }

                    if (projectForEvent == null || projectForEvent.isDisposed) {
                        val pathInfo = file?.path ?: event.path
                        logger.debug("Could not determine project for event (file: ${pathInfo}), or project is disposed. Skipping event.")
                        continue
                    }
                    
                    // Now projectForEvent is non-null and not disposed
                    val currentProject = projectForEvent

                    var isRelevant = false
                    // Use event.file directly for isInContent check if available and valid
                    val fileForRelevanceCheck = event.file 
                    if (fileForRelevanceCheck != null && fileForRelevanceCheck.isValid) {
                        if (ProjectFileIndex.getInstance(currentProject).isInContent(fileForRelevanceCheck)) {
                            isRelevant = true
                            logger.info("Relevant VFS event in project ${currentProject.name} for file ${fileForRelevanceCheck.path}.")
                        } else {
                            logger.debug("File ${fileForRelevanceCheck.path} is not in project content for ${currentProject.name}.")
                        }
                    } else if (event is VFileDeleteEvent || (event is VFileMoveEvent && fileForRelevanceCheck == null) ) { 
                        // If it's a delete event, or a move event where the new file object might not be available/relevant for old project context
                        // and we successfully found a project by its path (oldPath for move, path for delete), consider it relevant.
                        isRelevant = true
                        val pathInfo = if (event is VFileMoveEvent) event.oldPath else event.path
                        logger.info("Relevant VFS ${event::class.java.simpleName} event by path in project ${currentProject.name} for path $pathInfo (file object was null or invalid).")
                    } else {
                         logger.debug("Event for file ${file?.path ?: event.path} not considered relevant, or file is invalid and not a delete/move-by-path case.")
                    }

                    if (isRelevant) {
                        projectsToRefresh.add(currentProject)
                    }
                }

                projectsToRefresh.forEach { project ->
                    // This check is slightly redundant if the earlier check `projectForEvent.isDisposed` is comprehensive,
                    // but it's a good safeguard before publishing.
                    if (!project.isDisposed) { 
                        logger.info("Publishing file change event for project ${project.name}")
                        project.messageBus.syncPublisher(FILE_CHANGES_TOPIC).onFilesChanged()
                    } else {
                         logger.warn("Project ${project.name} was disposed before publishing message, skipping.")
                    }
                }
            }
        }
    }
}

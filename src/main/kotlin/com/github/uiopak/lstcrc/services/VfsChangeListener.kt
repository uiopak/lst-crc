package com.github.uiopak.lstcrc.services

import com.github.uiopak.lstcrc.messaging.FILE_CHANGES_TOPIC
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
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
        // Note: The method signature from the instructions was `prepareChange(...): AsyncFileListener.ChangeApplier?`
        // but the example implementation and the interface require a non-nullable `AsyncFileListener.ChangeApplier`.
        // I'm proceeding with the non-nullable return type as per the example and standard interface.
        return object : AsyncFileListener.ChangeApplier {
            override fun afterProcessing() {
                logger.info("VFS Change: afterProcessing invoked with ${events.size} events.")
                val projectsToRefresh = mutableSetOf<Project>()

                for (event in events) {
                    // Filter for relevant events, e.g., create, delete, content change, move, copy
                    if (event is VFileCreateEvent || event is VFileDeleteEvent ||
                        event is VFileContentChangeEvent || event is VFileMoveEvent || event is VFileCopyEvent) {
                        
                        val file = event.file
                        if (file == null) {
                            // For VFileDeleteEvent, the file might be null if already deleted.
                            // We might need a different way to get context or decide to refresh more broadly if needed.
                            // Path might be available via event.path for some event types.
                            logger.debug("Event ${event::class.java.simpleName} (path: ${event.path}) has no direct file object, attempting to use path for project location.")
                            // Attempt to use path for deleted files if file object is null
                            val pathForEvent = event.path
                            val project = ProjectLocator.getInstance().guessProjectForPath(pathForEvent)
                             if (project == null || project.isDisposed) {
                                logger.debug("Could not determine project for path: ${pathForEvent}, or project is disposed.")
                                continue
                            }
                            // For deleted files, we can't check isInContent directly with a null file.
                            // We might assume if a project was located by path, it's relevant.
                            // Or, one might need more sophisticated logic if this causes too many refreshes.
                            // For now, if a project is guessed by path for a delete event, we'll consider it.
                            if (event is VFileDeleteEvent) {
                                logger.info("Relevant VFS delete event by path in project ${project.name} for path ${pathForEvent}. Adding project to refresh queue.")
                                projectsToRefresh.add(project)
                            } else {
                                // For other event types, if file is null, it's harder to proceed.
                                 logger.debug("Event ${event::class.java.simpleName} has no file and is not a delete event. Skipping.")
                            }
                            continue // Move to next event
                        }


                        val project = ProjectLocator.getInstance().guessProjectForFile(file)
                        if (project == null || project.isDisposed) {
                            logger.debug("Could not determine project for file: ${file.path}, or project is disposed.")
                            continue
                        }

                        // Check if the file is part of the project's content
                        if (ProjectFileIndex.getInstance(project).isInContent(file)) {
                            logger.info("Relevant VFS event in project ${project.name} for file ${file.path}. Adding project to refresh queue.")
                            projectsToRefresh.add(project)
                        } else {
                            logger.debug("File ${file.path} is not in project content for ${project.name}. Path: ${file.path}, Project base: ${project.basePath}")
                        }
                    } else {
                        logger.debug("Ignoring event type: ${event::class.java.simpleName}")
                    }
                }

                projectsToRefresh.forEach { project ->
                    if (!project.isDisposed) {
                        logger.info("Publishing file change event for project ${project.name}")
                        project.messageBus.syncPublisher(FILE_CHANGES_TOPIC).onFilesChanged()
                    } else {
                        logger.warn("Project ${project.name} is disposed, not publishing message.")
                    }
                }
            }
        }
    }
}

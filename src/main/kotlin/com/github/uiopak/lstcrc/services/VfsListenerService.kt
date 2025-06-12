package com.github.uiopak.lstcrc.services

import com.github.uiopak.lstcrc.messaging.FILE_CHANGES_TOPIC
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent

@Service(Service.Level.PROJECT)
class VfsListenerService(private val project: Project) : BulkFileListener, Disposable {

    private val logger = thisLogger()

    init {
        logger.info("VFS_LISTENER: Initializing for project ${project.name}")
        // The connection will be automatically disposed when this service (Disposable) is disposed.
        val connection = project.messageBus.connect(this)
        connection.subscribe(VirtualFileManager.VFS_CHANGES, this)
        logger.info("VFS_LISTENER: Subscribed to VFS_CHANGES for project ${project.name}")
    }

    override fun after(events: MutableList<out VFileEvent>) {
        // This listener receives events for all projects, so we must filter for relevance to this specific project.
        var isDirty = false
        val fileIndex = ProjectFileIndex.getInstance(project)

        for (event in events) {
            // We only care about events that could change the git status.
            if (!(event is VFileCreateEvent || event is VFileDeleteEvent ||
                        event is VFileContentChangeEvent || event is VFileMoveEvent || event is VFileCopyEvent)) {
                continue
            }

            // Check if the file related to the event is part of this project's content.
            val file = event.file
            if (file != null) {
                if (fileIndex.isInContent(file)) {
                    isDirty = true
                    break // Found one relevant event, no need to check the rest of the list.
                }
            } else {
                // The file object can be null (e.g., for a deletion). Fall back to checking the path.
                val path = event.path
                val projectPath = project.basePath
                if (projectPath != null && path.startsWith(projectPath)) {
                    isDirty = true
                    break // Found one relevant event.
                }
            }
        }

        if (isDirty) {
            logger.info("VFS_LISTENER: Detected relevant VFS change(s) for project ${project.name}. Marking VCS dirty.")
            // This is the standard way to tell the VCS subsystem that file statuses may need re-checking.
            // This will trigger other listeners, like the one in our ChangesBrowser, to refresh.
            VcsDirtyScopeManager.getInstance(project).markEverythingDirty()

            // This topic is for our own components that might need a more immediate/custom notification.
            project.messageBus.syncPublisher(FILE_CHANGES_TOPIC).onFilesChanged()
        }
    }

    override fun dispose() {
        logger.info("VFS_LISTENER: Disposing for project ${project.name}. Message bus connection will be disposed automatically.")
    }
}
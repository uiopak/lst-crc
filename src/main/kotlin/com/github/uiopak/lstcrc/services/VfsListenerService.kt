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

/**
 * Listens for low-level VFS events (file creation, deletion, content changes) and triggers
 * a refresh of the VCS status. It filters events to act only on those relevant to the current
 * project. By calling [VcsDirtyScopeManager.markEverythingDirty], it ensures that the IDE's
 * change detection mechanism will run, which in turn triggers [com.github.uiopak.lstcrc.listeners.VcsChangeListener]
 * to update the plugin's data.
 */
@Service(Service.Level.PROJECT)
class VfsListenerService(private val project: Project) : BulkFileListener, Disposable {

    private val logger = thisLogger()

    init {
        logger.info("VFS_LISTENER: Initializing for project ${project.name}")
        // The connection will be automatically disposed when this service (a Disposable) is disposed.
        project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, this)
        logger.info("VFS_LISTENER: Subscribed to VFS_CHANGES for project ${project.name}")
    }

    override fun after(events: MutableList<out VFileEvent>) {
        // This listener receives events for all projects, so we must filter for relevance.
        var isDirty = false
        val fileIndex = ProjectFileIndex.getInstance(project)

        for (event in events) {
            if (!(event is VFileCreateEvent || event is VFileDeleteEvent ||
                        event is VFileContentChangeEvent || event is VFileMoveEvent || event is VFileCopyEvent)) {
                continue
            }

            val file = event.file
            if (file != null) {
                if (fileIndex.isInContent(file)) {
                    isDirty = true
                    break // A single relevant event is enough to trigger a refresh.
                }
            } else {
                // The file object can be null (e.g., for a deletion). Fall back to checking the path.
                val path = event.path
                val projectPath = project.basePath
                if (projectPath != null && path.startsWith(projectPath)) {
                    isDirty = true
                    break
                }
            }
        }

        if (isDirty) {
            logger.info("VFS_LISTENER: Detected relevant VFS change(s) for project ${project.name}. Marking VCS dirty.")
            // This is the standard way to tell the VCS subsystem that file statuses may need re-checking.
            // This will in turn trigger our VcsChangeListener to refresh the diff data.
            VcsDirtyScopeManager.getInstance(project).markEverythingDirty()

            // This topic is for our own components that might need a more immediate/custom notification.
            project.messageBus.syncPublisher(FILE_CHANGES_TOPIC).onFilesChanged()
        }
    }

    override fun dispose() {
        logger.info("VFS_LISTENER: Disposing for project ${project.name}. Message bus connection will be disposed automatically.")
    }
}
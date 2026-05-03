package com.github.uiopak.lstcrc.services

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
 * Marks VCS dirty after relevant VFS file events so ChangeList updates are emitted
 * quickly and UI consumers can refresh from the latest working-tree state.
 */
@Service(Service.Level.PROJECT)
class VfsListenerService(private val project: Project) : BulkFileListener, Disposable {

    private val logger = thisLogger()

    init {
        logger.info("VFS_LISTENER: Initializing for project ${project.name}")
        project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, this)
        logger.info("VFS_LISTENER: Subscribed to VFS_CHANGES for project ${project.name}")
    }

    override fun after(events: MutableList<out VFileEvent>) {
        var isDirty = false
        val fileIndex = ProjectFileIndex.getInstance(project)

        for (event in events) {
            if (event !is VFileCreateEvent &&
                event !is VFileDeleteEvent &&
                event !is VFileContentChangeEvent &&
                event !is VFileMoveEvent &&
                event !is VFileCopyEvent
            ) {
                continue
            }

            val file = event.file
            if (file != null) {
                if (fileIndex.isInContent(file)) {
                    isDirty = true
                    break
                }
            } else {
                val path = event.path
                val projectPath = project.basePath
                if (projectPath != null && path.startsWith(projectPath)) {
                    isDirty = true
                    break
                }
            }
        }

        if (isDirty) {
            logger.info("VFS_LISTENER: Relevant VFS changes detected. Marking VCS dirty for ${project.name}.")
            VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
        }
    }

    override fun dispose() {
        logger.info("VFS_LISTENER: Disposing for project ${project.name}")
    }
}
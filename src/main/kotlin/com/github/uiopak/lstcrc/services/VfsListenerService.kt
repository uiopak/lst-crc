package com.github.uiopak.lstcrc.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.components.Service // Added import

@Service(Service.Level.PROJECT) // Added annotation
class VfsListenerService(private val project: Project) : Disposable {

    private val logger = thisLogger()

    init {
        logger.info("VfsListenerService INITIALIZED for project ${project.name}")
        registerVfsListener()
    }

    private fun registerVfsListener() {
        val vfsChangeListenerInstance = VfsChangeListener() // This will trigger its own "INSTANTIATED" log

        // The connection will be automatically disposed when this service (Disposable) is disposed.
        val connection = project.messageBus.connect(this)
        connection.subscribe(VirtualFileManager.VFS_CHANGES, vfsChangeListenerInstance as BulkFileListener)
        logger.info("VfsListenerService: VfsChangeListener subscribed to VFS_CHANGES for project ${project.name}")
    }

    override fun dispose() {
        logger.info("VfsListenerService: Disposing for project ${project.name}. Message bus connection will be disposed automatically.")
        // No need to explicitly call connection.dispose() as it's tied to this Disposable.
    }
}

package com.github.uiopak.lstcrc.services

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener // Required for VFS_CHANGES topic type

class PluginStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val vfsChangeListenerInstance = VfsChangeListener() // VfsChangeListener is in the same package

        // A project is a Disposable, so it can be used as the parent disposable for the connection.
        // Subscribing to VFS_CHANGES requires a BulkFileListener.
        val connection = project.messageBus.connect(project)
        connection.subscribe(VirtualFileManager.VFS_CHANGES, vfsChangeListenerInstance as BulkFileListener) // Explicit cast

        // Removed the thisLogger().error(...) line
    }
}

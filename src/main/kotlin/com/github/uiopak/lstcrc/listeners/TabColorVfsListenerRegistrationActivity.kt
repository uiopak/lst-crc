package com.github.uiopak.lstcrc.listeners

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFileManager // Ensure this specific import for VFS_CHANGES
import com.intellij.openapi.Disposable // Ensure this is imported if project is used as disposable directly in connect

// If project itself is not directly a Disposable for connect, get a service that is, or use Application as parent
// However, Project itself is a Disposable.

class TabColorVfsListenerRegistrationActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        // ProjectActivity is a suspend function, listener subscription is not.
        // For simplicity, ensure this runs on a suitable context if needed,
        // but messageBus subscription itself is usually fine from here.

        // The project itself is a Disposable, so it can be used as the parent disposable for the connection.
        // This ensures the connection is closed when the project is disposed.
        project.messageBus.connect(project as Disposable).subscribe(VirtualFileManager.VFS_CHANGES, TabColorVfsListener(project))
    }
}

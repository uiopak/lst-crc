package com.github.uiopak.lstcrc.services

// Keep essential imports for class declaration and logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project // Keep if used in other methods, or if class needs it for other reasons
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
// Remove other imports if they are only used by the now-commented-out logic
// For example:
// import com.github.uiopak.lstcrc.messaging.FILE_CHANGES_TOPIC
// import com.intellij.openapi.project.ProjectLocator
// import com.intellij.openapi.project.ProjectManager
// import com.intellij.openapi.roots.ProjectFileIndex
// import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
// import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
// etc.

class VfsChangeListener : BulkFileListener {
    private val logger = thisLogger()

    init {
        // This log is fine, it indicates instantiation which might be useful.
        logger.info("OLD_VFS_LISTENER (.services): VfsChangeListener INSTANTIATED. This listener is expected to be obsolete for tab coloring.")
    }

    override fun before(events: MutableList<out VFileEvent>) { // Or List<VFileEvent> if that's the actual interface
        logger.warn("OLD_VFS_LISTENER (.services): VfsChangeListener.before() CALLED. Events: ${events.size}. This method should be inert.")
        // All logic commented out or removed
    }

    override fun after(events: MutableList<out VFileEvent>) { // Or List<VFileEvent>
        logger.warn("OLD_VFS_LISTENER (.services): VfsChangeListener.after() CALLED. This listener is expected to be obsolete for tab coloring. Events: ${events.size}")
        // All other lines of code, variable declarations, loops, etc., are removed or commented out.
        // For example:
        // // val projectsToRefresh = mutableSetOf<Project>()
        // // for (event in events) {
        // //    // ...
        // // }
        // // projectsToRefresh.forEach { project ->
        // //    // ...
        // // }
    }
}

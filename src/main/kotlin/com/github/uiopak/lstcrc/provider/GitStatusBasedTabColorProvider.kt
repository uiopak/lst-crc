package com.github.uiopak.lstcrc.provider

import com.github.uiopak.lstcrc.services.ProjectActiveDiffDataService
import com.github.uiopak.lstcrc.settings.TabColorSettingsState
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.impl.EditorTabColorProvider // Corrected import for EditorTabColorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import java.awt.Color

class GitStatusBasedTabColorProvider : EditorTabColorProvider {
    private val logger = thisLogger()

    override fun getEditorTabColor(project: Project, file: VirtualFile): Color? {
        val settings = TabColorSettingsState.getInstance(project)
        if (!settings.isTabColoringEnabled) {
            // logger.debug("Tab coloring disabled in settings for file ${file.path}")
            return null
        }

        val diffDataService = project.service<ProjectActiveDiffDataService>()

        if (diffDataService.activeBranchName == null) {
            // logger.debug("No active branch data in ProjectActiveDiffDataService for file ${file.path}, no color.")
            return null // No active branch data for coloring
        }

        // logger.debug("File: ${file.path}, Active branch for coloring: ${diffDataService.activeBranchName}, searching in ${diffDataService.activeChanges.size} changes.")

        val changeForFile = diffDataService.activeChanges.find { change ->
            // Check if the file in the editor tab matches either the 'before' or 'after' state's virtual file
            // ContentRevision.getFile() returns FilePath, then .getVirtualFile()
            // It's safer to compare VirtualFile objects directly if available and valid.
            val afterVf = change.afterRevision?.file?.virtualFile
            val beforeVf = change.beforeRevision?.file?.virtualFile

            (afterVf != null && afterVf.isValid && afterVf == file) || (beforeVf != null && beforeVf.isValid && beforeVf == file)
        }

        if (changeForFile == null) {
            // logger.debug("No specific change found for file ${file.path} in active diff for branch ${diffDataService.activeBranchName}.")
            return null
        }

        // logger.info("Change found for file ${file.path}: type ${changeForFile.type}, in branch ${diffDataService.activeBranchName}")

        val colorHex = when (changeForFile.type) {
            Change.Type.NEW -> "#62B543"        // IntelliJ Green for Added
            Change.Type.MODIFICATION -> "#3684CB" // IntelliJ Blue for Modified
            Change.Type.MOVED -> "#3684CB"      // Treat MOVED as MODIFIED (Blue)
            Change.Type.DELETED -> "#B93437"    // IntelliJ Red for Deleted (Darker Red)
            else -> {
                // logger.debug("Unhandled change type ${changeForFile.type} for file ${file.path}")
                null
            }
        }

        return colorHex?.let { hex ->
            try {
                Color.decode(hex)
            } catch (e: NumberFormatException) {
                logger.warn("Failed to decode color hex '$hex' for file ${file.path}", e)
                null
            }
        }
    }
}

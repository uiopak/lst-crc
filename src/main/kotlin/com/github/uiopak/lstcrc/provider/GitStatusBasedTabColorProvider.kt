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
        logger.info("PROVIDER_GET_COLOR: getEditorTabColor for project: '${project.name}', file: '${file.path}'")

        val settings = TabColorSettingsState.getInstance(project)
        logger.info("PROVIDER_GET_COLOR: Tab coloring enabled: ${settings.isTabColoringEnabled}")
        if (!settings.isTabColoringEnabled) {
            return null
        }

        val diffDataService = project.service<ProjectActiveDiffDataService>()
        logger.info("PROVIDER_GET_COLOR: DiffDataService active branch: '${diffDataService.activeBranchName}', changes count: ${diffDataService.activeChanges.size}")
        if (diffDataService.activeBranchName == null) {
            return null // No active branch data for coloring
        }

        val changeForFile = diffDataService.activeChanges.find { change ->
            val afterVf = change.afterRevision?.file?.virtualFile
            val beforeVf = change.beforeRevision?.file?.virtualFile
            (afterVf != null && afterVf.isValid && afterVf == file) || (beforeVf != null && beforeVf.isValid && beforeVf == file)
        }

        if (changeForFile == null) {
            logger.info("PROVIDER_GET_COLOR: No specific change found for file '${file.path}' in active diff for branch '${diffDataService.activeBranchName}'.")
            return null
        }

        logger.info("PROVIDER_GET_COLOR: Change found for file '${file.path}': type ${changeForFile.type}, in branch '${diffDataService.activeBranchName}'.")
        val colorHex = when (changeForFile.type) {
            Change.Type.NEW -> "#62B543"        // IntelliJ Green for Added
            Change.Type.MODIFICATION -> "#3684CB" // IntelliJ Blue for Modified
            Change.Type.MOVED -> "#3684CB"      // Treat MOVED as MODIFIED (Blue)
            Change.Type.DELETED -> "#B93437"    // IntelliJ Red for Deleted (Darker Red)
            else -> {
                logger.info("PROVIDER_GET_COLOR: Unhandled change type ${changeForFile.type} for file ${file.path}")
                null
            }
        }
        logger.info("PROVIDER_GET_COLOR: Determined colorHex: '$colorHex' for file '${file.path}'.")

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

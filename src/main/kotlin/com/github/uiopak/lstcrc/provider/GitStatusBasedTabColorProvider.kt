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

        // Get the FileStatus object from the Change
        val fileStatus = changeForFile.fileStatus // Removed explicit type FileStatus for brevity
        logger.info("PROVIDER_GET_COLOR: File '${file.path}' has status ${fileStatus.id} in branch '${diffDataService.activeBranchName}'.")

        // Get the theme-aware color from the FileStatus
        val themedColor: Color? = fileStatus.color

        if (themedColor != null) {
            logger.info("PROVIDER_GET_COLOR: Using theme color ${themedColor} for status ${fileStatus.id} on file '${file.path}'.")
            return themedColor
        } else {
            // This case should be rare for standard statuses like ADDED, MODIFIED, DELETED,
            // as they usually have a color defined. If a status genuinely has no theme color,
            // then no color will be applied by this provider.
            logger.info("PROVIDER_GET_COLOR: No theme color defined for status ${fileStatus.id} for file '${file.path}'. Returning null.")
            return null
        }
    }
}

package com.github.uiopak.lstcrc.provider

import com.github.uiopak.lstcrc.services.ProjectActiveDiffDataService
import com.github.uiopak.lstcrc.settings.TabColorSettingsState
import com.intellij.openapi.components.service
import com.github.uiopak.lstcrc.services.ProjectActiveDiffDataService
import com.github.uiopak.lstcrc.settings.TabColorSettingsState
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.impl.EditorTabColorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatus // Added for explicit enum access
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor // Added for JBColor.namedColor
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

        val fileStatus = changeForFile.fileStatus
        logger.info("PROVIDER_GET_COLOR: File '${file.path}' has status ${fileStatus.id} in branch '${diffDataService.activeBranchName}'.")

        val colorToReturn: Color? = when (fileStatus) {
            FileStatus.MODIFIED,
            FileStatus.MERGE -> { // MOVED is typically FileStatus.MODIFIED. MERGE is often a conflict state.
                // Using a key often associated with a prominent blue action button
                JBColor.namedColor("Plugins.Button.installFillBackground",
                                   JBColor(Color(0x36, 0x84, 0xCB), Color(0x36, 0x84, 0xCB)))
            }
            FileStatus.ADDED -> {
                // Using a key often associated with success/positive feedback
                JBColor.namedColor("Banner.successBackground",
                                   JBColor(Color(0x62, 0xB5, 0x43), Color(0x62, 0xB5, 0x43)))
            }
            FileStatus.DELETED -> {
                // Using a key often associated with errors/negative feedback
                JBColor.namedColor("Banner.errorBackground",
                                   JBColor(Color(0xB9, 0x34, 0x37), Color(0xB9, 0x34, 0x37)))
            }
            // Consider other statuses if necessary, e.g., FileStatus.MERGED_WITH_CONFLICTS might need a different color like orange/yellow.
            // For now, only handling the main ones explicitly requested or previously covered.
            else -> {
                logger.info("PROVIDER_GET_COLOR: Status ${fileStatus.id} for file '${file.path}' is not mapped to a specific theme color concept. No color will be applied.")
                null // No color for other statuses
            }
        }

        if (colorToReturn != null) {
            logger.info("PROVIDER_GET_COLOR: Determined theme-based color: ${colorToReturn} for file '${file.path}' with status ${fileStatus.id}.")
        }
        return colorToReturn
    }
}

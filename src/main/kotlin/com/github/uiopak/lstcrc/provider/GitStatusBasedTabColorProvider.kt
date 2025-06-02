package com.github.uiopak.lstcrc.provider

import com.github.uiopak.lstcrc.services.ProjectActiveDiffDataService
import com.github.uiopak.lstcrc.settings.TabColorSettingsState
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.impl.EditorTabColorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import java.awt.Color

class GitStatusBasedTabColorProvider : EditorTabColorProvider {
    private val logger = thisLogger()

    // Default colors based on Git status
    private val defaultColorMappings = mapOf(
        Change.Type.NEW to "#273828",         // IntelliJ Green for Added
        Change.Type.MODIFICATION to "#1d3d3b",  // IntelliJ Blue for Modified
        Change.Type.MOVED to "#35363b",       // Similar to Modified (Blueish/Grey)
        Change.Type.DELETED to "#472b2b"      // IntelliJ Red for Deleted (Darker Red)
    )

    private fun parseHexColor(hexColor: String?, filePathForLogging: String): Color? {
        return hexColor?.let { hex ->
            try {
                Color.decode(hex)
            } catch (e: NumberFormatException) {
                logger.warn("PROVIDER: Failed to decode color hex '$hex' for file $filePathForLogging", e)
                null
            }
        }
    }

    override fun getEditorTabColor(project: Project, file: VirtualFile): Color? {
        logger.trace("PROVIDER: Evaluating tab color for project: '${project.name}', file: '${file.path}'")

        val settings = TabColorSettingsState.getInstance(project)
        if (!settings.isTabColoringEnabled) {
            logger.trace("PROVIDER: Tab coloring disabled in settings.")
            return null
        }

        // Background Color Logic
        if (!settings.useDefaultBackgroundColor) {
            logger.trace("PROVIDER: Using custom background color. Provided hex: '${settings.tabBackgroundColor}'")
            return settings.tabBackgroundColor?.let {
                parseHexColor(it, file.path)
            } ?: run {
                logger.trace("PROVIDER: Custom background color is null, no color applied.")
                null
            }
        }

        // Default background color logic (based on Git status)
        logger.trace("PROVIDER: Using default background color based on Git status.")
        val diffDataService = project.service<ProjectActiveDiffDataService>()
        if (diffDataService.activeBranchName == null) {
            logger.trace("PROVIDER: No active branch data available in DiffDataService.")
            return null // No active branch data for coloring
        }

        val changeForFile = diffDataService.activeChanges.find { change ->
            val afterVf = change.afterRevision?.file?.virtualFile
            val beforeVf = change.beforeRevision?.file?.virtualFile
            (afterVf != null && afterVf.isValid && afterVf == file) || (beforeVf != null && beforeVf.isValid && beforeVf == file)
        }

        if (changeForFile == null) {
            logger.trace("PROVIDER: No specific Git change found for file '${file.path}' in branch '${diffDataService.activeBranchName}'.")
            return null
        }

        logger.trace("PROVIDER: Git change found for file '${file.path}': type ${changeForFile.type}.")
        val colorHex = defaultColorMappings[changeForFile.type]

        if (colorHex == null) {
            logger.trace("PROVIDER: No default color mapping for change type ${changeForFile.type} for file ${file.path}")
            return null
        }

        logger.trace("PROVIDER: Determined default background colorHex: '$colorHex' for file '${file.path}'.")
        return parseHexColor(colorHex, file.path)

        // Border Color Logic:
        // As per research, applying borders directly via EditorTabColorProvider is not straightforward.
        // This section would require a different approach, possibly interacting with tab UI components directly
        // or using a different extension point (e.g., a TabPainter).
        // This will be noted in the subtask report.
        // Example of what it might look like if settings.borderSide != "NONE":
        // val borderColorToApply = if (settings.useDefaultBorderColor) {
        //     // determine default border color from git status, potentially a new map
        // } else {
        //     parseHexColor(settings.borderColor, file.path)
        // }
        // If (borderColorToApply != null) { /* ... apply border ... */ }
    }
}

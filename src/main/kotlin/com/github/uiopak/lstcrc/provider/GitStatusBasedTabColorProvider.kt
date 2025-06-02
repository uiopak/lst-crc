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

    // Updated parseHexColor function for robustness
    private fun parseHexColor(hex: String?, contextFilePath: String? = null): Color? {
        val context = if (contextFilePath != null) " for file $contextFilePath" else "" // Added space for better formatting
        if (hex.isNullOrBlank()) {
            logger.trace("PROVIDER: parseHexColor: Input hex is null or blank$context.")
            return null
        }

        var processedHex = hex.trim()

        if (!processedHex.startsWith("#")) {
            // Regex for 6-digit hex or 3-digit hex (shorthand)
            if (processedHex.matches(Regex("^[0-9a-fA-F]{6}$")) || processedHex.matches(Regex("^[0-9a-fA-F]{3}$"))) {
                logger.trace("PROVIDER: parseHexColor: Input hex '$processedHex'$context is missing '#', prepending.")
                processedHex = "#$processedHex"
            } else {
                logger.warn("PROVIDER: parseHexColor: Input hex '$processedHex'$context is malformed (not 3 or 6 hex digits) and does not start with '#'.")
                return null
            }
        }

        return try {
            Color.decode(processedHex)
        } catch (e: NumberFormatException) {
            logger.warn("PROVIDER: parseHexColor: Failed to decode color hex '$processedHex'$context.", e)
            null
        }
    }


    override fun getEditorTabColor(project: Project, file: VirtualFile): Color? {
        // Phase 1 Logging:
        logger.info("PROVIDER: Invoked for project: '${project.name}' (hashCode: ${project.hashCode()}), file: '${file.path}'")

        val settings = TabColorSettingsState.getInstance(project)
        logger.info("PROVIDER: Loaded settings state for project '${project.name}':")
        logger.info("  isTabColoringEnabled: ${settings.isTabColoringEnabled}")
        logger.info("  useDefaultBackgroundColor: ${settings.useDefaultBackgroundColor}")
        logger.info("  tabBackgroundColor (override): ${settings.tabBackgroundColor}")
        logger.info("  newFileColor: ${settings.newFileColor}")
        logger.info("  modifiedFileColor: ${settings.modifiedFileColor}")
        logger.info("  deletedFileColor: ${settings.deletedFileColor}")
        logger.info("  movedFileColor: ${settings.movedFileColor}")
        logger.info("  borderSide: ${settings.borderSide}")
        // logger.info("  borderColor: ${settings.borderColor}") // For future border logic
        // logger.info("  useDefaultBorderColor: ${settings.useDefaultBorderColor}") // For future border logic

        if (!settings.isTabColoringEnabled) {
            logger.info("PROVIDER: Tab coloring disabled in settings. Returning null.")
            return null
        }

        val borderSide = settings.borderSide
        // Check if border highlighting is active for this tab
        if (!borderSide.isNullOrBlank() && borderSide != "NONE") {
            logger.info("PROVIDER: Border highlighting is active (side: $borderSide) for file '${file.path}'. Returning null for background color.")
            return null // Do not provide a background color if a border is to be shown
        }

        var determinedColorSource = "Unknown" // To track where the color decision came from
        var finalColorHexToParse: String? = null

        // Background Color Logic
        if (!settings.useDefaultBackgroundColor) {
            finalColorHexToParse = settings.tabBackgroundColor
            determinedColorSource = if (finalColorHexToParse != null) "Single Override Color" else "Single Override Color (null)"
            logger.info("PROVIDER: Using single override color logic. Color hex: '$finalColorHexToParse'. Source: $determinedColorSource")
        } else {
            logger.info("PROVIDER: Using Git status to determine background color for file '${file.path}'.")
            val diffDataService = project.service<ProjectActiveDiffDataService>()
            if (diffDataService.activeBranchName == null) {
                logger.info("PROVIDER: No active branch data in DiffDataService for file '${file.path}'. Returning null.")
                return null // No active branch data for coloring
            }

            val changeForFile = diffDataService.activeChanges.find { change ->
                val afterVf = change.afterRevision?.file?.virtualFile
                val beforeVf = change.beforeRevision?.file?.virtualFile
                (afterVf != null && afterVf.isValid && afterVf == file) || (beforeVf != null && beforeVf.isValid && beforeVf == file)
            }

            if (changeForFile == null) {
                logger.info("PROVIDER: No specific Git change found for file '${file.path}' in branch '${diffDataService.activeBranchName}'. Returning null.")
                return null
            }

            val statusType = changeForFile.type
            logger.info("PROVIDER: Git change type for file '${file.path}' is $statusType.")

            val userDefinedColorHex = when (statusType) {
                Change.Type.NEW -> settings.newFileColor
                Change.Type.MODIFICATION -> settings.modifiedFileColor
                Change.Type.DELETED -> settings.deletedFileColor
                Change.Type.MOVED -> settings.movedFileColor
                else -> {
                    logger.info("PROVIDER: Unhandled Git change type $statusType for file '${file.path}'.")
                    null
                }
            }

            if (!userDefinedColorHex.isNullOrBlank()) {
                finalColorHexToParse = userDefinedColorHex
                determinedColorSource = "User-defined for status $statusType"
                logger.info("PROVIDER: Using user-defined color for status $statusType: '$finalColorHexToParse' for file '${file.path}'. Source: $determinedColorSource")
            } else {
                finalColorHexToParse = defaultColorMappings[statusType]
                if (finalColorHexToParse != null) {
                    determinedColorSource = "Factory default for status $statusType"
                    logger.info("PROVIDER: User-defined color for $statusType is not set/blank. Using factory default: '$finalColorHexToParse' for file '${file.path}'. Source: $determinedColorSource")
                } else {
                    determinedColorSource = "No user-defined and no factory default for status $statusType"
                    logger.info("PROVIDER: No user-defined and no factory default for status $statusType for file '${file.path}'. No color to apply. Source: $determinedColorSource")
                }
            }
        }
        
        val resultColor = parseHexColor(finalColorHexToParse, file.path)
        logger.info("PROVIDER: Final chosen hex: '$finalColorHexToParse'. Parsed color: ${resultColor?.toString() ?: "null"}. Source: $determinedColorSource. File: '${file.path}'")
        return resultColor

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

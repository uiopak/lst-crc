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
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorComposite // May need to be more generic like JComponent or specific EditorWithProviderComposite
import com.intellij.util.ui.JBUI
import com.intellij.ui.JBColor
import javax.swing.JComponent


class GitStatusBasedTabColorProvider : EditorTabColorProvider {
    private val logger = thisLogger()
    // private val appliedBorders = mutableSetOf<VirtualFile>() // Commented out for testing

    // Default colors based on Git status (Commented out for testing)
    /*
    private val defaultColorMappings = mapOf(
        Change.Type.NEW to "#273828",         // IntelliJ Green for Added
        Change.Type.MODIFICATION to "#1d3d3b",  // IntelliJ Blue for Modified
        Change.Type.MOVED to "#35363b",       // Similar to Modified (Blueish/Grey)
        Change.Type.DELETED to "#472b2b"      // IntelliJ Red for Deleted (Darker Red)
    )
    */

    // Updated parseHexColor function for robustness (Commented out for testing)
    /*
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
    */
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
        logger.info("PROVIDER_ENTRY: getEditorTabColor called for project: '${project.name}', file: '${file.name}' (path: ${file.path})")
        
        logger.info("PROVIDER_TEST_MODE: Returning hardcoded ORANGE for ${file.name}")
        return JBColor.ORANGE

        // All previous logic commented out for this test:
        /*
        val settings = TabColorSettingsState.getInstance(project)
        logger.info("PROVIDER: Loaded settings state for project '${project.name}':")
        logger.info("  isTabColoringEnabled: ${settings.isTabColoringEnabled}")
        logger.info("  useDefaultBackgroundColor: ${settings.useDefaultBackgroundColor}")
        logger.info("  tabBackgroundColor (override): ${settings.tabBackgroundColor}")
        logger.info("  newFileColor: ${settings.newFileColor}")
        logger.info("  modifiedFileColor: ${settings.modifiedFileColor}")
        logger.info("  deletedFileColor: ${settings.deletedFileColor}")
        logger.info("  movedFileColor: ${settings.movedFileColor}")

        if (!settings.isTabColoringEnabled) {
            logger.info("PROVIDER: Tab coloring disabled in settings. Returning null.")
            return null
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
                finalColorHexToParse = defaultColorMappings[statusType] // defaultColorMappings is commented out
                if (finalColorHexToParse != null) {
                    determinedColorSource = "Factory default for status $statusType"
                    logger.info("PROVIDER: User-defined color for $statusType is not set/blank. Using factory default: '$finalColorHexToParse' for file '${file.path}'. Source: $determinedColorSource")
                } else {
                    determinedColorSource = "No user-defined and no factory default for status $statusType"
                    logger.info("PROVIDER: No user-defined and no factory default for status $statusType for file '${file.path}'. No color to apply. Source: $determinedColorSource")
                }
            }
        }
        
        val resultColor = parseHexColor(finalColorHexToParse, file.path) // parseHexColor is commented out
        logger.info("PROVIDER: Final chosen hex: '$finalColorHexToParse'. Parsed color: ${resultColor?.toString() ?: "null"}. Source: $determinedColorSource. File: '${file.path}'")
        
        // --- PoC: Try to apply border ---
        logger.info("PROVIDER_POC_CHECK: Checking border PoC eligibility for file: '${file.name}' (path: ${file.path})")
        // logger.info("PROVIDER_POC_CHECK: Current appliedBorders set: ${appliedBorders.map { it.name }.joinToString()}") // appliedBorders is commented out
        // logger.info("PROVIDER_POC_CHECK: Does appliedBorders contain '${file.name}'? ${appliedBorders.contains(file)}") // appliedBorders is commented out

        // if (!appliedBorders.contains(file)) { // appliedBorders is commented out
            try {
                logger.info("POC_BORDER: Attempting to find and border tab for ${file.name} (path: ${file.path})")
                val fem = FileEditorManager.getInstance(project) as? FileEditorManagerEx
                if (fem == null) {
                    logger.info("POC_BORDER: FileEditorManagerEx is null.")
                } else {
                    var foundMatchingComposite: JComponent? = null
                    
                    for (window in fem.windows) {
                        logger.info("POC_BORDER: Checking window: ${window.javaClass.name}. Number of editors in window: ${window.tabCount}") 
                        window.allComposites.forEachIndexed { index, composite -> 
                            logger.info("POC_BORDER:   Editor composite $index: ${composite.javaClass.name}, File: ${composite.file?.path}")
                            if (composite.file == file) {
                                logger.info("POC_BORDER:     Found matching EditorComposite for ${file.path}")
                                if (composite is JComponent) {
                                    foundMatchingComposite = composite
                                    logger.info("POC_BORDER:       EditorComposite is a JComponent. Class: ${composite::class.java.name}")
                                } else {
                                    logger.info("POC_BORDER:       EditorComposite is NOT a JComponent. Class: ${composite::class.java.name}")
                                }
                            }
                        }
                        if (foundMatchingComposite != null) break 
                    }

                    if (foundMatchingComposite != null) {
                        logger.info("POC_BORDER: Applying border to ${foundMatchingComposite?.javaClass?.name} for ${file.path}")
                        foundMatchingComposite?.setBorder(JBUI.Borders.customLine(JBColor.RED, 2, 0, 0, 0)) 
                        foundMatchingComposite?.repaint() 
                        // appliedBorders.add(file) // appliedBorders is commented out
                        logger.info("POC_BORDER: Border possibly applied for ${file.path}. Repaint called.")

                    } else {
                        logger.info("POC_BORDER: Did not find matching EditorComposite JComponent for ${file.path}")
                    }
                }
            } catch (e: Exception) {
                logger.error("POC_BORDER: Error during border application PoC for ${file.path}", e)
                // appliedBorders.add(file) // appliedBorders is commented out
            }
        // } else {
        //    logger.info("PROVIDER_POC_SKIP: Skipping border PoC for '${file.name}' (path: ${file.path}) because it's already in appliedBorders.")
        // }
        // --- End PoC ---

        return resultColor // This would be the original resultColor if not for the test
        */
    }
}

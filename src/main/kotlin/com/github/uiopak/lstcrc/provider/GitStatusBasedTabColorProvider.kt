package com.github.uiopak.lstcrc.provider

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.impl.EditorTabColorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.github.uiopak.lstcrc.services.GitService
import com.github.uiopak.lstcrc.settings.TabColorSettingsState
import java.awt.Color

class GitStatusBasedTabColorProvider : EditorTabColorProvider {

    private val logger = thisLogger()

    // Renamed from getColor to getEditorTabColor to match the interface (based on error)
    override fun getEditorTabColor(project: Project, file: VirtualFile): Color? {
        val settings = TabColorSettingsState.getInstance(project)
        if (!settings.isTabColoringEnabled) {
            logger.info("Tab coloring is disabled in settings. Skipping for file: ${file.name}")
            return null
        }

        // For now, colorTarget setting is noted but only background is affected by this provider.
        // If settings.colorTarget != "BACKGROUND", we might return null or log.
        // For this iteration, we proceed regardless of colorTarget, assuming background.
        logger.info("GitStatusBasedTabColorProvider.getEditorTabColor for ${file.path}, Target: ${settings.colorTarget}, Branch: ${settings.comparisonBranch}")


        val gitService = project.getService(GitService::class.java)
        if (gitService == null) {
            logger.warn("GitService not found for project ${project.name}. Cannot determine tab color.")
            return null
        }

        val colorHex = gitService.calculateEditorTabColor(file.path, settings.comparisonBranch)
        logger.info("Calculated color hex for ${file.name} (vs ${settings.comparisonBranch}): '$colorHex'")

        return if (colorHex.isNotBlank()) {
            try {
                Color.decode(colorHex)
            } catch (e: NumberFormatException) {
                logger.warn("Invalid hex color string: $colorHex for file ${file.name}", e)
                null
            }
        } else {
            null // No specific color, use default
        }
    }
}

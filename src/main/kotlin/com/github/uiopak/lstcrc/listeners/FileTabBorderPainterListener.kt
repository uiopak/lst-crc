package com.github.uiopak.lstcrc.listeners

import com.github.uiopak.lstcrc.services.ProjectActiveDiffDataService
import com.github.uiopak.lstcrc.settings.TabColorSettingsConfigurable
import com.github.uiopak.lstcrc.settings.TabColorSettingsState
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
// import com.intellij.openapi.diagnostic.thisLogger // No longer needed for instance logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ColorUtil
import java.awt.Color
import javax.swing.JComponent
import javax.swing.UIManager
import javax.swing.border.MatteBorder

class FileTabBorderPainterListener : FileEditorManagerListener {

    // Instance logger is no longer primary for core logic, but can be kept for instance-specific events if any.
    // private val logger = thisLogger() 

    companion object {
        private val LOG = Logger.getInstance(FileTabBorderPainterListener::class.java)

        // Helper to parse hex, similar to GitStatusBasedTabColorProvider
        internal fun parseHexColor(hex: String?, contextFilePath: String? = null): Color? {
            val context = if (contextFilePath != null) " for file $contextFilePath" else ""
            if (hex.isNullOrBlank()) {
                return null
            }
            var processedHex = hex.trim()
            if (!processedHex.startsWith("#")) {
                if (processedHex.matches(Regex("^[0-9a-fA-F]{6}$")) || processedHex.matches(Regex("^[0-9a-fA-F]{3}$"))) {
                    processedHex = "#$processedHex"
                } else {
                    return null
                }
            }
            return try {
                ColorUtil.fromHex(processedHex)
            } catch (e: Exception) {
                null
            }
        }

        private val DEFAULT_BORDER_COLOR_MAPPINGS = mapOf(
            Change.Type.NEW to TabColorSettingsConfigurable.DEFAULT_BORDER_COLOR_NEW_HEX,
            Change.Type.MODIFICATION to TabColorSettingsConfigurable.DEFAULT_BORDER_COLOR_MODIFIED_HEX,
            Change.Type.DELETED to TabColorSettingsConfigurable.DEFAULT_BORDER_COLOR_DELETED_HEX,
            Change.Type.MOVED to TabColorSettingsConfigurable.DEFAULT_BORDER_COLOR_MOVED_HEX
        )

        internal fun findTabComponent(project: Project, file: VirtualFile): JComponent? {
            LOG.debug("Attempting to find tab component for file: ${file.path}")
            val fileEditorManager = FileEditorManager.getInstance(project)

            val selectedEditor = fileEditorManager.getSelectedEditor(file)
            if (selectedEditor != null) {
                val editorComponent = selectedEditor.component
                if (editorComponent != null) {
                    var current: JComponent? = editorComponent
                    var depth = 0
                    val maxDepth = 15

                    LOG.debug("Starting hierarchy traversal from selected editor component: ${editorComponent.javaClass.name} for ${file.path}")
                    while (current != null && depth < maxDepth) {
                        if (isTabComponent(current, file)) {
                            LOG.info("Potential tab component found for ${file.path}: ${current.javaClass.name} (via selected editor traversal)")
                            return current
                        }
                        current = current.parent as? JComponent
                        depth++
                    }
                }
            }
            LOG.debug("File ${file.path} is not the currently selected editor, or tab component not found via its hierarchy. Broad search needed.")
            LOG.warn("findTabComponent: Advanced search for non-selected tabs is not yet fully implemented. File: ${file.path}")
            return null
        }

        private fun isTabComponent(component: JComponent, file: VirtualFile): Boolean {
            val componentName = component.javaClass.name.lowercase()
            val isPotentialTabLabel = componentName.contains("tablabel") ||
                                    componentName.contains("tabinfo") ||
                                    componentName.contains("head") || 
                                    componentName.contains("header") ||
                                    componentName.contains("editortab") 

            if (isPotentialTabLabel) {
                if (component.getClientProperty(VirtualFile::class.java) == file ||
                    component.getClientProperty("virtualFile") == file) {
                    return true
                }
                if (checkFileInParents(component, file, 3)) {
                     return true
                }
            }
            return false
        }

        private fun checkFileInParents(component: JComponent, file: VirtualFile, maxDepth: Int): Boolean {
            var parent = component.parent as? JComponent
            var depth = 0
            while (parent != null && depth < maxDepth) {
                if (parent.getClientProperty(VirtualFile::class.java) == file ||
                    parent.getClientProperty("virtualFile") == file) {
                    return true
                }
                parent = parent.parent as? JComponent
                depth++
            }
            return false
        }

        internal fun clearBorderFromTab(project: Project, file: VirtualFile?) {
            if (file == null) return
            LOG.info("Attempting to clear border from tab for ${file.path}")
            val tabComponent = findTabComponent(project, file)
            if (tabComponent != null) {
                try {
                    tabComponent.border = UIManager.getBorder("EditorTabs.tab.border") ?: UIManager.getBorder("TabbedPane.tabBorder")
                    LOG.info("Cleared border from tab for ${file.path} (component: ${tabComponent.javaClass.name})")
                    tabComponent.repaint()
                } catch (e: Exception) {
                    LOG.error("Exception clearing border from ${tabComponent.javaClass.name} for ${file.path}", e)
                }
            } else {
                LOG.warn("Could not find tab component to clear border for ${file.path}")
            }
        }

        internal fun applyBorderToTab(project: Project, file: VirtualFile?) {
            if (file == null) return
            LOG.info("Attempting to apply border to tab for ${file.path}")

            val settings = project.service<TabColorSettingsState>()
            if (!settings.isTabColoringEnabled) {
                LOG.info("Tab coloring disabled. Clearing border for ${file.path}")
                clearBorderFromTab(project, file)
                return
            }

            val borderSide = settings.borderSide
            if (borderSide.isNullOrBlank() || borderSide == "NONE") {
                LOG.info("Border side is NONE or not set. Clearing border for ${file.path}")
                clearBorderFromTab(project, file)
                return
            }

            val diffDataService = project.service<ProjectActiveDiffDataService>()
            val changeForFile = diffDataService.activeChanges.find { change ->
                val afterVf = change.afterRevision?.file?.virtualFile
                val beforeVf = change.beforeRevision?.file?.virtualFile
                (afterVf != null && afterVf.isValid && afterVf == file) || (beforeVf != null && beforeVf.isValid && beforeVf == file)
            }
            val statusType: Change.Type? = changeForFile?.type
            LOG.debug("File: ${file.path}, Status: $statusType, BorderSide: $borderSide")

            val colorHex: String? = if (settings.useDefaultBorderColor) {
                when (statusType) {
                    Change.Type.NEW -> settings.newFileBorderColor ?: DEFAULT_BORDER_COLOR_MAPPINGS[Change.Type.NEW]
                    Change.Type.MODIFICATION -> settings.modifiedFileBorderColor ?: DEFAULT_BORDER_COLOR_MAPPINGS[Change.Type.MODIFICATION]
                    Change.Type.DELETED -> settings.deletedFileBorderColor ?: DEFAULT_BORDER_COLOR_MAPPINGS[Change.Type.DELETED]
                    Change.Type.MOVED -> settings.movedFileBorderColor ?: DEFAULT_BORDER_COLOR_MAPPINGS[Change.Type.MOVED]
                    else -> {
                        LOG.debug("Unknown or null status type for file ${file.path}, no default border color.")
                        null
                    }
                }
            } else {
                settings.borderColor
            }

            if (colorHex == null) {
                LOG.debug("No border color hex determined for file ${file.path}. Clearing border.")
                clearBorderFromTab(project, file)
                return
            }

            val borderColor = parseHexColor(colorHex, file.path)
            if (borderColor == null) {
                LOG.warn("Failed to parse border color hex '$colorHex' for file ${file.path}. Clearing border.")
                clearBorderFromTab(project, file)
                return
            }

            val tabComponent = findTabComponent(project, file)
            if (tabComponent == null) {
                LOG.warn("Could not find tab component to apply border for ${file.path}")
                return
            }

            val borderThickness = 2 
            val matteBorder = when (borderSide) {
                "TOP" -> MatteBorder(borderThickness, 0, 0, 0, borderColor)
                "BOTTOM" -> MatteBorder(0, 0, borderThickness, 0, borderColor)
                "LEFT" -> MatteBorder(0, borderThickness, 0, 0, borderColor)
                "RIGHT" -> MatteBorder(0, 0, 0, borderThickness, borderColor)
                else -> {
                    LOG.warn("Unknown border side: $borderSide for file ${file.path}")
                    null
                }
            }

            if (matteBorder != null) {
                try {
                    tabComponent.border = matteBorder
                    LOG.info("Applied $borderSide border (color: $borderColor) to tab for ${file.path} (component: ${tabComponent.javaClass.name})")
                    tabComponent.repaint()
                } catch (e: Exception) {
                    LOG.error("Exception applying border to ${tabComponent.javaClass.name} for ${file.path}", e)
                }
            } else {
                clearBorderFromTab(project, file)
            }
        }
    } // End of companion object

    override fun selectionChanged(event: FileEditorManagerEvent) {
        LOG.info("Selection changed: newFile='${event.newFile?.path}', oldFile='${event.oldFile?.path}'")
        val project = event.manager.project
        event.oldFile?.let { Companion.clearBorderFromTab(project, it) }
        event.newFile?.let { Companion.applyBorderToTab(project, it) }
    }

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        LOG.info("File opened: ${file.path}")
        Companion.applyBorderToTab(source.project, file)
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        LOG.info("File closed: ${file.path}")
        // No specific action needed here for now, as selectionChanged handles the newly selected tab.
        // If the closed tab was the one with the border, it's gone.
        // If another tab is selected, selectionChanged will handle it.
    }
}

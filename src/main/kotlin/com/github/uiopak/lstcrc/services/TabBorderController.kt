package com.github.uiopak.lstcrc.services

import com.github.uiopak.lstcrc.settings.TabColorSettingsConfigurable // For default color constants
import com.github.uiopak.lstcrc.settings.TabColorSettingsState
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager // Keep for listener signature
import com.github.uiopak.lstcrc.services.ProjectActiveDiffDataService
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorWindow 
import com.intellij.ui.tabs.TabInfo 
import com.intellij.ui.tabs.JBTabs // Added for JBTabs
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import java.awt.Color
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.UIManager
import javax.swing.plaf.BorderUIResource
// Removed: import com.intellij.openapi.fileEditor.impl.EditorTabbedContainer 


@Service(Service.Level.PROJECT)
class TabBorderController(private val project: Project) : FileEditorManagerListener, LafManagerListener, Disposable {

    init {
        thisLogger().info("TabBorderController initializing for project: ${project.name}")
        project.messageBus.connect(this).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this)
        ApplicationManager.getApplication().messageBus.connect(this).subscribe(LafManagerListener.TOPIC, this)
        thisLogger().info("TabBorderController initialized and listeners subscribed for project: ${project.name}")
    }

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        thisLogger().debug("File opened: ${file.path}, triggering border update.")
        updateAllTabBorders()
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        thisLogger().debug("File closed: ${file.path}, triggering border update.")
        updateAllTabBorders() 
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
        thisLogger().debug("Selection changed. Old file: ${event.oldFile?.path}, New file: ${event.newFile?.path}. Triggering border update.")
        updateAllTabBorders()
    }

    override fun lookAndFeelChanged(source: LafManager) {
        thisLogger().info("Look and Feel changed, triggering border update for all open tabs.")
        updateAllTabBorders()
    }

    fun updateAllTabBorders() {
        thisLogger().debug("updateAllTabBorders called for project: ${project.name}")
        invokeLater { // Ensure UI updates are on the EDT
            val settings = TabColorSettingsState.getInstance(project)
            if (!settings.isTabColoringEnabled) {
                thisLogger().debug("Tab coloring disabled, clearing borders from all tabs.")
                clearAllBorders()
                return@invokeLater
            }

            val fileEditorManager = FileEditorManagerEx.getInstanceEx(project)
            val diffDataService = project.service<ProjectActiveDiffDataService>()
            val activeChanges = diffDataService.activeChanges
            val activeBranchName = diffDataService.activeBranchName
            thisLogger().debug("Active branch: $activeBranchName, ${activeChanges.size} active changes.")

            val windows: Array<com.intellij.openapi.fileEditor.impl.EditorWindow> = fileEditorManager.windows.filterIsInstance<com.intellij.openapi.fileEditor.impl.EditorWindow>().toTypedArray()

            if (windows.isEmpty()) {
                thisLogger().debug("No editor windows found.")
                return@invokeLater
            }

            for (win in windows) {
                val tabbedPaneComponent = win.tabbedPane?.component ?: continue
                val jbTabs = tabbedPaneComponent as? JBTabs ?: continue // Cast to JBTabs

                thisLogger().debug("Processing window with ${jbTabs.tabCount} tabs.")
                for (tabInfo in jbTabs.tabs) { // Iterate List<TabInfo>
                    val tabComponent = jbTabs.getTabLabel(tabInfo) as? JComponent ?: continue // Get component for this TabInfo
                    
                    val file = tabInfo.getObject() as? VirtualFile // Get VirtualFile from TabInfo's object
                    
                    if (file == null) {
                        thisLogger().trace("TabInfo ${tabInfo.text} has no VirtualFile, clearing its border.")
                        clearBorderForTab(tabComponent)
                        continue
                    }
                    thisLogger().trace("Processing tab for file: ${file.path}") // file is smart-cast to non-null

                    if (settings.borderSide.isNullOrBlank() || settings.borderSide == "NONE") {
                        thisLogger().trace("Border side is NONE for ${file.path}, clearing border.")
                        clearBorderForTab(tabComponent)
                        continue
                    }
                    
                    if (activeBranchName == null) {
                        thisLogger().trace("No active branch selected in plugin context, leaving border as is for ${file.path}.")
                        continue 
                    }

                    val changeForFile = activeChanges.find { change ->
                        val afterVf = change.afterRevision?.file?.virtualFile
                        val beforeVf = change.beforeRevision?.file?.virtualFile
                        (afterVf != null && afterVf.isValid && afterVf == file) || (beforeVf != null && beforeVf.isValid && beforeVf == file)
                    }

                    if (changeForFile == null) {
                        thisLogger().trace("No active change for ${file.path} in branch $activeBranchName, clearing border.")
                        clearBorderForTab(tabComponent)
                        continue
                    }
                    
                    val statusType = changeForFile.type // Safe: changeForFile is not null here
                    var finalColorHex: String? = null
                    if (settings.useDefaultBorderColor) {
                        finalColorHex = when (statusType) {
                            Change.Type.NEW -> settings.newFileBorderColor ?: TabColorSettingsConfigurable.DEFAULT_BORDER_COLOR_NEW_HEX
                            Change.Type.MODIFICATION -> settings.modifiedFileBorderColor ?: TabColorSettingsConfigurable.DEFAULT_BORDER_COLOR_MODIFIED_HEX
                            Change.Type.DELETED -> settings.deletedFileBorderColor ?: TabColorSettingsConfigurable.DEFAULT_BORDER_COLOR_DELETED_HEX
                            Change.Type.MOVED -> settings.movedFileBorderColor ?: TabColorSettingsConfigurable.DEFAULT_BORDER_COLOR_MOVED_HEX
                            else -> null
                        }
                        thisLogger().trace("Using default border color logic for ${file.path}, status $statusType, hex: $finalColorHex")
                    } else {
                        finalColorHex = settings.borderColor
                        thisLogger().trace("Using single custom border color for ${file.path}, hex: $finalColorHex")
                    }

                    if (finalColorHex.isNullOrBlank()) {
                        thisLogger().trace("Final border color hex is blank for ${file.path}, clearing border.")
                        clearBorderForTab(tabComponent)
                        continue
                    }

                    val borderColor = try {
                        Color.decode(finalColorHex)
                    } catch (e: NumberFormatException) {
                        thisLogger().warn("Failed to decode border color hex '$finalColorHex' for file ${file.path}", e)
                        clearBorderForTab(tabComponent)
                        continue
                    }

                    val borderThickness = 2 
                    thisLogger().debug("Applying border to ${file.path}: Side: ${settings.borderSide!!}, Color: $borderColor, Thickness: $borderThickness")
                    applyBorderToTabComponent(tabComponent, borderColor, settings.borderSide!!, borderThickness)
                }
            }
            thisLogger().debug("Finished updateAllTabBorders.")
        }
    }

    private fun clearAllBorders() {
        thisLogger().debug("clearAllBorders called for project: ${project.name}")
        invokeLater {
            val fileEditorManager = FileEditorManagerEx.getInstanceEx(project)
            val windows: Array<com.intellij.openapi.fileEditor.impl.EditorWindow> = fileEditorManager.windows.filterIsInstance<com.intellij.openapi.fileEditor.impl.EditorWindow>().toTypedArray()

            if (windows.isEmpty()) {
                thisLogger().debug("No editor windows to clear borders from.")
                return@invokeLater
            }
            
            for (win in windows) {
                val tabbedPaneComponent = win.tabbedPane?.component ?: continue
                val jbTabs = tabbedPaneComponent as? JBTabs ?: continue

                for (tabInfo in jbTabs.tabs) {
                    val tabComponent = jbTabs.getTabLabel(tabInfo) as? JComponent ?: continue
                    clearBorderForTab(tabComponent)
                }
            }
            thisLogger().info("Cleared borders from all tabs as feature is disabled or by explicit call.")
        }
    }
    
    private fun applyBorderToTabComponent(tabComponent: JComponent, color: Color, side: String, thickness: Int) {
        if (thickness <= 0) {
            clearBorderForTab(tabComponent)
            return
        }
        val border = when (side.uppercase()) {
            "TOP" -> BorderFactory.createMatteBorder(thickness, 0, 0, 0, color)
            "BOTTOM" -> BorderFactory.createMatteBorder(0, 0, thickness, 0, color)
            "LEFT" -> BorderFactory.createMatteBorder(0, thickness, 0, 0, color)
            "RIGHT" -> BorderFactory.createMatteBorder(0, 0, 0, thickness, color)
            "ALL" -> BorderFactory.createMatteBorder(thickness, thickness, thickness, thickness, color) 
            else -> {
                thisLogger().warn("Invalid border side: $side, clearing border for component ${tabComponent.name ?: "Unknown"}")
                null 
            }
        }

        if (border != null) {
            tabComponent.border = BorderUIResource(border) 
            thisLogger().trace("Applied border to ${tabComponent.name ?: "Unknown"}: side $side, color $color")
        } else {
            clearBorderForTab(tabComponent)
        }
        tabComponent.repaint() 
    }

    private fun clearBorderForTab(tabComponent: JComponent) {
        tabComponent.border = null 
        thisLogger().trace("Cleared border for ${tabComponent.name ?: "Unknown"}")
        tabComponent.repaint() 
    }
    
    override fun dispose() {
        thisLogger().info("TabBorderController disposing for project: ${project.name}")
    }
}

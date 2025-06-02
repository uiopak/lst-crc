package com.github.uiopak.lstcrc.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.ColorPanel
import com.intellij.ui.ColorUtil
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.util.ui.FormBuilder
import java.awt.Color
import javax.swing.*
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.components.service // Added for project.service<T>()
import com.github.uiopak.lstcrc.services.TabBorderController // Added for TabBorderController

class TabColorSettingsConfigurable(private val project: Project) : Configurable {

    private val settingsState: TabColorSettingsState = TabColorSettingsState.getInstance(project)
    private val logger = Logger.getInstance(TabColorSettingsConfigurable::class.java)

    companion object {
        private val LOG = Logger.getInstance(TabColorSettingsConfigurable::class.java) // Companion logger if needed for static methods
        // Defaults from GitStatusBasedTabColorProvider prior to this change
        val DEFAULT_COLOR_NEW_HEX: String = "#273828"
        val DEFAULT_COLOR_MODIFIED_HEX: String = "#1d3d3b"
        val DEFAULT_COLOR_DELETED_HEX: String = "#472b2b"
        val DEFAULT_COLOR_MOVED_HEX: String = "#35363b" // Or map to MODIFIED if that was the case

        val DEFAULT_BORDER_COLOR_NEW_HEX: String = "#37A83A"
        val DEFAULT_BORDER_COLOR_MODIFIED_HEX: String = "#3895D3"
        val DEFAULT_BORDER_COLOR_DELETED_HEX: String = "#CB4335"
        val DEFAULT_BORDER_COLOR_MOVED_HEX: String = "#85929E"

        // Convert hex to Color, used for setting picker defaults
        fun hexToColor(hex: String): Color = try { ColorUtil.fromHex(hex) } catch (e: Exception) { Color.GRAY }
        // Convert Color to Hex for storing in settings, null if color is null
        fun colorToHex(color: Color?): String? = color?.let { ColorUtil.toHex(it) }
    }

    // Existing components
    private val enableCheckBox = JBCheckBox("Enable editor tab coloring", settingsState.isTabColoringEnabled)

    // Background color components
    private val useDefaultBackgroundColorCheckBox = JBCheckBox("Use Git status to determine background color", settingsState.useDefaultBackgroundColor)
    private val tabBackgroundColorPicker = ColorPanel() // For single override color
    private val tabBackgroundColorLabel = JBLabel("Single background color for all changes:") // Renamed

    // Per-status color pickers (visible when useDefaultBackgroundColorCheckBox is true)
    private val perStatusColorSettingsLabel = JBLabel("Define background colors per Git status:")
    private val newFileColorPicker = ColorPanel()
    private val newFileColorLabel = JBLabel("New files:")
    private val newFileResetButton = JButton("Reset")
    private val modifiedFileColorPicker = ColorPanel()
    private val modifiedFileColorLabel = JBLabel("Modified files:")
    private val modifiedFileResetButton = JButton("Reset")
    private val deletedFileColorPicker = ColorPanel()
    private val deletedFileColorLabel = JBLabel("Deleted files:")
    private val deletedFileResetButton = JButton("Reset")
    private val movedFileColorPicker = ColorPanel()
    private val movedFileColorLabel = JBLabel("Moved files:")
    private val movedFileResetButton = JButton("Reset")

    // Removed perStatusColorPickersAndLabels list as it's not used with new layout structure

    // Border color and side components
    private val borderSettingsLabel = JBLabel("Border settings (experimental):")
    private val borderSideComboBox = JComboBox(arrayOf("NONE", "TOP", "RIGHT", "BOTTOM", "LEFT"))
    private val useDefaultBorderColorCheckBox = JBCheckBox("Use default border color based on Git status", settingsState.useDefaultBorderColor)
    private val borderColorPicker = ColorPanel()
    private val borderColorLabel = JBLabel("Custom border color:")

    // New per-status border color UI components
    private val perStatusBorderColorSettingsLabel = JBLabel("Define border colors per Git status:")
    private val newFileBorderColorPicker = ColorPanel()
    private val newFileBorderColorLabel = JBLabel("New files border:")
    private val newFileBorderResetButton = JButton("Reset")
    private val modifiedFileBorderColorPicker = ColorPanel()
    private val modifiedFileBorderColorLabel = JBLabel("Modified files border:")
    private val modifiedFileBorderResetButton = JButton("Reset")
    private val deletedFileBorderColorPicker = ColorPanel()
    private val deletedFileBorderColorLabel = JBLabel("Deleted files border:")
    private val deletedFileBorderResetButton = JButton("Reset")
    private val movedFileBorderColorPicker = ColorPanel()
    private val movedFileBorderColorLabel = JBLabel("Moved files border:")
    private val movedFileBorderResetButton = JButton("Reset")

    private lateinit var perStatusBorderColorPanel: JPanel // Panel to group per-status border pickers

    // Old colorTarget components
    private val colorTargetLabel = JBLabel("Color target (legacy):")
    private val backgroundRadioButton = JBRadioButton("Background", settingsState.colorTarget == "BACKGROUND")

    private var mainPanel: JPanel? = null
    private lateinit var perStatusColorPanel: JPanel // Panel to group per-status pickers for visibility toggle


    init {
        ButtonGroup().apply { add(backgroundRadioButton) }

        // Initial state for pickers that have a direct setting
        tabBackgroundColorPicker.selectedColor = settingsState.tabBackgroundColor?.let { hexToColor(it) }
        borderColorPicker.selectedColor = settingsState.borderColor?.let { hexToColor(it) }

        // Listener for useDefaultBackgroundColorCheckBox to toggle UI sections
        useDefaultBackgroundColorCheckBox.addActionListener {
            updateBackgroundColorSectionsVisibility()
        }

        // Add ActionListeners for Reset buttons
        newFileResetButton.addActionListener { newFileColorPicker.selectedColor = hexToColor(DEFAULT_COLOR_NEW_HEX) }
        modifiedFileResetButton.addActionListener { modifiedFileColorPicker.selectedColor = hexToColor(DEFAULT_COLOR_MODIFIED_HEX) }
        deletedFileResetButton.addActionListener { deletedFileColorPicker.selectedColor = hexToColor(DEFAULT_COLOR_DELETED_HEX) }
        movedFileResetButton.addActionListener { movedFileColorPicker.selectedColor = hexToColor(DEFAULT_COLOR_MOVED_HEX) }

        // Add ActionListeners for border Reset buttons
        newFileBorderResetButton.addActionListener { newFileBorderColorPicker.selectedColor = hexToColor(DEFAULT_BORDER_COLOR_NEW_HEX) }
        modifiedFileBorderResetButton.addActionListener { modifiedFileBorderColorPicker.selectedColor = hexToColor(DEFAULT_BORDER_COLOR_MODIFIED_HEX) }
        deletedFileBorderResetButton.addActionListener { deletedFileBorderColorPicker.selectedColor = hexToColor(DEFAULT_BORDER_COLOR_DELETED_HEX) }
        movedFileBorderResetButton.addActionListener { movedFileBorderColorPicker.selectedColor = hexToColor(DEFAULT_BORDER_COLOR_MOVED_HEX) }

        // Listeners for border controls
        borderSideComboBox.addActionListener {
            val borderEnabled = borderSideComboBox.selectedItem != "NONE"
            useDefaultBorderColorCheckBox.isEnabled = borderEnabled
            if (!borderEnabled) {
                // If borders are disabled, ensure the "use default" checkbox is effectively reset/unchecked by logic,
                // though its actual checked state might be preserved if desired until a border side is re-selected.
                // For simplicity here, we can ensure it's true to hide custom picker, or manage state more finely.
                // useDefaultBorderColorCheckBox.isSelected = true // This might be too aggressive.
            }
            updateBorderSectionsVisibility()
        }
        useDefaultBorderColorCheckBox.addActionListener {
            updateBorderSectionsVisibility()
        }
    }

    private fun updateBackgroundColorSectionsVisibility() {
        val useDefaults = useDefaultBackgroundColorCheckBox.isSelected
        // Single override color section
        tabBackgroundColorPicker.isVisible = !useDefaults
        tabBackgroundColorLabel.isVisible = !useDefaults

        // Per-status color section
        perStatusColorPanel.isVisible = useDefaults
    }

    private fun updateBorderSectionsVisibility() {
        val borderEnabled = borderSideComboBox.selectedItem != "NONE"
        val useDefaultBorders = useDefaultBorderColorCheckBox.isSelected

        // Visibility of the panel containing all per-status border color pickers
        perStatusBorderColorPanel.isVisible = borderEnabled && useDefaultBorders

        // Visibility of the single custom border color picker and its label
        borderColorLabel.isVisible = borderEnabled && !useDefaultBorders
        borderColorPicker.isVisible = borderEnabled && !useDefaultBorders
        
        // Enable/Disable the 'Use default border color' checkbox based on whether any border side is selected
        useDefaultBorderColorCheckBox.isEnabled = borderEnabled

        // If borders are disabled entirely, ensure custom picker is also disabled.
        if (!borderEnabled) {
            borderColorPicker.isEnabled = false
            borderColorLabel.isEnabled = false
        } else {
            // If borders are enabled, the custom picker's enabled state depends on whether default borders are used.
            borderColorPicker.isEnabled = !useDefaultBorders
            borderColorLabel.isEnabled = !useDefaultBorders
        }
    }


    override fun getDisplayName(): String = "Editor Tab Git Status Coloring"

    override fun createComponent(): JComponent? {
        if (mainPanel == null) {
            // Build rows for per-status color pickers with their reset buttons
            val newFilePanel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT)).apply {
                add(newFileColorLabel)
                add(newFileColorPicker)
                add(newFileResetButton)
            }
            val modifiedFilePanel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT)).apply {
                add(modifiedFileColorLabel)
                add(modifiedFileColorPicker)
                add(modifiedFileResetButton)
            }
            val deletedFilePanel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT)).apply {
                add(deletedFileColorLabel)
                add(deletedFileColorPicker)
                add(deletedFileResetButton)
            }
            val movedFilePanel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT)).apply {
                add(movedFileColorLabel)
                add(movedFileColorPicker)
                add(movedFileResetButton)
            }

            val perStatusFormBuilder = FormBuilder.createFormBuilder()
                .addComponent(perStatusColorSettingsLabel)
                .addComponent(newFilePanel)
                .addComponent(modifiedFilePanel)
                .addComponent(deletedFilePanel)
                .addComponent(movedFilePanel)
            this.perStatusColorPanel = perStatusFormBuilder.panel // Initialize background status panel

            // Panels for per-status border color pickers
            val newFileBorderPanel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT)).apply {
                add(newFileBorderColorLabel)
                add(newFileBorderColorPicker)
                add(newFileBorderResetButton)
            }
            val modifiedFileBorderPanel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT)).apply {
                add(modifiedFileBorderColorLabel)
                add(modifiedFileBorderColorPicker)
                add(modifiedFileBorderResetButton)
            }
            val deletedFileBorderPanel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT)).apply {
                add(deletedFileBorderColorLabel)
                add(deletedFileBorderColorPicker)
                add(deletedFileBorderResetButton)
            }
            val movedFileBorderPanel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT)).apply {
                add(movedFileBorderColorLabel)
                add(movedFileBorderColorPicker)
                add(movedFileBorderResetButton)
            }

            val perStatusBorderFormBuilder = FormBuilder.createFormBuilder()
                .addComponent(perStatusBorderColorSettingsLabel)
                .addComponent(newFileBorderPanel)
                .addComponent(modifiedFileBorderPanel)
                .addComponent(deletedFileBorderPanel)
                .addComponent(movedFileBorderPanel)
            this.perStatusBorderColorPanel = perStatusBorderFormBuilder.panel // Initialize border status panel

            // Now define the main formBuilder
            val formBuilder = FormBuilder.createFormBuilder()
                .addComponent(enableCheckBox)
                .addVerticalGap(10)
                // Background color settings
                .addComponent(useDefaultBackgroundColorCheckBox)
                // Single override color (initially may be hidden by reset())
                .addLabeledComponent(tabBackgroundColorLabel, tabBackgroundColorPicker)
                // Per-status colors (initially may be hidden by reset())
                .addComponent(perStatusColorPanel)
                .addVerticalGap(10)
                // Border settings
                .addComponent(borderSettingsLabel)
                .addLabeledComponent(JBLabel("Border side:"), borderSideComboBox)
                .addComponent(useDefaultBorderColorCheckBox)
                // Per-status border colors (logic for visibility in reset() and listeners)
                .addComponent(this.perStatusBorderColorPanel) // Use 'this.' for clarity
                // Single custom border color (logic for visibility in reset() and listeners)
                .addLabeledComponent(borderColorLabel, borderColorPicker)
                .addVerticalGap(10)
                // Legacy colorTarget
                .addComponent(colorTargetLabel)
                .addComponent(backgroundRadioButton)

            mainPanel = formBuilder.panel
            reset() // Call reset to set initial states and visibility, including new border sections
        }
        return mainPanel
    }

    override fun isModified(): Boolean {
        if (enableCheckBox.isSelected != settingsState.isTabColoringEnabled) return true
        if (useDefaultBackgroundColorCheckBox.isSelected != settingsState.useDefaultBackgroundColor) return true

        val currentTabBackgroundColorHex = colorToHex(tabBackgroundColorPicker.selectedColor)
        if (currentTabBackgroundColorHex != settingsState.tabBackgroundColor) return true

        // Check per-status colors
        val newFileColorHex = colorToHex(newFileColorPicker.selectedColor)
        if (isColorModified(newFileColorHex, settingsState.newFileColor, DEFAULT_COLOR_NEW_HEX)) return true

        val modifiedFileColorHex = colorToHex(modifiedFileColorPicker.selectedColor)
        if (isColorModified(modifiedFileColorHex, settingsState.modifiedFileColor, DEFAULT_COLOR_MODIFIED_HEX)) return true

        val deletedFileColorHex = colorToHex(deletedFileColorPicker.selectedColor)
        if (isColorModified(deletedFileColorHex, settingsState.deletedFileColor, DEFAULT_COLOR_DELETED_HEX)) return true

        val movedFileColorHex = colorToHex(movedFileColorPicker.selectedColor)
        if (isColorModified(movedFileColorHex, settingsState.movedFileColor, DEFAULT_COLOR_MOVED_HEX)) return true
        
        // Check border settings
        if (borderSideComboBox.selectedItem as String != settingsState.borderSide) return true
        if (useDefaultBorderColorCheckBox.isSelected != settingsState.useDefaultBorderColor) return true
        val currentBorderColorHex = colorToHex(borderColorPicker.selectedColor)
        if (currentBorderColorHex != settingsState.borderColor) return true

        // Check per-status border colors
        if (isColorModified(colorToHex(newFileBorderColorPicker.selectedColor), settingsState.newFileBorderColor, DEFAULT_BORDER_COLOR_NEW_HEX)) return true
        if (isColorModified(colorToHex(modifiedFileBorderColorPicker.selectedColor), settingsState.modifiedFileBorderColor, DEFAULT_BORDER_COLOR_MODIFIED_HEX)) return true
        if (isColorModified(colorToHex(deletedFileBorderColorPicker.selectedColor), settingsState.deletedFileBorderColor, DEFAULT_BORDER_COLOR_DELETED_HEX)) return true
        if (isColorModified(colorToHex(movedFileBorderColorPicker.selectedColor), settingsState.movedFileBorderColor, DEFAULT_BORDER_COLOR_MOVED_HEX)) return true
        
        // Check legacy colorTarget
        val selectedColorTarget = if (backgroundRadioButton.isSelected) "BACKGROUND" else settingsState.colorTarget
        if (selectedColorTarget != settingsState.colorTarget) return true

        return false
    }
    
    private fun isColorModified(pickerHex: String?, settingHex: String?, defaultHex: String): Boolean {
        // If setting is null (meaning use default), it's modified if picker is not matching default.
        // If setting is not null (custom color), it's modified if picker is different from setting.
        return if (settingHex == null) {
            pickerHex != defaultHex // Picker should match default if setting is null
        } else {
            pickerHex != settingHex // Picker should match custom setting
        }
    }


    override fun apply() {
        logger.info("CONFIG: Apply called. Project: ${project.name}")

        // Log values from UI pickers before saving
        logger.info("CONFIG: UI Picker Values Before Save:")
        logger.info("  enableCheckBox.isSelected: ${enableCheckBox.isSelected}")
        logger.info("  useDefaultBackgroundColorCheckBox.isSelected: ${useDefaultBackgroundColorCheckBox.isSelected}")
        logger.info("  tabBackgroundColorPicker.selectedColor (hex): ${colorToHex(tabBackgroundColorPicker.selectedColor)}")
        logger.info("  newFileColorPicker.selectedColor (hex): ${colorToHex(newFileColorPicker.selectedColor)}")
        logger.info("  modifiedFileColorPicker.selectedColor (hex): ${colorToHex(modifiedFileColorPicker.selectedColor)}")
        logger.info("  deletedFileColorPicker.selectedColor (hex): ${colorToHex(deletedFileColorPicker.selectedColor)}")
        logger.info("  movedFileColorPicker.selectedColor (hex): ${colorToHex(movedFileColorPicker.selectedColor)}")
        logger.info("  borderSideComboBox.selectedItem: ${borderSideComboBox.selectedItem}")
        logger.info("  useDefaultBorderColorCheckBox.isSelected: ${useDefaultBorderColorCheckBox.isSelected}")
        logger.info("  borderColorPicker.selectedColor (hex): ${colorToHex(borderColorPicker.selectedColor)}")
        logger.info("  newFileBorderColorPicker.selectedColor (hex): ${colorToHex(newFileBorderColorPicker.selectedColor)}")
        logger.info("  modifiedFileBorderColorPicker.selectedColor (hex): ${colorToHex(modifiedFileBorderColorPicker.selectedColor)}")
        logger.info("  deletedFileBorderColorPicker.selectedColor (hex): ${colorToHex(deletedFileBorderColorPicker.selectedColor)}")
        logger.info("  movedFileBorderColorPicker.selectedColor (hex): ${colorToHex(movedFileBorderColorPicker.selectedColor)}")
        logger.info("  backgroundRadioButton.isSelected (for legacy colorTarget): ${backgroundRadioButton.isSelected}")

        settingsState.isTabColoringEnabled = enableCheckBox.isSelected
        settingsState.useDefaultBackgroundColor = useDefaultBackgroundColorCheckBox.isSelected
        
        settingsState.tabBackgroundColor = colorToHex(tabBackgroundColorPicker.selectedColor)

        // Apply per-status colors
        settingsState.newFileColor = getAppliedColorHex(newFileColorPicker.selectedColor, DEFAULT_COLOR_NEW_HEX)
        settingsState.modifiedFileColor = getAppliedColorHex(modifiedFileColorPicker.selectedColor, DEFAULT_COLOR_MODIFIED_HEX)
        settingsState.deletedFileColor = getAppliedColorHex(deletedFileColorPicker.selectedColor, DEFAULT_COLOR_DELETED_HEX)
        settingsState.movedFileColor = getAppliedColorHex(movedFileColorPicker.selectedColor, DEFAULT_COLOR_MOVED_HEX)

        settingsState.borderSide = borderSideComboBox.selectedItem as String
        settingsState.useDefaultBorderColor = useDefaultBorderColorCheckBox.isSelected
        settingsState.borderColor = colorToHex(borderColorPicker.selectedColor)

        // Apply per-status border colors
        settingsState.newFileBorderColor = getAppliedColorHex(newFileBorderColorPicker.selectedColor, DEFAULT_BORDER_COLOR_NEW_HEX)
        settingsState.modifiedFileBorderColor = getAppliedColorHex(modifiedFileBorderColorPicker.selectedColor, DEFAULT_BORDER_COLOR_MODIFIED_HEX)
        settingsState.deletedFileBorderColor = getAppliedColorHex(deletedFileBorderColorPicker.selectedColor, DEFAULT_BORDER_COLOR_DELETED_HEX)
        settingsState.movedFileBorderColor = getAppliedColorHex(movedFileBorderColorPicker.selectedColor, DEFAULT_BORDER_COLOR_MOVED_HEX)

        settingsState.colorTarget = if (backgroundRadioButton.isSelected) "BACKGROUND" else settingsState.colorTarget

        // Log values stored in settingsState after saving
        logger.info("CONFIG: SettingsState Values After Save:")
        logger.info("  settingsState.isTabColoringEnabled: ${settingsState.isTabColoringEnabled}")
        logger.info("  settingsState.useDefaultBackgroundColor: ${settingsState.useDefaultBackgroundColor}")
        logger.info("  settingsState.tabBackgroundColor: ${settingsState.tabBackgroundColor}")
        logger.info("  settingsState.newFileColor: ${settingsState.newFileColor}")
        logger.info("  settingsState.modifiedFileColor: ${settingsState.modifiedFileColor}")
        logger.info("  settingsState.deletedFileColor: ${settingsState.deletedFileColor}")
        logger.info("  settingsState.movedFileColor: ${settingsState.movedFileColor}")
        logger.info("  settingsState.borderSide: ${settingsState.borderSide}")
        logger.info("  settingsState.useDefaultBorderColor: ${settingsState.useDefaultBorderColor}")
        logger.info("  settingsState.borderColor: ${settingsState.borderColor}")
        logger.info("  settingsState.newFileBorderColor: ${settingsState.newFileBorderColor}")
        logger.info("  settingsState.modifiedFileBorderColor: ${settingsState.modifiedFileBorderColor}")
        logger.info("  settingsState.deletedFileBorderColor: ${settingsState.deletedFileBorderColor}")
        logger.info("  settingsState.movedFileBorderColor: ${settingsState.movedFileBorderColor}")
        logger.info("  settingsState.colorTarget: ${settingsState.colorTarget}")
        
        logger.info("CONFIG: Attempting to refresh open editor tabs for project: ${project.name} (hashCode: ${project.hashCode()})")
        val fileEditorManager = FileEditorManager.getInstance(project) as? FileEditorManagerEx
        if (fileEditorManager == null) {
            logger.warn("CONFIG: FileEditorManagerEx is null for project ${project.name}. Cannot refresh tabs.")
            return
        }

        logger.info("CONFIG: Starting refresh loop for ${fileEditorManager.openFiles.size} open files.")
        fileEditorManager.openFiles.forEachIndexed { index, virtualFile ->
            logger.info("CONFIG: Refreshing presentation for (${index + 1}/${fileEditorManager.openFiles.size}): ${virtualFile.path}")
            try {
                // Ensure UI thread for specific manager calls if necessary, though updateFilePresentation is usually safe.
                // Forcing a more specific repaint if issues persist:
                // fileEditorManager.repaintEditor(virtualFile) // This is not a public API, use with caution or alternatives.
                // fileEditorManager.refreshIcons() // Might be relevant if icons are part of the presentation.
                fileEditorManager.updateFilePresentation(virtualFile)
            } catch (e: Exception) {
                logger.error("CONFIG: Exception during updateFilePresentation for ${virtualFile.path}", e)
            }
        }
        logger.info("CONFIG: Finished refresh loop.")

        // New call to TabBorderController to update all borders
        logger.info("CONFIG: Requesting TabBorderController to update all tab borders due to settings change.")
        try {
            val tabBorderController = project.service<com.github.uiopak.lstcrc.services.TabBorderController>()
            tabBorderController.updateAllTabBorders()
            logger.info("CONFIG: TabBorderController finished updating tab borders.")
        } catch (e: Exception) {
            logger.error("CONFIG: Exception when calling TabBorderController.updateAllTabBorders from settings apply", e)
        }
        
        // Example of a more global repaint, if needed:
        // com.intellij.openapi.fileEditor.ex.FileEditorManagerEx.getInstanceEx(project).repaintEditorWindows()
        // logger.info("CONFIG: Called repaintEditorWindows if necessary.")
    }

    private fun getAppliedColorHex(selectedColor: Color?, defaultHex: String): String? {
        val selectedHex = colorToHex(selectedColor)
        return if (selectedHex == defaultHex) null else selectedHex // Store null if color is same as factory default
    }

    override fun reset() {
        enableCheckBox.isSelected = settingsState.isTabColoringEnabled
        useDefaultBackgroundColorCheckBox.isSelected = settingsState.useDefaultBackgroundColor

        // Single override color
        tabBackgroundColorPicker.selectedColor = settingsState.tabBackgroundColor?.let { hexToColor(it) }

        // Per-status colors
        newFileColorPicker.selectedColor = settingsState.newFileColor?.let { hexToColor(it) } ?: hexToColor(DEFAULT_COLOR_NEW_HEX)
        modifiedFileColorPicker.selectedColor = settingsState.modifiedFileColor?.let { hexToColor(it) } ?: hexToColor(DEFAULT_COLOR_MODIFIED_HEX)
        deletedFileColorPicker.selectedColor = settingsState.deletedFileColor?.let { hexToColor(it) } ?: hexToColor(DEFAULT_COLOR_DELETED_HEX)
        movedFileColorPicker.selectedColor = settingsState.movedFileColor?.let { hexToColor(it) } ?: hexToColor(DEFAULT_COLOR_MOVED_HEX)

        updateBackgroundColorSectionsVisibility() // Update visibility based on checkbox

        // Border settings
        borderSideComboBox.selectedItem = settingsState.borderSide
        useDefaultBorderColorCheckBox.isSelected = settingsState.useDefaultBorderColor
        borderColorPicker.selectedColor = settingsState.borderColor?.let { hexToColor(it) }
        
        // Per-status border colors
        newFileBorderColorPicker.selectedColor = settingsState.newFileBorderColor?.let { hexToColor(it) } ?: hexToColor(DEFAULT_BORDER_COLOR_NEW_HEX)
        modifiedFileBorderColorPicker.selectedColor = settingsState.modifiedFileBorderColor?.let { hexToColor(it) } ?: hexToColor(DEFAULT_BORDER_COLOR_MODIFIED_HEX)
        deletedFileBorderColorPicker.selectedColor = settingsState.deletedFileBorderColor?.let { hexToColor(it) } ?: hexToColor(DEFAULT_BORDER_COLOR_DELETED_HEX)
        movedFileBorderColorPicker.selectedColor = settingsState.movedFileBorderColor?.let { hexToColor(it) } ?: hexToColor(DEFAULT_BORDER_COLOR_MOVED_HEX)

        updateBorderSectionsVisibility() // Update visibility of border sections

        // Legacy settings
        backgroundRadioButton.isSelected = settingsState.colorTarget == "BACKGROUND"
    }

    override fun disposeUIResources() {
        mainPanel = null
        // Potentially perStatusColorPanel = null if not part of mainPanel's component tree directly
    }
}

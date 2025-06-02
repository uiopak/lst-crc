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

class TabColorSettingsConfigurable(private val project: Project) : Configurable {

    private val settingsState: TabColorSettingsState = TabColorSettingsState.getInstance(project)

    companion object {
        // Defaults from GitStatusBasedTabColorProvider prior to this change
        val DEFAULT_COLOR_NEW_HEX: String = "#273828"
        val DEFAULT_COLOR_MODIFIED_HEX: String = "#1d3d3b"
        val DEFAULT_COLOR_DELETED_HEX: String = "#472b2b"
        val DEFAULT_COLOR_MOVED_HEX: String = "#35363b" // Or map to MODIFIED if that was the case

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
    private val modifiedFileColorPicker = ColorPanel()
    private val modifiedFileColorLabel = JBLabel("Modified files:")
    private val deletedFileColorPicker = ColorPanel()
    private val deletedFileColorLabel = JBLabel("Deleted files:")
    private val movedFileColorPicker = ColorPanel()
    private val movedFileColorLabel = JBLabel("Moved files:")

    private val perStatusColorPickersAndLabels: List<Pair<ColorPanel, JBLabel>> = listOf(
        newFileColorPicker to newFileColorLabel,
        modifiedFileColorPicker to modifiedFileColorLabel,
        deletedFileColorPicker to deletedFileColorLabel,
        movedFileColorPicker to movedFileColorLabel
    )

    // Border color and side components
    private val borderSettingsLabel = JBLabel("Border settings (experimental):")
    private val borderSideComboBox = JComboBox(arrayOf("NONE", "TOP", "RIGHT", "BOTTOM", "LEFT"))
    private val useDefaultBorderColorCheckBox = JBCheckBox("Use default border color based on Git status", settingsState.useDefaultBorderColor)
    private val borderColorPicker = ColorPanel()
    private val borderColorLabel = JBLabel("Custom border color:")

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

        // Listeners for border controls (unchanged from previous version)
        borderSideComboBox.addActionListener {
            val borderEnabled = borderSideComboBox.selectedItem != "NONE"
            useDefaultBorderColorCheckBox.isEnabled = borderEnabled
            val customBorderColorEnabled = borderEnabled && !useDefaultBorderColorCheckBox.isSelected
            borderColorLabel.isEnabled = customBorderColorEnabled
            borderColorPicker.isEnabled = customBorderColorEnabled
            if (!borderEnabled) {
                useDefaultBorderColorCheckBox.isSelected = true
                borderColorLabel.isEnabled = false
                borderColorPicker.isEnabled = false
            }
        }
        useDefaultBorderColorCheckBox.addActionListener {
            val borderEnabled = borderSideComboBox.selectedItem != "NONE"
            val customBorderColorEnabled = borderEnabled && !useDefaultBorderColorCheckBox.isSelected
            borderColorPicker.isEnabled = customBorderColorEnabled
            borderColorLabel.isEnabled = customBorderColorEnabled
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


    override fun getDisplayName(): String = "Editor Tab Git Status Coloring"

    override fun createComponent(): JComponent? {
        if (mainPanel == null) {
            val perStatusFormBuilder = FormBuilder.createFormBuilder()
                .addComponent(perStatusColorSettingsLabel)
                .addLabeledComponent(newFileColorLabel, newFileColorPicker)
                .addLabeledComponent(modifiedFileColorLabel, modifiedFileColorPicker)
                .addLabeledComponent(deletedFileColorLabel, deletedFileColorPicker)
                .addLabeledComponent(movedFileColorLabel, movedFileColorPicker)
            perStatusColorPanel = perStatusFormBuilder.panel

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
                .addLabeledComponent(borderColorLabel, borderColorPicker)
                .addVerticalGap(10)
                // Legacy colorTarget
                .addComponent(colorTargetLabel)
                .addComponent(backgroundRadioButton)

            mainPanel = formBuilder.panel
            reset() // Call reset to set initial states and visibility
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

        settingsState.colorTarget = if (backgroundRadioButton.isSelected) "BACKGROUND" else settingsState.colorTarget
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
        val borderEnabled = settingsState.borderSide != "NONE"
        useDefaultBorderColorCheckBox.isSelected = settingsState.useDefaultBorderColor
        useDefaultBorderColorCheckBox.isEnabled = borderEnabled
        borderColorPicker.selectedColor = settingsState.borderColor?.let { hexToColor(it) }
        val customBorderColorEnabled = borderEnabled && !settingsState.useDefaultBorderColor
        borderColorPicker.isEnabled = customBorderColorEnabled
        borderColorLabel.isEnabled = customBorderColorEnabled
        if (!borderEnabled) { // Ensure picker is disabled if border is NONE
             useDefaultBorderColorCheckBox.isEnabled = false
             borderColorPicker.isEnabled = false
             borderColorLabel.isEnabled = false
        }


        // Legacy settings
        backgroundRadioButton.isSelected = settingsState.colorTarget == "BACKGROUND"
    }

    override fun disposeUIResources() {
        mainPanel = null
        // Potentially perStatusColorPanel = null if not part of mainPanel's component tree directly
    }
}

package com.github.uiopak.lstcrc.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.ColorPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
// import com.intellij.ui.components.JBTextField // Not used anymore
import com.intellij.util.ui.FormBuilder
import java.awt.Color
import javax.swing.ButtonGroup
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel

class TabColorSettingsConfigurable(private val project: Project) : Configurable {

    private val settingsState: TabColorSettingsState = TabColorSettingsState.getInstance(project)

    // Existing components
    private val enableCheckBox = JBCheckBox("Enable editor tab coloring", settingsState.isTabColoringEnabled)

    // Background color components
    private val useDefaultBackgroundColorCheckBox = JBCheckBox("Use default background color based on Git status", settingsState.useDefaultBackgroundColor)
    private val tabBackgroundColorPicker = ColorPanel()
    private val tabBackgroundColorLabel = JBLabel("Custom background color:")

    // Border color and side components
    private val borderSettingsLabel = JBLabel("Border settings:")
    private val borderSideComboBox = JComboBox(arrayOf("NONE", "TOP", "RIGHT", "BOTTOM", "LEFT"))
    private val useDefaultBorderColorCheckBox = JBCheckBox("Use default border color based on Git status", settingsState.useDefaultBorderColor)
    private val borderColorPicker = ColorPanel()
    private val borderColorLabel = JBLabel("Custom border color:")

    // Old colorTarget components (backgroundRadioButton might be redundant, borderTopRadioButton is commented out)
    private val colorTargetLabel = JBLabel("Color target (legacy):")
    private val backgroundRadioButton = JBRadioButton("Background", settingsState.colorTarget == "BACKGROUND")
    // private val borderTopRadioButton = JBRadioButton("Border Top", settingsState.colorTarget == "BORDER_TOP")


    private var mainPanel: JPanel? = null

    init {
        // Group radio buttons for colorTarget
        ButtonGroup().apply {
            add(backgroundRadioButton)
            // add(borderTopRadioButton)
        }

        // Set initial states for color pickers
        tabBackgroundColorPicker.selectedColor = settingsState.tabBackgroundColor?.let { Color.decode(it) }
        borderColorPicker.selectedColor = settingsState.borderColor?.let { Color.decode(it) }

        // Add listeners to manage enabled state of color pickers
        useDefaultBackgroundColorCheckBox.addActionListener {
            tabBackgroundColorPicker.isEnabled = !useDefaultBackgroundColorCheckBox.isSelected
            tabBackgroundColorLabel.isEnabled = !useDefaultBackgroundColorCheckBox.isSelected
        }

        borderSideComboBox.addActionListener {
            val borderEnabled = borderSideComboBox.selectedItem != "NONE"
            useDefaultBorderColorCheckBox.isEnabled = borderEnabled
            borderColorLabel.isEnabled = borderEnabled && !useDefaultBorderColorCheckBox.isSelected
            borderColorPicker.isEnabled = borderEnabled && !useDefaultBorderColorCheckBox.isSelected
            // If border becomes NONE, ensure default border color checkbox is also updated
            if (!borderEnabled) {
                useDefaultBorderColorCheckBox.isSelected = true // Or reflect settingsState.useDefaultBorderColor
                borderColorLabel.isEnabled = false
                borderColorPicker.isEnabled = false
            }
        }

        useDefaultBorderColorCheckBox.addActionListener {
            val borderEnabled = borderSideComboBox.selectedItem != "NONE"
            borderColorPicker.isEnabled = borderEnabled && !useDefaultBorderColorCheckBox.isSelected
            borderColorLabel.isEnabled = borderEnabled && !useDefaultBorderColorCheckBox.isSelected
        }
    }

    override fun getDisplayName(): String = "Editor Tab Git Status Coloring"

    override fun createComponent(): JComponent? {
        if (mainPanel == null) {
            val formBuilder = FormBuilder.createFormBuilder()
                .addComponent(enableCheckBox)
                .addVerticalGap(10)

                // Background color settings
                .addComponent(useDefaultBackgroundColorCheckBox)
                .addLabeledComponent(tabBackgroundColorLabel, tabBackgroundColorPicker)
                .addVerticalGap(10)

                // Border settings
                .addComponent(borderSettingsLabel)
                .addLabeledComponent(JBLabel("Border side:"), borderSideComboBox)
                .addComponent(useDefaultBorderColorCheckBox)
                .addLabeledComponent(borderColorLabel, borderColorPicker)
                .addVerticalGap(10)

                // Legacy colorTarget (optional, can be removed later)
                .addComponent(colorTargetLabel)
                .addComponent(backgroundRadioButton)
                // .addComponent(borderTopRadioButton) // Commented out

            mainPanel = formBuilder.panel
            // Initialize enabled states after panel is created
            reset()
        }
        return mainPanel
    }

    override fun isModified(): Boolean {
        val selectedColorTarget = when {
            backgroundRadioButton.isSelected -> "BACKGROUND"
            // borderTopRadioButton.isSelected -> "BORDER_TOP"
            else -> settingsState.colorTarget // Keep existing if none of the old radio buttons are touched
        }
        // Convert Color object to hex string for comparison, or null if no color selected
        val currentTabBackgroundColor = tabBackgroundColorPicker.selectedColor?.let { "#%06X".format(0xFFFFFF and it.rgb) }
        val currentBorderColor = borderColorPicker.selectedColor?.let { "#%06X".format(0xFFFFFF and it.rgb) }

        return enableCheckBox.isSelected != settingsState.isTabColoringEnabled ||
               useDefaultBackgroundColorCheckBox.isSelected != settingsState.useDefaultBackgroundColor ||
               currentTabBackgroundColor != settingsState.tabBackgroundColor ||
               borderSideComboBox.selectedItem as String != settingsState.borderSide ||
               useDefaultBorderColorCheckBox.isSelected != settingsState.useDefaultBorderColor ||
               currentBorderColor != settingsState.borderColor ||
               selectedColorTarget != settingsState.colorTarget // Legacy setting
    }

    override fun apply() {
        settingsState.isTabColoringEnabled = enableCheckBox.isSelected
        settingsState.useDefaultBackgroundColor = useDefaultBackgroundColorCheckBox.isSelected
        settingsState.tabBackgroundColor = tabBackgroundColorPicker.selectedColor?.let { "#%06X".format(0xFFFFFF and it.rgb) }

        settingsState.borderSide = borderSideComboBox.selectedItem as String
        settingsState.useDefaultBorderColor = useDefaultBorderColorCheckBox.isSelected
        settingsState.borderColor = borderColorPicker.selectedColor?.let { "#%06X".format(0xFFFFFF and it.rgb) }

        // Legacy setting
        settingsState.colorTarget = when {
            backgroundRadioButton.isSelected -> "BACKGROUND"
            // borderTopRadioButton.isSelected -> "BORDER_TOP"
            else -> settingsState.colorTarget // Preserve if not explicitly changed by these radio buttons
        }
    }

    override fun reset() {
        enableCheckBox.isSelected = settingsState.isTabColoringEnabled

        useDefaultBackgroundColorCheckBox.isSelected = settingsState.useDefaultBackgroundColor
        tabBackgroundColorPicker.selectedColor = settingsState.tabBackgroundColor?.let { Color.decode(it) }
        tabBackgroundColorPicker.isEnabled = !settingsState.useDefaultBackgroundColor
        tabBackgroundColorLabel.isEnabled = !settingsState.useDefaultBackgroundColor

        borderSideComboBox.selectedItem = settingsState.borderSide
        val borderEnabled = settingsState.borderSide != "NONE"
        useDefaultBorderColorCheckBox.isSelected = settingsState.useDefaultBorderColor
        useDefaultBorderColorCheckBox.isEnabled = borderEnabled

        borderColorPicker.selectedColor = settingsState.borderColor?.let { Color.decode(it) }
        borderColorPicker.isEnabled = borderEnabled && !settingsState.useDefaultBorderColor
        borderColorLabel.isEnabled = borderEnabled && !settingsState.useDefaultBorderColor


        // Legacy settings
        backgroundRadioButton.isSelected = settingsState.colorTarget == "BACKGROUND"
        // borderTopRadioButton.isSelected = settingsState.colorTarget == "BORDER_TOP"
        if (!backgroundRadioButton.isSelected /* && !borderTopRadioButton.isSelected */) {
            // If settingsState.colorTarget is something else and those radio buttons are removed,
            // this logic might need adjustment. For now, if it's not BACKGROUND, nothing else is selected.
            // Consider if backgroundRadioButton should be forced selected if colorTarget is not BORDER_TOP.
        }
    }

    override fun disposeUIResources() {
        mainPanel = null
    }
}

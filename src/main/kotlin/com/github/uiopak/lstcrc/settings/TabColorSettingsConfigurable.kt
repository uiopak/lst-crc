package com.github.uiopak.lstcrc.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
// Removed JBRadioButton and JBLabel as they are no longer used
import com.intellij.util.ui.FormBuilder
// Removed ButtonGroup as it's no longer used
import javax.swing.JComponent
import javax.swing.JPanel

class TabColorSettingsConfigurable(private val project: Project) : Configurable {

    private val settingsState: TabColorSettingsState = TabColorSettingsState.getInstance(project)
    private val enableCheckBox = JBCheckBox("Enable editor tab coloring based on Git status (uses theme colors)", settingsState.isTabColoringEnabled)

    // Removed UI components for colorTarget:
    // private val colorTargetLabel: JBLabel
    // private val backgroundRadioButton: JBRadioButton
    // private val borderTopRadioButton: JBRadioButton
    // private val buttonGroup: ButtonGroup

    private var mainPanel: JPanel? = null

    // Removed init block for ButtonGroup and label text initialization

    override fun getDisplayName(): String = "Editor Tab Git Status Coloring" // Updated to be more specific

    override fun createComponent(): JComponent? {
        if (mainPanel == null) {
            val formBuilder = FormBuilder.createFormBuilder()
                .addComponent(enableCheckBox)
            // Removed components related to colorTarget from layout:
            // .addVerticalGap(10)
            // .addComponent(colorTargetLabel)
            // .addComponent(backgroundRadioButton)
            // .addComponent(borderTopRadioButton)
            mainPanel = formBuilder.panel
        }
        return mainPanel
    }

    override fun isModified(): Boolean {
        // Only check the state of the enableCheckBox
        return enableCheckBox.isSelected != settingsState.isTabColoringEnabled
        // Removed colorTarget logic:
        // || (backgroundRadioButton.isSelected && settingsState.colorTarget != "BACKGROUND")
        // || (borderTopRadioButton.isSelected && settingsState.colorTarget != "BORDER_TOP")
    }

    override fun apply() {
        settingsState.isTabColoringEnabled = enableCheckBox.isSelected
        // Removed colorTarget logic:
        // settingsState.colorTarget = if (backgroundRadioButton.isSelected) "BACKGROUND" else "BORDER_TOP"
    }

    override fun reset() {
        enableCheckBox.isSelected = settingsState.isTabColoringEnabled
        // Removed colorTarget logic:
        // backgroundRadioButton.isSelected = settingsState.colorTarget == "BACKGROUND"
        // borderTopRadioButton.isSelected = settingsState.colorTarget == "BORDER_TOP"
    }

    override fun disposeUIResources() {
        mainPanel = null
        // No other specific UI resources like radio buttons or labels to nullify
    }
}

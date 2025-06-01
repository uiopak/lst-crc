package com.github.uiopak.lstcrc.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JPanel

class TabColorSettingsConfigurable(private val project: Project) : Configurable {

    private val settingsState: TabColorSettingsState = TabColorSettingsState.getInstance(project)

    private val enableCheckBox = JBCheckBox("Enable editor tab coloring", settingsState.isTabColoringEnabled)

    private val colorTargetLabel = JBLabel("Color target:")
    private val backgroundRadioButton = JBRadioButton("Background", settingsState.colorTarget == "BACKGROUND")
    private val borderTopRadioButton = JBRadioButton("Border Top", settingsState.colorTarget == "BORDER_TOP")
    // Add other border options if pursuing them, for now focusing on background
    // For simplicity, only BACKGROUND is fully wired up initially. Others are UI placeholders.

    // Removed UI components for comparisonBranch
    // private val comparisonBranchLabel = JBLabel("Compare working tree against:")
    // private val headRadioButton = JBRadioButton("HEAD of current branch", ...)
    // private val specificBranchRadioButton = JBRadioButton("Specific branch:", ...)
    // private val specificBranchTextField = JBTextField(...)

    private var mainPanel: JPanel? = null

    init {
        // Group radio buttons for colorTarget
        ButtonGroup().apply {
            add(backgroundRadioButton)
            add(borderTopRadioButton)
        }
        // Removed ButtonGroup for comparisonBranch
        // Removed change listener for specificBranchRadioButton
    }

    override fun getDisplayName(): String = "Editor Tab Git Status Coloring"

    override fun createComponent(): JComponent? {
        if (mainPanel == null) {
            val formBuilder = FormBuilder.createFormBuilder()
                .addComponent(enableCheckBox)
                .addVerticalGap(10)
                .addComponent(colorTargetLabel)
                .addComponent(backgroundRadioButton)
            // .addComponent(borderTopRadioButton) // Uncomment if/when border coloring is implemented
            // Removed comparisonBranch components from layout

            mainPanel = formBuilder.panel
        }
        return mainPanel
    }

    override fun isModified(): Boolean {
        val selectedColorTarget = when {
            backgroundRadioButton.isSelected -> "BACKGROUND"
            // borderTopRadioButton.isSelected -> "BORDER_TOP" // Uncomment when implemented
            else -> "BACKGROUND" // Default if somehow none selected
        }
        // Removed comparisonBranch logic from isModified
        return enableCheckBox.isSelected != settingsState.isTabColoringEnabled ||
               selectedColorTarget != settingsState.colorTarget
    }

    override fun apply() {
        settingsState.isTabColoringEnabled = enableCheckBox.isSelected

        settingsState.colorTarget = when {
            backgroundRadioButton.isSelected -> "BACKGROUND"
            // borderTopRadioButton.isSelected -> "BORDER_TOP" // Uncomment when implemented
            else -> "BACKGROUND"
        }
        // Removed comparisonBranch logic from apply
    }

    override fun reset() {
        enableCheckBox.isSelected = settingsState.isTabColoringEnabled

        backgroundRadioButton.isSelected = settingsState.colorTarget == "BACKGROUND"
        // borderTopRadioButton.isSelected = settingsState.colorTarget == "BORDER_TOP" // Uncomment when implemented
        // Ensure only one is selected or provide a fallback
        if (!backgroundRadioButton.isSelected /* && !borderTopRadioButton.isSelected */) {
             backgroundRadioButton.isSelected = true // Default
        }
        // Removed comparisonBranch logic from reset
    }

    override fun disposeUIResources() {
        mainPanel = null
    }
}

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

    private val comparisonBranchLabel = JBLabel("Compare working tree against:")
    private val headRadioButton = JBRadioButton("HEAD of current branch", settingsState.comparisonBranch == "HEAD")
    private val specificBranchRadioButton = JBRadioButton("Specific branch:", settingsState.comparisonBranch != "HEAD" && settingsState.comparisonBranch != "current_branch") // Heuristic
    private val specificBranchTextField = JBTextField(if (specificBranchRadioButton.isSelected) settingsState.comparisonBranch else "")

    private var mainPanel: JPanel? = null

    init {
        // Group radio buttons
        ButtonGroup().apply {
            add(backgroundRadioButton)
            add(borderTopRadioButton)
        }
        ButtonGroup().apply {
            add(headRadioButton)
            add(specificBranchRadioButton)
        }

        // Enable/disable text field based on radio button selection
        specificBranchRadioButton.addChangeListener {
            specificBranchTextField.isEnabled = specificBranchRadioButton.isSelected
        }
        specificBranchTextField.isEnabled = specificBranchRadioButton.isSelected // Initial state
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
                .addVerticalGap(10)
                .addComponent(comparisonBranchLabel)
                .addComponent(headRadioButton)
                .addComponent(specificBranchRadioButton)
                .addLabeledComponent("Branch name:", specificBranchTextField, 5, false)

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
        val selectedComparisonBranch = when {
            headRadioButton.isSelected -> "HEAD"
            specificBranchRadioButton.isSelected -> specificBranchTextField.text.trim()
            else -> "HEAD" // Default
        }

        return enableCheckBox.isSelected != settingsState.isTabColoringEnabled ||
               selectedColorTarget != settingsState.colorTarget ||
               selectedComparisonBranch != settingsState.comparisonBranch ||
               (specificBranchRadioButton.isSelected && specificBranchTextField.text.trim() != settingsState.comparisonBranch)

    }

    override fun apply() {
        settingsState.isTabColoringEnabled = enableCheckBox.isSelected

        settingsState.colorTarget = when {
            backgroundRadioButton.isSelected -> "BACKGROUND"
            // borderTopRadioButton.isSelected -> "BORDER_TOP" // Uncomment when implemented
            else -> "BACKGROUND"
        }

        settingsState.comparisonBranch = when {
            headRadioButton.isSelected -> "HEAD"
            specificBranchRadioButton.isSelected -> specificBranchTextField.text.trim().ifEmpty { "HEAD" }
            else -> "HEAD"
        }
    }

    override fun reset() {
        enableCheckBox.isSelected = settingsState.isTabColoringEnabled

        backgroundRadioButton.isSelected = settingsState.colorTarget == "BACKGROUND"
        // borderTopRadioButton.isSelected = settingsState.colorTarget == "BORDER_TOP" // Uncomment when implemented
        // Ensure only one is selected or provide a fallback
        if (!backgroundRadioButton.isSelected /* && !borderTopRadioButton.isSelected */) {
             backgroundRadioButton.isSelected = true // Default
        }

        headRadioButton.isSelected = settingsState.comparisonBranch == "HEAD"
        specificBranchRadioButton.isSelected = settingsState.comparisonBranch != "HEAD" // Simplified
        specificBranchTextField.text = if (specificBranchRadioButton.isSelected) settingsState.comparisonBranch else ""
        specificBranchTextField.isEnabled = specificBranchRadioButton.isSelected
    }

    override fun disposeUIResources() {
        mainPanel = null
    }
}

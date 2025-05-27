package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.services.GitService
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup // Import ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
// ContentManagerUtil might not be needed anymore if OpenBranchSelectionTabAction is fully removed
// import com.intellij.ui.content.ContentManagerUtil // For findContent and removeContent 
import javax.swing.JComponent
import javax.swing.JPanel
import java.awt.FlowLayout
import com.intellij.ui.components.JButton // Using IntelliJ's JButton for better theme integration
import com.intellij.ui.content.ContentManagerListener
import com.intellij.ui.content.ContentManagerEvent

class MyToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val gitChangesUiProvider = GitChangesToolWindow(project)
        val contentFactory = ContentFactory.getInstance() // Already here, good.

        val gitService = project.service<GitService>()
        val initialBranchName = gitService.getCurrentBranch() ?: "HEAD"
        val initialBranchUi = gitChangesUiProvider.createBranchContentView(initialBranchName)
        val initialContent = contentFactory.createContent(initialBranchUi, initialBranchName, false)
        initialContent.isCloseable = true // Initial main branch tab can be closed
        initialContent.isPinned = false
        toolWindow.contentManager.addContent(initialContent)
        // Do not select initialContent yet, let the button tab be added first, then select initialContent.

        // Create Button Component
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        val button = JButton() // Using com.intellij.ui.components.JButton
        button.icon = AllIcons.General.Add
        button.isBorderPainted = false
        button.isContentAreaFilled = false
        button.isOpaque = false
        button.toolTipText = "Open branch selection tab"
        button.addActionListener {
            // Action listener logic starts here
            // 1. Access Necessary Variables (already in scope or easily gettable)
            val currentProject = project // from outer scope
            val currentToolWindow = toolWindow // from outer scope
            val currentUiProvider = uiProvider // from outer scope
            val currentContentManager = currentToolWindow.contentManager
            val currentContentFactory = ContentFactory.getInstance() // Can also use contentFactory from outer scope

            // 2. Define Selection Tab Name
            val selectionTabName = "Select Branch"

            // 3. Check for Existing "Select Branch" Tab
            val existingSelectionTab = currentContentManager.findContent(selectionTabName)
            if (existingSelectionTab != null) {
                currentContentManager.setSelectedContent(existingSelectionTab, true)
            } else {
                // 4. Create New "Select Branch" Tab (if it doesn't exist)
                val branchSelectionUi = currentUiProvider.createBranchSelectionView { selectedBranchName ->
                    // onBranchSelected lambda logic starts here
                    val manager = currentToolWindow.contentManager // Can also use currentContentManager
                    val selTabName = "Select Branch" // Must match selectionTabName

                    val selectionTabContent = manager.findContent(selTabName)

                    if (selectionTabContent == null) {
                        println("Error: Could not find the '${selTabName}' tab.")
                        return@createBranchSelectionView
                    }

                    var existingBranchTab: com.intellij.ui.content.Content? = null
                    for (content in manager.contents) {
                        if (content.displayName == selectedBranchName && content != selectionTabContent) {
                            existingBranchTab = content
                            break
                        }
                    }

                    if (existingBranchTab != null) {
                        manager.setSelectedContent(existingBranchTab, true)
                        manager.removeContent(selectionTabContent, true)
                    } else {
                        selectionTabContent.displayName = selectedBranchName
                        selectionTabContent.component = currentUiProvider.createBranchContentView(selectedBranchName)
                        manager.setSelectedContent(selectionTabContent, true)
                    }
                    // onBranchSelected lambda logic ends here
                }

                val newSelectionContent = currentContentFactory.createContent(branchSelectionUi, selectionTabName, true) // true for focusable
                newSelectionContent.isCloseable = true
                currentContentManager.addContent(newSelectionContent)
                currentContentManager.setSelectedContent(newSelectionContent, true)
            }
            // Action listener logic ends here
        }
        buttonPanel.add(button)

        // Create Content Object (Button Holder Tab)
        // ContentFactory instance is already available as contentFactory
        val buttonHolderContent = contentFactory.createContent(buttonPanel, "", false)
        buttonHolderContent.isCloseable = false
        buttonHolderContent.isPinned = true
        // buttonHolderContent.setDisposer { /* Not needed for this simple setup */ }

        // Add to ContentManager at index 0
        toolWindow.contentManager.addContent(buttonHolderContent, 0)

        // Now select the initial branch content after the button tab has been added
        toolWindow.contentManager.setSelectedContent(initialContent, true)


        // --- ADD SETTINGS GROUP DIRECTLY TO THE TOOL WINDOW'S "GEAR" MENU ---
        // Get the ActionGroup that represents your settings section (it's already a popup group)
        val pluginSettingsSubMenu: ActionGroup = gitChangesUiProvider.createToolWindowSettingsGroup()

        // This is the group whose children will appear directly in the gear menu.
        val allGearActionsGroup = DefaultActionGroup()
        allGearActionsGroup.add(pluginSettingsSubMenu) // Add your settings sub-menu as an item
        // If you had other top-level actions for the gear menu, you'd add them here.
        // e.g., allGearActionsGroup.add(Separator.getInstance())
        // e.g., allGearActionsGroup.add(SomeOtherAction())

        toolWindow.setAdditionalGearActions(allGearActionsGroup)
        // --- END SETTINGS ACTION ---

        toolWindow.contentManager.addContentManagerListener(object : ContentManagerListener {
            override fun selectionChanged(event: ContentManagerEvent) {
                val selectedContentFromEvent = event.content
                // Check if the selected tab is the "button holder tab" (empty display name)
                if (selectedContentFromEvent.displayName.isNullOrEmpty() &&
                    event.operation == ContentManagerEvent.ContentOperation.SELECT) {

                    val currentContentManager = toolWindow.contentManager // toolWindow is from outer scope
                    val selectionTabName = "Select Branch"
                    val selectBranchActualTab = currentContentManager.findContent(selectionTabName)

                    if (selectBranchActualTab != null) {
                        // If "Select Branch" tab exists, ensure it's selected.
                        // This also handles the case where this event is for "Select Branch" tab itself.
                        if (!selectBranchActualTab.isSelected) {
                            currentContentManager.setSelectedContent(selectBranchActualTab, true)
                        }
                    } else {
                        // "Select Branch" tab does not exist. Duplicate button's ActionListener logic.
                        // uiProvider and contentFactory are from the outer scope of createToolWindowContent
                        val branchSelectionUi = uiProvider.createBranchSelectionView { selectedBranchName ->
                            // This is the onBranchSelected lambda, duplicated from the button's ActionListener
                            val manager = toolWindow.contentManager // toolWindow from outer scope
                            val selTabName = "Select Branch" // Must match selectionTabName

                            val selectionTabContent = manager.findContent(selTabName)

                            if (selectionTabContent == null) {
                                println("Error: Could not find the '${selTabName}' tab from ContentManagerListener.")
                                return@createBranchSelectionView
                            }

                            var existingBranchTab: com.intellij.ui.content.Content? = null
                            for (content in manager.contents) {
                                if (content.displayName == selectedBranchName && content != selectionTabContent) {
                                    existingBranchTab = content
                                    break
                                }
                            }

                            if (existingBranchTab != null) {
                                manager.setSelectedContent(existingBranchTab, true)
                                manager.removeContent(selectionTabContent, true)
                            } else {
                                selectionTabContent.displayName = selectedBranchName
                                selectionTabContent.component = uiProvider.createBranchContentView(selectedBranchName)
                                manager.setSelectedContent(selectionTabContent, true)
                            }
                        }

                        val newSelectionContent = contentFactory.createContent(branchSelectionUi, selectionTabName, true) // true for focusable
                        newSelectionContent.isCloseable = true
                        currentContentManager.addContent(newSelectionContent)
                        currentContentManager.setSelectedContent(newSelectionContent, true) // Select the newly created tab
                    }
                }
            }

            override fun contentAdded(event: ContentManagerEvent) {
                // No specific action needed for this experimental step
            }

            override fun contentRemoved(event: ContentManagerEvent) {
                // No specific action needed for this experimental step
                // A more robust solution might handle selection falling back to button holder here.
            }

            override fun contentRemoveQuery(event: ContentManagerEvent) {
                // No specific action needed for this experimental step
            }
        })
    }

    override fun shouldBeAvailable(project: Project) = true
}
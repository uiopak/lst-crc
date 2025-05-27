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
import com.intellij.ui.content.ContentManagerUtil // For findContent and removeContent
import javax.swing.JComponent

class MyToolWindowFactory : ToolWindowFactory {

    private fun createAndAddPlusTab(
        project: Project,
        toolWindow: ToolWindow,
        uiProvider: GitChangesToolWindow,
        contentFactory: ContentFactory
    ) {
        val plusTabContent = uiProvider.createBranchSelectionView { selectedBranchName ->
            // 1a. Identify the currently active "+" tab
            // The "current" + tab is the one that contains the branch selection view that triggered this callback.
            // We need to find it in the content manager. A simple way is to assume it's the last one named "+".
            // A more robust way would involve passing the Content object to the callback if possible,
            // but given the current structure, we'll find it by name and assume it's the one.
            var plusContentTab = toolWindow.contentManager.findContent("+")

            // 1b. Check if a tab for the selectedBranchName already exists
            val existingContent = toolWindow.contentManager.findContent(selectedBranchName)
            if (existingContent != null) {
                // 1c. If it exists, select that tab.
                toolWindow.contentManager.setSelectedContent(existingContent, true)
                // And remove the now redundant "+" tab
                if (plusContentTab != null) {
                    toolWindow.contentManager.removeContent(plusContentTab, true)
                }
            } else {
                // 1d. If it does not exist, transform the current "+" tab
                if (plusContentTab != null) {
                    // i. Rename it to selectedBranchName
                    plusContentTab.displayName = selectedBranchName
                    // ii. Replace its content
                    val newBranchUi = uiProvider.createBranchContentView(selectedBranchName)
                    plusContentTab.component = newBranchUi
                    // iii. Make it closable
                    plusContentTab.isCloseable = true
                    // iv. Select this transformed tab
                    toolWindow.contentManager.setSelectedContent(plusContentTab, true)
                } else {
                    // Fallback: if somehow the "+" tab wasn't found, create a new one for the branch
                    // This shouldn't ideally happen if the UI flow is correct.
                    val newBranchUi = uiProvider.createBranchContentView(selectedBranchName)
                    val newContent = contentFactory.createContent(newBranchUi, selectedBranchName, false)
                    newContent.isCloseable = true
                    toolWindow.contentManager.addContent(newContent)
                    toolWindow.contentManager.setSelectedContent(newContent, true)
                }
            }
            // v. After transforming/selecting, trigger the creation of a new "+" tab.
            createAndAddPlusTab(project, toolWindow, uiProvider, contentFactory)
        }

        val plusContent = contentFactory.createContent(plusTabContent, "+", false)
        plusContent.isCloseable = false // The "+" tab itself is not closable
        toolWindow.contentManager.addContent(plusContent)
        // The new "+" tab should not automatically take focus; focus should remain on the selected/created branch tab.
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val gitChangesUiProvider = GitChangesToolWindow(project)
        val contentFactory = ContentFactory.getInstance()

        val gitService = project.service<GitService>()
        val initialBranchName = gitService.getCurrentBranch() ?: "HEAD"
        val initialBranchUi = gitChangesUiProvider.createBranchContentView(initialBranchName)
        val initialContent = contentFactory.createContent(initialBranchUi, initialBranchName, false)
        initialContent.isCloseable = true
        initialContent.isPinned = false // Assuming default behavior, can be true if needed
        toolWindow.contentManager.addContent(initialContent)
        toolWindow.contentManager.setSelectedContent(initialContent, true) // Select the initial branch tab

        // Call the new function to add the initial "+" tab
        createAndAddPlusTab(project, toolWindow, gitChangesUiProvider, contentFactory)

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
    }

    override fun shouldBeAvailable(project: Project) = true
}
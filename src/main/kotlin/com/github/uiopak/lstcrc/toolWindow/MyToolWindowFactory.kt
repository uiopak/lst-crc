package com.github.uiopak.lstcrc.toolWindow

package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.services.GitService
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManagerEvent // Added import
import com.intellij.ui.content.ContentManagerListener // Added import
import com.intellij.ide.util.PropertiesComponent
import javax.swing.JComponent

class MyToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val gitChangesUiProvider = GitChangesToolWindow(project, toolWindow.disposable)
        val contentFactory = ContentFactory.getInstance()
        val gitService = project.service<GitService>()
        val stateService = ToolWindowStateService.getInstance(project)
        val persistedState = stateService.state
        val contentManager = toolWindow.contentManager // Store reference

        // Always create and add the "HEAD" tab first.
        val currentRepository = gitService.getCurrentRepository()
        val headTabTargetName = if (currentRepository != null) {
            currentRepository.currentBranchName ?: currentRepository.currentRevision ?: "HEAD"
        } else {
            "HEAD"
        }
        val headView = gitChangesUiProvider.createBranchContentView(headTabTargetName)
        val headContent = contentFactory.createContent(headView, "HEAD", false)
        headContent.isCloseable = false
        headContent.isPinned = true
        contentManager.addContent(headContent)

        var selectedContentRestored = false

        if (persistedState.openTabs.isNotEmpty()) {
            persistedState.openTabs.forEach { tabInfo ->
                if (tabInfo.branchName != "HEAD" && tabInfo.branchName != headTabTargetName) {
                    val branchView = gitChangesUiProvider.createBranchContentView(tabInfo.branchName)
                    val branchContent = contentFactory.createContent(branchView, tabInfo.branchName, false)
                    branchContent.isCloseable = true
                    contentManager.addContent(branchContent)
                }
            }

            if (persistedState.selectedTabIndex >= 0 && persistedState.selectedTabIndex < persistedState.openTabs.size) {
                val selectedBranchNameFromState = persistedState.openTabs[persistedState.selectedTabIndex].branchName
                val contentToSelect = contentManager.contents.find { it.displayName == selectedBranchNameFromState && it.isCloseable }
                if (contentToSelect != null) {
                    contentManager.setSelectedContent(contentToSelect, true)
                    selectedContentRestored = true
                }
            }
        } else {
            val currentActualBranchName = currentRepository?.currentBranchName
            if (currentActualBranchName != null && currentActualBranchName != "HEAD" && currentActualBranchName != headTabTargetName) {
                val initialBranchView = gitChangesUiProvider.createBranchContentView(currentActualBranchName)
                val initialBranchContent = contentFactory.createContent(initialBranchView, currentActualBranchName, false)
                initialBranchContent.isCloseable = true
                contentManager.addContent(initialBranchContent)
                contentManager.setSelectedContent(initialBranchContent, true)
                selectedContentRestored = true
            }
        }

        if (!selectedContentRestored) {
            contentManager.setSelectedContent(headContent, true)
        }

        // Add ContentManagerListener
        contentManager.addContentManagerListener(object : ContentManagerListener {
            override fun contentAdded(event: ContentManagerEvent) {
                // Adding is handled by OpenBranchSelectionTabAction or initial load.
                // However, if a tab is added programmatically elsewhere, this could be a place.
                // For now, ensure selection change below handles newly added tab selection.
                // We also need to update the selected tab index in the state service if a new tab is added and selected.
                val addedContent = event.content
                if (addedContent.isCloseable && contentManager.isSelected(addedContent)) {
                     // This can also be triggered when initial tabs are added.
                     // The addTab for *new* tabs is in OpenBranchSelectionTabAction.
                     // Here, we primarily care about updating selection.
                    val branchName = addedContent.displayName
                    val closableTabsInManager = contentManager.contents.filter { it.isCloseable }
                    val currentSelectedInClosable = closableTabsInManager.indexOf(addedContent)
                    if (currentSelectedInClosable != -1) {
                        // Ensure the tab is actually in the state service list before setting index
                        // This check is important because initial tabs might not be in stateService yet
                        // if this listener fires before they are explicitly added by OpenBranch... or load.
                        // However, addTab in OpenBranchSelectionTabAction should handle new tabs.
                        // And loadState handles initial load.
                        // This selectionChanged is more for subsequent user interactions.
                         if (stateService.state.openTabs.any { it.branchName == branchName }) {
                            stateService.setSelectedTab(currentSelectedInClosable)
                        }
                    }
                }
            }

            override fun contentRemoved(event: ContentManagerEvent) {
                if (event.content.isCloseable) { // Only care about closable tabs
                    val branchName = event.content.displayName
                    stateService.removeTab(branchName)
                    // Selection will likely change, let selectionChanged handle updating the selected index in state.
                    // If the last closable tab is removed, HEAD should become selected.
                    if (contentManager.contentCount > 0 && contentManager.contents.none{it.isCloseable}) {
                         contentManager.setSelectedContent(contentManager.getContent(0)!!, true) // Select HEAD
                         stateService.setSelectedTab(-1) // Or an appropriate index for "no closable tab selected"
                    } else if (contentManager.contentCount == 0) {
                        // Should not happen if HEAD is pinned, but as a safeguard
                         stateService.setSelectedTab(-1)
                    }
                }
            }

            override fun selectionChanged(event: ContentManagerEvent) {
                val selectedContent = event.content
                if (selectedContent.isCloseable) {
                    val branchName = selectedContent.displayName
                    // Find index in the persisted list (which only contains closable tabs)
                    val closableTabsInState = stateService.state.openTabs.map { it.branchName }
                    val indexInPersistedList = closableTabsInState.indexOf(branchName)
                    if (indexInPersistedList != -1) {
                        stateService.setSelectedTab(indexInPersistedList)
                    } else {
                        // This case might happen if a tab is selected that somehow isn't in our state's list yet.
                        // This could be an issue if contentAdded didn't fully sync.
                        // For safety, one could add it here, but it's better if addTab is the primary source.
                        // For now, if not found, we don't update, assuming it's a transient state or HEAD.
                    }
                } else {
                    // "HEAD" or other non-closable tab is selected
                    stateService.setSelectedTab(-1) // Mark as no *closable* tab selected
                }
            }
        })

        // Existing actions
        val openSelectionTabAction = OpenBranchSelectionTabAction(project, toolWindow, gitChangesUiProvider)
        toolWindow.setTitleActions(listOf(openSelectionTabAction))

        val propertiesComponent = PropertiesComponent.getInstance()
        val settingsProvider = ToolWindowSettingsProvider(propertiesComponent)
        val pluginSettingsSubMenu: ActionGroup = settingsProvider.createToolWindowSettingsGroup()

        val allGearActionsGroup = DefaultActionGroup()
        allGearActionsGroup.add(pluginSettingsSubMenu)
        toolWindow.setAdditionalGearActions(allGearActionsGroup)
    }

    override fun shouldBeAvailable(project: Project) = true
}

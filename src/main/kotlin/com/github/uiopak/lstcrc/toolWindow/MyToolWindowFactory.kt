package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.services.GitService
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.diagnostic.thisLogger // Added for logging

class MyToolWindowFactory : ToolWindowFactory {
    private val logger = thisLogger() // Initialize logger

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        try {
            project.getService(com.github.uiopak.lstcrc.services.VfsListenerService::class.java)?.let {
                logger.info("VfsListenerService instance successfully retrieved during tool window creation.")
            } ?: logger.warn("VfsListenerService is null after attempting to retrieve it. VFS-based updates might not function as expected.")
        } catch (e: Throwable) {
            logger.error("EXCEPTION while trying to initialize or retrieve VfsListenerService. VFS updates likely impacted.", e)
        }

        logger.info("createToolWindowContent called for project: ${project.name}")
        val gitChangesUiProvider = GitChangesToolWindow(project, toolWindow.disposable)
        val contentFactory = ContentFactory.getInstance()
        val gitService = project.service<GitService>()
        val stateService = ToolWindowStateService.getInstance(project)
        logger.debug("ToolWindowStateService instance obtained.")
        val persistedState = stateService.state // This will call getState()
        logger.trace("Initial persistedState loaded: $persistedState")

        val contentManager = toolWindow.contentManager
        val currentRepository = gitService.getCurrentRepository()
        val headTabTargetName = currentRepository?.currentBranchName ?: currentRepository?.currentRevision ?: "HEAD"
        logger.debug("headTabTargetName is $headTabTargetName")

        // Create and add the non-closable "HEAD" tab first.
        val headView = gitChangesUiProvider.createBranchContentView(headTabTargetName)
        val headContent = contentFactory.createContent(headView, "HEAD", false).apply {
            isCloseable = false
            isPinned = true
        }
        contentManager.addContent(headContent)
        logger.info("'HEAD' tab added to content manager.")

        var aTabWasSelected = restorePersistedTabs(project, contentManager, gitChangesUiProvider, persistedState, headTabTargetName, stateService)

        if (!aTabWasSelected) {
            aTabWasSelected = setupInitialTabs(project, contentManager, gitChangesUiProvider, gitService, stateService, headTabTargetName)
        }

        // Fallback: If no tab was selected through restoration or initial setup, select HEAD.
        // The selectionChanged listener will then ensure stateService.setSelectedTab(-1) is called.
        if (!aTabWasSelected) {
            logger.info("No specific tab restored or initially set. Selecting 'HEAD' tab by default.")
            contentManager.setSelectedContent(headContent, true)
            // Explicitly set state here as selectionChanged might not fire if HEAD was already selected (e.g. only tab)
            // However, ToolWindowStateService.setSelectedTab now handles no-op if index is same.
            // Let selectionChanged handle it for consistency. If HEAD is already selected, contentManager might not fire event.
            // If contentManager.getSelectedContent() is already headContent, this explicit call is important.
            if (contentManager.selectedContent == headContent) {
                 stateService.setSelectedTab(-1) // Ensure state is updated if HEAD is programmatically selected.
            }
        }

        contentManager.addContentManagerListener(object : ContentManagerListener {
            override fun contentAdded(event: ContentManagerEvent) {
                logger.debug("ContentManagerListener: contentAdded - ${event.content.displayName}. No specific action taken to update stateService selected tab here.")
                // The primary responsibility for updating selected tab state in ToolWindowStateService
                // now rests with the selectionChanged listener or explicit setup logic.
            }

            override fun contentRemoved(event: ContentManagerEvent) {
                logger.debug("ContentManagerListener: contentRemoved - ${event.content.displayName}, isCloseable: ${event.content.isCloseable}")
                if (event.content.isCloseable) {
                    val branchName = event.content.displayName
                    logger.debug("Closable tab $branchName removed. Calling stateService.removeTab($branchName).")
                    stateService.removeTab(branchName) // This updates the persisted list of open tabs

                    // If the removed tab was selected, and no other tab gets auto-selected,
                    // or if the auto-selected tab is HEAD, selectionChanged handles it.
                    // If all closable tabs are removed, select HEAD.
                    if (contentManager.contents.none { it.isCloseable } && contentManager.contentCount > 0) {
                        logger.debug("No closable tabs left after removal. Selecting 'HEAD' tab.")
                        contentManager.setSelectedContent(contentManager.getContent(0)!!, true)
                        // selectionChanged will call stateService.setSelectedTab(-1)
                    } else if (contentManager.selectedContent == null && contentManager.contentCount > 0) {
                        // This case might occur if the IDE doesn't auto-select another tab.
                        logger.debug("No tab selected after removal. Selecting 'HEAD' tab.")
                        contentManager.setSelectedContent(contentManager.getContent(0)!!, true)
                    } else if (contentManager.contentCount == 0) {
                        // Should not happen if HEAD is pinned and always present.
                        logger.warn("All tabs removed, including HEAD. This is unexpected. Setting selected tab state to -1.")
                        stateService.setSelectedTab(-1)
                    }
                    // If another closable tab is auto-selected by the ContentManager,
                    // the selectionChanged event will fire and handle updating the state.
                }
            }

            override fun selectionChanged(event: ContentManagerEvent) {
                val selectedContent = event.content
                logger.debug("ContentManagerListener: selectionChanged - new selection: ${selectedContent.displayName}, isCloseable: ${selectedContent.isCloseable}")

                if (selectedContent.isCloseable) {
                    val branchName = selectedContent.displayName
                    // state.openTabs should only contain closable tabs.
                    val indexInStateList = stateService.state.openTabs.indexOfFirst { it.branchName == branchName }

                    if (indexInStateList != -1) {
                        logger.debug("Selected closable tab '$branchName' found at index $indexInStateList in state.openTabs. Calling stateService.setSelectedTab($indexInStateList).")
                        stateService.setSelectedTab(indexInStateList)
                    } else {
                        // This could happen if a tab is added and selected by external means (e.g. user action not yet tracked)
                        // or if there's a timing issue with state updates.
                        logger.warn("Selected closable tab '$branchName' not found in stateService.state.openTabs. Selection in state will not be updated by this event. This could be normal if the tab was just added by OpenBranchSelectionTabAction and state isn't updated yet.")
                        // To handle tabs added by OpenBranchSelectionTabAction, that action itself should ensure
                        // stateService.addTab and stateService.setSelectedTab are called appropriately.
                    }
                } else {
                    // Non-closable tab selected (e.g., "HEAD")
                    logger.debug("Non-closable tab '${selectedContent.displayName}' (likely HEAD) selected. Calling stateService.setSelectedTab(-1).")
                    stateService.setSelectedTab(-1)
                }
            }
        })
        logger.debug("ContentManagerListener added.")

        val openSelectionTabAction = OpenBranchSelectionTabAction(project, toolWindow, gitChangesUiProvider)
        toolWindow.setTitleActions(listOf(openSelectionTabAction))

        val propertiesComponent = PropertiesComponent.getInstance()
        val settingsProvider = ToolWindowSettingsProvider(propertiesComponent)
        val pluginSettingsSubMenu: ActionGroup = settingsProvider.createToolWindowSettingsGroup()

        val allGearActionsGroup = DefaultActionGroup()
        allGearActionsGroup.add(pluginSettingsSubMenu)
        toolWindow.setAdditionalGearActions(allGearActionsGroup)
        logger.debug("Additional gear actions set.")
        logger.info("createToolWindowContent finished.")
    }

    private fun restorePersistedTabs(
        project: Project,
        contentManager: ContentManager,
        gitChangesUiProvider: GitChangesToolWindow,
        persistedState: ToolWindowState,
        headTabTargetName: String, // To avoid re-creating HEAD or equivalent
        stateService: ToolWindowStateService // To access logger and potentially other methods if needed
    ): Boolean {
        var selectedContentRestored = false
        val contentFactory = ContentFactory.getInstance()

        if (persistedState.openTabs.isNotEmpty()) {
            logger.info("Persisted state has ${persistedState.openTabs.size} open tabs. Restoring them.")
            persistedState.openTabs.forEach { tabInfo ->
                // Ensure we don't try to re-create HEAD or a branch that's effectively HEAD
                if (tabInfo.branchName != "HEAD" && tabInfo.branchName != headTabTargetName) {
                    logger.debug("Restoring tab for branch: ${tabInfo.branchName}")
                    val branchView = gitChangesUiProvider.createBranchContentView(tabInfo.branchName)
                    val branchContent = contentFactory.createContent(branchView, tabInfo.branchName, false).apply {
                        isCloseable = true
                    }
                    contentManager.addContent(branchContent)
                    // Note: stateService.addTab is NOT called here, as these tabs are already in the state.
                } else {
                    logger.debug("Skipping restoration of tab '${tabInfo.branchName}' as it's considered equivalent to the base 'HEAD' tab or is literally 'HEAD'.")
                }
            }

            // Attempt to select the persisted selected tab *among the closable tabs*
            if (persistedState.selectedTabIndex >= 0 && persistedState.selectedTabIndex < persistedState.openTabs.size) {
                val selectedBranchNameFromState = persistedState.openTabs[persistedState.selectedTabIndex].branchName
                logger.debug("Attempting to restore selected tab to: '$selectedBranchNameFromState' (persisted index ${persistedState.selectedTabIndex})")

                // Find the content in the ContentManager that matches the branch name and is closable
                val contentToSelect = contentManager.contents.find { it.displayName == selectedBranchNameFromState && it.isCloseable }

                if (contentToSelect != null) {
                    contentManager.setSelectedContent(contentToSelect, true) // true to request focus
                    // The selectionChanged listener will handle calling stateService.setSelectedTab with the correct new index.
                    selectedContentRestored = true
                    logger.info("Successfully initiated selection restoration to '$selectedBranchNameFromState'. selectionChanged listener will finalize state.")
                } else {
                    logger.warn("Could not find closable content for persisted selected branch '$selectedBranchNameFromState' to restore selection. It might be HEAD or was not restored properly.")
                }
            } else {
                logger.debug("No valid selectedTabIndex (>=0 and < size) in persisted state (${persistedState.selectedTabIndex}). Will not attempt to restore selection based on index.")
            }
        } else {
            logger.info("No persisted closable tabs found in state to restore.")
        }
        return selectedContentRestored
    }

    private fun setupInitialTabs(
        project: Project,
        contentManager: ContentManager,
        gitChangesUiProvider: GitChangesToolWindow,
        gitService: GitService,
        stateService: ToolWindowStateService,
        headTabTargetName: String // To avoid creating a tab for current branch if it's HEAD
    ): Boolean {
        var initialTabSelected = false
        val contentFactory = ContentFactory.getInstance()
        val currentRepository = gitService.getCurrentRepository()
        val currentActualBranchName = currentRepository?.currentBranchName

        logger.info("No persisted tabs or selection restored. Performing initial (closable) branch tab setup if applicable.")

        if (currentActualBranchName != null && currentActualBranchName != "HEAD" && currentActualBranchName != headTabTargetName) {
            logger.info("Current actual branch is '$currentActualBranchName'. Creating an initial tab for it.")
            val initialBranchView = gitChangesUiProvider.createBranchContentView(currentActualBranchName)
            val initialBranchContent = contentFactory.createContent(initialBranchView, currentActualBranchName, false).apply {
                isCloseable = true
            }
            contentManager.addContent(initialBranchContent)
            // Add to state BEFORE selecting in UI, so selectionChanged can find it.
            stateService.addTab(currentActualBranchName)
            logger.debug("Added initial tab '$currentActualBranchName' to stateService.")

            contentManager.setSelectedContent(initialBranchContent, true) // Request focus
            // The selectionChanged listener should fire due to setSelectedContent.
            // It will then call stateService.setSelectedTab with the correct index.
            initialTabSelected = true
            logger.info("Initial tab for '$currentActualBranchName' created and selected. selectionChanged listener will finalize state.")
        } else {
            logger.debug("Current branch is '$currentActualBranchName', which is considered 'HEAD' or equivalent ('$headTabTargetName'). No separate initial closable tab will be created.")
        }
        return initialTabSelected
    }

    override fun shouldBeAvailable(project: Project) = true
}

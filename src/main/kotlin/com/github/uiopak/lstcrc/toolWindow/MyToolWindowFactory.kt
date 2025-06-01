package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.services.GitService
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.diagnostic.thisLogger // Added for logging

class MyToolWindowFactory : ToolWindowFactory {
    private val logger = thisLogger() // Initialize logger

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // FORCED DIAGNOSTIC: Attempt to get VfsListenerService
        try {
            val retrievedVfsListenerService = project.getService(com.github.uiopak.lstcrc.services.VfsListenerService::class.java)
            if (retrievedVfsListenerService != null) {
                logger.error("FORCED LOG (MyToolWindowFactory): Successfully retrieved VfsListenerService instance: $retrievedVfsListenerService")
                // If the service is retrieved here, its init block (and VfsChangeListener's init) should have already fired or will fire now.
            } else {
                logger.error("FORCED LOG (MyToolWindowFactory): project.getService(VfsListenerService::class.java) returned NULL.")
            }
        } catch (e: Throwable) {
            logger.error("FORCED LOG (MyToolWindowFactory): EXCEPTION while trying to get VfsListenerService.", e)
        }
        logger.info("MyToolWindowFactory: createToolWindowContent called.")
        val gitChangesUiProvider = GitChangesToolWindow(project, toolWindow.disposable)
        val contentFactory = ContentFactory.getInstance()
        val gitService = project.service<GitService>()
        val stateService = ToolWindowStateService.getInstance(project)
        logger.info("MyToolWindowFactory: ToolWindowStateService instance obtained.")
        val persistedState = stateService.state // This will call getState()
        logger.info("MyToolWindowFactory: Initial persistedState loaded: $persistedState")

        val contentManager = toolWindow.contentManager

        val currentRepository = gitService.getCurrentRepository()
        val headTabTargetName = if (currentRepository != null) {
            currentRepository.currentBranchName ?: currentRepository.currentRevision ?: "HEAD"
        } else {
            "HEAD"
        }
        logger.info("MyToolWindowFactory: headTabTargetName is $headTabTargetName")

        val headView = gitChangesUiProvider.createBranchContentView(headTabTargetName)
        val headContent = contentFactory.createContent(headView, "HEAD", false)
        headContent.isCloseable = false
        headContent.isPinned = true
        contentManager.addContent(headContent)
        logger.info("MyToolWindowFactory: 'HEAD' tab added to content manager.")

        var selectedContentRestored = false

        if (persistedState.openTabs.isNotEmpty()) {
            logger.info("MyToolWindowFactory: Persisted state has ${persistedState.openTabs.size} open tabs. Restoring them.")
            persistedState.openTabs.forEach { tabInfo ->
                if (tabInfo.branchName != "HEAD" && tabInfo.branchName != headTabTargetName) {
                    logger.info("MyToolWindowFactory: Restoring tab for branch ${tabInfo.branchName}")
                    val branchView = gitChangesUiProvider.createBranchContentView(tabInfo.branchName)
                    val branchContent = contentFactory.createContent(branchView, tabInfo.branchName, false)
                    branchContent.isCloseable = true
                    contentManager.addContent(branchContent)
                } else {
                    logger.info("MyToolWindowFactory: Skipping restoration of tab ${tabInfo.branchName} as it's HEAD or equivalent.")
                }
            }

            if (persistedState.selectedTabIndex >= 0 && persistedState.selectedTabIndex < persistedState.openTabs.size) {
                val selectedBranchNameFromState = persistedState.openTabs[persistedState.selectedTabIndex].branchName
                logger.info("MyToolWindowFactory: Attempting to restore selected tab: $selectedBranchNameFromState (index ${persistedState.selectedTabIndex})")
                val contentToSelect = contentManager.contents.find { it.displayName == selectedBranchNameFromState && it.isCloseable }
                if (contentToSelect != null) {
                    contentManager.setSelectedContent(contentToSelect, true)
                    selectedContentRestored = true
                    logger.info("MyToolWindowFactory: Successfully restored selection to $selectedBranchNameFromState.")
                } else {
                    logger.warn("MyToolWindowFactory: Could not find content for persisted selected branch $selectedBranchNameFromState to restore selection.")
                }
            } else {
                 logger.info("MyToolWindowFactory: No valid selectedTabIndex in persisted state (${persistedState.selectedTabIndex}).")
            }
        } else {
            logger.info("MyToolWindowFactory: No persisted tabs found in state. Performing initial branch tab setup if needed.")
            val currentActualBranchName = currentRepository?.currentBranchName
            if (currentActualBranchName != null && currentActualBranchName != "HEAD" && currentActualBranchName != headTabTargetName) {
                logger.info("MyToolWindowFactory: Creating initial tab for current branch $currentActualBranchName.")
                val initialBranchView = gitChangesUiProvider.createBranchContentView(currentActualBranchName)
                val initialBranchContent = contentFactory.createContent(initialBranchView, currentActualBranchName, false)
                initialBranchContent.isCloseable = true
                contentManager.addContent(initialBranchContent)
                contentManager.setSelectedContent(initialBranchContent, true)
                selectedContentRestored = true // Mark that we've set a selection

                // Also add this initial tab to the state service
                // This was a potential gap: initial tab wasn't added to state.
                logger.info("MyToolWindowFactory: Adding initial tab $currentActualBranchName to state service.")
                stateService.addTab(currentActualBranchName)
                val closableTabs = contentManager.contents.filter { it.isCloseable }.map { it.displayName }
                val newTabIndexInClosable = closableTabs.indexOf(currentActualBranchName)
                if (newTabIndexInClosable != -1) {
                     logger.info("MyToolWindowFactory: Setting selected tab in state service to $newTabIndexInClosable for $currentActualBranchName.")
                    stateService.setSelectedTab(newTabIndexInClosable)
                }

            } else {
                logger.info("MyToolWindowFactory: Current branch is HEAD or equivalent, no initial closable tab created.")
            }
        }

        if (!selectedContentRestored) {
            logger.info("MyToolWindowFactory: No specific tab restored or set as selected, selecting 'HEAD' tab.")
            contentManager.setSelectedContent(headContent, true)
            // If HEAD is selected, ensure state service reflects this.
            stateService.setSelectedTab(-1)
        }

        contentManager.addContentManagerListener(object : ContentManagerListener {
            override fun contentAdded(event: ContentManagerEvent) {
                logger.debug("MyToolWindowFactory.ContentManagerListener: contentAdded - ${event.content.displayName}")
                // Logic for contentAdded was primarily for selection.
                // Tab additions themselves are handled by OpenBranchSelectionTabAction or initial load.
                // If a newly added tab is selected, selectionChanged should handle state update.
                val addedContent = event.content
                if (addedContent.isCloseable && contentManager.isSelected(addedContent)) {
                    val branchName = addedContent.displayName
                    logger.info("MyToolWindowFactory.ContentManagerListener: A closable tab $branchName was added and selected.")
                    // This might be redundant if OpenBranchSelectionTabAction already set it.
                    // Or if initial tab setup handled it.
                    // But it acts as a catch-all for selection.
                    val closableTabsInState = stateService.state.openTabs.map { it.branchName }
                    val indexInPersistedList = closableTabsInState.indexOf(branchName)
                    if (indexInPersistedList != -1) {
                        if (stateService.state.selectedTabIndex != indexInPersistedList) {
                           logger.info("MyToolWindowFactory.ContentManagerListener: Updating selected tab in state to $indexInPersistedList for $branchName due to contentAdded+selected.")
                           stateService.setSelectedTab(indexInPersistedList)
                        }
                    } else {
                        // This could happen if a tab is added by means other than our action, and it's not yet in state.
                        // For now, we assume tabs are added via our action or initial load.
                        logger.warn("MyToolWindowFactory.ContentManagerListener: Selected tab $branchName (from contentAdded) not found in state's openTabs.")
                    }
                }
            }

            override fun contentRemoved(event: ContentManagerEvent) {
                logger.info("MyToolWindowFactory.ContentManagerListener: contentRemoved - ${event.content.displayName}, isCloseable: ${event.content.isCloseable}")
                if (event.content.isCloseable) {
                    val branchName = event.content.displayName
                    logger.info("MyToolWindowFactory.ContentManagerListener: Calling stateService.removeTab($branchName).")
                    stateService.removeTab(branchName)

                    if (contentManager.contentCount > 0 && contentManager.contents.none { it.isCloseable }) {
                        logger.info("MyToolWindowFactory.ContentManagerListener: No closable tabs left. Selecting HEAD and setting state selectedTab to -1.")
                        contentManager.setSelectedContent(contentManager.getContent(0)!!, true)
                        stateService.setSelectedTab(-1)
                    } else if (contentManager.contentCount == 0) {
                        logger.warn("MyToolWindowFactory.ContentManagerListener: All tabs removed (should not happen if HEAD is pinned). Setting state selectedTab to -1.")
                        stateService.setSelectedTab(-1)
                    }
                    // If another closable tab is auto-selected, selectionChanged will handle updating the state.
                }
            }

            override fun selectionChanged(event: ContentManagerEvent) {
                val selectedContent = event.content
                logger.info("MyToolWindowFactory.ContentManagerListener: selectionChanged - new selection: ${selectedContent.displayName}, isCloseable: ${selectedContent.isCloseable}")
                if (selectedContent.isCloseable) {
                    val branchName = selectedContent.displayName
                    val closableTabsInState = stateService.state.openTabs.map { it.branchName }
                    val indexInPersistedList = closableTabsInState.indexOf(branchName)

                    if (indexInPersistedList != -1) {
                        logger.info("MyToolWindowFactory.ContentManagerListener: Selected closable tab $branchName. Calling stateService.setSelectedTab($indexInPersistedList).")
                        stateService.setSelectedTab(indexInPersistedList)
                    } else {
                        logger.warn("MyToolWindowFactory.ContentManagerListener: Selected closable tab $branchName not found in stateService's openTabs. This might be an issue if it wasn't added correctly.")
                        // Potentially, if a tab is created and selected by some other means, it might not be in the state.
                        // For now, we only set selected if it's a known tab.
                    }
                } else {
                    logger.info("MyToolWindowFactory.ContentManagerListener: Non-closable tab ${selectedContent.displayName} (likely HEAD) selected. Calling stateService.setSelectedTab(-1).")
                    stateService.setSelectedTab(-1)
                }
            }
        })
        logger.info("MyToolWindowFactory: ContentManagerListener added.")

        val openSelectionTabAction = OpenBranchSelectionTabAction(project, toolWindow, gitChangesUiProvider)
        toolWindow.setTitleActions(listOf(openSelectionTabAction))

        val propertiesComponent = PropertiesComponent.getInstance()
        val settingsProvider = ToolWindowSettingsProvider(propertiesComponent)
        val pluginSettingsSubMenu: ActionGroup = settingsProvider.createToolWindowSettingsGroup()

        val allGearActionsGroup = DefaultActionGroup()
        allGearActionsGroup.add(pluginSettingsSubMenu)
        toolWindow.setAdditionalGearActions(allGearActionsGroup) // Corrected typo here
        logger.info("MyToolWindowFactory: Additional gear actions set.")
        logger.info("MyToolWindowFactory: createToolWindowContent finished.")
    }

    override fun shouldBeAvailable(project: Project) = true
}

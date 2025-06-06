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
import com.github.uiopak.lstcrc.utils.LstCrcKeys

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
        logger.debug("Initial persistedState loaded: $persistedState")

        val contentManager = toolWindow.contentManager

        val currentRepository = gitService.getCurrentRepository()
        val headTabTargetName = currentRepository?.currentBranchName ?: currentRepository?.currentRevision ?: "HEAD"
        logger.debug("headTabTargetName is $headTabTargetName")

        val headView = gitChangesUiProvider.createBranchContentView(headTabTargetName)
        val headContent = contentFactory.createContent(headView, "HEAD", false).apply {
            isCloseable = false
            isPinned = true
        }
        contentManager.addContent(headContent)
        logger.info("'HEAD' tab added to content manager.")

        var selectedContentRestored = false

        if (persistedState.openTabs.isNotEmpty()) {
            logger.info("Persisted state has ${persistedState.openTabs.size} open tabs. Restoring them.")
            persistedState.openTabs.forEach { tabInfo ->
                if (tabInfo.branchName != "HEAD" && tabInfo.branchName != headTabTargetName) {
                    logger.debug("Restoring tab for branch ${tabInfo.branchName}")
                    val branchView = gitChangesUiProvider.createBranchContentView(tabInfo.branchName)
                    val branchContent = contentFactory.createContent(branchView, tabInfo.branchName, false).apply {
                        isCloseable = true
                    }
                    contentManager.addContent(branchContent)
                } else {
                    logger.debug("Skipping restoration of tab ${tabInfo.branchName} as it's HEAD or equivalent.")
                }
            }

            if (persistedState.selectedTabIndex >= 0 && persistedState.selectedTabIndex < persistedState.openTabs.size) {
                val selectedBranchNameFromState = persistedState.openTabs[persistedState.selectedTabIndex].branchName
                logger.debug("Attempting to restore selected tab: $selectedBranchNameFromState (index ${persistedState.selectedTabIndex})")
                val contentToSelect = contentManager.contents.find { it.displayName == selectedBranchNameFromState && it.isCloseable }
                if (contentToSelect != null) {
                    contentManager.setSelectedContent(contentToSelect, true) // true to request focus
                    selectedContentRestored = true
                    logger.info("Successfully restored selection to $selectedBranchNameFromState.")
                } else {
                    logger.warn("Could not find content for persisted selected branch $selectedBranchNameFromState to restore selection.")
                }
            } else {
                logger.debug("No valid selectedTabIndex in persisted state (${persistedState.selectedTabIndex}).")
            }
        } else {
            logger.info("No persisted tabs found in state. Performing initial branch tab setup if needed.")
            val currentActualBranchName = currentRepository?.currentBranchName
            if (currentActualBranchName != null && currentActualBranchName != "HEAD" && currentActualBranchName != headTabTargetName) {
                logger.info("Creating initial tab for current branch $currentActualBranchName.")
                val initialBranchView = gitChangesUiProvider.createBranchContentView(currentActualBranchName)
                val initialBranchContent = contentFactory.createContent(initialBranchView, currentActualBranchName, false).apply {
                    isCloseable = true
                }
                contentManager.addContent(initialBranchContent)
                contentManager.setSelectedContent(initialBranchContent, true) // true to request focus
                selectedContentRestored = true // Mark that we've set a selection

                logger.debug("Adding initial tab $currentActualBranchName to state service.")
                stateService.addTab(currentActualBranchName)
                // Find the index of the newly added closable tab.
                // Note: This assumes 'openTabs' in stateService reflects only closable tabs for indexing.
                // If stateService.addTab made it the last one, its index would be stateService.state.openTabs.size - 1
                val newTabIndexInState = stateService.state.openTabs.indexOfFirst { it.branchName == currentActualBranchName }
                if (newTabIndexInState != -1) {
                    logger.debug("Setting selected tab in state service to $newTabIndexInState for $currentActualBranchName.")
                    stateService.setSelectedTab(newTabIndexInState)
                } else {
                    logger.warn("Could not find newly added tab '$currentActualBranchName' in state service's openTabs list immediately after adding it. Selection persistence might be affected.")
                }

            } else {
                logger.debug("Current branch is HEAD or equivalent, no initial closable tab created.")
            }
        }

        if (!selectedContentRestored) {
            logger.info("No specific tab restored or set as selected, selecting 'HEAD' tab.")
            contentManager.setSelectedContent(headContent, true)
            // If HEAD is selected, ensure state service reflects this.
            stateService.setSelectedTab(-1)
        }

        contentManager.addContentManagerListener(object : ContentManagerListener {
            override fun contentAdded(event: ContentManagerEvent) {
                logger.debug("ContentManagerListener: contentAdded - ${event.content.displayName}")
                // Logic for contentAdded was primarily for selection.
                // Tab additions themselves are handled by OpenBranchSelectionTabAction or initial load.
                // If a newly added tab is selected, selectionChanged should handle state update.
                val addedContent = event.content
                if (addedContent.isCloseable && contentManager.isSelected(addedContent)) {
                    val branchName = addedContent.displayName
                    logger.debug("A closable tab $branchName was added and selected.")
                    // This might be redundant if OpenBranchSelectionTabAction already set it.
                    // Or if initial tab setup handled it.
                    // But it acts as a catch-all for selection.
                    val closableTabsInState = stateService.state.openTabs.map { it.branchName }
                    val indexInPersistedList = closableTabsInState.indexOf(branchName)
                    if (indexInPersistedList != -1) {
                        if (stateService.state.selectedTabIndex != indexInPersistedList) {
                            logger.debug("Updating selected tab in state to $indexInPersistedList for $branchName due to contentAdded+selected.")
                            stateService.setSelectedTab(indexInPersistedList)
                        }
                    } else {
                        // This could happen if a tab is added by means other than our action, and it's not yet in state.
                        // For now, we assume tabs are added via our action or initial load.
                        logger.warn("Selected tab $branchName (from contentAdded) not found in state's openTabs.")
                    }
                }
            }

            override fun contentRemoved(event: ContentManagerEvent) {
                logger.debug("ContentManagerListener: contentRemoved - ${event.content.displayName}, isCloseable: ${event.content.isCloseable}")
                if (event.content.isCloseable) {
                    val branchName = event.content.displayName
                    logger.debug("Calling stateService.removeTab($branchName).")
                    stateService.removeTab(branchName)

                    if (contentManager.contentCount > 0 && contentManager.contents.none { it.isCloseable }) {
                        logger.debug("No closable tabs left. Selecting HEAD and setting state selectedTab to -1.")
                        contentManager.setSelectedContent(contentManager.getContent(0)!!, true)
                        stateService.setSelectedTab(-1)
                    } else if (contentManager.contentCount == 0) {
                        logger.warn("All tabs removed (should not happen if HEAD is pinned). Setting state selectedTab to -1.")
                        stateService.setSelectedTab(-1)
                    }
                    // If another closable tab is auto-selected, selectionChanged will handle updating the state.
                }
            }

            override fun selectionChanged(event: ContentManagerEvent) {
                val selectedContent = event.content
                logger.debug("ContentManagerListener: selectionChanged - new selection: ${selectedContent.displayName}, isCloseable: ${selectedContent.isCloseable}")
                if (selectedContent.isCloseable) {
                    val branchName = selectedContent.displayName
                    val closableTabsInState = stateService.state.openTabs.map { it.branchName }
                    val indexInPersistedList = closableTabsInState.indexOf(branchName)

                    if (indexInPersistedList != -1) {
                        logger.debug("Selected closable tab $branchName. Calling stateService.setSelectedTab($indexInPersistedList).")
                        stateService.setSelectedTab(indexInPersistedList)
                    } else {
                        logger.warn("Selected closable tab $branchName not found in stateService's openTabs. This might be an issue if it wasn't added correctly.")
                        // Potentially, if a tab is created and selected by some other means, it might not be in the state.
                        // For now, we only set selected if it's a known tab.
                    }
                } else {
                    logger.debug("Non-closable tab ${selectedContent.displayName} (likely HEAD) selected. Calling stateService.setSelectedTab(-1).")
                    stateService.setSelectedTab(-1)
                }
            }
        })
        logger.debug("ContentManagerListener added.")

        val openSelectionTabAction = OpenBranchSelectionTabAction(project, toolWindow, gitChangesUiProvider)
        toolWindow.setTitleActions(listOf(openSelectionTabAction))
        // Explicit version for diagnostics as requested
        val key: com.intellij.openapi.util.Key<com.github.uiopak.lstcrc.toolWindow.OpenBranchSelectionTabAction> = LstCrcKeys.OPEN_BRANCH_SELECTION_ACTION_KEY
        val action: com.github.uiopak.lstcrc.toolWindow.OpenBranchSelectionTabAction = openSelectionTabAction
        (toolWindow as com.intellij.openapi.util.UserDataHolder).putUserData(key, action)
        // toolWindow.putUserData(LstCrcKeys.OPEN_BRANCH_SELECTION_ACTION_KEY, openSelectionTabAction) // Original simpler call

        val propertiesComponent = PropertiesComponent.getInstance()
        val settingsProvider = ToolWindowSettingsProvider(propertiesComponent)
        val pluginSettingsSubMenu: ActionGroup = settingsProvider.createToolWindowSettingsGroup()

        val allGearActionsGroup = DefaultActionGroup()
        allGearActionsGroup.add(pluginSettingsSubMenu)
        toolWindow.setAdditionalGearActions(allGearActionsGroup)
        logger.debug("Additional gear actions set.")
        logger.info("createToolWindowContent finished.")
    }

    override fun shouldBeAvailable(project: Project) = true
}
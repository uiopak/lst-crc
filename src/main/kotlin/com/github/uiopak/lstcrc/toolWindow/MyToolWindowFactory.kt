package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.services.GitService
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.github.uiopak.lstcrc.state.ToolWindowState
import com.github.uiopak.lstcrc.utils.LstCrcKeys
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener

/**
 * The factory responsible for creating and setting up the LST-CRC tool window when the project opens.
 * It restores tabs from the persisted state, sets up the permanent "HEAD" tab, and registers listeners
 * to keep the UI and the persisted state synchronized.
 */
class MyToolWindowFactory : ToolWindowFactory {
    private val logger = thisLogger()

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        logger.info("createToolWindowContent called for project: ${project.name}")

        val properties = PropertiesComponent.getInstance()
        val showTitle = properties.getBoolean(
            ToolWindowSettingsProvider.APP_SHOW_TOOL_WINDOW_TITLE_KEY,
            ToolWindowSettingsProvider.DEFAULT_SHOW_TOOL_WINDOW_TITLE
        )
        toolWindow.component.putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, if (showTitle) null else "true")

        val stateService = ToolWindowStateService.getInstance(project)
        val contentManager = toolWindow.contentManager

        // Listen for state changes to update UI elements like tab aliases.
        project.messageBus.connect(toolWindow.disposable).subscribe(ToolWindowStateService.TOPIC,
            object : ToolWindowStateService.Companion.ToolWindowStateListener {
                override fun stateChanged(newState: ToolWindowState) {
                    ApplicationManager.getApplication().invokeLater {
                        if (project.isDisposed || toolWindow.isDisposed) return@invokeLater
                        newState.openTabs.forEach { tabInfo ->
                            val content = contentManager.contents.find { c -> c.getUserData(LstCrcKeys.BRANCH_NAME_KEY) == tabInfo.branchName }
                            content?.let {
                                val newDisplayName = tabInfo.alias ?: tabInfo.branchName
                                if (it.displayName != newDisplayName) {
                                    it.displayName = newDisplayName
                                }
                            }
                        }
                    }
                }
            })

        // Defer the main content population to ensure the tool window components are ready.
        // This fixes issues where the window is initialized while hidden.
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed || toolWindow.isDisposed) return@invokeLater

            val gitChangesUiProvider = GitChangesToolWindow(project, toolWindow.disposable)
            val contentFactory = ContentFactory.getInstance()
            val gitService = project.service<GitService>()

            // Create the permanent "HEAD" tab.
            val headView = gitChangesUiProvider.createBranchContentView("HEAD")
            val headContent = contentFactory.createContent(headView, LstCrcBundle.message("tab.name.head"), false).apply {
                isCloseable = false
                isPinned = true
            }
            contentManager.addContent(headContent)

            // Restore persisted tabs from the previous session.
            var selectedContentRestored = false
            val persistedState = stateService.state
            val currentRepository = gitService.getCurrentRepository()

            if (persistedState.openTabs.isNotEmpty()) {
                persistedState.openTabs.forEach { tabInfo ->
                    val branchView = gitChangesUiProvider.createBranchContentView(tabInfo.branchName)
                    val displayName = tabInfo.alias ?: tabInfo.branchName
                    val branchContent = contentFactory.createContent(branchView, displayName, false).apply {
                        isCloseable = true
                        putUserData(LstCrcKeys.BRANCH_NAME_KEY, tabInfo.branchName)
                    }
                    contentManager.addContent(branchContent)
                }
                if (persistedState.selectedTabIndex >= 0 && persistedState.selectedTabIndex < persistedState.openTabs.size) {
                    val selectedTabInfo = persistedState.openTabs[persistedState.selectedTabIndex]
                    val contentToSelect = contentManager.contents.find { it.getUserData(LstCrcKeys.BRANCH_NAME_KEY) == selectedTabInfo.branchName }
                    if (contentToSelect != null) {
                        contentManager.setSelectedContent(contentToSelect, true)
                        selectedContentRestored = true
                    }
                }
            } else {
                // On first launch with no persisted state, add a tab for the current Git branch.
                val currentActualBranchName = currentRepository?.currentBranchName
                if (currentActualBranchName != null) {
                    val initialBranchView = gitChangesUiProvider.createBranchContentView(currentActualBranchName)
                    val initialBranchContent = contentFactory.createContent(initialBranchView, currentActualBranchName, false).apply {
                        isCloseable = true
                        putUserData(LstCrcKeys.BRANCH_NAME_KEY, currentActualBranchName)
                    }
                    contentManager.addContent(initialBranchContent)
                    contentManager.setSelectedContent(initialBranchContent, true)
                    selectedContentRestored = true
                    stateService.addTab(currentActualBranchName)
                    val newTabIndexInState = stateService.state.openTabs.indexOfFirst { it.branchName == currentActualBranchName }
                    if (newTabIndexInState != -1) {
                        stateService.setSelectedTab(newTabIndexInState)
                    }
                }
            }

            // If no other tab was restored as selected, default to the HEAD tab.
            if (!selectedContentRestored) {
                contentManager.setSelectedContent(headContent, true)
                stateService.setSelectedTab(-1)
            }
        }

        // Keep the persisted state in sync with UI actions.
        contentManager.addContentManagerListener(object : ContentManagerListener {
            override fun contentRemoved(event: ContentManagerEvent) {
                val branchName = event.content.getUserData(LstCrcKeys.BRANCH_NAME_KEY)
                if (branchName != null) {
                    stateService.removeTab(branchName)
                    if (contentManager.contentCount > 0 && contentManager.selectedContent?.isCloseable == false) {
                        stateService.setSelectedTab(-1)
                    }
                }
            }

            override fun selectionChanged(event: ContentManagerEvent) {
                ApplicationManager.getApplication().invokeLater {
                    // Do not check for isVisible here. The state must always be in sync
                    // with the UI selection, even if the window is hidden.
                    if (project.isDisposed || toolWindow.isDisposed) {
                        return@invokeLater
                    }
                    val selectedContent = toolWindow.contentManager.selectedContent ?: return@invokeLater

                    val branchName = selectedContent.getUserData(LstCrcKeys.BRANCH_NAME_KEY)
                    if (branchName != null) {
                        val closableTabsInState = stateService.state.openTabs
                        val indexInPersistedList = closableTabsInState.indexOfFirst { it.branchName == branchName }
                        if (indexInPersistedList != -1) {
                            stateService.setSelectedTab(indexInPersistedList)
                        }
                    } else { // This branch is taken for the HEAD tab
                        stateService.setSelectedTab(-1)
                    }
                }
            }
        })

        val openSelectionTabAction = OpenBranchSelectionTabAction(project, toolWindow)
        toolWindow.setTitleActions(listOf(openSelectionTabAction))

        val pluginSettingsSubMenu: ActionGroup = ToolWindowSettingsProvider.createToolWindowSettingsGroup()

        val allGearActionsGroup = DefaultActionGroup()
        allGearActionsGroup.add(pluginSettingsSubMenu)
        toolWindow.setAdditionalGearActions(allGearActionsGroup)

        logger.info("createToolWindowContent finished.")
    }

    override fun shouldBeAvailable(project: Project) = true
}
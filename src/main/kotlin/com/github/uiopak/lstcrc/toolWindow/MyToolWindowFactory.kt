package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.services.GitService
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.github.uiopak.lstcrc.state.ToolWindowState
import com.github.uiopak.lstcrc.utils.LstCrcKeys
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.util.ui.tree.TreeUtil

class MyToolWindowFactory : ToolWindowFactory {
    private val logger = thisLogger()

    private fun getActiveChangesTreePanel(toolWindow: ToolWindow): ChangesTreePanel? {
        return toolWindow.contentManager.selectedContent?.component as? ChangesTreePanel
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // --- Standard Initialization ---
        try {
            project.getService(com.github.uiopak.lstcrc.services.VfsListenerService::class.java)?.let {
                logger.info("VfsListenerService instance successfully retrieved during tool window creation.")
            } ?: logger.warn("VfsListenerService is null after attempting to retrieve it. VFS-based updates might not function as expected.")
        } catch (e: Throwable) {
            logger.error("EXCEPTION while trying to initialize or retrieve VfsListenerService. VFS updates likely impacted.", e)
        }

        try {
            // By getting the service instance, we trigger its `init` block which sets up the necessary listeners for gutter markers.
            project.service<com.github.uiopak.lstcrc.services.LstCrcGutterTrackerService>().let {
                logger.info("LstCrcGutterTrackerService instance successfully retrieved/initialized during tool window creation.")
            }
        } catch (e: Throwable) {
            logger.error("EXCEPTION while trying to initialize or retrieve LstCrcGutterTrackerService. Gutter markers will not work.", e)
        }

        logger.info("createToolWindowContent called for project: ${project.name}")
        val gitChangesUiProvider = GitChangesToolWindow(project, toolWindow.disposable)
        val contentFactory = ContentFactory.getInstance()
        val gitService = project.service<GitService>()
        val stateService = ToolWindowStateService.getInstance(project)
        val contentManager = toolWindow.contentManager

        // --- State/UI Sync Logic ---
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

        // --- Content Creation ---
        val currentRepository = gitService.getCurrentRepository()
        val headTabTargetName = currentRepository?.currentBranchName ?: currentRepository?.currentRevision ?: "HEAD"

        val headView = gitChangesUiProvider.createBranchContentView("HEAD")
        val headContent = contentFactory.createContent(headView, "HEAD", false).apply {
            isCloseable = false
            isPinned = true
        }
        contentManager.addContent(headContent)

        var selectedContentRestored = false
        val persistedState = stateService.state
        if (persistedState.openTabs.isNotEmpty()) {
            persistedState.openTabs.forEach { tabInfo ->
                if (tabInfo.branchName != "HEAD" && tabInfo.branchName != headTabTargetName) {
                    val branchView = gitChangesUiProvider.createBranchContentView(tabInfo.branchName)
                    val displayName = tabInfo.alias ?: tabInfo.branchName
                    val branchContent = contentFactory.createContent(branchView, displayName, false).apply {
                        isCloseable = true
                        putUserData(LstCrcKeys.BRANCH_NAME_KEY, tabInfo.branchName)
                    }
                    contentManager.addContent(branchContent)
                }
            }
            if (persistedState.selectedTabIndex >= 0 && persistedState.selectedTabIndex < persistedState.openTabs.size) {
                val selectedTabInfo = persistedState.openTabs[persistedState.selectedTabIndex]
                val branchNameToFind = selectedTabInfo.branchName
                val contentToSelect = contentManager.contents.find { it.getUserData(LstCrcKeys.BRANCH_NAME_KEY) == branchNameToFind }
                if (contentToSelect != null) {
                    contentManager.setSelectedContent(contentToSelect, true)
                    selectedContentRestored = true
                }
            }
        } else {
            val currentActualBranchName = currentRepository?.currentBranchName
            if (currentActualBranchName != null && currentActualBranchName != "HEAD" && currentActualBranchName != headTabTargetName) {
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

        if (!selectedContentRestored) {
            contentManager.setSelectedContent(headContent, true)
            stateService.setSelectedTab(-1)
        }

        // --- Content Listeners ---
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
                    if (project.isDisposed || toolWindow.isDisposed || !toolWindow.isVisible) {
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

        // --- Tool Window Header Actions ---
        val openSelectionTabAction = OpenBranchSelectionTabAction(project, toolWindow, gitChangesUiProvider)
        val expandAllAction = object : DumbAwareAction("Expand All", "Expand all nodes in the tree", AllIcons.Actions.Expandall) {
            override fun actionPerformed(e: AnActionEvent) {
                getActiveChangesTreePanel(toolWindow)?.tree?.let { TreeUtil.expandAll(it) }
            }
            override fun update(e: AnActionEvent) { e.presentation.isEnabled = getActiveChangesTreePanel(toolWindow) != null }
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        }
        val collapseAllAction = object : DumbAwareAction("Collapse All", "Collapse all nodes in the tree", AllIcons.Actions.Collapseall) {
            override fun actionPerformed(e: AnActionEvent) {
                getActiveChangesTreePanel(toolWindow)?.tree?.let { TreeUtil.collapseAll(it, 1) }
            }
            override fun update(e: AnActionEvent) { e.presentation.isEnabled = getActiveChangesTreePanel(toolWindow) != null }
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        }
        toolWindow.setTitleActions(listOf(openSelectionTabAction, expandAllAction, collapseAllAction))

        // --- Tool Window Gear (Options) Menu ---
        val settingsProvider = ToolWindowSettingsProvider(project)
        val pluginSettingsSubMenu: ActionGroup = settingsProvider.createToolWindowSettingsGroup()

        val allGearActionsGroup = DefaultActionGroup()
        allGearActionsGroup.add(pluginSettingsSubMenu)
        toolWindow.setAdditionalGearActions(allGearActionsGroup)

        logger.info("createToolWindowContent finished.")
    }

    override fun shouldBeAvailable(project: Project) = true
}
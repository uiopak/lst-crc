package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.messaging.TOOL_WINDOW_STATE_TOPIC
import com.github.uiopak.lstcrc.messaging.ToolWindowStateListener
import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.services.GitService
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.github.uiopak.lstcrc.state.ToolWindowState
import com.github.uiopak.lstcrc.state.displayName
import com.github.uiopak.lstcrc.utils.LstCrcKeys
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
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
        logger.info("createToolWindowContent called for project: ${project.name}.")

        val stateService = project.service<ToolWindowStateService>()
        val persistedState = stateService.state

        ApplicationManager.getApplication().executeOnPooledThread {
            val currentActualBranchName = resolveInitialBranchName(project, persistedState)

            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed || toolWindow.isDisposed) {
                    return@invokeLater
                }

                initializeToolWindowContent(
                    project,
                    toolWindow,
                    stateService,
                    persistedState,
                    currentActualBranchName
                )
            }
        }
    }

    private fun resolveInitialBranchName(project: Project, persistedState: ToolWindowState): String? {
        if (persistedState.openTabs.isNotEmpty()) {
            return null
        }
        return project.service<GitService>().getPrimaryRepository()?.currentBranchName
    }

    private fun initializeToolWindowContent(
        project: Project,
        toolWindow: ToolWindow,
        stateService: ToolWindowStateService,
        persistedState: ToolWindowState,
        currentActualBranchName: String?
    ) {
        val contentManager = toolWindow.contentManager

        applyToolWindowTitleSetting(toolWindow)
        subscribeToStateChanges(project, toolWindow)
        val headContent = createHeadTab(project, toolWindow)
        val selectedContentRestored = restoreOrCreateInitialTabs(
            project, toolWindow, persistedState, currentActualBranchName
        )
        if (!selectedContentRestored) {
            selectHeadFallback(contentManager, headContent, stateService)
        }
        registerContentManagerListener(project, toolWindow, stateService)
        setupToolWindowActions(project, toolWindow)

        logger.info("Tool window UI setup complete.")
    }

    private fun applyToolWindowTitleSetting(toolWindow: ToolWindow) {
        val showTitle = ToolWindowSettingsProvider.isShowToolWindowTitleEnabled()
        ToolWindowUiCompatibility.setToolWindowTitleVisible(toolWindow, showTitle)
    }

    private fun selectHeadFallback(
        contentManager: ContentManager,
        headContent: Content,
        stateService: ToolWindowStateService
    ) {
        contentManager.setSelectedContent(headContent, true)
        stateService.setSelectedTab(-1)
    }

    private fun subscribeToStateChanges(project: Project, toolWindow: ToolWindow) {
        val contentManager = toolWindow.contentManager
        project.messageBus.connect(toolWindow.disposable).subscribe(TOOL_WINDOW_STATE_TOPIC,
            object : ToolWindowStateListener {
                override fun stateChanged(newState: ToolWindowState) {
                    ApplicationManager.getApplication().invokeLater {
                        if (project.isDisposed || toolWindow.isDisposed) return@invokeLater
                        syncTabDisplayNames(contentManager, newState)
                    }
                }
            })
    }

    private fun syncTabDisplayNames(contentManager: ContentManager, newState: ToolWindowState) {
        newState.openTabs.forEach { tabInfo ->
            val content = ToolWindowHelper.findContentByBranchName(contentManager, tabInfo.branchName)
            content?.let {
                val newDisplayName = tabInfo.displayName
                if (it.displayName != newDisplayName) {
                    it.displayName = newDisplayName
                }
            }
        }
    }

    private fun createHeadTab(project: Project, toolWindow: ToolWindow): Content {
        val contentFactory = ContentFactory.getInstance()
        val headDisposable = Disposer.newDisposable("LST-CRC HEAD tab")
        Disposer.register(toolWindow.disposable, headDisposable)
        val headView = LstCrcChangesBrowser(project, "HEAD", headDisposable)
        val headContent = contentFactory.createContent(headView, LstCrcBundle.message("tab.name.head"), false).apply {
            isCloseable = false
            isPinned = true
            setDisposer(headDisposable)
        }
        toolWindow.contentManager.addContent(headContent)
        return headContent
    }

    /**
     * Restores persisted tabs or creates an initial tab from the current branch.
     * @return true if a non-HEAD tab was selected.
     */
    private fun restoreOrCreateInitialTabs(
        project: Project,
        toolWindow: ToolWindow,
        persistedState: ToolWindowState,
        currentActualBranchName: String?
    ): Boolean {
        if (persistedState.openTabs.isNotEmpty()) {
            return restorePersistedTabs(project, toolWindow, persistedState)
        }

        if (currentActualBranchName != null) {
            ToolWindowHelper.createAndSelectTab(project, toolWindow, currentActualBranchName)
            return true
        }
        return false
    }

    private fun restorePersistedTabs(project: Project, toolWindow: ToolWindow, persistedState: ToolWindowState): Boolean {
        val contentManager = toolWindow.contentManager
        persistedState.openTabs.forEach { tabInfo ->
            ToolWindowHelper.createBranchContent(project, tabInfo.branchName, tabInfo.displayName, contentManager)
        }
        if (persistedState.selectedTabIndex >= 0 && persistedState.selectedTabIndex < persistedState.openTabs.size) {
            val selectedTabInfo = persistedState.openTabs[persistedState.selectedTabIndex]
            val contentToSelect = ToolWindowHelper.findContentByBranchName(contentManager, selectedTabInfo.branchName)
            if (contentToSelect != null) {
                contentManager.setSelectedContent(contentToSelect, true)
                return true
            }
        }
        return false
    }

    private fun registerContentManagerListener(
        project: Project,
        toolWindow: ToolWindow,
        stateService: ToolWindowStateService
    ) {
        toolWindow.contentManager.addContentManagerListener(object : ContentManagerListener {
            override fun contentRemoved(event: ContentManagerEvent) {
                val branchName = contentBranchName(event.content)
                if (branchName != null) {
                    stateService.removeTab(branchName)
                }
            }

            override fun selectionChanged(event: ContentManagerEvent) {
                if (project.isDisposed || toolWindow.isDisposed) return
                syncSelectedTabFromContent(toolWindow, stateService)
            }
        })
    }

    private fun syncSelectedTabFromContent(toolWindow: ToolWindow, stateService: ToolWindowStateService) {
        val selectedContent = toolWindow.contentManager.selectedContent ?: return
        val branchName = contentBranchName(selectedContent)
        if (branchName != null) {
            val indexInPersistedList = stateService.findTabIndex(branchName)
            if (indexInPersistedList != -1) {
                stateService.setSelectedTab(indexInPersistedList)
            }
            return
        }
        stateService.setSelectedTab(-1)
    }

    private fun contentBranchName(content: Content): String? {
        return content.getUserData(LstCrcKeys.BRANCH_NAME_KEY)
    }

    private fun setupToolWindowActions(project: Project, toolWindow: ToolWindow) {
        val openSelectionTabAction = OpenBranchSelectionTabAction(project, toolWindow)
        ToolWindowUiCompatibility.setTabActions(toolWindow, openSelectionTabAction)
        toolWindow.setAdditionalGearActions(createGearActionsGroup())
    }

    private fun createGearActionsGroup(): ActionGroup {
        val pluginSettingsSubMenu: ActionGroup = ToolWindowSettingsProvider.createToolWindowSettingsGroup()
        return DefaultActionGroup().apply {
            add(pluginSettingsSubMenu)
        }
    }

    override fun shouldBeAvailable(project: Project) = true

    override suspend fun isApplicableAsync(project: Project): Boolean = true
}
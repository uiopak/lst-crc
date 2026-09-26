package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.messaging.TOOL_WINDOW_STATE_TOPIC
import com.github.uiopak.lstcrc.messaging.ToolWindowStateListener
import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.services.GitService
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.github.uiopak.lstcrc.state.ToolWindowState
import com.github.uiopak.lstcrc.state.displayName
import com.github.uiopak.lstcrc.utils.LstCrcKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.Content
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
        logger.debug { "createToolWindowContent called for project: ${project.name}." }

        val stateService = project.service<ToolWindowStateService>()
        val persistedState = stateService.state

        ApplicationManager.getApplication().executeOnPooledThread {
            // Without persisted tabs, the first start opens a tab for the current branch.
            val currentActualBranchName = if (persistedState.openTabs.isEmpty()) {
                project.service<GitService>().getPrimaryRepository()?.currentBranchName
            } else {
                null
            }

            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed || toolWindow.isDisposed) return@invokeLater
                initializeToolWindowContent(project, toolWindow, stateService, persistedState, currentActualBranchName)
            }
        }
    }

    private fun initializeToolWindowContent(
        project: Project,
        toolWindow: ToolWindow,
        stateService: ToolWindowStateService,
        persistedState: ToolWindowState,
        currentActualBranchName: String?
    ) {
        val contentManager = toolWindow.contentManager

        ToolWindowUiCompatibility.setToolWindowTitleVisible(toolWindow, ToolWindowSettingsProvider.isShowToolWindowTitleEnabled())
        subscribeToStateChanges(project, toolWindow)
        val headContent = createHeadTab(project, toolWindow)
        if (!restoreOrCreateInitialTabs(project, toolWindow, persistedState, currentActualBranchName)) {
            contentManager.setSelectedContent(headContent, true)
            stateService.setSelectedTab(-1)
        }
        registerContentManagerListener(project, toolWindow, stateService)
        ToolWindowUiCompatibility.setTabActions(toolWindow, OpenBranchSelectionTabAction(project, toolWindow))
        toolWindow.setAdditionalGearActions(DefaultActionGroup(ToolWindowSettingsProvider.createToolWindowSettingsGroup()))

        logger.debug { "Tool window UI setup complete." }
    }

    /** Keeps tab titles in sync with renamed aliases. */
    private fun subscribeToStateChanges(project: Project, toolWindow: ToolWindow) {
        val contentManager = toolWindow.contentManager
        project.messageBus.connect(toolWindow.disposable).subscribe(TOOL_WINDOW_STATE_TOPIC,
            object : ToolWindowStateListener {
                override fun stateChanged(newState: ToolWindowState) {
                    ApplicationManager.getApplication().invokeLater {
                        if (project.isDisposed || toolWindow.isDisposed) return@invokeLater
                        newState.openTabs.forEach { tabInfo ->
                            val content = ToolWindowHelper.findContentByBranchName(contentManager, tabInfo.branchName)
                            if (content != null && content.displayName != tabInfo.displayName) {
                                content.displayName = tabInfo.displayName
                            }
                        }
                    }
                }
            })
    }

    private fun createHeadTab(project: Project, toolWindow: ToolWindow): Content {
        val contentFactory = ContentFactory.getInstance()
        val headDisposable = Disposer.newDisposable("LST-CRC HEAD tab")
        Disposer.register(toolWindow.disposable, headDisposable)
        val headView = LstCrcChangesBrowser(project, "HEAD", headDisposable)
        val headContent = contentFactory.createContent(headView, LstCrcBundle.message("tab.name.head"), false).apply {
            isCloseable = false
            isPinned = true
            @Suppress("UsePropertyAccessSyntax") // Content.disposer is a val; the setter is the only API
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
        val contentManager = toolWindow.contentManager
        if (persistedState.openTabs.isEmpty()) {
            currentActualBranchName ?: return false
            ToolWindowHelper.createAndSelectTab(project, toolWindow, currentActualBranchName)
            return true
        }

        persistedState.openTabs.forEach { tabInfo ->
            ToolWindowHelper.createBranchContent(project, tabInfo.branchName, tabInfo.displayName, contentManager)
        }
        val selectedTabInfo = persistedState.openTabs.getOrNull(persistedState.selectedTabIndex) ?: return false
        val contentToSelect = ToolWindowHelper.findContentByBranchName(contentManager, selectedTabInfo.branchName) ?: return false
        contentManager.setSelectedContent(contentToSelect, true)
        return true
    }

    private fun registerContentManagerListener(
        project: Project,
        toolWindow: ToolWindow,
        stateService: ToolWindowStateService
    ) {
        toolWindow.contentManager.addContentManagerListener(object : ContentManagerListener {
            override fun contentRemoved(event: ContentManagerEvent) {
                event.content.getUserData(LstCrcKeys.BRANCH_NAME_KEY)?.let(stateService::removeTab)
            }

            override fun selectionChanged(event: ContentManagerEvent) {
                if (project.isDisposed || toolWindow.isDisposed) return
                val selectedContent = toolWindow.contentManager.selectedContent ?: return
                // HEAD has no branch name. A comparison tab not yet added to the state is registered by its creator.
                val branchName = selectedContent.getUserData(LstCrcKeys.BRANCH_NAME_KEY)
                val index = if (branchName == null) -1 else stateService.findTabIndex(branchName).takeIf { it != -1 } ?: return
                stateService.setSelectedTab(index)
            }
        })
    }

    override fun shouldBeAvailable(project: Project) = true

    override suspend fun isApplicableAsync(project: Project): Boolean = true
}
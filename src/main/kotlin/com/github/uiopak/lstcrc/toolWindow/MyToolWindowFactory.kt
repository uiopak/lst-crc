@file:Suppress("DialogTitleCapitalization")

package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.messaging.TOOL_WINDOW_STATE_TOPIC
import com.github.uiopak.lstcrc.messaging.ToolWindowStateListener
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
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowEx
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
        logger.info("createToolWindowContent called for project: ${project.name}. Scheduling data fetch and UI setup.")

        object : Task.Backgroundable(project, LstCrcBundle.message("git.task.initializing"), false) {
            lateinit var persistedState: ToolWindowState
            var currentActualBranchName: String? = null

            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Fetching Git repository info..."
                val stateService = project.service<ToolWindowStateService>()
                persistedState = stateService.state

                if (persistedState.openTabs.isEmpty()) {
                    val gitService = project.service<GitService>()
                    currentActualBranchName = gitService.getPrimaryRepository()?.currentBranchName
                }
            }

            override fun onSuccess() {
                if (project.isDisposed || toolWindow.isDisposed) {
                    logger.info("Project or tool window disposed before UI setup.")
                    return
                }
                logger.info("Setting up tool window UI on EDT.")

                val stateService = project.service<ToolWindowStateService>()
                val contentManager = toolWindow.contentManager

                applyToolWindowTitleSetting(toolWindow)
                subscribeToStateChanges(project, toolWindow)
                val headContent = createHeadTab(project, toolWindow)
                val selectedContentRestored = restoreOrCreateInitialTabs(
                    project, toolWindow, stateService, persistedState, currentActualBranchName
                )
                if (!selectedContentRestored) {
                    contentManager.setSelectedContent(headContent, true)
                    stateService.setSelectedTab(-1)
                }
                registerContentManagerListener(project, toolWindow, stateService)
                setupToolWindowActions(project, toolWindow)

                logger.info("Tool window UI setup complete.")
            }

            override fun onThrowable(error: Throwable) {
                logger.error("Failed to initialize tool window content.", error)
            }
        }.queue()
    }

    private fun applyToolWindowTitleSetting(toolWindow: ToolWindow) {
        val showTitle = PropertiesComponent.getInstance().getBoolean(
            ToolWindowSettingsProvider.APP_SHOW_TOOL_WINDOW_TITLE_KEY,
            ToolWindowSettingsProvider.DEFAULT_SHOW_TOOL_WINDOW_TITLE
        )
        toolWindow.component.putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, if (showTitle) null else "true")
    }

    private fun subscribeToStateChanges(project: Project, toolWindow: ToolWindow) {
        val contentManager = toolWindow.contentManager
        project.messageBus.connect(toolWindow.disposable).subscribe(TOOL_WINDOW_STATE_TOPIC,
            object : ToolWindowStateListener {
                override fun stateChanged(newState: ToolWindowState) {
                    ApplicationManager.getApplication().invokeLater {
                        if (project.isDisposed || toolWindow.isDisposed) return@invokeLater
                        newState.openTabs.forEach { tabInfo ->
                            val content = contentManager.contents.find { c ->
                                c.getUserData(LstCrcKeys.BRANCH_NAME_KEY) == tabInfo.branchName
                            }
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
    }

    private fun createHeadTab(project: Project, toolWindow: ToolWindow): com.intellij.ui.content.Content {
        val contentFactory = ContentFactory.getInstance()
        val headView = LstCrcChangesBrowser(project, "HEAD", toolWindow.disposable)
        val headContent = contentFactory.createContent(headView, LstCrcBundle.message("tab.name.head"), false).apply {
            isCloseable = false
            isPinned = true
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
        stateService: ToolWindowStateService,
        persistedState: ToolWindowState,
        currentActualBranchName: String?
    ): Boolean {
        val contentManager = toolWindow.contentManager

        if (persistedState.openTabs.isNotEmpty()) {
            persistedState.openTabs.forEach { tabInfo ->
                val displayName = tabInfo.alias ?: tabInfo.branchName
                ToolWindowHelper.createBranchContent(project, toolWindow, tabInfo.branchName, displayName, contentManager)
            }
            if (persistedState.selectedTabIndex >= 0 && persistedState.selectedTabIndex < persistedState.openTabs.size) {
                val selectedTabInfo = persistedState.openTabs[persistedState.selectedTabIndex]
                val contentToSelect = contentManager.contents.find {
                    it.getUserData(LstCrcKeys.BRANCH_NAME_KEY) == selectedTabInfo.branchName
                }
                if (contentToSelect != null) {
                    contentManager.setSelectedContent(contentToSelect, true)
                    return true
                }
            }
        } else {
            val branchNameToCreate = currentActualBranchName
            if (branchNameToCreate != null) {
                val initialBranchContent = ToolWindowHelper.createBranchContent(
                    project, toolWindow, branchNameToCreate, branchNameToCreate, contentManager
                )
                contentManager.setSelectedContent(initialBranchContent, true)
                stateService.addTab(branchNameToCreate)
                val newTabIndexInState = stateService.state.openTabs.indexOfFirst { it.branchName == branchNameToCreate }
                if (newTabIndexInState != -1) {
                    stateService.setSelectedTab(newTabIndexInState)
                }
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
                val branchName = event.content.getUserData(LstCrcKeys.BRANCH_NAME_KEY)
                if (branchName != null) {
                    stateService.removeTab(branchName)
                }
            }

            override fun selectionChanged(event: ContentManagerEvent) {
                if (project.isDisposed || toolWindow.isDisposed) return
                val selectedContent = toolWindow.contentManager.selectedContent ?: return

                val branchName = selectedContent.getUserData(LstCrcKeys.BRANCH_NAME_KEY)
                if (branchName != null) {
                    val indexInPersistedList = stateService.state.openTabs.indexOfFirst { it.branchName == branchName }
                    if (indexInPersistedList != -1) {
                        stateService.setSelectedTab(indexInPersistedList)
                    }
                } else {
                    stateService.setSelectedTab(-1)
                }
            }
        })
    }

    private fun setupToolWindowActions(project: Project, toolWindow: ToolWindow) {
        val openSelectionTabAction = OpenBranchSelectionTabAction(project, toolWindow)
        (toolWindow as? ToolWindowEx)?.setTabActions(openSelectionTabAction)

        val pluginSettingsSubMenu: ActionGroup = ToolWindowSettingsProvider.createToolWindowSettingsGroup()
        val allGearActionsGroup = DefaultActionGroup()
        allGearActionsGroup.add(pluginSettingsSubMenu)
        toolWindow.setAdditionalGearActions(allGearActionsGroup)
    }

    override fun shouldBeAvailable(project: Project) = true

    override suspend fun isApplicableAsync(project: Project): Boolean = true
}
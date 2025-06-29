package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.services.GitService
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.github.uiopak.lstcrc.state.ToolWindowState
import com.github.uiopak.lstcrc.utils.LstCrcKeys
import com.intellij.icons.AllIcons
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
            // Data to be fetched on BGT
            lateinit var persistedState: ToolWindowState
            var currentActualBranchName: String? = null

            override fun run(indicator: ProgressIndicator) {
                // This runs on a BGT, safe to call slow methods
                indicator.text = "Fetching Git repository info..."
                val stateService = project.service<ToolWindowStateService>()
                persistedState = stateService.state

                // Only need to fetch current branch if there are no persisted tabs,
                // which is a potentially slow operation.
                if (persistedState.openTabs.isEmpty()) {
                    val gitService = project.service<GitService>()
                    val currentRepository = gitService.getPrimaryRepository()
                    currentActualBranchName = currentRepository?.currentBranchName
                }
            }

            override fun onSuccess() {
                // This runs on the EDT, safe to do UI work
                if (project.isDisposed || toolWindow.isDisposed) {
                    logger.info("Project or tool window disposed before UI setup.")
                    return
                }

                logger.info("Setting up tool window UI on EDT.")

                val stateService = project.service<ToolWindowStateService>()
                val contentManager = toolWindow.contentManager

                // Programmatically set the icon to use a standard IDE icon.
                toolWindow.setIcon(AllIcons.Actions.ListChanges)

                val properties = PropertiesComponent.getInstance()
                val showTitle = properties.getBoolean(
                    ToolWindowSettingsProvider.APP_SHOW_TOOL_WINDOW_TITLE_KEY,
                    ToolWindowSettingsProvider.DEFAULT_SHOW_TOOL_WINDOW_TITLE
                )
                toolWindow.component.putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, if (showTitle) null else "true")

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

                val gitChangesUiProvider = GitChangesToolWindow(project, toolWindow.disposable)
                val contentFactory = ContentFactory.getInstance()

                // Create the permanent "HEAD" tab.
                val headView = gitChangesUiProvider.createBranchContentView("HEAD")
                val headContent = contentFactory.createContent(headView, LstCrcBundle.message("tab.name.head"), false).apply {
                    isCloseable = false
                    isPinned = true
                }
                contentManager.addContent(headContent)

                // Restore persisted tabs from the previous session.
                var selectedContentRestored = false
                if (persistedState.openTabs.isNotEmpty()) {
                    persistedState.openTabs.forEach { tabInfo ->
                        val displayName = tabInfo.alias ?: tabInfo.branchName
                        ToolWindowHelper.createBranchContent(project, toolWindow, tabInfo.branchName, displayName, contentManager)
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
                    // On first launch, use the branch name fetched in the background.
                    // Capture the mutable property in a local, immutable variable for safe smart casting.
                    val branchNameToCreate = currentActualBranchName
                    if (branchNameToCreate != null) {
                        val initialBranchContent = ToolWindowHelper.createBranchContent(project, toolWindow, branchNameToCreate, branchNameToCreate, contentManager)
                        contentManager.setSelectedContent(initialBranchContent, true)
                        selectedContentRestored = true
                        stateService.addTab(branchNameToCreate)
                        val newTabIndexInState = stateService.state.openTabs.indexOfFirst { it.branchName == branchNameToCreate }
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

                // Keep the persisted state in sync with UI actions.
                contentManager.addContentManagerListener(object : ContentManagerListener {
                    override fun contentRemoved(event: ContentManagerEvent) {
                        // The only job here is to update the state list.
                        // The selectionChanged event will handle updating the selected index and triggering the data refresh.
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
                            val closableTabsInState = stateService.state.openTabs
                            val indexInPersistedList = closableTabsInState.indexOfFirst { it.branchName == branchName }
                            if (indexInPersistedList != -1) {
                                stateService.setSelectedTab(indexInPersistedList)
                            }
                        } else { // This branch is taken for the HEAD tab
                            stateService.setSelectedTab(-1)
                        }
                    }
                })

                // Use setTabActions to place the '+' button directly next to the tabs
                val openSelectionTabAction = OpenBranchSelectionTabAction(project, toolWindow)
                (toolWindow as? ToolWindowEx)?.setTabActions(openSelectionTabAction)

                val pluginSettingsSubMenu: ActionGroup = ToolWindowSettingsProvider.createToolWindowSettingsGroup()
                val allGearActionsGroup = DefaultActionGroup()
                allGearActionsGroup.add(pluginSettingsSubMenu)
                toolWindow.setAdditionalGearActions(allGearActionsGroup)

                logger.info("Tool window UI setup complete.")
            }

            override fun onThrowable(error: Throwable) {
                logger.error("Failed to initialize tool window content.", error)
            }
        }.queue()
    }

    override fun shouldBeAvailable(project: Project) = true
}
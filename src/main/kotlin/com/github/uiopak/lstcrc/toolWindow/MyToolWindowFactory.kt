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
import com.intellij.ui.content.Content
import java.awt.Color
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.UIManager
import com.intellij.ui.tabs.impl.TabLabel // Attempting to use TabLabel
import com.intellij.util.ui.UIUtil

class MyToolWindowFactory : ToolWindowFactory {
    private val logger = thisLogger() // Initialize logger

    companion object {
        // Keys for tab coloring settings (copied from ToolWindowSettingsProvider)
        private const val TAB_COLORING_ENABLED_KEY = "com.github.uiopak.lstcrc.app.tabColoringEnabled"
        private const val TAB_COLORING_STYLE_KEY = "com.github.uiopak.lstcrc.app.tabColoringStyle"
        private const val TAB_COLORING_COLOR_KEY = "com.github.uiopak.lstcrc.app.tabColoringColor"

        private const val DEFAULT_TAB_COLORING_ENABLED = true
        private const val DEFAULT_TAB_COLORING_STYLE = "BACKGROUND"
        private const val DEFAULT_TAB_COLORING_COLOR = "Default"
    }

    // Simple data class to hold tab coloring settings
    data class TabColoringSettings(
        val enabled: Boolean,
        val style: String,
        val color: String
    )

    private fun getTabColoringSettings(propertiesComponent: PropertiesComponent): TabColoringSettings {
        val enabled = propertiesComponent.getBoolean(TAB_COLORING_ENABLED_KEY, DEFAULT_TAB_COLORING_ENABLED)
        val style = propertiesComponent.getValue(TAB_COLORING_STYLE_KEY, DEFAULT_TAB_COLORING_STYLE)
        val color = propertiesComponent.getValue(TAB_COLORING_COLOR_KEY, DEFAULT_TAB_COLORING_COLOR)
        return TabColoringSettings(enabled, style, color)
    }

    private fun applyTabStyling(content: Content, settings: TabColoringSettings, project: Project, toolWindow: ToolWindow) {
        logger.debug("Applying tab styling for ${content.displayName}. Settings: $settings")

        val targetComponent: JComponent? = content.component

        if (targetComponent == null) {
            logger.warn("Target component for content ${content.displayName} is null. Cannot apply styling.")
            return
        }

        // Attempt to find the TabLabel
        var tabLabel: TabLabel? = null
        val contentManager = toolWindow.contentManager
        val toolWindowComponent = toolWindow.component
        UIUtil.findComponentsOfType(toolWindowComponent, TabLabel::class.java).forEach { label ->
            if (label.content?.displayName == content.displayName || (label.content == content)) { // Check content equality directly too
                tabLabel = label
                return@forEach
            }
        }


        val newColor = when (settings.color) {
            "Red" -> Color.RED
            "Green" -> Color.GREEN
            "Blue" -> Color.BLUE
            "Yellow" -> Color.YELLOW
            "Default" -> null // Will be handled to reset to default
            else -> {
                try {
                    Color.decode(settings.color) // For hex codes
                } catch (e: NumberFormatException) {
                    logger.warn("Invalid color string: ${settings.color}. Falling back to default.")
                    null // Fallback for invalid custom color string
                }
            }
        }

        val componentToStyle = tabLabel ?: targetComponent // Fallback to content.component if TabLabel not found
        logger.debug("Component to style: ${componentToStyle.javaClass.name}")


        // Reset styles first
        componentToStyle.background = if (componentToStyle is TabLabel) null else UIManager.getColor("Panel.background") // TabLabel might handle null differently
        componentToStyle.border = if (componentToStyle is TabLabel) null else UIManager.getBorder("Component.border") // Or specific tab border
        if (componentToStyle is JComponent && componentToStyle.isOpaque) { // Check if it's JComponent first
             if (componentToStyle !is TabLabel) componentToStyle.isOpaque = false // Avoid making TabLabel non-opaque unless specifically styling background
        }


        if (!settings.enabled) {
            logger.debug("Tab coloring disabled. Resetting styles for ${content.displayName}.")
            // Styles already reset above
             if (tabLabel != null) {
                // For TabLabel, null background and border should revert to default look and feel
                tabLabel?.background = null
                tabLabel?.isOpaque = false // Important for default tab look
                tabLabel?.border = null
            } else {
                // For content.component, set to typical UI defaults
                targetComponent.background = UIManager.getColor("Panel.background")
                targetComponent.border = UIManager.getBorder("Component.border") // Or a more specific default
                targetComponent.isOpaque = false // Or true depending on typical component behavior
            }
        } else {
            logger.debug("Tab coloring enabled. Applying style ${settings.style} with color $newColor for ${content.displayName}.")
            when (settings.style) {
                "BACKGROUND" -> {
                    if (newColor != null) {
                        componentToStyle.background = newColor
                        if (componentToStyle is JComponent) componentToStyle.isOpaque = true
                    } else {
                         if (tabLabel != null) {
                            tabLabel?.background = null // Revert to default
                            tabLabel?.isOpaque = false
                        } else {
                            targetComponent.background = UIManager.getColor("Panel.background")
                            targetComponent.isOpaque = false
                        }
                    }
                }
                "BORDER_TOP", "BORDER_LEFT", "BORDER_RIGHT", "BORDER_BOTTOM" -> {
                    if (newColor != null) {
                        val thickness = 2
                        val border = when (settings.style) {
                            "BORDER_TOP" -> BorderFactory.createMatteBorder(thickness, 0, 0, 0, newColor)
                            "BORDER_LEFT" -> BorderFactory.createMatteBorder(0, thickness, 0, 0, newColor)
                            "BORDER_RIGHT" -> BorderFactory.createMatteBorder(0, 0, 0, thickness, newColor)
                            "BORDER_BOTTOM" -> BorderFactory.createMatteBorder(0, 0, thickness, 0, newColor)
                            else -> null
                        }
                        componentToStyle.border = border
                    } else {
                        componentToStyle.border = if (tabLabel != null) null else UIManager.getBorder("Component.border")
                    }
                }
            }
        }

        // If TabLabel was found and styled, its parent needs repaint.
        // If only targetComponent (content.component) was styled, it needs repaint.
        (tabLabel ?: componentToStyle).parent?.revalidate()
        (tabLabel ?: componentToStyle).parent?.repaint()
        componentToStyle.revalidate()
        componentToStyle.repaint()

        if (tabLabel == null) {
            logger.warn("Could not find specific TabLabel for ${content.displayName}. Applied style to content.component. // TODO: Refine to target specific tab header (e.g., TabLabel)")
        }
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // FORCED DIAGNOSTIC: Attempt to get VfsListenerService
        try {
            val retrievedVfsListenerService = project.getService(com.github.uiopak.lstcrc.services.VfsListenerService::class.java)
            if (retrievedVfsListenerService != null) {
                logger.info("MyToolWindowFactory: Successfully retrieved VfsListenerService instance.")
                // If the service is retrieved here, its init block (and VfsChangeListener's init) should have already fired or will fire now.
            } else {
                logger.warn("MyToolWindowFactory: project.getService(VfsListenerService::class.java) returned NULL. VFS updates might not work.")
            }
        } catch (e: Throwable) {
            logger.error("MyToolWindowFactory: EXCEPTION while trying to get VfsListenerService. VFS updates might not work.", e)
        }
        logger.info("MyToolWindowFactory: createToolWindowContent called.")
        val propertiesComponent = PropertiesComponent.getInstance()
        val tabColoringSettings = getTabColoringSettings(propertiesComponent)
        logger.info("MyToolWindowFactory: Loaded tab coloring settings: $tabColoringSettings")

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
        applyTabStyling(headContent, tabColoringSettings, project, toolWindow)
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
                    applyTabStyling(branchContent, tabColoringSettings, project, toolWindow)
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
                applyTabStyling(initialBranchContent, tabColoringSettings, project, toolWindow)
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

                applyTabStyling(event.content, getTabColoringSettings(PropertiesComponent.getInstance()), project, toolWindow) // Get fresh settings
                logger.info("MyToolWindowFactory.ContentManagerListener: Applied style to added tab ${event.content.displayName}")

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
                // Potentially re-apply style for selected tab if needed (e.g., different style for active tab)
                // For now, basic styling is applied on add. Active/inactive distinction is not yet handled.
                // applyTabStyling(selectedContent, getTabColoringSettings(PropertiesComponent.getInstance()), project, toolWindow) // Get fresh settings
                logger.info("MyToolWindowFactory.ContentManagerListener: selectionChanged - new selection: ${selectedContent.displayName}. Styling is currently applied on add/load.")

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
        // Regarding OpenBranchSelectionTabAction.kt:
        // If OpenBranchSelectionTabAction.kt directly creates and adds content in a way that
        // bypasses contentAdded or makes it difficult to style immediately, a comment would be needed there.
        // For now, we assume contentAdded in MyToolWindowFactory will catch tabs created by this action.
        // If issues arise, a // TODO: Ensure tab styling is applied when this action creates a tab. would be added to OpenBranchSelectionTabAction.kt
        toolWindow.setTitleActions(listOf(openSelectionTabAction))

        // PropertiesComponent already retrieved at the beginning of the method
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

package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.services.BranchSnapshot
import com.github.uiopak.lstcrc.services.GitService
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.github.uiopak.lstcrc.utils.LstCrcKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import git4idea.repo.GitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A helper object for common tool window UI operations.
 */
object ToolWindowHelper {
    private val logger = thisLogger()

    private data class BranchSelectionData(
        val primaryRepo: GitRepository?,
        val branchSnapshot: BranchSnapshot
    )

    /**
     * Creates a new closable content tab for a branch comparison and adds it to the content manager.
     * This is the standardized way to create a new branch tab.
     *
     * @param project The current project.
     * @param toolWindow The tool window instance.
     * @param branchName The branch/revision identifier.
     * @param displayName The text to show on the tab.
     * @param contentManager The content manager to add the tab to.
     * @return The newly created [Content] object.
     */
    internal fun createBranchContent(
        project: Project,
        toolWindow: ToolWindow,
        branchName: String,
        displayName: String,
        contentManager: ContentManager
    ): Content {
        val newContentView = LstCrcChangesBrowser(project, branchName, toolWindow.disposable)

        val contentFactory = ContentFactory.getInstance()
        val newContent = contentFactory.createContent(newContentView, displayName, false).apply {
            isCloseable = true
            putUserData(LstCrcKeys.BRANCH_NAME_KEY, branchName)
        }

        contentManager.addContent(newContent)
        return newContent
    }

    /**
     * Creates and selects a new comparison tab for the given branch/revision name.
     * If a tab for this name already exists, it simply selects it.
     *
     * @param project The current project.
     * @param toolWindow The LST-CRC tool window instance.
     * @param branchName The branch or revision identifier for the new tab.
     */
    fun createAndSelectTab(project: Project, toolWindow: ToolWindow, branchName: String) {
        logger.info("HELPER: createAndSelectTab called for '$branchName'")
        val contentManager = toolWindow.contentManager
        val stateService = project.service<ToolWindowStateService>()

        val existingContent = contentManager.contents.find { it.getUserData(LstCrcKeys.BRANCH_NAME_KEY) == branchName }

        if (existingContent != null) {
            logger.info("HELPER: Tab for '$branchName' already exists. Selecting it.")
            contentManager.setSelectedContent(existingContent, true)
        } else {
            logger.info("HELPER: Creating new tab for '$branchName'")
            val newContent = createBranchContent(project, toolWindow, branchName, branchName, contentManager)
            contentManager.setSelectedContent(newContent, true)
            addAndSelectTabInState(stateService, branchName, newContent.component as? LstCrcChangesBrowser)
        }
    }


    /**
     * Registers a newly created tab in the state service and selects it,
     * triggering a data refresh. Falls back to direct refresh if state sync fails.
     */
    private fun addAndSelectTabInState(
        stateService: ToolWindowStateService,
        branchName: String,
        browser: LstCrcChangesBrowser?
    ) {
        stateService.addTab(branchName)
        val newIndex = stateService.state.openTabs.indexOfFirst { it.branchName == branchName }
        if (newIndex != -1) {
            stateService.setSelectedTab(newIndex)
        } else {
            browser?.requestRefreshData()
        }
    }

    /**
     * Opens a temporary "Select Branch" tab in the tool window.
     * If such a tab already exists, it is selected. Otherwise, a new one is created.
     * The tab contains a [BranchSelectionPanel] to choose a branch. Upon selection,
     * the temporary tab is replaced by a permanent comparison tab for the selected branch.
     *
     * @param project The current project.
     * @param toolWindow The LST-CRC tool window instance.
     */
    fun openBranchSelectionTab(project: Project, toolWindow: ToolWindow) {
        toolWindow.activate({
            logger.info("HELPER: openBranchSelectionTab called.")
            val selectionTabName = LstCrcBundle.message("tab.name.select.branch")
            val contentManager: ContentManager = toolWindow.contentManager

            if (selectExistingBranchSelectionTab(contentManager, selectionTabName)) {
                return@activate
            }

            val stateService = project.service<ToolWindowStateService>()

            stateService.coroutineScope.launch {
                val data = loadBranchSelectionData(project)

                withContext(Dispatchers.EDT) {
                    if (project.isDisposed || toolWindow.isDisposed) return@withContext
                    addBranchSelectionContent(project, toolWindow, selectionTabName, contentManager, stateService, data)
                }
            }

        }, true, true)
    }

    private fun selectExistingBranchSelectionTab(contentManager: ContentManager, selectionTabName: String): Boolean {
        val existingContent = contentManager.findContent(selectionTabName) ?: return false
        contentManager.setSelectedContent(existingContent, true)
        (existingContent.component as? BranchSelectionPanel)?.requestFocusOnSearchField()
        logger.info("HELPER: Found existing '$selectionTabName' tab and selected it.")
        return true
    }

    private suspend fun loadBranchSelectionData(project: Project): BranchSelectionData {
        return withBackgroundProgress(project, LstCrcBundle.message("git.task.repo.info")) {
            val gitService = project.service<GitService>()
            val repo = gitService.getPrimaryRepository()?.also { it.update() }
            BranchSelectionData(repo, gitService.getBranchSnapshot(repo))
        }
    }

    private fun addBranchSelectionContent(
        project: Project,
        toolWindow: ToolWindow,
        selectionTabName: String,
        contentManager: ContentManager,
        stateService: ToolWindowStateService,
        data: BranchSelectionData
    ) {
        val branchSelectionUi = createBranchSelectionPanel(project, toolWindow, selectionTabName, stateService, data)
        logger.info("HELPER: Creating and adding new '$selectionTabName' tab to UI.")
        val newContent = ContentFactory.getInstance().createContent(branchSelectionUi.getPanel(), selectionTabName, true).apply {
            isCloseable = true
        }
        contentManager.addContent(newContent)
        contentManager.setSelectedContent(newContent, true)
        branchSelectionUi.requestFocusOnSearchField()
    }

    private fun createBranchSelectionPanel(
        project: Project,
        toolWindow: ToolWindow,
        selectionTabName: String,
        stateService: ToolWindowStateService,
        data: BranchSelectionData
    ): BranchSelectionPanel {
        val gitService = project.service<GitService>()
        return BranchSelectionPanel(gitService, data.primaryRepo, data.branchSnapshot) { selectedBranchName ->
            handleBranchSelected(project, toolWindow, selectionTabName, stateService, selectedBranchName)
        }
    }

    private fun handleBranchSelected(
        project: Project,
        toolWindow: ToolWindow,
        selectionTabName: String,
        stateService: ToolWindowStateService,
        selectedBranchName: String
    ) {
        logger.info("HELPER (Callback): Branch '$selectedBranchName' selected from panel.")
        val manager = toolWindow.contentManager
        val selectionTabContent = manager.findContent(selectionTabName)
        if (selectedBranchName.isBlank() || selectionTabContent == null) {
            logger.error("HELPER (Callback): selectedBranchName is blank or selection tab disappeared.")
            selectionTabContent?.let { manager.removeContent(it, true) }
            return
        }

        val existingBranchTab = manager.contents.find { it.getUserData(LstCrcKeys.BRANCH_NAME_KEY) == selectedBranchName }
        if (existingBranchTab != null) {
            manager.setSelectedContent(existingBranchTab, true)
            manager.removeContent(selectionTabContent, true)
            return
        }

        repurposeSelectionTab(project, toolWindow, stateService, manager, selectionTabContent, selectedBranchName)
    }

    private fun repurposeSelectionTab(
        project: Project,
        toolWindow: ToolWindow,
        stateService: ToolWindowStateService,
        manager: ContentManager,
        selectionTabContent: Content,
        selectedBranchName: String
    ) {
        logger.info("HELPER (Callback): Repurposing selection tab to '$selectedBranchName'.")
        val newBranchContentView = LstCrcChangesBrowser(project, selectedBranchName, toolWindow.disposable)
        selectionTabContent.displayName = selectedBranchName
        selectionTabContent.component = newBranchContentView
        selectionTabContent.putUserData(LstCrcKeys.BRANCH_NAME_KEY, selectedBranchName)
        manager.setSelectedContent(selectionTabContent, true)
        addAndSelectTabInState(stateService, selectedBranchName, newBranchContentView)
    }
}
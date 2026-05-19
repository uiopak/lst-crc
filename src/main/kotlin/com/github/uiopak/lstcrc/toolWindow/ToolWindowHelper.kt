package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.LstCrcConstants
import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.services.BranchSnapshot
import com.github.uiopak.lstcrc.services.GitService
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.github.uiopak.lstcrc.utils.LstCrcKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
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

    internal fun normalizedTabAlias(alias: String?): String? = alias?.trim()?.ifEmpty { null }

    internal fun branchSelectionTabName(): String = LstCrcBundle.message("tab.name.select.branch")

    internal fun activateToolWindow(project: Project, onActivated: (ToolWindow) -> Unit): Boolean {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(LstCrcConstants.TOOL_WINDOW_ID) ?: return false
        activateToolWindow(toolWindow, onActivated)
        return true
    }

    internal fun activateToolWindow(toolWindow: ToolWindow, onActivated: (ToolWindow) -> Unit) {
        toolWindow.activate({ onActivated(toolWindow) }, true, true)
    }

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
        branchName: String,
        displayName: String,
        contentManager: ContentManager,
        order: Int? = null
    ): Content {
        val contentDisposable = Disposer.newDisposable("LST-CRC branch tab: $branchName")
        val newContentView = LstCrcChangesBrowser(project, branchName, contentDisposable)

        val contentFactory = ContentFactory.getInstance()
        val newContent = contentFactory.createContent(newContentView, displayName, false).apply {
            isCloseable = true
            setDisposer(contentDisposable)
            putUserData(LstCrcKeys.BRANCH_NAME_KEY, branchName)
        }

        if (order != null && order >= 0) {
            contentManager.addContent(newContent, order)
        } else {
            contentManager.addContent(newContent)
        }
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

        val existingContent = findContentByBranchName(contentManager, branchName)

        if (existingContent != null) {
            logger.info("HELPER: Tab for '$branchName' already exists. Selecting it.")
            contentManager.setSelectedContent(existingContent, true)
        } else {
            logger.info("HELPER: Creating new tab for '$branchName'")
            createSelectAndRegisterBranchContent(project, branchName, contentManager, stateService)
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
        val newIndex = stateService.findTabIndex(branchName)
        if (newIndex != -1) {
            stateService.setSelectedTab(newIndex)
        } else {
            browser?.requestRefreshData()
        }
    }

    private fun createSelectAndRegisterBranchContent(
        project: Project,
        branchName: String,
        contentManager: ContentManager,
        stateService: ToolWindowStateService,
        order: Int? = null
    ): Content {
        val newContent = createBranchContent(project, branchName, branchName, contentManager, order)
        contentManager.setSelectedContent(newContent, true)
        addAndSelectTabInState(stateService, branchName, newContent.component as? LstCrcChangesBrowser)
        return newContent
    }

    internal fun findContentByBranchName(contentManager: ContentManager, branchName: String): Content? {
        return findContent(contentManager) { it.getUserData(LstCrcKeys.BRANCH_NAME_KEY) == branchName }
    }

    internal fun findHeadContent(contentManager: ContentManager): Content? {
        return findContent(contentManager) { !it.isCloseable }
    }

    internal fun findContentByDisplayName(contentManager: ContentManager, displayName: String): Content? {
        return findContent(contentManager) { it.displayName == displayName }
    }

    internal fun findBranchSelectionContent(contentManager: ContentManager): Content? {
        return findContentByDisplayName(contentManager, branchSelectionTabName())
    }

    private fun findContent(contentManager: ContentManager, matches: (Content) -> Boolean): Content? {
        return contentManager.contents.firstOrNull(matches)
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
        activateToolWindow(toolWindow) {
            logger.info("HELPER: openBranchSelectionTab called.")
            val contentManager: ContentManager = toolWindow.contentManager

            if (selectExistingBranchSelectionTab(contentManager)) {
                return@activateToolWindow
            }

            val stateService = project.service<ToolWindowStateService>()

            stateService.coroutineScope.launch {
                val gitService = project.service<GitService>()
                val (primaryRepo, branchSnapshot) = withBackgroundProgress(project, LstCrcBundle.message("git.task.repo.info")) {
                    val repo = gitService.getPrimaryRepository()
                    Pair(repo, gitService.getBranchSnapshot(repo))
                }

                withContext(Dispatchers.EDT) {
                    if (project.isDisposed || toolWindow.isDisposed) return@withContext
                    if (selectExistingBranchSelectionTab(contentManager)) {
                        return@withContext
                    }
                    addBranchSelectionContent(project, toolWindow, contentManager, stateService, primaryRepo, branchSnapshot)
                }
            }
        }
    }

    private fun selectExistingBranchSelectionTab(contentManager: ContentManager): Boolean {
        val selectionTabName = branchSelectionTabName()
        val existingSelection = findBranchSelectionContent(contentManager) ?: return false
        contentManager.setSelectedContent(existingSelection, true)
        (existingSelection.component as? BranchSelectionPanel)?.requestFocusOnSearchField()
        logger.info("HELPER: Found existing '$selectionTabName' tab and selected it.")
        return true
    }

    private fun addBranchSelectionContent(
        project: Project,
        toolWindow: ToolWindow,
        contentManager: ContentManager,
        stateService: ToolWindowStateService,
        primaryRepo: GitRepository?,
        branchSnapshot: BranchSnapshot
    ) {
        val selectionTabName = branchSelectionTabName()
        val gitService = project.service<GitService>()
        val branchSelectionUi = BranchSelectionPanel(gitService, primaryRepo, branchSnapshot) { selectedBranchName ->
            handleBranchSelected(project, toolWindow, stateService, selectedBranchName)
        }
        logger.info("HELPER: Creating and adding new '$selectionTabName' tab to UI.")
        val newContent = ContentFactory.getInstance().createContent(branchSelectionUi, selectionTabName, true).apply {
            isCloseable = true
            setDisposer(branchSelectionUi)
        }
        contentManager.addContent(newContent)
        contentManager.setSelectedContent(newContent, true)
        branchSelectionUi.requestFocusOnSearchField()
    }

    private fun handleBranchSelected(
        project: Project,
        toolWindow: ToolWindow,
        stateService: ToolWindowStateService,
        selectedBranchName: String
    ) {
        logger.info("HELPER (Callback): Branch '$selectedBranchName' selected from panel.")
        val manager = toolWindow.contentManager
        val selectionTabContent = findBranchSelectionContent(manager)
        if (selectedBranchName.isBlank() || selectionTabContent == null) {
            logger.error("HELPER (Callback): selectedBranchName is blank or selection tab disappeared.")
            selectionTabContent?.let { manager.removeContent(it, true) }
            return
        }

        val existingBranchTab = findContentByBranchName(manager, selectedBranchName)
        if (existingBranchTab != null) {
            manager.setSelectedContent(existingBranchTab, true)
            manager.removeContent(selectionTabContent, true)
            return
        }

        replaceSelectionTab(project, toolWindow, stateService, manager, selectionTabContent, selectedBranchName)
    }

    private fun replaceSelectionTab(
        project: Project,
        toolWindow: ToolWindow,
        stateService: ToolWindowStateService,
        manager: ContentManager,
        selectionTabContent: Content,
        selectedBranchName: String
    ) {
        logger.info("HELPER (Callback): Replacing selection tab with '$selectedBranchName'.")
        val selectionIndex = manager.getIndexOfContent(selectionTabContent)
        manager.removeContent(selectionTabContent, true)

        createSelectAndRegisterBranchContent(project, selectedBranchName, manager, stateService, selectionIndex)
    }
}
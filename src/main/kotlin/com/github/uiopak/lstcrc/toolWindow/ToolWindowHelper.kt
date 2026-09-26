package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.LstCrcConstants
import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.services.BranchSnapshot
import com.github.uiopak.lstcrc.services.GitService
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.github.uiopak.lstcrc.utils.LstCrcKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
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

    internal fun updateNormalizedTabAlias(project: Project, branchName: String, alias: String?) {
        project.service<ToolWindowStateService>().updateTabAlias(branchName, normalizedTabAlias(alias))
    }

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
            @Suppress("UsePropertyAccessSyntax") // Content.disposer is a val; the setter is the only API
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
        logger.debug { "HELPER: createAndSelectTab called for '$branchName'" }
        val contentManager = toolWindow.contentManager
        val existingContent = findContentByBranchName(contentManager, branchName)
        if (existingContent != null) {
            logger.debug { "HELPER: Tab for '$branchName' already exists. Selecting it." }
            contentManager.setSelectedContent(existingContent, true)
        } else {
            logger.debug { "HELPER: Creating new tab for '$branchName'" }
            createSelectAndRegisterBranchContent(project, branchName, contentManager)
        }
    }

    /**
     * Adds and selects a tab for [branchName], then registers it in the state service, which selects it
     * there and loads its data. Falls back to a direct refresh if the state has no such tab.
     */
    private fun createSelectAndRegisterBranchContent(
        project: Project,
        branchName: String,
        contentManager: ContentManager,
        order: Int? = null
    ) {
        val newContent = createBranchContent(project, branchName, branchName, contentManager, order)
        contentManager.setSelectedContent(newContent, true)
        val stateService = project.service<ToolWindowStateService>()
        stateService.addTab(branchName)
        val newIndex = stateService.findTabIndex(branchName)
        if (newIndex != -1) {
            stateService.setSelectedTab(newIndex)
        } else {
            (newContent.component as? LstCrcChangesBrowser)?.requestRefreshData()
        }
    }

    internal fun findContentByBranchName(contentManager: ContentManager, branchName: String): Content? =
        contentManager.contents.firstOrNull { it.getUserData(LstCrcKeys.BRANCH_NAME_KEY) == branchName }

    internal fun findHeadContent(contentManager: ContentManager): Content? =
        contentManager.contents.firstOrNull { !it.isCloseable }

    internal fun findContentByDisplayName(contentManager: ContentManager, displayName: String): Content? =
        contentManager.contents.firstOrNull { it.displayName == displayName }

    internal fun findBranchSelectionContent(contentManager: ContentManager): Content? =
        findContentByDisplayName(contentManager, branchSelectionTabName())

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
            logger.debug { "HELPER: openBranchSelectionTab called." }
            val contentManager: ContentManager = toolWindow.contentManager

            if (selectExistingBranchSelectionTab(contentManager)) {
                return@activateToolWindow
            }

            project.service<ToolWindowStateService>().coroutineScope.launch {
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
                    addBranchSelectionContent(project, toolWindow, contentManager, primaryRepo, branchSnapshot)
                }
            }
        }
    }

    private fun selectExistingBranchSelectionTab(contentManager: ContentManager): Boolean {
        val selectionTabName = branchSelectionTabName()
        val existingSelection = findBranchSelectionContent(contentManager) ?: return false
        contentManager.setSelectedContent(existingSelection, true)
        (existingSelection.component as? BranchSelectionPanel)?.requestFocusOnSearchField()
        logger.debug { "HELPER: Found existing '$selectionTabName' tab and selected it." }
        return true
    }

    private fun addBranchSelectionContent(
        project: Project,
        toolWindow: ToolWindow,
        contentManager: ContentManager,
        primaryRepo: GitRepository?,
        branchSnapshot: BranchSnapshot
    ) {
        val selectionTabName = branchSelectionTabName()
        val gitService = project.service<GitService>()
        val branchSelectionUi = BranchSelectionPanel(gitService, primaryRepo, branchSnapshot) { selectedBranchName ->
            handleBranchSelected(project, toolWindow, selectedBranchName)
        }
        logger.debug { "HELPER: Creating and adding new '$selectionTabName' tab to UI." }
        val newContent = ContentFactory.getInstance().createContent(branchSelectionUi, selectionTabName, true).apply {
            isCloseable = true
            @Suppress("UsePropertyAccessSyntax") // Content.disposer is a val; the setter is the only API
            setDisposer(branchSelectionUi)
        }
        contentManager.addContent(newContent)
        contentManager.setSelectedContent(newContent, true)
        branchSelectionUi.requestFocusOnSearchField()
    }

    /** Replaces the "Select Branch" tab with the comparison tab for [selectedBranchName], at the same position. */
    private fun handleBranchSelected(project: Project, toolWindow: ToolWindow, selectedBranchName: String) {
        logger.debug { "HELPER (Callback): Branch '$selectedBranchName' selected from panel." }
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

        logger.debug { "HELPER (Callback): Replacing selection tab with '$selectedBranchName'." }
        val selectionIndex = manager.getIndexOfContent(selectionTabContent)
        manager.removeContent(selectionTabContent, true)
        createSelectAndRegisterBranchContent(project, selectedBranchName, manager, selectionIndex)
    }
}
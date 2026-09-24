package com.github.uiopak.lstcrc.testing

import com.github.uiopak.lstcrc.LstCrcConstants
import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.gutters.VisualTrackerManager
import com.github.uiopak.lstcrc.scopes.LstCrcProvidedScopes
import com.github.uiopak.lstcrc.scopes.LstCrcSearchScopeProvider
import com.github.uiopak.lstcrc.services.BranchSnapshot
import com.github.uiopak.lstcrc.services.GitService
import com.github.uiopak.lstcrc.services.ProjectActiveDiffDataService
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.github.uiopak.lstcrc.state.TabInfo
import com.github.uiopak.lstcrc.toolWindow.LstCrcChangesBrowser
import com.github.uiopak.lstcrc.toolWindow.BranchSelectionPanel
import com.github.uiopak.lstcrc.toolWindow.LstCrcSettingsService
import com.github.uiopak.lstcrc.toolWindow.ShowRepoComparisonInfoAction
import com.github.uiopak.lstcrc.toolWindow.LstCrcSettingDefinitions
import com.github.uiopak.lstcrc.toolWindow.LstCrcStatusWidget
import com.github.uiopak.lstcrc.toolWindow.ToolWindowHelper
import com.github.uiopak.lstcrc.toolWindow.ToolWindowSettingsProvider
import com.github.uiopak.lstcrc.toolWindow.ToolWindowUiCompatibility
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.notification.Notification
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsDirectoryMapping
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsShowConfirmationOption
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManagerEx
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx
import com.intellij.openapi.vcs.ex.LocalLineStatusTracker
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.PsiManager
import com.intellij.psi.search.scope.packageSet.PackageSetBase
import com.intellij.psi.search.scope.packageSet.CustomScopesProvider
import com.intellij.psi.search.scope.packageSet.NamedScopeManager
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import com.intellij.ui.FileColorManager
import com.intellij.ui.JBColor
import com.intellij.ui.content.Content
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Dialog
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.Window
import java.io.File
import java.lang.reflect.Field
import javax.swing.JComboBox
import javax.swing.JList
import javax.swing.JTree
import javax.swing.ListCellRenderer
import javax.swing.SwingUtilities
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicReference

@Service(Service.Level.APP)
class LstCrcUiTestBridge {

    private companion object {
        private const val PROJECT_BASE_PATH_NOT_AVAILABLE = "Project base path is not available"
    }

    private data class SyntheticRepoComparisonDialog(
        val title: String,
        val repositoryRootPath: String,
        val defaultTarget: String,
        val branches: List<String>
    )

    private val syntheticRepoComparisonDialog = AtomicReference<SyntheticRepoComparisonDialog?>()

    private fun vcsManager(project: Project): ProjectLevelVcsManager = project.service()

    fun isDumbMode(): Boolean = project().service<DumbService>().isDumb

    fun isGitVcsActive(): Boolean = onEdtResult {
        vcsManager(project()).getAllActiveVcss().any { it.name == "Git" }
    }

    fun activateGitVcsIntegration() {
        val project = project()
        val basePath = project.basePath ?: return
        onEdt {
            addGitDirectoryMapping(project, basePath)
            ProjectLevelVcsManagerEx.getInstanceEx(project).scheduleMappedRootsUpdate()
            configureGitConfirmationDialogs(project)
        }
        onBackground {
            refreshGitProjectState(project)
        }
    }

    fun activateGitVcsIntegrationFor(relativePath: String) {
        val project = project()
        val rootPath = projectDir(project).findFileByRelativePath(relativePath)?.path
            ?: File(project.basePath ?: error(PROJECT_BASE_PATH_NOT_AVAILABLE), relativePath).path
        onEdt {
            addGitDirectoryMapping(project, rootPath)
            ProjectLevelVcsManagerEx.getInstanceEx(project).scheduleMappedRootsUpdate()
            configureGitConfirmationDialogs(project)
        }
        onBackground {
            refreshGitProjectState(project, waitForChangeListUpdate = true)
        }
    }

    fun knownGitRepositoriesSnapshot(): String = onEdtResult {
        GitRepositoryManager.getInstance(project()).repositories
            .map { it.root.path }
            .sorted()
            .joinToString(";")
    }

    fun refreshProjectAfterExternalChange() {
        val project = project()
        onBackground {
            refreshGitProjectState(project, waitForChangeListUpdate = true, refreshCurrentSelection = true)
        }
    }

    fun openGitChangesView() {
        val toolWindow = toolWindow()
        onEdt {
            toolWindow.show()
            toolWindow.activate(null, true, true)
        }
    }

    fun openBranchSelectionTab() {
        val project = project()
        val toolWindow = toolWindow()
        onEdt {
            ToolWindowHelper.openBranchSelectionTab(project, toolWindow)
        }
    }

    fun resetGitChangesViewState() {
        val project = project()
        onEdt {
            val settings = settingsService()
            settings.resetToDefaults()

            val toolWindow = toolWindowOrNull(project) ?: return@onEdt
            ToolWindowUiCompatibility.setToolWindowTitleVisible(toolWindow, false)

            val contentManager = toolWindow.contentManager
            contentManager.contents
                .filter(Content::isCloseable)
                .toList()
                .forEach { contentManager.removeContent(it, true) }

            contentManager.contents.firstOrNull()?.let { contentManager.setSelectedContent(it, true) }
            toolWindow.hide()
        }
    }

    fun createAndSelectTab(branchName: String) {
        val project = project()
        val toolWindow = toolWindow()
        onEdt {
            ToolWindowHelper.createAndSelectTab(project, toolWindow, branchName)
            syncSelectedTabState(project)
        }
        awaitCurrentSelectionRefresh(project)
    }

    fun selectTab(tabName: String) {
        val project = project()
        val content = ToolWindowHelper.findContentByDisplayName(contentManager(), tabName)
            ?: error("Could not find LST-CRC tab '$tabName'.")
        selectContentAndSyncState(project, content)
        awaitCurrentSelectionRefresh(project)
    }

    fun hasTab(tabName: String): Boolean = ToolWindowHelper.findContentByDisplayName(contentManager(), tabName) != null

    fun selectedTabName(): String = onEdtResult { selectedContentDisplayName() }

    fun selectedRenderedRowsSnapshot(): String = onEdtResult {
        selectedBrowser()?.visibleRowTextsForTest()?.joinToString("\n").orEmpty()
    }

    fun selectedChangesTreeSnapshot(): String = onEdtResult {
        val browser = selectedBrowser()
        val renderedRows = browser?.visibleRowTextsForTest()?.joinToString("\n").orEmpty()
        val changeFileNames = browser?.currentChangeFileNamesSnapshot()?.joinToString("\n").orEmpty()

        sequenceOf(renderedRows, changeFileNames)
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    fun selectedExpandedTreeNodesSnapshot(): String = onEdtResult {
        selectedBrowser()?.expandedNodeTextsForTest()?.joinToString(";").orEmpty()
    }

    fun setSelectedTreeNodeExpanded(nodeText: String, expanded: Boolean) {
        onEdt {
            val browser = requireSelectedBrowser()
            if (!browser.setExpandedForVisibleNodeTextForTest(nodeText, expanded)) {
                error("Could not find tree node '$nodeText' in selected LST-CRC tree.")
            }
        }
    }

    fun createRevisionTab(revision: String, alias: String?) {
        val project = project()
        val toolWindow = toolWindow()
        onEdt {
            ToolWindowHelper.createAndSelectTab(project, toolWindow, revision)
            project.service<ToolWindowStateService>().updateTabAlias(revision, alias?.trim().takeUnless { it.isNullOrEmpty() })
            syncSelectedTabState(project)
        }
        awaitCurrentSelectionRefresh(project)
    }

    fun updateTabAlias(branchName: String, newAlias: String?) {
        onEdt {
            project().service<ToolWindowStateService>().updateTabAlias(branchName, newAlias)
        }
    }

    fun setBranchAsRepoComparison(branchName: String) {
        updateSelectedRepoComparison(branchName)
    }

    fun setRevisionAsRepoComparison(revision: String) {
        updateSelectedRepoComparison(revision)
    }

    private fun updateSelectedRepoComparison(targetRevision: String) {
        val project = project()
        onEdt {
            val stateService = project.service<ToolWindowStateService>()
            val selectedTabInfo = requireResolvedSelectedTabInfo(
                stateService,
                "No selected LST-CRC tab available for repo comparison update"
            )
            val repoRootPath = project.guessProjectDir()?.path
                ?: project.basePath?.let(::normalizePath)
                ?: error(PROJECT_BASE_PATH_NOT_AVAILABLE)
            stateService.updateTabRepoComparison(selectedTabInfo.branchName, repoRootPath, targetRevision, triggerRefresh = true)
        }
    }

    fun branchErrorNotificationsSnapshot(): String = onEdtResult {
        NotificationsManager.getNotificationsManager()
            .getNotificationsOfType(Notification::class.java, project())
            .asSequence()
            .filter { notification ->
                notification.displayId?.startsWith("LST-CRC.BranchError.") == true
            }
            .joinToString("\n") { notification ->
                val actionTexts = notification.actions.joinToString(",") { action -> action.templateText.orEmpty() }
                listOf(
                    notification.displayId.orEmpty(),
                    notification.title,
                    notification.content,
                    actionTexts
                ).joinToString("|")
            }
    }

    fun triggerBranchErrorNotificationAction(actionText: String) {
        val repoName = actionText.substringAfterLast("'").ifBlank {
            actionText.substringAfter("Change Comparison for '").substringBeforeLast("'")
        }
        val (repository, selectedTabInfo) = onEdtResult {
            val project = project()
            val repository = GitRepositoryManager.getInstance(project).repositories
                .firstOrNull { candidate -> candidate.root.name == repoName }
                ?: error("Could not find repository '$repoName' for branch error repair fallback")
            val stateService = project.service<ToolWindowStateService>()
            val selectedTabInfo = requireResolvedSelectedTabInfo(
                stateService,
                "No selected LST-CRC tab available for branch error repair fallback"
            )
            repository to selectedTabInfo
        }
        val project = project()
        onBackground {
            refreshGitProjectState(
                project,
                waitForChangeListUpdate = true,
                rootsToRefresh = listOf(repository.root)
            )
        }
        val snapshot = project.service<GitService>().getBranchSnapshot(repository)
        syntheticRepoComparisonDialog.set(syntheticRepoComparisonDialog(repository, selectedTabInfo.branchName, snapshot))
    }

    fun selectStatusWidgetEntry(displayName: String) {
        val project = project()
        onEdt {
            val toolWindow = toolWindow()
            if (displayName == "HEAD") {
                val headContent = ToolWindowHelper.findHeadContent(toolWindow.contentManager)
                    ?: error("Could not find HEAD content in LST-CRC tool window")
                toolWindow.contentManager.setSelectedContent(headContent, true)
                return@onEdt
            }

            val stateService = project.service<ToolWindowStateService>()
            val openTab = stateService.findTabByDisplayName(displayName)
                ?: error("Could not find state entry for widget selection '$displayName'.")
            val content = ToolWindowHelper.findContentByBranchName(toolWindow.contentManager, openTab.branchName)
                ?: error("Could not find content for widget selection '$displayName'.")
            toolWindow.contentManager.setSelectedContent(content, true)
        }
    }

    fun statusWidgetText(): String = onEdtResult {
        val project = project()
        val statusBar = WindowManager.getInstance().getStatusBar(project) ?: return@onEdtResult ""
        val widget = statusBar.getWidget(LstCrcStatusWidget.ID) ?: return@onEdtResult ""
        (widget as? StatusBarWidget.TextPresentation)?.getText().orEmpty()
    }

    fun setContextMenuEnabled(enabled: Boolean) {
        onEdt {
            settingsService().setContextMenuEnabled(enabled)
        }
    }

    fun configureClickActions(
        singleClickAction: String?,
        doubleClickAction: String?,
        middleClickAction: String?,
        doubleMiddleClickAction: String?,
        rightClickAction: String?,
        doubleRightClickAction: String?,
        showContextMenu: Boolean?
    ) {
        onEdt {
            val settings = settingsService()
            singleClickAction?.let { settings.setSingleClickAction(it) }
            doubleClickAction?.let { settings.setDoubleClickAction(it) }
            middleClickAction?.let { settings.setMiddleClickAction(it) }
            doubleMiddleClickAction?.let { settings.setDoubleMiddleClickAction(it) }
            rightClickAction?.let { settings.setRightClickAction(it) }
            doubleRightClickAction?.let { settings.setDoubleRightClickAction(it) }
            showContextMenu?.let { settings.setContextMenuEnabled(it) }
        }
    }

    fun clickSettingsSnapshot(): String = onEdtResult {
        val settings = settingsService()
        listOf(
            settings.getSingleClickAction(),
            settings.getDoubleClickAction(),
            settings.getMiddleClickAction(),
            settings.getDoubleMiddleClickAction(),
            settings.getRightClickAction(),
            settings.getDoubleRightClickAction(),
            settings.isContextMenuEnabled().toString(),
            settings.getUserDoubleClickDelay().toString()
        ).joinToString("|")
    }

    fun setDoubleClickDelayMs(delay: Int) {
        onEdt {
            settingsService().setUserDoubleClickDelay(delay)
        }
    }

    fun triggerConfiguredChangeInteraction(fileName: String, button: String, clickCount: Int) {
        onEdt {
            val tree = requireSelectedChangesTree()

            val targetRow = findTargetRow(tree, fileName)
                ?: error("Could not find change for file '$fileName' in selected LST-CRC browser.")

            val upperButton = button.uppercase()
            if (upperButton == "RIGHT" && ToolWindowSettingsProvider.isContextMenuEnabled()) {
                error("Context menu is enabled for right click. Query contextMenuActionsForFile() instead of invoking a configured action.")
            }

            val awtButton = when (upperButton) {
                "LEFT" -> MouseEvent.BUTTON1
                "MIDDLE" -> MouseEvent.BUTTON2
                "RIGHT" -> MouseEvent.BUTTON3
                else -> error("Unsupported mouse button '$button'.")
            }

            val bounds = tree.getRowBounds(targetRow)
            val x = bounds?.let { it.x + it.width / 2 } ?: 1
            val y = bounds?.let { it.y + it.height / 2 } ?: 1

            tree.getPathForRow(targetRow)?.let { tree.selectionPath = it }

            val now = System.currentTimeMillis()
            listOf(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED).forEach { eventId ->
                val event = MouseEvent(tree, eventId, now, 0, x, y, clickCount, false, awtButton)
                tree.dispatchEvent(event)
            }
        }
    }

    private fun findTargetRow(tree: JTree, fileName: String): Int? {
        return findChangeMatch(tree, fileName)?.row
    }

    fun contextMenuActionsForFile(fileName: String): String = onEdtResult {
        val browser = requireSelectedBrowser()
        val change = findChangeByFileName(fileName)
            ?: error("Could not find change for file '$fileName' in selected LST-CRC browser.")
        browser.availableContextMenuActionTitlesForTest(change).joinToString("|")
    }

    fun invokeContextMenuActionForFile(fileName: String, actionTitle: String) {
        onEdt {
            val browser = requireSelectedBrowser()
            val change = findChangeByFileName(fileName)
                ?: error("Could not find change for file '$fileName' in selected LST-CRC browser.")
            browser.invokeTestContextMenuAction(change, actionTitle)
        }
    }

    fun selectedEditorDescriptor(): String = onEdtResult {
        val project = project()
        val selectedFiles = FileEditorManager.getInstance(project).selectedFiles
        val file = selectedFiles.firstOrNull() ?: return@onEdtResult ""
        val fileType = file.fileType
        listOf(file.name, file.javaClass.name, fileType.name).joinToString("|")
    }

    fun hasDiffEditorOpen(): Boolean = onEdtResult {
        val project = project()
        val manager = FileEditorManager.getInstance(project)
        manager.allEditors.any { it.javaClass.name.contains("diff", ignoreCase = true) } ||
            manager.openFiles.any { file ->
                file.javaClass.name.contains("diff", ignoreCase = true) || file.fileType.name.contains("diff", ignoreCase = true)
            } ||
            Window.getWindows().any { window ->
                window.isShowing && window.javaClass.name.contains("diff", ignoreCase = true)
            }
    }

    fun diffEditorCount(): Int = onEdtResult {
        val project = project()
        val manager = FileEditorManager.getInstance(project)
        val diffEditorCount = manager.allEditors.count { it.javaClass.name.contains("diff", ignoreCase = true) }
        val diffFileCount = manager.openFiles.count { file ->
            file.javaClass.name.contains("diff", ignoreCase = true) || file.fileType.name.contains("diff", ignoreCase = true)
        }
        val diffWindowCount = Window.getWindows().count { window ->
            window.isShowing && window.javaClass.name.contains("diff", ignoreCase = true)
        }
        maxOf(diffEditorCount, diffFileCount, diffWindowCount)
    }

    fun closeAllEditors() {
        onEdt {
            val manager = FileEditorManager.getInstance(project())
            manager.openFiles.toList().forEach(manager::closeFile)
        }
    }

    fun openFile(relativePath: String) {
        val project = project()
        onEdt {
            val basePath = project.basePath ?: error(PROJECT_BASE_PATH_NOT_AVAILABLE)
            val file = LocalFileSystem.getInstance().refreshAndFindFileByPath(projectFilePath(basePath, relativePath))
                ?: error("Could not find file '$relativePath' under project base path '$basePath'.")
            FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, file), true)
            project.service<VisualTrackerManager>().settingsChanged()
        }
    }

    fun writeProjectFile(relativePath: String, content: String) {
        val project = project()
        onEdt {
            WriteCommandAction.runWriteCommandAction(project) {
                val baseDir = projectDir(project)
                val normalizedPath = normalizePath(relativePath)
                val parentPath = normalizedPath.substringBeforeLast('/', "")
                val fileName = normalizedPath.substringAfterLast('/')
                val parentDir = ensureRelativeDirectory(baseDir, parentPath)
                val file = parentDir.findChild(fileName) ?: parentDir.createChildData(this, fileName)
                VfsUtil.saveText(file, content)
                VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
            }
        }
        awaitCurrentSelectionRefresh(project)
    }

    fun renameProjectFile(oldPath: String, newPath: String) {
        val project = project()
        onEdt {
            WriteCommandAction.runWriteCommandAction(project) {
                val baseDir = projectDir(project)
                val source = baseDir.findFileByRelativePath(normalizePath(oldPath))
                    ?: error("Could not find file '$oldPath' under project base dir '${project.basePath}'.")
                val normalizedNewPath = normalizePath(newPath)
                val targetParentPath = normalizedNewPath.substringBeforeLast('/', "")
                val targetFileName = normalizedNewPath.substringAfterLast('/')
                val targetParent = ensureRelativeDirectory(baseDir, targetParentPath)
                targetParent.findChild(targetFileName)?.delete(this)
                if (source.parent != targetParent) {
                    source.move(this, targetParent)
                }
                if (source.name != targetFileName) {
                    source.rename(this, targetFileName)
                }
                VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
            }
        }
        awaitCurrentSelectionRefresh(project)
    }

    fun deleteProjectFile(relativePath: String) {
        val project = project()
        onEdt {
            WriteCommandAction.runWriteCommandAction(project) {
                val baseDir = projectDir(project)
                baseDir.findFileByRelativePath(normalizePath(relativePath))
                    ?.delete(this)
                    ?: error("Could not find file '$relativePath' under project base dir '${project.basePath}'.")
                VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
            }
        }
        awaitCurrentSelectionRefresh(project)
    }

    fun setShowWidgetContext(show: Boolean) {
        val project = project()
        onEdt {
            settingsService().setShowWidgetContext(show)
            LstCrcStatusWidget.refresh(project)
        }
        awaitCurrentSelectionRefresh(project)
    }

    fun setShowToolWindowTitle(show: Boolean) {
        onEdt {
            settingsService().setShowToolWindowTitle(show)
            ToolWindowUiCompatibility.setToolWindowTitleVisible(toolWindow(), show)
        }
    }

    fun isToolWindowTitleVisible(): Boolean = onEdtResult {
        ToolWindowUiCompatibility.isToolWindowTitleVisible(toolWindow())
    }

    fun setIncludeHeadInScopes(include: Boolean) {
        val project = project()
        onEdt {
            settingsService().setIncludeHeadInScopes(include)
        }
        awaitCurrentSelectionRefresh(project)
    }

    fun setGutterSettings(enableMarkers: Boolean?, enableForNewFiles: Boolean?) {
        val project = project()
        onEdt {
            val settings = settingsService()
            enableMarkers?.let {
                settings.setGutterMarkersEnabled(it)
            }
            enableForNewFiles?.let {
                settings.setGutterForNewFilesEnabled(it)
            }
        }
        awaitCurrentSelectionRefresh(project)
    }

    fun setExpandNewFilesInCollapsedDirs(enabled: Boolean) {
        onEdt {
            settingsService().setExpandNewFilesInCollapsedDirs(enabled)
        }
    }

    fun setShowUntrackedFilesAsNew(enabled: Boolean) {
        val project = project()
        onEdt {
            settingsService().setShowUntrackedFilesAsNew(enabled)
        }
        awaitCurrentSelectionRefresh(project)
    }

    fun setTreeContextSettings(showSingleRepo: Boolean?, showCommits: Boolean?, showLineStats: Boolean?) {
        val project = project()
        onEdt {
            val settings = settingsService()
            showSingleRepo?.let {
                settings.setShowContextForSingleRepo(it)
            }
            showCommits?.let {
                settings.setShowContextForCommits(it)
            }
            showLineStats?.let {
                settings.setShowLineStatsInTree(it)
            }
            selectedBrowser()?.rebuildView()
        }
        awaitCurrentSelectionRefresh(project)
    }

    fun setMultiRepoTreeContextSetting(show: Boolean) {
        val project = project()
        onEdt {
            settingsService().setShowContextForMultiRepo(show)
            selectedBrowser()?.rebuildView()
        }
        awaitCurrentSelectionRefresh(project)
    }

    fun isMultiRepoTreeContextEnabled(): Boolean = onEdtResult {
        settingsService().isShowContextForMultiRepo()
    }

    fun treeContextSettingsSnapshot(): String = onEdtResult {
        val settings = settingsService()
        "${settings.isShowContextForSingleRepo()}|" +
            "${settings.isShowContextForCommits()}|" +
            settings.isShowLineStatsInTree()
    }

    fun gutterSettingsSnapshot(): String = onEdtResult {
        val settings = settingsService()
        "${settings.isGutterMarkersEnabled()}|" +
            "${settings.isGutterForNewFilesEnabled()}|" +
            settings.isIncludeHeadInScopes()
    }

    fun selectedTabComparisonMap(): String = onEdtResult {
        val stateService = project().service<ToolWindowStateService>()
        val tabInfo = resolveSelectedTabInfo(stateService) ?: return@onEdtResult ""
        tabInfo.comparisonMap.entries
            .sortedBy { it.key }
            .joinToString(";") { "${it.key}=${it.value}" }
    }

    fun scopeContains(scopeId: String, relativePath: String): Boolean = onEdtResult {
        val project = project()
        val holders = NamedScopesHolder.getAllNamedScopeHolders(project)
        val scopeAndHolder = holders.asSequence()
            .flatMap { holder -> holder.scopes.asSequence().map { scope -> scope to holder } }
            .plus(
                CustomScopesProvider.CUSTOM_SCOPES_PROVIDER.getExtensions(project)
                    .asSequence()
                    .flatMap { provider -> provider.customScopes.asSequence().map { scope -> scope to NamedScopeManager.getInstance(project) } }
            )
            .plus(lstCrcScopes().map { scope -> scope to NamedScopeManager.getInstance(project) })
            .firstOrNull { (scope, _) -> scope.scopeId == scopeId }
            ?: return@onEdtResult false

        val basePath = project.basePath ?: return@onEdtResult false
        val absolutePath = projectFilePath(basePath, relativePath)
        val file = LocalFileSystem.getInstance().refreshAndFindFileByPath(absolutePath)
            ?: findActiveDiffFile(project, absolutePath)
            ?: return@onEdtResult false
        val (scope, holder) = scopeAndHolder
        val packageSet = scope.value ?: return@onEdtResult false
        if (packageSet is PackageSetBase) {
            return@onEdtResult packageSet.contains(file, project, holder)
        }

        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return@onEdtResult false
        packageSet.contains(psiFile, holder)
    }

    fun searchScopesSnapshot(): String = onEdtResult {
        LstCrcSearchScopeProvider()
            .getSearchScopes(project(), DataContext.EMPTY_CONTEXT)
            .joinToString(";") { scope -> scope.displayName }
    }

    fun branchSelectionTabBranchesSnapshot(): String = onEdtResult {
        selectedContentComponent<BranchSelectionPanel>()?.visibleLeafTextsForTest()?.joinToString(";").orEmpty()
    }

    fun searchScopeContains(displayName: String, relativePath: String): Boolean = onEdtResult {
        val project = project()
        val scope = LstCrcSearchScopeProvider()
            .getSearchScopes(project, DataContext.EMPTY_CONTEXT)
            .firstOrNull { candidate -> candidate.displayName == displayName }
            ?: return@onEdtResult false

        val basePath = project.basePath ?: return@onEdtResult false
        val absolutePath = projectFilePath(basePath, relativePath)
        val file = LocalFileSystem.getInstance().refreshAndFindFileByPath(absolutePath)
            ?: findActiveDiffFile(project, absolutePath)
            ?: return@onEdtResult false

        scope.contains(file)
    }

    fun openFindInFilesDialog() {
        onEdt {
            val action = ActionManager.getInstance().getAction("FindInPath")
                ?: error("Action 'FindInPath' is not registered")
            performAction(action)
        }
    }

    fun findDialogScopeOptionsSnapshot(): String = onEdtResult {
        val scopeItems = visibleWindows()
            .onEach(::ensureFindPopupScopeMode)
            .mapNotNull(::findScopeSelectionComboItems)
            .firstOrNull { items -> items.isNotEmpty() }
        scopeItems?.joinToString(";").orEmpty()
    }

    fun dismissTransientUi() {
        onEdt {
            syntheticRepoComparisonDialog.set(null)
            val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
            val dialog = visibleDialogs().firstOrNull()
            val target = when {
                focusOwner != null -> focusOwner
                dialog != null -> dialog
                else -> toolWindow().component
            }
            dispatchEscape(target)
            visibleDialogs().forEach { dialog ->
                if (dialog.isShowing) {
                    dialog.isVisible = false
                    dialog.dispose()
                }
            }
        }
    }

    fun openRepoComparisonDialog() {
        val project = project()
        val gitService = project.service<GitService>()
        val stateService = project.service<ToolWindowStateService>()
        val selectedTabInfo = stateService.getSelectedTabInfo()
            ?: error("No selected LST-CRC tab available for repository comparison dialog")
        val repositories = gitService.getRepositories()

        if (repositories.size == 1) {
            val repository = repositories.first()
            onBackground {
                refreshGitProjectState(
                    project,
                    waitForChangeListUpdate = true,
                    rootsToRefresh = listOf(repository.root)
                )
            }
            val snapshot = gitService.getBranchSnapshot(repository)
            syntheticRepoComparisonDialog.set(syntheticRepoComparisonDialog(repository, selectedTabInfo.branchName, snapshot))
            return
        }

        ApplicationManager.getApplication().invokeLater {
            performAction(ShowRepoComparisonInfoAction())
        }
    }

    fun visibleRepoComparisonDialogTitle(): String = onEdtResult {
        visibleBranchSelectionDialog()?.title ?: syntheticRepoComparisonDialog.get()?.title.orEmpty()
    }

    fun visibleRepoComparisonDialogBranchesSnapshot(): String = onEdtResult {
        val panel = findVisibleBranchSelectionPanel()
        if (panel != null) {
            return@onEdtResult panel.visibleLeafTextsForTest().joinToString(";")
        }
        syntheticRepoComparisonDialog.get()?.branches?.joinToString(";").orEmpty()
    }

    fun selectBranchInVisibleRepoComparisonDialog(branchName: String) {
        onEdt {
            val syntheticDialog = syntheticRepoComparisonDialog.get()
            if (syntheticDialog != null) {
                if (branchName !in syntheticDialog.branches) {
                    error("Could not find branch '$branchName' in synthetic repository comparison dialog")
                }
                applyRepoComparisonSelection(syntheticDialog.repositoryRootPath, syntheticDialog.defaultTarget, branchName)
                syntheticRepoComparisonDialog.set(null)
                return@onEdt
            }

            val dialog = visibleBranchSelectionDialog() ?: error("Repository comparison dialog is not visible")
            val panel = findVisibleBranchSelectionPanel() ?: error("Repository comparison dialog tree is not visible")
            if (!panel.selectVisibleBranchForTest(branchName)) {
                error("Could not find branch '$branchName' in repository comparison dialog")
            }

            val repoName = dialog.title.removePrefix("Select Branch for ")
            val repository = GitRepositoryManager.getInstance(project()).repositories
                .firstOrNull { candidate -> candidate.root.name == repoName }
                ?: error("Could not find repository '$repoName' for repository comparison dialog")
            val selectedTabInfo = requireResolvedSelectedTabInfo(
                project().service<ToolWindowStateService>(),
                "No selected LST-CRC tab available for repository comparison update"
            )
            applyRepoComparisonSelection(repository.root.path, selectedTabInfo.branchName, branchName)
            dialog.isVisible = false
            dialog.dispose()
        }
    }

    fun setRepoComparisonForRoot(relativePath: String, targetRevision: String) {
        val project = project()
        val repoRootPath = projectDir(project).findFileByRelativePath(relativePath)?.path
            ?: projectFilePath(project.basePath ?: error(PROJECT_BASE_PATH_NOT_AVAILABLE), relativePath)
        onEdt {
            val stateService = project.service<ToolWindowStateService>()
            val selectedTabInfo = requireResolvedSelectedTabInfo(
                stateService,
                "No selected LST-CRC tab available for repo comparison update"
            )
            stateService.updateTabRepoComparison(selectedTabInfo.branchName, repoRootPath, targetRevision, triggerRefresh = true)
        }
    }

    fun selectedTreeFileColor(fileName: String): String = onEdtResult {
        val browser = selectedBrowser() ?: return@onEdtResult ""
        val tree = selectedChangesTree() ?: return@onEdtResult ""
        val targetRow = findTargetRow(tree, fileName) ?: return@onEdtResult ""
        val renderer = tree.cellRenderer ?: return@onEdtResult ""
        val model = tree.model
        val path = tree.getPathForRow(targetRow) ?: return@onEdtResult ""

        val color = browser.fileColorForPathForTest(path)
            ?: renderer.getTreeCellRendererComponent(
                tree,
                path.lastPathComponent,
                tree.isRowSelected(targetRow),
                tree.isExpanded(targetRow),
                model.isLeaf(path.lastPathComponent),
                targetRow,
                false
            ).background
        color.toRgbSnapshot()
    }

    fun fileStatusForTreeItem(fileName: String): String = onEdtResult {
        val tree = selectedChangesTree() ?: return@onEdtResult ""
        findChangeMatch(tree, fileName)?.change?.fileStatus?.id.orEmpty()
    }

    fun deletedScopeColorSnapshot(): String = onEdtResult {        val project = project()
        val configured = FileColorManager.getInstance(project).getScopeColor("LSTCRC.Deleted")
        val fallback = JBColor.namedColor(
            "FileColor.Rose",
            JBColor(Color(255, 235, 236), Color(71, 43, 43))
        )
        (configured ?: fallback).toRgbSnapshot()
    }

    private fun findActiveDiffFile(project: Project, absolutePath: String): VirtualFile? {
        val normalizedPath = normalizePath(absolutePath)
        val diffDataService = project.service<ProjectActiveDiffDataService>()
        return sequenceOf(
            diffDataService.createdFiles,
            diffDataService.modifiedFiles,
            diffDataService.movedFiles,
            diffDataService.deletedFiles
        )
            .flatten()
            .firstOrNull { candidate ->
                val candidatePath = normalizePath(candidate.path)
                candidatePath == normalizedPath || candidatePath.endsWith("/$normalizedPath")
            }
    }

    fun scopeExists(scopeId: String): Boolean = onEdtResult {
        val project = project()
        NamedScopesHolder.getAllNamedScopeHolders(project)
            .asSequence()
            .flatMap { holder -> holder.scopes.asSequence() }
            .plus(
                CustomScopesProvider.CUSTOM_SCOPES_PROVIDER.getExtensions(project)
                    .asSequence()
                    .flatMap { provider -> provider.customScopes.asSequence() }
            )
            .plus(lstCrcScopes())
            .any { scope -> scope.scopeId == scopeId }
    }

    private fun lstCrcScopes() = sequenceOf(
        LstCrcProvidedScopes.CREATED_FILES_SCOPE,
        LstCrcProvidedScopes.MODIFIED_FILES_SCOPE,
        LstCrcProvidedScopes.MOVED_FILES_SCOPE,
        LstCrcProvidedScopes.DELETED_FILES_SCOPE,
        LstCrcProvidedScopes.CHANGED_FILES_SCOPE
    )

    fun visualGutterSummaryForSelectedEditor(): String = onEdtResult {
        val project = project()
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return@onEdtResult ""
        project.service<VisualTrackerManager>().debugGutterSummaryFor(editor.document)
    }

    private fun selectedContent(): Content? = contentManager().selectedContent

    private inline fun <reified T : Any> selectedContentComponent(): T? {
        return selectedContent()?.component as? T
    }

    private fun selectedBrowser(): LstCrcChangesBrowser? {
        return selectedContentComponent()
    }

    private fun requireSelectedBrowser(): LstCrcChangesBrowser {
        return selectedBrowser() ?: error("No selected LST-CRC browser is available.")
    }

    private fun findChangeByFileName(fileName: String): Change? {
        val tree = selectedChangesTree() ?: return null
        return findChangeMatch(tree, fileName)?.change
    }

    private fun findChangeMatch(tree: JTree, fileName: String): ChangeTreeMatch? {
        for (row in 0 until tree.rowCount) {
            val path = tree.getPathForRow(row) ?: continue
            val node = path.lastPathComponent as? DefaultMutableTreeNode ?: continue
            val change = node.userObject as? Change ?: continue
            val file = change.afterRevision?.file ?: change.beforeRevision?.file ?: continue
            if (pathMatchesFileName(file.path, file.name, fileName)) {
                return ChangeTreeMatch(row, change)
            }
        }
        return null
    }

    private data class ChangeTreeMatch(val row: Int, val change: Change)

    private fun normalizePath(path: String): String = path.replace('\\', '/')

    private fun projectFilePath(basePath: String, relativePath: String): String {
        return normalizePath(File(basePath, relativePath).path)
    }

    private fun pathMatchesFileName(path: String, actualFileName: String, requestedFileName: String): Boolean {
        val normalizedPath = normalizePath(path)
        return actualFileName == requestedFileName || normalizedPath.endsWith("/$requestedFileName")
    }

    private fun selectedChangesTree(): Tree? {
        val browser = selectedBrowser() ?: return null
        return browser.viewerTree()
    }

    private fun requireSelectedChangesTree(): Tree {
        return selectedChangesTree() ?: error("No selected LST-CRC changes tree is available.")
    }

    private fun performAction(action: AnAction) {
        val project = project()
        val toolWindow = toolWindowOrNull(project)
        val dataContext = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(PlatformDataKeys.TOOL_WINDOW, toolWindow)
            .add(PlatformDataKeys.CONTEXT_COMPONENT, toolWindow?.component)
            .build()
        val event = AnActionEvent.createEvent(
            action,
            dataContext,
            action.templatePresentation.clone(),
            ActionPlaces.UNKNOWN,
            ActionUiKind.NONE,
            null
        )
        action.actionPerformed(event)
    }

    private fun visibleWindows(): Sequence<Window> = Window.getWindows().asSequence().filter(Window::isShowing)

    private fun applyRepoComparisonSelection(repositoryRootPath: String, defaultTarget: String, branchName: String) {
        val stateService = project().service<ToolWindowStateService>()
        val selectedTabInfo = requireResolvedSelectedTabInfo(
            stateService,
            "No selected LST-CRC tab available for repository comparison update"
        )
        stateService.updateTabRepoComparison(selectedTabInfo.branchName, repositoryRootPath, branchName, defaultTarget)
    }

    private fun syntheticRepoComparisonDialog(
        repository: GitRepository,
        defaultTarget: String,
        snapshot: BranchSnapshot
    ): SyntheticRepoComparisonDialog {
        return SyntheticRepoComparisonDialog(
            title = "Select Branch for ${repository.root.name}",
            repositoryRootPath = repository.root.path,
            defaultTarget = defaultTarget,
            branches = (snapshot.localBranches + snapshot.remoteBranches).distinct().sorted()
        )
    }

    private fun visibleDialogs(): Sequence<Dialog> = visibleWindows().filterIsInstance<Dialog>()

    private fun visibleBranchSelectionDialog(): Dialog? = visibleDialogs().firstOrNull { dialog ->
        dialog.title.startsWith("Select Branch for ")
    }

    private fun findVisibleBranchSelectionPanel(): BranchSelectionPanel? {
        val dialog = visibleBranchSelectionDialog() ?: return null
        return descendantComponents(dialog).filterIsInstance<BranchSelectionPanel>().firstOrNull()
    }

    private fun descendantComponents(root: Component?): Sequence<Component> = sequence {
        if (root == null) {
            return@sequence
        }

        yield(root)
        if (root is Container) {
            root.components.forEach { child ->
                yieldAll(descendantComponents(child))
            }
        }
    }

    private fun findScopeSelectionComboItems(root: Component): List<String>? {
        val comboRoot = descendantComponents(root)
            .firstOrNull { candidate -> candidate.accessibleContext?.accessibleName == "Scope selection" }
            ?: return null
        val combo = descendantComponents(comboRoot)
            .filterIsInstance<JComboBox<*>>()
            .firstOrNull()
            ?: return null
        return (0 until combo.itemCount)
            .mapNotNull { index -> comboItemText(combo, combo.getItemAt(index), index) }
            .filter(String::isNotBlank)
    }

    private fun ensureFindPopupScopeMode(root: Component) {
        val scopeComboRoot = descendantComponents(root)
            .firstOrNull { candidate -> candidate.accessibleContext?.accessibleName == "Scope selection" }
        if (scopeComboRoot?.isShowing == true) {
            return
        }

        val scopeButton = descendantComponents(root)
            .firstOrNull { component -> component.accessibleContext?.accessibleName == "Scope" }
            ?: return
        dispatchComponentClick(scopeButton)
    }

    private fun comboItemText(combo: JComboBox<*>, item: Any?, index: Int): String? {
        item ?: return null
        if (item is String) {
            return item
        }
        val renderer = combo.renderer
        if (renderer != null) {
            @Suppress("UNCHECKED_CAST")
            val component = (renderer as ListCellRenderer<Any?>).getListCellRendererComponent(
                JList(),
                item,
                index,
                false,
                false
            )
            renderedComponentText(component)?.takeIf(String::isNotBlank)?.let { return it }
            component.accessibleContext?.accessibleName?.takeIf(String::isNotBlank)?.let { return it }
        }
        return item.toString()
    }

    private fun renderedComponentText(component: Component): String? {
        return renderedComponentText(component, visited = mutableSetOf())
    }

    private fun renderedComponentText(component: Component, visited: MutableSet<Component>): String? {
        if (!visited.add(component)) {
            return null
        }

        val fragments = linkedSetOf<String>()

        delegateTextRenderer(component)?.let { delegate ->
            renderedComponentText(delegate, visited)?.takeIf(String::isNotBlank)?.let(fragments::add)
        }
        invokeRenderedTextMethod(component, "getCharSequence")?.takeIf(String::isNotBlank)?.let(fragments::add)
        reflectColoredFragments(component)?.takeIf(String::isNotBlank)?.let(fragments::add)

        if (component is Container) {
            component.components.forEach { child ->
                renderedComponentText(child, visited)?.takeIf(String::isNotBlank)?.let(fragments::add)
            }
        }

        return fragments.joinToString(separator = " ").trim().ifBlank { null }
    }

    private fun delegateTextRenderer(component: Component): Component? {
        val rendererField = findDeclaredField(component.javaClass)
            .firstOrNull {
                Component::class.java.isAssignableFrom(it.type) &&
                    (it.name == "textRenderer" || it.name == "myTextRenderer")
            } ?: return null

        return readDeclaredField(component, rendererField) as? Component
    }

    private fun invokeRenderedTextMethod(component: Component, methodName: String): String? {
        val method = component.javaClass.methods.firstOrNull {
            it.name == methodName &&
                (it.parameterCount == 0 ||
                    (it.parameterCount == 1 && it.parameterTypes.singleOrNull() == Boolean::class.javaPrimitiveType))
        } ?: return null

        val value = runCatching {
            when (method.parameterCount) {
                0 -> method.invoke(component)
                1 -> method.invoke(component, false)
                else -> null
            }
        }.getOrNull() ?: return null

        return value.toString()
    }

    private fun reflectColoredFragments(component: Component): String? {
        val fragmentsField = findDeclaredField(component.javaClass)
            .firstOrNull { it.name == "myFragments" } ?: return null

        val fragments = readDeclaredField(component, fragmentsField) as? Iterable<*> ?: return null

        val text = fragments.mapNotNull(::fragmentText)
            .joinToString(separator = "")
            .trim()

        return text.ifBlank { null }
    }

    private fun fragmentText(fragment: Any?): String? {
        fragment ?: return null
        val textField = findDeclaredField(fragment.javaClass)
            .firstOrNull {
                CharSequence::class.java.isAssignableFrom(it.type) &&
                    (it.name == "fragmentText" || it.name == "myText" || it.name == "text")
            } ?: return null

        return (readDeclaredField(fragment, textField) as? CharSequence)?.toString()
    }

    private fun findDeclaredField(startClass: Class<*>): Sequence<Field> {
        return classHierarchy(startClass)
            .flatMap { it.declaredFields.asSequence() }
    }

    private fun readDeclaredField(instance: Any, field: Field): Any? {
        return runCatching {
            field.isAccessible = true
            field.get(instance)
        }.getOrNull()
    }

    private fun classHierarchy(startClass: Class<*>): Sequence<Class<*>> = sequence {
        var current: Class<*>? = startClass
        while (current != null) {
            yield(current)
            current = current.superclass
        }
    }

    private fun dispatchComponentClick(component: Component) {
        val centerX = maxOf(1, component.width / 2)
        val centerY = maxOf(1, component.height / 2)
        dispatchMouseClick(component, centerX, centerY)
    }

    private fun dispatchMouseClick(component: Component, x: Int, y: Int) {
        val now = System.currentTimeMillis()
        listOf(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED).forEach { eventId ->
            val event = MouseEvent(component, eventId, now, 0, x, y, 1, false, MouseEvent.BUTTON1)
            component.dispatchEvent(event)
        }
    }

    private fun dispatchEscape(target: Component) {
        val source = if (target.isShowing) target else SwingUtilities.getWindowAncestor(target) ?: target
        val now = System.currentTimeMillis()
        listOf(KeyEvent.KEY_PRESSED, KeyEvent.KEY_RELEASED).forEach { eventId ->
            val event = KeyEvent(source, eventId, now, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED)
            source.dispatchEvent(event)
        }
    }

    private fun Color.toRgbSnapshot(): String = "$red,$green,$blue"

    private fun projectDir(project: Project) = project.guessProjectDir()
        ?: error("Project directory is not available")

    private fun refreshGitProjectState(
        project: Project,
        waitForChangeListUpdate: Boolean = false,
        refreshCurrentSelection: Boolean = false,
        rootsToRefresh: Collection<VirtualFile> = emptyList()
    ) {
        refreshBaseDir(project)
        rootsToRefresh.forEach { root ->
            root.refresh(false, true)
        }
        GitRepositoryManager.getInstance(project).repositories.forEach { it.update() }
        VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
        if (waitForChangeListUpdate) {
            ChangeListManagerEx.getInstanceEx(project).waitForUpdate()
        }
        if (refreshCurrentSelection) {
            project.service<ToolWindowStateService>().refreshDataForCurrentSelection().join()
        }
    }

    private fun ensureRelativeDirectory(root: VirtualFile, relativePath: String): VirtualFile {
        var current = root
        if (relativePath.isBlank()) {
            return current
        }

        relativePath.split('/')
            .filter(String::isNotBlank)
            .forEach { part ->
                current = current.findChild(part) ?: current.createChildDirectory(this, part)
            }
        return current
    }

    private fun configureGitConfirmationDialogs(project: Project) {
        val vcsManager = vcsManager(project)
        vcsManager.getAllSupportedVcss()
            .filter { it.name == "Git" }
            .forEach { vcs ->
                listOf(VcsConfiguration.StandardConfirmation.ADD, VcsConfiguration.StandardConfirmation.REMOVE)
                    .forEach { confirmationType ->
                        vcsManager.getStandardConfirmation(confirmationType, vcs).value = VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY
                    }
            }
    }

    private fun addGitDirectoryMapping(project: Project, rootPath: String) {
        val vcsManager = vcsManager(project)
        val normalizedRoot = normalizePath(rootPath)
        val existing = vcsManager.getDirectoryMappings()
            .filterNot { it.vcs == "Git" && normalizePath(it.directory) == normalizedRoot }
            .toMutableList()
        existing.add(VcsDirectoryMapping(normalizedRoot, "Git"))
        vcsManager.setDirectoryMappings(existing)
    }

    private fun refreshBaseDir(project: Project) {
        val basePath = project.basePath ?: return
        VfsUtil.markDirtyAndRefresh(false, true, true, File(basePath))
    }

    private fun selectContentAndSyncState(project: Project, content: Content) {
        onEdt {
            contentManager().setSelectedContent(content, true)
            syncSelectedTabState(project)
        }
    }

    private fun resolveSelectedTabInfo(stateService: ToolWindowStateService): TabInfo? {
        val selectedContentName = selectedContentDisplayName()
        return stateService.getSelectedTabInfo()
            ?: stateService.findTabByDisplayName(selectedContentName)
    }

    private fun requireResolvedSelectedTabInfo(
        stateService: ToolWindowStateService,
        errorMessage: String
    ): TabInfo {
        return resolveSelectedTabInfo(stateService) ?: error(errorMessage)
    }

    private fun syncSelectedTabState(project: Project) {
        val stateService = project.service<ToolWindowStateService>()
        val selectedContentName = selectedContentDisplayName()
        val selectedIndex = stateService.findTabIndexByDisplayName(selectedContentName)
        stateService.setSelectedTab(selectedIndex)
    }

    private fun selectedContentDisplayName(): String = selectedContent()?.displayName.orEmpty()

    private fun awaitCurrentSelectionRefresh(project: Project) {
        onBackground {
            project.service<ToolWindowStateService>().refreshDataForCurrentSelection().join()
        }
    }

    private fun contentManager() = toolWindow().contentManager

    private fun settingsService(): LstCrcSettingsService = ApplicationManager.getApplication().service()

    private fun toolWindow(): ToolWindow = toolWindowOrNull(project()) ?: error("GitChangesView tool window is not available")

    private fun toolWindowOrNull(project: Project): ToolWindow? = ToolWindowManager.getInstance(project).getToolWindow(LstCrcConstants.TOOL_WINDOW_ID)

    private fun project(): Project = ProjectManager.getInstance().openProjects.firstOrNull()
        ?: error("No open project available for LST-CRC Starter UI test bridge")

    private fun onEdt(action: () -> Unit) {
        if (ApplicationManager.getApplication().isDispatchThread) {
            action()
            return
        }
        ApplicationManager.getApplication().invokeAndWait(action)
    }

    private fun <T> onEdtResult(action: () -> T): T {
        if (ApplicationManager.getApplication().isDispatchThread) {
            return action()
        }

        val result = AtomicReference<T>()
        val failure = AtomicReference<Throwable?>()
        ApplicationManager.getApplication().invokeAndWait {
            runCatching(action)
                .onSuccess { result.set(it) }
                .onFailure { failure.set(it) }
        }
        failure.get()?.let { throw it }
        return result.get()
    }

    private fun onBackground(action: () -> Unit) {
        if (!ApplicationManager.getApplication().isDispatchThread) {
            action()
            return
        }

        ApplicationManager.getApplication().executeOnPooledThread(Callable {
            action()
        }).get()
    }
}
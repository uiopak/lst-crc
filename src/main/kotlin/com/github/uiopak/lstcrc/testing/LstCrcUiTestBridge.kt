package com.github.uiopak.lstcrc.testing

import com.github.uiopak.lstcrc.LstCrcConstants
import com.github.uiopak.lstcrc.gutters.VisualTrackerManager
import com.github.uiopak.lstcrc.messaging.PLUGIN_SETTINGS_CHANGED_TOPIC
import com.github.uiopak.lstcrc.scopes.LstCrcProvidedScopes
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.github.uiopak.lstcrc.state.TabInfo
import com.github.uiopak.lstcrc.toolWindow.LstCrcChangesBrowser
import com.github.uiopak.lstcrc.toolWindow.ToolWindowHelper
import com.github.uiopak.lstcrc.toolWindow.ToolWindowSettingsProvider
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsShowConfirmationOption
import com.intellij.openapi.vcs.changes.ChangeListManagerEx
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.psi.PsiManager
import com.intellij.psi.search.scope.packageSet.CustomScopesProvider
import com.intellij.psi.search.scope.packageSet.NamedScopeManager
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import com.intellij.ui.content.Content
import com.intellij.ui.content.impl.ContentManagerImpl
import git4idea.repo.GitRepositoryManager
import java.awt.Window
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicReference

@Service(Service.Level.APP)
class LstCrcUiTestBridge {

    fun isDumbMode(): Boolean = project().service<com.intellij.openapi.project.DumbService>().isDumb

    fun isGitVcsActive(): Boolean = onEdtResult {
        ProjectLevelVcsManager.getInstance(project()).getAllActiveVcss().any { it.name == "Git" }
    }

    fun activateGitVcsIntegration() {
        val project = project()
        val basePath = project.basePath ?: return
        onEdt {
            val vcsManager = ProjectLevelVcsManager.getInstance(project)
            vcsManager.setDirectoryMapping(basePath, "Git")
            ProjectLevelVcsManagerEx.getInstanceEx(project).scheduleMappedRootsUpdate()
            configureGitConfirmationDialogs(project)
        }
        onBackground {
            refreshBaseDir(project)
            GitRepositoryManager.getInstance(project).repositories.forEach { it.update() }
            VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
        }
    }

    fun refreshProjectAfterExternalChange() {
        val project = project()
        onBackground {
            refreshBaseDir(project)
            GitRepositoryManager.getInstance(project).repositories.forEach { it.update() }
            VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
            ChangeListManagerEx.getInstanceEx(project).waitForUpdate()
            project.service<ToolWindowStateService>().refreshDataForCurrentSelection().join()
        }
    }

    fun openGitChangesView() {
        val toolWindow = toolWindow()
        onEdt {
            toolWindow.show()
            toolWindow.activate(null, true, true)
        }
    }

    fun resetGitChangesViewState() {
        val project = project()
        onEdt {
            val properties = PropertiesComponent.getInstance()
            properties.setValue(ToolWindowSettingsProvider.APP_SINGLE_CLICK_ACTION_KEY, ToolWindowSettingsProvider.ACTION_OPEN_SOURCE)
            properties.setValue(ToolWindowSettingsProvider.APP_DOUBLE_CLICK_ACTION_KEY, ToolWindowSettingsProvider.ACTION_NONE)
            properties.setValue(ToolWindowSettingsProvider.APP_MIDDLE_CLICK_ACTION_KEY, ToolWindowSettingsProvider.ACTION_SHOW_IN_PROJECT_TREE)
            properties.setValue(ToolWindowSettingsProvider.APP_DOUBLE_MIDDLE_CLICK_ACTION_KEY, ToolWindowSettingsProvider.ACTION_NONE)
            properties.setValue(ToolWindowSettingsProvider.APP_RIGHT_CLICK_ACTION_KEY, ToolWindowSettingsProvider.ACTION_OPEN_DIFF)
            properties.setValue(ToolWindowSettingsProvider.APP_DOUBLE_RIGHT_CLICK_ACTION_KEY, ToolWindowSettingsProvider.ACTION_NONE)
            properties.setValue(ToolWindowSettingsProvider.APP_SHOW_CONTEXT_MENU_KEY, ToolWindowSettingsProvider.DEFAULT_SHOW_CONTEXT_MENU, false)
            properties.setValue(ToolWindowSettingsProvider.APP_USER_DOUBLE_CLICK_DELAY_KEY, ToolWindowSettingsProvider.DELAY_OPTION_SYSTEM_DEFAULT.toString())
            properties.setValue(ToolWindowSettingsProvider.APP_INCLUDE_HEAD_IN_SCOPES_KEY, ToolWindowSettingsProvider.DEFAULT_INCLUDE_HEAD_IN_SCOPES, false)
            properties.setValue(ToolWindowSettingsProvider.APP_ENABLE_GUTTER_MARKERS_KEY, ToolWindowSettingsProvider.DEFAULT_ENABLE_GUTTER_MARKERS, true)
            properties.setValue(ToolWindowSettingsProvider.APP_ENABLE_GUTTER_FOR_NEW_FILES_KEY, ToolWindowSettingsProvider.DEFAULT_ENABLE_GUTTER_FOR_NEW_FILES, false)
            properties.setValue(ToolWindowSettingsProvider.APP_SHOW_TOOL_WINDOW_TITLE_KEY, ToolWindowSettingsProvider.DEFAULT_SHOW_TOOL_WINDOW_TITLE, false)
            properties.setValue(ToolWindowSettingsProvider.APP_SHOW_WIDGET_CONTEXT_KEY, ToolWindowSettingsProvider.DEFAULT_SHOW_WIDGET_CONTEXT, false)
            properties.setValue(ToolWindowSettingsProvider.APP_SHOW_CONTEXT_SINGLE_REPO_KEY, ToolWindowSettingsProvider.DEFAULT_SHOW_CONTEXT_SINGLE_REPO, true)
            properties.setValue(ToolWindowSettingsProvider.APP_SHOW_CONTEXT_FOR_COMMITS_KEY, ToolWindowSettingsProvider.DEFAULT_SHOW_CONTEXT_FOR_COMMITS, false)

            val toolWindow = toolWindowOrNull(project) ?: return@onEdt
            toolWindow.component.putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, "true")

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
        val content = contentManager().contents.firstOrNull { it.displayName == tabName }
            ?: error("Could not find LST-CRC tab '$tabName'.")
        onEdt {
            contentManager().setSelectedContent(content, true)
            syncSelectedTabState(project)
        }
        awaitCurrentSelectionRefresh(project)
    }

    fun hasTab(tabName: String): Boolean = contentManager().contents.any { it.displayName == tabName }

    fun selectedTabName(): String = onEdtResult {
        contentManager().selectedContent?.displayName.orEmpty()
    }

    fun selectedChangesTreeSnapshot(): String = onEdtResult {
        val browser = selectedBrowser() ?: return@onEdtResult ""
        sequenceOf(
            browser.debugRenderedRowsSnapshot(),
            browser.debugChangeFileNamesSnapshot()
        )
            .filter { it.isNotBlank() }
            .joinToString("\n")
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

    fun setRevisionAsRepoComparison(revision: String) {
        val project = project()
        onEdt {
            val stateService = project.service<ToolWindowStateService>()
            val selectedTabInfo = resolveSelectedTabInfo(stateService)
                ?: error("No selected LST-CRC tab available for repo comparison update")
            val repoRootPath = project.basePath ?: error("Project base path is not available")
            val newMap = selectedTabInfo.comparisonMap.toMutableMap()
            newMap[repoRootPath] = revision
            stateService.updateTabComparisonMap(selectedTabInfo.branchName, newMap, true)
        }
    }

    fun selectStatusWidgetEntry(displayName: String) {
        val project = project()
        onEdt {
            val toolWindow = toolWindow()
            if (displayName == "HEAD") {
                val headContent = toolWindow.contentManager.contents.firstOrNull { !it.isCloseable }
                    ?: error("Could not find HEAD content in LST-CRC tool window")
                toolWindow.contentManager.setSelectedContent(headContent, true)
                return@onEdt
            }

            val stateService = project.service<ToolWindowStateService>()
            val openTab = stateService.state.openTabs.firstOrNull { it.branchName == displayName || it.alias == displayName }
                ?: error("Could not find state entry for widget selection '$displayName'.")
            val content = toolWindow.contentManager.contents.firstOrNull { candidate ->
                candidate.displayName == (openTab.alias ?: openTab.branchName)
            } ?: error("Could not find content for widget selection '$displayName'.")
            toolWindow.contentManager.setSelectedContent(content, true)
        }
    }

    fun statusWidgetText(): String = onEdtResult {
        val project = project()
        val statusBar = WindowManager.getInstance().getStatusBar(project) ?: return@onEdtResult ""
        val widget = statusBar.getWidget("LstCrcStatusWidget") ?: return@onEdtResult ""
        (widget as? com.intellij.openapi.wm.StatusBarWidget.TextPresentation)?.getText().orEmpty()
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
            val properties = PropertiesComponent.getInstance()
            singleClickAction?.let { properties.setValue(ToolWindowSettingsProvider.APP_SINGLE_CLICK_ACTION_KEY, it) }
            doubleClickAction?.let { properties.setValue(ToolWindowSettingsProvider.APP_DOUBLE_CLICK_ACTION_KEY, it) }
            middleClickAction?.let { properties.setValue(ToolWindowSettingsProvider.APP_MIDDLE_CLICK_ACTION_KEY, it) }
            doubleMiddleClickAction?.let { properties.setValue(ToolWindowSettingsProvider.APP_DOUBLE_MIDDLE_CLICK_ACTION_KEY, it) }
            rightClickAction?.let { properties.setValue(ToolWindowSettingsProvider.APP_RIGHT_CLICK_ACTION_KEY, it) }
            doubleRightClickAction?.let { properties.setValue(ToolWindowSettingsProvider.APP_DOUBLE_RIGHT_CLICK_ACTION_KEY, it) }
            showContextMenu?.let { properties.setValue(ToolWindowSettingsProvider.APP_SHOW_CONTEXT_MENU_KEY, it, false) }
        }
    }

    fun clickSettingsSnapshot(): String = onEdtResult {
        val properties = PropertiesComponent.getInstance()
        listOf(
            properties.getValue(ToolWindowSettingsProvider.APP_SINGLE_CLICK_ACTION_KEY, ""),
            properties.getValue(ToolWindowSettingsProvider.APP_DOUBLE_CLICK_ACTION_KEY, ""),
            properties.getValue(ToolWindowSettingsProvider.APP_MIDDLE_CLICK_ACTION_KEY, ""),
            properties.getValue(ToolWindowSettingsProvider.APP_DOUBLE_MIDDLE_CLICK_ACTION_KEY, ""),
            properties.getValue(ToolWindowSettingsProvider.APP_RIGHT_CLICK_ACTION_KEY, ""),
            properties.getValue(ToolWindowSettingsProvider.APP_DOUBLE_RIGHT_CLICK_ACTION_KEY, ""),
            PropertiesComponent.getInstance().getBoolean(ToolWindowSettingsProvider.APP_SHOW_CONTEXT_MENU_KEY, false).toString(),
            properties.getValue(ToolWindowSettingsProvider.APP_USER_DOUBLE_CLICK_DELAY_KEY, "")
        ).joinToString("|")
    }

    fun setDoubleClickDelayMs(delay: Int) {
        onEdt {
            PropertiesComponent.getInstance().setValue(ToolWindowSettingsProvider.APP_USER_DOUBLE_CLICK_DELAY_KEY, delay.toString())
        }
    }

    fun triggerConfiguredChangeInteraction(fileName: String, button: String, clickCount: Int) {
        onEdt {
            selectedBrowser()?.invokeConfiguredActionForFile(fileName, button, clickCount)
                ?: error("No selected LST-CRC browser is available.")
        }
    }

    fun contextMenuActionsForFile(fileName: String): String = onEdtResult {
        selectedBrowser()?.contextMenuActionTitlesForFile(fileName).orEmpty()
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

    fun closeAllEditors() {
        onEdt {
            val manager = FileEditorManager.getInstance(project())
            manager.openFiles.toList().forEach(manager::closeFile)
        }
    }

    fun openFile(relativePath: String) {
        val project = project()
        onEdt {
            val basePath = project.basePath ?: error("Project base path is not available")
            val file = LocalFileSystem.getInstance().refreshAndFindFileByPath(File(basePath, relativePath).path.replace('\\', '/'))
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
                val normalizedPath = relativePath.replace('\\', '/')
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
                val source = baseDir.findFileByRelativePath(oldPath.replace('\\', '/'))
                    ?: error("Could not find file '$oldPath' under project base dir '${project.basePath}'.")
                val normalizedNewPath = newPath.replace('\\', '/')
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
                baseDir.findFileByRelativePath(relativePath.replace('\\', '/'))
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
            PropertiesComponent.getInstance().setValue(ToolWindowSettingsProvider.APP_SHOW_WIDGET_CONTEXT_KEY, show, ToolWindowSettingsProvider.DEFAULT_SHOW_WIDGET_CONTEXT)
            project.messageBus.syncPublisher(PLUGIN_SETTINGS_CHANGED_TOPIC).onSettingsChanged()
        }
        awaitCurrentSelectionRefresh(project)
    }

    fun setShowToolWindowTitle(show: Boolean) {
        onEdt {
            PropertiesComponent.getInstance().setValue(ToolWindowSettingsProvider.APP_SHOW_TOOL_WINDOW_TITLE_KEY, show, ToolWindowSettingsProvider.DEFAULT_SHOW_TOOL_WINDOW_TITLE)
            val toolWindow = toolWindow()
            toolWindow.component.putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, if (show) null else "true")
            (toolWindow.contentManager as? ContentManagerImpl)?.let { manager ->
                (manager.ui as? ToolWindowContentUi)?.update()
            }
        }
    }

    fun isToolWindowTitleVisible(): Boolean = onEdtResult {
        toolWindow().component.getClientProperty(ToolWindowContentUi.HIDE_ID_LABEL) == null
    }

    fun setIncludeHeadInScopes(include: Boolean) {
        val project = project()
        onEdt {
            PropertiesComponent.getInstance().setValue(ToolWindowSettingsProvider.APP_INCLUDE_HEAD_IN_SCOPES_KEY, include, ToolWindowSettingsProvider.DEFAULT_INCLUDE_HEAD_IN_SCOPES)
        }
        awaitCurrentSelectionRefresh(project)
    }

    fun setGutterSettings(enableMarkers: Boolean?, enableForNewFiles: Boolean?) {
        val project = project()
        onEdt {
            val properties = PropertiesComponent.getInstance()
            enableMarkers?.let {
                properties.setValue(ToolWindowSettingsProvider.APP_ENABLE_GUTTER_MARKERS_KEY, it, ToolWindowSettingsProvider.DEFAULT_ENABLE_GUTTER_MARKERS)
            }
            enableForNewFiles?.let {
                properties.setValue(ToolWindowSettingsProvider.APP_ENABLE_GUTTER_FOR_NEW_FILES_KEY, it, ToolWindowSettingsProvider.DEFAULT_ENABLE_GUTTER_FOR_NEW_FILES)
            }
        }
        awaitCurrentSelectionRefresh(project)
    }

    fun setTreeContextSettings(showSingleRepo: Boolean?, showCommits: Boolean?) {
        val project = project()
        onEdt {
            val properties = PropertiesComponent.getInstance()
            showSingleRepo?.let {
                properties.setValue(ToolWindowSettingsProvider.APP_SHOW_CONTEXT_SINGLE_REPO_KEY, it, ToolWindowSettingsProvider.DEFAULT_SHOW_CONTEXT_SINGLE_REPO)
            }
            showCommits?.let {
                properties.setValue(ToolWindowSettingsProvider.APP_SHOW_CONTEXT_FOR_COMMITS_KEY, it, ToolWindowSettingsProvider.DEFAULT_SHOW_CONTEXT_FOR_COMMITS)
            }
            selectedBrowser()?.rebuildView()
        }
        awaitCurrentSelectionRefresh(project)
    }

    fun treeContextSettingsSnapshot(): String = onEdtResult {
        val properties = PropertiesComponent.getInstance()
        "${properties.getBoolean(ToolWindowSettingsProvider.APP_SHOW_CONTEXT_SINGLE_REPO_KEY, ToolWindowSettingsProvider.DEFAULT_SHOW_CONTEXT_SINGLE_REPO)}|" +
            properties.getBoolean(ToolWindowSettingsProvider.APP_SHOW_CONTEXT_FOR_COMMITS_KEY, ToolWindowSettingsProvider.DEFAULT_SHOW_CONTEXT_FOR_COMMITS)
    }

    fun gutterSettingsSnapshot(): String = onEdtResult {
        val properties = PropertiesComponent.getInstance()
        "${properties.getBoolean(ToolWindowSettingsProvider.APP_ENABLE_GUTTER_MARKERS_KEY, ToolWindowSettingsProvider.DEFAULT_ENABLE_GUTTER_MARKERS)}|" +
            "${properties.getBoolean(ToolWindowSettingsProvider.APP_ENABLE_GUTTER_FOR_NEW_FILES_KEY, ToolWindowSettingsProvider.DEFAULT_ENABLE_GUTTER_FOR_NEW_FILES)}|" +
            properties.getBoolean(ToolWindowSettingsProvider.APP_INCLUDE_HEAD_IN_SCOPES_KEY, ToolWindowSettingsProvider.DEFAULT_INCLUDE_HEAD_IN_SCOPES)
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
        val file = LocalFileSystem.getInstance().refreshAndFindFileByPath(File(basePath, relativePath).path.replace('\\', '/'))
            ?: return@onEdtResult false
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return@onEdtResult false
        val (scope, holder) = scopeAndHolder
        scope.value?.contains(psiFile, holder) == true
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
        val (highlighterSummary, gutterHighlighterCount) = collectGutterHighlighterSummary(editor)
        val trackerSummary = buildTrackerSummary(project, editor.document)
        "$highlighterSummary|highlighters=$gutterHighlighterCount|$trackerSummary"
    }

    private fun selectedBrowser(): LstCrcChangesBrowser? {
        return contentManager().selectedContent?.component as? LstCrcChangesBrowser
    }

    private fun collectGutterHighlighterSummary(editor: com.intellij.openapi.editor.Editor): Pair<String, Int> {
        val document = editor.document
        val highlighterParts = mutableListOf<String>()
        var gutterHighlighterCount = 0

        editor.markupModel.allHighlighters.forEach { highlighter ->
            val renderer = highlighter.gutterIconRenderer ?: return@forEach
            gutterHighlighterCount += 1
            val startOffset = highlighter.startOffset
            val endOffsetExclusive = maxOf(highlighter.endOffset, startOffset + 1)
            val startLine = document.getLineNumber(startOffset)
            val endLine = document.getLineNumber(endOffsetExclusive - 1) + 1
            highlighterParts.add("$startLine-$endLine:${renderer.javaClass.simpleName}")
        }

        return highlighterParts.joinToString(",") to gutterHighlighterCount
    }

    private fun buildTrackerSummary(project: Project, document: com.intellij.openapi.editor.Document): String {
        val tracker = LineStatusTrackerManager.getInstance(project).getLineStatusTracker(document) ?: return "tracker=none"
        val rangeParts = extractTrackerRangeParts(tracker)
        val visible = extractTrackerVisibility(tracker)
        return "${tracker.javaClass.simpleName}|visible=$visible|ranges=${rangeParts.joinToString(",")}"
    }

    private fun extractTrackerRangeParts(tracker: Any): List<String> {
        return runCatching {
            val getRanges = tracker.javaClass.methods.firstOrNull { it.name == "getRanges" && it.parameterCount == 0 }
            val ranges = (getRanges?.invoke(tracker) as? Collection<*>) ?: emptyList<Any?>()
            ranges.mapNotNull { range -> range?.let(::describeTrackerRange) }
        }.getOrDefault(emptyList())
    }

    private fun describeTrackerRange(rangeObject: Any): String? {
        val rangeClass = rangeObject::class.java
        val line1 = (rangeClass.methods.firstOrNull { it.name == "getLine1" }?.invoke(rangeObject) as? Number)?.toInt()
        val line2 = (rangeClass.methods.firstOrNull { it.name == "getLine2" }?.invoke(rangeObject) as? Number)?.toInt()
        if (line1 == null || line2 == null) {
            return null
        }

        val typeValue = (rangeClass.methods.firstOrNull { it.name == "getType" }?.invoke(rangeObject) as? Number)?.toInt()
        return "$line1-$line2:${trackerRangeTypeName(typeValue)}"
    }

    private fun trackerRangeTypeName(typeValue: Int?): String {
        return when (typeValue) {
            com.intellij.openapi.vcs.ex.Range.MODIFIED.toInt() -> "MODIFIED"
            com.intellij.openapi.vcs.ex.Range.INSERTED.toInt() -> "INSERTED"
            com.intellij.openapi.vcs.ex.Range.DELETED.toInt() -> "DELETED"
            else -> "UNKNOWN"
        }
    }

    private fun extractTrackerVisibility(tracker: Any): String {
        return runCatching {
            val mode = tracker.javaClass.methods.firstOrNull { it.name == "getMode" && it.parameterCount == 0 }?.invoke(tracker)
            if (mode == null) {
                null
            } else {
                mode::class.java.methods.firstOrNull { it.name == "isVisible" && it.parameterCount == 0 }?.invoke(mode)
            }
        }.getOrNull()?.toString() ?: "n/a"
    }

    private fun projectDir(project: Project) = project.guessProjectDir()
        ?: error("Project directory is not available")

    private fun ensureRelativeDirectory(root: com.intellij.openapi.vfs.VirtualFile, relativePath: String): com.intellij.openapi.vfs.VirtualFile {
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
            val vcsManager = ProjectLevelVcsManager.getInstance(project)
            vcsManager.getAllSupportedVcss()
                .filter { it.name == "Git" }
                .forEach { vcs ->
                    listOf(VcsConfiguration.StandardConfirmation.ADD, VcsConfiguration.StandardConfirmation.REMOVE)
                        .forEach { confirmationType ->
                            vcsManager.getStandardConfirmation(confirmationType, vcs).value = VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY
                        }
                }
        }

    private fun refreshBaseDir(project: Project) {
        val basePath = project.basePath ?: return
        val fileSystem = LocalFileSystem.getInstance()
        val baseDir = fileSystem.refreshAndFindFileByPath(basePath.replace('\\', '/')) ?: return
        baseDir.refresh(false, true)
        baseDir.findChild(".git")?.refresh(false, true)
    }

    private fun resolveSelectedTabInfo(stateService: ToolWindowStateService): TabInfo? {
        val selectedContentName = contentManager().selectedContent?.displayName.orEmpty()
        return stateService.getSelectedTabInfo()
            ?: stateService.state.openTabs.firstOrNull { it.branchName == selectedContentName || it.alias == selectedContentName }
    }

    private fun syncSelectedTabState(project: Project) {
        val stateService = project.service<ToolWindowStateService>()
        val selectedContentName = contentManager().selectedContent?.displayName.orEmpty()
        val selectedIndex = stateService.state.openTabs.indexOfFirst {
            it.branchName == selectedContentName || it.alias == selectedContentName
        }
        stateService.setSelectedTab(selectedIndex)
    }

    private fun awaitCurrentSelectionRefresh(project: Project) {
        onBackground {
            project.service<ToolWindowStateService>().refreshDataForCurrentSelection().join()
        }
    }

    private fun contentManager() = toolWindow().contentManager

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
package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.services.CategorizedChanges
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListListener
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffAction
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.SimpleAsyncChangesBrowser
import com.intellij.openapi.vfs.VirtualFile
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.UIManager

class LstCrcChangesBrowser(
    private val project: Project,
    private val propertiesComponent: PropertiesComponent,
    private val targetBranchToCompare: String,
    parentDisposable: Disposable
) : SimpleAsyncChangesBrowser(project, false, true), Disposable, ChangeListListener, GitRepositoryChangeListener {

    private val logger = thisLogger()
    private var refreshDebounceTimer: Timer? = null

    private class ClickState {
        var timer: Timer? = null
        var pendingChange: Change? = null
        var pendingPath: javax.swing.tree.TreePath? = null
        var actionHasFiredForPath: javax.swing.tree.TreePath? = null

        fun clear() {
            timer?.stop()
            timer = null
            pendingChange = null
            pendingPath = null
            actionHasFiredForPath = null
        }
    }
    private val leftClickState = ClickState()
    private val middleClickState = ClickState()
    private val rightClickState = ClickState()

    init {
        viewer.emptyText.text = "Loading..."
        project.messageBus.connect(this).subscribe(GitRepository.GIT_REPO_CHANGE, this)
        ChangeListManager.getInstance(project).addChangeListListener(this, this)
        com.intellij.openapi.util.Disposer.register(parentDisposable, this)

        // --- DISABLE DEFAULT HANDLERS ---
        // 1. Remove the default double-click/enter key behavior.
        viewer.setDoubleClickAndEnterKeyHandler {}
        // 2. The default popup menu is disabled by overriding createPopupMenuActions() below.

        // --- INSTALL OUR MASTER LISTENER ---
        // This single listener now has full control over all mouse clicks.
        viewer.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                // Restore the robust "click anywhere on the row" logic.
                val row = viewer.getClosestRowForLocation(e.x, e.y)
                if (row == -1) return
                val bounds = viewer.getRowBounds(row)
                if (bounds == null || e.y < bounds.y || e.y >= bounds.y + bounds.height) {
                    return // Click was outside the actual row bounds.
                }

                val path = viewer.getPathForRow(row) ?: return
                val change = (path.lastPathComponent as? ChangesBrowserNode<*>)?.userObject as? Change ?: return

                when {
                    SwingUtilities.isLeftMouseButton(e) -> {
                        middleClickState.clear()
                        rightClickState.clear()
                        handleGenericClick(e, change, path, getSingleClickAction(), getDoubleClickAction(), leftClickState)
                    }
                    SwingUtilities.isMiddleMouseButton(e) -> {
                        leftClickState.clear()
                        rightClickState.clear()
                        handleGenericClick(e, change, path, getMiddleClickAction(), getDoubleMiddleClickAction(), middleClickState)
                    }
                    SwingUtilities.isRightMouseButton(e) -> {
                        leftClickState.clear()
                        middleClickState.clear()
                        handleGenericClick(e, change, path, getRightClickAction(), getDoubleRightClickAction(), rightClickState)
                    }
                }
            }
        })
    }

    /**
     * Override and return an empty list to completely disable the default right-click context menu.
     * This gives our custom MouseListener full control over right-click events.
     */
    override fun createPopupMenuActions(): MutableList<AnAction> {
        return mutableListOf()
    }

    private fun handleGenericClick(
        e: MouseEvent,
        change: Change,
        path: javax.swing.tree.TreePath,
        singleClickAction: String,
        doubleClickAction: String,
        clickState: ClickState
    ) {
        if (viewer.selectionPath != path) {
            viewer.selectionPath = path
        }
        viewer.requestFocusInWindow()

        if (doubleClickAction == "NONE") {
            clickState.clear()
            if (e.clickCount == 1 && singleClickAction != "NONE") {
                performConfiguredAction(change, singleClickAction)
            }
            return
        }

        if (e.clickCount == 1) {
            if (clickState.pendingPath != path || clickState.actionHasFiredForPath != null) {
                clickState.clear()
            }
            clickState.pendingChange = change
            clickState.pendingPath = path
            clickState.timer?.stop()
            clickState.timer = Timer(getUserDoubleClickDelayMs()) {
                val sChange = clickState.pendingChange
                val sPath = clickState.pendingPath
                clickState.clear()

                if (sChange != null && sPath != null && singleClickAction != "NONE") {
                    performConfiguredAction(sChange, singleClickAction)
                    clickState.actionHasFiredForPath = sPath
                }
            }.apply { isRepeats = false; start() }
        } else if (e.clickCount >= 2) {
            if (clickState.actionHasFiredForPath == path) {
                clickState.actionHasFiredForPath = null
                clickState.timer?.stop()
                return
            }
            if (clickState.pendingPath == path) {
                clickState.clear()
            }
            if (doubleClickAction != "NONE") {
                performConfiguredAction(change, doubleClickAction)
            }
            clickState.actionHasFiredForPath = null
        }
    }

    private fun performConfiguredAction(change: Change, actionType: String) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            when (actionType) {
                "OPEN_DIFF" -> openDiff(change)
                "OPEN_SOURCE" -> openSource(change)
            }
        }
    }

    private fun openDiff(change: Change) {
        ShowDiffAction.showDiffForChange(project, listOf(change))
    }

    private fun openSource(change: Change) {
        val fileToOpen: VirtualFile? = when (change.type) {
            Change.Type.DELETED -> change.beforeRevision?.file?.virtualFile
            else -> change.afterRevision?.file?.virtualFile ?: change.beforeRevision?.file?.virtualFile
        }
        if (fileToOpen != null && fileToOpen.isValid && !fileToOpen.isDirectory) {
            OpenFileDescriptor(project, fileToOpen).navigate(true)
        } else {
            val pathForMessage = (change.afterRevision?.file ?: change.beforeRevision?.file)?.path ?: "Unknown path"
            Messages.showWarningDialog(project, "Could not open source file (it may no longer exist, is not accessible, or is a directory): $pathForMessage", "Open Source Error")
        }
    }

    // --- Public API for updating the browser ---

    fun showLoadingStateAndPrepareForData() {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            viewer.emptyText.text = "Loading..."
            setChangesToDisplay(emptyList())
        }
    }

    fun displayChanges(categorizedChanges: CategorizedChanges?, forBranchName: String) {
        if (forBranchName != targetBranchToCompare) {
            return
        }
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            if (categorizedChanges == null) {
                viewer.emptyText.text = "Error loading changes for $forBranchName"
                setChangesToDisplay(emptyList())
            } else if (categorizedChanges.allChanges.isEmpty()) {
                viewer.emptyText.text = "No changes found for $forBranchName"
                setChangesToDisplay(emptyList())
            } else {
                viewer.emptyText.text = "No changes" // Default empty text
                setChangesToDisplay(categorizedChanges.allChanges)
            }
        }
    }

    fun requestRefreshData() {
        showLoadingStateAndPrepareForData()
        project.service<ToolWindowStateService>().refreshDataForActiveTabIfMatching(targetBranchToCompare)
    }

    // --- Listener Implementations ---

    override fun repositoryChanged(repository: GitRepository) {
        if (repository.project == this.project) {
            triggerDebouncedDataRefresh()
        }
    }

    override fun changeListChanged(changeList: com.intellij.openapi.vcs.changes.ChangeList) = triggerDebouncedDataRefresh()
    override fun changesAdded(changes: Collection<Change>, changeList: com.intellij.openapi.vcs.changes.ChangeList?) { if (changes.isNotEmpty()) triggerDebouncedDataRefresh() }
    override fun changesRemoved(changes: Collection<Change>, changeList: com.intellij.openapi.vcs.changes.ChangeList?) { if (changes.isNotEmpty()) triggerDebouncedDataRefresh() }
    override fun unchangedFileStatusChanged() = triggerDebouncedDataRefresh()

    private fun triggerDebouncedDataRefresh() {
        refreshDebounceTimer?.stop()
        refreshDebounceTimer = Timer(100, null).apply {
            addActionListener {
                ApplicationManager.getApplication().invokeLater {
                    if (!project.isDisposed) {
                        requestRefreshData()
                    }
                }
            }
            isRepeats = false
        }
        refreshDebounceTimer?.start()
    }

    // --- Disposable Implementation ---

    override fun dispose() {
        shutdown()
        refreshDebounceTimer?.stop()
        leftClickState.clear()
        middleClickState.clear()
        rightClickState.clear()
        logger.info("LstCrcChangesBrowser for branch '$targetBranchToCompare' disposed.")
    }

    // --- Settings Getters ---
    private fun getSingleClickAction(): String = propertiesComponent.getValue(ToolWindowSettingsProvider.APP_SINGLE_CLICK_ACTION_KEY, ToolWindowSettingsProvider.DEFAULT_SINGLE_CLICK_ACTION)
    private fun getDoubleClickAction(): String = propertiesComponent.getValue(ToolWindowSettingsProvider.APP_DOUBLE_CLICK_ACTION_KEY, ToolWindowSettingsProvider.DEFAULT_DOUBLE_CLICK_ACTION)
    private fun getMiddleClickAction(): String = propertiesComponent.getValue(ToolWindowSettingsProvider.APP_MIDDLE_CLICK_ACTION_KEY, ToolWindowSettingsProvider.DEFAULT_MIDDLE_CLICK_ACTION)
    private fun getDoubleMiddleClickAction(): String = propertiesComponent.getValue(ToolWindowSettingsProvider.APP_DOUBLE_MIDDLE_CLICK_ACTION_KEY, ToolWindowSettingsProvider.DEFAULT_DOUBLE_MIDDLE_CLICK_ACTION)
    private fun getRightClickAction(): String = propertiesComponent.getValue(ToolWindowSettingsProvider.APP_RIGHT_CLICK_ACTION_KEY, ToolWindowSettingsProvider.DEFAULT_RIGHT_CLICK_ACTION)
    private fun getDoubleRightClickAction(): String = propertiesComponent.getValue(ToolWindowSettingsProvider.APP_DOUBLE_RIGHT_CLICK_ACTION_KEY, ToolWindowSettingsProvider.DEFAULT_DOUBLE_RIGHT_CLICK_ACTION)

    private fun getUserDoubleClickDelayMs(): Int {
        val storedValue = propertiesComponent.getInt(ToolWindowSettingsProvider.APP_USER_DOUBLE_CLICK_DELAY_KEY, ToolWindowSettingsProvider.DELAY_OPTION_SYSTEM_DEFAULT)
        if (storedValue > 0) {
            return storedValue
        }
        val systemValue = UIManager.get("Tree.doubleClickTimeout") as? Int
        return systemValue?.takeIf { it > 0 } ?: 300
    }
}
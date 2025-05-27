package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.services.GitService
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.*
import com.intellij.ide.util.PropertiesComponent // Application-level PropertiesComponent
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.ui.JBColor
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import java.awt.*
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.Timer
// import javax.swing.DefaultListModel // No longer needed
// import java.awt.event.MouseAdapter // Already imported
// import javax.swing.event.DocumentListener // Already exists
// import javax.swing.event.DocumentEvent // Already exists
// com.intellij.ui.treeStructure.Tree is already imported
// javax.swing.tree.DefaultMutableTreeNode is already imported
// javax.swing.tree.DefaultTreeModel is already imported
// com.intellij.util.ui.tree.TreeUtil is already imported


class GitChangesToolWindow(private val project: Project) { // project is still needed for project-specific services like GitService
    private val gitService = project.service<GitService>()
    // Use application-level PropertiesComponent for settings
    private val propertiesComponent = PropertiesComponent.getInstance()

    private var singleClickTimer: Timer? = null
    private var pendingSingleClickChange: Change? = null
    private var pendingSingleClickNodePath: javax.swing.tree.TreePath? = null
    private var singleClickActionHasFiredForPendingPath: Boolean = false
    private var singleClickActionHasFiredForPath: javax.swing.tree.TreePath? = null

    companion object {
        // New Preference Keys
        private const val APP_SINGLE_CLICK_ACTION_KEY = "com.github.uiopak.lstcrc.app.singleClickAction"
        private const val APP_DOUBLE_CLICK_ACTION_KEY = "com.github.uiopak.lstcrc.app.doubleClickAction"

        // Action Values
        private const val ACTION_NONE = "NONE" // New action value
        private const val ACTION_OPEN_DIFF = "OPEN_DIFF"
        private const val ACTION_OPEN_SOURCE = "OPEN_SOURCE"

        // Defaults
        private const val DEFAULT_SINGLE_CLICK_ACTION = ACTION_NONE
        private const val DEFAULT_DOUBLE_CLICK_ACTION = ACTION_OPEN_DIFF

        private const val APP_USER_DOUBLE_CLICK_DELAY_KEY = "com.github.uiopak.lstcrc.app.userDoubleClickDelay"
        private const val DELAY_OPTION_SYSTEM_DEFAULT = -1 // Special value to signify using system/default logic
        private const val DEFAULT_USER_DELAY_MS = 300 // Fallback if system is 0 and user hasn't set one
    }

    private fun getSingleClickAction(): String =
        propertiesComponent.getValue(APP_SINGLE_CLICK_ACTION_KEY, DEFAULT_SINGLE_CLICK_ACTION)

    private fun setSingleClickAction(action: String) =
        propertiesComponent.setValue(APP_SINGLE_CLICK_ACTION_KEY, action)

    private fun getDoubleClickAction(): String =
        propertiesComponent.getValue(APP_DOUBLE_CLICK_ACTION_KEY, DEFAULT_DOUBLE_CLICK_ACTION)

    private fun setDoubleClickAction(action: String) =
        propertiesComponent.setValue(APP_DOUBLE_CLICK_ACTION_KEY, action)

    private fun getUserDoubleClickDelayMs(): Int {
        val storedValue = propertiesComponent.getInt(APP_USER_DOUBLE_CLICK_DELAY_KEY, DELAY_OPTION_SYSTEM_DEFAULT)
        if (storedValue == DELAY_OPTION_SYSTEM_DEFAULT) {
            val systemTimeout = UIManager.getInt("Tree.doubleClickTimeout")
            return if (systemTimeout > 0) systemTimeout else DEFAULT_USER_DELAY_MS
        }
        return if (storedValue > 0) storedValue else DEFAULT_USER_DELAY_MS // Ensure it's positive
    }

    private fun setUserDoubleClickDelayMs(delay: Int) { // delay = -1 means use system default
        propertiesComponent.setValue(APP_USER_DOUBLE_CLICK_DELAY_KEY, delay, DELAY_OPTION_SYSTEM_DEFAULT)
    }


    fun createBranchContentView(branchName: String): JComponent {
        val tree = createChangesTree(branchName) // Pass branchName for initial refresh
        val scrollPane = JBScrollPane(tree)
        scrollPane.border = null // <--- ADD THIS LINE
        return scrollPane
    }

    // This method creates the "Git Changes View Options" entry for the gear menu,
    // which itself will expand into further options.
    fun createToolWindowSettingsGroup(): ActionGroup {
        val rootSettingsGroup = DefaultActionGroup("Git Changes View Options", true) // isPopup = true

        // --- Action on Single Click SubGroup ---
        val singleClickActionGroup = DefaultActionGroup("Action on Single Click:", true)
        singleClickActionGroup.add(object : ToggleAction("None") {
            override fun isSelected(ev: AnActionEvent) = getSingleClickAction() == ACTION_NONE
            override fun setSelected(ev: AnActionEvent, state: Boolean) {
                if (state) setSingleClickAction(ACTION_NONE)
            }
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
        })
        singleClickActionGroup.add(object : ToggleAction("Show Diff") {
            override fun isSelected(ev: AnActionEvent) = getSingleClickAction() == ACTION_OPEN_DIFF
            override fun setSelected(ev: AnActionEvent, state: Boolean) {
                if (state) setSingleClickAction(ACTION_OPEN_DIFF)
            }
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
        })
        singleClickActionGroup.add(object : ToggleAction("Show Source File") {
            override fun isSelected(ev: AnActionEvent) = getSingleClickAction() == ACTION_OPEN_SOURCE
            override fun setSelected(ev: AnActionEvent, state: Boolean) {
                if (state) setSingleClickAction(ACTION_OPEN_SOURCE)
            }
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
        })
        rootSettingsGroup.add(singleClickActionGroup)
        rootSettingsGroup.addSeparator()

        // --- Action on Double Click SubGroup ---
        val doubleClickActionGroup = DefaultActionGroup("Action on Double Click:", true)
        doubleClickActionGroup.add(object : ToggleAction("None") {
            override fun isSelected(ev: AnActionEvent) = getDoubleClickAction() == ACTION_NONE
            override fun setSelected(ev: AnActionEvent, state: Boolean) {
                if (state) setDoubleClickAction(ACTION_NONE)
            }
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
        })
        doubleClickActionGroup.add(object : ToggleAction("Show Diff") {
            override fun isSelected(ev: AnActionEvent) = getDoubleClickAction() == ACTION_OPEN_DIFF
            override fun setSelected(ev: AnActionEvent, state: Boolean) {
                if (state) setDoubleClickAction(ACTION_OPEN_DIFF)
            }
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
        })
        doubleClickActionGroup.add(object : ToggleAction("Show Source File") {
            override fun isSelected(ev: AnActionEvent) = getDoubleClickAction() == ACTION_OPEN_SOURCE
            override fun setSelected(ev: AnActionEvent, state: Boolean) {
                if (state) setDoubleClickAction(ACTION_OPEN_SOURCE)
            }
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
        })
        rootSettingsGroup.add(doubleClickActionGroup)

        rootSettingsGroup.addSeparator()
        val delaySpeedGroup = DefaultActionGroup("Double-Click Speed:", true)

        // Option to use system default (or our fallback if system is 0)
        delaySpeedGroup.add(object : ToggleAction("Default") {
            override fun isSelected(e: AnActionEvent) = propertiesComponent.getInt(APP_USER_DOUBLE_CLICK_DELAY_KEY, DELAY_OPTION_SYSTEM_DEFAULT) == DELAY_OPTION_SYSTEM_DEFAULT
            override fun setSelected(e: AnActionEvent, state: Boolean) {
                if (state) setUserDoubleClickDelayMs(DELAY_OPTION_SYSTEM_DEFAULT)
            }
            override fun getActionUpdateThread() = ActionUpdateThread.BGT
        })

        val predefinedDelays = listOf(Pair("Faster (200ms)", 200),Pair("Fast (250ms)", 250), Pair("Medium (300ms)", 300), Pair("Slow (500ms)", 500))
        predefinedDelays.forEach { (label, value) ->
            delaySpeedGroup.add(object : ToggleAction(label) {
                override fun isSelected(e: AnActionEvent) = propertiesComponent.getInt(APP_USER_DOUBLE_CLICK_DELAY_KEY, DELAY_OPTION_SYSTEM_DEFAULT) == value
                override fun setSelected(e: AnActionEvent, state: Boolean) {
                    if (state) setUserDoubleClickDelayMs(value)
                }
                override fun getActionUpdateThread() = ActionUpdateThread.BGT
            })
        }
        rootSettingsGroup.add(delaySpeedGroup)

        return rootSettingsGroup
    }


    private fun createChangesTree(branchNameForInitialRefresh: String): Tree {
        val root = DefaultMutableTreeNode("Changes")
        val treeModel = DefaultTreeModel(root)
        val tree = object : Tree(treeModel) {
            override fun getScrollableTracksViewportWidth(): Boolean = true
        }

        tree.setCellRenderer(object : ColoredTreeCellRenderer() {
            override fun customizeCellRenderer(
                jTree: javax.swing.JTree, value: Any?, selected: Boolean, expanded: Boolean,
                leaf: Boolean, row: Int, hasFocus: Boolean
            ) {
                if (value !is DefaultMutableTreeNode) {
                    append(value?.toString() ?: ""); return
                }
                val userObject = value.userObject
                when (userObject) {
                    is Change -> {
                        val change = userObject
                        val filePath = change.afterRevision?.file ?: change.beforeRevision?.file
                        val fileName = filePath?.name ?: "Unknown File"
                        val fgColor = if (selected) UIManager.getColor("Tree.selectionForeground")
                        else when (change.type) {
                            Change.Type.NEW -> JBColor.namedColor("VersionControl.FileStatus.Added", JBColor.GREEN)
                            Change.Type.DELETED -> JBColor.namedColor("VersionControl.FileStatus.Deleted", JBColor.RED)
                            Change.Type.MOVED, Change.Type.MODIFICATION -> JBColor.namedColor("VersionControl.FileStatus.Modified", JBColor.BLUE)
                            else -> UIManager.getColor("Tree.foreground")
                        } ?: UIManager.getColor("Tree.foreground")
                        append(fileName, SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, fgColor))
                        var fileIcon: Icon? = null
                        if (filePath != null) {
                            filePath.virtualFile?.let { vf -> fileIcon = vf.fileType.icon }
                            if (fileIcon == null) fileIcon = FileTypeManager.getInstance().getFileTypeByFileName(filePath.name).icon
                        }
                        icon = fileIcon ?: AllIcons.FileTypes.Unknown
                    }
                    is String -> { append(userObject, SimpleTextAttributes.REGULAR_ATTRIBUTES); icon = AllIcons.Nodes.Folder }
                    else -> append(value.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES)
                }
            }
        })

        tree.addMouseListener(object : MouseAdapter() {
            private fun logState(event: String, e: MouseEvent?, currentPath: javax.swing.tree.TreePath? = null) {
                val time = System.currentTimeMillis() % 100000
                val clickInfo = e?.let { "ClickCount=${it.clickCount} @(${it.x},${it.y})" } ?: "N/A"
                val currentPathStr = currentPath?.lastPathComponent?.toString()?.takeLast(30) ?: "null"
                val pendingPathStr = pendingSingleClickNodePath?.lastPathComponent?.toString()?.takeLast(30) ?: "null"
                val firedPathStr = singleClickActionHasFiredForPath?.lastPathComponent?.toString()?.takeLast(30) ?: "null" // Log new state
                val timerRunning = singleClickTimer?.isRunning ?: false

                println(String.format("[%05d] %-30s | %-20s | Path: %-30s | PendPath: %-30s | FiredPath: %-30s | TimerRun: %s",
                    time, event, clickInfo, currentPathStr, pendingPathStr, firedPathStr, timerRunning))
            }

            override fun mouseClicked(e: MouseEvent) {
                logState("mouseClicked ENTER", e)
                val clickPoint = e.point
                var currentTargetPath: javax.swing.tree.TreePath? = null
                // ... (code to get currentTargetPath, same as before) ...
                val row = tree.getClosestRowForLocation(clickPoint.x, clickPoint.y)
                if (row != -1) {
                    val contentBoundsForRow = tree.getRowBounds(row)
                    if (contentBoundsForRow != null) {
                        val yWithinRowContent = clickPoint.y >= contentBoundsForRow.y && clickPoint.y < (contentBoundsForRow.y + contentBoundsForRow.height)
                        val xWithinTreeVisible = clickPoint.x >= tree.visibleRect.x && clickPoint.x < (tree.visibleRect.x + tree.visibleRect.width)
                        if (yWithinRowContent && xWithinTreeVisible) {
                            currentTargetPath = tree.getPathForRow(row)
                        }
                    }
                }
                logState("Path Determined", e, currentTargetPath)


                if (currentTargetPath == null) {
                    logState("No Valid Target Path", e)
                    singleClickTimer?.stop()
                    pendingSingleClickChange = null
                    pendingSingleClickNodePath = null
                    singleClickActionHasFiredForPath = null // Reset new flag
                    return
                }

                val node = currentTargetPath.lastPathComponent as? DefaultMutableTreeNode
                (node?.userObject as? Change)?.let { currentChange ->
                    logState("Processing Change Node", e, currentTargetPath)
                    if (tree.selectionPath != currentTargetPath) {
                        tree.selectionPath = currentTargetPath
                        logState("  Node Selected", e, currentTargetPath)
                    }

                    val singleClickConfiguredAction = getSingleClickAction()
                    val doubleClickConfiguredAction = getDoubleClickAction()

                    if (e.clickCount == 1) {
                        logState("Click Count == 1", e, currentTargetPath)

                        // If this click is on a new path OR the previous single click action has already fired for *any* path,
                        // it's a fresh start for a single-click sequence.
                        if (pendingSingleClickNodePath != currentTargetPath || singleClickActionHasFiredForPath != null) {
                            logState("  Resetting for new single click seq", e, currentTargetPath)
                            singleClickTimer?.stop()
                            pendingSingleClickChange = null
                            pendingSingleClickNodePath = null
                            singleClickActionHasFiredForPath = null // Key reset
                        }


                        if (doubleClickConfiguredAction == ACTION_NONE) {
                            // ... (Optimization logic - no change needed here, but ensure it also clears singleClickActionHasFiredForPath)
                            logState("  Optimization: No DblClick", e, currentTargetPath)
                            singleClickTimer?.stop()
                            pendingSingleClickChange = null
                            pendingSingleClickNodePath = null
                            singleClickActionHasFiredForPath = null // Reset

                            if (singleClickConfiguredAction != ACTION_NONE) {
                                logState("    Executing Single (Optimized)", e, currentTargetPath)
                                performConfiguredAction(currentChange, singleClickConfiguredAction)
                            }
                            return@let
                        }

                        logState("  Setting up Timer", e, currentTargetPath)
                        pendingSingleClickChange = currentChange
                        pendingSingleClickNodePath = currentTargetPath
                        // singleClickActionHasFiredForPath is already null here due to above reset or initial state

                        singleClickTimer?.stop()
                        val userConfiguredDelay = getUserDoubleClickDelayMs()

                        singleClickTimer = Timer(userConfiguredDelay) {
                            logState("TIMER ACTION LISTENER FIRED", null, pendingSingleClickNodePath) // Log with the path active when timer was created
                            val sChange = pendingSingleClickChange
                            val sPath = pendingSingleClickNodePath // This is the path this timer was for

                            pendingSingleClickChange = null // Clear pending for future clicks
                            pendingSingleClickNodePath = null // Clear pending for future clicks

                            if (sChange != null && sPath != null) { // sPath should always be non-null if we got here
                                if (singleClickConfiguredAction != ACTION_NONE) {
                                    logState("    TIMER: Performing SingleClick", null, sPath)
                                    performConfiguredAction(sChange, singleClickConfiguredAction)
                                    singleClickActionHasFiredForPath = sPath // SET FLAG with the path
                                    logState("    TIMER: FiredPath SET", null, sPath)
                                } else { /* ... */ }
                            } else { /* ... */ }
                        }
                        singleClickTimer?.isRepeats = false
                        singleClickTimer?.start()
                        logState("  Timer Started", e, currentTargetPath)

                    } else if (e.clickCount >= 2) {
                        logState("Click Count >= 2", e, currentTargetPath)

                        // If a single-click action has ALREADY fired for THIS specific currentTargetPath,
                        // then this "second click" should NOT trigger a double-click action.
                        if (singleClickActionHasFiredForPath == currentTargetPath) {
                            logState("  DblClick: IGNORED (Single Fired for this path)", e, currentTargetPath)
                            singleClickActionHasFiredForPath = null // Reset for next sequence
                            // No need to clear pendingSingleClickNodePath/Change as they were cleared by the timer
                            // or weren't relevant to this "fired" state.
                            // Timer should also be stopped or have fired.
                            singleClickTimer?.stop() // Stop just in case
                            return@let
                        }

                        // If not ignored, proceed to cancel any PENDING single-click timer for this path
                        if (pendingSingleClickNodePath == currentTargetPath) {
                            logState("  DblClick: Cancelling PENDING Timer", e, currentTargetPath)
                            singleClickTimer?.stop()
                            pendingSingleClickChange = null
                            pendingSingleClickNodePath = null
                            // singleClickActionHasFiredForPath should be null here, as the timer hasn't fired yet.
                            logState("    Pending Single Cleared (timer cancelled)", e)
                        } else {
                            logState("  DblClick: Path different or no PENDING single", e, currentTargetPath)
                        }

                        if (doubleClickConfiguredAction != ACTION_NONE) {
                            logState("  DblClick: Performing Action", e, currentTargetPath)
                            performConfiguredAction(currentChange, doubleClickConfiguredAction)
                        } else { /* ... */ }

                        // After any double click (performed or not, unless ignored due to prior single),
                        // ensure any "fired" state is reset for future interactions.
                        singleClickActionHasFiredForPath = null
                        logState("  DblClick: FiredPath Reset (post-action/ignore)", e, currentTargetPath)
                    }
                } ?: run { // Click was on non-Change node or outside
                    logState("Non-Change Node or Invalid Path", e, currentTargetPath)
                    singleClickTimer?.stop()
                    pendingSingleClickChange = null
                    pendingSingleClickNodePath = null
                    singleClickActionHasFiredForPath = null // Reset
                    logState("  State Reset", e)
                }
                logState("mouseClicked EXIT", e, currentTargetPath)
            }
        })
        refreshChangesTree(tree, branchNameForInitialRefresh)
        return tree
    }

    private fun performConfiguredAction(change: Change, actionType: String) {
        when (actionType) {
            ACTION_OPEN_DIFF -> openDiff(change)
            ACTION_OPEN_SOURCE -> openSource(change)
            ACTION_NONE -> { /* Do nothing */ }
            // else -> Optional: log an unknown actionType
        }
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
            if (fileToOpen?.isDirectory == true) {
                Messages.showWarningDialog(project, "Cannot open a directory as source. Please select a file.", "Open Source Error")
            } else {
                Messages.showWarningDialog(project, "Could not open source file (it may no longer exist or is not accessible): $pathForMessage", "Open Source Error")
            }
        }
    }

    private fun refreshChangesTree(tree: javax.swing.JTree, branchName: String) {
        val changes = gitService.getChanges(branchName)
        val rootModelNode = tree.model.root as DefaultMutableTreeNode
        rootModelNode.removeAllChildren()
        buildTreeFromChanges(rootModelNode, changes)
        (tree.model as DefaultTreeModel).reload(rootModelNode)
        TreeUtil.expandAll(tree)
    }

    private fun buildTreeFromChanges(rootNode: DefaultMutableTreeNode, changes: List<Change>) {
        val repositoryRoot = gitService.getCurrentRepository()?.root
        for (changeItem in changes) {
            val currentFilePathObj = changeItem.afterRevision?.file ?: changeItem.beforeRevision?.file
            val rawPath = currentFilePathObj?.path
            var displayPath: String? = null
            if (currentFilePathObj != null && repositoryRoot != null) {
                val vf = currentFilePathObj.virtualFile
                if (vf != null) {
                    displayPath = VfsUtilCore.getRelativePath(vf, repositoryRoot, '/')
                } else if (rawPath != null) {
                    val repoRootPathString = repositoryRoot.path
                    if (rawPath.startsWith(repoRootPathString + "/")) {
                        displayPath = rawPath.substring(repoRootPathString.length + 1)
                    } else if (rawPath.startsWith(repoRootPathString)) {
                        displayPath = rawPath.substring(repoRootPathString.length).let { if (it.startsWith("/")) it.substring(1) else it }
                    } else {
                        displayPath = rawPath
                    }
                }
            }
            if (displayPath == null) displayPath = rawPath
            if (displayPath == null) continue
            val normalizedPath = displayPath.replace('\\', '/')
            val pathComponents = normalizedPath.split('/').filter { it.isNotEmpty() }
            var currentNode = rootNode
            if (pathComponents.isEmpty() && currentFilePathObj != null) {
                var fileNodeExists = false
                for (j in 0 until currentNode.childCount) {
                    val existingChild = currentNode.getChildAt(j) as DefaultMutableTreeNode
                    if (existingChild.userObject is Change) {
                        val existingChange = existingChild.userObject as Change
                        val existingChangePath = existingChange.afterRevision?.file?.path ?: existingChange.beforeRevision?.file?.path
                        if (existingChangePath == rawPath) {
                            fileNodeExists = true; break
                        }
                    }
                }
                if (!fileNodeExists) currentNode.add(DefaultMutableTreeNode(changeItem))
                continue
            }
            for (i in pathComponents.indices) {
                val componentName = pathComponents[i]
                val isLastComponent = i == pathComponents.size - 1
                var childNode: DefaultMutableTreeNode? = null
                for (j in 0 until currentNode.childCount) {
                    val existingChild = currentNode.getChildAt(j) as DefaultMutableTreeNode
                    if (isLastComponent && existingChild.userObject is Change) {
                        val existingChange = existingChild.userObject as Change
                        val existingChangeOriginalPath = existingChange.afterRevision?.file?.path ?: existingChange.beforeRevision?.file?.path
                        if (existingChangeOriginalPath == rawPath) {
                            childNode = existingChild; break
                        }
                    } else if (!isLastComponent && existingChild.userObject is String && existingChild.userObject == componentName) {
                        childNode = existingChild; break
                    }
                }
                if (childNode == null) {
                    childNode = if (isLastComponent) DefaultMutableTreeNode(changeItem) else DefaultMutableTreeNode(componentName)
                    currentNode.add(childNode)
                }
                currentNode = childNode
            }
        }
    }

    private fun openDiff(change: Change) {
        try {
            ShowDiffAction.showDiffForChange(project, listOf(change))
        } catch (e: Exception) {
            Messages.showErrorDialog(project, "Error opening diff: ${e.message}", "Error")
        }
    }

    fun showBranchSelectionDialog(onBranchSelected: (branchName: String) -> Unit) {
        val dialog = object : DialogWrapper(project, true) {
            private val searchTextField = SearchTextField()
            private var listPopup: JBPopup? = null
            private val allBranches = gitService.getAllBranches().sorted()
            private val filteredListModel = DefaultListModel<String>()
            init {
                title = "Select Branch to Compare with HEAD"
                init()
                searchTextField.addDocumentListener(object : DocumentListener {
                    override fun insertUpdate(e: DocumentEvent?) = filterAndShowPopup()
                    override fun removeUpdate(e: DocumentEvent?) = filterAndShowPopup()
                    override fun changedUpdate(e: DocumentEvent?) = filterAndShowPopup()
                })
                SwingUtilities.invokeLater { filterAndShowPopup() }
            }
            private fun filterAndShowPopup() {
                val searchText = searchTextField.text.trim()
                filteredListModel.clear()
                val sourceList = if (searchText.isEmpty()) allBranches else allBranches.filter { it.contains(searchText, ignoreCase = true) }
                sourceList.forEach { filteredListModel.addElement(it) }
                if (listPopup?.isDisposed == false) listPopup?.cancel()
                if (filteredListModel.isEmpty && searchText.isNotEmpty()) return
                if (filteredListModel.size > 0) {
                    val jbList = JBList(filteredListModel)
                    jbList.visibleRowCount = JBUI.scale(10).coerceAtMost(filteredListModel.size).coerceAtLeast(1)
                    listPopup = JBPopupFactory.getInstance().createListPopupBuilder(jbList)
                        .setMovable(false).setResizable(false).setRequestFocus(false)
                        .setItemChoosenCallback { jbList.selectedValue?.let { onBranchSelected(it); close(OK_EXIT_CODE) } }
                        .createPopup()
                    jbList.addKeyListener(object : KeyAdapter() {
                        override fun keyPressed(e: KeyEvent) {
                            if (e.keyCode == KeyEvent.VK_ENTER) {
                                jbList.selectedValue?.let { onBranchSelected(it); close(OK_EXIT_CODE); e.consume() }
                            }
                        }
                    })
                    if (searchTextField.isShowing) listPopup?.showUnderneathOf(searchTextField)
                }
            }
            override fun createCenterPanel(): JComponent {
                val panel = JBPanel<JBPanel<*>>(BorderLayout(0, JBUI.scale(5)))
                panel.add(JBLabel("Search for branch to compare with current HEAD:"), BorderLayout.NORTH)
                panel.add(searchTextField, BorderLayout.CENTER)
                panel.preferredSize = JBUI.size(450, 60)
                return panel
            }
            override fun getPreferredFocusedComponent(): JComponent? = searchTextField
            override fun doOKAction() {
                if (listPopup?.isVisible == true) {
                    (listPopup?.content as? JBList<*>)?.selectedValue?.let { onBranchSelected(it as String); super.doOKAction(); return }
                }
                if (!filteredListModel.isEmpty) {
                    onBranchSelected(filteredListModel.getElementAt(0)); super.doOKAction()
                } else {
                    if (searchTextField.text.isNotBlank() && filteredListModel.isEmpty) {
                        Messages.showWarningDialog(project, "No branch found matching '${searchTextField.text}'.", "Branch Not Found")
                        return
                    }
                    super.doCancelAction()
                }
            }
            override fun dispose() { listPopup?.cancel(); super.dispose() }
        }
        dialog.show()
    }

    fun createBranchSelectionView(onBranchSelected: (branchName: String) -> Unit): JComponent {
        val panel = JBPanel<JBPanel<*>>(BorderLayout(0, JBUI.scale(5)))
        val searchTextField = SearchTextField(false)
        panel.add(searchTextField, BorderLayout.NORTH)

        val tree = object : Tree() { // Anonymous subclass of com.intellij.ui.treeStructure.Tree
            override fun getScrollableTracksViewportWidth(): Boolean = true
        }
        tree.isRootVisible = false
        tree.showsRootHandles = true // Shows handles for top-level nodes if root is invisible

        tree.setCellRenderer(object : ColoredTreeCellRenderer() {
            override fun customizeCellRenderer(
                jTree: JTree, // javax.swing.JTree
                value: Any?,
                selected: Boolean,
                expanded: Boolean,
                leaf: Boolean,
                row: Int,
                hasFocus: Boolean
            ) {
                if (value !is DefaultMutableTreeNode) {
                    append(value?.toString() ?: "")
                    return
                }

                val userObject = value.userObject
                when (userObject) {
                    "Local" -> {
                        append(userObject)
                        icon = AllIcons.Nodes.Folder // Or a more specific icon for local branches
                    }
                    "Remote" -> {
                        append(userObject)
                        icon = AllIcons.Nodes.WebFolder // Or a more specific icon for remote branches
                    }
                    is String -> { // This should be a branch name
                        append(userObject)
                        // Optionally, set a specific icon for branch nodes if desired
                        icon = AllIcons.Vcs.Branch 
                    }
                    else -> {
                        append(value.toString())
                    }
                }
            }
        })

        fun buildBranchTreeModel(searchTerm: String): DefaultTreeModel {
            val rootNode = DefaultMutableTreeNode("Root")
            val localBranchesNode = DefaultMutableTreeNode("Local")
            val remoteBranchesNode = DefaultMutableTreeNode("Remote")

            val localBranches = gitService.getLocalBranches().sorted()
            val remoteBranches = gitService.getRemoteBranches().sorted()

            localBranches.filter { it.contains(searchTerm, ignoreCase = true) }
                .forEach { localBranchesNode.add(DefaultMutableTreeNode(it)) }

            remoteBranches.filter { it.contains(searchTerm, ignoreCase = true) }
                .forEach { remoteBranchesNode.add(DefaultMutableTreeNode(it)) }
            
            if (localBranchesNode.childCount > 0) {
                rootNode.add(localBranchesNode)
            }
            if (remoteBranchesNode.childCount > 0) {
                rootNode.add(remoteBranchesNode)
            }
            
            return DefaultTreeModel(rootNode)
        }

        tree.model = buildBranchTreeModel("")
        TreeUtil.expandAll(tree)


        searchTextField.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) { filterTree() }
            override fun removeUpdate(e: DocumentEvent?) { filterTree() }
            override fun changedUpdate(e: DocumentEvent?) { filterTree() }

            private fun filterTree() {
                val searchTerm = searchTextField.text.trim()
                tree.model = buildBranchTreeModel(searchTerm)
                // The TreeUtil.expandAll(tree) needs to be called AFTER the model is updated.
                // If nodes are removed and re-added, their expansion state might be lost.
                // We might need to selectively expand if performance is an issue,
                // but for now, expanding all after model update is simplest.
                TreeUtil.expandAll(tree)
            }
        })

        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 1) { // Only process single clicks
                    val row = tree.getRowForLocation(e.x, e.y) // Get the row at the click coordinates
                    if (row != -1) { // Check if a valid row was clicked (not outside any row)
                        val path = tree.getPathForRow(row) // Get the TreePath for this row
                        if (path != null) {
                            val node = path.lastPathComponent as? DefaultMutableTreeNode // Get the node for this path
                            // Check if the node is a leaf (actual item) and not null
                            if (node != null && node.isLeaf) {
                                // Ensure this leaf node is a branch by checking its parent's userObject
                                val parentNode = node.parent as? DefaultMutableTreeNode
                                if (parentNode != null && (parentNode.userObject == "Local" || parentNode.userObject == "Remote")) {
                                    // If it's a valid branch node, get its name and call the callback
                                    (node.userObject as? String)?.let { branchName ->
                                        onBranchSelected(branchName)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        })

        val scrollPane = JBScrollPane(tree)
        panel.add(scrollPane, BorderLayout.CENTER)

        return panel
    }
}
package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.ui.PopupHandler
import com.intellij.util.ui.tree.TreeUtil
import java.awt.Component
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities
import javax.swing.tree.TreePath
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** An action on changes in the tree: the value that selects it in the click settings and its menu title. */
internal data class BrowserChangeActionDefinition(
    val settingValue: String,
    val titleKey: String,
    val isEnabled: (List<Change>) -> Boolean = { it.isNotEmpty() },
    val action: (List<Change>) -> Unit
)

/**
 * Mouse, Enter-key and context-menu handling of a changes tree. Clicks run the actions configured in the settings
 * on the clicked change; when a button also has a double-click action, the single-click action waits for the
 * double-click delay first. Enter opens the diff ([openDiff]) of the selected changes.
 */
internal class ChangesTreeClickHandler(
    private val project: Project,
    private val tree: ChangesTree,
    private val actions: List<BrowserChangeActionDefinition>,
    private val openDiff: (List<Change>) -> Unit
) : Disposable {

    private val logger = thisLogger()
    private val clickScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pendingClickJob: Job? = null

    private val selectedChanges: List<Change>
        get() = VcsTreeModelData.selected(tree).userObjects(Change::class.java)

    fun install() {
        tree.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) {
                    val changes = selectedChanges
                    if (changes.isNotEmpty()) {
                        openDiff(changes)
                        e.consume()
                    }
                }
            }
        })

        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) = handleMouseClick(e)
        })
        installContextMenuHandler()
    }

    override fun dispose() {
        pendingClickJob?.cancel()
        clickScope.cancel()
    }

    fun configuredActionForButton(button: Int, doubleClick: Boolean): String = when (button) {
        MouseEvent.BUTTON1 -> if (doubleClick) ToolWindowSettingsProvider.getDoubleClickAction() else ToolWindowSettingsProvider.getSingleClickAction()
        MouseEvent.BUTTON2 -> if (doubleClick) ToolWindowSettingsProvider.getDoubleMiddleClickAction() else ToolWindowSettingsProvider.getMiddleClickAction()
        MouseEvent.BUTTON3 -> if (doubleClick) ToolWindowSettingsProvider.getDoubleRightClickAction() else ToolWindowSettingsProvider.getRightClickAction()
        else -> ToolWindowSettingsProvider.ACTION_NONE
    }

    private fun handleMouseClick(e: MouseEvent) {
        val clickCount = e.clickCount
        val button = e.button

        if (SwingUtilities.isRightMouseButton(e) && ToolWindowSettingsProvider.isContextMenuEnabled()) {
            return
        }

        val path = TreeUtil.getPathForLocation(tree, e.x, e.y) ?: return
        val change = changeAt(path) ?: return

        selectPathAndFocus(path)

        val singleAction = configuredActionForButton(button, doubleClick = false)
        val doubleAction = configuredActionForButton(button, doubleClick = true)

        if (clickCount == 1) {
            if (singleAction == ToolWindowSettingsProvider.ACTION_NONE) return

            // If double action is NONE, fire immediately
            if (doubleAction == ToolWindowSettingsProvider.ACTION_NONE) {
                performConfiguredActionLater(change, singleAction)
                return
            }

            // Otherwise, delay to see if a double click comes
            val delayMs = ToolWindowSettingsProvider.getUserDoubleClickDelayMs().toLong()
            pendingClickJob?.cancel()
            pendingClickJob = clickScope.launch {
                delay(delayMs.milliseconds)
                withContext(Dispatchers.EDT) {
                    if (!project.isDisposed) performConfiguredAction(change, singleAction)
                }
            }
        } else if (clickCount == 2) {
            pendingClickJob?.cancel()
            if (doubleAction != ToolWindowSettingsProvider.ACTION_NONE) {
                performConfiguredActionLater(change, doubleAction)
            }
        }
    }

    /** Runs the configured action after the current mouse event has been fully processed. */
    private fun performConfiguredActionLater(change: Change, actionType: String) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) performConfiguredAction(change, actionType)
        }
    }

    /** Must be called on the EDT. */
    private fun performConfiguredAction(change: Change, actionType: String) {
        val changes = listOf(change)
        actions.firstOrNull { it.settingValue == actionType }
            ?.takeIf { it.isEnabled(changes) }
            ?.action
            ?.invoke(changes)
    }

    private fun changeAt(path: TreePath): Change? {
        return (path.lastPathComponent as? ChangesBrowserNode<*>)?.userObject as? Change
    }

    private fun createContextMenuAction(
        definition: BrowserChangeActionDefinition
    ): AnAction {
        return object : DumbAwareAction(LstCrcBundle.message(definition.titleKey)) {
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = definition.isEnabled(selectedChanges)
            }
            override fun actionPerformed(e: AnActionEvent) = definition.action(selectedChanges)
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        }
    }

    private fun installContextMenuHandler() {
        // Remove the default empty popup handler that the base class installs.
        tree.mouseListeners.filterIsInstance<PopupHandler>().forEach {
            tree.removeMouseListener(it)
            logger.debug { "Removed a default PopupHandler to prevent empty context menu." }
        }

        // Install our custom context menu handler
        tree.addMouseListener(object : PopupHandler() {
            override fun invokePopup(comp: Component?, x: Int, y: Int) {
                if (!ToolWindowSettingsProvider.isContextMenuEnabled()) return

                val path = TreeUtil.getPathForLocation(tree, x, y)?.takeIf { changeAt(it) != null } ?: return

                selectPathAndFocus(path)
                val changes = selectedChanges
                if (changes.isEmpty()) return

                val group = DefaultActionGroup(actions.filter { it.isEnabled(changes) }.map(::createContextMenuAction))

                val popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.TOOLWINDOW_POPUP, group)
                popupMenu.component.show(comp, x, y)
            }
        })
    }

    private fun selectPathAndFocus(path: TreePath) {
        if (tree.selectionPath != path) {
            tree.selectionPath = path
        }
        tree.requestFocusInWindow()
    }
}

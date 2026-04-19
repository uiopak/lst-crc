package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.LstCrcConstants
import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.github.uiopak.lstcrc.utils.LstCrcKeys
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.impl.content.BaseLabel
import com.intellij.ui.ComponentUtil
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.content.Content
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Point
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JPanel

/**
 * A context menu action (right-click on a tab) for renaming a closable comparison tab.
 * Resolves the [Content] for the right-clicked tab using [BaseLabel.getContent],
 * matching how the platform itself resolves Content from tab components.
 */
class RenameTabAction : AnAction() {

    private val logger = thisLogger()

    /**
     * Finds the [Content] associated with the right-clicked tab label by walking up
     * the component hierarchy to find a [BaseLabel], then calling its public
     * [BaseLabel.getContent] method. This mirrors the platform's own approach
     * in `ToolWindowContentUi`.
     */
    private fun findContent(source: Component?): Content? {
        if (source == null) return null
        val label = (source as? BaseLabel)
            ?: ComponentUtil.getParentOfType(BaseLabel::class.java, source)
        if (label == null) {
            logger.warn("RenameTabAction: No BaseLabel found in component hierarchy for ${source.javaClass.name}")
            return null
        }
        return label.content
    }

    override fun update(e: AnActionEvent) {
        val toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW)
        if (e.project == null || toolWindow == null || toolWindow.id != LstCrcConstants.TOOL_WINDOW_ID) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val content = findContent(e.getData(PlatformDataKeys.CONTEXT_COMPONENT))

        val isRenamable = content != null &&
                content.isCloseable &&
                content.getUserData(LstCrcKeys.BRANCH_NAME_KEY) != null

        e.presentation.isEnabledAndVisible = isRenamable
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project!!
        val component = e.getData(PlatformDataKeys.CONTEXT_COMPONENT)
        val content = findContent(component)

        if (component == null || content == null) {
            thisLogger().warn("Rename action performed without a valid component or content context.")
            return
        }

        val branchName = content.getUserData(LstCrcKeys.BRANCH_NAME_KEY)
        if (branchName == null) {
            thisLogger().warn("Cannot rename tab, it has no branch name identifier.")
            return
        }

        invokeRenamePopup(project, component, branchName)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

/**
 * Shows an inline popup with a text field to rename a tab.
 *
 * @param project The project.
 * @param owner The UI component of the tab label to which the popup will be anchored.
 * @param branchName The persistent identifier of the tab being renamed.
 */
private fun invokeRenamePopup(project: Project, owner: Component, branchName: String) {
    ApplicationManager.getApplication().invokeLater {
        val stateService = project.service<ToolWindowStateService>()
        val tabInfo = stateService.state.openTabs.find { it.branchName == branchName }
        val currentDisplayName = tabInfo?.alias ?: branchName

        val textField = JBTextField(currentDisplayName, 17)
        val titleLabel = JBLabel(LstCrcBundle.message("rename.popup.title"))

        val panel = JPanel(VerticalLayout(JBUI.scale(4), VerticalLayout.FILL)).apply {
            border = JBUI.Borders.empty(2)
            add(titleLabel)
            add(textField)
        }

        var balloon: Balloon? = null

        val onOk = {
            val newAlias = textField.text.trim().ifEmpty { null }
            if (tabInfo?.alias != newAlias) {
                stateService.updateTabAlias(branchName, newAlias)
            }
            balloon?.hide()
        }

        textField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_ENTER -> onOk()
                    KeyEvent.VK_ESCAPE -> balloon?.hide()
                }
            }
        })

        balloon = JBPopupFactory.getInstance()
            .createBalloonBuilder(panel)
            .setFillColor(UIUtil.getPanelBackground())
            .setBorderColor(JBUI.CurrentTheme.Popup.borderColor(true))
            .setAnimationCycle(0)
            .setShadow(true)
            .setCloseButtonEnabled(false)
            .setHideOnAction(false)
            .setHideOnKeyOutside(true)
            .setHideOnClickOutside(true)
            .setRequestFocus(true)
            .createBalloon()

        val point = Point(owner.width / 2, 0)
        balloon.show(RelativePoint(owner, point), Balloon.Position.above)

        UIUtil.invokeLaterIfNeeded {
            textField.requestFocusInWindow()
            textField.selectAll()
        }
    }
}
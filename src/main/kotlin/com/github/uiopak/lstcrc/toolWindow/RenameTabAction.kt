package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.github.uiopak.lstcrc.utils.LstCrcKeys
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import com.intellij.util.ReflectionUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Point
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.lang.reflect.InvocationTargetException
import javax.swing.JPanel
import javax.swing.JPopupMenu

/**
 * A context menu action (right-click on a tab) for renaming a closable comparison tab.
 * It uses a robust, multi-strategy reflective search to find the content, making it
 * work for normal, grouped, and combo-box style tabs.
 */
class RenameTabAction : AnAction() {

    private val logger = thisLogger()

    /**
     * Finds the associated Content by walking up the component hierarchy from the source component
     * and trying multiple reflection strategies using the platform's [ReflectionUtil]. This is necessary
     * to support different UI structures. The reflection attempts are logged to aid debugging
     * in case of future IDE UI changes.
     */
    private fun findContent(source: Component?): Content? {
        var component = source
        while (component != null) {
            // Strategy 1: Look for a direct 'myContent' field. Common in simple tab labels.
            ReflectionUtil.getField(component.javaClass, component, Content::class.java, "myContent")?.let {
                logger.debug { "RenameTabAction: Found content via Strategy 1 (myContent field) on ${component.javaClass.name}" }
                return it
            }

            // Strategy 2: Look for 'myInfo' (an internal TabInfo object which holds the content).
            ReflectionUtil.getField(component.javaClass, component, Any::class.java, "myInfo")?.let { tabInfo ->
                ReflectionUtil.getField(tabInfo.javaClass, tabInfo, Content::class.java, "myObject")?.let {
                    logger.debug { "RenameTabAction: Found content via Strategy 2 (myInfo.myObject field) on ${component.javaClass.name}" }
                    return it
                }
            }

            // Strategy 3: For combo-box style tabs, navigate through 'myLayout' -> 'ui' -> getContentManager().
            ReflectionUtil.getField(component.javaClass, component, Any::class.java, "myLayout")?.let { layout ->
                val uiTargetClass = if (layout.javaClass.superclass.name.endsWith("ContentLayout")) layout.javaClass.superclass else layout.javaClass
                ReflectionUtil.getField(uiTargetClass, layout, Any::class.java, "ui")?.let { contentUi ->
                    ReflectionUtil.getMethod(contentUi.javaClass, "getContentManager")?.let { method ->
                        try {
                            (method.invoke(contentUi) as? ContentManager)?.let { contentManager ->
                                // For a combo box, the selected content is the correct one.
                                logger.debug { "RenameTabAction: Found content via Strategy 3 (getContentManager) on ${component.javaClass.name}" }
                                return contentManager.selectedContent
                            }
                        } catch (e: Exception) {
                            when (e) {
                                is IllegalAccessException, is InvocationTargetException -> {
                                    logger.debug(e) { "RenameTabAction: Strategy 3 failed to invoke getContentManager on ${contentUi.javaClass.name}" }
                                }
                                else -> throw e
                            }
                        }
                    }
                }
            }

            if (component is JPopupMenu || component is java.awt.Window) break
            component = component.parent
        }
        logger.warn("RenameTabAction: Could not find Content for component: ${source?.javaClass?.name}. All strategies failed.")
        return null
    }

    override fun update(e: AnActionEvent) {
        val toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW)
        if (e.project == null || toolWindow == null || toolWindow.id != "GitChangesView") {
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

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    companion object {
        /**
         * Shows an inline popup with a text field to rename a tab.
         *
         * @param project The project.
         * @param owner The UI component of the tab label to which the popup will be anchored.
         * @param branchName The persistent identifier of the tab being renamed.
         */
        fun invokeRenamePopup(project: Project, owner: Component, branchName: String) {
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
    }
}
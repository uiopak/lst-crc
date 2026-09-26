package com.github.uiopak.lstcrc.toolWindow

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.impl.content.BaseLabel
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.ui.ComponentUtil
import com.intellij.ui.content.Content
import com.intellij.ui.content.impl.ContentManagerImpl
import java.awt.Component

/**
 * Centralizes the plugin's unavoidable dependencies on internal tool-window UI classes.
 *
 * The IntelliJ public ToolWindow API does not expose a supported way to hide or re-show the
 * tool-window ID label after creation, nor to find the tab behind a clicked tab label. Until the
 * platform exposes one, this file is the only production seam allowed to touch those internals.
 */
internal object ToolWindowUiCompatibility {

    fun setTabActions(toolWindow: ToolWindow, vararg actions: AnAction) {
        (toolWindow as? ToolWindowEx)?.setTabActions(*actions)
    }

    fun setToolWindowTitleVisible(toolWindow: ToolWindow, showTitle: Boolean) {
        toolWindow.component.putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, if (showTitle) null else "true")
        val contentManager = toolWindow.contentManager as? ContentManagerImpl
        val ui = contentManager?.ui as? ToolWindowContentUi
        ui?.update()
    }

    /** The tab whose label contains [component] (or is it), or null outside tab labels. */
    fun findTabContent(component: Component): Content? {
        val label = component as? BaseLabel ?: ComponentUtil.getParentOfType(BaseLabel::class.java, component)
        return label?.content
    }

    @Suppress("unused")
    fun isToolWindowTitleVisible(toolWindow: ToolWindow): Boolean {
        return toolWindow.component.getClientProperty(ToolWindowContentUi.HIDE_ID_LABEL) == null
    }
}

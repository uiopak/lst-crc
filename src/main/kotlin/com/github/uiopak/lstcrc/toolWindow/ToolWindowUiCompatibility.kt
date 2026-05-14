package com.github.uiopak.lstcrc.toolWindow

import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.ui.content.impl.ContentManagerImpl

/**
 * Centralizes the plugin's unavoidable dependencies on internal tool-window UI classes.
 */
internal object ToolWindowUiCompatibility {

    fun setToolWindowTitleVisible(toolWindow: ToolWindow, showTitle: Boolean) {
        toolWindow.component.putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, if (showTitle) null else "true")
        val contentManager = toolWindow.contentManager as? ContentManagerImpl
        val ui = contentManager?.ui as? ToolWindowContentUi
        ui?.update()
    }

    @Suppress("unused")
    fun isToolWindowTitleVisible(toolWindow: ToolWindow): Boolean {
        return toolWindow.component.getClientProperty(ToolWindowContentUi.HIDE_ID_LABEL) == null
    }
}

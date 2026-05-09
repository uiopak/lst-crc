package com.github.uiopak.lstcrc.toolWindow

import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.impl.content.BaseLabel
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.ui.ComponentUtil
import com.intellij.ui.content.Content
import com.intellij.ui.content.impl.ContentManagerImpl
import java.awt.Component

/**
 * Centralizes the plugin's unavoidable dependencies on internal tool-window UI classes.
 */
internal object ToolWindowUiCompatibility {

    fun findContentFromContextComponent(source: Component?): Content? {
        if (source == null) return null
        val label = (source as? BaseLabel)
            ?: ComponentUtil.getParentOfType(BaseLabel::class.java, source)
        return label?.content
    }

    fun setToolWindowTitleVisible(toolWindow: ToolWindow, showTitle: Boolean) {
        toolWindow.component.putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, if (showTitle) null else "true")
        val contentManager = toolWindow.contentManager as? ContentManagerImpl
        val ui = contentManager?.ui as? ToolWindowContentUi
        ui?.update()
    }
}

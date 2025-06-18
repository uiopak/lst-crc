package com.github.uiopak.lstcrc.resources

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * Manages and loads custom plugin icons, providing a single, type-safe point of access.
 */
object LstCrcIcons {
    /**
     * The main icon for the LST-CRC plugin, used for the tool window.
     */
    @JvmField
    val TOOL_WINDOW: Icon = IconLoader.getIcon("/icons/toolWindowIcon.svg", javaClass)
}
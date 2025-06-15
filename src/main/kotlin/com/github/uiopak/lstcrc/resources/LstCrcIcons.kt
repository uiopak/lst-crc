package com.github.uiopak.lstcrc.resources

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * Object for managing and loading custom plugin icons.
 * This is the idiomatic way to handle icons, ensuring they are loaded correctly
 * and providing a single, type-safe point of access.
 */
object LstCrcIcons {
    /**
     * The main icon for the LST-CRC plugin, used for the tool window and other brand elements.
     * It represents a comparison between a stable local state (grey bar) and a branch (blue arrow).
     */
    @JvmField
    val TOOL_WINDOW: Icon = IconLoader.getIcon("/icons/toolWindowIcon.svg", javaClass)
}
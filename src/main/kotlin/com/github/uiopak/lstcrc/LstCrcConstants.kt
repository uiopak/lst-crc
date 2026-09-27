package com.github.uiopak.lstcrc

/**
 * Shared constants used across the plugin.
 */
object LstCrcConstants {
    /** The tool window ID registered in plugin.xml. */
    const val TOOL_WINDOW_ID = "GitChangesView"

    /**
     * Git's `HEAD` revision. Also the profile name of the permanent HEAD tab, which compares against it: loads and
     * the active diff of that tab are reported under this name.
     */
    const val HEAD = "HEAD"
}

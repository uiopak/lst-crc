package com.github.uiopak.lstcrc.state

/**
 * Represents the persistent state of the LstCrc tool window.
 * This includes the list of currently open tabs (branch comparisons) and the index of the selected tab.
 *
 * @property openTabs List of [TabInfo] objects, each representing an open tab in the tool window.
 * @property selectedTabIndex The index of the currently selected tab in the `openTabs` list.
 *                            A value of -1 typically indicates that the default or "HEAD" tab is selected,
 *                            or no specific closable tab is active.
 */
data class ToolWindowState(
    var openTabs: List<TabInfo> = emptyList(),
    var selectedTabIndex: Int = -1 // -1 can indicate no specific tab selected or default (e.g., HEAD tab)
)

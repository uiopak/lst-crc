package com.github.uiopak.lstcrc.state

data class ToolWindowState(
    var openTabs: List<TabInfo> = emptyList(),
    var selectedTabIndex: Int = -1 // -1 can indicate no specific tab selected or default
)

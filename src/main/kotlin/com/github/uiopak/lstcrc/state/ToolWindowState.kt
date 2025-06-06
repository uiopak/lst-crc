package com.github.uiopak.lstcrc.state

import com.intellij.util.xmlb.annotations.XCollection

data class ToolWindowState(
    @get:XCollection(style = XCollection.Style.v2)
    var openTabs: List<TabInfo> = mutableListOf(), // Changed to mutableListOf as per initial example, though List is often fine.
    var selectedTabIndex: Int = -1 // -1 for HEAD or no selection
)

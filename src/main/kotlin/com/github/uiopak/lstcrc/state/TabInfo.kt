package com.github.uiopak.lstcrc.state

import com.intellij.util.xmlb.annotations.Attribute

data class TabInfo(
    @field:Attribute("branchName")
    var branchName: String = "",
    @field:Attribute("alias")
    var alias: String? = null
)
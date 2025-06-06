package com.github.uiopak.lstcrc.state

import com.intellij.util.xmlb.annotations.Attribute // Keeping this as it's good practice

data class TabInfo(
    @field:Attribute("branchName") // Apply to field for data class
    var branchName: String = ""
)

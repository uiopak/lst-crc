package com.github.uiopak.lstcrc.state

import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.MapAnnotation

data class TabInfo(
    @field:Attribute("branchName")
    var branchName: String = "",
    @field:Attribute("alias")
    var alias: String? = null,
    @get:MapAnnotation(sortBeforeSave = false)
    var comparisonMap: MutableMap<String, String> = mutableMapOf()
)
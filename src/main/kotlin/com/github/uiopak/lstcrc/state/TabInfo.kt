package com.github.uiopak.lstcrc.state

import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.MapAnnotation

data class TabInfo(
    @field:Attribute("branchName")
    val branchName: String = "",
    @field:Attribute("alias")
    val alias: String? = null,
    @get:MapAnnotation(sortBeforeSave = false)
    var comparisonMap: Map<String, String> = emptyMap()
)
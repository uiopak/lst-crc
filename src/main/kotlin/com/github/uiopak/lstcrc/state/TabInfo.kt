package com.github.uiopak.lstcrc.state

import com.intellij.util.xmlb.annotations.Attribute

class TabInfo() { // Public no-arg constructor for serializer

    @Attribute("branchName")
    var branchName: String = ""

    // Secondary constructor for convenient use in code
    constructor(name: String) : this() {
        this.branchName = name
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false // Using javaClass for compatibility

        other as TabInfo // Smart cast

        return branchName == other.branchName
    }

    override fun hashCode(): Int {
        return branchName.hashCode()
    }

    override fun toString(): String {
        return "TabInfo(branchName='$branchName')"
    }
}

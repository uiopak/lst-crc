package com.github.uiopak.lstcrc.state

import com.intellij.util.xmlb.annotations.Attribute

/**
 * Represents information about a single tab in the LstCrc tool window.
 * Primarily stores the branch name that the tab is comparing against.
 *
 * This class is designed to be serializable by IntelliJ's PersistentStateComponent system.
 */
class TabInfo() { // Public no-arg constructor required by IntelliJ serializer

    @Attribute("branchName") // Annotation for XML serialization
    var branchName: String = ""

    /**
     * Secondary constructor for more convenient object creation in code.
     * @param name The branch name for this tab.
     */
    constructor(name: String) : this() {
        this.branchName = name
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TabInfo) return false // More idiomatic Kotlin check

        return branchName == other.branchName
    }

    override fun hashCode(): Int {
        return branchName.hashCode()
    }

    override fun toString(): String {
        return "TabInfo(branchName='$branchName')"
    }
}

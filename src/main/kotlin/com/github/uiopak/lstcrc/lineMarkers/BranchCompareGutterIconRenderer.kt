package com.github.uiopak.lstcrc.lineMarkers

import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import javax.swing.Icon

class BranchCompareGutterIconRenderer(private val tooltipText: String) : GutterIconRenderer() {

    override fun getIcon(): Icon {
        return AllIcons.Actions.Modify
    }

    override fun getTooltipText(): String {
        return tooltipText
    }

    override fun getAlignment(): Alignment {
        return Alignment.LEFT
    }

    // Optional: No action for now
    override fun getClickAction(): com.intellij.openapi.actionSystem.AnAction? {
        return null
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as BranchCompareGutterIconRenderer
        return tooltipText == other.tooltipText && getIcon() == other.getIcon() // Compare icon too for completeness
    }

    override fun hashCode(): Int {
        var result = tooltipText.hashCode()
        result = 31 * result + getIcon().hashCode()
        return result
    }
}

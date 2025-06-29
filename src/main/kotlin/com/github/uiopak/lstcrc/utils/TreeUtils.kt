package com.github.uiopak.lstcrc.utils

import java.awt.event.MouseEvent
import javax.swing.JTree
import javax.swing.tree.TreePath

/**
 * Checks if a mouse event occurred within the bounds of a tree row and returns the path if so.
 * This helper avoids boilerplate for validating clicks on tree nodes.
 *
 * @return The [TreePath] of the clicked row, or `null` if the click was outside any row's bounds.
 */
fun JTree.getTreePathForMouseCoordinates(e: MouseEvent): TreePath? {
    val row = getClosestRowForLocation(e.x, e.y)
    if (row == -1) return null

    val bounds = getRowBounds(row)
    if (bounds == null || e.y < bounds.y || e.y >= bounds.y + bounds.height) {
        return null
    }

    return getPathForRow(row)
}
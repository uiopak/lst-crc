package com.github.uiopak.lstcrc.toolWindow

import com.intellij.ide.util.treeView.TreeState
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.util.containers.JBTreeTraverser
import com.intellij.util.ui.tree.TreeUtil
import java.util.Comparator
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

/**
 * A custom TreeStateStrategy for ChangesTree that preserves the existing expansion state
 * while ensuring that any new nodes (Changes) are made visible by expanding their parent directories.
 *
 * How it works:
 * 1. `saveState`: It saves the complete current TreeState (selections and expansions) and also
 *    collects the set of all `Change` objects currently displayed in the tree.
 * 2. `restoreState`:
 *    a. It first applies the saved TreeState, which restores the user's previous expansions
 *       and collapses for nodes that still exist.
 *    b. It then determines which `Change` objects are new by comparing the current set of changes
 *       with the set saved in the state.
 *    c. For each new `Change`, it finds its path in the tree and collects all of its parent paths.
 *    d. Finally, it expands all of these parent paths, ensuring the new files are visible in the tree,
 *       without disturbing the state of previously existing, unchanged parts of the tree.
 */
class ExpandNewNodesStateStrategy : ChangesTree.TreeStateStrategy<ExpandNewNodesStateStrategy.State> {

    /**
     * Holds the state captured from the tree before a rebuild.
     * @param treeState The complete expansion and selection state of the tree.
     * @param changes The set of all `Change` objects present in the tree.
     */
    data class State(
        val treeState: TreeState?,
        val changes: Set<Change>
    )

    override fun saveState(tree: ChangesTree): State {
        val state = TreeState.createOn(tree, true, true)
        val currentChanges = VcsTreeModelData.all(tree).userObjects(Change::class.java).toSet()
        return State(state, currentChanges)
    }

    override fun restoreState(tree: ChangesTree, savedState: State, scrollToSelection: Boolean) {
        val oldTreeState = savedState.treeState
        if (oldTreeState == null || oldTreeState.isEmpty) {
            // This happens on the very first load or if the previous state was empty.
            // Fall back to the default behavior of expanding the tree.
            tree.resetTreeState()
            return
        }

        // 1. Restore the previous expansion and selection state for all existing nodes.
        oldTreeState.setScrollToSelection(scrollToSelection)
        oldTreeState.applyTo(tree)

        // 2. Find new changes and ensure their parent nodes are expanded.
        val oldChanges = savedState.changes
        val allCurrentChanges = VcsTreeModelData.all(tree).userObjects(Change::class.java)
        val newChangesToMakeVisible = allCurrentChanges.filter { it !in oldChanges }

        if (newChangesToMakeVisible.isEmpty()) {
            return
        }

        // Build a map of user objects to their nodes for efficient lookup.
        val userObjectToNodeMap = mutableMapOf<Any, DefaultMutableTreeNode>()
        val traverser: JBTreeTraverser<DefaultMutableTreeNode> = TreeUtil.treeNodeTraverser(tree.root)
            .expandAndFilter { it is DefaultMutableTreeNode }
            .map { it as DefaultMutableTreeNode }

        traverser.forEach { node ->
            node.userObject?.let { userObject ->
                userObjectToNodeMap[userObject] = node
            }
        }

        // Collect all unique parent paths of the new changes.
        val pathsToExpand = mutableSetOf<TreePath>()
        for (newChange in newChangesToMakeVisible) {
            val node = userObjectToNodeMap[newChange] ?: continue
            var parentPath = TreeUtil.getPathFromRoot(node).parentPath

            // Add all ancestors to the set to ensure the entire branch is expanded.
            // We stop when parentPath is null or is the invisible root (pathCount <= 1).
            while (parentPath != null && parentPath.pathCount > 1) {
                pathsToExpand.add(parentPath)
                parentPath = parentPath.parentPath
            }
        }

        if (pathsToExpand.isEmpty()) return

        // Sort paths by depth to ensure parents are expanded before their children.
        val sortedPaths = pathsToExpand.sortedWith(Comparator.comparingInt(TreePath::getPathCount))

        // Expand the paths to make the new files visible.
        for (path in sortedPaths) {
            tree.expandPath(path)
        }
    }
}
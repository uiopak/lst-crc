package com.github.uiopak.lstcrc.toolWindow

import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.util.Comparator
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

/**
 * Preserves the current expansion/collapse state while ensuring newly added changes become visible.
 *
 * Selection restore is handled manually instead of delegating to `TreeState.applyTo()`. IntelliJ's
 * generic tree-state restore recenters the selected row through `TreeUtil.showRowCentered(...)`,
 * which breaks the comparison browser's requirement to preserve the user's current viewport when
 * the selected change is offscreen.
 *
 * When [expandNewFilesInCollapsedDirs] returns `true` (the default, driven by the
 * "Expand Collapsed Folders for New Changes" setting), directories that are currently
 * collapsed will be expanded when new changes appear inside them — matching the
 * pre-collapse-persistence behavior. When it returns `false`, collapsed directories
 * stay collapsed even if they receive new changes.
 */
class ExpandNewNodesStateStrategy(
    private val expandNewFilesInCollapsedDirs: () -> Boolean = {
        ToolWindowSettingsProvider.isExpandNewFilesInCollapsedDirs()
    }
) : ChangesTree.TreeStateStrategy<ExpandNewNodesStateStrategy.State> {

    data class ChangeKey(
        val firstPath: String?,
        val secondPath: String?
    )

    private fun Change.asChangeKey(): ChangeKey {
        val beforePath = beforeRevision?.file?.path
        val afterPath = afterRevision?.file?.path

        return when {
            beforePath == null && afterPath == null -> ChangeKey(null, null)
            beforePath == null -> ChangeKey(null, afterPath)
            afterPath == null -> ChangeKey(null, beforePath)
            beforePath <= afterPath -> ChangeKey(beforePath, afterPath)
            else -> ChangeKey(afterPath, beforePath)
        }
    }

    data class State(
        val changes: Set<ChangeKey>,
        val selectedChanges: List<ChangeKey>,
        val expandedPaths: Set<String>,
        val collapsedPaths: Set<String>
    )

    private fun userObjectKey(userObject: Any?): String {
        return when (userObject) {
            null -> "null"
            is Change -> {
                val key = userObject.asChangeKey()
                "change:${key.firstPath}|${key.secondPath}"
            }
            is VirtualFile -> "${userObject.javaClass.name}:path=${userObject.path}"
            is FilePath -> "${userObject.javaClass.name}:path=${userObject.path}"
            is File -> "${userObject.javaClass.name}:path=${userObject.path}"
            is String -> "string:$userObject"
            else -> {
                // If it's a known IntelliJ class with a name, we can extract it if needed,
                // but for general directory nodes, FilePath/VirtualFile covers it.
                val stringRep = runCatching { userObject.toString() }.getOrNull()
                if (!stringRep.isNullOrBlank() && stringRep != "${userObject.javaClass.name}@${Integer.toHexString(userObject.hashCode())}") {
                     "${userObject.javaClass.name}:toString=$stringRep"
                } else {
                     userObject.javaClass.name
                }
            }
        }
    }

    private fun pathKey(path: TreePath): String {
        return path.path
            .drop(1)
            .joinToString("/") { component ->
                val node = component as? DefaultMutableTreeNode
                userObjectKey(node?.userObject)
            }
    }

    override fun saveState(tree: ChangesTree): State {
        val currentChanges = VcsTreeModelData.all(tree)
            .userObjects(Change::class.java)
            .map { it.asChangeKey() }
            .toSet()
        val selectedChanges = VcsTreeModelData.selected(tree)
            .userObjects(Change::class.java)
            .map { it.asChangeKey() }

        val expandedPaths = mutableSetOf<String>()
        val collapsedPaths = mutableSetOf<String>()
        for (row in 0 until tree.rowCount) {
            val path = tree.getPathForRow(row) ?: continue
            val node = path.lastPathComponent as? DefaultMutableTreeNode ?: continue
            if (path.pathCount <= 1 || node.isLeaf) continue
            val key = pathKey(path)
            if (tree.isExpanded(path)) {
                expandedPaths.add(key)
            } else {
                collapsedPaths.add(pathKey(path))
            }
        }

        return State(currentChanges, selectedChanges, expandedPaths, collapsedPaths)
    }

    override fun restoreState(tree: ChangesTree, savedState: State, scrollToSelection: Boolean) {
        val oldChanges = savedState.changes
        val allCurrentChanges = VcsTreeModelData.all(tree).userObjects(Change::class.java)
        val newChangesToMakeVisible = allCurrentChanges.filter { it.asChangeKey() !in oldChanges }

        val expandedForNewFiles = mutableSetOf<String>()

        val pathKeyToTreePath = mutableMapOf<String, TreePath>()
        val selectedPathsByChange = mutableMapOf<ChangeKey, TreePath>()
        TreeUtil.treeNodeTraverser(tree.root).forEach { treeNode ->
            val node = treeNode as? DefaultMutableTreeNode ?: return@forEach
            val path = TreeUtil.getPathFromRoot(node)
            if (path.pathCount > 1 && !node.isLeaf) {
                pathKeyToTreePath[pathKey(path)] = path
            }
            val change = node.userObject as? Change ?: return@forEach
            selectedPathsByChange[change.asChangeKey()] = path
        }

        savedState.expandedPaths
            .asSequence()
            .mapNotNull { key -> pathKeyToTreePath[key] }
            .sortedBy { it.pathCount }
            .forEach { tree.expandPath(it) }

        if (newChangesToMakeVisible.isNotEmpty()) {
            val changeKeyToNodeMap = mutableMapOf<ChangeKey, DefaultMutableTreeNode>()
            TreeUtil.treeNodeTraverser(tree.root).forEach { treeNode ->
                val node = treeNode as? DefaultMutableTreeNode ?: return@forEach
                val change = node.userObject as? Change ?: return@forEach
                changeKeyToNodeMap[change.asChangeKey()] = node
            }

            val pathsToExpand = mutableSetOf<TreePath>()
            for (newChange in newChangesToMakeVisible) {
                val node = changeKeyToNodeMap[newChange.asChangeKey()] ?: continue
                var parentPath = TreeUtil.getPathFromRoot(node).parentPath
                while (parentPath != null && parentPath.pathCount > 1) {
                    pathsToExpand.add(parentPath)
                    parentPath = parentPath.parentPath
                }
            }

            val sortedPaths = pathsToExpand.sortedWith(Comparator.comparingInt(TreePath::getPathCount))
            for (path in sortedPaths) {
                tree.expandPath(path)
                expandedForNewFiles.add(pathKey(path))
            }
        }

        savedState.collapsedPaths
            .asSequence()
            .filter { key ->
                // When the setting is enabled, don't re-collapse a dir that was just expanded
                // to reveal newly appeared changes inside it.
                expandedForNewFiles.isEmpty() || !expandNewFilesInCollapsedDirs() || key !in expandedForNewFiles
            }
            .mapNotNull { key -> pathKeyToTreePath[key] }
            .sortedByDescending { it.pathCount }
            .forEach { tree.collapsePath(it) }

        val selectedPaths = savedState.selectedChanges
            .mapNotNull { changeKey -> selectedPathsByChange[changeKey] }
        if (selectedPaths.isNotEmpty()) {
            // Preserve selection without recentering it into view.
            tree.selectionPaths = selectedPaths.toTypedArray()
        }
    }
}
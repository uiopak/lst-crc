package com.github.uiopak.lstcrc.toolWindow

import com.intellij.ide.util.treeView.TreeState
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.util.ui.tree.TreeUtil
import java.lang.reflect.Method
import java.util.Comparator
import java.util.concurrent.ConcurrentHashMap
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

/**
 * Preserves the current expansion/collapse state while ensuring newly-added changes become visible.
 *
 * When [expandNewFilesInCollapsedDirs] returns `true` (the default, driven by the
 * "Expand Collapsed Folders for New Changes" setting), directories that are currently
 * collapsed will be expanded when new changes appear inside them — matching the
 * pre-collapse-persistence behaviour. When it returns `false`, collapsed directories
 * stay collapsed even if they receive new changes.
 */
class ExpandNewNodesStateStrategy(
    private val expandNewFilesInCollapsedDirs: () -> Boolean = {
        ToolWindowSettingsProvider.isExpandNewFilesInCollapsedDirs()
    }
) : ChangesTree.TreeStateStrategy<ExpandNewNodesStateStrategy.State> {

    private data class UserObjectAccessors(
        val pathAccessor: Method?,
        val nameAccessor: Method?
    )

    private val userObjectAccessorCache = ConcurrentHashMap<Class<*>, UserObjectAccessors>()

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
        val treeState: TreeState?,
        val changes: Set<ChangeKey>,
        val collapsedPaths: Set<String>
    )

    private fun resolveUserObjectAccessors(userObject: Any): UserObjectAccessors {
        return userObjectAccessorCache.computeIfAbsent(userObject.javaClass) { userObjectClass ->
            val zeroArgMethods = userObjectClass.methods.filter { method -> method.parameterCount == 0 }
            UserObjectAccessors(
                pathAccessor = zeroArgMethods.firstOrNull { method ->
                    method.name == "getPath" || method.name == "path"
                },
                nameAccessor = zeroArgMethods.firstOrNull { method ->
                    method.name == "getName" || method.name == "name"
                }
            )
        }
    }

    private fun invokeAccessor(userObject: Any, accessor: Method?): String? {
        return accessor
            ?.let { method -> runCatching { method.invoke(userObject)?.toString() }.getOrNull() }
            ?.takeIf(String::isNotBlank)
    }

    private fun userObjectKey(userObject: Any?): String {
        return when (userObject) {
            null -> "null"
            is Change -> {
                val key = userObject.asChangeKey()
                "change:${key.firstPath}|${key.secondPath}"
            }
            is String -> "string:$userObject"
            else -> {
                val accessors = resolveUserObjectAccessors(userObject)
                val pathValue = invokeAccessor(userObject, accessors.pathAccessor)

                if (!pathValue.isNullOrBlank()) {
                    return "${userObject.javaClass.name}:path=$pathValue"
                }

                val nameValue = invokeAccessor(userObject, accessors.nameAccessor)

                if (!nameValue.isNullOrBlank()) {
                    return "${userObject.javaClass.name}:name=$nameValue"
                }

                userObject.javaClass.name
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
        val state = TreeState.createOn(tree, true, true)
        val currentChanges = VcsTreeModelData.all(tree)
            .userObjects(Change::class.java)
            .map { it.asChangeKey() }
            .toSet()

        val collapsedPaths = mutableSetOf<String>()
        for (row in 0 until tree.rowCount) {
            val path = tree.getPathForRow(row) ?: continue
            val node = path.lastPathComponent as? DefaultMutableTreeNode ?: continue
            if (path.pathCount <= 1 || node.isLeaf) continue
            if (!tree.isExpanded(path)) {
                collapsedPaths.add(pathKey(path))
            }
        }

        return State(state, currentChanges, collapsedPaths)
    }

    override fun restoreState(tree: ChangesTree, savedState: State, scrollToSelection: Boolean) {
        val oldTreeState = savedState.treeState
        if (oldTreeState == null) {
            tree.resetTreeState()
            return
        }

        oldTreeState.setScrollToSelection(scrollToSelection)
        oldTreeState.applyTo(tree)

        val oldChanges = savedState.changes
        val allCurrentChanges = VcsTreeModelData.all(tree).userObjects(Change::class.java)
        val newChangesToMakeVisible = allCurrentChanges.filter { it.asChangeKey() !in oldChanges }

        val expandedForNewFiles = mutableSetOf<String>()

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

        val collapsedPathToTreePath = mutableMapOf<String, TreePath>()
        TreeUtil.treeNodeTraverser(tree.root).forEach { treeNode ->
            val node = treeNode as? DefaultMutableTreeNode ?: return@forEach
            val path = TreeUtil.getPathFromRoot(node)
            if (path.pathCount > 1 && !node.isLeaf) {
                collapsedPathToTreePath[pathKey(path)] = path
            }
        }

        savedState.collapsedPaths
            .asSequence()
            .filter { key ->
                // When the setting is enabled, don't re-collapse a dir that was just expanded
                // to reveal newly-appeared changes inside it.
                expandedForNewFiles.isEmpty() || !expandNewFilesInCollapsedDirs() || key !in expandedForNewFiles
            }
            .mapNotNull { key -> collapsedPathToTreePath[key] }
            .sortedByDescending { it.pathCount }
            .forEach { tree.collapsePath(it) }
    }
}
package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.services.ChangeLineStats
import com.github.uiopak.lstcrc.services.GitService
import com.github.uiopak.lstcrc.services.ProjectActiveDiffDataService
import com.github.uiopak.lstcrc.services.ChangeLineStatsKey
import com.github.uiopak.lstcrc.services.CategorizedChanges
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.github.uiopak.lstcrc.utils.isCommitHash
import com.intellij.dvcs.ui.RepositoryChangesBrowserNode
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.FontUtil
import com.intellij.util.ui.tree.TreeUtil
import git4idea.repo.GitRepository
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JTree
import java.util.IdentityHashMap
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeNode

/**
 * Custom renderer for the LST-CRC changes tree.
 * It extends the default renderer to add specific comparison context information
 * to repository grouping nodes or changelist nodes in single-repo projects.
 */
class RepoNodeRenderer(
    project: Project,
    private val categorizedChangesProvider: () -> CategorizedChanges?,
    isShowFlatten: () -> Boolean,
    isHighlightProblems: Boolean
) : ChangesTreeCellRenderer(ChangesBrowserNodeRenderer(project, isShowFlatten, isHighlightProblems)) {

    private val gitService = project.service<GitService>()
    private val diffDataService = project.service<ProjectActiveDiffDataService>()
    private val stateService = project.service<ToolWindowStateService>()
    // Its border is set per row in updateRendererInsets.
    private val trailingRenderer = SimpleColoredComponent().apply {
        isOpaque = false
        iconTextGap = 0
    }

    // Per-tree caches. The renderer runs for every visible row on every repaint, so work that only
    // changes when the tree is rebuilt (new root node) or the line stats change is done once.
    private var cachedRoot: Any? = null
    private var cachedLineStats: Map<ChangeLineStatsKey, ChangeLineStats>? = null
    private var cachedIsMultiRepo = false
    private var cachedAnnotationNode: ChangesBrowserNode<*>? = null
    private var annotationNodeResolved = false
    private val aggregateCache = IdentityHashMap<TreeNode, ChangeLineStats?>()

    init {
        add(trailingRenderer, BorderLayout.EAST)
    }

    private fun refreshCachesIfStale(tree: JTree, lineStatsByChange: Map<ChangeLineStatsKey, ChangeLineStats>) {
        val root = tree.model?.root
        if (root === cachedRoot && lineStatsByChange === cachedLineStats) return
        cachedRoot = root
        cachedLineStats = lineStatsByChange
        cachedIsMultiRepo = gitService.getRepositories().size > 1
        annotationNodeResolved = false
        aggregateCache.clear()
    }

    /** Fills the trailing "(vs target) +a -r" text and sets the insets that depend on whether it is shown. */
    private fun configureTrailingRenderer(targetRevision: String?, lineStats: ChangeLineStats?) {
        trailingRenderer.clear()
        val visibleTarget = targetRevision?.takeUnless { isCommitHash(it) && !ToolWindowSettingsProvider.isShowContextForCommitsEnabled() }
        trailingMetadataFragments(lineStats, visibleTarget, ToolWindowSettingsProvider.isShowLineStatsInTree())
            .forEachIndexed { index, fragment ->
                if (index > 0) trailingRenderer.append(FontUtil.spaceAndThinSpace())
                trailingRenderer.append(fragment.text, fragment.attributes)
            }

        val hasTrailingMetadata = trailingRenderer.fragmentCount > 0
        trailingRenderer.isVisible = hasTrailingMetadata
        border = if (hasTrailingMetadata) JBUI.Borders.empty() else JBUI.Borders.emptyRight(RENDERER_RIGHT_PADDING)
        trailingRenderer.border = JBUI.Borders.emptyRight(if (hasTrailingMetadata) TRAILING_METADATA_RIGHT_GAP else 0)
    }

    override fun getTreeCellRendererComponent(
        tree: JTree,
        value: Any,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ): Component {
        textRenderer.clear()

        // First, let the standard renderer do its job.
        super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)

        val node = value as? ChangesBrowserNode<*> ?: return this
        val lineStatsByChange = categorizedChangesProvider()?.lineStatsByChange ?: diffDataService.lineStatsByChange
        refreshCachesIfStale(tree, lineStatsByChange)
        configureTrailingRenderer(resolveTargetRevision(tree, node), aggregateLineStatsForNode(node, lineStatsByChange, aggregateCache))
        trailingRenderer.background = textRenderer.background

        return this
    }

    @Suppress("unused")
    fun renderedTextForTest(
        tree: JTree,
        value: Any,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ): String {
        getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
        val leadingText = textRenderer.getCharSequence(false).toString().trim()
        val trailingText = trailingRenderer.getCharSequence(false).toString().trim()
        return sequenceOf(leadingText, trailingText)
            .filter(String::isNotBlank)
            .joinToString(" ")
    }

    /** The "(vs target)" context for [node]: on repository nodes in multi-repo projects, else on one top-level node. */
    private fun resolveTargetRevision(tree: JTree, node: ChangesBrowserNode<*>): String? {
        val context = categorizedChangesProvider()?.comparisonContext ?: diffDataService.activeComparisonContext
        val target = if (cachedIsMultiRepo) {
            if (!ToolWindowSettingsProvider.isShowContextForMultiRepoEnabled()) return null
            val repository = (node as? RepositoryChangesBrowserNode)?.userObject as? GitRepository ?: return null
            context[repository.root.path]
        } else {
            if (!ToolWindowSettingsProvider.isShowContextForSingleRepoEnabled()) return null
            if (node !== singleRepoAnnotationNode(tree)) return null
            context.values.firstOrNull()
        }
        return target ?: diffDataService.activeBranchName ?: stateService.getSelectedTabBranchName()
    }

    private fun singleRepoAnnotationNode(tree: JTree): ChangesBrowserNode<*>? {
        if (!annotationNodeResolved) {
            cachedAnnotationNode = findSingleRepoAnnotationNode(tree)
            annotationNodeResolved = true
        }
        return cachedAnnotationNode
    }

    /** The top-level node that carries the context in single-repo projects; computed once per tree root. */
    private fun findSingleRepoAnnotationNode(tree: JTree): ChangesBrowserNode<*>? {
        val changesTree = tree as? ChangesTree ?: return null
        val rootNode = tree.model.root as? DefaultMutableTreeNode ?: return null
        val topLevelNodes = TreeUtil.listChildren(rootNode).filterIsInstance<ChangesBrowserNode<*>>()
        if (topLevelNodes.isEmpty()) return null

        val groupingSupport = changesTree.groupingSupport
        return topLevelNodes.firstOrNull { candidate ->
            when (candidate) {
                is ChangesBrowserModuleNode -> groupingSupport[ChangesGroupingSupport.MODULE_GROUPING]
                is ChangesBrowserFilePathNode -> groupingSupport.isDirectory && !groupingSupport[ChangesGroupingSupport.MODULE_GROUPING]
                is ChangesBrowserChangeListNode -> groupingSupport.isNone
                else -> false
            }
        } ?: topLevelNodes.firstOrNull { it.childCount > 0 }
            ?: topLevelNodes.first()
    }
}

internal val ADDED_LINE_STATS_ATTRIBUTES: SimpleTextAttributes =
    SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, UIUtil.getLabelSuccessForeground())

internal val REMOVED_LINE_STATS_ATTRIBUTES: SimpleTextAttributes = SimpleTextAttributes.ERROR_ATTRIBUTES

private const val TRAILING_METADATA_RIGHT_GAP = 10
private const val RENDERER_RIGHT_PADDING = 10

private data class TrailingMetadataFragment(
    val text: String,
    val attributes: SimpleTextAttributes
)

private fun trailingMetadataFragments(
    lineStats: ChangeLineStats?,
    targetRevision: String?,
    showLineStats: Boolean
): List<TrailingMetadataFragment> {
    val fragments = mutableListOf<TrailingMetadataFragment>()

    targetRevision?.let {
        fragments += TrailingMetadataFragment("(vs $it)", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }

    if (showLineStats && lineStats != null) {
        if (lineStats.addedLines > 0) {
            fragments += TrailingMetadataFragment("+${lineStats.addedLines}", ADDED_LINE_STATS_ATTRIBUTES)
        }
        if (lineStats.removedLines > 0) {
            fragments += TrailingMetadataFragment("-${lineStats.removedLines}", REMOVED_LINE_STATS_ATTRIBUTES)
        }
    }

    return fragments
}

internal fun buildTrailingMetadataText(
    lineStats: ChangeLineStats?,
    targetRevision: String?,
    showLineStats: Boolean
): String? {
    val fragments = trailingMetadataFragments(lineStats, targetRevision, showLineStats)
    return fragments.takeIf { it.isNotEmpty() }
        ?.joinToString(FontUtil.spaceAndThinSpace()) { it.text }
}

/**
 * Line stats of a change node, or the sum over all changes below a directory/group node; null when none
 * have stats. Pass a [cache] shared across calls on the same tree so each node is summed only once.
 */
internal fun aggregateLineStatsForNode(
    node: DefaultMutableTreeNode,
    lineStatsByChange: Map<ChangeLineStatsKey, ChangeLineStats>,
    cache: MutableMap<TreeNode, ChangeLineStats?> = IdentityHashMap()
): ChangeLineStats? {
    if (cache.containsKey(node)) return cache[node]

    val change = node.userObject as? Change
    val result = if (change != null) {
        lineStatsByChange[ChangeLineStatsKey.from(change)]
    } else {
        var addedLines = 0
        var removedLines = 0
        var foundAny = false
        for (child in node.children()) {
            val stats = aggregateLineStatsForNode(child as? DefaultMutableTreeNode ?: continue, lineStatsByChange, cache) ?: continue
            addedLines += stats.addedLines
            removedLines += stats.removedLines
            foundAny = true
        }
        if (foundAny) ChangeLineStats(addedLines = addedLines, removedLines = removedLines) else null
    }

    cache[node] = result
    return result
}
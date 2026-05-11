package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.services.ChangeLineStats
import com.github.uiopak.lstcrc.services.GitService
import com.github.uiopak.lstcrc.services.ProjectActiveDiffDataService
import com.github.uiopak.lstcrc.services.ChangeLineStatsKey
import com.github.uiopak.lstcrc.services.CategorizedChanges
import git4idea.GitUtil
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
import javax.swing.tree.DefaultMutableTreeNode

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
    private val stateService = project.service<com.github.uiopak.lstcrc.services.ToolWindowStateService>()
    private val trailingRenderer = SimpleColoredComponent().apply {
        border = JBUI.Borders.emptyLeft(TRAILING_METADATA_LEFT_GAP)
        isOpaque = false
        iconTextGap = 0
    }

    init {
        add(trailingRenderer, BorderLayout.EAST)
    }

    private fun visibleTargetRevision(targetRevision: String?): String? {
        targetRevision ?: return null
        val showForCommits = ToolWindowSettingsProvider.isShowContextForCommitsEnabled()
        return targetRevision.takeUnless { GitUtil.isHashString(it, false) && !showForCommits }
    }

    private fun configureTrailingRenderer(targetRevision: String?, lineStats: ChangeLineStats?) {
        trailingRenderer.clear()

        targetRevision?.let {
            trailingRenderer.append("(vs $it)", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }

        if (ToolWindowSettingsProvider.isShowLineStatsInTree() && lineStats != null) {
            if (trailingRenderer.fragmentCount > 0) {
                trailingRenderer.append(FontUtil.spaceAndThinSpace())
            }
            if (lineStats.addedLines > 0) {
                trailingRenderer.append(
                    "+${lineStats.addedLines}",
                    ADDED_LINE_STATS_ATTRIBUTES
                )
            }
            if (lineStats.removedLines > 0) {
                if (trailingRenderer.fragmentCount > 0) {
                    trailingRenderer.append(FontUtil.spaceAndThinSpace())
                }
                trailingRenderer.append(
                    "-${lineStats.removedLines}",
                    REMOVED_LINE_STATS_ATTRIBUTES
                )
            }
        }

        trailingRenderer.isVisible = trailingRenderer.fragmentCount > 0
    }

    private fun updateRendererInsets(hasTrailingMetadata: Boolean) {
        border = if (hasTrailingMetadata) {
            JBUI.Borders.empty()
        } else {
            JBUI.Borders.emptyRight(RENDERER_RIGHT_PADDING)
        }

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
        val lineStatsByChange = currentLineStatsByChange()

        val targetRevision = visibleTargetRevision(resolveTargetRevision(tree, node))
        val lineStats = (node.userObject as? Change)?.let { lineStatsByChange[ChangeLineStatsKey.from(it)] }
            ?: aggregateLineStatsForNode(node, lineStatsByChange)

        configureTrailingRenderer(targetRevision, lineStats)
        updateRendererInsets(trailingRenderer.isVisible)

        trailingRenderer.background = textRenderer.background

        return this
    }

    private fun resolveTargetRevision(tree: JTree, node: ChangesBrowserNode<*>): String? {
        val isMultiRepo = gitService.getRepositories().size > 1
        if (!shouldShowContext(isMultiRepo)) {
            return null
        }

        return if (isMultiRepo) {
            resolveMultiRepoTargetRevision(node)
        } else {
            resolveSingleRepoTargetRevision(tree, node)
        }
    }

    private fun shouldShowContext(isMultiRepo: Boolean): Boolean {
        return if (isMultiRepo) {
            ToolWindowSettingsProvider.isShowContextForMultiRepoEnabled()
        } else {
            ToolWindowSettingsProvider.isShowContextForSingleRepoEnabled()
        }
    }

    private fun resolveMultiRepoTargetRevision(node: ChangesBrowserNode<*>): String? {
        val repositoryNode = node as? RepositoryChangesBrowserNode ?: return null
        val repository = repositoryNode.userObject as? GitRepository ?: return null
        return resolveTargetRevisionForRepository(repository)
    }

    private fun resolveSingleRepoTargetRevision(tree: JTree, node: ChangesBrowserNode<*>): String? {
        if (!shouldAnnotateSingleRepoNode(tree, node)) {
            return null
        }

        return currentComparisonContext().values.firstOrNull()
            ?: diffDataService.activeBranchName
            ?: stateService.getSelectedTabBranchName()
    }

    private fun resolveTargetRevisionForRepository(repository: GitRepository): String? {
        return currentComparisonContext()[repository.root.path]
            ?: diffDataService.activeBranchName
            ?: stateService.getSelectedTabBranchName()
    }

    private fun currentComparisonContext(): Map<String, String> {
        return categorizedChangesProvider()?.comparisonContext ?: diffDataService.activeComparisonContext
    }

    private fun currentLineStatsByChange(): Map<ChangeLineStatsKey, ChangeLineStats> {
        return categorizedChangesProvider()?.lineStatsByChange ?: diffDataService.lineStatsByChange
    }

    private fun shouldAnnotateSingleRepoNode(tree: JTree, node: ChangesBrowserNode<*>): Boolean {
        val changesTree = tree as? ChangesTree ?: return false
        val rootNode = tree.model.root as? DefaultMutableTreeNode ?: return false
        val topLevelNodes = TreeUtil.listChildren(rootNode)
            .filterIsInstance<ChangesBrowserNode<*>>()
        if (topLevelNodes.isEmpty()) {
            return false
        }

        val groupingSupport = changesTree.groupingSupport
        val annotationNode = topLevelNodes.firstOrNull { candidate ->
            when (candidate) {
                is ChangesBrowserModuleNode -> groupingSupport[ChangesGroupingSupport.MODULE_GROUPING]
                is ChangesBrowserFilePathNode -> groupingSupport.isDirectory && !groupingSupport[ChangesGroupingSupport.MODULE_GROUPING]
                is ChangesBrowserChangeListNode -> groupingSupport.isNone
                else -> false
            }
        } ?: topLevelNodes.firstOrNull { it.childCount > 0 }
            ?: topLevelNodes.first()

        return node === annotationNode
    }
}

internal val ADDED_LINE_STATS_ATTRIBUTES: SimpleTextAttributes =
    SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, UIUtil.getLabelSuccessForeground())

internal val REMOVED_LINE_STATS_ATTRIBUTES: SimpleTextAttributes = SimpleTextAttributes.ERROR_ATTRIBUTES

private const val TRAILING_METADATA_LEFT_GAP = 0
private const val TRAILING_METADATA_RIGHT_GAP = 10
private const val RENDERER_RIGHT_PADDING = 10

internal fun buildTrailingMetadataText(
    lineStats: ChangeLineStats?,
    targetRevision: String?,
    showLineStats: Boolean
): String? {
    val fragments = mutableListOf<String>()
    targetRevision?.let { fragments += "(vs $it)" }
    if (showLineStats && lineStats != null) {
        if (lineStats.addedLines > 0) {
            fragments += "+${lineStats.addedLines}"
        }
        if (lineStats.removedLines > 0) {
            fragments += "-${lineStats.removedLines}"
        }
    }
    return fragments.takeIf { it.isNotEmpty() }?.joinToString(FontUtil.spaceAndThinSpace())
}

internal fun aggregateLineStatsForNode(
    node: DefaultMutableTreeNode,
    lineStatsByChange: Map<ChangeLineStatsKey, ChangeLineStats>
): ChangeLineStats? {
    var addedLines = 0
    var removedLines = 0
    var foundAny = false

    TreeUtil.treeNodeTraverser(node).forEach { treeNode ->
        val descendantNode = treeNode as? DefaultMutableTreeNode ?: return@forEach
        val change = descendantNode.userObject as? Change ?: return@forEach
        val stats = lineStatsByChange[ChangeLineStatsKey.from(change)] ?: return@forEach
        addedLines += stats.addedLines
        removedLines += stats.removedLines
        foundAny = true
    }

    return if (foundAny) ChangeLineStats(addedLines = addedLines, removedLines = removedLines) else null
}
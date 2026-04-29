package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.services.GitService
import com.github.uiopak.lstcrc.services.ProjectActiveDiffDataService
import com.github.uiopak.lstcrc.utils.RevisionUtils
import com.intellij.dvcs.ui.RepositoryChangesBrowserNode
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.FontUtil
import git4idea.repo.GitRepository
import java.awt.Component
import javax.swing.JTree

/**
 * Custom renderer for the LST-CRC changes tree.
 * It extends the default renderer to add specific comparison context information
 * to repository grouping nodes or changelist nodes in single-repo projects.
 */
class RepoNodeRenderer(
    project: Project,
    isShowFlatten: () -> Boolean,
    isHighlightProblems: Boolean
) : ChangesTreeCellRenderer(ChangesBrowserNodeRenderer(project, isShowFlatten, isHighlightProblems)) {

    private val gitService = project.service<GitService>()
    private val diffDataService = project.service<ProjectActiveDiffDataService>()

    private fun appendContextText(targetRevision: String) {
        val showForCommits = ToolWindowSettingsProvider.isShowContextForCommitsEnabled()
        if (RevisionUtils.isCommitHash(targetRevision) && !showForCommits) {
            return
        }

        textRenderer.append(FontUtil.spaceAndThinSpace())
        textRenderer.append("(vs $targetRevision)", SimpleTextAttributes.GRAYED_ATTRIBUTES)
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
        // First, let the standard renderer do its job.
        super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)

        val node = value as? ChangesBrowserNode<*> ?: return this

        val targetRevision = resolveTargetRevision(tree, node) ?: return this
        appendContextText(targetRevision)

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
        return diffDataService.activeComparisonContext[repository.root.path]
    }

    private fun resolveSingleRepoTargetRevision(tree: JTree, node: ChangesBrowserNode<*>): String? {
        if (!shouldAnnotateSingleRepoNode(tree, node)) {
            return null
        }

        val repository = gitService.getPrimaryRepository() ?: return null
        return diffDataService.activeComparisonContext[repository.root.path]
    }

    private fun shouldAnnotateSingleRepoNode(tree: JTree, node: ChangesBrowserNode<*>): Boolean {
        val changesTree = tree as? ChangesTree ?: return false
        val groupingSupport = changesTree.groupingSupport
        val parentNode = node.parent ?: return false
        val rootNode = tree.model.root
        if (parentNode != rootNode || parentNode.childCount != 1) {
            return false
        }

        return when (node) {
            is ChangesBrowserModuleNode -> groupingSupport[ChangesGroupingSupport.MODULE_GROUPING]
            is ChangesBrowserFilePathNode -> groupingSupport.isDirectory && !groupingSupport[ChangesGroupingSupport.MODULE_GROUPING]
            is ChangesBrowserChangeListNode -> groupingSupport.isNone
            else -> false
        }
    }
}
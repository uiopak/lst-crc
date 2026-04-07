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
    private val project: Project,
    isShowFlatten: () -> Boolean,
    isHighlightProblems: Boolean
) : ChangesTreeCellRenderer(ChangesBrowserNodeRenderer(project, isShowFlatten, isHighlightProblems)) {

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

        val gitService = project.service<GitService>()
        val repositories = gitService.getRepositories()
        val isMultiRepo = repositories.size > 1

        val shouldShowContext = if (isMultiRepo) {
            ToolWindowSettingsProvider.isShowContextForMultiRepoEnabled()
        } else {
            ToolWindowSettingsProvider.isShowContextForSingleRepoEnabled()
        }

        if (!shouldShowContext) return this

        val diffDataService = project.service<ProjectActiveDiffDataService>()
        val node = value as? ChangesBrowserNode<*> ?: return this

        if (isMultiRepo) {
            // Case 1: Multi-repo view, append to the repository node if grouping is active.
            if (node is RepositoryChangesBrowserNode) {
                val repository = node.userObject as? GitRepository ?: return this
                val comparisonContext = diffDataService.activeComparisonContext
                val targetRevision = comparisonContext[repository.root.path]

                if (targetRevision != null) {
                    appendContextText(targetRevision)
                }
            }
        } else {
            // Case 2: Single-repo view. Annotate the top-level node if it's unique.
            val groupingSupport = (tree as ChangesTree).groupingSupport
            val parentNode = node.parent
            val rootNode = tree.model.root

            // Check if the node is a unique, top-level node under the invisible root.
            if (parentNode != null && parentNode == rootNode && parentNode.childCount == 1) {
                var shouldAnnotate = false
                when {
                    node is ChangesBrowserModuleNode && groupingSupport[ChangesGroupingSupport.MODULE_GROUPING] -> {
                        shouldAnnotate = true
                    }
                    node is ChangesBrowserFilePathNode && groupingSupport.isDirectory && !groupingSupport[ChangesGroupingSupport.MODULE_GROUPING] -> {
                        shouldAnnotate = true
                    }
                    node is ChangesBrowserChangeListNode && groupingSupport.isNone -> {
                        shouldAnnotate = true
                    }
                }

                if (shouldAnnotate) {
                    val repository = gitService.getPrimaryRepository() ?: return this
                    val comparisonContext = diffDataService.activeComparisonContext
                    val targetRevision = comparisonContext[repository.root.path]

                    if (targetRevision != null) {
                        appendContextText(targetRevision)
                    }
                }
            }
        }

        return this
    }
}
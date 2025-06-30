package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.services.ProjectActiveDiffDataService
import com.intellij.dvcs.ui.RepositoryChangesBrowserNode
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer
import com.intellij.openapi.vcs.changes.ui.ChangesTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.FontUtil
import git4idea.repo.GitRepository
import java.awt.Component
import javax.swing.JTree

/**
 * Custom renderer for the LST-CRC changes tree.
 * It extends the default renderer to add specific comparison context information
 * to repository grouping nodes.
 */
class RepoNodeRenderer(
    private val project: Project,
    isShowFlatten: () -> Boolean,
    isHighlightProblems: Boolean
) : ChangesTreeCellRenderer(ChangesBrowserNodeRenderer(project, isShowFlatten, isHighlightProblems)) {

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

        // We only want to customize the Repository node.
        if (value is RepositoryChangesBrowserNode) {
            val repository = value.userObject as? GitRepository ?: return this

            val diffDataService = project.service<ProjectActiveDiffDataService>()
            val comparisonContext = diffDataService.activeComparisonContext
            val targetRevision = comparisonContext[repository.root.path]

            if (targetRevision != null) {
                // The 'textRenderer' is a protected property from the parent class.
                // It's the component that holds the text part of the node.
                // We can simply append our custom text to it.
                textRenderer.append(FontUtil.spaceAndThinSpace())
                textRenderer.append("(vs $targetRevision)", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
        }

        return this
    }
}
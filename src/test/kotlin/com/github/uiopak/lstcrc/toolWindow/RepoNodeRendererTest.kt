package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.services.ChangeLineStats
import com.github.uiopak.lstcrc.services.ChangeLineStatsKey
import com.github.uiopak.lstcrc.services.calculateLineStats
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.ui.SimpleTextAttributes
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.FontUtil
import com.intellij.util.ui.UIUtil
import com.intellij.vcsUtil.VcsUtil
import javax.swing.tree.DefaultMutableTreeNode

class RepoNodeRendererTest : BasePlatformTestCase() {

    fun testAddedLineStatsUseBuiltInSuccessForeground() {
        assertEquals(UIUtil.getLabelSuccessForeground(), ADDED_LINE_STATS_ATTRIBUTES.fgColor)
    }

    fun testRemovedLineStatsUseBuiltInErrorAttributes() {
        assertSame(SimpleTextAttributes.ERROR_ATTRIBUTES, REMOVED_LINE_STATS_ATTRIBUTES)
    }

    fun testBuildTrailingMetadataTextIncludesVisibleLineStatsAndRevision() {
        val text = buildTrailingMetadataText(ChangeLineStats(addedLines = 3, removedLines = 2), "feature", showLineStats = true)

        assertEquals("(vs feature)${FontUtil.spaceAndThinSpace()}+3${FontUtil.spaceAndThinSpace()}-2", text)
    }

    fun testBuildTrailingMetadataTextOmitsLineStatsWhenDisabled() {
        val text = buildTrailingMetadataText(ChangeLineStats(addedLines = 3, removedLines = 2), "feature", showLineStats = false)

        assertEquals("(vs feature)", text)
    }

    fun testBuildTrailingMetadataTextSupportsLineStatsWithoutRevision() {
        val text = buildTrailingMetadataText(ChangeLineStats(addedLines = 3, removedLines = 2), null, showLineStats = true)

        assertEquals("+3${FontUtil.spaceAndThinSpace()}-2", text)
    }

    fun testAggregateLineStatsForFolderNodeSumsDescendantChanges() {
        val modifiedChange = change("nested/Main.txt", beforeContent = "Base line\n", afterContent = "Feature tree line\n")
        val addedChange = change("nested/Extra.txt", beforeContent = null, afterContent = "One\n")
        val folderNode = DefaultMutableTreeNode("nested").apply {
            add(DefaultMutableTreeNode(modifiedChange))
            add(DefaultMutableTreeNode(addedChange))
        }
        val lineStatsByChange = listOf(modifiedChange, addedChange).associateWith { change ->
            calculateLineStats(change.beforeRevision?.content ?: "", change.afterRevision?.content ?: "")
        }.mapKeys { (change, _) -> ChangeLineStatsKey.from(change) }

        val aggregatedStats = aggregateLineStatsForNode(folderNode, lineStatsByChange)

        assertNotNull(aggregatedStats)
        assertEquals(2, aggregatedStats!!.addedLines)
        assertEquals(1, aggregatedStats.removedLines)
    }

    fun testAggregateLineStatsForFolderNodeReturnsNullWithoutDescendantChanges() {
        assertNull(aggregateLineStatsForNode(DefaultMutableTreeNode("nested"), emptyMap()))
    }

    private fun change(path: String, beforeContent: String?, afterContent: String?): Change {
        val beforeRevision = beforeContent?.let { TestContentRevision(path, it, "before") }
        val afterRevision = afterContent?.let { TestContentRevision(path, it, "after") }
        val fileStatus = when {
            beforeRevision == null -> FileStatus.ADDED
            afterRevision == null -> FileStatus.DELETED
            else -> FileStatus.MODIFIED
        }
        return Change(beforeRevision, afterRevision, fileStatus)
    }

    private class TestContentRevision(
        path: String,
        private val text: String,
        private val revision: String
    ) : ContentRevision {
        private val filePath: FilePath = VcsUtil.getFilePath(path, false)

        override fun getFile(): FilePath = filePath

        override fun getRevisionNumber(): VcsRevisionNumber = object : VcsRevisionNumber {
            override fun asString(): String = revision

            override fun compareTo(other: VcsRevisionNumber): Int = 0
        }

        override fun getContent(): String = text
    }
}
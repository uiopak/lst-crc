package com.github.uiopak.lstcrc.services

import com.github.uiopak.lstcrc.fixtures.LstCrcPerformanceReport
import com.github.uiopak.lstcrc.state.ToolWindowState
import com.github.uiopak.lstcrc.testsupport.LstCrcTestCase
import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.Change
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitRevisionNumber
import java.lang.management.ManagementFactory
import kotlin.time.measureTime

class DiffComputationPerformanceTest : LstCrcTestCase() {
    fun testAddedAndDeletedLineStatsMatchTheDiffEngine() {
        fun engineStats(before: String, after: String): ChangeLineStats {
            val fragments = ComparisonManager.getInstance().compareLines(
                StringUtil.convertLineSeparators(before), StringUtil.convertLineSeparators(after),
                ComparisonPolicy.DEFAULT, DumbProgressIndicator.INSTANCE
            )
            return ChangeLineStats(
                fragments.sumOf { it.endLine2 - it.startLine2 },
                fragments.sumOf { it.endLine1 - it.startLine1 }
            )
        }
        for (text in listOf("", "one", "one\n", "\n", "one\n\n", "one\rtwo",
            "\r\n", "one\r\ntwo", "one\r\ntwo\r\n", "single line without a newline")) {
            var calls = 0
            fun stats(before: String, after: String) = calculateLineStatsForTest(before, after) { a, b ->
                calls++
                ComparisonManager.getInstance().compareLines(a, b, ComparisonPolicy.DEFAULT, DumbProgressIndicator.INSTANCE)
            }
            val sample = text.replace("\r", "\\r").replace("\n", "\\n")
            assertEquals("Empty before: $sample", engineStats("", text), stats("", text))
            assertEquals("Empty after: $sample", engineStats(text, ""), stats(text, ""))
            println("[lstcrc-work] empty-side diff-engine calls=$calls for ${text.length} characters")
            assertEquals("Nonempty additions and deletions must use the engine's line semantics",
                if (text.isEmpty()) 0 else 2, calls)
        }
    }

    fun testEqualLineStatsSkipTheDiffEngine() {
        var calls = 0
        val stats = calculateLineStatsForTest("one\r\ntwo\r\n", "one\ntwo\n") { before, after ->
            calls++
            ComparisonManager.getInstance().compareLines(before, after, ComparisonPolicy.DEFAULT, DumbProgressIndicator.INSTANCE)
        }
        assertEquals(ChangeLineStats(0, 0), stats)
        println("[lstcrc-work] equal-content diff-engine calls=$calls")
        assertEquals("Equal normalized text does not need a diff algorithm", 0, calls)
    }

    fun testChangedLineStatsStillUsesTheDiffEngine() {
        var calls = 0
        val stats = calculateLineStatsForTest("before\nsecond\n", "after\nsecond\n") { before, after ->
            calls++
            ComparisonManager.getInstance().compareLines(before, after, ComparisonPolicy.DEFAULT, DumbProgressIndicator.INSTANCE)
        }
        assertEquals(ChangeLineStats(1, 1), stats)
        assertEquals(1, calls)
    }

    fun testDuplicateDiskPathsKeepLastChangeAndStats() {
        val path = VcsUtil.getFilePath("/repo/Main.txt", false)
        val before = TextContentRevision(path, "before\n", GitRevisionNumber("HEAD"))
        val after = TextContentRevision(path, "after\n", GitRevisionNumber("LOCAL"))
        val earlier = Change(before, after, FileStatus.MODIFIED)
        val later = Change(null, after, FileStatus.ADDED)
        val loaded = LoadedChanges(listOf(earlier, later), mapOf(
            ChangeLineStatsKey.from(earlier) to ChangeLineStats(1, 1),
            ChangeLineStatsKey.from(later) to ChangeLineStats(1, 0)
        ))
        val merged = deduplicateDiskChanges(loaded)
        assertEquals(listOf(later), merged.changes)
        assertEquals(mapOf(ChangeLineStatsKey.from(later) to ChangeLineStats(1, 0)), merged.lineStatsByChange)
    }

    fun testUnchangedSnapshotSkipsPerChangeComparison() {
        val entries = List(200) { i ->
            val path = VcsUtil.getFilePath("/repo/file$i.txt", false)
            Change(TextContentRevision(path, "before\n", GitRevisionNumber("HEAD")),
                TextContentRevision(path, "after\n", GitRevisionNumber("LOCAL")), FileStatus.MODIFIED)
        }
        var reads = 0
        val counted = object : AbstractList<Change>() {
            override val size: Int get() = entries.size
            override fun get(index: Int): Change { reads++; return entries[index] }
        }
        val changes = CategorizedChanges(counted, emptyList(), emptyList(), emptyList(), emptyList(), emptyMap(), emptyMap())
        val service = project.service<ProjectActiveDiffDataService>()
        val tabState = project.service<ToolWindowStateService>()
        val previousTabState = tabState.state
        try {
            tabState.loadState(ToolWindowState())
            service.updateActiveDiff("HEAD", changes)
            assertSame("Rejecting an update must not make the work counter pass", changes, service.categorizedChanges)
            reads = 0
            repeat(20) { service.updateActiveDiff("HEAD", changes) }
            println("[lstcrc-work] unchanged-snapshot change reads=$reads for 20 x 200 changes")
            assertEquals("An identical immutable comparison needs no per-change checks", 0, reads)
        } finally {
            tabState.loadState(previousTabState)
        }
    }

    fun testReportLargeLineStatsWork() {
        val text = (1..5000).joinToString("\n", postfix = "\n") { "line $it" }
        val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        for ((step, before, after) in listOf(
            Triple("added 5000 lines", "", text),
            Triple("deleted 5000 lines", text, ""),
            Triple("unchanged 5000 lines", text, text),
            Triple("changed 5000 lines", text, text.replace("line 2500\n", "edited 2500\n"))
        )) {
            repeat(5) { calculateLineStats(before, after) }
            val startBytes = bean.getThreadAllocatedBytes(Thread.currentThread().threadId())
            val time = measureTime { repeat(20) { calculateLineStats(before, after) } }
            val bytes = bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - startBytes
            LstCrcPerformanceReport.record("unit-line-stats", step, time, "20 samples; allocated bytes=$bytes")
        }
    }
}

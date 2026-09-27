package com.github.uiopak.lstcrc.services

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.github.uiopak.lstcrc.testsupport.LstCrcTestCase
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitRevisionNumber

class GitServiceOverlayMergeTest : LstCrcTestCase() {

    fun testPreservesNewChangeTypeWhenUnsavedOverlayIsApplied() {
        val existingChange = Change(null, StubRevision("C:/repo/Local.txt"), FileStatus.ADDED)
        val overlayRevision = StubRevision("C:/repo/Local.txt")
        val unsavedOverlay = Change(StubRevision("C:/repo/Local.txt"), overlayRevision, FileStatus.MODIFIED)

        val mergedChange = mergeUnsavedOverlayChange(existingChange, unsavedOverlay)

        assertEquals(Change.Type.NEW, mergedChange.type)
        assertNull(mergedChange.beforeRevision)
        assertSame(overlayRevision, mergedChange.afterRevision)
    }

    fun testKeepsModificationOverlayForNonNewFiles() {
        val existingChange = Change(StubRevision("C:/repo/Main.txt"), StubRevision("C:/repo/Main.txt"), FileStatus.MODIFIED)
        val unsavedOverlay = Change(StubRevision("C:/repo/Main.txt"), StubRevision("C:/repo/Main.txt"), FileStatus.MODIFIED)

        val mergedChange = mergeUnsavedOverlayChange(existingChange, unsavedOverlay)

        assertSame(unsavedOverlay, mergedChange)
    }

    fun testTrackedAddedPathsSkipsUntrackedAndModifiedFiles() {
        val changes = listOf(
            Change(null, StubRevision("C:/repo/Added.txt"), FileStatus.ADDED),
            Change(null, StubRevision("C:/repo/Untracked.txt"), FileStatus.UNKNOWN),
            Change(StubRevision("C:/repo/Main.txt"), StubRevision("C:/repo/Main.txt"), FileStatus.MODIFIED),
            Change(StubRevision("C:/repo/Gone.txt"), null, FileStatus.DELETED)
        )

        assertEquals(setOf("C:/repo/Added.txt"), trackedAddedPaths(changes))
    }

    private class StubRevision(path: String) : ContentRevision {
        private val filePath: FilePath = VcsUtil.getFilePath(path, false)
        private val revisionNumber = object : VcsRevisionNumber {
            override fun asString(): String = "stub"

            override fun compareTo(other: VcsRevisionNumber): Int = 0
        }

        override fun getFile(): FilePath = filePath

        override fun getContent(): String? = null

        override fun getRevisionNumber(): VcsRevisionNumber = revisionNumber
    }

    fun testParseTrackedDiffReadsRawAndNumstatRecordsIncludingRenames() {
        val root = myFixture.tempDirFixture.findOrCreateDir("repo")
        val output = listOf(
            ":100644 100644 1111111 0000000 M", "Main.txt",
            ":100644 100644 2222222 0000000 R090", "Old.txt", "New.txt",
            ":000000 100644 0000000 0000000 A", "Added.bin",
            "1\t2\tMain.txt",
            "3\t0\t", "Old.txt", "New.txt",
            "-\t-\tAdded.bin",
            ""
        ).joinToString("\u0000")

        val loaded = parseTrackedDiff(project, root, GitRevisionNumber("feature"), output)

        assertEquals(
            listOf(
                Triple("${root.path}/Main.txt", "${root.path}/Main.txt", FileStatus.MODIFIED),
                Triple("${root.path}/Old.txt", "${root.path}/New.txt", FileStatus.MODIFIED),
                Triple(null, "${root.path}/Added.bin", FileStatus.ADDED)
            ),
            loaded.changes.map { Triple(it.beforeRevision?.file?.path, it.afterRevision?.file?.path, it.fileStatus) }
        )
        assertEquals(
            mapOf(
                ChangeLineStatsKey.fromPaths("${root.path}/Main.txt", "${root.path}/Main.txt") to ChangeLineStats(1, 2),
                ChangeLineStatsKey.fromPaths("${root.path}/Old.txt", "${root.path}/New.txt") to ChangeLineStats(3, 0)
            ),
            loaded.lineStatsByChange
        )
        // Moved files map their new path to the path they have in the target.
        assertEquals(mapOf("${root.path}/New.txt" to "${root.path}/Old.txt"), movedSourcePaths(loaded.changes).mapValues { it.value.path })
    }

    fun testParseTrackedDiffKeepsTabsInNumstatPaths() {
        val root = myFixture.tempDirFixture.findOrCreateDir("repo")
        val output = listOf(":100644 100644 1111111 0000000 M", "a\tb.txt", "1\t2\ta\tb.txt", "").joinToString("\u0000")

        val loaded = parseTrackedDiff(project, root, GitRevisionNumber("feature"), output)

        val path = "${root.path}/a\tb.txt"
        assertEquals(mapOf(ChangeLineStatsKey.fromPaths(path, path) to ChangeLineStats(1, 2)), loaded.lineStatsByChange)
    }

    fun testUntrackedChangesKeepFileNamesMadeOfSpaces() {
        val root = myFixture.tempDirFixture.findOrCreateDir("repo")

        val changes = untrackedChanges(project, root, " \u0000plain.txt\u0000")

        assertEquals(listOf("${root.path}/ ", "${root.path}/plain.txt"), changes.map { it.afterRevision?.file?.path })
    }

    fun testUntrackedChangesKeepBackslashesInFileNames() {
        val root = myFixture.tempDirFixture.findOrCreateDir("repo")

        val changes = untrackedChanges(project, root, "a\\b.txt\u0000a\\q.txt\u0000plain.txt\u0000")

        assertEquals(
            listOf("${root.path}/a\\b.txt", "${root.path}/a\\q.txt", "${root.path}/plain.txt"),
            changes.map { it.afterRevision!!.file.path }
        )
        assertTrue(changes.all { it.beforeRevision == null && it.fileStatus == FileStatus.UNKNOWN })
    }
}

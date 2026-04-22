package com.github.uiopak.lstcrc.services

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.vcsUtil.VcsUtil

class GitServiceOverlayMergeTest : BasePlatformTestCase() {

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

    private class StubRevision(path: String) : ContentRevision {
        private val filePath: FilePath = VcsUtil.getFilePath(path)
        private val revisionNumber = object : VcsRevisionNumber {
            override fun asString(): String = "stub"

            override fun compareTo(other: VcsRevisionNumber): Int = 0
        }

        override fun getFile(): FilePath = filePath

        override fun getContent(): String? = null

        override fun getRevisionNumber(): VcsRevisionNumber = revisionNumber
    }
}
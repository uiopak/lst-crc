package com.github.uiopak.lstcrc.gutters

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ex.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt

/**
 * A custom implementation of a local line status tracker for LST-CRC.
 * This class is based on the platform's [SimpleLocalLineStatusTracker] but under our own
 * type, allowing our [LstCrcLineStatusTrackerProvider] to identify and manage it.
 *
 * It extends [LocalLineStatusTrackerImpl] to provide the core functionality for
 * tracking changes between a document and its base revision.
 */
class LstCrcSimpleLocalLineStatusTracker(
    project: Project,
    document: Document,
    virtualFile: VirtualFile
) : LocalLineStatusTrackerImpl<Range>(project, document, virtualFile) {

    override val renderer: LocalLineStatusMarkerRenderer = LocalLineStatusMarkerRenderer(this)

    override fun toRange(block: DocumentTracker.Block): Range =
        LstCrcRange(block.start, block.end, block.vcsStart, block.vcsEnd,
            block.ourData.innerRanges, block.ourData.clientIds)

    @RequiresEdt
    override fun setBaseRevision(vcsContent: CharSequence) {
        setBaseRevisionContent(vcsContent, null)
    }

    override val DocumentTracker.Block.ourData: LstCrcBlockData
        get() {
            if (data == null) data = LstCrcBlockData()
            return data as LstCrcBlockData
        }

    /**
     * Stores tracker-specific data for each changed block.
     */
    protected data class LstCrcBlockData(
        override var innerRanges: List<Range.InnerRange>? = null,
        override var clientIds: List<com.intellij.codeWithMe.ClientId> = emptyList()
    ) : LocalBlockData

    /**
     * Represents a single changed range, including Code With Me info.
     */
    private class LstCrcRange(
        line1: Int, line2: Int, vcsLine1: Int, vcsLine2: Int, innerRanges: List<InnerRange>?,
        override val clientIds: List<com.intellij.codeWithMe.ClientId>
    ) : Range(line1, line2, vcsLine1, vcsLine2, innerRanges), LstLocalRange
}
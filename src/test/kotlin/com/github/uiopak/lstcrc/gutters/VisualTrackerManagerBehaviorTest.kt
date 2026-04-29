package com.github.uiopak.lstcrc.gutters

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.vcs.ex.LocalLineStatusTracker.Mode
import com.intellij.openapi.vcs.ex.Range
import com.intellij.openapi.vcs.ex.SimpleLocalLineStatusTracker
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class VisualTrackerManagerBehaviorTest : BasePlatformTestCase() {

    fun testUnderlyingTrackerReportsInsertedRangeForPartialInsertionAgainstExistingBase() {
        val tracker = createTracker(text = "alpha\nbeta\n", baseText = "alpha\n")

        try {
            val ranges = tracker.getRanges() ?: emptyList()

            assertSize(1, ranges)
            assertEquals(Range.INSERTED, ranges.single().type)
        } finally {
            tracker.release()
        }
    }

    fun testUnderlyingTrackerReportsInitialInsertedRangeForWholeNewFileAgainstEmptyBase() {
        val tracker = createTracker(text = "local new file\n", baseText = "")

        try {
            val ranges = tracker.getRanges() ?: emptyList()

            assertSize(1, ranges)
            assertEquals(Range.INSERTED, ranges.single().type)
        } finally {
            tracker.release()
        }
    }

    fun testStandaloneTrackerInstallsGutterHighlightersForWholeNewFile() {
        val file = LightVirtualFile("tracker-highlighters.txt", PlainTextFileType.INSTANCE, "local new file\n")
        myFixture.openFileInEditor(file)
        val tracker = createTracker(file = file, baseText = "")

        try {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            val markupModel = DocumentMarkupModel.forDocument(fileDocument(file), project, true) as MarkupModelEx
            val gutterHighlighters = markupModel.allHighlighters.count { it.lineMarkerRenderer != null }
            assertTrue("Expected standalone tracker to install line marker renderers for a new file", gutterHighlighters > 0)
        } finally {
            tracker.release()
        }
    }

    private fun createTracker(text: String, baseText: String): SimpleLocalLineStatusTracker {
        val file = LightVirtualFile("tracker-behavior.txt", PlainTextFileType.INSTANCE, text)
        return createTracker(file, baseText)
    }

    private fun createTracker(file: LightVirtualFile, baseText: String): SimpleLocalLineStatusTracker {
        val document = fileDocument(file)

        val tracker = ApplicationManager.getApplication().runWriteAction<SimpleLocalLineStatusTracker> {
            SimpleLocalLineStatusTracker.createTracker(project, document, file).also {
                it.mode = Mode(true, true, true)
                it.setBaseRevision(baseText)
            }
        }

        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        return tracker
    }

    private fun fileDocument(file: LightVirtualFile) = FileDocumentManager.getInstance().getDocument(file)!!
}
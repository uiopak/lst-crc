package com.github.uiopak.lstcrc.services

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class GitServiceLineStatsTest : BasePlatformTestCase() {

    fun testCalculateLineStatsForSingleLineReplacement() {
        val stats = calculateLineStats("Base line\n", "Feature tree line\n")

        assertEquals(1, stats.addedLines)
        assertEquals(1, stats.removedLines)
    }

    fun testCalculateLineStatsForNewFileContent() {
        val stats = calculateLineStats("", "First\nSecond\n")

        assertEquals(2, stats.addedLines)
        assertEquals(0, stats.removedLines)
    }

    fun testCalculateLineStatsForDeletedFileContent() {
        val stats = calculateLineStats("First\nSecond\n", "")

        assertEquals(0, stats.addedLines)
        assertEquals(2, stats.removedLines)
    }

    fun testCreateLiveDocumentContentRevisionReadsLatestUnsavedDocumentText() {
        val file = myFixture.addFileToProject("tracked.txt", "base\n").virtualFile
        val document = FileDocumentManager.getInstance().getDocument(file)!!

        WriteCommandAction.runWriteCommandAction(project) {
            document.setText("baseX\n")
        }

        val currentRevision = createLiveDocumentContentRevision(file)

        assertEquals("baseX\n", currentRevision.content)
    }

    fun testCreateLiveDocumentContentRevisionAllowsBackgroundThreadAccess() {
        val file = myFixture.addFileToProject("tracked.txt", "alpha\nbeta\n").virtualFile
        val document = FileDocumentManager.getInstance().getDocument(file)!!

        WriteCommandAction.runWriteCommandAction(project) {
            document.setText("alphaX\nbetaY\n")
        }

        val content = CompletableFuture.supplyAsync {
            createLiveDocumentContentRevision(file).content
        }.get(10, TimeUnit.SECONDS)

        assertEquals("alphaX\nbetaY\n", content)
    }
}
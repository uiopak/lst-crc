package com.github.uiopak.lstcrc.services

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class GitServiceLineStatsTest : BasePlatformTestCase() {

    fun testCalculateLineStatsIgnoresLineEndingOnlyDifferences() {
        val stats = calculateLineStats("First\r\nSecond\r\n", "First\nSecond\n")

        assertEquals(0, stats.addedLines)
        assertEquals(0, stats.removedLines)
    }

    fun testCalculateLineStatsCountsRealChangesWhenLineEndingsAlsoDiffer() {
        val stats = calculateLineStats("Base\r\nSecond\r\n", "Changed\nSecond\n")

        assertEquals(1, stats.addedLines)
        assertEquals(1, stats.removedLines)
    }

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

    fun testTrackedLineStatsDiffArgsIgnoreLineEndingOnlyChurn() {
        val repoPath = Files.createTempDirectory("lstcrc-line-stats-")

        try {
            initializeTrackedStatsGitRepo(repoPath)

            val noisyDiff = runGit(repoPath, "diff", "--numstat", "feature-line-endings")
            val normalizedDiff = runGit(repoPath, "diff", *trackedLineStatsDiffArgs("feature-line-endings").toTypedArray())

            assertTrue(noisyDiff, noisyDiff.lineSequence().any { it == "3\t3\tMain.txt" })
            assertTrue(normalizedDiff, normalizedDiff.lineSequence().any { it == "1\t1\tMain.txt" })
        } finally {
            repoPath.toFile().deleteRecursively()
        }
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

    private fun initializeTrackedStatsGitRepo(projectPath: Path) {
        runGit(projectPath, "init", "--initial-branch=main")
        runGit(projectPath, "config", "user.name", "LST-CRC Tests")
        runGit(projectPath, "config", "user.email", "lst-crc-tests@example.invalid")
        runGit(projectPath, "config", "core.autocrlf", "false")

        Files.writeString(projectPath.resolve(".gitattributes"), "*.txt -text\n")
        Files.writeString(projectPath.resolve("Main.txt"), "alpha\r\nbeta\r\ngamma\r\n")
        runGit(projectPath, "add", ".gitattributes", "Main.txt")
        runGit(projectPath, "commit", "-m", "Initial CRLF content")

        runGit(projectPath, "checkout", "-b", "feature-line-endings")
        Files.writeString(projectPath.resolve("Main.txt"), "alpha changed\nbeta\ngamma\n")
        runGit(projectPath, "add", "Main.txt")
        runGit(projectPath, "commit", "-m", "Feature LF content")
        runGit(projectPath, "checkout", "main")
    }

    private fun runGit(projectPath: Path, vararg args: String): String {
        val process = ProcessBuilder(listOf("git", *args))
            .directory(projectPath.toFile())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()

        assertEquals("git ${args.joinToString(" ")} failed:\n$output", 0, exitCode)
        return output
    }
}
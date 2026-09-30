package com.github.uiopak.lstcrc.services

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.github.uiopak.lstcrc.testsupport.LstCrcTestCase
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitRevisionNumber
import java.lang.reflect.InvocationTargetException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class GitServiceLineStatsTest : LstCrcTestCase() {

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

    fun testLineStatsContentFailureDoesNotDiscardOtherFiles() {
        val failed = Change(
            contentRevision("/repo/Failed.txt") { throw VcsException("Unable to read revision") },
            contentRevision("/repo/Failed.txt") { "changed\n" },
            FileStatus.MODIFIED
        )
        val added = Change(null, contentRevision("/repo/Added.txt") { "first\nsecond\n" }, FileStatus.ADDED)

        assertEquals(mapOf(ChangeLineStatsKey.from(added) to ChangeLineStats(2, 0)), fallbackLineStats(listOf(failed, added)))
    }

    fun testUnavailableRevisionTextDoesNotCountAsAnEmptyFile() {
        val unavailableBefore = Change(
            contentRevision("/repo/Before.txt") { null },
            contentRevision("/repo/Before.txt") { "text\n" },
            FileStatus.MODIFIED
        )
        val unavailableAfter = Change(
            contentRevision("/repo/After.txt") { "text\n" },
            contentRevision("/repo/After.txt") { null },
            FileStatus.MODIFIED
        )

        assertEmpty(fallbackLineStats(listOf(unavailableBefore, unavailableAfter)).entries)
    }

    fun testLineStatsContentCancellationIsPropagated() {
        val cancellations = listOf(
            com.intellij.openapi.progress.ProcessCanceledException(),
            kotlinx.coroutines.CancellationException("Refresh cancelled")
        )
        cancellations.forEach { cancellation ->
            val cancelled = Change(null, contentRevision("/repo/Cancelled.txt") { throw cancellation }, FileStatus.ADDED)
            assertThrows(cancellation.javaClass) {
                fallbackLineStats(listOf(cancelled))
            }
        }
    }

    fun testTrackedBinaryDiffDoesNotGetFallbackLineStats() {
        val root = myFixture.tempDirFixture.findOrCreateDir("repo")
        val output = listOf(":000000 100644 0000000 1111111 A", "Binary.dat", "-\t-\tBinary.dat", "").joinToString("\u0000")
        val parsed = parseTrackedDiff(project, root, GitRevisionNumber("HEAD"), output)
        var contentReads = 0
        val binaryChange = parsed.changes.single()
        val tracked = parsed.copy(changes = listOf(Change(
            binaryChange.beforeRevision,
            contentRevision(binaryChange.afterRevision!!.file.path) { contentReads++; "Binary-marked text\n" },
            binaryChange.fileStatus
        )))
        val repo = java.lang.reflect.Proxy.newProxyInstance(
            git4idea.repo.GitRepository::class.java.classLoader,
            arrayOf(git4idea.repo.GitRepository::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getRoot" -> root
                "isFresh" -> false
                else -> error("Unexpected repository access: ${method.name}")
            }
        } as git4idea.repo.GitRepository
        val keyClass = GitService::class.java.declaredClasses.single { it.simpleName == "DiskChangesKey" }
        val key = keyClass.getDeclaredConstructor(String::class.java, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }.newInstance("HEAD", true, false)
        val disk = GitService::class.java.getDeclaredMethod("buildDiskChanges", git4idea.repo.GitRepository::class.java, keyClass, LoadedChanges::class.java)
            .apply { isAccessible = true }.invoke(GitService(project), repo, key, tracked)
        val loaded = disk.javaClass.getDeclaredMethod("getLoaded").apply { isAccessible = true }.invoke(disk) as LoadedChanges

        assertEquals(1, loaded.changes.size)
        assertEmpty(loaded.lineStatsByChange.entries)
        assertEquals("Git's binary entry must not load content for fallback stats", 0, contentReads)
    }

    fun testTrackedLineStatsDiffArgsIgnoreLineEndingOnlyChurn() {
        val repoPath = Files.createTempDirectory("lstcrc-line-stats-")

        try {
            initializeTrackedStatsGitRepo(repoPath)

            val noisyDiff = runGit(repoPath, "diff", "--numstat", "feature-line-endings")
            val normalizedDiff = runGit(repoPath, "diff", *trackedDiffArgs("feature-line-endings", includeLineStats = true).toTypedArray())

            assertTrue(noisyDiff, noisyDiff.lineSequence().any { it == "3\t3\tMain.txt" })
            assertTrue(normalizedDiff, normalizedDiff.split('\u0000').any { it.trim() == "1\t1\tMain.txt" })
        } finally {
            repoPath.toFile().deleteRecursively()
        }
    }

    fun testTrackedDiffArgsAcceptBranchNamedLikeAFolder() {
        val repoPath = Files.createTempDirectory("lstcrc-ambiguous-branch-")

        try {
            initializeTrackedStatsGitRepo(repoPath)
            Files.createDirectories(repoPath.resolve("docs"))
            Files.writeString(repoPath.resolve("docs/Guide.txt"), "guide\n")
            runGit(repoPath, "add", "docs/Guide.txt")
            runGit(repoPath, "commit", "-m", "Add docs")
            runGit(repoPath, "branch", "docs")
            Files.writeString(repoPath.resolve("docs/Guide.txt"), "guide changed\n")

            // Without the trailing "--", git fails with "ambiguous argument 'docs': both revision and filename".
            val diff = runGit(repoPath, "diff", "--raw", *trackedDiffArgs("docs", includeLineStats = false).toTypedArray())

            assertTrue(diff, diff.split('\u0000').contains("docs/Guide.txt"))
        } finally {
            repoPath.toFile().deleteRecursively()
        }
    }

    fun testRevisionExistsOnlyForResolvableTargets() {
        val repoPath = Files.createTempDirectory("lstcrc-revision-exists-")

        try {
            initializeTrackedStatsGitRepo(repoPath)
            val root = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(repoPath)!!

            assertTrue(revisionExists(project, root, "HEAD"))
            assertTrue(revisionExists(project, root, "feature-line-endings"))
            assertFalse(revisionExists(project, root, "missing-branch"))
            assertFalse(revisionExists(project, root, "f".repeat(40)))
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

    fun testLiveDocumentContentRevisionsAreEqualOnlyForTheSameText() {
        val file = myFixture.addFileToProject("tracked.txt", "base\n").virtualFile
        val document = FileDocumentManager.getInstance().getDocument(file)!!

        WriteCommandAction.runWriteCommandAction(project) { document.setText("edited\n") }
        val first = createLiveDocumentContentRevision(file)
        val second = createLiveDocumentContentRevision(file)
        WriteCommandAction.runWriteCommandAction(project) { document.setText("edited again\n") }
        val third = createLiveDocumentContentRevision(file)

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertFalse(first == third)
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

    fun testUnsavedEditOfAddedFileIncludesLiveTextAndLineStats() {
        val root = myFixture.tempDirFixture.findOrCreateDir("repo")
        val file = myFixture.addFileToProject("repo/Added.txt", "disk\n").virtualFile
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        val repo = java.lang.reflect.Proxy.newProxyInstance(
            git4idea.repo.GitRepository::class.java.classLoader,
            arrayOf(git4idea.repo.GitRepository::class.java)
        ) { _, method, _ ->
            if (method.name == "getRoot") root else error("A new file must not load target content: ${method.name}")
        } as git4idea.repo.GitRepository
        val service = GitService(project)

        WriteCommandAction.runWriteCommandAction(project) { document.setText("first\nsecond\n") }
        val first = service.collectUnsavedDocumentChanges(repo, "HEAD", setOf(file.path), emptyMap()).single()

        assertNull(first.beforeRevision)
        assertEquals(com.intellij.openapi.vcs.FileStatus.ADDED, first.fileStatus)
        assertEquals("first\nsecond\n", first.afterRevision!!.content)
        assertEquals(ChangeLineStats(2, 0), calculateLineStats("", first.afterRevision!!.content!!))

        WriteCommandAction.runWriteCommandAction(project) { document.setText("first\nsecond\nthird\n") }
        val second = service.collectUnsavedDocumentChanges(repo, "HEAD", setOf(file.path), emptyMap()).single()

        assertEquals("first\nsecond\nthird\n", second.afterRevision!!.content)
        assertFalse(first.afterRevision == second.afterRevision)
        assertEquals(ChangeLineStats(3, 0), calculateLineStats("", second.afterRevision!!.content!!))
    }

    fun testUnsavedOverlayCancellationIsPropagated() {
        val root = myFixture.tempDirFixture.findOrCreateDir("repo")
        val file = myFixture.addFileToProject("repo/Cancelled.txt", "base\n").virtualFile
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        WriteCommandAction.runWriteCommandAction(project) { document.setText("unsaved\n") }
        val cancellations = listOf(
            com.intellij.openapi.progress.ProcessCanceledException(),
            kotlinx.coroutines.CancellationException("Overlay cancelled")
        )

        cancellations.forEach { cancellation ->
            val repo = java.lang.reflect.Proxy.newProxyInstance(
                git4idea.repo.GitRepository::class.java.classLoader,
                arrayOf(git4idea.repo.GitRepository::class.java)
            ) { _, method, _ ->
                when (method.name) {
                    "getRoot" -> root
                    "getCurrentRevision" -> throw cancellation
                    else -> error("Unexpected repository access: ${method.name}")
                }
            } as git4idea.repo.GitRepository

            assertThrows(cancellation.javaClass) {
                GitService(project).collectUnsavedDocumentChanges(repo, "HEAD", emptySet(), emptyMap())
            }
        }
    }

    // Regression (round five): an unsaved edit of a moved file was compared with the file's new path, which the
    // target doesn't have, so its line stats never followed the text (and git show failed on every refresh).
    fun testUnsavedEditOfMovedFileIsComparedWithItsOldPath() {
        val repoPath = Files.createTempDirectory("lstcrc-moved-unsaved-")
        var document: com.intellij.openapi.editor.Document? = null

        try {
            initializeTrackedStatsGitRepo(repoPath)
            runGit(repoPath, "mv", "Main.txt", "Moved.txt")
            val head = runGit(repoPath, "rev-parse", "HEAD").trim()
            val root = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(repoPath)!!
            val moved = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(repoPath.resolve("Moved.txt"))!!
            document = FileDocumentManager.getInstance().getDocument(moved)!!
            WriteCommandAction.runWriteCommandAction(project) { document.setText("alpha\nbeta edited\ngamma\n") }
            val repo = java.lang.reflect.Proxy.newProxyInstance(
                git4idea.repo.GitRepository::class.java.classLoader,
                arrayOf(git4idea.repo.GitRepository::class.java)
            ) { _, method, _ ->
                when (method.name) {
                    "getRoot" -> root
                    "getCurrentRevision" -> head
                    "getBranches" -> git4idea.branch.GitBranchesCollection(emptyMap(), emptyMap(), emptyList())
                    else -> null
                }
            } as git4idea.repo.GitRepository
            val oldPath = com.intellij.vcsUtil.VcsUtil.getFilePath(repoPath.resolve("Main.txt").toString(), false)

            val changes = GitService(project).collectUnsavedDocumentChanges(repo, "HEAD", emptySet(), mapOf(moved.path to oldPath))

            assertEquals(1, changes.size)
            assertEquals("alpha\nbeta\ngamma\n", changes.single().beforeRevision?.content)
            assertEquals("alpha\nbeta edited\ngamma\n", changes.single().afterRevision?.content)
        } finally {
            document?.let { doc -> WriteCommandAction.runWriteCommandAction(project) { FileDocumentManager.getInstance().reloadFromDisk(doc) } }
            repoPath.toFile().deleteRecursively()
        }
    }

    private fun contentRevision(path: String, load: () -> String?): ContentRevision = object : ContentRevision {
        override fun getFile() = VcsUtil.getFilePath(path, false)
        override fun getRevisionNumber() = GitRevisionNumber("test")
        override fun getContent() = load()
    }

    @Suppress("UNCHECKED_CAST")
    private fun fallbackLineStats(changes: List<Change>): Map<ChangeLineStatsKey, ChangeLineStats> {
        val method = GitService::class.java.getDeclaredMethod("buildLineStats", List::class.java, Map::class.java, Set::class.java, Set::class.java)
            .apply { isAccessible = true }
        return try {
            method.invoke(GitService(project), changes, emptyMap<ChangeLineStatsKey, ChangeLineStats>(), emptySet<ChangeLineStatsKey>(), emptySet<ChangeLineStatsKey>())
                as Map<ChangeLineStatsKey, ChangeLineStats>
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
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

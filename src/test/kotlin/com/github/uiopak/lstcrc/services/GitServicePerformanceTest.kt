package com.github.uiopak.lstcrc.services

import com.github.uiopak.lstcrc.fixtures.LstCrcPerformanceReport
import com.github.uiopak.lstcrc.services.GitService.Companion.CATEGORIZATION_WORK
import com.github.uiopak.lstcrc.services.GitService.Companion.GIT_DIFF_WORK
import com.github.uiopak.lstcrc.messaging.ActiveDiffDataChangedListener
import com.github.uiopak.lstcrc.messaging.DIFF_DATA_CHANGED_TOPIC
import com.github.uiopak.lstcrc.state.TabInfo
import com.github.uiopak.lstcrc.state.ToolWindowState
import com.github.uiopak.lstcrc.testsupport.LstCrcTestCase
import com.github.uiopak.lstcrc.toolWindow.LstCrcSettingDefinitions
import com.github.uiopak.lstcrc.toolWindow.LstCrcSettingsService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryImpl
import kotlinx.coroutines.runBlocking
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.measureTime

class GitServicePerformanceTest : LstCrcTestCase() {
    override fun runInDispatchThread(): Boolean = false

    fun testRefreshesReusePreparedDiskWork() = withRepository { repo, _ ->
        val root = repo.root
        val service = GitService(project)
        val counts = ConcurrentHashMap<String, AtomicInteger>()
        var document: com.intellij.openapi.editor.Document? = null
        val diffData = project.service<ProjectActiveDiffDataService>()
        val tabState = project.service<ToolWindowStateService>()
        val previousTabState = tabState.state
        ApplicationManager.getApplication().invokeAndWait { tabState.loadState(ToolWindowState()) }
        val notifications = AtomicInteger()
        project.messageBus.connect(testRootDisposable).subscribe(DIFF_DATA_CHANGED_TOPIC, ActiveDiffDataChangedListener {
            notifications.incrementAndGet()
        })
        val threadBean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        var edtBytes = 0L
        val measuredWork = mutableMapOf<String, Map<String, Int>>()
        fun apply(result: GetChangesResult) {
            ApplicationManager.getApplication().invokeAndWait {
                val before = threadBean.getThreadAllocatedBytes(Thread.currentThread().threadId())
                diffData.updateActiveDiff("HEAD", result.categorizedChanges)
                edtBytes += threadBean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before
                assertSame("The measured snapshot must be accepted by the selected tab",
                    result.categorizedChanges, diffData.categorizedChanges)
            }
        }
        fun report(step: String, action: () -> Unit) {
            counts.clear()
            notifications.set(0)
            edtBytes = 0
            val start = Instant.now()
            val duration = measureTime(action)
            val end = Instant.now()
            measuredWork[step] = counts.mapValues { it.value.get() }
            LstCrcPerformanceReport.record("unit-200-files", step, duration,
                "$counts; diff notifications=${notifications.get()}; EDT allocated bytes=$edtBytes; UTC=$start..$end")
        }
        try {
            service.setLoadObserverForTest(listOf(repo)) { work, size ->
                counts.computeIfAbsent(work) { AtomicInteger() }.addAndGet(size)
            }
            report("full refresh with line stats") {
                val result = runBlocking { service.getChanges(null) }
                assertEquals(200, result.categorizedChanges.allChanges.size)
                apply(result)
            }
            report("20 edit-only refreshes without unsaved files") {
                repeat(20) { apply(runBlocking { service.getChanges(null, reuseDiskChanges = true) }) }
            }
            val file = root.findChild("file0.txt")!!
            ApplicationManager.getApplication().invokeAndWait {
                document = FileDocumentManager.getInstance().getDocument(file)!!
                WriteCommandAction.runWriteCommandAction(project) { document!!.setText("unsaved\nsecond\n") }
            }
            report("20 typing refreshes with one unsaved file") {
                repeat(20) { i ->
                    ApplicationManager.getApplication().invokeAndWait {
                        WriteCommandAction.runWriteCommandAction(project) { document!!.setText("unsaved-$i\nsecond\n") }
                    }
                    apply(runBlocking { service.getChanges(null, reuseDiskChanges = true) })
                }
            }
            val full = measuredWork.getValue("full refresh with line stats")
            assertEquals(1, full[GIT_DIFF_WORK] ?: 0)
            assertEquals(200, full[CATEGORIZATION_WORK] ?: 0)
            val unchanged = measuredWork.getValue("20 edit-only refreshes without unsaved files")
            assertEquals(0, unchanged[CATEGORIZATION_WORK] ?: 0)
            assertEquals(0, unchanged[GIT_DIFF_WORK] ?: 0)
            val typing = measuredWork.getValue("20 typing refreshes with one unsaved file")
            assertEquals("Each distinct edit must categorize the new content", 4000, typing[CATEGORIZATION_WORK] ?: 0)
            assertEquals(0, typing[GIT_DIFF_WORK] ?: 0)
        } finally {
            service.setLoadObserverForTest(null)
            ApplicationManager.getApplication().invokeAndWait {
                document?.let { FileDocumentManager.getInstance().reloadFromDisk(it) }
                tabState.loadState(previousTabState)
            }
        }
    }

    fun testPreparedResultsInvalidateForTargetsSettingsAndDiskReloads() = withRepository { repo, repoPath ->
        val gitService = GitService(project)
        val settings = service<LstCrcSettingsService>()
        val commands = AtomicInteger()
        fun load(tab: TabInfo? = null, reuse: Boolean = true) =
            runBlocking { gitService.getChanges(tab, reuseDiskChanges = reuse) }
        try {
            val discoveredRepositories = gitService.getRepositories().toList()
            gitService.setLoadObserverForTest(listOf(repo)) { work, size ->
                if (work == GIT_DIFF_WORK) commands.addAndGet(size)
            }
            assertEquals("Test loads must not override public repository discovery",
                discoveredRepositories, gitService.getRepositories())
            val head = load(reuse = false)
            assertSame(head.categorizedChanges, load().categorizedChanges)
            assertEquals(1, commands.get())

            val branch = load(TabInfo("feature"))
            assertEquals("feature", branch.categorizedChanges.comparisonContext[repo.root.path])
            assertSame(branch.categorizedChanges, load(TabInfo("feature")).categorizedChanges)
            assertEquals(2, commands.get())

            settings[LstCrcSettingDefinitions.SHOW_LINE_STATS_IN_TREE] = false
            assertFalse(load(TabInfo("feature")).categorizedChanges.lineStatsIncluded)
            assertEquals(3, commands.get())

            Files.writeString(repoPath.resolve("untracked.txt"), "new file\n")
            settings[LstCrcSettingDefinitions.SHOW_UNTRACKED_FILES_AS_NEW] = true
            assertEquals(201, load(TabInfo("feature")).categorizedChanges.allChanges.size)
            assertEquals(4, commands.get())

            Files.writeString(repoPath.resolve("file0.txt"), "base\nsecond\n")
            assertEquals(201, load(TabInfo("feature")).categorizedChanges.allChanges.size)
            assertEquals(200, load(TabInfo("feature"), reuse = false).categorizedChanges.allChanges.size)
            assertEquals(5, commands.get())

            repeat(2) {
                val missing = load(TabInfo("missing-target"))
                assertEquals("missing-target", missing.failures[repo])
            }
            assertEquals("Missing targets are retried rather than reused as successful data", 7, commands.get())
            println("[lstcrc-work] target/settings/full refresh invalidations: 7 git diffs, 0 on identical edit-only loads")
        } finally {
            gitService.setLoadObserverForTest(null)
        }
    }

    private fun withRepository(test: (GitRepository, Path) -> Unit) {
        val repoPath = Files.createTempDirectory("lstcrc-perf-")
        fun git(vararg args: String) {
            val process = ProcessBuilder("git", *args).directory(repoPath.toFile()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            assertEquals(output, 0, process.waitFor())
        }
        git("init", "--initial-branch=main")
        git("config", "user.name", "LST-CRC Tests")
        git("config", "user.email", "lst-crc-tests@example.invalid")
        git("config", "core.autocrlf", "false")
        repeat(200) { Files.writeString(repoPath.resolve("file$it.txt"), "base\nsecond\n") }
        git("add", ".")
        git("commit", "-m", "Initial files")
        git("branch", "feature")
        repeat(200) { Files.writeString(repoPath.resolve("file$it.txt"), "changed\nsecond\n") }
        val root = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(repoPath)!!
        val repo = GitRepositoryImpl.createInstance(root, project, testRootDisposable)!!
        val settings = service<LstCrcSettingsService>()
        val previousSettings = settings.state.copy(values = settings.state.values.toMutableMap())
        settings[LstCrcSettingDefinitions.SHOW_LINE_STATS_IN_TREE] = true
        settings[LstCrcSettingDefinitions.SHOW_UNTRACKED_FILES_AS_NEW] = false
        try {
            test(repo, repoPath)
        } finally {
            settings.loadState(previousSettings)
            ApplicationManager.getApplication().invokeAndWait { Disposer.dispose(repo) }
            // This directory is created and owned by this test.
            repoPath.toFile().deleteRecursively()
        }
    }
}

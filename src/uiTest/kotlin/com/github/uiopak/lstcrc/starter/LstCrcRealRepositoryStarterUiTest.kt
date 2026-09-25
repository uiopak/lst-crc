package com.github.uiopak.lstcrc.starter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Scenario tests on a real repository ([GsonFixture]: google/gson at pinned release commits).
 *
 * Every expectation is computed from git itself (`git diff --name-status -M`, `--numstat -z`), so the
 * tests check that the plugin reports exactly what git reports: created/modified/moved/deleted files,
 * line stats, scope membership and search results. Each step is also timed into
 * [LstCrcPerformanceReport]; timings never fail a test, only the hang-level timeouts do.
 */
@Tag("starter")
class LstCrcRealRepositoryStarterUiTest : LstCrcStarterUiTestBase() {

    @Test
    fun testBranchComparisonsMatchGit() = runStarterUiTest(prepareProject = { GsonFixture.copyInto(path) }) {
        val test = "gson-branch-comparisons"
        startOnGsonFixture()
        val git = GitOracle(project)

        openToolWindowOnHead(test)

        // 2.11.0 -> 2.13.1: ~200 changes including 30 renames.
        assertComparisonMatchesGit(test, "gson-2.11.0", git, action = { ui.createAndSelectTab("gson-2.11.0") })
        assertScopesMatchGit("gson-2.11.0", git)
        assertFindInFilesMatchesGit(test, "gson-2.11.0", git, text = "Gson")

        // Turning line stats on reloads the data; the counts must equal git's numstat.
        measureUntil(test, "load line stats vs gson-2.11.0", action = {
            ui.setTreeContextSettings(showSingleRepo = null, showCommits = null, showLineStats = true)
        }) { ui.activeDiffEntries().lineSequence().first().endsWith("lineStats=true") }
        assertLineStatsMatchGit("gson-2.11.0", git)

        // 2.10.1 -> 2.13.1: ~300 changes, with line stats.
        assertComparisonMatchesGit(test, "gson-2.10.1", git, action = { ui.createAndSelectTab("gson-2.10.1") })
        assertLineStatsMatchGit("gson-2.10.1", git)

        // Switching back reloads the other comparison.
        assertComparisonMatchesGit(test, "gson-2.11.0", git, step = "switch tab back to gson-2.11.0",
            action = { ui.selectTab("gson-2.11.0") })
    }

    @Test
    fun testCheckoutAndLocalEditsUpdateComparison() = runStarterUiTest(prepareProject = { GsonFixture.copyInto(path) }) {
        val test = "gson-checkout-and-edits"
        startOnGsonFixture()
        val git = GitOracle(project)
        openToolWindowOnHead(test)

        assertComparisonMatchesGit(test, "gson-2.11.0", git, action = { ui.createAndSelectTab("gson-2.11.0") })

        // Moving the working copy to another release changes every comparison.
        assertComparisonMatchesGit(test, "gson-2.11.0", git, step = "refresh after checkout of gson-2.12.1", action = {
            project.checkout("gson-2.12.1")
            ui.refreshProjectAfterExternalChange()
        })
        assertScopesMatchGit("gson-2.11.0", git)

        // Local edits on top: modify, add (intent-to-add), delete and rename tracked files.
        assertComparisonMatchesGit(test, "gson-2.11.0", git, step = "refresh after local edits", action = {
            project.writeFile("README.md", project.path.resolve("README.md").readText() + "\nLocal edit.\n")
            project.writeFile("gson/src/main/java/com/google/gson/LstCrcLocalNew.java", "class LstCrcLocalNew {}\n")
            project.runGit("rm", "--quiet", "ReleaseProcess.md")
            project.runGit("mv", "Troubleshooting.md", "extras/TroubleshootingMoved.md")
            ui.refreshProjectAfterExternalChange()
        })
        assertScopesMatchGit("gson-2.11.0", git)

        // The HEAD tab shows exactly the local edits.
        assertComparisonMatchesGit(test, "HEAD", git, step = "switch to HEAD tab with local edits",
            action = { ui.selectTab("HEAD") })
    }

    private fun LstCrcStarterContext.startOnGsonFixture() {
        prepareLstCrc()
        ui.activateGitVcsIntegration()
        waitUntil(60.seconds) { ui.isGitVcsActive() }
        ui.refreshProjectAfterExternalChange()
    }

    /**
     * Opens the tool window and selects HEAD. The fixture is already a git repository when the IDE
     * opens it, so on first open the plugin also creates and selects a tab for the current branch.
     */
    private fun LstCrcStarterContext.openToolWindowOnHead(test: String) {
        measureUntil(test, "open tool window", action = { openGitChangesView() }) { ui.hasTab("HEAD") }
        ui.selectTab("HEAD")
        waitForSelectedTab("HEAD", 60.seconds)
    }

    /** Runs [action], waits until [condition] holds and records how long that took. */
    private fun LstCrcStarterContext.measureUntil(
        test: String,
        step: String,
        detail: String = "",
        timeout: Duration = 180.seconds,
        action: () -> Unit = {},
        condition: () -> Boolean
    ): Duration {
        val start = TimeSource.Monotonic.markNow()
        action()
        waitUntil(timeout, 100.milliseconds, condition)
        return start.elapsedNow().also { LstCrcPerformanceReport.record(test, step, it, detail) }
    }

    private fun LstCrcStarterContext.assertComparisonMatchesGit(
        test: String,
        target: String,
        git: GitOracle,
        step: String = "load tab vs $target",
        action: () -> Unit = {}
    ) {
        val expected = git.entries(target).map(GitOracle.Entry::line)
        var actual = emptyList<String>()
        val matched = runCatching {
            measureUntil(test, step, "${expected.size} changes", action = action) {
                val lines = ui.activeDiffEntries().lines()
                actual = lines.drop(1).filterNot { it.startsWith("S\t") }
                lines.first().startsWith("branch=$target|") && actual == expected
            }
        }.isSuccess
        if (!matched) {
            assertEquals(expected.joinToString("\n"), actual.joinToString("\n"), "Comparison with $target differs from git")
        }
    }

    private fun LstCrcStarterContext.assertLineStatsMatchGit(target: String, git: GitOracle) {
        val expected = git.lineStats(target)
        var actual = emptyList<String>()
        val matched = runCatching {
            waitUntil(120.seconds, 100.milliseconds) {
                val lines = ui.activeDiffEntries().lines()
                actual = lines.filter { it.startsWith("S\t") && it.split('\t').let { f -> f[1] !in git.binaryPaths(target) && f[2] !in git.binaryPaths(target) } }
                lines.first().startsWith("branch=$target|lineStats=true") && actual == expected
            }
        }.isSuccess
        if (!matched) {
            assertEquals(expected.joinToString("\n"), actual.joinToString("\n"), "Line stats vs $target differ from git numstat")
        }
    }

    private fun LstCrcStarterContext.assertScopesMatchGit(target: String, git: GitOracle) {
        val entries = git.entries(target)
        val candidates = entries.flatMap { listOfNotNull(it.before, it.after) }.distinct().joinToString("\n")
        fun inScope(scopeId: String) = ui.filesMatchingScope(scopeId, candidates).lines().filter(String::isNotBlank).toSet()
        fun afterPaths(vararg statuses: Char) = entries.filter { it.status in statuses }.mapNotNull { it.after }.toSet()

        assertEquals(afterPaths('A'), inScope("LSTCRC.Created"), "Created scope vs $target")
        assertEquals(afterPaths('M'), inScope("LSTCRC.Modified"), "Modified scope vs $target")
        assertEquals(afterPaths('R'), inScope("LSTCRC.Moved"), "Moved scope vs $target")
        assertEquals(entries.filter { it.status == 'D' }.mapNotNull { it.before }.toSet(), inScope("LSTCRC.Deleted"), "Deleted scope vs $target")
        assertEquals(afterPaths('A', 'M', 'R'), inScope("LSTCRC.Changed"), "Changed scope vs $target")
    }

    private fun LstCrcStarterContext.assertFindInFilesMatchesGit(test: String, target: String, git: GitOracle, text: String) {
        val binary = git.binaryPaths(target)
        val expected = git.entries(target)
            .filter { it.status != 'D' }
            .mapNotNull { it.after }
            .filter { it !in binary }
            .filter { path -> project.path.resolve(path).let { it.isRegularFile() && text in it.readText() } }
            .sorted()

        val start = TimeSource.Monotonic.markNow()
        val actual = ui.findInFilesPaths(text, "LSTCRC: Changed Files").lines().filter(String::isNotBlank)
        LstCrcPerformanceReport.record(test, "find in files '$text' in changed files vs $target", start.elapsedNow(), "${actual.size} files")

        assertEquals(expected.joinToString("\n"), actual.joinToString("\n"), "Find in Files '$text' in 'LSTCRC: Changed Files' vs $target")
    }

    /** Expected results computed with the git CLI, using the same diff options as the plugin. */
    private class GitOracle(private val project: LstCrcStarterProject) {

        /** One change as the bridge reports it: `A path`, `M path`, `D path` or `R old new`. */
        data class Entry(val status: Char, val before: String?, val after: String?) {
            val line: String
                get() = when (status) {
                    'A' -> "A\t$after"
                    'D' -> "D\t$before"
                    'M' -> "M\t$after"
                    else -> "R\t$before\t$after"
                }
        }

        fun entries(target: String): List<Entry> =
            project.runGit("diff", "--name-status", "-M", "--diff-filter=ADCMRUXT", target)
                .lineSequence()
                .filter(String::isNotBlank)
                .mapNotNull { line ->
                    val fields = line.split('\t')
                    when (fields[0].first()) {
                        'A' -> Entry('A', null, fields[1])
                        'D' -> Entry('D', fields[1], null)
                        'M', 'T', 'U', 'X' -> Entry('M', fields[1], fields[1])
                        'R', 'C' -> Entry('R', fields[1], fields[2])
                        else -> null
                    }
                }
                .sortedBy(Entry::line)
                .toList()

        /** `S<TAB>before<TAB>after<TAB>added<TAB>removed` lines, sorted; binary files are skipped. */
        fun lineStats(target: String): List<String> {
            val byPath = entries(target).flatMap { entry -> listOfNotNull(entry.before, entry.after).map { it to entry } }.toMap()
            return numstat(target)
                .filter { it.added != null && it.removed != null }
                .mapNotNull { record ->
                    val entry = if (record.oldPath != null) Entry('R', record.oldPath, record.path) else byPath[record.path] ?: return@mapNotNull null
                    "S\t${entry.before.orEmpty()}\t${entry.after.orEmpty()}\t${record.added}\t${record.removed}"
                }
                .sorted()
        }

        fun binaryPaths(target: String): Set<String> =
            numstat(target).filter { it.added == null }.flatMap { listOfNotNull(it.oldPath, it.path) }.toSet()

        private data class NumstatRecord(val added: Int?, val removed: Int?, val oldPath: String?, val path: String)

        private fun numstat(target: String): List<NumstatRecord> {
            val fields = project.runGit("diff", "--numstat", "-z", "-M", "--diff-filter=ADCMRUXT", "--ignore-cr-at-eol", target)
                .split('\u0000')
                .iterator()
            val records = mutableListOf<NumstatRecord>()
            while (fields.hasNext()) {
                val header = fields.next().trim('\n').split('\t')
                if (header.size < 3) continue
                records += if (header[2].isEmpty()) {
                    NumstatRecord(header[0].toIntOrNull(), header[1].toIntOrNull(), fields.next(), fields.next())
                } else {
                    NumstatRecord(header[0].toIntOrNull(), header[1].toIntOrNull(), null, header[2])
                }
            }
            return records
        }
    }
}

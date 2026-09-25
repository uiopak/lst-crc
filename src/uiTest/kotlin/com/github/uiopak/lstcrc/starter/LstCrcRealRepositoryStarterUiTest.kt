package com.github.uiopak.lstcrc.starter

import com.github.uiopak.lstcrc.fixtures.GitDiffOracle
import com.github.uiopak.lstcrc.fixtures.GsonFixture
import com.github.uiopak.lstcrc.fixtures.LstCrcPerformanceReport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.io.path.readText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Scenario tests on a real repository ([GsonFixture]: google/gson at pinned release commits).
 *
 * Every expectation is computed from git itself ([GitDiffOracle]), so the tests check that the plugin
 * reports exactly what git reports: created/modified/moved/deleted files, line stats, scope membership
 * and search results. Each step is also timed into [LstCrcPerformanceReport]; timings never fail a
 * test, only the hang-level timeouts do.
 *
 * The Remote Robot suite runs the same scenarios on Linux, Windows and macOS (`LstCrcRealRepositoryUiTest`).
 */
@Tag("starter")
class LstCrcRealRepositoryStarterUiTest : LstCrcStarterUiTestBase() {

    @Test
    fun testBranchComparisonsMatchGit() = runStarterUiTest(prepareProject = { GsonFixture.copyInto(path) }) {
        val test = "gson-branch-comparisons"
        startOnGsonFixture()
        val git = GitDiffOracle(project.path)

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
        val git = GitDiffOracle(project.path)
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
        detail: () -> String = { "" },
        timeout: Duration = 180.seconds,
        action: () -> Unit = {},
        condition: () -> Boolean
    ): Duration {
        val start = TimeSource.Monotonic.markNow()
        action()
        waitUntil(timeout, 100.milliseconds, condition)
        return start.elapsedNow().also { LstCrcPerformanceReport.record(test, step, it, detail()) }
    }

    private fun LstCrcStarterContext.assertComparisonMatchesGit(
        test: String,
        target: String,
        git: GitDiffOracle,
        step: String = "load tab vs $target",
        action: () -> Unit = {}
    ) {
        // Asked after [action], which may move the working copy (checkout, local edits).
        val expected by lazy { git.entries(target).map(GitDiffOracle.Entry::line) }
        var actual = emptyList<String>()
        val matched = runCatching {
            measureUntil(test, step, detail = { "${expected.size} changes" }, action = action) {
                val lines = ui.activeDiffEntries().lines()
                actual = lines.drop(1).filterNot { it.startsWith("S\t") }
                lines.first().startsWith("branch=$target|") && actual == expected
            }
        }.isSuccess
        if (!matched) {
            assertEquals(expected.joinToString("\n"), actual.joinToString("\n"), "Comparison with $target differs from git")
        }
    }

    private fun LstCrcStarterContext.assertLineStatsMatchGit(target: String, git: GitDiffOracle) {
        val expected = git.lineStats(target)
        val binary = git.binaryPaths(target)
        var actual = emptyList<String>()
        val matched = runCatching {
            waitUntil(120.seconds, 100.milliseconds) {
                val lines = ui.activeDiffEntries().lines()
                actual = lines.filter { it.startsWith("S\t") && it.split('\t').let { f -> f[1] !in binary && f[2] !in binary } }
                lines.first().startsWith("branch=$target|lineStats=true") && actual == expected
            }
        }.isSuccess
        if (!matched) {
            assertEquals(expected.joinToString("\n"), actual.joinToString("\n"), "Line stats vs $target differ from git numstat")
        }
    }

    private fun LstCrcStarterContext.assertScopesMatchGit(target: String, git: GitDiffOracle) {
        val candidates = git.scopeCandidates(target).joinToString("\n")
        git.expectedScopes(target).forEach { (scopeId, expected) ->
            val actual = ui.filesMatchingScope(scopeId, candidates).lines().filter(String::isNotBlank).toSet()
            assertEquals(expected, actual, "$scopeId scope vs $target")
        }
    }

    private fun LstCrcStarterContext.assertFindInFilesMatchesGit(test: String, target: String, git: GitDiffOracle, text: String) {
        val expected = git.expectedFindInFiles(target, text)

        val start = TimeSource.Monotonic.markNow()
        val actual = ui.findInFilesPaths(text, "LSTCRC: Changed Files").lines().filter(String::isNotBlank)
        LstCrcPerformanceReport.record(test, "find in files '$text' in changed files vs $target", start.elapsedNow(), "${actual.size} files")

        assertEquals(expected.joinToString("\n"), actual.joinToString("\n"), "Find in Files '$text' in 'LSTCRC: Changed Files' vs $target")
    }
}

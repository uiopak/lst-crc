package com.github.uiopak.lstcrc.plugin

import com.automation.remarks.junit5.Video
import com.github.uiopak.lstcrc.fixtures.GitDiffOracle
import com.github.uiopak.lstcrc.fixtures.LstCrcPerformanceReport
import com.github.uiopak.lstcrc.plugin.pages.IdeaFrame
import com.github.uiopak.lstcrc.plugin.pages.activeDiffEntries
import com.github.uiopak.lstcrc.plugin.pages.branchSelection
import com.github.uiopak.lstcrc.plugin.pages.filesMatchingScope
import com.github.uiopak.lstcrc.plugin.pages.findInFilesPaths
import com.github.uiopak.lstcrc.plugin.pages.gitChangesView
import com.github.uiopak.lstcrc.plugin.pages.idea
import com.github.uiopak.lstcrc.plugin.pages.isGitRepositoryDetected
import com.github.uiopak.lstcrc.plugin.pages.refreshAfterExternalChange
import com.github.uiopak.lstcrc.plugin.utils.openGsonFixtureProject
import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.waitFor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.TimeSource

/**
 * Scenario tests on a real repository (`GsonFixture`: google/gson at pinned release commits), driven
 * through the real UI on Linux, Windows and macOS: tabs are added with the "+" button and the branch
 * tree, and external changes reach the plugin only through the IDE's own VFS/VCS refresh.
 *
 * Expectations come from git itself ([GitDiffOracle]); the IDE Starter suite runs the same scenarios
 * (`LstCrcRealRepositoryStarterUiTest`). Each step is timed into [LstCrcPerformanceReport], per OS.
 */
@LstCrcUiTest
class LstCrcRealRepositoryUiTest : LstCrcUiTestSupport() {

    @Test
    @Video
    fun testBranchComparisonsMatchGit(remoteRobot: RemoteRobot) = with(remoteRobot) {
        val test = "gson-branch-comparisons"
        val git = startOnGsonFixture(test)

        idea {
            // 2.11.0 -> 2.13.1: ~200 changes including 30 renames.
            assertComparisonMatchesGit(test, "gson-2.11.0", git) { addTabFor("gson-2.11.0") }
            assertScopesMatchGit("gson-2.11.0", git)
            assertFindInFilesMatchesGit(test, "gson-2.11.0", git, text = "Gson")

            // Turning line stats on reloads the data; the counts must equal git's numstat.
            measureUntil(test, "load line stats vs gson-2.11.0", action = { setTreeContextSettings(showLineStats = true) }) {
                activeDiffEntries().lineSequence().first().endsWith("lineStats=true")
            }
            assertLineStatsMatchGit("gson-2.11.0", git)

            // 2.10.1 -> 2.13.1: ~300 changes, with line stats.
            assertComparisonMatchesGit(test, "gson-2.10.1", git) { addTabFor("gson-2.10.1") }
            assertLineStatsMatchGit("gson-2.10.1", git)

            // Switching back reloads the other comparison.
            assertComparisonMatchesGit(test, "gson-2.11.0", git, step = "switch tab back to gson-2.11.0") {
                gitChangesView { selectTab("gson-2.11.0") }
            }
        }
    }

    @Test
    @Video
    fun testCheckoutAndLocalEditsUpdateComparison(remoteRobot: RemoteRobot) = with(remoteRobot) {
        val test = "gson-checkout-and-edits"
        val git = startOnGsonFixture(test)
        val projectDir = git.repoDir

        idea {
            assertComparisonMatchesGit(test, "gson-2.11.0", git) { addTabFor("gson-2.11.0") }

            // Moving the working copy to another release changes every comparison.
            assertComparisonMatchesGit(test, "gson-2.11.0", git, step = "refresh after checkout of gson-2.12.1") {
                git.git("checkout", "--quiet", "gson-2.12.1")
                refreshAfterExternalChange()
            }
            assertScopesMatchGit("gson-2.11.0", git)

            // Local edits on top: modify, add (intent-to-add), delete and rename tracked files.
            assertComparisonMatchesGit(test, "gson-2.11.0", git, step = "refresh after local edits") {
                projectDir.resolve("README.md").let { it.writeText(it.readText() + "\nLocal edit.\n") }
                projectDir.resolve("gson/src/main/java/com/google/gson/LstCrcLocalNew.java").apply {
                    parent.createDirectories()
                    writeText("class LstCrcLocalNew {}\n")
                }
                git.git("add", "--intent-to-add", "gson/src/main/java/com/google/gson/LstCrcLocalNew.java")
                git.git("rm", "--quiet", "ReleaseProcess.md")
                git.git("mv", "Troubleshooting.md", "extras/TroubleshootingMoved.md")
                refreshAfterExternalChange()
            }
            assertScopesMatchGit("gson-2.11.0", git)

            // The HEAD tab shows exactly the local edits.
            assertComparisonMatchesGit(test, "HEAD", git, step = "switch to HEAD tab with local edits") {
                gitChangesView { selectTab("HEAD") }
            }
        }
    }

    /**
     * Opens a fresh copy of the fixture, waits until the plugin has found its repository, and shows
     * the tool window on HEAD. The fixture is already a git repository when the IDE opens it, so on
     * first open the plugin also creates and selects a tab for the current branch.
     */
    private fun RemoteRobot.startOnGsonFixture(test: String): GitDiffOracle {
        val git = GitDiffOracle(openGsonFixtureProject())
        idea {
            step("Wait for smart mode") { dumbAware(Duration.ofMinutes(5)) {} }
            waitFor(Duration.ofMinutes(2), interval = Duration.ofSeconds(1)) { isGitRepositoryDetected() }
            resetGitChangesViewState()

            var headTabShown = false
            measureUntil(test, "open tool window", action = { openGitChangesView() }) {
                gitChangesView { headTabShown = hasTab("HEAD") }
                headTabShown
            }
            gitChangesView { selectTab("HEAD") }
        }
        return git
    }

    /** Adds a comparison tab through the "+" button and the branch tree, and selects it. */
    private fun IdeaFrame.addTabFor(branchName: String) {
        gitChangesView { addTab() }
        branchSelection { searchAndSelect(branchName) }
        gitChangesView { selectTab(branchName) }
    }

    /** Runs [action], waits until [condition] holds and records how long that took. */
    private fun RemoteRobot.measureUntil(
        test: String,
        step: String,
        detail: () -> String = { "" },
        timeout: Duration = Duration.ofMinutes(3),
        action: () -> Unit = {},
        condition: () -> Boolean
    ) {
        val start = TimeSource.Monotonic.markNow()
        action()
        waitFor(timeout, interval = Duration.ofMillis(200), condition = condition)
        LstCrcPerformanceReport.record(test, step, start.elapsedNow(), detail())
    }

    private fun IdeaFrame.assertComparisonMatchesGit(
        test: String,
        target: String,
        git: GitDiffOracle,
        step: String = "load tab vs $target",
        action: () -> Unit
    ) = step("Comparison with $target matches git ($step)") {
        // Asked after [action], which may move the working copy (checkout, local edits).
        val expected by lazy { git.entries(target).map(GitDiffOracle.Entry::line) }
        var actual = emptyList<String>()
        val matched = runCatching {
            remoteRobot.measureUntil(test, step, detail = { "${expected.size} changes" }, action = action) {
                val lines = remoteRobot.activeDiffEntries().lines()
                actual = lines.drop(1).filterNot { it.startsWith("S\t") }
                lines.first().startsWith("branch=$target|") && actual == expected
            }
        }.isSuccess
        if (!matched) {
            assertEquals(expected.joinToString("\n"), actual.joinToString("\n"), "Comparison with $target differs from git")
        }
    }

    private fun IdeaFrame.assertLineStatsMatchGit(target: String, git: GitDiffOracle) = step("Line stats vs $target match git") {
        val expected = git.lineStats(target)
        val binary = git.binaryPaths(target)
        var actual = emptyList<String>()
        val matched = runCatching {
            waitFor(Duration.ofMinutes(2), interval = Duration.ofMillis(200)) {
                val lines = remoteRobot.activeDiffEntries().lines()
                actual = lines.filter { it.startsWith("S\t") && it.split('\t').let { f -> f[1] !in binary && f[2] !in binary } }
                lines.first().startsWith("branch=$target|lineStats=true") && actual == expected
            }
        }.isSuccess
        if (!matched) {
            assertEquals(expected.joinToString("\n"), actual.joinToString("\n"), "Line stats vs $target differ from git numstat")
        }
    }

    private fun IdeaFrame.assertScopesMatchGit(target: String, git: GitDiffOracle) = step("Scopes vs $target match git") {
        val candidates = git.scopeCandidates(target)
        git.expectedScopes(target).forEach { (scopeId, expected) ->
            assertEquals(expected, remoteRobot.filesMatchingScope(scopeId, candidates), "$scopeId scope vs $target")
        }
    }

    private fun IdeaFrame.assertFindInFilesMatchesGit(test: String, target: String, git: GitDiffOracle, text: String) =
        step("Find in Files '$text' vs $target matches git") {
            val expected = git.expectedFindInFiles(target, text)

            val start = TimeSource.Monotonic.markNow()
            val actual = remoteRobot.findInFilesPaths(text, "LSTCRC: Changed Files")
            LstCrcPerformanceReport.record(test, "find in files '$text' in changed files vs $target", start.elapsedNow(), "${actual.size} files")

            assertEquals(expected.joinToString("\n"), actual.joinToString("\n"), "Find in Files '$text' in 'LSTCRC: Changed Files' vs $target")
        }
}


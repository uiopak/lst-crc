package com.github.uiopak.lstcrc.services

import com.github.uiopak.lstcrc.state.TabInfo
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import git4idea.repo.GitRepository

class GitServiceComparisonTargetTest : BasePlatformTestCase() {

    fun testBranchOverrideUsesRevisionComparisonPath() {
        val gitService = GitService(project)

        assertFalse(gitService.shouldCompareAgainstWorkingTree("feature-a", "origin/main"))
        assertFalse(gitService.isExplicitRevisionTarget("origin/main"))
    }

    fun testExplicitCommitHashUsesRevisionComparisonPath() {
        val gitService = GitService(project)
        val commitHash = "0123456789abcdef0123456789abcdef01234567"

        assertFalse(gitService.shouldCompareAgainstWorkingTree("feature-a", commitHash))
        assertTrue(gitService.isExplicitRevisionTarget(commitHash))
    }

    fun testExplicitRepoOverrideUsesWorkingTreeWhenPrimaryRevisionIsMissingInRepo() {
        val gitService = GitService(project)

        assertTrue(gitService.shouldCompareAgainstWorkingTree("feature-a", "repair-target", primaryRevisionExistsInRepo = false))
    }

    fun testTabPrimaryRevisionStillUsesWorkingTreeWhenTargetsMatch() {
        val gitService = GitService(project)
        val commitHash = "0123456789abcdef0123456789abcdef01234567"

        assertTrue(gitService.shouldCompareAgainstWorkingTree(commitHash, commitHash))
    }

    fun testResolveComparisonTargetPrecedence() {
        val gitService = GitService(project)
        val mockRootFile = LightVirtualFile("repo-root")
        val mockRepo = java.lang.reflect.Proxy.newProxyInstance(
            GitRepository::class.java.classLoader,
            arrayOf(GitRepository::class.java)
        ) { _, method, _ ->
            if (method.name == "getRoot") {
                mockRootFile
            } else {
                null
            }
        } as GitRepository

        // 1. With null tabInfo, comparison target is HEAD
        assertEquals("HEAD", gitService.resolveComparisonTarget(mockRepo, null))

        // 2. With tabInfo, comparison target defaults to primary branch name
        val tabInfoDefault = TabInfo("feature-x")
        assertEquals("feature-x", gitService.resolveComparisonTarget(mockRepo, tabInfoDefault))

        // 3. With explicit override in comparisonMap, comparison target uses the override
        val tabInfoOverride = TabInfo("feature-x", comparisonMap = mutableMapOf(mockRootFile.path to "override-branch"))
        assertEquals("override-branch", gitService.resolveComparisonTarget(mockRepo, tabInfoOverride))
    }
}
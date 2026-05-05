package com.github.uiopak.lstcrc.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class GitServiceComparisonTargetTest : BasePlatformTestCase() {

    fun testBranchOverrideStillUsesWorkingTreeDiff() {
        val gitService = GitService(project)

        assertTrue(gitService.shouldCompareAgainstWorkingTree("feature-a", "origin/main"))
        assertFalse(gitService.isExplicitRevisionTarget("origin/main"))
    }

    fun testExplicitCommitHashUsesRevisionComparisonPath() {
        val gitService = GitService(project)
        val commitHash = "0123456789abcdef0123456789abcdef01234567"

        assertFalse(gitService.shouldCompareAgainstWorkingTree("feature-a", commitHash))
        assertTrue(gitService.isExplicitRevisionTarget(commitHash))
    }

    fun testTabPrimaryRevisionStillUsesWorkingTreeWhenTargetsMatch() {
        val gitService = GitService(project)
        val commitHash = "0123456789abcdef0123456789abcdef01234567"

        assertTrue(gitService.shouldCompareAgainstWorkingTree(commitHash, commitHash))
    }
}
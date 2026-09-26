package com.github.uiopak.lstcrc.services

import com.github.uiopak.lstcrc.state.TabInfo
import com.intellij.testFramework.LightVirtualFile
import com.github.uiopak.lstcrc.testsupport.LstCrcTestCase
import com.intellij.vcs.log.impl.HashImpl
import git4idea.GitLocalBranch
import git4idea.branch.GitBranchesCollection
import git4idea.repo.GitRepository

class GitServiceComparisonTargetTest : LstCrcTestCase() {

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

    fun testResolveCommitHashUsesRepositoryStateAndRejectsAmbiguousRevisions() {
        val headHash = "1".repeat(40)
        val featureHash = "2".repeat(40)
        val hexNamedBranchHash = "3".repeat(40)
        val hexNamedBranch = "a".repeat(40)
        val branches = GitBranchesCollection(
            mapOf(
                GitLocalBranch("feature") to HashImpl.build(featureHash),
                GitLocalBranch(hexNamedBranch) to HashImpl.build(hexNamedBranchHash)
            ),
            emptyMap(),
            emptyList()
        )
        val repo = java.lang.reflect.Proxy.newProxyInstance(
            GitRepository::class.java.classLoader,
            arrayOf(GitRepository::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getCurrentRevision" -> headHash
                "getBranches" -> branches
                else -> null
            }
        } as GitRepository

        assertEquals(headHash, resolveCommitHash(repo, "HEAD"))
        assertEquals(featureHash, resolveCommitHash(repo, "feature"))
        // A branch wins over a hash-like name, so its content follows the branch.
        assertEquals(hexNamedBranchHash, resolveCommitHash(repo, hexNamedBranch))
        val fullHash = "4".repeat(40)
        assertEquals(fullHash, resolveCommitHash(repo, fullHash))
        // Tags, abbreviated hashes and unknown names are not resolved, so they are never cached.
        assertNull(resolveCommitHash(repo, "v1.0"))
        assertNull(resolveCommitHash(repo, "4444444"))
        assertNull(resolveCommitHash(repo, "missing-branch"))
    }
}

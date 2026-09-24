package com.github.uiopak.lstcrc.services

import com.github.uiopak.lstcrc.state.TabInfo
import com.intellij.testFramework.LightVirtualFile
import com.github.uiopak.lstcrc.testsupport.LstCrcTestCase
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
}
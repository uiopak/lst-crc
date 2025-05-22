package com.github.uiopak.lstcrc.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

class GitServiceTest : BasePlatformTestCase() {

    @Test
    fun testGetLocalBranches() {
        val gitService = GitService(project)
        val branches = gitService.getLocalBranches()
        
        // Verify that the local branches list is not empty
        assertFalse("Local branches list should not be empty", branches.isEmpty())
        
        // Verify that the local branches list contains "main"
        assertTrue("Local branches list should contain 'main'", branches.contains("main"))
    }

    @Test
    fun testGetRemoteBranches() {
        val gitService = GitService(project)
        val branches = gitService.getRemoteBranches()
        
        // Verify that the remote branches list is not empty
        assertFalse("Remote branches list should not be empty", branches.isEmpty())
        
        // Verify that the remote branches list contains "origin/main"
        assertTrue("Remote branches list should contain 'origin/main'", branches.contains("origin/main"))
    }

    @Test
    fun testGetAllBranches() {
        val gitService = GitService(project)
        val branches = gitService.getAllBranches()
        
        // Verify that the all branches list contains both local and remote branches
        assertTrue("All branches list should contain 'main'", branches.contains("main"))
        assertTrue("All branches list should contain 'origin/main'", branches.contains("origin/main"))
    }

    @Test
    fun testGetCurrentBranch() {
        val gitService = GitService(project)
        val currentBranch = gitService.getCurrentBranch()
        
        // Verify that the current branch is not null
        assertNotNull("Current branch should not be null", currentBranch)
    }
}
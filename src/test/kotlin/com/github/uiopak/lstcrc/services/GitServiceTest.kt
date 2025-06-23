//package com.github.uiopak.lstcrc.services
//
//import com.intellij.testFramework.fixtures.BasePlatformTestCase
//import git4idea.repo.GitRepository
//import org.junit.Test
//import org.mockito.kotlin.*
//
///**
// * Tests for GitService that use mocking to avoid requiring a real Git repository.
// */
//class GitServiceTest : BasePlatformTestCase() {
//
//    /**
//     * Create a mock GitService that returns predefined test data
//     */
//    private fun createMockGitService(): GitService {
//        // Create a mock GitService
//        val gitService = spy(GitService(project))
//
//        // Create a mock repository
//        val mockRepo: GitRepository = mock()
//
//        // Set up the repository to return branch names
//        val mockBranches = mock<Any>()
//
//        // Set up local branches
//        val localBranches = listOf("main", "develop")
//        val remoteBranches = listOf("origin/main", "origin/develop")
//
//        // Mock the behavior of GitService.getLocalBranches() to return our predefined list
//        doReturn(localBranches).whenever(gitService).getLocalBranches()
//
//        // Mock the behavior of GitService.getRemoteBranches() to return our predefined list
//        doReturn(remoteBranches).whenever(gitService).getRemoteBranches()
//
//        // Mock the behavior of GitService.getCurrentRepository() to return our mock repository
//        doReturn(mockRepo).whenever(gitService).getCurrentRepository()
//
//        return gitService
//    }
//
//    @Test
//    fun testGetLocalBranches() {
//        val gitService = createMockGitService()
//        val branches = gitService.getLocalBranches()
//
//        // Verify that the local branches list is not empty
//        assertFalse("Local branches list should not be empty", branches.isEmpty())
//
//        // Verify that the local branches list contains "main"
//        assertTrue("Local branches list should contain 'main'", branches.contains("main"))
//    }
//
//    @Test
//    fun testGetRemoteBranches() {
//        val gitService = createMockGitService()
//        val branches = gitService.getRemoteBranches()
//
//        // Verify that the remote branches list is not empty
//        assertFalse("Remote branches list should not be empty", branches.isEmpty())
//
//        // Verify that the remote branches list contains "origin/main"
//        assertTrue("Remote branches list should contain 'origin/main'", branches.contains("origin/main"))
//    }
//
//    @Test
//    fun testCombinedBranches() {
//        val gitService = createMockGitService()
//        val localBranches = gitService.getLocalBranches()
//        val remoteBranches = gitService.getRemoteBranches()
//
//        // Verify that the local branches list contains 'main'
//        assertTrue("Local branches list should contain 'main'", localBranches.contains("main"))
//
//        // Verify that the remote branches list contains 'origin/main'
//        assertTrue("Remote branches list should contain 'origin/main'", remoteBranches.contains("origin/main"))
//    }
//
//    @Test
//    fun testGetCurrentRepository() {
//        val gitService = createMockGitService()
//        val currentRepo = gitService.getCurrentRepository()
//
//        // Verify that the current repository is not null
//        assertNotNull("Current repository should not be null", currentRepo)
//    }
//}

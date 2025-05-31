package com.github.uiopak.lstcrc.services

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.changes.CurrentContentRevision
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase // Keeping for `project` instance
import git4idea.changes.GitChangeUtils
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test // JUnit 5 Test annotation
import org.junit.jupiter.api.extension.ExtendWith
import io.mockk.junit5.MockKExtension // For @MockK annotated mocks if needed, or manual mockk()

// Using BasePlatformTestCase for project access, but tests will use JUnit5/MockK
// If BasePlatformTestCase causes issues with JUnit5, we might need to remove it and mock Project.
@ExtendWith(MockKExtension::class) // Important for MockK integration with JUnit 5
class GitServiceTest : BasePlatformTestCase() { // Still extending for `project`

    private lateinit var gitService: GitService
    private lateinit var mockProject: Project // Will use this for GitService if needed, or the one from BasePlatformTestCase
    private lateinit var mockRepositoryManager: GitRepositoryManager
    private lateinit var mockRepository: GitRepository
    private lateinit var mockChangeListManager: ChangeListManager
    // GitChangeUtils is an object with static methods, so we use mockkStatic/unmockkStatic

    @BeforeEach
    fun setUpProjectAndMocks() {
        // `project` is inherited from BasePlatformTestCase.
        // We can choose to use this directly or mock it if more control is needed.
        // For now, let's assume the inherited `project` is sufficient.
        mockProject = project // Use the project from BasePlatformTestCase

        mockRepositoryManager = mockk()
        mockRepository = mockk()
        mockChangeListManager = mockk()

        // Mock static methods of GitChangeUtils
        mockkStatic(GitChangeUtils::class)
        // Mock static methods of CurrentContentRevision
        mockkStatic(CurrentContentRevision::class)
        // Mock GitRepositoryManager.getInstance(project)
        mockkStatic(GitRepositoryManager::class)


        every { GitRepositoryManager.getInstance(mockProject) } returns mockRepositoryManager
        every { mockRepositoryManager.repositories } returns listOf(mockRepository)
        every { mockRepository.currentBranchName } returns "main" // Default current branch for tests

        // Instantiate GitService with the real project from BasePlatformTestCase
        // or a mocked one if we decide to fully mock Project.
        gitService = GitService(mockProject)

        // Common stubs for ChangeListManager
        every { ChangeListManager.getInstance(mockProject) } returns mockChangeListManager
    }

    @AfterEach
    fun tearDownMocks() {
        unmockkStatic(GitChangeUtils::class)
        unmockkStatic(CurrentContentRevision::class)
        unmockkStatic(GitRepositoryManager::class) // Ensure this is unmocked
        // clearAllMocks() // Clears all MockK mocks
    }

    // --- Tests for getChanges ---

    @Test
    fun `getChanges when only untracked files should include them`() {
        val untrackedFileName = "new_untracked_file.txt"
        val mockUntrackedFile = mockk<VirtualFile>()
        val mockAfterRevision = mockk<ContentRevision>()

        every { mockUntrackedFile.path } returns "/project/path/$untrackedFileName"
        every { CurrentContentRevision.create(mockUntrackedFile) } returns mockAfterRevision
        // Ensure the afterRevision.file.path is accessible for the duplicate check logic and assertions
        val mockUntrackedFilePath = mockk<com.intellij.openapi.vcs.FilePath>()
        every { mockAfterRevision.file } returns mockUntrackedFilePath
        every { mockUntrackedFilePath.path } returns "/project/path/$untrackedFileName"


        every { GitChangeUtils.getDiffWithWorkingTree(mockRepository, "otherBranch", true) } returns emptyList()
        every { mockChangeListManager.unversionedFiles } returns listOf(mockUntrackedFile)
        every { mockRepository.currentBranchName } returns "currentBranch" // Ensure we take the 'else' path

        val future = gitService.getChanges("otherBranch")
        val changes = future.get() // Blocking call for test simplicity

        assertEquals(1, changes.size)
        val change = changes.first()
        assertEquals(Change.Type.NEW, change.type)
        assertNull(change.beforeRevision)
        assertSame(mockAfterRevision, change.afterRevision)
        assertEquals("/project/path/$untrackedFileName", change.afterRevision?.file?.path)
    }

    @Test
    fun `getChanges with mixed tracked and untracked files`() {
        val trackedFileName = "tracked_modified_file.txt"
        val mockTrackedChange = mockk<Change>()
        val mockTrackedFileRev = mockk<ContentRevision>()
        val mockTrackedFilePath = mockk<com.intellij.openapi.vcs.FilePath>()
        every { mockTrackedChange.type } returns Change.Type.MODIFICATION
        every { mockTrackedChange.beforeRevision } returns mockk<ContentRevision>()
        every { mockTrackedChange.afterRevision } returns mockTrackedFileRev
        every { mockTrackedFileRev.file } returns mockTrackedFilePath
        every { mockTrackedFilePath.path } returns "/project/path/$trackedFileName"


        val untrackedFileName = "new_untracked_file.txt"
        val mockUntrackedFile = mockk<VirtualFile>()
        val mockUntrackedFileRev = mockk<ContentRevision>()
        val mockUntrackedFilePath = mockk<com.intellij.openapi.vcs.FilePath>()
        every { mockUntrackedFile.path } returns "/project/path/$untrackedFileName"
        every { CurrentContentRevision.create(mockUntrackedFile) } returns mockUntrackedFileRev
        every { mockUntrackedFileRev.file } returns mockUntrackedFilePath
        every { mockUntrackedFilePath.path } returns "/project/path/$untrackedFileName"


        every { GitChangeUtils.getDiffWithWorkingTree(mockRepository, "otherBranch", true) } returns listOf(mockTrackedChange)
        every { mockChangeListManager.unversionedFiles } returns listOf(mockUntrackedFile)
        every { mockRepository.currentBranchName } returns "currentBranch"

        val future = gitService.getChanges("otherBranch")
        val changes = future.get()

        assertEquals(2, changes.size)
        assertTrue(changes.any { it.afterRevision?.file?.path == "/project/path/$trackedFileName" && it.type == Change.Type.MODIFICATION })
        assertTrue(changes.any { it.afterRevision?.file?.path == "/project/path/$untrackedFileName" && it.type == Change.Type.NEW })
    }

    @Test
    fun `getChanges should prevent duplicates for untracked file already covered by getDiffWithWorkingTree`() {
        // This scenario assumes getDiffWithWorkingTree might return a change for a file that's also in unversionedFiles.
        // The implementation has a check: if (virtualFile.path !in existingPaths)
        val commonFileName = "some_file.txt"

        val mockChangeFromGitDiff = mockk<Change>()
        val mockRevisionFromGitDiff = mockk<ContentRevision>()
        val mockFilePathFromGitDiff = mockk<com.intellij.openapi.vcs.FilePath>()
        every { mockChangeFromGitDiff.type } returns Change.Type.NEW // Say GitChangeUtils reported it as NEW
        every { mockChangeFromGitDiff.beforeRevision } returns null
        every { mockChangeFromGitDiff.afterRevision } returns mockRevisionFromGitDiff
        every { mockRevisionFromGitDiff.file } returns mockFilePathFromGitDiff
        every { mockFilePathFromGitDiff.path } returns "/project/path/$commonFileName"

        val mockUntrackedVirtualFile = mockk<VirtualFile>()
        // Do not mock CurrentContentRevision.create for this file if it's already handled,
        // as it shouldn't be called due to the duplicate check.
        every { mockUntrackedVirtualFile.path } returns "/project/path/$commonFileName"


        every { GitChangeUtils.getDiffWithWorkingTree(mockRepository, "otherBranch", true) } returns listOf(mockChangeFromGitDiff)
        every { mockChangeListManager.unversionedFiles } returns listOf(mockUntrackedVirtualFile)
        every { mockRepository.currentBranchName } returns "currentBranch"


        val future = gitService.getChanges("otherBranch")
        val changes = future.get()

        assertEquals(1, changes.size)
        val change = changes.first()
        assertSame(mockChangeFromGitDiff, change) // Should be the one from GitChangeUtils
        assertEquals("/project/path/$commonFileName", change.afterRevision?.file?.path)
        verify(exactly = 0) { CurrentContentRevision.create(mockUntrackedVirtualFile) } // Crucial check: ensure no new Change was made for the duplicate
    }

    @Test
    fun `getChanges with no untracked files`() {
        val trackedFileName = "tracked_modified_file.txt"
        val mockTrackedChange = mockk<Change>()
        val mockTrackedFileRev = mockk<ContentRevision>()
        val mockTrackedFilePath = mockk<com.intellij.openapi.vcs.FilePath>()

        every { mockTrackedChange.type } returns Change.Type.MODIFICATION
        every { mockTrackedChange.beforeRevision } returns mockk<ContentRevision>()
        every { mockTrackedChange.afterRevision } returns mockTrackedFileRev
        every { mockTrackedFileRev.file } returns mockTrackedFilePath
        every { mockTrackedFilePath.path } returns "/project/path/$trackedFileName"


        every { GitChangeUtils.getDiffWithWorkingTree(mockRepository, "otherBranch", true) } returns listOf(mockTrackedChange)
        every { mockChangeListManager.unversionedFiles } returns emptyList() // No untracked files
        every { mockRepository.currentBranchName } returns "currentBranch"

        val future = gitService.getChanges("otherBranch")
        val changes = future.get()

        assertEquals(1, changes.size)
        assertSame(mockTrackedChange, changes.first())
        assertEquals("/project/path/$trackedFileName", changes.first().afterRevision?.file?.path)
    }

    // --- Adapted existing tests ---
    @Test
    fun `getLocalBranches should return local branches from repository`() {
        val localBranchMock1 = mockk<git4idea.GitLocalBranch>()
        every { localBranchMock1.name } returns "main"
        val localBranchMock2 = mockk<git4idea.GitLocalBranch>()
        every { localBranchMock2.name } returns "feature/foo"
        val localBranchesMock = listOf(localBranchMock1, localBranchMock2)
        every { mockRepository.branches.localBranches } returns localBranchesMock

        val branches = gitService.getLocalBranches()
        assertEquals(2, branches.size)
        assertTrue(branches.contains("main"))
        assertTrue(branches.contains("feature/foo"))
    }

    @Test
    fun `getRemoteBranches should return remote branches from repository`() {
        val remoteBranchMock1 = mockk<git4idea.GitRemoteBranch>()
        every { remoteBranchMock1.name } returns "origin/main"
        val remoteBranchMock2 = mockk<git4idea.GitRemoteBranch>()
        every { remoteBranchMock2.name } returns "origin/develop"
        val remoteBranchesMock = listOf(remoteBranchMock1, remoteBranchMock2)
        every { mockRepository.branches.remoteBranches } returns remoteBranchesMock

        val branches = gitService.getRemoteBranches()
        assertEquals(2, branches.size)
        assertTrue(branches.contains("origin/main"))
        assertTrue(branches.contains("origin/develop"))
    }

    @Test
    fun `getAllBranches should return combined local and remote branches`() {
        val localBranchMock = mockk<git4idea.GitLocalBranch>()
        every { localBranchMock.name } returns "main"
        val remoteBranchMock = mockk<git4idea.GitRemoteBranch>()
        every { remoteBranchMock.name } returns "origin/main"

        every { mockRepository.branches.localBranches } returns listOf(localBranchMock)
        every { mockRepository.branches.remoteBranches } returns listOf(remoteBranchMock)

        val branches = gitService.getAllBranches()
        assertEquals(2, branches.size) // Assumes "main" and "origin/main" are distinct enough for the list
        assertTrue(branches.contains("main"))
        assertTrue(branches.contains("origin/main"))
    }

    @Test
    fun `getCurrentBranch should return current branch name from repository`() {
        // This is already configured in setUpProjectAndMocks:
        // every { mockRepository.currentBranchName } returns "main"
        val currentBranch = gitService.getCurrentBranch()
        assertNotNull(currentBranch)
        assertEquals("main", currentBranch)
    }
}
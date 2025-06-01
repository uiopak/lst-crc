package com.github.uiopak.lstcrc.provider

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.github.uiopak.lstcrc.services.GitService
import com.github.uiopak.lstcrc.settings.TabColorSettingsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import java.awt.Color

class GitStatusBasedTabColorProviderTest {

    private lateinit var tabColorProvider: GitStatusBasedTabColorProvider
    private lateinit var mockProject: Project
    private lateinit var mockVirtualFile: VirtualFile
    private lateinit var mockGitService: GitService
    private lateinit var mockSettingsState: TabColorSettingsState

    @Before
    fun setUp() {
        mockProject = Mockito.mock(Project::class.java)
        mockVirtualFile = Mockito.mock(VirtualFile::class.java)
        mockGitService = Mockito.mock(GitService::class.java)
        mockSettingsState = Mockito.mock(TabColorSettingsState::class.java)

        tabColorProvider = GitStatusBasedTabColorProvider()

        // Mock project.getService for GitService and TabColorSettingsState
        // This assumes TabColorSettingsState.getInstance(project) internally calls project.getService
        // And that GitStatusBasedTabColorProvider gets GitService via project.getService
        // Let's check GitStatusBasedTabColorProvider:
        // settings = TabColorSettingsState.getInstance(project) -> project.getService(TabColorSettingsState::class.java)
        // gitService = project.getService(GitService::class.java)
        // This is correct.

        `when`(mockProject.getService(TabColorSettingsState::class.java)).thenReturn(mockSettingsState)
        `when`(mockProject.getService(GitService::class.java)).thenReturn(mockGitService)

        `when`(mockVirtualFile.name).thenReturn("testfile.txt")
        `when`(mockVirtualFile.path).thenReturn("/test/testfile.txt")
    }

    @Test
    fun `getColor when tab coloring is disabled should return null`() {
        `when`(mockSettingsState.isTabColoringEnabled).thenReturn(false)

        val color = tabColorProvider.getColor(mockProject, mockVirtualFile)
        assertNull(color)
    }

    @Test
    fun `getColor when tab coloring is enabled and service returns valid color should return Color object`() {
        `when`(mockSettingsState.isTabColoringEnabled).thenReturn(true)
        `when`(mockSettingsState.comparisonBranch).thenReturn("HEAD")
        `when`(mockGitService.calculateEditorTabColor(mockVirtualFile.path, "HEAD")).thenReturn("#FF0000")

        val color = tabColorProvider.getColor(mockProject, mockVirtualFile)
        assertEquals(Color.RED, color)
    }

    @Test
    fun `getColor when service returns empty string should return null`() {
        `when`(mockSettingsState.isTabColoringEnabled).thenReturn(true)
        `when`(mockSettingsState.comparisonBranch).thenReturn("main")
        `when`(mockGitService.calculateEditorTabColor(mockVirtualFile.path, "main")).thenReturn("")

        val color = tabColorProvider.getColor(mockProject, mockVirtualFile)
        assertNull(color)
    }

    @Test
    fun `getColor when service returns invalid hex string should return null`() {
        `when`(mockSettingsState.isTabColoringEnabled).thenReturn(true)
        `when`(mockSettingsState.comparisonBranch).thenReturn("HEAD")
        `when`(mockGitService.calculateEditorTabColor(mockVirtualFile.path, "HEAD")).thenReturn("invalid_hex")

        val color = tabColorProvider.getColor(mockProject, mockVirtualFile)
        assertNull(color)
    }

    @Test
    fun `getColor should use comparisonBranch from settings`() {
        `when`(mockSettingsState.isTabColoringEnabled).thenReturn(true)
        `when`(mockSettingsState.comparisonBranch).thenReturn("develop")
        `when`(mockGitService.calculateEditorTabColor(mockVirtualFile.path, "develop")).thenReturn("#00FF00")

        val color = tabColorProvider.getColor(mockProject, mockVirtualFile)
        assertEquals(Color.GREEN, color)
        Mockito.verify(mockGitService).calculateEditorTabColor(mockVirtualFile.path, "develop")
    }

    @Test
    fun `getColor when GitService is null should return null`() {
        // This can happen if project.getService(GitService::class.java) returns null
        `when`(mockProject.getService(GitService::class.java)).thenReturn(null)
        `when`(mockSettingsState.isTabColoringEnabled).thenReturn(true)

        val color = tabColorProvider.getColor(mockProject, mockVirtualFile)
        assertNull(color)
    }
}

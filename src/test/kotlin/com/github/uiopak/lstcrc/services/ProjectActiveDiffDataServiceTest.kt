package com.github.uiopak.lstcrc.services

import com.github.uiopak.lstcrc.state.TabInfo
import com.github.uiopak.lstcrc.state.ToolWindowState
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ProjectActiveDiffDataServiceTest : BasePlatformTestCase() {

    fun testAcceptsHeadUpdateWhenHeadTabIsSelected() {
        val diffDataService = project.service<ProjectActiveDiffDataService>()
        val headFile = myFixture.addFileToProject("diff/HeadOnly.txt", "head\n").virtualFile

        project.service<ToolWindowStateService>().noStateLoaded()

        diffDataService.updateActiveDiff(
            "HEAD",
            listOf(headFile),
            emptyList<VirtualFile>(),
            emptyList<VirtualFile>(),
            emptyList<VirtualFile>(),
            emptyMap<String, String>()
        )
        flushEdt()

        assertEquals("HEAD", diffDataService.activeBranchName)
        assertTrue(diffDataService.createdFilesSet.contains(headFile))
        assertTrue(diffDataService.changedFilesSet.contains(headFile))
        assertTrue(diffDataService.createdFilePaths.contains(headFile.path))
    }

    fun testRejectsStaleUpdateWhenSelectedBranchDoesNotMatch() {
        val diffDataService = project.service<ProjectActiveDiffDataService>()
        val staleFile = myFixture.addFileToProject("diff/Stale.txt", "stale\n").virtualFile

        project.service<ToolWindowStateService>().loadState(
            ToolWindowState(
                openTabs = listOf(TabInfo(branchName = "selected-branch")),
                selectedTabIndex = 0
            )
        )

        diffDataService.updateActiveDiff(
            "other-branch",
            listOf(staleFile),
            emptyList<VirtualFile>(),
            emptyList<VirtualFile>(),
            emptyList<VirtualFile>(),
            emptyMap<String, String>()
        )
        flushEdt()

        assertNull(diffDataService.activeBranchName)
        assertTrue(diffDataService.createdFiles.isEmpty())
        assertTrue(diffDataService.changedFilesSet.isEmpty())
        assertFalse(diffDataService.createdFilePaths.contains(staleFile.path))
    }

    private fun flushEdt() {
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        ApplicationManager.getApplication().invokeAndWait(object : Runnable {
            override fun run() = Unit
        })
    }
}
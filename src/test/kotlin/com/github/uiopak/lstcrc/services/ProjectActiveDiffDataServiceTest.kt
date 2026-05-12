package com.github.uiopak.lstcrc.services

import com.github.uiopak.lstcrc.testsupport.categorizedChanges
import com.github.uiopak.lstcrc.testsupport.flushEdt
import com.github.uiopak.lstcrc.testsupport.selectComparisonTab
import com.github.uiopak.lstcrc.testsupport.selectHeadTab
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ProjectActiveDiffDataServiceTest : BasePlatformTestCase() {

    fun testAcceptsHeadUpdateWhenHeadTabIsSelected() {
        val diffDataService = project.service<ProjectActiveDiffDataService>()
        val headFile = myFixture.addFileToProject("diff/HeadOnly.txt", "head\n").virtualFile

        selectHeadTab(project)

        diffDataService.updateActiveDiff(
            "HEAD",
            categorizedChanges(createdFiles = listOf(headFile))
        )
        flushEdt()

        assertEquals("HEAD", diffDataService.activeBranchName)
        assertTrue(diffDataService.createdFilesSet.contains(headFile))
        assertTrue(diffDataService.changedFilesSet.contains(headFile))
        assertTrue(diffDataService.createdFilePaths.contains(headFile.path))
    }

    fun testRejectsStaleUpdateWhenSelectedBranchDoesNotMatch() {
        val diffDataService = project.service<ProjectActiveDiffDataService>()
        val selectedFile = myFixture.addFileToProject("diff/Selected.txt", "selected\n").virtualFile
        val staleFile = myFixture.addFileToProject("diff/Stale.txt", "stale\n").virtualFile

        selectComparisonTab(project, "selected-branch")

        diffDataService.updateActiveDiff(
            "selected-branch",
            categorizedChanges(createdFiles = listOf(selectedFile))
        )
        flushEdt()

        diffDataService.updateActiveDiff(
            "other-branch",
            categorizedChanges(createdFiles = listOf(staleFile))
        )
        flushEdt()

        assertEquals("selected-branch", diffDataService.activeBranchName)
        assertEquals(listOf(selectedFile), diffDataService.createdFiles)
        assertTrue(diffDataService.createdFilesSet.contains(selectedFile))
        assertFalse(diffDataService.createdFilesSet.contains(staleFile))
        assertTrue(diffDataService.changedFilesSet.contains(selectedFile))
        assertFalse(diffDataService.changedFilesSet.contains(staleFile))
        assertTrue(diffDataService.createdFilePaths.contains(selectedFile.path))
        assertFalse(diffDataService.createdFilePaths.contains(staleFile.path))
    }

    fun testRejectsHeadUpdateWhileComparisonTabIsSelected() {
        val diffDataService = project.service<ProjectActiveDiffDataService>()
        val selectedFile = myFixture.addFileToProject("diff/BranchSelected.txt", "branch selected\n").virtualFile
        val headFile = myFixture.addFileToProject("diff/HeadShouldBeRejected.txt", "head\n").virtualFile

        selectComparisonTab(project, "selected-branch")

        diffDataService.updateActiveDiff(
            "selected-branch",
            categorizedChanges(createdFiles = listOf(selectedFile))
        )
        flushEdt()

        diffDataService.updateActiveDiff(
            "HEAD",
            categorizedChanges(createdFiles = listOf(headFile))
        )
        flushEdt()

        assertEquals("selected-branch", diffDataService.activeBranchName)
        assertEquals(listOf(selectedFile), diffDataService.createdFiles)
        assertTrue(diffDataService.createdFilesSet.contains(selectedFile))
        assertFalse(diffDataService.createdFilesSet.contains(headFile))
        assertTrue(diffDataService.changedFilesSet.contains(selectedFile))
        assertFalse(diffDataService.changedFilesSet.contains(headFile))
        assertTrue(diffDataService.createdFilePaths.contains(selectedFile.path))
        assertFalse(diffDataService.createdFilePaths.contains(headFile.path))
    }
}
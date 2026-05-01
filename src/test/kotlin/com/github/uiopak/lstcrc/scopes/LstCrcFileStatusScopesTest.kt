package com.github.uiopak.lstcrc.scopes

import com.github.uiopak.lstcrc.services.ProjectActiveDiffDataService
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.psi.search.scope.packageSet.NamedScopeManager
import com.intellij.psi.search.scope.packageSet.PackageSetBase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LstCrcFileStatusScopesTest : BasePlatformTestCase() {

    fun testDeletedScopeMatchesDeletedPathsWhileChangedExcludesThem() {
        val diffDataService = project.service<ProjectActiveDiffDataService>()
        val createdFile = myFixture.addFileToProject("scopes/NewFile.txt", "new\n").virtualFile
        val movedFile = myFixture.addFileToProject("scopes/Moved.txt", "moved\n").virtualFile
        val deletedFile = myFixture.addFileToProject("scopes/Deleted.txt", "deleted\n").virtualFile

        project.service<ToolWindowStateService>().noStateLoaded()
        diffDataService.updateActiveDiff(
            "HEAD",
            listOf(createdFile),
            emptyList(),
            listOf(movedFile),
            listOf(deletedFile),
            emptyMap()
        )
        flushEdt()

        assertEquals("HEAD", diffDataService.activeBranchName)
        assertTrue(diffDataService.deletedFilePaths.contains(deletedFile.path))
        assertFalse(diffDataService.changedFilePaths.contains(deletedFile.path))

        val holder = NamedScopeManager.getInstance(project)
        val deletedScope = DeletedFilesScope().value as? PackageSetBase
            ?: error("Deleted scope is missing its PackageSetBase")
        val changedScope = ChangedFilesScope().value as? PackageSetBase
            ?: error("Changed scope is missing its PackageSetBase")

        assertTrue(deletedScope.contains(deletedFile, project, holder))
        assertFalse(changedScope.contains(deletedFile, project, holder))
        assertTrue(changedScope.contains(createdFile, project, holder))
        assertTrue(changedScope.contains(movedFile, project, holder))
    }

    private fun flushEdt() {
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        ApplicationManager.getApplication().invokeAndWait(object : Runnable {
            override fun run() = Unit
        })
    }
}
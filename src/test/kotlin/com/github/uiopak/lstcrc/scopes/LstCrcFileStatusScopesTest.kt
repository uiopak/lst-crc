package com.github.uiopak.lstcrc.scopes

import com.github.uiopak.lstcrc.services.ProjectActiveDiffDataService
import com.github.uiopak.lstcrc.services.CategorizedChanges
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.github.uiopak.lstcrc.toolWindow.LstCrcSettingsService
import com.github.uiopak.lstcrc.toolWindow.ToolWindowSettingsProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.psi.search.scope.packageSet.NamedScopeManager
import com.intellij.psi.search.scope.packageSet.PackageSetBase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LstCrcFileStatusScopesTest : BasePlatformTestCase() {

    fun testDeletedScopeMatchesDeletedPathsWhileChangedExcludesThem() {
        enableIncludeHeadInScopes(true)
        val diffDataService = project.service<ProjectActiveDiffDataService>()
        val createdFile = myFixture.addFileToProject("scopes/NewFile.txt", "new\n").virtualFile
        val movedFile = myFixture.addFileToProject("scopes/Moved.txt", "moved\n").virtualFile
        val deletedFile = myFixture.addFileToProject("scopes/Deleted.txt", "deleted\n").virtualFile

        project.service<ToolWindowStateService>().noStateLoaded()
        diffDataService.updateActiveDiff(
            "HEAD",
            CategorizedChanges(
                allChanges = emptyList(),
                createdFiles = listOf(createdFile),
                modifiedFiles = emptyList(),
                movedFiles = listOf(movedFile),
                deletedFiles = listOf(deletedFile),
                comparisonContext = emptyMap(),
                lineStatsByChange = emptyMap()
            )
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

    fun testScopesExcludeHeadChangesWhenIncludeHeadInScopesIsDisabled() {
        enableIncludeHeadInScopes(false)
        val diffDataService = project.service<ProjectActiveDiffDataService>()
        val createdFile = myFixture.addFileToProject("scopes/HeadNew.txt", "new\n").virtualFile

        project.service<ToolWindowStateService>().noStateLoaded()
        diffDataService.updateActiveDiff(
            "HEAD",
            CategorizedChanges(
                allChanges = emptyList(),
                createdFiles = listOf(createdFile),
                modifiedFiles = emptyList(),
                movedFiles = emptyList(),
                deletedFiles = emptyList(),
                comparisonContext = emptyMap(),
                lineStatsByChange = emptyMap()
            )
        )
        flushEdt()

        assertEquals("HEAD", diffDataService.activeBranchName)

        val holder = NamedScopeManager.getInstance(project)
        val createdScope = CreatedFilesScope().value as? PackageSetBase
            ?: error("Created scope is missing its PackageSetBase")
        val changedScope = ChangedFilesScope().value as? PackageSetBase
            ?: error("Changed scope is missing its PackageSetBase")

        assertFalse(createdScope.contains(createdFile, project, holder))
        assertFalse(changedScope.contains(createdFile, project, holder))
    }

    private fun enableIncludeHeadInScopes(enabled: Boolean) {
        ApplicationManager.getApplication().service<LstCrcSettingsService>()
            .setBoolean(
                ToolWindowSettingsProvider.APP_INCLUDE_HEAD_IN_SCOPES_KEY,
                enabled,
                ToolWindowSettingsProvider.DEFAULT_INCLUDE_HEAD_IN_SCOPES
            )
    }

    private fun flushEdt() {
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        ApplicationManager.getApplication().invokeAndWait { }
    }
}
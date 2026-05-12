package com.github.uiopak.lstcrc.testsupport

import com.github.uiopak.lstcrc.services.CategorizedChanges
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.github.uiopak.lstcrc.state.TabInfo
import com.github.uiopak.lstcrc.state.ToolWindowState
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil

internal fun categorizedChanges(
    createdFiles: List<VirtualFile> = emptyList(),
    modifiedFiles: List<VirtualFile> = emptyList(),
    movedFiles: List<VirtualFile> = emptyList(),
    deletedFiles: List<VirtualFile> = emptyList()
): CategorizedChanges = CategorizedChanges(
    allChanges = emptyList(),
    createdFiles = createdFiles,
    modifiedFiles = modifiedFiles,
    movedFiles = movedFiles,
    deletedFiles = deletedFiles,
    comparisonContext = emptyMap(),
    lineStatsByChange = emptyMap()
)

internal fun selectHeadTab(project: Project) {
    project.service<ToolWindowStateService>().noStateLoaded()
}

internal fun selectComparisonTab(project: Project, branchName: String) {
    project.service<ToolWindowStateService>().loadState(
        ToolWindowState(
            openTabs = listOf(TabInfo(branchName = branchName)),
            selectedTabIndex = 0
        )
    )
}

internal fun flushEdt() {
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    ApplicationManager.getApplication().invokeAndWait { }
}
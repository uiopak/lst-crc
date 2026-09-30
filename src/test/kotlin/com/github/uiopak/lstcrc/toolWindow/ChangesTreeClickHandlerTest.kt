package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.testsupport.LstCrcTestCase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.testFramework.PlatformTestUtil
import git4idea.GitContentRevision
import java.awt.event.MouseEvent

class ChangesTreeClickHandlerTest : LstCrcTestCase() {
    fun testQueuedClickDoesNotRunAfterHandlerDisposal() = assertQueuedClick(disposeBeforeDispatch = true)

    fun testQueuedClickRunsWhileHandlerIsAlive() = assertQueuedClick(disposeBeforeDispatch = false)

    private fun assertQueuedClick(disposeBeforeDispatch: Boolean) {
        val settings = ApplicationManager.getApplication().service<LstCrcSettingsService>()
        val previous = settings.state
        settings[LstCrcSettingDefinitions.SINGLE_CLICK_ACTION] = ToolWindowSettingsProvider.ACTION_OPEN_DIFF
        settings[LstCrcSettingDefinitions.DOUBLE_CLICK_ACTION] = ToolWindowSettingsProvider.ACTION_NONE
        val file = myFixture.addFileToProject("Clicked.txt", "text\n").virtualFile
        val change = Change(null, GitContentRevision.createRevision(com.intellij.vcsUtil.VcsUtil.getFilePath(file), null, project), FileStatus.ADDED)
        val tree = object : ChangesTree(project, false, false) {
            override fun rebuildTree() = Unit
        }.apply {
            model = TreeModelBuilder(project, grouping).setChanges(listOf(change), null).build()
            setSize(400, 300)
            expandAll()
        }
        var actions = 0
        val handler = ChangesTreeClickHandler(
            project, tree,
            listOf(BrowserChangeActionDefinition(ToolWindowSettingsProvider.ACTION_OPEN_DIFF, "context.menu.show.diff") { actions++ }),
            { actions++ }
        )
        Disposer.register(testRootDisposable, handler)
        handler.install()
        try {
            val row = (0 until tree.rowCount).first { tree.getPathForRow(it).lastPathComponent.toString().contains("Clicked.txt") }
            val bounds = tree.getRowBounds(row)
            tree.dispatchEvent(MouseEvent(tree, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0,
                bounds.x + bounds.width / 2, bounds.y + bounds.height / 2, 1, false, MouseEvent.BUTTON1))
            if (disposeBeforeDispatch) Disposer.dispose(handler)
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            assertEquals(if (disposeBeforeDispatch) 0 else 1, actions)
        } finally {
            Disposer.dispose(handler)
            settings.loadState(previous)
        }
    }
}

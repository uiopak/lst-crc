package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.services.CategorizedChanges
import com.github.uiopak.lstcrc.services.ProjectActiveDiffDataService
import com.github.uiopak.lstcrc.services.TextContentRevision
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.changes.ui.AsyncChangesTree
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.testFramework.PlatformTestUtil
import com.github.uiopak.lstcrc.testsupport.LstCrcTestCase
import com.github.uiopak.lstcrc.testsupport.selectComparisonTab
import com.github.uiopak.lstcrc.testsupport.selectHeadTab
import com.intellij.vcsUtil.VcsUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Point
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.event.ChangeListener

class LstCrcChangesBrowserTest : LstCrcTestCase() {

    private data class BrowserFixture(
        val browser: LstCrcChangesBrowser,
        val tree: JTree,
        val scrollPane: JScrollPane,
        val host: JPanel
    )

    fun testRefreshPreservesTreeViewportPosition() {
        withBrowserFixture {
            displayChanges(browser, categorizedChanges(changeCount = 80))
            waitForRowCount(tree, minimumRows = 60)

                onEdt {
                    selectRowContaining(tree, "File000.txt")
                    scrollPane.viewport.viewPosition = Point(0, 260)
                }
                flushUiEvents()

                val beforeRefreshPosition = onEdt { Point(scrollPane.viewport.viewPosition) }
                assertTrue("Precondition failed: expected the tree to be scrolled vertically before refresh", beforeRefreshPosition.y > 0)

                displayChanges(browser, categorizedChanges(changeCount = 81))
                waitForRowCount(tree, minimumRows = 61)

                val afterRefreshPosition = onEdt { Point(scrollPane.viewport.viewPosition) }
                assertEquals("Tree refresh should preserve the current viewport position", beforeRefreshPosition, afterRefreshPosition)
        }
    }

    fun testRepeatedRefreshPreservesTreeViewportPosition() {
        withBrowserFixture {
            displayChanges(browser, categorizedChanges(changeCount = 80))
            waitForRowCount(tree, minimumRows = 60)

                onEdt {
                    selectRowContaining(tree, "File000.txt")
                    scrollPane.viewport.viewPosition = Point(0, 260)
                }
                flushUiEvents()

                val initialPosition = onEdt { Point(scrollPane.viewport.viewPosition) }
                assertTrue("Precondition failed: expected the tree to be scrolled vertically before refresh", initialPosition.y > 0)

                displayChanges(browser, categorizedChanges(changeCount = 81))
                waitForRowCount(tree, minimumRows = 61)
                assertEquals(
                    "First refresh should preserve the current viewport position",
                    initialPosition,
                    onEdt { Point(scrollPane.viewport.viewPosition) }
                )

                displayChanges(browser, categorizedChanges(changeCount = 82))
                waitForRowCount(tree, minimumRows = 62)
                assertEquals(
                    "Repeated refreshes should preserve the current viewport position",
                    initialPosition,
                    onEdt { Point(scrollPane.viewport.viewPosition) }
                )
        }
    }

    fun testRefreshPreservesTopViewportWhenSelectionIsOffscreen() {
        withBrowserFixture {
            displayChanges(browser, categorizedChanges(changeCount = 80))
            waitForRowCount(tree, minimumRows = 60)

                onEdt {
                    selectRowContaining(tree, "File079.txt")
                    scrollPane.viewport.viewPosition = Point(0, 0)
                }
                flushUiEvents()

                val beforeRefreshPosition = onEdt { Point(scrollPane.viewport.viewPosition) }
                assertEquals("Precondition failed: expected the tree viewport to remain at the top", Point(0, 0), beforeRefreshPosition)

                displayChanges(browser, categorizedChanges(changeCount = 81))
                waitForRowCount(tree, minimumRows = 61)

                val afterRefreshPosition = onEdt { Point(scrollPane.viewport.viewPosition) }
                assertEquals(
                    "Refreshing with an offscreen selection should not pull the viewport away from the top",
                    beforeRefreshPosition,
                    afterRefreshPosition
                )
        }
    }

    fun testRefreshDoesNotMoveViewportWhileSelectionIsOffscreen() {
        withBrowserFixture {
            displayChanges(browser, categorizedChanges(changeCount = 80))
            waitForRowCount(tree, minimumRows = 60)

            onEdt {
                selectRowContaining(tree, "File079.txt")
                scrollPane.viewport.viewPosition = Point(0, 0)
            }
            flushUiEvents()

            val initialPosition = onEdt { Point(scrollPane.viewport.viewPosition) }
            val viewportPositions = mutableListOf(initialPosition)
            val listener = ChangeListener {
                viewportPositions += Point(scrollPane.viewport.viewPosition)
            }

            onEdt {
                scrollPane.viewport.addChangeListener(listener)
            }
            try {
                displayChanges(browser, categorizedChanges(changeCount = 81))
                waitForRowCount(tree, minimumRows = 61)
            } finally {
                onEdt {
                    scrollPane.viewport.removeChangeListener(listener)
                }
            }

            val distinctPositions = viewportPositions.distinct()
            assertEquals(
                "Refreshing should not move the viewport at all while the selected file is offscreen",
                listOf(initialPosition),
                distinctPositions
            )
        }
    }

    fun testRefreshDoesNotMoveViewportForSelectedAddedFileWhileOffscreen() {
        withBrowserFixture {
            displayChanges(browser, addedCategorizedChanges(changeCount = 80))
            waitForRowCount(tree, minimumRows = 60)

            onEdt {
                selectRowContaining(tree, "File079.txt")
                scrollPane.viewport.viewPosition = Point(0, 120)
            }
            flushUiEvents()

            val initialPosition = onEdt { Point(scrollPane.viewport.viewPosition) }
            assertTrue("Precondition failed: expected the tree to be scrolled vertically", initialPosition.y > 0)

            val viewportPositions = mutableListOf(initialPosition)
            val listener = ChangeListener {
                viewportPositions += Point(scrollPane.viewport.viewPosition)
            }

            onEdt {
                scrollPane.viewport.addChangeListener(listener)
            }
            try {
                displayChanges(browser, addedCategorizedChanges(changeCount = 81))
                waitForRowCount(tree, minimumRows = 61)
            } finally {
                onEdt {
                    scrollPane.viewport.removeChangeListener(listener)
                }
            }

            assertEquals(
                "Refreshing should not move the viewport while an added selected file stays offscreen",
                listOf(initialPosition),
                viewportPositions.distinct()
            )
        }
    }

    fun testRefreshKeepsCollapsedFolderCollapsed() {
        withBrowserFixture {
            displayChanges(browser, nestedDeletedFileChanges())
            waitForRowCount(tree, minimumRows = 2)
            onEdt { assertTrue(browser.setExpandedForVisibleNodeTextForTest("featureA", false)) }
            flushUiEvents()

            displayChangesAndWaitForRebuild(browser, nestedDeletedFileChanges())

            assertEquals("A refresh must not reopen a collapsed folder", listOf("featureA"), visibleRowNames(tree))
        }
    }

    // Regression: the selection was restored as the changes under a selected folder, and selecting them made
    // JTree expand the folder again (the macOS flake of testTreeStatePersistsAcrossTabSwitches).
    fun testRefreshKeepsSelectedCollapsedFolderCollapsedAndSelected() {
        withBrowserFixture {
            displayChanges(browser, nestedDeletedFileChanges())
            waitForRowCount(tree, minimumRows = 2)
            onEdt {
                selectRowContaining(tree, "OnlyA.txt")
                // Collapsing a folder moves the selection from its file to the folder.
                assertTrue(browser.setExpandedForVisibleNodeTextForTest("featureA", false))
            }
            flushUiEvents()
            assertEquals("Precondition: the collapsed folder is selected", listOf("featureA"), selectedRowNames(tree))

            displayChangesAndWaitForRebuild(browser, nestedDeletedFileChanges())

            assertEquals("A refresh must not reopen a selected collapsed folder", listOf("featureA"), visibleRowNames(tree))
            assertEquals("A refresh must keep the folder, not its files, selected", listOf("featureA"), selectedRowNames(tree))
        }
    }

    // A failed load clears the diff data. The error text went to the HEAD tab's browser, whichever tab was selected.
    fun testClearedDiffShowsLoadErrorInSelectedTab() {
        selectComparisonTab(project, "feature")
        try {
            val browser = createBrowser()

            project.service<ProjectActiveDiffDataService>().clearActiveDiff()
            flushUiEvents()

            assertEquals(LstCrcBundle.message("changes.browser.error.loading", "feature"), browser.viewerTree().emptyText.text)
        } finally {
            selectHeadTab(project)
        }
    }

    // A diff tab opened before the target moved (a fetch or commit) was reused and showed the old target content.
    fun testDiffKeyChangesWhenTheTargetMovesToAnotherCommit() {
        val changeKeys = listOf(change("Main.txt", beforeContent = "base\n", afterContent = "updated\n").toDiffChangeKey())
        val opened = DiffSelectionKey("feature", changeKeys, mapOf("/repo" to "1".repeat(40)))
        val reopened = opened.copy(targetCommits = mapOf("/repo" to "2".repeat(40)))

        assertFalse("a moved target must not reuse the tab", opened == reopened)
        assertTrue("the old tab is replaced", opened.isOlderVersionOf(reopened))
        assertFalse(reopened.isOlderVersionOf(reopened))
        assertFalse("another selection is left open", opened.copy(changes = emptyList()).isOlderVersionOf(reopened))
        assertFalse("another tab's diff is left open", opened.copy(comparisonTarget = "other").isOlderVersionOf(reopened))
    }

    fun testDiffKeyOfUnsavedEditChangesWithItsText() {
        val filePath = VcsUtil.getFilePath("/repo/Main.txt", false)
        fun unsavedEdit(text: String) = Change(
            TextContentRevision(filePath, "base\n", git4idea.GitRevisionNumber("HEAD")),
            TextContentRevision(filePath, text, git4idea.GitRevisionNumber("LOCAL")),
            FileStatus.MODIFIED
        )

        assertEquals(unsavedEdit("one\n").toDiffChangeKey(), unsavedEdit("one\n").toDiffChangeKey())
        assertFalse(unsavedEdit("one\n").toDiffChangeKey() == unsavedEdit("two\n").toDiffChangeKey())
    }

    fun testAvailableContextMenuActionsIncludeProjectTreeForNonDeletedChange() {
        val browser = createBrowser()
        val change = change("Main.txt", beforeContent = "base\n", afterContent = "updated\n")

        assertEquals(
            listOf<String>(
                LstCrcBundle.message("context.menu.show.diff"),
                LstCrcBundle.message("context.menu.open.source"),
                LstCrcBundle.message("context.menu.show.project.tree")
            ),
            browser.availableContextMenuActionTitlesForTest(change)
        )
    }

    fun testAvailableContextMenuActionsOmitProjectTreeForDeletedChange() {
        val browser = createBrowser()
        val change = change("Deleted.txt", beforeContent = "gone\n", afterContent = null)

        assertEquals(
            listOf<String>(
                LstCrcBundle.message("context.menu.show.diff"),
                LstCrcBundle.message("context.menu.open.source")
            ),
            browser.availableContextMenuActionTitlesForTest(change)
        )
    }

    fun testConfiguredClickActionLookupUsesButtonSpecificSettings() {
        val browser = createBrowser()
        val settingsService = ApplicationManager.getApplication().service<LstCrcSettingsService>()
        settingsService.resetToDefaults()

        settingsService[LstCrcSettingDefinitions.SINGLE_CLICK_ACTION] = ToolWindowSettingsProvider.ACTION_OPEN_DIFF
        settingsService[LstCrcSettingDefinitions.DOUBLE_CLICK_ACTION] = ToolWindowSettingsProvider.ACTION_OPEN_SOURCE
        settingsService[LstCrcSettingDefinitions.MIDDLE_CLICK_ACTION] = ToolWindowSettingsProvider.ACTION_SHOW_IN_PROJECT_TREE
        settingsService[LstCrcSettingDefinitions.DOUBLE_MIDDLE_CLICK_ACTION] = ToolWindowSettingsProvider.ACTION_NONE
        settingsService[LstCrcSettingDefinitions.RIGHT_CLICK_ACTION] = ToolWindowSettingsProvider.ACTION_OPEN_SOURCE
        settingsService[LstCrcSettingDefinitions.DOUBLE_RIGHT_CLICK_ACTION] = ToolWindowSettingsProvider.ACTION_OPEN_DIFF

        assertEquals(ToolWindowSettingsProvider.ACTION_OPEN_DIFF, browser.configuredActionForClickForTest(java.awt.event.MouseEvent.BUTTON1, false))
        assertEquals(ToolWindowSettingsProvider.ACTION_OPEN_SOURCE, browser.configuredActionForClickForTest(java.awt.event.MouseEvent.BUTTON1, true))
        assertEquals(ToolWindowSettingsProvider.ACTION_SHOW_IN_PROJECT_TREE, browser.configuredActionForClickForTest(java.awt.event.MouseEvent.BUTTON2, false))
        assertEquals(ToolWindowSettingsProvider.ACTION_NONE, browser.configuredActionForClickForTest(java.awt.event.MouseEvent.BUTTON2, true))
        assertEquals(ToolWindowSettingsProvider.ACTION_OPEN_SOURCE, browser.configuredActionForClickForTest(java.awt.event.MouseEvent.BUTTON3, false))
        assertEquals(ToolWindowSettingsProvider.ACTION_OPEN_DIFF, browser.configuredActionForClickForTest(java.awt.event.MouseEvent.BUTTON3, true))
    }

    fun testConfiguredClickActionLookupFallsBackToNoneForUnsupportedButtons() {
        val browser = createBrowser()

        assertEquals(ToolWindowSettingsProvider.ACTION_NONE, browser.configuredActionForClickForTest(99, false))
    }

    fun testToolbarActionsIncludeRepoComparisonActionImmediatelyAfterGroupByWhenPresent() {
        val browser = createBrowser()

        val actionNames = browser.toolbarActionSimpleNamesForTest()
        val groupByActionIndex = actionNames.indexOf("GroupByActionGroup")
        val repoComparisonActionIndex = actionNames.indexOf(ShowRepoComparisonInfoAction::class.java.simpleName)

        assertTrue("Expected ShowRepoComparisonInfoAction to be present in toolbar actions", repoComparisonActionIndex >= 0)
        if (groupByActionIndex >= 0) {
            assertEquals(groupByActionIndex + 1, repoComparisonActionIndex)
        } else {
            assertEquals(actionNames.lastIndex, repoComparisonActionIndex)
        }
    }

    private fun withBrowserFixture(testBody: BrowserFixture.() -> Unit) {
        val parentDisposable = Disposer.newDisposable()

        try {
            val browser = LstCrcChangesBrowser(project, "feature", parentDisposable)
            val tree = browser.viewerTree()
            val scrollPane = tree.parent.parent as JScrollPane
            val host = onEdt {
                JPanel(BorderLayout()).apply {
                    preferredSize = Dimension(320, 220)
                    size = preferredSize
                    add(browser, BorderLayout.CENTER)
                    doLayout()
                    validate()
                    browser.setSize(size)
                    browser.doLayout()
                    scrollPane.setSize(size)
                    scrollPane.doLayout()
                }
            }

            try {
                testBody(BrowserFixture(browser, tree, scrollPane, host))
            } finally {
                onEdt {
                    host.remove(browser)
                }
            }
        } finally {
            Disposer.dispose(parentDisposable)
        }
    }

    private fun createBrowser(): LstCrcChangesBrowser {
        val parentDisposable = Disposer.newDisposable()
        Disposer.register(testRootDisposable, parentDisposable)
        return LstCrcChangesBrowser(project, "feature", parentDisposable)
    }

    private fun categorizedChanges(changeCount: Int): CategorizedChanges {
        val changes = (0 until changeCount).map { index ->
            change(
                path = "File${index.toString().padStart(3, '0')}.txt",
                beforeContent = "base line\n",
                afterContent = "updated line $index\n"
            )
        }
        return CategorizedChanges(
            allChanges = changes,
            createdFiles = emptyList(),
            modifiedFiles = emptyList(),
            movedFiles = emptyList(),
            deletedFiles = emptyList(),
            comparisonContext = emptyMap(),
            lineStatsByChange = emptyMap()
        )
    }

    private fun addedCategorizedChanges(changeCount: Int): CategorizedChanges {
        val changes = (0 until changeCount).map { index ->
            change(
                path = "File${index.toString().padStart(3, '0')}.txt",
                beforeContent = null,
                afterContent = "added line $index\n"
            )
        }
        return CategorizedChanges(
            allChanges = changes,
            createdFiles = emptyList(),
            modifiedFiles = emptyList(),
            movedFiles = emptyList(),
            deletedFiles = emptyList(),
            comparisonContext = emptyMap(),
            lineStatsByChange = emptyMap()
        )
    }

    private fun change(path: String, beforeContent: String?, afterContent: String?): Change {
        val beforeRevision = beforeContent?.let { TestContentRevision(path, it, "before") }
        val afterRevision = afterContent?.let { TestContentRevision(path, it, "after") }
        val fileStatus = when {
            beforeRevision == null -> FileStatus.ADDED
            afterRevision == null -> FileStatus.DELETED
            else -> FileStatus.MODIFIED
        }
        return Change(beforeRevision, afterRevision, fileStatus)
    }

    private fun displayChanges(browser: LstCrcChangesBrowser, categorizedChanges: CategorizedChanges) {
        val method = LstCrcChangesBrowser::class.java.getDeclaredMethod(
            "displayChanges",
            CategorizedChanges::class.java,
            String::class.java
        )
        method.isAccessible = true
        method.invoke(browser, categorizedChanges, "feature")
        flushUiEvents()

        onEdt {
            val tree = browser.viewerTree()
            val scrollPane = tree.parent.parent as JScrollPane
            val viewportSize = Dimension(320, 220)
            val rowHeight = tree.rowHeight.takeIf { it > 0 } ?: 20
            val contentSize = Dimension(
                viewportSize.width,
                maxOf(viewportSize.height * 2, tree.rowCount * rowHeight)
            )

            tree.preferredSize = contentSize
            tree.size = contentSize
            scrollPane.preferredSize = viewportSize
            scrollPane.size = viewportSize
            scrollPane.viewport.extentSize = viewportSize
            browser.preferredSize = viewportSize
            browser.size = viewportSize

            browser.doLayout()
            scrollPane.doLayout()
            scrollPane.viewport.doLayout()
        }
        flushUiEvents()
    }

    /** One file deleted in the comparison, two folders deep, so the tree shows a folder row and a file row. */
    private fun nestedDeletedFileChanges(): CategorizedChanges = CategorizedChanges(
        allChanges = listOf(change(path = "${project.basePath}/nested/featureA/OnlyA.txt", beforeContent = "only a\n", afterContent = null)),
        createdFiles = emptyList(),
        modifiedFiles = emptyList(),
        movedFiles = emptyList(),
        deletedFiles = emptyList(),
        comparisonContext = emptyMap(),
        lineStatsByChange = emptyMap()
    )

    /** Displays [changes] and waits until the tree has applied the rebuilt model. */
    private fun displayChangesAndWaitForRebuild(browser: LstCrcChangesBrowser, changes: CategorizedChanges) {
        val rebuilt = java.util.concurrent.CountDownLatch(1)
        displayChanges(browser, changes)
        onEdt { (browser.viewerTree() as AsyncChangesTree).invokeAfterRefresh { rebuilt.countDown() } }
        repeat(100) {
            if (rebuilt.count == 0L) return flushUiEvents()
            flushUiEvents()
            Thread.sleep(50)
        }
        fail("The changes tree was not rebuilt")
    }

    private fun visibleRowNames(tree: JTree): List<String> = onEdt {
        (0 until tree.rowCount).map { tree.getPathForRow(it).lastPathComponent.toString().substringAfterLast('/') }
    }

    private fun selectedRowNames(tree: JTree): List<String> = onEdt {
        tree.selectionPaths.orEmpty().map { it.lastPathComponent.toString().substringAfterLast('/') }
    }

    private fun waitForRowCount(tree: JTree, minimumRows: Int) {
        repeat(20) {
            flushUiEvents()
            if (onEdt { tree.rowCount } >= minimumRows) {
                return
            }
            Thread.sleep(50)
        }
        fail("Expected at least $minimumRows visible rows, but found ${onEdt { tree.rowCount }}")
    }

    private fun selectRowContaining(tree: JTree, text: String) {
        val row = (0 until tree.rowCount).firstOrNull { index ->
            tree.getPathForRow(index)?.lastPathComponent?.toString()?.contains(text) == true
        } ?: error("Could not find a tree row containing '$text'")
        tree.setSelectionRow(row)
    }

    private fun flushUiEvents() {
        repeat(5) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        }
    }

    private fun <T> onEdt(action: () -> T): T {
        var result: T? = null
        var failure: Throwable? = null
        ApplicationManager.getApplication().invokeAndWait {
            try {
                result = action()
            } catch (t: Throwable) {
                failure = t
            }
        }
        failure?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private class TestContentRevision(
        path: String,
        private val text: String,
        private val revision: String
    ) : ContentRevision {
        private val filePath: FilePath = VcsUtil.getFilePath(path, false)

        override fun getFile(): FilePath = filePath

        override fun getRevisionNumber(): VcsRevisionNumber = object : VcsRevisionNumber {
            override fun asString(): String = revision

            override fun compareTo(other: VcsRevisionNumber): Int = 0
        }

        override fun getContent(): String = text
    }
}
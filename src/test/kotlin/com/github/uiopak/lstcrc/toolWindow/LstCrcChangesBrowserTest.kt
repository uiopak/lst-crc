package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.services.CategorizedChanges
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.vcsUtil.VcsUtil
import java.awt.Point
import javax.swing.JFrame
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.event.ChangeListener

class LstCrcChangesBrowserTest : BasePlatformTestCase() {

    private data class BrowserFixture(
        val browser: LstCrcChangesBrowser,
        val tree: JTree,
        val scrollPane: JScrollPane,
        val frame: JFrame
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

    private fun withBrowserFixture(testBody: BrowserFixture.() -> Unit) {
        val parentDisposable = Disposer.newDisposable()

        try {
            val browser = LstCrcChangesBrowser(project, "feature", parentDisposable)
            val tree = browser.viewerTree()
            val scrollPane = tree.parent.parent as JScrollPane
            val frame = onEdt {
                JFrame("LstCrcChangesBrowserTest").apply {
                    contentPane.add(browser)
                    setSize(320, 220)
                    setLocationRelativeTo(null)
                    isVisible = true
                }
            }

            try {
                testBody(BrowserFixture(browser, tree, scrollPane, frame))
            } finally {
                onEdt {
                    frame.isVisible = false
                    frame.dispose()
                }
            }
        } finally {
            Disposer.dispose(parentDisposable)
        }
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
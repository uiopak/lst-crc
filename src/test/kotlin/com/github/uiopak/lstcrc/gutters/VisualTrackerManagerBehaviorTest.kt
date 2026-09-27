package com.github.uiopak.lstcrc.gutters

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.vcs.ex.LocalLineStatusTracker.Mode
import com.intellij.openapi.vcs.ex.Range
import com.intellij.openapi.vcs.ex.SimpleLocalLineStatusTracker
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.openapi.components.service
import com.github.uiopak.lstcrc.testsupport.LstCrcTestCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class VisualTrackerManagerBehaviorTest : LstCrcTestCase() {

    fun testUnderlyingTrackerReportsInsertedRangeForPartialInsertionAgainstExistingBase() {
        val tracker = createTracker(text = "alpha\nbeta\n", baseText = "alpha\n")

        try {
            val ranges = tracker.getRanges() ?: emptyList()

            assertSize(1, ranges)
            assertEquals(Range.INSERTED, ranges.single().type)
        } finally {
            tracker.release()
        }
    }

    fun testUnderlyingTrackerReportsInitialInsertedRangeForWholeNewFileAgainstEmptyBase() {
        val tracker = createTracker(text = "local new file\n", baseText = "")

        try {
            val ranges = tracker.getRanges() ?: emptyList()

            assertSize(1, ranges)
            assertEquals(Range.INSERTED, ranges.single().type)
        } finally {
            tracker.release()
        }
    }

    fun testStandaloneTrackerInstallsGutterHighlightersForWholeNewFile() {
        val file = LightVirtualFile("tracker-highlighters.txt", PlainTextFileType.INSTANCE, "local new file\n")
        myFixture.openFileInEditor(file)
        val tracker = createTracker(file = file, baseText = "")

        try {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            val markupModel = DocumentMarkupModel.forDocument(fileDocument(file), project, true) as MarkupModelEx
            val gutterHighlighters = markupModel.allHighlighters.count { it.lineMarkerRenderer != null }
            assertTrue("Expected standalone tracker to install line marker renderers for a new file", gutterHighlighters > 0)
        } finally {
            tracker.release()
        }
    }

    fun testVisualTrackerManagerCleanupOnTrackerRemoved() {
        val manager = project.service<VisualTrackerManager>()
        val file = myFixture.addFileToProject("TrackerRemoved.txt", "text\n").virtualFile
        myFixture.openFileInEditor(file)
        val document = FileDocumentManager.getInstance().getDocument(file)!!

        manager.dispose()
        val tracker = manager.findStandaloneTracker(document)
        assertNull(tracker)
    }

    fun testStandaloneTrackerIsReleasedWhenItsLastEditorCloses() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val manager = VisualTrackerManager(project, scope)
        val file = myFixture.addFileToProject("Untracked.txt", "text\n").virtualFile
        myFixture.openFileInEditor(file)
        val document = FileDocumentManager.getInstance().getDocument(file)!!

        try {
            manager.init()
            manager.ensureVisualTracker(document, file, "feature")
            assertNotNull(manager.findStandaloneTracker(document))

            FileEditorManager.getInstance(project).closeFile(file)
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            assertNull("Closing the last editor should release the standalone tracker", manager.findStandaloneTracker(document))
        } finally {
            Disposer.dispose(manager)
            scope.cancel()
        }
    }

    // Regression: a repository change only forgot the loaded revisions, so an open editor kept comparing against
    // the old commit when the refresh that followed published no new diff data.
    fun testRepositoryChangeRechecksTheTrackersOfVisibleEditors() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val manager = VisualTrackerManager(project, scope)
        val file = myFixture.addFileToProject("Visible.txt", "text\n").virtualFile
        myFixture.openFileInEditor(file)
        val document = FileDocumentManager.getInstance().getDocument(file)!!

        try {
            manager.ensureVisualTracker(document, file, "feature")
            assertNotNull(manager.findStandaloneTracker(document))

            // The file is in no repository, so a re-check finds no target and releases its tracker.
            manager.onRepositoryChanged()

            val deadline = System.currentTimeMillis() + 10_000
            while (manager.findStandaloneTracker(document) != null && System.currentTimeMillis() < deadline) {
                PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
                Thread.sleep(20)
            }
            assertNull("A repository change should re-check the visible editor's tracker", manager.findStandaloneTracker(document))
        } finally {
            Disposer.dispose(manager)
            scope.cancel()
        }
    }

    // Regression (round five): toggling "Include HEAD tab changes in file scopes" only asked for a reload, which found
    // the same data and changed nothing, so gutters (and file colours) ignored the new value.
    fun testIncludeHeadToggleRechecksTrackers() {
        val manager = project.service<VisualTrackerManager>()
        val settings = com.intellij.openapi.application.ApplicationManager.getApplication().service<com.github.uiopak.lstcrc.toolWindow.LstCrcSettingsService>()
        val definition = com.github.uiopak.lstcrc.toolWindow.LstCrcSettingDefinitions.INCLUDE_HEAD_IN_SCOPES
        val original = settings[definition]
        val file = myFixture.addFileToProject("IncludeHead.txt", "text\n").virtualFile
        myFixture.openFileInEditor(file)
        val document = FileDocumentManager.getInstance().getDocument(file)!!

        try {
            // Load the data first, so the reload an old version of the toggle asked for finds nothing new.
            val load = project.service<com.github.uiopak.lstcrc.services.ToolWindowStateService>().refreshDataForCurrentSelection()
            PlatformTestUtil.waitWithEventsDispatching("The first load did not finish", { load.isDone }, 10)
            dispatchEventsFor(millis = 500) // lets the re-check that the first load triggered finish
            manager.ensureVisualTracker(document, file, "feature")
            dispatchEventsFor(millis = 500)
            assertNotNull("Precondition: nothing else re-checks the tracker", manager.findStandaloneTracker(document))
            val toggle = allActions(com.github.uiopak.lstcrc.toolWindow.ToolWindowSettingsProvider.createToolWindowSettingsGroup())
                .filterIsInstance<com.intellij.openapi.actionSystem.ToggleAction>()
                .single { it.templateText == com.github.uiopak.lstcrc.resources.LstCrcBundle.message("settings.include.head.in.scopes") }
            val event = com.intellij.testFramework.TestActionEvent.createTestEvent(
                toggle,
                com.intellij.openapi.actionSystem.impl.SimpleDataContext.getProjectContext(project)
            )

            toggle.setSelected(event, !original)

            // The file is in no repository, so the re-check finds no target and releases its tracker.
            val deadline = System.currentTimeMillis() + 10_000
            while (manager.findStandaloneTracker(document) != null && System.currentTimeMillis() < deadline) {
                PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
                Thread.sleep(20)
            }
            assertNull("Toggling the setting should re-check gutter trackers", manager.findStandaloneTracker(document))
        } finally {
            settings[definition] = original
        }
    }

    private fun dispatchEventsFor(millis: Long) {
        val end = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < end) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            Thread.sleep(20)
        }
    }

    private fun allActions(action: com.intellij.openapi.actionSystem.AnAction): List<com.intellij.openapi.actionSystem.AnAction> =
        listOf(action) + ((action as? com.intellij.openapi.actionSystem.DefaultActionGroup)?.childActionsOrStubs.orEmpty().flatMap { allActions(it) })

    private fun createTracker(text: String, baseText: String): SimpleLocalLineStatusTracker {
        val file = LightVirtualFile("tracker-behavior.txt", PlainTextFileType.INSTANCE, text)
        return createTracker(file, baseText)
    }

    private fun createTracker(file: LightVirtualFile, baseText: String): SimpleLocalLineStatusTracker {
        val document = fileDocument(file)

        @Suppress("UnstableApiUsage")
        val tracker = ApplicationManager.getApplication().runWriteAction<SimpleLocalLineStatusTracker> {
            SimpleLocalLineStatusTracker.createTracker(project, document, file).also {
                it.mode = Mode(isVisible = true, showErrorStripeMarkers = true, detectWhitespaceChangedLines = true)
                it.setBaseRevision(baseText)
            }
        }

        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        return tracker
    }

    private fun fileDocument(file: LightVirtualFile) = FileDocumentManager.getInstance().getDocument(file)!!
}
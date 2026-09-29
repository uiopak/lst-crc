package com.github.uiopak.lstcrc.gutters

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.vcs.ex.LocalLineStatusTracker.Mode
import com.intellij.openapi.vcs.ex.Range
import com.intellij.openapi.vcs.ex.SimpleLocalLineStatusTracker
import com.intellij.openapi.vcs.ex.LineStatusTrackerListener
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import com.github.uiopak.lstcrc.testsupport.LstCrcTestCase
import com.github.uiopak.lstcrc.services.GitService
import com.github.uiopak.lstcrc.services.ProjectActiveDiffDataService
import com.github.uiopak.lstcrc.services.RevisionContentCache
import com.github.uiopak.lstcrc.testsupport.categorizedChanges
import com.github.uiopak.lstcrc.testsupport.selectComparisonTab
import com.github.uiopak.lstcrc.testsupport.selectHeadTab
import com.github.uiopak.lstcrc.toolWindow.LstCrcSettingsService
import com.github.uiopak.lstcrc.toolWindow.LstCrcSettingDefinitions
import git4idea.GitLocalBranch
import git4idea.GitVcs
import git4idea.branch.GitBranchesCollection
import com.intellij.vcs.log.impl.HashImpl
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.LinkedBlockingDeque

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

    fun testDisposingManagerReleasesExistingTracker() {
        withCachedGutterTargets { manager, scope, file ->
            val document = FileDocumentManager.getInstance().getDocument(file)!!
            manager.ensureVisualTracker(document, file, "base-a")
            waitForGutterLoad(scope)
            assertNotNull(manager.findStandaloneTracker(document))

            manager.dispose()

            assertNull("Disposal must release the existing visual tracker", manager.findStandaloneTracker(document))
        }
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

    fun testGutterContentCancellationIsPropagated() {
        val root = myFixture.tempDirFixture.findOrCreateDir("repo")
        val file = myFixture.addFileToProject("repo/Cancelled.txt", "text\n").virtualFile
        val repositoryManager = GitRepositoryManager.getInstance(project)
        val vcs = GitVcs.getInstance(project)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val manager = VisualTrackerManager(project, scope)
        val cancellations = listOf(
            com.intellij.openapi.progress.ProcessCanceledException(),
            kotlinx.coroutines.CancellationException("Gutter load cancelled")
        )

        try {
            cancellations.forEach { cancellation ->
                val repo = java.lang.reflect.Proxy.newProxyInstance(
                    GitRepository::class.java.classLoader,
                    arrayOf(GitRepository::class.java)
                ) { _, method, _ ->
                    when (method.name) {
                        "getRoot" -> root
                        "getVcs" -> vcs
                        "getCurrentRevision" -> throw cancellation
                        else -> error("Unexpected repository access: ${method.name}")
                    }
                } as GitRepository
                repositoryManager.addExternalRepository(root, repo)

                assertThrows(cancellation.javaClass) {
                    runBlocking { loadTargetContent(manager, file) }
                }
                repositoryManager.removeExternalRepository(root)
            }
        } finally {
            repositoryManager.removeExternalRepository(root)
            Disposer.dispose(manager)
            scope.cancel()
        }
    }

    fun testCancelledGutterLoadCanBeRetried() {
        val root = myFixture.tempDirFixture.findOrCreateDir("repo")
        val file = myFixture.addFileToProject("repo/Retry.txt", "text\n").virtualFile
        myFixture.openFileInEditor(file)
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        val repositoryManager = GitRepositoryManager.getInstance(project)
        val vcs = GitVcs.getInstance(project)
        val loads = AtomicInteger()
        val repo = java.lang.reflect.Proxy.newProxyInstance(
            GitRepository::class.java.classLoader,
            arrayOf(GitRepository::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getRoot" -> root
                "getVcs" -> vcs
                "getCurrentRevision" -> {
                    loads.incrementAndGet()
                    throw kotlinx.coroutines.CancellationException("Gutter load cancelled")
                }
                else -> error("Unexpected repository access: ${method.name}")
            }
        } as GitRepository
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val manager = VisualTrackerManager(project, scope)

        try {
            repositoryManager.addExternalRepository(root, repo)
            manager.ensureVisualTracker(document, file, "HEAD")
            PlatformTestUtil.waitWithEventsDispatching(
                "The first gutter load did not finish",
                { loads.get() == 1 && scope.coroutineContext[Job]!!.children.none() }, 5
            )

            manager.ensureVisualTracker(document, file, "HEAD")
            PlatformTestUtil.waitWithEventsDispatching(
                "The same target must be retried after cancellation",
                { loads.get() == 2 && scope.coroutineContext[Job]!!.children.none() }, 5
            )
        } finally {
            repositoryManager.removeExternalRepository(root)
            Disposer.dispose(manager)
            scope.cancel()
        }
    }

    fun testSwitchingTargetsWithIdenticalTextKeepsGutterMarkers() {
        withCachedGutterTargets { manager, scope, file ->
            val document = FileDocumentManager.getInstance().getDocument(file)!!
            manager.ensureVisualTracker(document, file, "base-a")
            waitForGutterLoad(scope)
            val tracker = manager.findStandaloneTracker(document)!!
            val markup = DocumentMarkupModel.forDocument(document, project, true) as MarkupModelEx
            val originalMarkers = markup.allHighlighters.filter { it.lineMarkerRenderer != null }
            assertSize(1, originalMarkers)
            var baseResets = 0
            var removedMarkers = 0
            tracker.addListener(object : LineStatusTrackerListener {
                override fun onBecomingValid() { baseResets++ }
            })
            markup.addMarkupModelListener(testRootDisposable, object : MarkupModelListener {
                override fun beforeRemoved(highlighter: RangeHighlighterEx) {
                    if (originalMarkers.any { it === highlighter }) removedMarkers++
                }
            })

            manager.ensureVisualTracker(document, file, "base-b")
            waitForGutterLoad(scope)

            assertSame(tracker, manager.findStandaloneTracker(document))
            assertEquals("The same file text at a different commit must not reset the gutter base", 0, baseResets)
            assertEquals("Existing gutter markers must remain installed", 0, removedMarkers)
            assertTrue(originalMarkers.all { it.isValid })
            assertEquals("base\ncontext\n", tracker.vcsDocument.text)
        }
    }

    fun testSwitchingTargetsWithDifferentTextUpdatesGutterRanges() {
        withCachedGutterTargets { manager, scope, file ->
            val document = FileDocumentManager.getInstance().getDocument(file)!!
            manager.ensureVisualTracker(document, file, "base-a")
            waitForGutterLoad(scope)
            val tracker = manager.findStandaloneTracker(document)!!
            assertEquals(0, tracker.getRanges()!!.single().line1)

            manager.ensureVisualTracker(document, file, "different-base")
            waitForGutterLoad(scope)

            assertSame(tracker, manager.findStandaloneTracker(document))
            assertEquals("edited\nold context\n", tracker.vcsDocument.text)
            assertEquals("The marker must move to the line changed against the new base", 1, tracker.getRanges()!!.single().line1)
        }
    }

    fun testOlderGutterRefreshCannotReplaceNewerComparison() {
        withQueuedGutterRefresh { manager, scope, file, ui ->
            val document = FileDocumentManager.getInstance().getDocument(file)!!
            manager.onRepositoryChanged()
            waitForQueuedGutterTasks(ui, 1)

            activateGutterComparison("different-base")
            manager.onRepositoryChanged()
            waitForQueuedGutterTasks(ui, 2)
            ui.runLast() // Apply the newer decision before the older one.
            waitForQueuedGutterTasks(ui, 2)
            ui.runLast() // Apply the newer target's content.
            assertEquals("edited\nold context\n", manager.findStandaloneTracker(document)!!.vcsDocument.text)

            drainGutterJobs(scope, ui)

            assertEquals("An older resolved target must not replace the active comparison's base",
                "edited\nold context\n", manager.findStandaloneTracker(document)!!.vcsDocument.text)
        }
    }

    fun testOlderDisabledGutterRefreshCannotRemoveNewerMarkers() {
        withQueuedGutterRefresh { manager, scope, file, ui ->
            val document = FileDocumentManager.getInstance().getDocument(file)!!
            manager.ensureVisualTracker(document, file, "base-a")
            drainGutterJobs(scope, ui)
            val tracker = manager.findStandaloneTracker(document)!!
            val settings = ApplicationManager.getApplication().service<LstCrcSettingsService>()

            settings[LstCrcSettingDefinitions.ENABLE_GUTTER_MARKERS] = false
            manager.settingsChanged()
            waitForQueuedGutterTasks(ui, 1)
            settings[LstCrcSettingDefinitions.ENABLE_GUTTER_MARKERS] = true
            manager.settingsChanged()
            waitForQueuedGutterTasks(ui, 2)
            ui.runLast()
            drainGutterJobs(scope, ui)

            assertSame("An obsolete disabled setting must not release the current tracker",
                tracker, manager.findStandaloneTracker(document))
        }
    }

    fun testClosingEditorPreventsPendingRefreshFromCreatingTracker() {
        withQueuedGutterRefresh { manager, scope, file, ui ->
            val document = FileDocumentManager.getInstance().getDocument(file)!!
            manager.onRepositoryChanged()
            waitForQueuedGutterTasks(ui, 1)

            FileEditorManager.getInstance(project).closeFile(file)
            drainGutterJobs(scope, ui)

            assertNull("A delayed refresh must not install markers after the last editor closes",
                manager.findStandaloneTracker(document))
        }
    }

    fun testDisposingManagerPreventsPendingRefreshFromCreatingTracker() {
        withQueuedGutterRefresh { manager, scope, file, ui ->
            val document = FileDocumentManager.getInstance().getDocument(file)!!
            manager.onRepositoryChanged()
            waitForQueuedGutterTasks(ui, 1)

            manager.dispose()
            drainGutterJobs(scope, ui)

            assertNull("A delayed refresh must not recreate a tracker after manager disposal",
                manager.findStandaloneTracker(document))
        }
    }

    fun testGutterRefreshIgnoresEditorsOutsideFileEditorManager() {
        withQueuedGutterRefresh { manager, scope, file, ui ->
            val previewFile = myFixture.addFileToProject("cached-repo/Preview.txt", "edited\ncontext\n").virtualFile
            val previewDocument = FileDocumentManager.getInstance().getDocument(previewFile)!!
            revisionContentCache().get(file.parent.path, "1".repeat(40), "Preview.txt", previewFile.charset) {
                "base\ncontext\n"
            }
            val previewEditor = EditorFactory.getInstance().createEditor(previewDocument, project)
            try {
                manager.settingsChanged()
                drainGutterJobs(scope, ui)

                assertNotNull("The open file must still receive its gutter tracker",
                    manager.findStandaloneTracker(FileDocumentManager.getInstance().getDocument(file)!!))
                assertNull("A preview editor must not receive a persistent comparison tracker",
                    manager.findStandaloneTracker(previewDocument))
            } finally {
                EditorFactory.getInstance().releaseEditor(previewEditor)
            }
        }
    }

    fun testRefreshDuringPendingBaseLoadStillInitializesTracker() {
        withQueuedGutterRefresh { manager, scope, file, ui ->
            val document = FileDocumentManager.getInstance().getDocument(file)!!
            manager.ensureVisualTracker(document, file, "base-a")
            waitForQueuedGutterTasks(ui, 1)

            manager.settingsChanged()
            waitForQueuedGutterTasks(ui, 2)
            ui.runLast()
            drainGutterJobs(scope, ui)

            val tracker = manager.findStandaloneTracker(document)!!
            assertNotNull("A refresh must not leave the same target's pending load reserved forever", tracker.getRanges())
            assertEquals("base\ncontext\n", tracker.vcsDocument.text)
            assertEquals(0, tracker.getRanges()!!.single().line1)
        }
    }

    fun testEditorSelectionKeepsPendingRefreshOfOtherOpenFiles() {
        withQueuedGutterRefresh { manager, scope, file, ui ->
            val otherFile = openAnotherCachedGutterFile(file)
            val editors = FileEditorManager.getInstance(project)
            manager.init()

            manager.settingsChanged()
            waitForQueuedGutterTasks(ui, 1)
            editors.openFile(otherFile, true)
            waitForQueuedGutterTasks(ui, 2)
            drainGutterJobs(scope, ui)

            assertNotNull("Changing editor selection must not drop the full refresh of the other open file",
                manager.findStandaloneTracker(FileDocumentManager.getInstance().getDocument(file)!!))
            assertNotNull("The selected editor must also receive the active comparison",
                manager.findStandaloneTracker(FileDocumentManager.getInstance().getDocument(otherFile)!!))
        }
    }

    fun testRepositoryRefreshKeepsPendingUpdatesOfOtherOpenFiles() {
        withQueuedGutterRefresh { manager, scope, file, ui ->
            val otherFile = openAnotherCachedGutterFile(file)
            manager.init()
            manager.settingsChanged()
            waitForQueuedGutterTasks(ui, 1)

            manager.onRepositoryChanged()
            waitForQueuedGutterTasks(ui, 2)
            drainGutterJobs(scope, ui)

            assertNotNull("The visible file must receive the active comparison",
                manager.findStandaloneTracker(FileDocumentManager.getInstance().getDocument(file)!!))
            assertNotNull("A repository event must not discard the settings refresh of the other open file",
                manager.findStandaloneTracker(FileDocumentManager.getInstance().getDocument(otherFile)!!))
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

    // Settings are application-wide, but a change only reached the project whose menu made it: the gutters of the
    // other open projects kept the old setting. An event without a project stands for the other project's menu.
    fun testGutterToggleFromAnotherProjectRechecksThisProjectsTrackers() {
        val manager = project.service<VisualTrackerManager>()
        val settings = com.intellij.openapi.application.ApplicationManager.getApplication().service<com.github.uiopak.lstcrc.toolWindow.LstCrcSettingsService>()
        val definition = com.github.uiopak.lstcrc.toolWindow.LstCrcSettingDefinitions.ENABLE_GUTTER_MARKERS
        val original = settings[definition]
        val file = myFixture.addFileToProject("OtherProjectToggle.txt", "text\n").virtualFile
        myFixture.openFileInEditor(file)
        val document = FileDocumentManager.getInstance().getDocument(file)!!

        try {
            settings[definition] = true
            val load = project.service<com.github.uiopak.lstcrc.services.ToolWindowStateService>().refreshDataForCurrentSelection()
            PlatformTestUtil.waitWithEventsDispatching("The first load did not finish", { load.isDone }, 10)
            dispatchEventsFor(millis = 500)
            manager.ensureVisualTracker(document, file, "feature")
            dispatchEventsFor(millis = 500)
            assertNotNull("Precondition: nothing else re-checks the tracker", manager.findStandaloneTracker(document))
            val toggle = allActions(com.github.uiopak.lstcrc.toolWindow.ToolWindowSettingsProvider.createToolWindowSettingsGroup())
                .filterIsInstance<com.intellij.openapi.actionSystem.ToggleAction>()
                .single { it.templateText == com.github.uiopak.lstcrc.resources.LstCrcBundle.message("settings.gutter.enable") }
            val event = com.intellij.testFramework.TestActionEvent.createTestEvent(
                toggle,
                com.intellij.openapi.actionSystem.DataContext.EMPTY_CONTEXT
            )

            toggle.setSelected(event, false)

            val deadline = System.currentTimeMillis() + 10_000
            while (manager.findStandaloneTracker(document) != null && System.currentTimeMillis() < deadline) {
                PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
                Thread.sleep(20)
            }
            assertNull("Turning gutter markers off anywhere should release this project's trackers", manager.findStandaloneTracker(document))
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

    private fun waitForGutterLoad(scope: CoroutineScope) {
        PlatformTestUtil.waitWithEventsDispatching(
            "The gutter content load did not finish", { scope.coroutineContext[Job]!!.children.none() }, 5
        )
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }

    private fun withCachedGutterTargets(
        dispatchers: VisualTrackerDispatchers = VisualTrackerDispatchers(),
        test: (VisualTrackerManager, CoroutineScope, VirtualFile) -> Unit
    ) {
        val root = myFixture.tempDirFixture.findOrCreateDir("cached-repo")
        val file = myFixture.addFileToProject("cached-repo/Same.txt", "edited\ncontext\n").virtualFile
        myFixture.openFileInEditor(file)
        val revisions = listOf("base-a", "base-b", "different-base").mapIndexed { index, name ->
            GitLocalBranch(name) to HashImpl.build((index + 1).toString().repeat(40))
        }.toMap()
        val branches = GitBranchesCollection(revisions, emptyMap(), emptyList())
        val vcs = GitVcs.getInstance(project)
        val repo = java.lang.reflect.Proxy.newProxyInstance(
            GitRepository::class.java.classLoader, arrayOf(GitRepository::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getRoot" -> root
                "getVcs" -> vcs
                "getBranches" -> branches
                "getCurrentBranchName" -> "working-branch"
                "getCurrentRevision" -> "9".repeat(40)
                else -> error("Unexpected repository access: ${method.name}")
            }
        } as GitRepository
        val cache = revisionContentCache()
        revisions.forEach { (branch, hash) ->
            cache.get(root.path, hash.asString(), "Same.txt", file.charset) {
                if (branch.name == "different-base") "edited\nold context\n" else "base\ncontext\n"
            }
        }
        val repositoryManager = GitRepositoryManager.getInstance(project)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val manager = VisualTrackerManager(project, scope, dispatchers)

        try {
            repositoryManager.addExternalRepository(root, repo)
            test(manager, scope, file)
        } finally {
            repositoryManager.removeExternalRepository(root)
            Disposer.dispose(manager)
            scope.cancel()
            (dispatchers.ui as? QueuedUiDispatcher)?.runAll()
        }
    }

    private fun revisionContentCache(): RevisionContentCache = GitService::class.java.getDeclaredField("revisionContentCache")
        .apply { isAccessible = true }.get(project.service<GitService>()) as RevisionContentCache

    private fun openAnotherCachedGutterFile(file: VirtualFile): VirtualFile {
        val otherFile = myFixture.addFileToProject("cached-repo/Other.txt", "edited\ncontext\n").virtualFile
        revisionContentCache().get(file.parent.path, "1".repeat(40), "Other.txt", otherFile.charset) {
            "base\ncontext\n"
        }
        val editors = FileEditorManager.getInstance(project)
        editors.openFile(otherFile, true)
        editors.openFile(file, true)
        return otherFile
    }

    private fun activateGutterComparison(target: String) {
        selectComparisonTab(project, target)
        project.service<ProjectActiveDiffDataService>().updateActiveDiff(target, categorizedChanges())
    }

    private fun withQueuedGutterRefresh(
        test: (VisualTrackerManager, CoroutineScope, VirtualFile, QueuedUiDispatcher) -> Unit
    ) {
        val settings = ApplicationManager.getApplication().service<LstCrcSettingsService>()
        val original = settings[LstCrcSettingDefinitions.ENABLE_GUTTER_MARKERS]
        val ui = QueuedUiDispatcher()
        try {
            settings[LstCrcSettingDefinitions.ENABLE_GUTTER_MARKERS] = true
            withCachedGutterTargets(VisualTrackerDispatchers(ui = ui)) { manager, scope, file ->
                activateGutterComparison("base-a")
                try {
                    test(manager, scope, file, ui)
                } finally {
                    // The light project is shared across test methods, including its initialized service manager.
                    project.service<ProjectActiveDiffDataService>().clearActiveDiff()
                    val serviceManager = project.service<VisualTrackerManager>()
                    val document = FileDocumentManager.getInstance().getDocument(file)!!
                    PlatformTestUtil.waitWithEventsDispatching("The service tracker did not release the test comparison",
                        { serviceManager.findStandaloneTracker(document) == null }, 5)
                }
            }
        } finally {
            settings[LstCrcSettingDefinitions.ENABLE_GUTTER_MARKERS] = original
            selectHeadTab(project)
        }
    }

    private fun waitForQueuedGutterTasks(ui: QueuedUiDispatcher, count: Int) {
        PlatformTestUtil.waitWithEventsDispatching("Expected $count pending gutter UI tasks", { ui.size >= count }, 5)
    }

    private fun drainGutterJobs(scope: CoroutineScope, ui: QueuedUiDispatcher) {
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        PlatformTestUtil.waitWithEventsDispatching("The gutter jobs did not finish", {
            ui.runAll()
            scope.coroutineContext[Job]!!.children.none() && ui.size == 0
        }, 5)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }

    /** Lets tests deliver resolved gutter decisions out of order while still applying them on the EDT. */
    private class QueuedUiDispatcher : CoroutineDispatcher() {
        private val tasks = LinkedBlockingDeque<Runnable>()
        val size: Int get() = tasks.size
        override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.addLast(block) }
        fun runLast() { checkNotNull(tasks.pollLast()) { "No queued gutter task" }.run() }
        fun runAll() { while (true) (tasks.pollFirst() ?: return).run() }
    }

    private suspend fun loadTargetContent(manager: VisualTrackerManager, file: VirtualFile): CharSequence? =
        suspendCoroutineUninterceptedOrReturn { continuation ->
            val method = VisualTrackerManager::class.java.getDeclaredMethod(
                "loadTargetContent", VirtualFile::class.java, String::class.java, String::class.java, Continuation::class.java
            ).apply { isAccessible = true }
            try {
                method.invoke(manager, file, "HEAD", file.path, continuation)
            } catch (e: InvocationTargetException) {
                throw e.targetException
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

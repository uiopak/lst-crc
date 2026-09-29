package com.github.uiopak.lstcrc.listeners

import com.github.uiopak.lstcrc.testsupport.LstCrcTestCase
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class VcsChangeListenerTest : LstCrcTestCase() {

    fun testHandleDocumentChangeTriggersRefreshForRepositoryFiles() {
        val trackedFile = myFixture.addFileToProject("tracked.txt", "tracked\n").virtualFile
        val refreshLatch = CountDownLatch(1)
        val listener = createListener(refreshLatch) { candidate -> candidate == trackedFile }

        try {
            waitUntil { listener.isCollectingSignals }
            listener.handleDocumentChange(trackedFile)

            assertTrue("Expected a refresh for repository-backed document changes", refreshLatch.await(2, TimeUnit.SECONDS))
        } finally {
            listener.dispose()
        }
    }

    fun testHandleDocumentChangeIgnoresNonRepositoryFiles() {
        val trackedFile = myFixture.addFileToProject("tracked.txt", "tracked\n").virtualFile
        val otherFile = myFixture.addFileToProject("other.txt", "other\n").virtualFile
        val refreshLatch = CountDownLatch(1)
        val listener = createListener(refreshLatch) { candidate -> candidate == trackedFile }

        try {
            waitUntil { listener.isCollectingSignals }
            listener.handleDocumentChange(otherFile)

            assertFalse("Non-repository files should not trigger a refresh", refreshLatch.await(500, TimeUnit.MILLISECONDS))
        } finally {
            listener.dispose()
        }
    }

    fun testHandleDocumentChangeDoesNotBlockOnRepositoryCheck() {
        val trackedFile = myFixture.addFileToProject("tracked.txt", "tracked\n").virtualFile
        val refreshLatch = CountDownLatch(1)
        val predicateStarted = CountDownLatch(1)
        val releasePredicate = CountDownLatch(1)
        val handleCompleted = CountDownLatch(1)
        val listener = createListener(refreshLatch) { candidate ->
            predicateStarted.countDown()
            releasePredicate.await(2, TimeUnit.SECONDS)
            candidate == trackedFile
        }

        try {
            waitUntil { listener.isCollectingSignals }
            thread(start = true, isDaemon = true) {
                listener.handleDocumentChange(trackedFile)
                handleCompleted.countDown()
            }

            assertTrue(
                "handleDocumentChange should return before repository resolution completes",
                handleCompleted.await(250, TimeUnit.MILLISECONDS)
            )
            assertTrue("Expected repository check to run asynchronously", predicateStarted.await(2, TimeUnit.SECONDS))

            releasePredicate.countDown()

            assertTrue(
                "Expected refresh after asynchronous repository resolution completes",
                refreshLatch.await(2, TimeUnit.SECONDS)
            )
        } finally {
            releasePredicate.countDown()
            listener.dispose()
        }
    }

    fun testDocumentEditsAloneRequestEditOnlyRefreshWhileVcsEventsRequestFullRefresh() {
        val trackedFile = myFixture.addFileToProject("tracked.txt", "tracked\n").virtualFile
        val fullRefreshes = AtomicInteger()
        val editOnlyRefreshes = AtomicInteger()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val listener = VcsChangeListener(
            project,
            scope,
            refreshCurrentSelection = { fullRefreshes.incrementAndGet() },
            isRepositoryFile = { candidate -> candidate == trackedFile },
            refreshAfterDocumentEdit = { editOnlyRefreshes.incrementAndGet() }
        )

        try {
            waitUntil { listener.isCollectingSignals }
            // A burst of edits alone: one edit-only refresh.
            repeat(3) { listener.handleDocumentChange(trackedFile) }
            waitUntil { editOnlyRefreshes.get() == 1 }
            assertEquals(0, fullRefreshes.get())

            // Edits plus a changelist update in the same burst: one full refresh.
            listener.handleDocumentChange(trackedFile)
            listener.changeListUpdateDone()
            listener.handleDocumentChange(trackedFile)
            waitUntil { fullRefreshes.get() == 1 }
            Thread.sleep(500)
            assertEquals(1, fullRefreshes.get())
            assertEquals(1, editOnlyRefreshes.get())
        } finally {
            listener.dispose()
            scope.cancel()
        }
    }

    fun testVcsEventSurvivesABurstWhileRepositoryCheckIsBusy() {
        assertFullRefreshSurvivesEditBurst { listener, _ -> listener.changeListUpdateDone() }
    }

    fun testDocumentSaveSurvivesABurstWhileRepositoryCheckIsBusy() {
        assertFullRefreshSurvivesEditBurst { listener, document -> listener.beforeDocumentSaving(document) }
    }

    fun testRepositoryEditSurvivesABurstOfForeignDocumentEdits() {
        val trackedFile = myFixture.addFileToProject("tracked.txt", "tracked\n").virtualFile
        val otherFile = myFixture.addFileToProject("other.txt", "other\n").virtualFile
        val predicateStarted = CountDownLatch(1)
        val releasePredicate = CountDownLatch(1)
        val firstCheck = AtomicBoolean(true)
        val refreshed = CountDownLatch(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val listener = VcsChangeListener(project, scope, { refreshed.countDown() }, { candidate ->
            if (firstCheck.getAndSet(false)) {
                predicateStarted.countDown()
                releasePredicate.await(5, TimeUnit.SECONDS)
            }
            candidate == trackedFile
        })

        try {
            waitUntil { listener.isCollectingSignals }
            listener.handleDocumentChange(otherFile)
            assertTrue("Expected the first repository check to start", predicateStarted.await(2, TimeUnit.SECONDS))
            listener.handleDocumentChange(trackedFile)
            repeat(128) { listener.handleDocumentChange(otherFile) }
            releasePredicate.countDown()

            assertTrue("Edits in other projects must not discard this project's refresh", refreshed.await(2, TimeUnit.SECONDS))
        } finally {
            releasePredicate.countDown()
            Disposer.dispose(listener)
            scope.cancel()
        }
    }

    fun testForeignDocumentSaveDoesNotForceAFullRefresh() {
        val trackedFile = myFixture.addFileToProject("tracked.txt", "tracked\n").virtualFile
        val otherFile = myFixture.addFileToProject("other.txt", "other\n").virtualFile
        val otherDocument = FileDocumentManager.getInstance().getDocument(otherFile)!!
        val fullRefreshes = AtomicInteger()
        val editRefreshed = CountDownLatch(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val listener = VcsChangeListener(
            project, scope,
            refreshCurrentSelection = { fullRefreshes.incrementAndGet() },
            isRepositoryFile = { it == trackedFile },
            refreshAfterDocumentEdit = { editRefreshed.countDown() }
        )

        try {
            waitUntil { listener.isCollectingSignals }
            listener.beforeDocumentSaving(otherDocument)
            listener.handleDocumentChange(trackedFile)

            assertTrue("Expected an edit-only refresh for this project's document", editRefreshed.await(2, TimeUnit.SECONDS))
            assertEquals("A save in another project must not force a disk reload", 0, fullRefreshes.get())
        } finally {
            Disposer.dispose(listener)
            scope.cancel()
        }
    }

    private fun assertFullRefreshSurvivesEditBurst(
        signal: (VcsChangeListener, com.intellij.openapi.editor.Document) -> Unit
    ) {
        val file = myFixture.addFileToProject("tracked.txt", "tracked\n").virtualFile
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        val predicateStarted = CountDownLatch(1)
        val releasePredicate = CountDownLatch(1)
        val firstCheck = AtomicBoolean(true)
        val fullRefreshed = CountDownLatch(1)
        val editRefreshes = AtomicInteger()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val listener = VcsChangeListener(
            project, scope,
            refreshCurrentSelection = { fullRefreshed.countDown() },
            isRepositoryFile = { candidate ->
                if (firstCheck.getAndSet(false)) {
                    predicateStarted.countDown()
                    releasePredicate.await(5, TimeUnit.SECONDS)
                }
                candidate == file
            },
            refreshAfterDocumentEdit = { editRefreshes.incrementAndGet() }
        )

        try {
            waitUntil { listener.isCollectingSignals }
            listener.handleDocumentChange(file)
            assertTrue("Expected the first repository check to start", predicateStarted.await(2, TimeUnit.SECONDS))
            signal(listener, document)
            repeat(128) { listener.handleDocumentChange(file) }
            releasePredicate.countDown()

            assertTrue("The burst must preserve its full refresh requirement", fullRefreshed.await(2, TimeUnit.SECONDS))
            assertEquals("The full refresh must replace the edit-only refresh", 0, editRefreshes.get())
        } finally {
            releasePredicate.countDown()
            Disposer.dispose(listener)
            scope.cancel()
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (!condition()) {
            assertTrue("Condition not met within 2 seconds", System.nanoTime() < deadline)
            Thread.sleep(20)
        }
    }

    private fun createListener(
        refreshLatch: CountDownLatch,
        isRepositoryFile: (com.intellij.openapi.vfs.VirtualFile) -> Boolean
    ): VcsChangeListener {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        return VcsChangeListener(project, scope, { refreshLatch.countDown() }, isRepositoryFile)
    }
}

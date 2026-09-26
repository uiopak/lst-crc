package com.github.uiopak.lstcrc.listeners

import com.github.uiopak.lstcrc.testsupport.LstCrcTestCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class VcsChangeListenerTest : LstCrcTestCase() {

    fun testHandleDocumentChangeTriggersRefreshForRepositoryFiles() {
        val trackedFile = myFixture.addFileToProject("tracked.txt", "tracked\n").virtualFile
        val refreshLatch = CountDownLatch(1)
        val listener = createListener(refreshLatch) { candidate -> candidate == trackedFile }

        try {
            Thread.sleep(100)
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
            Thread.sleep(100)
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
            Thread.sleep(100)
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
        val listener = VcsChangeListener.createForTest(
            project,
            scope,
            refreshCurrentSelection = { fullRefreshes.incrementAndGet() },
            isRepositoryFile = { candidate -> candidate == trackedFile },
            refreshAfterDocumentEdit = { editOnlyRefreshes.incrementAndGet() }
        )

        try {
            Thread.sleep(100)
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
        return VcsChangeListener.createForTest(project, scope, { refreshLatch.countDown() }, isRepositoryFile)
    }
}
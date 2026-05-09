package com.github.uiopak.lstcrc.listeners

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class VcsChangeListenerTest : BasePlatformTestCase() {

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

    private fun createListener(
        refreshLatch: CountDownLatch,
        isRepositoryFile: (com.intellij.openapi.vfs.VirtualFile) -> Boolean
    ): VcsChangeListener {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        return VcsChangeListener.createForTest(project, scope, { refreshLatch.countDown() }, isRepositoryFile)
    }
}
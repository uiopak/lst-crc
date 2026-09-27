package com.github.uiopak.lstcrc.listeners

import com.github.uiopak.lstcrc.testsupport.LstCrcTestCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletableFuture

class PluginStartupActivityTest : LstCrcTestCase() {

    // Regression (round three): the startup catch-all swallowed cancellation, so a closing project kept starting up.
    fun testInitialDiffLoadPropagatesCancellation() {
        val cancelled = CompletableFuture<Unit>().apply { completeExceptionally(CancellationException("project closing")) }

        assertThrows(CancellationException::class.java) {
            runBlocking { awaitInitialDiffLoad(cancelled) }
        }
    }

    fun testInitialDiffLoadFailureDoesNotStopStartup() {
        val failed = CompletableFuture<Unit>().apply { completeExceptionally(IllegalStateException("git failed")) }

        runBlocking { awaitInitialDiffLoad(failed) }
    }
}

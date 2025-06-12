package com.github.uiopak.lstcrc.utils

import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Awaits the completion of a [CompletableFuture] without blocking a thread.
 *
 * This is a lightweight, dependency-free alternative to `kotlinx-coroutines-jdk8`.
 * It suspends the coroutine and resumes it when the future is completed.
 * If the future completes with an exception, it is thrown by this function.
 *
 * @return The result of the CompletableFuture.
 */
suspend fun <T> CompletableFuture<T>.await(): T =
    suspendCancellableCoroutine { continuation ->
        whenComplete { result, exception ->
            if (exception != null) {
                continuation.resumeWithException(exception)
            } else {
                continuation.resume(result)
            }
        }
        continuation.invokeOnCancellation {
            this.cancel(false)
        }
    }
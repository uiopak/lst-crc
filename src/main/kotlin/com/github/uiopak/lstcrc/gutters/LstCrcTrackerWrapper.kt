package com.github.uiopak.lstcrc.gutters

import com.intellij.openapi.vcs.ex.LocalLineStatusTracker
import com.intellij.openapi.vcs.ex.Range
import com.intellij.openapi.vcs.ex.SimpleLocalLineStatusTracker

/**
 * Wraps [SimpleLocalLineStatusTracker] to intercept specific calls.
 *
 * Specifically, we override [isOperational] to return `false` when called from
 * [com.intellij.openapi.vcs.actions.AnnotateWarningsService].
 * This forces [com.intellij.openapi.vcs.impl.UpToDateLineNumberProviderImpl] to use
 * the Document's line count (matched to the editor) instead of the Base Revision's line count,
 * avoiding the "Unexpected annotation lines number" warning when local changes exist.
 */
class LstCrcTrackerWrapper(val delegate: SimpleLocalLineStatusTracker) :
    LocalLineStatusTracker<Range> by delegate {

    override fun isOperational(): Boolean {
        if (shouldSuppressOperationalStatus()) {
            return false
        }
        return delegate.isOperational()
    }

    private fun shouldSuppressOperationalStatus(): Boolean {
        // Check the call stack for AnnotateWarningsService
        val stackTrace = Thread.currentThread().stackTrace
        for (element in stackTrace) {
            if (element.className.contains("AnnotateWarningsService")) {
                return true
            }
        }
        return false
    }

    // Explicitly allow access to the delegate for internal casting if needed,
    // though ideally consumers should use the interface.
    fun getDelegateTracker(): SimpleLocalLineStatusTracker = delegate
}

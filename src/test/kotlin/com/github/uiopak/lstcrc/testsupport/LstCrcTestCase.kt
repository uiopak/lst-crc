package com.github.uiopak.lstcrc.testsupport

import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Base class for the plugin's light platform tests.
 *
 * IntelliJ IDEA 2026.2.2+ ships an obfuscated `com.intellij.modules.ultimate` startup activity whose
 * class name collides with another obfuscated class, so it cannot be instantiated when the test
 * project opens. The IDE only logs that error, but the test framework turns every logged error into
 * a failure. This ignores exactly that error and keeps all others fatal.
 * See https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2182.
 */
abstract class LstCrcTestCase : BasePlatformTestCase() {
    private companion object {
        init {
            // Installed once for the whole test JVM; the token is intentionally never closed.
            LoggedErrorProcessor.executeWith(IgnoreUltimateStartupActivityError)
        }
    }

    private object IgnoreUltimateStartupActivityError : LoggedErrorProcessor() {
        override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> {
            val text = buildString {
                append(message)
                generateSequence(t) { it.cause }.forEach { append('\n').append(it.message) }
            }
            return if ("com.intellij.modules.ultimate" in text && "Cannot find suitable constructor" in text) {
                Action.NONE
            } else {
                super.processError(category, message, details, t)
            }
        }
    }
}

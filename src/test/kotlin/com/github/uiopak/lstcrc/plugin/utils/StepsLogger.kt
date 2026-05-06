// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.github.uiopak.lstcrc.plugin.utils

import com.intellij.remoterobot.stepsProcessing.StepLogger
import com.intellij.remoterobot.stepsProcessing.StepWorker

object StepsLogger {
    private var initialized = false
    @JvmStatic
    fun init() {
        if (initialized.not()) {
            StepWorker.registerProcessor(StepLogger())
            initialized = true
        }
    }
}
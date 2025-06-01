package com.github.uiopak.lstcrc.listeners

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

class MySimpleStartupActivity : StartupActivity.DumbAware {
    private val logger = thisLogger()

    override fun runActivity(project: Project) {
        logger.info("ZZZZ_TEST_LOG StartupActivity.DumbAware IS RUNNING for project: ${project.name} - NOW EXPECTED TO BE OBSOLETE/EMPTY")
        // All substantive logic moved to ProjectOpenCloseListener or not needed if this was just a test
    }
}

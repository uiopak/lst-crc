package com.github.uiopak.lstcrc.plugin

import com.github.uiopak.lstcrc.plugin.utils.RemoteRobotExtension
import com.github.uiopak.lstcrc.plugin.utils.StepsLogger
import com.github.uiopak.lstcrc.plugin.utils.createFreshProjectFromWelcomeScreen
import com.intellij.remoterobot.RemoteRobot
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.junit.jupiter.api.extension.ExtendWith

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Tag("ui")
@ExtendWith(RemoteRobotExtension::class)
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
annotation class LstCrcUiTest

abstract class LstCrcUiTestSupport {
    init {
        StepsLogger.init()
    }

    protected fun RemoteRobot.prepareFreshProject() {
        createFreshProjectFromWelcomeScreen()
    }
}
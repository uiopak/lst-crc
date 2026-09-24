package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.github.uiopak.lstcrc.state.TabInfo
import com.github.uiopak.lstcrc.state.ToolWindowState
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LstCrcStatusWidgetTest : BasePlatformTestCase() {

    override fun tearDown() {
        try {
            ApplicationManager.getApplication().service<LstCrcSettingsService>().resetToDefaults()
        } finally {
            super.tearDown()
        }
    }

    fun testGetTextReturnsHeadWhenHeadIsSelectedEvenIfWidgetContextEnabled() {
        val stateService = project.service<ToolWindowStateService>()
        val widget = LstCrcStatusWidget(project)

        setShowWidgetContext(true)
        stateService.noStateLoaded()

        assertEquals(LstCrcBundle.message("tab.name.head"), widget.getText())
    }

    fun testGetTextUsesAliasPrefixAndTruncationForSelectedTab() {
        val stateService = project.service<ToolWindowStateService>()
        val widget = LstCrcStatusWidget(project)
        val longAlias = "renamed-feature-with-long-name"

        setShowWidgetContext(true)
        stateService.loadState(
            ToolWindowState(
                openTabs = listOf(TabInfo(branchName = "feature-widget", alias = longAlias)),
                selectedTabIndex = 0
            )
        )

        assertEquals(
            LstCrcBundle.message("widget.context.prefix") + longAlias.take(20),
            widget.getText()
        )
    }

    fun testGetTextFallsBackToPluginNameForInvalidSelectedTabIndex() {
        val stateService = project.service<ToolWindowStateService>()
        val widget = LstCrcStatusWidget(project)

        setShowWidgetContext(true)
        stateService.loadState(
            ToolWindowState(
                openTabs = listOf(TabInfo(branchName = "feature-widget")),
                selectedTabIndex = 3
            )
        )

        assertEquals(LstCrcBundle.message("plugin.name.short"), widget.getText())
    }

    fun testPluginXmlStatusWidgetFactoryIdMatchesWidgetConstant() {
        val pluginXml = javaClass.classLoader.getResourceAsStream("META-INF/plugin.xml")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("Could not load META-INF/plugin.xml from test classpath.")

        assertTrue(
            "plugin.xml statusBarWidgetFactory id should match LstCrcStatusWidget.ID.",
            pluginXml.contains("""statusBarWidgetFactory implementation="com.github.uiopak.lstcrc.toolWindow.LstCrcStatusWidgetFactory" id="${LstCrcStatusWidget.ID}"""),
        )
    }

    @Suppress("SameParameterValue")
    private fun setShowWidgetContext(show: Boolean) {
        ApplicationManager.getApplication().service<LstCrcSettingsService>().setShowWidgetContext(show)
    }
}
package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.github.uiopak.lstcrc.state.TabInfo
import com.github.uiopak.lstcrc.state.ToolWindowState
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.github.uiopak.lstcrc.testsupport.LstCrcTestCase

class LstCrcStatusWidgetTest : LstCrcTestCase() {

    override fun tearDown() {
        try {
            ApplicationManager.getApplication().service<LstCrcSettingsService>().resetToDefaults()
        } finally {
            super.tearDown()
        }
    }

    // Action texts treat '_' as a mnemonic marker, which dropped it from branch names in the popup.
    fun testPopupShowsTabNamesWithUnderscoresAsTyped() {
        val texts = LstCrcStatusWidget(project)
            .createPopupActions(listOf(TabInfo(branchName = "feature_login"), TabInfo(branchName = "fix", alias = "my_alias")))
            .mapNotNull { it.templatePresentation.text }

        assertTrue(texts.toString(), texts.containsAll(listOf("feature_login", "my_alias")))
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

        // A cut name ends with an ellipsis (20 characters in all), and the tooltip shows the whole name.
        assertEquals(
            LstCrcBundle.message("widget.context.prefix") + longAlias.take(19) + "\u2026",
            widget.getText()
        )
        assertTrue(widget.getTooltipText(), widget.getTooltipText().contains(longAlias))

        // A name of exactly 20 characters is shown whole.
        stateService.loadState(
            ToolWindowState(openTabs = listOf(TabInfo(branchName = "renamed-feature-menu")), selectedTabIndex = 0)
        )
        assertEquals(LstCrcBundle.message("widget.context.prefix") + "renamed-feature-menu", widget.getText())
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
        ApplicationManager.getApplication().service<LstCrcSettingsService>()[LstCrcSettingDefinitions.SHOW_WIDGET_CONTEXT] = show
    }
}
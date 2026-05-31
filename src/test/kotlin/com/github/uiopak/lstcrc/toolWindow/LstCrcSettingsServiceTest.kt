package com.github.uiopak.lstcrc.toolWindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LstCrcSettingsServiceTest : BasePlatformTestCase() {

    fun testResetToDefaultsRestoresRepresentativeValues() {
        val settings = ApplicationManager.getApplication().service<LstCrcSettingsService>()

        try {
            settings.setSingleClickAction(ToolWindowSettingsProvider.ACTION_NONE)
            settings.setDoubleClickAction(ToolWindowSettingsProvider.ACTION_OPEN_DIFF)
            settings.setContextMenuEnabled(true)
            settings.setUserDoubleClickDelay(500)
            settings.setShowLineStatsInTree(true)

            settings.resetToDefaults()

            assertEquals(LstCrcSettingDefinitions.SINGLE_CLICK_ACTION.defaultValue, settings.getSingleClickAction())
            assertEquals(LstCrcSettingDefinitions.DOUBLE_CLICK_ACTION.defaultValue, settings.getDoubleClickAction())
            assertFalse(settings.isContextMenuEnabled())
            assertEquals(LstCrcSettingDefinitions.USER_DOUBLE_CLICK_DELAY.defaultValue, settings.getUserDoubleClickDelay())
            assertFalse(settings.isShowLineStatsInTree())
        } finally {
            settings.resetToDefaults()
        }
    }

    fun testSettersAndGettersRoundTripValues() {
        val settings = ApplicationManager.getApplication().service<LstCrcSettingsService>()

        try {
            settings.setRightClickAction(ToolWindowSettingsProvider.ACTION_SHOW_IN_PROJECT_TREE)
            settings.setShowWidgetContext(true)
            settings.setIncludeHeadInScopes(true)
            settings.setShowToolWindowTitle(true)
            settings.setUserDoubleClickDelay(300)

            assertEquals(ToolWindowSettingsProvider.ACTION_SHOW_IN_PROJECT_TREE, settings.getRightClickAction())
            assertTrue(settings.isShowWidgetContext())
            assertTrue(settings.isIncludeHeadInScopes())
            assertTrue(settings.isShowToolWindowTitle())
            assertEquals(300, settings.getUserDoubleClickDelay())
        } finally {
            settings.resetToDefaults()
        }
    }
}
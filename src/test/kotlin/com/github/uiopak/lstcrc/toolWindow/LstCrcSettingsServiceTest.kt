package com.github.uiopak.lstcrc.toolWindow

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.github.uiopak.lstcrc.testsupport.LstCrcTestCase

class LstCrcSettingsServiceTest : LstCrcTestCase() {

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

    fun testImportsLegacyPropertiesComponentValues() {
        val legacy = PropertiesComponent.getInstance()
        try {
            legacy.setValue(LstCrcSettingDefinitions.SINGLE_CLICK_ACTION.key, ToolWindowSettingsProvider.ACTION_OPEN_DIFF)
            legacy.setValue(LstCrcSettingDefinitions.ENABLE_GUTTER_MARKERS.key, false, true)
            legacy.setValue(LstCrcSettingDefinitions.USER_DOUBLE_CLICK_DELAY.key, 250, -1)

            val settings = LstCrcSettingsService()
            settings.importLegacySettings(legacy)

            assertEquals(ToolWindowSettingsProvider.ACTION_OPEN_DIFF, settings.getSingleClickAction())
            assertFalse(settings.isGutterMarkersEnabled())
            assertEquals(250, settings.getUserDoubleClickDelay())
            // Keys absent from the legacy store keep their defaults.
            assertEquals(LstCrcSettingDefinitions.MIDDLE_CLICK_ACTION.defaultValue, settings.getMiddleClickAction())
            assertTrue(settings.isShowContextForSingleRepo())
        } finally {
            LstCrcSettingDefinitions.allKeys.forEach(legacy::unsetValue)
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
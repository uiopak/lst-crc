package com.github.uiopak.lstcrc.toolWindow

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.github.uiopak.lstcrc.testsupport.LstCrcTestCase

class LstCrcSettingsServiceTest : LstCrcTestCase() {

    fun testResetToDefaultsRestoresRepresentativeValues() {
        val settings = ApplicationManager.getApplication().service<LstCrcSettingsService>()

        try {
            settings[LstCrcSettingDefinitions.SINGLE_CLICK_ACTION] = ToolWindowSettingsProvider.ACTION_NONE
            settings[LstCrcSettingDefinitions.DOUBLE_CLICK_ACTION] = ToolWindowSettingsProvider.ACTION_OPEN_DIFF
            settings[LstCrcSettingDefinitions.SHOW_CONTEXT_MENU] = true
            settings[LstCrcSettingDefinitions.USER_DOUBLE_CLICK_DELAY] = 500
            settings[LstCrcSettingDefinitions.SHOW_LINE_STATS_IN_TREE] = true

            settings.resetToDefaults()

            assertEquals(LstCrcSettingDefinitions.SINGLE_CLICK_ACTION.defaultValue, settings[LstCrcSettingDefinitions.SINGLE_CLICK_ACTION])
            assertEquals(LstCrcSettingDefinitions.DOUBLE_CLICK_ACTION.defaultValue, settings[LstCrcSettingDefinitions.DOUBLE_CLICK_ACTION])
            assertFalse(settings[LstCrcSettingDefinitions.SHOW_CONTEXT_MENU])
            assertEquals(LstCrcSettingDefinitions.USER_DOUBLE_CLICK_DELAY.defaultValue, settings[LstCrcSettingDefinitions.USER_DOUBLE_CLICK_DELAY])
            assertFalse(settings[LstCrcSettingDefinitions.SHOW_LINE_STATS_IN_TREE])
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

            assertEquals(ToolWindowSettingsProvider.ACTION_OPEN_DIFF, settings[LstCrcSettingDefinitions.SINGLE_CLICK_ACTION])
            assertFalse(settings[LstCrcSettingDefinitions.ENABLE_GUTTER_MARKERS])
            assertEquals(250, settings[LstCrcSettingDefinitions.USER_DOUBLE_CLICK_DELAY])
            // Keys absent from the legacy store keep their defaults.
            assertEquals(LstCrcSettingDefinitions.MIDDLE_CLICK_ACTION.defaultValue, settings[LstCrcSettingDefinitions.MIDDLE_CLICK_ACTION])
            assertTrue(settings[LstCrcSettingDefinitions.SHOW_CONTEXT_SINGLE_REPO])
        } finally {
            LstCrcSettingDefinitions.allKeys.forEach(legacy::unsetValue)
        }
    }

    fun testSettersAndGettersRoundTripValues() {
        val settings = ApplicationManager.getApplication().service<LstCrcSettingsService>()

        try {
            settings[LstCrcSettingDefinitions.RIGHT_CLICK_ACTION] = ToolWindowSettingsProvider.ACTION_SHOW_IN_PROJECT_TREE
            settings[LstCrcSettingDefinitions.SHOW_WIDGET_CONTEXT] = true
            settings[LstCrcSettingDefinitions.INCLUDE_HEAD_IN_SCOPES] = true
            settings[LstCrcSettingDefinitions.SHOW_TOOL_WINDOW_TITLE] = true
            settings[LstCrcSettingDefinitions.USER_DOUBLE_CLICK_DELAY] = 300

            assertEquals(ToolWindowSettingsProvider.ACTION_SHOW_IN_PROJECT_TREE, settings[LstCrcSettingDefinitions.RIGHT_CLICK_ACTION])
            assertTrue(settings[LstCrcSettingDefinitions.SHOW_WIDGET_CONTEXT])
            assertTrue(settings[LstCrcSettingDefinitions.INCLUDE_HEAD_IN_SCOPES])
            assertTrue(settings[LstCrcSettingDefinitions.SHOW_TOOL_WINDOW_TITLE])
            assertEquals(300, settings[LstCrcSettingDefinitions.USER_DOUBLE_CLICK_DELAY])
        } finally {
            settings.resetToDefaults()
        }
    }
}
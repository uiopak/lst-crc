package com.github.uiopak.lstcrc.toolWindow

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

data class StringSettingDefinition(val key: String, val defaultValue: String)
data class BooleanSettingDefinition(val key: String, val defaultValue: Boolean)
data class IntSettingDefinition(val key: String, val defaultValue: Int)

object LstCrcSettingDefinitions {
    val SINGLE_CLICK_ACTION = StringSettingDefinition(
        key = "com.github.uiopak.lstcrc.app.singleClickAction",
        defaultValue = ToolWindowSettingsProvider.ACTION_OPEN_SOURCE
    )
    val DOUBLE_CLICK_ACTION = StringSettingDefinition(
        key = "com.github.uiopak.lstcrc.app.doubleClickAction",
        defaultValue = ToolWindowSettingsProvider.ACTION_NONE
    )
    val MIDDLE_CLICK_ACTION = StringSettingDefinition(
        key = "com.github.uiopak.lstcrc.app.middleClickAction",
        defaultValue = ToolWindowSettingsProvider.ACTION_SHOW_IN_PROJECT_TREE
    )
    val DOUBLE_MIDDLE_CLICK_ACTION = StringSettingDefinition(
        key = "com.github.uiopak.lstcrc.app.doubleMiddleClickAction",
        defaultValue = ToolWindowSettingsProvider.ACTION_NONE
    )
    val RIGHT_CLICK_ACTION = StringSettingDefinition(
        key = "com.github.uiopak.lstcrc.app.rightClickAction",
        defaultValue = ToolWindowSettingsProvider.ACTION_OPEN_DIFF
    )
    val DOUBLE_RIGHT_CLICK_ACTION = StringSettingDefinition(
        key = "com.github.uiopak.lstcrc.app.doubleRightClickAction",
        defaultValue = ToolWindowSettingsProvider.ACTION_NONE
    )

    val SHOW_CONTEXT_MENU = BooleanSettingDefinition(
        key = "com.github.uiopak.lstcrc.app.showContextMenu",
        defaultValue = false
    )
    val USER_DOUBLE_CLICK_DELAY = IntSettingDefinition(
        key = "com.github.uiopak.lstcrc.app.userDoubleClickDelay",
        defaultValue = -1
    )
    val INCLUDE_HEAD_IN_SCOPES = BooleanSettingDefinition(
        key = "com.github.uiopak.lstcrc.app.includeHeadInScopes",
        defaultValue = false
    )
    val ENABLE_GUTTER_MARKERS = BooleanSettingDefinition(
        key = "com.github.uiopak.lstcrc.app.enableGutterMarkers",
        defaultValue = true
    )
    val ENABLE_GUTTER_FOR_NEW_FILES = BooleanSettingDefinition(
        key = "com.github.uiopak.lstcrc.app.enableGutterForNewFiles",
        defaultValue = false
    )
    val SHOW_TOOL_WINDOW_TITLE = BooleanSettingDefinition(
        key = "com.github.uiopak.lstcrc.app.showToolWindowTitle",
        defaultValue = false
    )
    val SHOW_WIDGET_CONTEXT = BooleanSettingDefinition(
        key = "com.github.uiopak.lstcrc.app.showWidgetContext",
        defaultValue = false
    )
    val EXPAND_NEW_FILES_IN_COLLAPSED_DIRS = BooleanSettingDefinition(
        key = "com.github.uiopak.lstcrc.app.expandNewFilesInCollapsedDirs",
        defaultValue = true
    )
    val SHOW_UNTRACKED_FILES_AS_NEW = BooleanSettingDefinition(
        key = "com.github.uiopak.lstcrc.app.showUntrackedFilesAsNew",
        defaultValue = false
    )
    val SHOW_LINE_STATS_IN_TREE = BooleanSettingDefinition(
        key = "com.github.uiopak.lstcrc.app.showLineStatsInTree",
        defaultValue = false
    )
    val SHOW_CONTEXT_SINGLE_REPO = BooleanSettingDefinition(
        key = "com.github.uiopak.lstcrc.app.showContextSingleRepo",
        defaultValue = true
    )
    val SHOW_CONTEXT_MULTI_REPO = BooleanSettingDefinition(
        key = "com.github.uiopak.lstcrc.app.showContextMultiRepo",
        defaultValue = true
    )
    val SHOW_CONTEXT_FOR_COMMITS = BooleanSettingDefinition(
        key = "com.github.uiopak.lstcrc.app.showContextForCommits",
        defaultValue = false
    )

    val stringSettings: List<StringSettingDefinition> = listOf(
        SINGLE_CLICK_ACTION, DOUBLE_CLICK_ACTION, MIDDLE_CLICK_ACTION,
        DOUBLE_MIDDLE_CLICK_ACTION, RIGHT_CLICK_ACTION, DOUBLE_RIGHT_CLICK_ACTION
    )

    val booleanSettings: List<BooleanSettingDefinition> = listOf(
        SHOW_CONTEXT_MENU, INCLUDE_HEAD_IN_SCOPES, ENABLE_GUTTER_MARKERS,
        ENABLE_GUTTER_FOR_NEW_FILES, SHOW_TOOL_WINDOW_TITLE, SHOW_WIDGET_CONTEXT,
        SHOW_CONTEXT_SINGLE_REPO, SHOW_CONTEXT_MULTI_REPO, SHOW_CONTEXT_FOR_COMMITS,
        SHOW_LINE_STATS_IN_TREE, EXPAND_NEW_FILES_IN_COLLAPSED_DIRS, SHOW_UNTRACKED_FILES_AS_NEW
    )

    val intSettings: List<IntSettingDefinition> = listOf(USER_DOUBLE_CLICK_DELAY)

    val allKeys: List<String>
        get() = stringSettings.map { it.key } + booleanSettings.map { it.key } + intSettings.map { it.key }
}

@State(name = "LstCrcSettingsService", storages = [Storage("lstCrcSettings.xml")])
@Service(Service.Level.APP)
class LstCrcSettingsService : PersistentStateComponent<LstCrcSettingsService.SettingsState> {

    data class SettingsState(
        var values: MutableMap<String, String> = mutableMapOf()
    )

    private var state = SettingsState()

    override fun getState(): SettingsState = state
    override fun loadState(state: SettingsState) { this.state = state }

    /**
     * Earlier plugin versions stored settings in the application-level [PropertiesComponent]
     * under the same keys. When no settings file exists yet, import those values once so upgrading
     * users keep their configuration. Legacy keys are left in place so a downgrade still works.
     */
    override fun noStateLoaded() {
        importLegacySettings(PropertiesComponent.getInstance())
    }

    internal fun importLegacySettings(legacy: PropertiesComponent) {
        LstCrcSettingDefinitions.allKeys.forEach { key ->
            legacy.getValue(key)?.takeUnless(String::isBlank)?.let { state.values[key] = it }
        }
    }

    private fun storedValue(key: String): String? = state.values[key]?.takeUnless(String::isBlank)

    // --- Typed accessors: settings[LstCrcSettingDefinitions.SHOW_LINE_STATS_IN_TREE] = true ---

    operator fun get(definition: StringSettingDefinition): String = getString(definition.key, definition.defaultValue)
    operator fun set(definition: StringSettingDefinition, value: String) = setString(definition.key, value)

    operator fun get(definition: BooleanSettingDefinition): Boolean = getBoolean(definition.key, definition.defaultValue)
    operator fun set(definition: BooleanSettingDefinition, value: Boolean) = setBoolean(definition.key, value)

    operator fun get(definition: IntSettingDefinition): Int = getInt(definition.key, definition.defaultValue)
    operator fun set(definition: IntSettingDefinition, value: Int) = setInt(definition.key, value)

    // --- Raw-key accessors, used by the Remote Robot JavaScript (which cannot pick an operator overload) ---

    fun getString(key: String, default: String): String = storedValue(key) ?: default

    fun setString(key: String, value: String) {
        state.values[key] = value
    }

    fun getBoolean(key: String, default: Boolean): Boolean =
        storedValue(key)?.toBooleanStrictOrNull() ?: default

    fun setBoolean(key: String, value: Boolean) {
        state.values[key] = value.toString()
    }

    fun getInt(key: String, default: Int): Int =
        storedValue(key)?.toIntOrNull() ?: default

    fun setInt(key: String, value: Int) {
        state.values[key] = value.toString()
    }

    @Suppress("unused")
    fun resetToDefaults() {
        LstCrcSettingDefinitions.stringSettings.forEach { this[it] = it.defaultValue }
        LstCrcSettingDefinitions.booleanSettings.forEach { this[it] = it.defaultValue }
        LstCrcSettingDefinitions.intSettings.forEach { this[it] = it.defaultValue }
    }
}

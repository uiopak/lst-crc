package com.github.uiopak.lstcrc.toolWindow

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/** One persisted setting: its key, default and how a stored string is read back (null when it can't be). */
class SettingDefinition<T : Any>(val key: String, val defaultValue: T, val parse: (String) -> T?)

private const val KEY_PREFIX = "com.github.uiopak.lstcrc.app."

private fun stringSetting(name: String, defaultValue: String) = SettingDefinition(KEY_PREFIX + name, defaultValue) { it }
private fun booleanSetting(name: String, defaultValue: Boolean) = SettingDefinition(KEY_PREFIX + name, defaultValue, String::toBooleanStrictOrNull)
private fun intSetting(name: String, defaultValue: Int) = SettingDefinition(KEY_PREFIX + name, defaultValue, String::toIntOrNull)

object LstCrcSettingDefinitions {
    val SINGLE_CLICK_ACTION = stringSetting("singleClickAction", ToolWindowSettingsProvider.ACTION_OPEN_SOURCE)
    val DOUBLE_CLICK_ACTION = stringSetting("doubleClickAction", ToolWindowSettingsProvider.ACTION_NONE)
    val MIDDLE_CLICK_ACTION = stringSetting("middleClickAction", ToolWindowSettingsProvider.ACTION_SHOW_IN_PROJECT_TREE)
    val DOUBLE_MIDDLE_CLICK_ACTION = stringSetting("doubleMiddleClickAction", ToolWindowSettingsProvider.ACTION_NONE)
    val RIGHT_CLICK_ACTION = stringSetting("rightClickAction", ToolWindowSettingsProvider.ACTION_OPEN_DIFF)
    val DOUBLE_RIGHT_CLICK_ACTION = stringSetting("doubleRightClickAction", ToolWindowSettingsProvider.ACTION_NONE)

    val SHOW_CONTEXT_MENU = booleanSetting("showContextMenu", false)
    val USER_DOUBLE_CLICK_DELAY = intSetting("userDoubleClickDelay", -1)
    val INCLUDE_HEAD_IN_SCOPES = booleanSetting("includeHeadInScopes", false)
    val ENABLE_GUTTER_MARKERS = booleanSetting("enableGutterMarkers", true)
    val ENABLE_GUTTER_FOR_NEW_FILES = booleanSetting("enableGutterForNewFiles", false)
    val SHOW_TOOL_WINDOW_TITLE = booleanSetting("showToolWindowTitle", false)
    val SHOW_WIDGET_CONTEXT = booleanSetting("showWidgetContext", false)
    val EXPAND_NEW_FILES_IN_COLLAPSED_DIRS = booleanSetting("expandNewFilesInCollapsedDirs", true)
    val SHOW_UNTRACKED_FILES_AS_NEW = booleanSetting("showUntrackedFilesAsNew", false)
    val SHOW_LINE_STATS_IN_TREE = booleanSetting("showLineStatsInTree", false)
    val SHOW_CONTEXT_SINGLE_REPO = booleanSetting("showContextSingleRepo", true)
    val SHOW_CONTEXT_MULTI_REPO = booleanSetting("showContextMultiRepo", true)
    val SHOW_CONTEXT_FOR_COMMITS = booleanSetting("showContextForCommits", false)

    val all: List<SettingDefinition<*>> = listOf(
        SINGLE_CLICK_ACTION, DOUBLE_CLICK_ACTION, MIDDLE_CLICK_ACTION,
        DOUBLE_MIDDLE_CLICK_ACTION, RIGHT_CLICK_ACTION, DOUBLE_RIGHT_CLICK_ACTION,
        SHOW_CONTEXT_MENU, USER_DOUBLE_CLICK_DELAY, INCLUDE_HEAD_IN_SCOPES, ENABLE_GUTTER_MARKERS,
        ENABLE_GUTTER_FOR_NEW_FILES, SHOW_TOOL_WINDOW_TITLE, SHOW_WIDGET_CONTEXT,
        SHOW_CONTEXT_SINGLE_REPO, SHOW_CONTEXT_MULTI_REPO, SHOW_CONTEXT_FOR_COMMITS,
        SHOW_LINE_STATS_IN_TREE, EXPAND_NEW_FILES_IN_COLLAPSED_DIRS, SHOW_UNTRACKED_FILES_AS_NEW
    )

    val allKeys: List<String>
        get() = all.map { it.key }
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

    operator fun <T : Any> get(definition: SettingDefinition<T>): T =
        storedValue(definition.key)?.let(definition.parse) ?: definition.defaultValue

    operator fun <T : Any> set(definition: SettingDefinition<T>, value: T) {
        state.values[definition.key] = value.toString()
    }

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
        LstCrcSettingDefinitions.all.forEach { state.values[it.key] = it.defaultValue.toString() }
    }
}

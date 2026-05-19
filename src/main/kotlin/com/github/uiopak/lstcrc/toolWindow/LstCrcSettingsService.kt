package com.github.uiopak.lstcrc.toolWindow

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "LstCrcSettingsService", storages = [Storage("lstCrcSettings.xml")])
@Service(Service.Level.APP)
class LstCrcSettingsService : PersistentStateComponent<LstCrcSettingsService.SettingsState> {

    private data class StringSettingDefinition(val key: String, val defaultValue: String)
    private data class BooleanSettingDefinition(val key: String, val defaultValue: Boolean)
    private data class IntSettingDefinition(val key: String, val defaultValue: Int)

    private companion object {
        private val SINGLE_CLICK_ACTION = StringSettingDefinition(
            ToolWindowSettingsProvider.APP_SINGLE_CLICK_ACTION_KEY,
            ToolWindowSettingsProvider.DEFAULT_SINGLE_CLICK_ACTION
        )
        private val DOUBLE_CLICK_ACTION = StringSettingDefinition(
            ToolWindowSettingsProvider.APP_DOUBLE_CLICK_ACTION_KEY,
            ToolWindowSettingsProvider.DEFAULT_DOUBLE_CLICK_ACTION
        )
        private val MIDDLE_CLICK_ACTION = StringSettingDefinition(
            ToolWindowSettingsProvider.APP_MIDDLE_CLICK_ACTION_KEY,
            ToolWindowSettingsProvider.DEFAULT_MIDDLE_CLICK_ACTION
        )
        private val DOUBLE_MIDDLE_CLICK_ACTION = StringSettingDefinition(
            ToolWindowSettingsProvider.APP_DOUBLE_MIDDLE_CLICK_ACTION_KEY,
            ToolWindowSettingsProvider.DEFAULT_DOUBLE_MIDDLE_CLICK_ACTION
        )
        private val RIGHT_CLICK_ACTION = StringSettingDefinition(
            ToolWindowSettingsProvider.APP_RIGHT_CLICK_ACTION_KEY,
            ToolWindowSettingsProvider.DEFAULT_RIGHT_CLICK_ACTION
        )
        private val DOUBLE_RIGHT_CLICK_ACTION = StringSettingDefinition(
            ToolWindowSettingsProvider.APP_DOUBLE_RIGHT_CLICK_ACTION_KEY,
            ToolWindowSettingsProvider.DEFAULT_DOUBLE_RIGHT_CLICK_ACTION
        )
        private val SHOW_CONTEXT_MENU = BooleanSettingDefinition(
            ToolWindowSettingsProvider.APP_SHOW_CONTEXT_MENU_KEY,
            ToolWindowSettingsProvider.DEFAULT_SHOW_CONTEXT_MENU
        )
        private val USER_DOUBLE_CLICK_DELAY = IntSettingDefinition(
            ToolWindowSettingsProvider.APP_USER_DOUBLE_CLICK_DELAY_KEY,
            ToolWindowSettingsProvider.DELAY_OPTION_SYSTEM_DEFAULT
        )
        private val INCLUDE_HEAD_IN_SCOPES = BooleanSettingDefinition(
            ToolWindowSettingsProvider.APP_INCLUDE_HEAD_IN_SCOPES_KEY,
            ToolWindowSettingsProvider.DEFAULT_INCLUDE_HEAD_IN_SCOPES
        )
        private val ENABLE_GUTTER_MARKERS = BooleanSettingDefinition(
            ToolWindowSettingsProvider.APP_ENABLE_GUTTER_MARKERS_KEY,
            ToolWindowSettingsProvider.DEFAULT_ENABLE_GUTTER_MARKERS
        )
        private val ENABLE_GUTTER_FOR_NEW_FILES = BooleanSettingDefinition(
            ToolWindowSettingsProvider.APP_ENABLE_GUTTER_FOR_NEW_FILES_KEY,
            ToolWindowSettingsProvider.DEFAULT_ENABLE_GUTTER_FOR_NEW_FILES
        )
        private val SHOW_TOOL_WINDOW_TITLE = BooleanSettingDefinition(
            ToolWindowSettingsProvider.APP_SHOW_TOOL_WINDOW_TITLE_KEY,
            ToolWindowSettingsProvider.DEFAULT_SHOW_TOOL_WINDOW_TITLE
        )
        private val SHOW_WIDGET_CONTEXT = BooleanSettingDefinition(
            ToolWindowSettingsProvider.APP_SHOW_WIDGET_CONTEXT_KEY,
            ToolWindowSettingsProvider.DEFAULT_SHOW_WIDGET_CONTEXT
        )
        private val SHOW_CONTEXT_SINGLE_REPO = BooleanSettingDefinition(
            ToolWindowSettingsProvider.APP_SHOW_CONTEXT_SINGLE_REPO_KEY,
            ToolWindowSettingsProvider.DEFAULT_SHOW_CONTEXT_SINGLE_REPO
        )
        private val SHOW_CONTEXT_MULTI_REPO = BooleanSettingDefinition(
            ToolWindowSettingsProvider.APP_SHOW_CONTEXT_MULTI_REPO_KEY,
            ToolWindowSettingsProvider.DEFAULT_SHOW_CONTEXT_MULTI_REPO
        )
        private val SHOW_CONTEXT_FOR_COMMITS = BooleanSettingDefinition(
            ToolWindowSettingsProvider.APP_SHOW_CONTEXT_FOR_COMMITS_KEY,
            ToolWindowSettingsProvider.DEFAULT_SHOW_CONTEXT_FOR_COMMITS
        )
        private val SHOW_LINE_STATS_IN_TREE = BooleanSettingDefinition(
            ToolWindowSettingsProvider.APP_SHOW_LINE_STATS_IN_TREE_KEY,
            ToolWindowSettingsProvider.DEFAULT_SHOW_LINE_STATS_IN_TREE
        )
        private val EXPAND_NEW_FILES_IN_COLLAPSED_DIRS = BooleanSettingDefinition(
            ToolWindowSettingsProvider.APP_EXPAND_NEW_FILES_IN_COLLAPSED_DIRS_KEY,
            ToolWindowSettingsProvider.DEFAULT_EXPAND_NEW_FILES_IN_COLLAPSED_DIRS
        )
        private val SHOW_UNTRACKED_FILES_AS_NEW = BooleanSettingDefinition(
            ToolWindowSettingsProvider.APP_SHOW_UNTRACKED_FILES_AS_NEW_KEY,
            ToolWindowSettingsProvider.DEFAULT_SHOW_UNTRACKED_FILES_AS_NEW
        )

        private val STRING_SETTINGS = listOf(
            SINGLE_CLICK_ACTION,
            DOUBLE_CLICK_ACTION,
            MIDDLE_CLICK_ACTION,
            DOUBLE_MIDDLE_CLICK_ACTION,
            RIGHT_CLICK_ACTION,
            DOUBLE_RIGHT_CLICK_ACTION
        )

        private val BOOLEAN_SETTINGS = listOf(
            SHOW_CONTEXT_MENU,
            INCLUDE_HEAD_IN_SCOPES,
            ENABLE_GUTTER_MARKERS,
            ENABLE_GUTTER_FOR_NEW_FILES,
            SHOW_TOOL_WINDOW_TITLE,
            SHOW_WIDGET_CONTEXT,
            SHOW_CONTEXT_SINGLE_REPO,
            SHOW_CONTEXT_MULTI_REPO,
            SHOW_CONTEXT_FOR_COMMITS,
            SHOW_LINE_STATS_IN_TREE,
            EXPAND_NEW_FILES_IN_COLLAPSED_DIRS,
            SHOW_UNTRACKED_FILES_AS_NEW
        )

        private val INT_SETTINGS = listOf(USER_DOUBLE_CLICK_DELAY)
    }

    data class SettingsState(
        var values: MutableMap<String, String> = mutableMapOf()
    )

    private var state = SettingsState()

    private val properties: PropertiesComponent
        get() = PropertiesComponent.getInstance()

    override fun getState(): SettingsState = state

    override fun loadState(state: SettingsState) {
        this.state = state
    }

    private fun storedValue(key: String): String? = state.values[key]?.takeUnless(String::isBlank)

    private fun getString(key: String, default: String): String {
        storedValue(key)?.let { return it }

        val legacyValue = properties.getValue(key)
        if (!legacyValue.isNullOrBlank()) {
            state.values[key] = legacyValue
            return legacyValue
        }

        return default
    }

    private fun setString(key: String, value: String) {
        state.values[key] = value
        // Keep legacy keys in sync for staged migration compatibility.
        properties.setValue(key, value)
    }

    private fun getString(definition: StringSettingDefinition): String =
        getString(definition.key, definition.defaultValue)

    private fun setString(definition: StringSettingDefinition, value: String) {
        setString(definition.key, value)
    }

    private fun setBoolean(definition: BooleanSettingDefinition, value: Boolean) {
        setBoolean(definition.key, value, definition.defaultValue)
    }

    private fun getBoolean(definition: BooleanSettingDefinition): Boolean =
        getBoolean(definition.key, definition.defaultValue)

    private fun getInt(definition: IntSettingDefinition): Int =
        getInt(definition.key, definition.defaultValue)

    private fun setInt(definition: IntSettingDefinition, value: Int) {
        setInt(definition.key, value, definition.defaultValue)
    }

    fun getSingleClickAction(): String = getString(SINGLE_CLICK_ACTION)

    fun setSingleClickAction(action: String) {
        setString(SINGLE_CLICK_ACTION, action)
    }

    fun getDoubleClickAction(): String = getString(DOUBLE_CLICK_ACTION)

    fun setDoubleClickAction(action: String) {
        setString(DOUBLE_CLICK_ACTION, action)
    }

    fun getMiddleClickAction(): String = getString(MIDDLE_CLICK_ACTION)

    fun setMiddleClickAction(action: String) {
        setString(MIDDLE_CLICK_ACTION, action)
    }

    fun getDoubleMiddleClickAction(): String = getString(DOUBLE_MIDDLE_CLICK_ACTION)

    fun setDoubleMiddleClickAction(action: String) {
        setString(DOUBLE_MIDDLE_CLICK_ACTION, action)
    }

    fun getRightClickAction(): String = getString(RIGHT_CLICK_ACTION)

    fun setRightClickAction(action: String) {
        setString(RIGHT_CLICK_ACTION, action)
    }

    fun getDoubleRightClickAction(): String = getString(DOUBLE_RIGHT_CLICK_ACTION)

    fun setDoubleRightClickAction(action: String) {
        setString(DOUBLE_RIGHT_CLICK_ACTION, action)
    }

    fun isContextMenuEnabled(): Boolean = getBoolean(SHOW_CONTEXT_MENU)

    fun setContextMenuEnabled(enabled: Boolean) {
        setBoolean(SHOW_CONTEXT_MENU, enabled)
    }

    fun getUserDoubleClickDelay(): Int = getInt(USER_DOUBLE_CLICK_DELAY)

    fun setUserDoubleClickDelay(delay: Int) {
        setInt(USER_DOUBLE_CLICK_DELAY, delay)
    }

    fun isIncludeHeadInScopes(): Boolean = getBoolean(INCLUDE_HEAD_IN_SCOPES)

    fun setIncludeHeadInScopes(enabled: Boolean) {
        setBoolean(INCLUDE_HEAD_IN_SCOPES, enabled)
    }

    fun isGutterMarkersEnabled(): Boolean = getBoolean(ENABLE_GUTTER_MARKERS)

    fun setGutterMarkersEnabled(enabled: Boolean) {
        setBoolean(ENABLE_GUTTER_MARKERS, enabled)
    }

    fun isGutterForNewFilesEnabled(): Boolean = getBoolean(ENABLE_GUTTER_FOR_NEW_FILES)

    fun setGutterForNewFilesEnabled(enabled: Boolean) {
        setBoolean(ENABLE_GUTTER_FOR_NEW_FILES, enabled)
    }

    fun isShowToolWindowTitle(): Boolean = getBoolean(SHOW_TOOL_WINDOW_TITLE)

    fun setShowToolWindowTitle(enabled: Boolean) {
        setBoolean(SHOW_TOOL_WINDOW_TITLE, enabled)
    }

    fun isShowWidgetContext(): Boolean = getBoolean(SHOW_WIDGET_CONTEXT)

    fun setShowWidgetContext(enabled: Boolean) {
        setBoolean(SHOW_WIDGET_CONTEXT, enabled)
    }

    fun isShowContextForSingleRepo(): Boolean = getBoolean(SHOW_CONTEXT_SINGLE_REPO)

    fun setShowContextForSingleRepo(enabled: Boolean) {
        setBoolean(SHOW_CONTEXT_SINGLE_REPO, enabled)
    }

    fun isShowContextForMultiRepo(): Boolean = getBoolean(SHOW_CONTEXT_MULTI_REPO)

    fun setShowContextForMultiRepo(enabled: Boolean) {
        setBoolean(SHOW_CONTEXT_MULTI_REPO, enabled)
    }

    fun isShowContextForCommits(): Boolean = getBoolean(SHOW_CONTEXT_FOR_COMMITS)

    fun setShowContextForCommits(enabled: Boolean) {
        setBoolean(SHOW_CONTEXT_FOR_COMMITS, enabled)
    }

    fun isShowLineStatsInTree(): Boolean = getBoolean(SHOW_LINE_STATS_IN_TREE)

    fun setShowLineStatsInTree(enabled: Boolean) {
        setBoolean(SHOW_LINE_STATS_IN_TREE, enabled)
    }

    fun isExpandNewFilesInCollapsedDirs(): Boolean = getBoolean(EXPAND_NEW_FILES_IN_COLLAPSED_DIRS)

    fun setExpandNewFilesInCollapsedDirs(enabled: Boolean) {
        setBoolean(EXPAND_NEW_FILES_IN_COLLAPSED_DIRS, enabled)
    }

    fun isShowUntrackedFilesAsNew(): Boolean = getBoolean(SHOW_UNTRACKED_FILES_AS_NEW)

    fun setShowUntrackedFilesAsNew(enabled: Boolean) {
        setBoolean(SHOW_UNTRACKED_FILES_AS_NEW, enabled)
    }

    fun getBoolean(key: String, default: Boolean): Boolean {
        storedValue(key)?.let { return it.toBooleanStrictOrNull() ?: default }

        val legacyValue = properties.getBoolean(key, default)
        state.values[key] = legacyValue.toString()
        return legacyValue
    }

    fun setBoolean(key: String, value: Boolean, default: Boolean) {
        state.values[key] = value.toString()
        // Keep legacy keys in sync for staged migration compatibility.
        properties.setValue(key, value, default)
    }

    fun getInt(key: String, default: Int): Int {
        storedValue(key)?.let { return it.toIntOrNull() ?: default }

        val legacyValue = properties.getInt(key, default)
        state.values[key] = legacyValue.toString()
        return legacyValue
    }

    fun setInt(key: String, value: Int, default: Int) {
        state.values[key] = value.toString()
        // Keep legacy keys in sync for staged migration compatibility.
        properties.setValue(key, value, default)
    }

    @Suppress("unused")
    fun resetToDefaults() {
        STRING_SETTINGS.forEach { definition -> setString(definition, definition.defaultValue) }
        BOOLEAN_SETTINGS.forEach { definition -> setBoolean(definition, definition.defaultValue) }
        INT_SETTINGS.forEach { definition -> setInt(definition, definition.defaultValue) }
    }
}

package com.github.uiopak.lstcrc.toolWindow

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "LstCrcSettingsService", storages = [Storage("lstCrcSettings.xml")])
@Service(Service.Level.APP)
class LstCrcSettingsService : PersistentStateComponent<LstCrcSettingsService.SettingsState> {

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

    private fun getString(key: String, default: String): String {
        val stateValue = state.values[key]
        if (!stateValue.isNullOrBlank()) return stateValue

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

    fun getSingleClickAction(): String = getString(
        ToolWindowSettingsProvider.APP_SINGLE_CLICK_ACTION_KEY,
        ToolWindowSettingsProvider.DEFAULT_SINGLE_CLICK_ACTION
    )

    fun setSingleClickAction(action: String) {
        setString(ToolWindowSettingsProvider.APP_SINGLE_CLICK_ACTION_KEY, action)
    }

    fun getDoubleClickAction(): String = getString(
        ToolWindowSettingsProvider.APP_DOUBLE_CLICK_ACTION_KEY,
        ToolWindowSettingsProvider.DEFAULT_DOUBLE_CLICK_ACTION
    )

    fun setDoubleClickAction(action: String) {
        setString(ToolWindowSettingsProvider.APP_DOUBLE_CLICK_ACTION_KEY, action)
    }

    fun getMiddleClickAction(): String = getString(
        ToolWindowSettingsProvider.APP_MIDDLE_CLICK_ACTION_KEY,
        ToolWindowSettingsProvider.DEFAULT_MIDDLE_CLICK_ACTION
    )

    fun setMiddleClickAction(action: String) {
        setString(ToolWindowSettingsProvider.APP_MIDDLE_CLICK_ACTION_KEY, action)
    }

    fun getDoubleMiddleClickAction(): String = getString(
        ToolWindowSettingsProvider.APP_DOUBLE_MIDDLE_CLICK_ACTION_KEY,
        ToolWindowSettingsProvider.DEFAULT_DOUBLE_MIDDLE_CLICK_ACTION
    )

    fun setDoubleMiddleClickAction(action: String) {
        setString(ToolWindowSettingsProvider.APP_DOUBLE_MIDDLE_CLICK_ACTION_KEY, action)
    }

    fun getRightClickAction(): String = getString(
        ToolWindowSettingsProvider.APP_RIGHT_CLICK_ACTION_KEY,
        ToolWindowSettingsProvider.DEFAULT_RIGHT_CLICK_ACTION
    )

    fun setRightClickAction(action: String) {
        setString(ToolWindowSettingsProvider.APP_RIGHT_CLICK_ACTION_KEY, action)
    }

    fun getDoubleRightClickAction(): String = getString(
        ToolWindowSettingsProvider.APP_DOUBLE_RIGHT_CLICK_ACTION_KEY,
        ToolWindowSettingsProvider.DEFAULT_DOUBLE_RIGHT_CLICK_ACTION
    )

    fun setDoubleRightClickAction(action: String) {
        setString(ToolWindowSettingsProvider.APP_DOUBLE_RIGHT_CLICK_ACTION_KEY, action)
    }

    fun getBoolean(key: String, default: Boolean): Boolean {
        val stateValue = state.values[key]
        if (!stateValue.isNullOrBlank()) return stateValue.toBooleanStrictOrNull() ?: default

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
        val stateValue = state.values[key]
        if (!stateValue.isNullOrBlank()) return stateValue.toIntOrNull() ?: default

        val legacyValue = properties.getInt(key, default)
        state.values[key] = legacyValue.toString()
        return legacyValue
    }

    fun setInt(key: String, value: Int, default: Int) {
        state.values[key] = value.toString()
        // Keep legacy keys in sync for staged migration compatibility.
        properties.setValue(key, value, default)
    }

    fun resetToDefaults() {
        setSingleClickAction(ToolWindowSettingsProvider.DEFAULT_SINGLE_CLICK_ACTION)
        setDoubleClickAction(ToolWindowSettingsProvider.DEFAULT_DOUBLE_CLICK_ACTION)
        setMiddleClickAction(ToolWindowSettingsProvider.DEFAULT_MIDDLE_CLICK_ACTION)
        setDoubleMiddleClickAction(ToolWindowSettingsProvider.DEFAULT_DOUBLE_MIDDLE_CLICK_ACTION)
        setRightClickAction(ToolWindowSettingsProvider.DEFAULT_RIGHT_CLICK_ACTION)
        setDoubleRightClickAction(ToolWindowSettingsProvider.DEFAULT_DOUBLE_RIGHT_CLICK_ACTION)

        setBoolean(
            ToolWindowSettingsProvider.APP_SHOW_CONTEXT_MENU_KEY,
            ToolWindowSettingsProvider.DEFAULT_SHOW_CONTEXT_MENU,
            ToolWindowSettingsProvider.DEFAULT_SHOW_CONTEXT_MENU
        )
        setInt(
            ToolWindowSettingsProvider.APP_USER_DOUBLE_CLICK_DELAY_KEY,
            ToolWindowSettingsProvider.DELAY_OPTION_SYSTEM_DEFAULT,
            ToolWindowSettingsProvider.DELAY_OPTION_SYSTEM_DEFAULT
        )
        setBoolean(
            ToolWindowSettingsProvider.APP_INCLUDE_HEAD_IN_SCOPES_KEY,
            ToolWindowSettingsProvider.DEFAULT_INCLUDE_HEAD_IN_SCOPES,
            ToolWindowSettingsProvider.DEFAULT_INCLUDE_HEAD_IN_SCOPES
        )
        setBoolean(
            ToolWindowSettingsProvider.APP_ENABLE_GUTTER_MARKERS_KEY,
            ToolWindowSettingsProvider.DEFAULT_ENABLE_GUTTER_MARKERS,
            ToolWindowSettingsProvider.DEFAULT_ENABLE_GUTTER_MARKERS
        )
        setBoolean(
            ToolWindowSettingsProvider.APP_ENABLE_GUTTER_FOR_NEW_FILES_KEY,
            ToolWindowSettingsProvider.DEFAULT_ENABLE_GUTTER_FOR_NEW_FILES,
            ToolWindowSettingsProvider.DEFAULT_ENABLE_GUTTER_FOR_NEW_FILES
        )
        setBoolean(
            ToolWindowSettingsProvider.APP_SHOW_TOOL_WINDOW_TITLE_KEY,
            ToolWindowSettingsProvider.DEFAULT_SHOW_TOOL_WINDOW_TITLE,
            ToolWindowSettingsProvider.DEFAULT_SHOW_TOOL_WINDOW_TITLE
        )
        setBoolean(
            ToolWindowSettingsProvider.APP_SHOW_WIDGET_CONTEXT_KEY,
            ToolWindowSettingsProvider.DEFAULT_SHOW_WIDGET_CONTEXT,
            ToolWindowSettingsProvider.DEFAULT_SHOW_WIDGET_CONTEXT
        )
        setBoolean(
            ToolWindowSettingsProvider.APP_SHOW_CONTEXT_SINGLE_REPO_KEY,
            ToolWindowSettingsProvider.DEFAULT_SHOW_CONTEXT_SINGLE_REPO,
            ToolWindowSettingsProvider.DEFAULT_SHOW_CONTEXT_SINGLE_REPO
        )
        setBoolean(
            ToolWindowSettingsProvider.APP_SHOW_CONTEXT_MULTI_REPO_KEY,
            ToolWindowSettingsProvider.DEFAULT_SHOW_CONTEXT_MULTI_REPO,
            ToolWindowSettingsProvider.DEFAULT_SHOW_CONTEXT_MULTI_REPO
        )
        setBoolean(
            ToolWindowSettingsProvider.APP_SHOW_CONTEXT_FOR_COMMITS_KEY,
            ToolWindowSettingsProvider.DEFAULT_SHOW_CONTEXT_FOR_COMMITS,
            ToolWindowSettingsProvider.DEFAULT_SHOW_CONTEXT_FOR_COMMITS
        )
        setBoolean(
            ToolWindowSettingsProvider.APP_SHOW_LINE_STATS_IN_TREE_KEY,
            ToolWindowSettingsProvider.DEFAULT_SHOW_LINE_STATS_IN_TREE,
            ToolWindowSettingsProvider.DEFAULT_SHOW_LINE_STATS_IN_TREE
        )
        setBoolean(
            ToolWindowSettingsProvider.APP_EXPAND_NEW_FILES_IN_COLLAPSED_DIRS_KEY,
            ToolWindowSettingsProvider.DEFAULT_EXPAND_NEW_FILES_IN_COLLAPSED_DIRS,
            ToolWindowSettingsProvider.DEFAULT_EXPAND_NEW_FILES_IN_COLLAPSED_DIRS
        )
        setBoolean(
            ToolWindowSettingsProvider.APP_SHOW_UNTRACKED_FILES_AS_NEW_KEY,
            ToolWindowSettingsProvider.DEFAULT_SHOW_UNTRACKED_FILES_AS_NEW,
            ToolWindowSettingsProvider.DEFAULT_SHOW_UNTRACKED_FILES_AS_NEW
        )
    }
}

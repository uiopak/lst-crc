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
}

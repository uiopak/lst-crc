package com.github.uiopak.lstcrc.toolWindow

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

    private fun storedValue(key: String): String? = state.values[key]?.takeUnless(String::isBlank)

    private fun getString(key: String, default: String): String = storedValue(key) ?: default
    private fun setString(key: String, value: String) { state.values[key] = value }

    private fun getString(def: StringSettingDefinition): String = getString(def.key, def.defaultValue)
    private fun setString(def: StringSettingDefinition, value: String) = setString(def.key, value)
    private fun getBoolean(def: BooleanSettingDefinition): Boolean = getBoolean(def.key, def.defaultValue)
    private fun setBoolean(def: BooleanSettingDefinition, value: Boolean) = setBoolean(def.key, value, def.defaultValue)
    private fun getInt(def: IntSettingDefinition): Int = getInt(def.key, def.defaultValue)
    private fun setInt(def: IntSettingDefinition, value: Int) = setInt(def.key, value, def.defaultValue)

    // --- Public typed accessors ---

    fun getSingleClickAction(): String = getString(LstCrcSettingDefinitions.SINGLE_CLICK_ACTION)
    fun setSingleClickAction(action: String) = setString(LstCrcSettingDefinitions.SINGLE_CLICK_ACTION, action)

    fun getDoubleClickAction(): String = getString(LstCrcSettingDefinitions.DOUBLE_CLICK_ACTION)
    fun setDoubleClickAction(action: String) = setString(LstCrcSettingDefinitions.DOUBLE_CLICK_ACTION, action)

    fun getMiddleClickAction(): String = getString(LstCrcSettingDefinitions.MIDDLE_CLICK_ACTION)
    fun setMiddleClickAction(action: String) = setString(LstCrcSettingDefinitions.MIDDLE_CLICK_ACTION, action)

    fun getDoubleMiddleClickAction(): String = getString(LstCrcSettingDefinitions.DOUBLE_MIDDLE_CLICK_ACTION)
    fun setDoubleMiddleClickAction(action: String) = setString(LstCrcSettingDefinitions.DOUBLE_MIDDLE_CLICK_ACTION, action)

    fun getRightClickAction(): String = getString(LstCrcSettingDefinitions.RIGHT_CLICK_ACTION)
    fun setRightClickAction(action: String) = setString(LstCrcSettingDefinitions.RIGHT_CLICK_ACTION, action)

    fun getDoubleRightClickAction(): String = getString(LstCrcSettingDefinitions.DOUBLE_RIGHT_CLICK_ACTION)
    fun setDoubleRightClickAction(action: String) = setString(LstCrcSettingDefinitions.DOUBLE_RIGHT_CLICK_ACTION, action)

    fun isContextMenuEnabled(): Boolean = getBoolean(LstCrcSettingDefinitions.SHOW_CONTEXT_MENU)
    fun setContextMenuEnabled(enabled: Boolean) = setBoolean(LstCrcSettingDefinitions.SHOW_CONTEXT_MENU, enabled)

    fun getUserDoubleClickDelay(): Int = getInt(LstCrcSettingDefinitions.USER_DOUBLE_CLICK_DELAY)
    fun setUserDoubleClickDelay(delay: Int) = setInt(LstCrcSettingDefinitions.USER_DOUBLE_CLICK_DELAY, delay)

    fun isIncludeHeadInScopes(): Boolean = getBoolean(LstCrcSettingDefinitions.INCLUDE_HEAD_IN_SCOPES)
    fun setIncludeHeadInScopes(enabled: Boolean) = setBoolean(LstCrcSettingDefinitions.INCLUDE_HEAD_IN_SCOPES, enabled)

    fun isGutterMarkersEnabled(): Boolean = getBoolean(LstCrcSettingDefinitions.ENABLE_GUTTER_MARKERS)
    fun setGutterMarkersEnabled(enabled: Boolean) = setBoolean(LstCrcSettingDefinitions.ENABLE_GUTTER_MARKERS, enabled)

    fun isGutterForNewFilesEnabled(): Boolean = getBoolean(LstCrcSettingDefinitions.ENABLE_GUTTER_FOR_NEW_FILES)
    fun setGutterForNewFilesEnabled(enabled: Boolean) = setBoolean(LstCrcSettingDefinitions.ENABLE_GUTTER_FOR_NEW_FILES, enabled)

    fun isShowToolWindowTitle(): Boolean = getBoolean(LstCrcSettingDefinitions.SHOW_TOOL_WINDOW_TITLE)
    fun setShowToolWindowTitle(enabled: Boolean) = setBoolean(LstCrcSettingDefinitions.SHOW_TOOL_WINDOW_TITLE, enabled)

    fun isShowWidgetContext(): Boolean = getBoolean(LstCrcSettingDefinitions.SHOW_WIDGET_CONTEXT)
    fun setShowWidgetContext(enabled: Boolean) = setBoolean(LstCrcSettingDefinitions.SHOW_WIDGET_CONTEXT, enabled)

    fun isShowContextForSingleRepo(): Boolean = getBoolean(LstCrcSettingDefinitions.SHOW_CONTEXT_SINGLE_REPO)
    fun setShowContextForSingleRepo(enabled: Boolean) = setBoolean(LstCrcSettingDefinitions.SHOW_CONTEXT_SINGLE_REPO, enabled)

    fun isShowContextForMultiRepo(): Boolean = getBoolean(LstCrcSettingDefinitions.SHOW_CONTEXT_MULTI_REPO)
    fun setShowContextForMultiRepo(enabled: Boolean) = setBoolean(LstCrcSettingDefinitions.SHOW_CONTEXT_MULTI_REPO, enabled)

    fun isShowContextForCommits(): Boolean = getBoolean(LstCrcSettingDefinitions.SHOW_CONTEXT_FOR_COMMITS)
    fun setShowContextForCommits(enabled: Boolean) = setBoolean(LstCrcSettingDefinitions.SHOW_CONTEXT_FOR_COMMITS, enabled)

    fun isShowLineStatsInTree(): Boolean = getBoolean(LstCrcSettingDefinitions.SHOW_LINE_STATS_IN_TREE)
    fun setShowLineStatsInTree(enabled: Boolean) = setBoolean(LstCrcSettingDefinitions.SHOW_LINE_STATS_IN_TREE, enabled)

    fun isExpandNewFilesInCollapsedDirs(): Boolean = getBoolean(LstCrcSettingDefinitions.EXPAND_NEW_FILES_IN_COLLAPSED_DIRS)
    fun setExpandNewFilesInCollapsedDirs(enabled: Boolean) = setBoolean(LstCrcSettingDefinitions.EXPAND_NEW_FILES_IN_COLLAPSED_DIRS, enabled)

    fun isShowUntrackedFilesAsNew(): Boolean = getBoolean(LstCrcSettingDefinitions.SHOW_UNTRACKED_FILES_AS_NEW)
    fun setShowUntrackedFilesAsNew(enabled: Boolean) = setBoolean(LstCrcSettingDefinitions.SHOW_UNTRACKED_FILES_AS_NEW, enabled)

    // --- Public raw-key accessors (used by tests via reflection) ---

    fun getBoolean(key: String, default: Boolean): Boolean =
        storedValue(key)?.toBooleanStrictOrNull() ?: default

    fun setBoolean(key: String, value: Boolean, default: Boolean) {
        state.values[key] = value.toString()
    }

    fun getInt(key: String, default: Int): Int =
        storedValue(key)?.toIntOrNull() ?: default

    fun setInt(key: String, value: Int, default: Int) {
        state.values[key] = value.toString()
    }

    @Suppress("unused")
    fun resetToDefaults() {
        LstCrcSettingDefinitions.stringSettings.forEach { setString(it, it.defaultValue) }
        LstCrcSettingDefinitions.booleanSettings.forEach { setBoolean(it, it.defaultValue) }
        LstCrcSettingDefinitions.intSettings.forEach { setInt(it, it.defaultValue) }
    }
}

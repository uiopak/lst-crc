package com.github.uiopak.lstcrc.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "com.github.uiopak.lstcrc.settings.TabColorSettingsState",
    storages = [Storage("lstcrcTabColorSettings.xml")]
)
data class TabColorSettingsState(
    var isTabColoringEnabled: Boolean = true,
    var colorTarget: String = "BACKGROUND", // Options: BACKGROUND, BORDER_TOP, BORDER_RIGHT, BORDER_BOTTOM, BORDER_LEFT
    var tabBackgroundColor: String? = null,
    var borderColor: String? = null,
    var borderSide: String? = "NONE",
    var useDefaultBackgroundColor: Boolean = true,
    var useDefaultBorderColor: Boolean = true,
    // Per-status custom background colors
    var newFileColor: String? = null,
    var modifiedFileColor: String? = null,
    var deletedFileColor: String? = null,
    var movedFileColor: String? = null
    // Removed: var comparisonBranch: String = "HEAD"
) : PersistentStateComponent<TabColorSettingsState> {

    override fun getState(): TabColorSettingsState {
        return this
    }

    override fun loadState(state: TabColorSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(project: Project): TabColorSettingsState {
            return project.getService(TabColorSettingsState::class.java)
        }
    }
}

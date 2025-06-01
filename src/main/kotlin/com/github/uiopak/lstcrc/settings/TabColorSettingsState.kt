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
    var comparisonBranch: String = "HEAD"    // Options: HEAD, current_branch, specific_branch_name
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

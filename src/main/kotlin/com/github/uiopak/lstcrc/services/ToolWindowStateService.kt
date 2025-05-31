package com.github.uiopak.lstcrc.services

import com.github.uiopak.lstcrc.state.TabInfo
import com.github.uiopak.lstcrc.state.ToolWindowState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "com.github.uiopak.lstcrc.services.ToolWindowStateService",
    storages = [Storage("gitTabsIdeaPluginState.xml")]
)
class ToolWindowStateService(private val project: Project) : PersistentStateComponent<ToolWindowState> {

    private var myState = ToolWindowState()

    override fun getState(): ToolWindowState {
        return myState
    }

    override fun loadState(state: ToolWindowState) {
        XmlSerializerUtil.copyBean(state, myState)
    }

    fun addTab(branchName: String) {
        val newTabs = myState.openTabs.toMutableList()
        // Avoid adding duplicates if a tab for the branch already exists
        if (newTabs.none { it.branchName == branchName }) {
            newTabs.add(TabInfo(branchName))
            myState.openTabs = newTabs
        }
    }

    fun removeTab(branchName: String) {
        val currentTabs = myState.openTabs.toMutableList()
        currentTabs.removeAll { it.branchName == branchName }
        myState.openTabs = currentTabs
        // Adjust selectedTabIndex if the removed tab was selected or before the selected one
        // This logic might need refinement based on how ContentManager handles indices
    }

    fun setSelectedTab(index: Int) {
        myState.selectedTabIndex = index
    }

    fun getSelectedTabBranchName(): String? {
        if (myState.selectedTabIndex >= 0 && myState.selectedTabIndex < myState.openTabs.size) {
            return myState.openTabs[myState.selectedTabIndex].branchName
        }
        return null
    }

    companion object {
        fun getInstance(project: Project): ToolWindowStateService {
            return project.getService(ToolWindowStateService::class.java)
        }
    }
}

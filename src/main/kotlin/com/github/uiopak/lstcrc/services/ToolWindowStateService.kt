package com.github.uiopak.lstcrc.services

import com.github.uiopak.lstcrc.state.TabInfo
import com.github.uiopak.lstcrc.state.ToolWindowState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.openapi.diagnostic.thisLogger // Added for logging

@State(
    name = "com.github.uiopak.lstcrc.services.ToolWindowStateService",
    storages = [Storage("gitTabsIdeaPluginState.xml")]
)
class ToolWindowStateService(private val project: Project) : PersistentStateComponent<ToolWindowState> {

    private var myState = ToolWindowState()
    private val logger = thisLogger() // Initialize logger

    override fun getState(): ToolWindowState {
        logger.info("ToolWindowStateService: getState() called. Current state: $myState")
        return myState
    }

    override fun loadState(state: ToolWindowState) {
        logger.info("ToolWindowStateService: loadState() called. Loading state: $state")
        XmlSerializerUtil.copyBean(state, myState)
    }

    override fun noStateLoaded() {
        logger.info("ToolWindowStateService: noStateLoaded() called. Initializing with default state.")
        myState = ToolWindowState() // Ensure it's a clean state
    }

    fun addTab(branchName: String) {
        logger.info("ToolWindowStateService: addTab($branchName) called.")
        val currentTabs = myState.openTabs.toMutableList()
        if (currentTabs.none { it.branchName == branchName }) {
            currentTabs.add(TabInfo(branchName))
            myState.openTabs = currentTabs // This modification should be detected
            logger.info("ToolWindowStateService: Tab $branchName added. New state: $myState")
        } else {
            logger.info("ToolWindowStateService: Tab $branchName already exists.")
        }
    }

    fun removeTab(branchName: String) {
        logger.info("ToolWindowStateService: removeTab($branchName) called.")
        val currentTabs = myState.openTabs.toMutableList()
        val removed = currentTabs.removeAll { it.branchName == branchName }
        if (removed) {
            myState.openTabs = currentTabs // This modification should be detected
            logger.info("ToolWindowStateService: Tab $branchName removed. New state: $myState")
            // Consider adjusting selectedTabIndex here if necessary, though selectionChanged should also handle it
        } else {
            logger.info("ToolWindowStateService: Tab $branchName not found for removal.")
        }
    }

    fun setSelectedTab(index: Int) {
        logger.info("ToolWindowStateService: setSelectedTab($index) called.")
        if (myState.selectedTabIndex != index) {
            myState.selectedTabIndex = index // This modification should be detected
            logger.info("ToolWindowStateService: Selected tab index set to $index. New state: $myState")
        } else {
            logger.info("ToolWindowStateService: Selected tab index $index is already set.")
        }
    }

    fun getSelectedTabBranchName(): String? {
        // This is a read-only operation, no logging needed unless for specific debugging
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

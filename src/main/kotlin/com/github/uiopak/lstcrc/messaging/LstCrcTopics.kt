package com.github.uiopak.lstcrc.messaging

import com.intellij.util.messages.Topic
import java.util.EventListener

/**
 * Topic for broadcasting when the active diff data has been updated.
 * This decouples listeners (like the Gutter Service) from UI events and ensures they
 * only react when the underlying data they depend on is ready.
 */
interface ActiveDiffDataChangedListener : EventListener {
    fun onDiffDataChanged()
}

val DIFF_DATA_CHANGED_TOPIC: Topic<ActiveDiffDataChangedListener> = Topic.create("LSTCRC Active Diff Data Changed", ActiveDiffDataChangedListener::class.java)

/**
 * Topic for broadcasting when the tool window's tab state has changed (tabs added/removed, selection changed, etc.).
 *
 * @see com.github.uiopak.lstcrc.services.ToolWindowStateService
 */
interface ToolWindowStateListener : EventListener {
    fun stateChanged(newState: com.github.uiopak.lstcrc.state.ToolWindowState)
}

val TOOL_WINDOW_STATE_TOPIC: Topic<ToolWindowStateListener> = Topic.create("LST-CRC ToolWindow State Changed", ToolWindowStateListener::class.java)
package com.github.uiopak.lstcrc.messaging

import com.intellij.util.messages.Topic
import java.util.EventListener

interface FileChangeListener : EventListener {
    fun onFilesChanged()
}

val FILE_CHANGES_TOPIC: Topic<FileChangeListener> = Topic.create("LSTCRC File Changes", FileChangeListener::class.java)


// --- NEW ---
// Topic for broadcasting when the active diff data has been updated.
// This decouples listeners (like the Gutter Service) from UI events and ensures they
// only react when the underlying data they depend on is ready.
interface ActiveDiffDataChangedListener : EventListener {
    fun onDiffDataChanged()
}

val DIFF_DATA_CHANGED_TOPIC: Topic<ActiveDiffDataChangedListener> = Topic.create("LSTCRC Active Diff Data Changed", ActiveDiffDataChangedListener::class.java)
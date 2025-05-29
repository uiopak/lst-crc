package com.github.uiopak.lstcrc.messaging

import com.intellij.util.messages.Topic
import java.util.EventListener

interface FileChangeListener : EventListener {
    fun onFilesChanged()
}

val FILE_CHANGES_TOPIC: Topic<FileChangeListener> = Topic.create("LSTCRC File Changes", FileChangeListener::class.java)

package com.ajsy.taskflow.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

class CursorViewModel {
    var id: String = UUID.randomUUID().toString()
    var position: Int by mutableStateOf(0)
    var taskTimerRunning: Boolean = false

    fun stop() {}
    fun cancelTimerAsync() {}
}

package com.ajsy.taskflow.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ajsy.taskflow.db.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class TaskViewModel(
    var name: String,
    var durationStr: String,
    var taskRepository: TaskRepository,
    var viewModelScope: CoroutineScope
) {
    var id: String = UUID.randomUUID().toString()
    var position: Int = 0
    var originalDuration: Long = stringMinutesToLongMillis(durationStr)
    var remainingDuration: Long by mutableStateOf(originalDuration)
    var taskTimerTickTime: Long = 0
    var taskTimerRunning: Boolean  by mutableStateOf(false)

    val updateMutex = Mutex()

    //TODO: I think this class is getting leaked and we just have a bunch of timers
    // emitting updates for old versions of each task
    private var timer: Job? = null

    //TODO: i think i know what the problem is now, we are concurrently reading and writing to this
    // class all the time. thats not safe, it doesnt really work. Its causing wacky behavior when
    // syncing to the db i think. this is one thing that stateflow will do for us. we can write to the
    // flow from coroutines concurrently, then you can have observers that can read the latest version and will
    // get updates, presumably, the room db can be setup to be updated whenever the state is updated

    fun start() {
        timer = viewModelScope.launch {
            updateMutex.withLock {
                taskTimerTickTime = System.currentTimeMillis()
                taskTimerRunning = true
                taskRepository.updateTask(this@TaskViewModel)
            }

            while(this.isActive) {
                checkpointTickTime(viewModelScope)
                delay(500L)
            }
        }
    }

    fun stop() {
        viewModelScope.launch {
            updateMutex.withLock {
                cancelTimer()

                taskTimerRunning = false
                taskRepository.updateTask(this@TaskViewModel)
            }
        }
    }

    fun cancelTimer() {
        timer?.cancel()
        timer = null
    }

    fun checkpointTickTime(viewModelScope: CoroutineScope) {
        viewModelScope.launch {
            updateMutex.withLock {
                remainingDuration -= (System.currentTimeMillis() - taskTimerTickTime)
                taskTimerTickTime = System.currentTimeMillis()
                taskRepository.updateTask(this@TaskViewModel)
            }
        }
    }

    private fun stringMinutesToLongMillis(durationStr :String): Long {
        return try {
            durationStr.toLong()*60*1000
        } catch (e: NumberFormatException) {
            0
        }
    }

    fun percentageComplete(): Float {
        var percentageComplete = 1f - (remainingDuration.toFloat() / originalDuration.toFloat())
        if(percentageComplete > 1) {
            percentageComplete = 1f
        }
        return percentageComplete
    }

    fun formatTimeRemaining(): String {
        val seconds = (this.remainingDuration / 1000).toInt()

        val sign = if (seconds < 0) "-" else ""
        val absSeconds = kotlin.math.abs(seconds)

        val minutes = absSeconds / 60
        val remainingSeconds = absSeconds % 60

        return "$sign$minutes:${String.format("%02d", remainingSeconds)}"
    }

    suspend fun setPosition(i: Int, viewModelScope: CoroutineScope) {
        viewModelScope.launch {
            updateMutex.withLock {
                position = i
                taskRepository.updateTask(this@TaskViewModel)
            }
        }
    }
}

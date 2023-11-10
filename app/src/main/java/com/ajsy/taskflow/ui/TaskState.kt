package com.ajsy.taskflow.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.room.PrimaryKey
import com.ajsy.taskflow.db.Task
import java.util.Timer
import java.util.TimerTask

class TaskState(var name: String, var durationStr: String) {
    var id: Long = System.currentTimeMillis()
    var position: Int = 0
    var originalDuration: Long = stringMinutesToLongMillis(durationStr)
    var remainingDuration: Long by mutableStateOf(originalDuration)
    var taskTimerTickTime: Long = 0
    var taskTimerRunning: Boolean  by mutableStateOf(false)

    private var timer: Timer? = null

    companion object {
        fun dtoToState(taskDto: Task): TaskState {
            var taskState = TaskState(taskDto.name, taskDto.durationStr)
            taskState.id = taskDto.id
            taskState.position = taskDto.position
            taskState.taskTimerRunning = taskDto.taskTimerRunning
            taskState.remainingDuration = taskDto.remainingDuration
            taskState.originalDuration = taskDto.originalDuration
            taskState.taskTimerTickTime = taskDto.taskTimerTickTime
            return taskState
        }

        fun stateToDto(taskState: TaskState): Task {
            return Task(
                id = taskState.id,
                name = taskState.name,
                position = taskState.position,
                durationStr = taskState.durationStr,
                taskTimerRunning = taskState.taskTimerRunning,
                remainingDuration = taskState.remainingDuration,
                originalDuration = taskState.originalDuration,
                taskTimerTickTime = taskState.taskTimerTickTime,
            )
        }

        fun dtoListToStateList(taskListDto: List<Task>): List<TaskState> {
            return taskListDto.map { dtoToState(it) }
        }
    }

    fun resetElapsedMillis() {
        this.remainingDuration = originalDuration
    }

    fun getTaskId(): Long {
        return id
    }

    fun setRemainingDurationForTesting(remainingDuration: Long): TaskState {
        this.remainingDuration = remainingDuration
        return this
    }


    fun start() {
        this.taskTimerTickTime = System.currentTimeMillis()
        this.taskTimerRunning = true

        var thisTask = this

        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                thisTask.checkpointTickTime()
            }
        }, 0L, 500L)
    }

    fun stop() {
        timer?.cancel()
        timer = null

        this.taskTimerRunning = false
    }

    fun checkpointTickTime() {
        this.remainingDuration = this.remainingDuration - (System.currentTimeMillis() - this.taskTimerTickTime)
        this.taskTimerTickTime = System.currentTimeMillis()
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
}

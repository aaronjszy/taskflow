package com.ajsy.taskflow.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ajsy.taskflow.db.Task
import com.ajsy.taskflow.db.TaskDao
import com.ajsy.taskflow.db.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask
import java.util.UUID

data class TaskState(
    var name: String,
    var durationStr: String,
    var taskRepository: TaskRepository
) {
    var id: String = UUID.randomUUID().toString()
    var position: Int = 0
    var originalDuration: Long = stringMinutesToLongMillis(durationStr)
    var remainingDuration: Long by mutableStateOf(originalDuration)
    var taskTimerTickTime: Long = 0
    var taskTimerRunning: Boolean  by mutableStateOf(false)

    private var timer: Timer? = null

    companion object {
        private fun dtoToState(taskDto: Task, taskRepository: TaskRepository, viewModelScope: CoroutineScope): TaskState {
            val taskState = TaskState(taskDto.name, taskDto.durationStr, taskRepository)
            taskState.id = taskDto.id
            taskState.position = taskDto.position
            taskState.taskTimerRunning = taskDto.taskTimerRunning
            taskState.remainingDuration = taskDto.remainingDuration
            taskState.originalDuration = taskDto.originalDuration
            taskState.taskTimerTickTime = taskDto.taskTimerTickTime
            if(taskState.taskTimerRunning) {
                taskState.start(viewModelScope)
            } else {
                taskState.stop()
            }
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

        fun dtoListToStateList(
            taskListDto: List<Task>,
            taskRepository: TaskRepository,
            viewModelScope: CoroutineScope
        ): List<TaskState> {
            return taskListDto.map { dtoToState(it, taskRepository, viewModelScope) }
        }
    }

    fun getTaskId(): String {
        return id
    }

    fun start(viewModelScope: CoroutineScope) {
        this.taskTimerTickTime = System.currentTimeMillis()
        this.taskTimerRunning = true

        // TODO: Race condition, constantly calling update means that we can overwrite pausing the timer
        //       need to get all changes to taskState combined with calls to db upate within viewmodel scope
        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                checkpointTickTime(viewModelScope)
            }
        }, 0L, 500L)
    }

    fun stop() {
        timer?.cancel()
        timer = null

        // TODO: another place where our changes could get lost
        this.taskTimerRunning = false
    }

    fun checkpointTickTime(viewModelScope: CoroutineScope) {
        val thisTask = this
        viewModelScope.launch {
            remainingDuration = remainingDuration - (System.currentTimeMillis() - taskTimerTickTime)
            taskTimerTickTime = System.currentTimeMillis()
            taskRepository.updateTask(stateToDto(thisTask))
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
        var thisTask = this
        viewModelScope.launch {
            position = i
            taskRepository.updateTask(stateToDto(thisTask))
        }
    }
}

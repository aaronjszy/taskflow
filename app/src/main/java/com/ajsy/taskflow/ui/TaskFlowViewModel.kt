package com.ajsy.taskflow.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ajsy.taskflow.db.TaskDao
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask

class TaskFlowViewModel(
    private var dao: TaskDao
) : ViewModel() {

    var newItemName by mutableStateOf("")
        private set
    var newItemTime by mutableStateOf("")
        private set

    var tasks = mutableStateListOf<TaskState>()
        private set

    private var timer: Timer? = null

    var isTimerRunning by mutableStateOf(false)
        private set

    fun initList() {
        viewModelScope.launch {
            var stateList = TaskState.dtoListToStateList(dao.getTasks())
            for(state in stateList) {
                tasks.add(state)
            }
        }
    }

    fun toggleTimer(task : TaskState) {
        if (isTimerRunning) {
            stopTimer(task)
        } else {
            startTimerForTask(task)
        }
        isTimerRunning = !isTimerRunning
    }

    private fun startTimerForTask(task : TaskState) {
        stopTimer(tasks[0])

        moveTaskToTop(task)
        task.checkpointStartTime()

        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                task.checkpointTickTime()
            }
        }, 0L, 1000L)
    }

    private fun stopTimer(task: TaskState) {
        timer?.cancel()
        timer = null
        task.stop()
    }

    fun updateNewItemName(name: String) {
        newItemName = name
    }

    fun updateNewItemTime(time: String) {
        newItemTime = time
    }

    fun addItem() {
        var taskState = TaskState(newItemName, newItemTime)
        taskState.position = tasks.size
        tasks.add(taskState)

        newItemTime = ""
        newItemName = ""

        viewModelScope.launch {
            dao.upsertTask(TaskState.stateToDto(taskState))
        }
    }

    fun moveTaskUp(task: TaskState) {
        var currentIndex = tasks.indexOf(task)
        if(currentIndex == 0) {
            return
        }
        var newIndex = currentIndex-1;

        tasks[currentIndex] = tasks[newIndex]
        tasks[newIndex] = task

        tasks[currentIndex].position = currentIndex
        tasks[newIndex].position = newIndex
    }

    fun moveTaskDown(task: TaskState) {
        var currentIndex = tasks.indexOf(task)
        if(currentIndex == tasks.size - 1) {
            return
        }
        var newIndex = currentIndex+1;

        tasks[currentIndex] = tasks[newIndex]
        tasks[newIndex] = task

        syncTaskPositions()
    }

    private fun moveTaskToTop(task: TaskState) {
        val currentIndex = tasks.indexOf(task)
        if(currentIndex == 0) {
            return
        }
        val newIndex = 0;

        tasks[currentIndex] = tasks[newIndex]
        tasks[newIndex] = task

        syncTaskPositions()
    }

    fun deleteTask(task: TaskState) {
        var isRunningTask = false

        if(task == tasks[0] && isTimerRunning) {
            stopTimer(task)
            isRunningTask = true
        }

        tasks.remove(task)

        // Start the timer for the next task
        if(isRunningTask && tasks.size > 0) {
            startTimerForTask(tasks[0])
        }

        viewModelScope.launch {
            dao.deleteTask(TaskState.stateToDto(task))
            syncTaskPositions()
        }
    }

    private fun syncTaskPositions() {
        viewModelScope.launch {
            for ((index, task) in tasks.withIndex()) {
                task.position = index
                dao.upsertTask(TaskState.stateToDto(task))
            }
        }
    }
}

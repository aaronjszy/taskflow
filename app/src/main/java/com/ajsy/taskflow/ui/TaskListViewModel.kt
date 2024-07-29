package com.ajsy.taskflow.ui

import androidx.compose.runtime.mutableStateListOf
import com.ajsy.taskflow.db.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

data class TaskListViewModel(
    var taskRepository: TaskRepository,
    var viewModelScope: CoroutineScope
) {
    private val _tasks = mutableStateListOf<TaskViewModel>()
    val tasks: List<TaskViewModel> get() = _tasks

    private val taskListMutex = Mutex()

    fun stopAllTimers() {
        _tasks.filter { it.taskTimerRunning }.forEach { it.stop() }
    }

    fun appendTask(taskViewModel: TaskViewModel) {
        addTask(taskViewModel, false)
    }

    fun pushTask(taskViewModel: TaskViewModel) {
        addTask(taskViewModel, true)
    }

    private fun addTask(taskViewModel: TaskViewModel, shouldPush: Boolean) {
        viewModelScope.launch {
            val taskViewModel = taskViewModel.apply {
                position = _tasks.size
            }

            taskListMutex.withLock {
                val newTasksList = _tasks.toMutableList().apply {
                    add(taskViewModel)
                }

                stopAllTimers()
                _tasks.clear()
                _tasks.addAll(newTasksList)
            }

            if(shouldPush) {
                if (_tasks.firstOrNull()?.taskTimerRunning == true) {
                    moveTaskToTop(taskViewModel, noSync = true, noStopTimer = true)
                    moveTaskDown(taskViewModel)
                } else {
                    moveTaskToTop(taskViewModel, noSync = false, noStopTimer = false)
                }
            }

            taskRepository.upsertTask(taskViewModel)
            syncTasks()
        }
    }

    fun moveTaskUp(task: TaskViewModel, noStopTimer: Boolean) {
        viewModelScope.launch {
            taskListMutex.withLock {
                val currentIndex = _tasks.indexOf(task)
                if (currentIndex <= 0) return@launch
                val newIndex = currentIndex - 1
                if (newIndex == 0 && !noStopTimer) _tasks[newIndex].stop()
                _tasks[currentIndex] = _tasks[newIndex]
                _tasks[newIndex] = task
                _tasks[currentIndex].setPosition(currentIndex, viewModelScope)
                _tasks[newIndex].setPosition(newIndex, viewModelScope)
            }
            syncTasks()
        }
    }

    fun moveTaskDown(task: TaskViewModel) {
        viewModelScope.launch {
            taskListMutex.withLock {
                val currentIndex = _tasks.indexOf(task)
                if (currentIndex == _tasks.size - 1) return@launch
                val newIndex = currentIndex + 1
                _tasks[currentIndex] = _tasks[newIndex]
                _tasks[currentIndex].setPosition(currentIndex, viewModelScope)
                _tasks[newIndex] = task
                _tasks[newIndex].setPosition(newIndex, viewModelScope)
            }
            task.stop()
            syncTasks()
        }
    }

    private fun moveTaskToTop(task: TaskViewModel, noSync: Boolean, noStopTimer: Boolean) {
        val currentIndex = _tasks.indexOf(task)
        if (currentIndex == 0) return
        for (i in currentIndex downTo 1) {
            moveTaskUp(_tasks[i], noStopTimer)
        }
        if (!noSync) syncTasksAsync()
    }

    data class TimeRange(val startTime: LocalDateTime, val endTime: LocalDateTime)

    fun calcTimeRange(targetTask: TaskViewModel): TimeRange {
        var durationSum: Long = 0
        val currentTime = Instant.now()
        var startTime = LocalDateTime.ofInstant(currentTime, ZoneId.systemDefault())
        var endTime = LocalDateTime.ofInstant(currentTime, ZoneId.systemDefault())
        for (task in _tasks) {
            if (task.id == targetTask.id) {
                startTime = LocalDateTime.ofInstant(currentTime.plusMillis(durationSum), ZoneId.systemDefault())
            }
            if (task.remainingDuration > 0) {
                durationSum += task.remainingDuration
            }
            if (task.id == targetTask.id) {
                endTime = LocalDateTime.ofInstant(currentTime.plusMillis(durationSum), ZoneId.systemDefault())
                break
            }
        }
        return TimeRange(startTime, endTime)
    }

    fun deleteTask(task: TaskViewModel) {
        viewModelScope.launch {
            var wasTimerRunning = false
            taskListMutex.withLock {
                if (task == _tasks.firstOrNull()) {
                    wasTimerRunning = task.taskTimerRunning
                    task.stop()
                }
                _tasks.remove(task)
            }
            if (wasTimerRunning && _tasks.isNotEmpty()) {
                startTimerForTask(_tasks[0])
            }

            taskRepository.deleteTask(task)
            syncTasks()
        }
    }

    private fun startTimerForTask(task: TaskViewModel) {
        stopAllTimers()
        moveTaskToTop(task, noSync = false, noStopTimer = false)
        task.start()
        syncTasksAsync()
    }

    private suspend fun syncTasks() {
        taskRepository.updateTasks(_tasks.toList())
    }

    private fun syncTasksAsync() {
        viewModelScope.launch {
            syncTasks()
        }
    }
}

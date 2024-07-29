package com.ajsy.taskflow.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ajsy.taskflow.db.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class TaskFlowViewModel(
    private var taskRepository: TaskRepository
) : ViewModel() {

    private val _newItemName = MutableStateFlow("")
    val newItemName: StateFlow<String> = _newItemName

    private val _newItemTime = MutableStateFlow("")
    val newItemTime: StateFlow<String> = _newItemTime

    private val _addItemMode = MutableStateFlow("append")
    val addItemMode: StateFlow<String> = _addItemMode

    private val _tasks = mutableStateListOf<TaskViewModel>()
    val tasks: List<TaskViewModel> get() = _tasks

    private val taskListMutex = Mutex()

    fun initList() {
        viewModelScope.launch {
            taskListMutex.withLock {
                val taskList = taskRepository.getTasks(viewModelScope)

                stopAllTimers()
                _tasks.clear()
                _tasks.addAll(taskList)
            }
        }
    }

    fun switchAddItemMode() {
        _addItemMode.value = if (_addItemMode.value == "append") "push" else "append"
    }

    fun toggleTimer(task: TaskViewModel) {
        if (task.taskTimerRunning) {
            task.stop()
        } else {
            startTimerForTask(task)
        }
    }

    private fun startTimerForTask(task: TaskViewModel) {
        stopAllTimers()
        moveTaskToTop(task, noSync = false, noStopTimer = false)
        task.start()
        syncTasksAsync()
    }

    fun updateNewItemName(name: String) {
        _newItemName.value = name
    }

    fun updateNewItemTime(time: String) {
        _newItemTime.value = time
    }

    fun stopAllTimers() {
        _tasks.filter { it.taskTimerRunning }.forEach { it.stop() }
    }

    fun addItem() {
        if (_newItemTime.value.isEmpty() && lineIncludesTime(_newItemName.value)) {
            val multipleTasks = parseMultilineString(_newItemName.value)
            multipleTasks.forEach { task ->
                val taskName = task[0]
                val taskTime = task[1]
                addSingleItem(taskName, taskTime)
            }
        } else {
            addSingleItem(_newItemName.value, _newItemTime.value)
        }
        _newItemTime.value = ""
        _newItemName.value = ""
    }

    fun addSingleItem(taskName: String, taskTime: String) {
        viewModelScope.launch {
            val assignedTaskTime = taskTime.ifEmpty { "1" }
            val taskViewModel = TaskViewModel(taskName, assignedTaskTime, taskRepository, viewModelScope).apply {
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

            if (_addItemMode.value == "push") {
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

    fun lineIncludesTime(line: String): Boolean {
        val regex = """\d+$""".toRegex()
        return regex.containsMatchIn(line)
    }

    fun parseMultilineString(input: String): Array<Array<String>> {
        val lines = input.lines()
        val resultList = lines.map { line ->
            val regex = """(.*)\s+(\d+)$""".toRegex()
            val matchResult = regex.find(line)
            if (matchResult != null) {
                val (name, time) = matchResult.destructured
                arrayOf(name.trim(), time.trim())
            } else if (line.trim().isNotEmpty()) {
                arrayOf(line.trim(), "1")
            } else {
                null
            }
        }.filterNotNull()
        return resultList.toTypedArray()
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

    private suspend fun syncTasks() {
        taskRepository.updateTasks(_tasks.toList())
    }

    private fun syncTasksAsync() {
        viewModelScope.launch {
            syncTasks()
        }
    }

    override fun onCleared() {
        super.onCleared()

        _tasks.forEach { it.cancelTimer() }
        _tasks.clear()
    }
}

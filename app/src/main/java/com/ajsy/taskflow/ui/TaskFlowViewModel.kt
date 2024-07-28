package com.ajsy.taskflow.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ajsy.taskflow.db.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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

    private val _tasks = mutableStateListOf<TaskState>()
    val tasks: List<TaskState> get() = _tasks

    fun initList() {
        viewModelScope.launch {
            val stateList = TaskState.dtoListToStateList(taskRepository.getTasks(), taskRepository, viewModelScope)
            _tasks.clear()
            _tasks.addAll(stateList)
        }
    }

    fun toggleTimer(task: TaskState) {
        if (task.taskTimerRunning) {
            stopTimer(task)
        } else {
            startTimerForTask(task)
        }
    }

    private fun startTimerForTask(task: TaskState) {
        stopAllTimers()
        moveTaskToTop(task, noSync = false, noStopTimer = false)
        task.start(viewModelScope)
        viewModelScope.launch {
            taskRepository.updateTask(TaskState.stateToDto(task))
            syncTasks()
        }
    }

    fun updateTask(updatedTask: TaskState) {
        val index = _tasks.indexOfFirst { it.id == updatedTask.id }
        if (index != -1) {
            _tasks[index] = updatedTask
        }
    }

    fun stopAllTimers() {
        _tasks.filter { it.taskTimerRunning }.forEach { stopTimer(it) }
    }

    private fun stopTimer(task: TaskState) {
        task.stop()
        viewModelScope.launch {
            taskRepository.updateTask(TaskState.stateToDto(task))
        }
    }

    fun updateNewItemName(name: String) {
        _newItemName.value = name
    }

    fun updateNewItemTime(time: String) {
        _newItemTime.value = time
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

    private fun addTask(taskState: TaskState) {
        _tasks.add(taskState)
    }

    fun addSingleItem(taskName: String, taskTime: String) {
        val assignedTaskTime = taskTime.ifEmpty { "1" }
        val taskState = TaskState(taskName, assignedTaskTime, taskRepository).apply {
            position = _tasks.size
        }
        val newTasksList = _tasks.toMutableList().apply {
            add(taskState)
        }
        _tasks.clear()
        _tasks.addAll(newTasksList)
        if (_addItemMode.value == "push") {
            if (_tasks.firstOrNull()?.taskTimerRunning == true) {
                moveTaskToTop(taskState, noSync = true, noStopTimer = true)
                moveTaskDown(taskState)
            } else {
                moveTaskToTop(taskState, noSync = false, noStopTimer = false)
            }
        }
        viewModelScope.launch {
            taskRepository.upsertTask(TaskState.stateToDto(taskState))
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

    fun switchAddItemMode() {
        _addItemMode.value = if (_addItemMode.value == "append") "push" else "append"
    }

    fun moveTaskUp(task: TaskState, noStopTimer: Boolean) {
        val currentIndex = _tasks.indexOf(task)
        if (currentIndex <= 0) return
        val newIndex = currentIndex - 1
        if (newIndex == 0 && !noStopTimer) stopTimer(_tasks[newIndex])
        _tasks[currentIndex] = _tasks[newIndex]
        _tasks[newIndex] = task
        _tasks[currentIndex].position = currentIndex
        _tasks[newIndex].position = newIndex
        syncTasksAsync()
    }

    fun moveTaskDown(task: TaskState) {
        val currentIndex = _tasks.indexOf(task)
        if (currentIndex == _tasks.size - 1) return
        val newIndex = currentIndex + 1
        _tasks[currentIndex] = _tasks[newIndex]
        _tasks[newIndex] = task
        stopTimer(task)
        syncTasksAsync()
    }

    private fun moveTaskToTop(task: TaskState, noSync: Boolean, noStopTimer: Boolean) {
        val currentIndex = _tasks.indexOf(task)
        if (currentIndex == 0) return
        for (i in currentIndex downTo 1) {
            moveTaskUp(_tasks[i], noStopTimer)
        }
        if (!noSync) syncTasksAsync()
    }

    data class TimeRange(val startTime: LocalDateTime, val endTime: LocalDateTime)

    fun calcTimeRange(targetTask: TaskState): TimeRange {
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

    fun deleteTask(task: TaskState) {
        var wasTimerRunning = false
        if (task == _tasks.firstOrNull()) {
            wasTimerRunning = task.taskTimerRunning
            stopTimer(task)
        }
        _tasks.remove(task)
        if (wasTimerRunning && _tasks.isNotEmpty()) {
            startTimerForTask(_tasks[0])
        }
        viewModelScope.launch {
            taskRepository.deleteTask(TaskState.stateToDto(task))
            syncTasks()
        }
    }

    private suspend fun syncTasks() {
        for (i in _tasks.indices) {
            val task = _tasks[i]
            task.position = i
            taskRepository.updateTask(TaskState.stateToDto(task))
        }
    }

    private fun syncTasksAsync() {
        viewModelScope.launch {
            syncTasks()
        }
    }
}

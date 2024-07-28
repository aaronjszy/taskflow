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
import java.time.ZonedDateTime

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
            // Fetch the tasks from the repository and convert them to TaskState
            val stateList = TaskState.dtoListToStateList(taskRepository.getTasks(), taskRepository, viewModelScope)

            // Clear the current list of tasks
            _tasks.clear()

            // Add each task to the mutable state list
            _tasks.addAll(stateList)
        }
    }

    fun toggleTimer(task : TaskState) {
        if (task.taskTimerRunning) {
            stopTimer(task)
        } else {
            startTimerForTask(task)
        }
    }

    private fun startTimerForTask(task : TaskState) {
        // Stop the timer for the task in the first row
        // in case its already running since were going to
        stopAllTimers()

        moveTaskToTop(task, false, false)

        task.start(viewModelScope)

        viewModelScope.launch {
            taskRepository.updateTask(TaskState.stateToDto(task))
            syncTasks()
        }
    }

    fun updateTask(updatedTask: TaskState) {
        // Find the index of the task to update
        val index = _tasks.indexOfFirst { it.id == updatedTask.id }
        if (index != -1) {
            // Replace the task at the found index
            _tasks[index] = updatedTask
        }
    }


    fun stopAllTimers() {
        for (task in _tasks) {
            if (task.taskTimerRunning) {
                stopTimer(task) // Stop the timer for tasks that are running
            }
        }
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
        if(_newItemTime.value=="" && lineIncludesTime(_newItemName.value)) {
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
        _tasks.add(taskState) // Directly add the new task to the mutable state list
    }

    fun addSingleItem(taskName: String, taskTime: String) {
        var assignedTaskTime = taskTime.ifEmpty { "1" } // Use Kotlin's ifEmpty to assign default value

        val taskState = TaskState(taskName, assignedTaskTime, taskRepository).apply {
            position = _tasks.size // Set the position of the new task
        }

        // Create a new list that includes the new task
        val newTasksList = _tasks.toMutableList().apply {
            add(taskState) // Add the new task to the list
        }

        // Update the tasks with the new list
        _tasks.clear() // Clear the current list
        _tasks.addAll(newTasksList) // Add all tasks from the new list

        // Manage task positioning based on timer state
        if (_addItemMode.value == "push") {
            if (_tasks.firstOrNull()?.taskTimerRunning == true) {
                moveTaskToTop(taskState, true, true)
                moveTaskDown(taskState)
            } else {
                moveTaskToTop(taskState, false, false)
            }
        }

        // Asynchronously upsert the new task to the repository
        viewModelScope.launch {
            taskRepository.upsertTask(TaskState.stateToDto(taskState))
            syncTasks()
        }
    }

    fun lineIncludesTime(line: String): Boolean {
        // Regular expression to match a line that ends with a number
        val regex = """\d+$""".toRegex()

        // Check if the line matches the regex (i.e., ends with a number)
        return regex.containsMatchIn(line)
    }

    fun parseMultilineString(input: String): Array<Array<String>> {
        // Split the input string into lines
        val lines = input.lines()

        // Process each line to extract task name and time
        val resultList = lines.map { line ->
            // Use a regular expression to match the name and time
            val regex = """(.*)\s+(\d+)$""".toRegex()
            val matchResult = regex.find(line)

            if (matchResult != null) {
                val (name, time) = matchResult.destructured
                arrayOf(name.trim(), time.trim())
            } else if (line.trim() != "") {
                arrayOf(line.trim(), "1")
            } else {
                null
            }
        }.filterNotNull()

        // Convert the list to an array of arrays
        return resultList.toTypedArray()
    }

    fun switchAddItemMode() {
        if(_addItemMode.value == "append") {
            _addItemMode.value = "push"
        } else if(_addItemMode.value == "push") {
            _addItemMode.value = "append"
        }
    }
    fun moveTaskUp(task: TaskState, noStopTimer: Boolean) {
        val currentIndex = _tasks.indexOf(task)

        if (currentIndex <= 0) {
            return // No need to move if the task is at the top or not found
        }

        val newIndex = currentIndex - 1

        // If we're moving the task to the top row,
        // the task currently there might have a timer running; we need to stop it
        if (newIndex == 0 && !noStopTimer) {
            stopTimer(_tasks[newIndex])
        }

        // Swap the tasks in the mutable list
        _tasks[currentIndex] = _tasks[newIndex]
        _tasks[newIndex] = task

        // Update the positions of the tasks
        _tasks[currentIndex].position = currentIndex
        _tasks[newIndex].position = newIndex

        // Optionally, sync tasks asynchronously
        syncTasksAsync()
    }

    fun moveTaskDown(task: TaskState) {
        val currentIndex = _tasks.indexOf(task)

        if (currentIndex == _tasks.size - 1) {
            return // No need to move if the task is at the bottom
        }

        val newIndex = currentIndex + 1

        // Swap the tasks in the mutable list
        _tasks[currentIndex] = _tasks[newIndex]
        _tasks[newIndex] = task

        // If a timer for this task was running, it should now be stopped
        stopTimer(task)

        // Optionally, sync tasks asynchronously
        syncTasksAsync()
    }

    private fun moveTaskToTop(task: TaskState, noSync: Boolean, noStopTimer: Boolean) {
        val currentIndex = _tasks.indexOf(task)

        if (currentIndex == 0) {
            return // No need to move if the task is already at the top
        }

        // Move the task to the top by moving it up one position at a time
        for (i in currentIndex downTo 1) {
            moveTaskUp(_tasks[i], noStopTimer)
        }

        if (!noSync) {
            syncTasksAsync() // Sync tasks asynchronously if needed
        }
    }

    data class TimeRange(val startTime: LocalDateTime, val endTime: LocalDateTime)

    fun calcTimeRange(targetTask: TaskState): TimeRange {
        var durationSum: Long = 0
        var startTime: LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(System.currentTimeMillis()), ZonedDateTime.now().offset)
        var endTime: LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(System.currentTimeMillis()), ZonedDateTime.now().offset)

        // Get the current list of tasks directly
        for (task in _tasks) {
            if (task.id == targetTask.id) {
                startTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(System.currentTimeMillis() + durationSum), ZonedDateTime.now().offset)
            }

            if (task.remainingDuration > 0) {
                durationSum += task.remainingDuration
            }

            if (task.id == targetTask.id) {
                endTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(System.currentTimeMillis() + durationSum), ZonedDateTime.now().offset)
                break
            }
        }

        return TimeRange(startTime, endTime)
    }

    fun deleteTask(task: TaskState) {
        // Stop timer if the task is at the top
        var wasTimerRunning = false

        if (task == _tasks.firstOrNull()) {
            wasTimerRunning = task.taskTimerRunning
            stopTimer(task)
        }

        _tasks.remove(task)

        // Start the timer for the next task
        if (wasTimerRunning && _tasks.isNotEmpty()) {
            startTimerForTask(_tasks[0])
        }

        // Runs asynchronously
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

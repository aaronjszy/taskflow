package com.ajsy.taskflow.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ajsy.taskflow.db.TaskDao
import kotlinx.coroutines.launch

class TaskFlowViewModel(
    private var dao: TaskDao
) : ViewModel() {

    var newItemName by mutableStateOf("")
        private set
    var newItemTime by mutableStateOf("")
        private set

    var tasks = mutableStateListOf<TaskState>()
        private set

//    private var timer: Timer? = null

    fun initList() {
        viewModelScope.launch {
            val stateList = TaskState.dtoListToStateList(dao.getTasks())
            for(state in stateList) {
                tasks.add(state)
            }
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
        stopTimer(tasks[0])

        moveTaskToTop(task)

        task.start()

//        timer = Timer()
//        timer?.scheduleAtFixedRate(object : TimerTask() {
//            override fun run() {
//                task.checkpointTickTime()
//            }
//        }, 0L, 1000L)
    }

    private fun stopTimer(task: TaskState) {
//        timer?.cancel()
//        timer = null
        task.stop()
    }

    fun updateNewItemName(name: String) {
        newItemName = name
    }

    fun updateNewItemTime(time: String) {
        newItemTime = time
    }

    fun addItem() {
        val taskState = TaskState(newItemName, newItemTime)
        taskState.position = tasks.size
        tasks.add(taskState)

        newItemTime = ""
        newItemName = ""

        viewModelScope.launch {
            dao.upsertTask(TaskState.stateToDto(taskState))
        }
    }

    fun moveTaskUp(task: TaskState) {
        val currentIndex = tasks.indexOf(task)
        if(currentIndex == 0) {
            return
        }
        val newIndex = currentIndex-1

        // If were moving the task to the top row,
        // the task currently there might have a
        // timer running, we need to stop it
        if(newIndex == 0) {
            stopTimer(tasks[newIndex])
        }

        tasks[currentIndex] = tasks[newIndex]
        tasks[newIndex] = task

        tasks[currentIndex].position = currentIndex
        tasks[newIndex].position = newIndex
    }

    fun moveTaskDown(task: TaskState) {
        val currentIndex = tasks.indexOf(task)
        if(currentIndex == tasks.size - 1) {
            return
        }
        val newIndex = currentIndex+1

        tasks[currentIndex] = tasks[newIndex]
        tasks[newIndex] = task

        // If a timer for this task was running, it
        // should now be stopped since its likely
        // no longer at the top of the list
        stopTimer(task)

        syncTaskPositions()
    }


    private fun moveTaskToTop(task: TaskState) {
        val currentIndex = tasks.indexOf(task)
        if(currentIndex == 0) {
            return
        }
        val newIndex = 0

        tasks[currentIndex] = tasks[newIndex]
        tasks[newIndex] = task

        syncTaskPositions()
    }

    fun deleteTask(task: TaskState) {
        // Stop timer if the task is at the top
        var wasTimerRunning = false
        if(task == tasks[0]) {
            wasTimerRunning = task.taskTimerRunning
            stopTimer(task)
        }

        tasks.remove(task)

        // Start the timer for the next task
        if(wasTimerRunning && tasks.size > 0) {
            startTimerForTask(tasks[0])
        }

        viewModelScope.launch {
            dao.deleteTask(TaskState.stateToDto(task))
            syncTaskPositions()
        }
    }

    private fun syncTaskPositions() {
        viewModelScope.launch {
            for(i in 0..tasks.size){
                if(i < tasks.size){
                    val task = tasks[i]
                    task.position = i
                    dao.upsertTask(TaskState.stateToDto(task))
                }
            }
        }
    }
}

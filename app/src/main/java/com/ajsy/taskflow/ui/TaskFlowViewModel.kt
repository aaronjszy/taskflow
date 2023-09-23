package com.ajsy.taskflow.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.util.Timer
import java.util.TimerTask

class TaskFlowViewModel : ViewModel() {
    var newItemName by mutableStateOf("")
        private set
    var newItemTime by mutableStateOf("")
        private set

    var tasks = mutableStateListOf<Task>()
        private set

    private var timer: Timer? = null

    var isTimerRunning by mutableStateOf(false)
        private set

    fun toggleTimer(task :Task) {
        if (isTimerRunning) {
            stopTimer(task)
        } else {
            startTimerForTask(task)
        }
        isTimerRunning = !isTimerRunning
    }

    private fun startTimerForTask(task :Task) {
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

    private fun stopTimer(task: Task) {
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
        tasks.add(Task(newItemName, newItemTime))
        newItemTime = ""
        newItemName = ""
    }

    fun moveTaskUp(task: Task) {
        var currentIndex = tasks.indexOf(task)
        if(currentIndex == 0) {
            return
        }
        var newIndex = currentIndex-1;

        tasks[currentIndex] = tasks[newIndex]
        tasks[newIndex] = task
    }

    fun moveTaskDown(task: Task) {
        println(task.name)
        var currentIndex = tasks.indexOf(task)
        if(currentIndex == tasks.size - 1) {
            return
        }
        var newIndex = currentIndex+1;

        tasks[currentIndex] = tasks[newIndex]
        tasks[newIndex] = task
    }

    private fun moveTaskToTop(task: Task) {
        var currentIndex = tasks.indexOf(task)
        if(currentIndex == 0) {
            return
        }
        var newIndex = 0;

        tasks[currentIndex] = tasks[newIndex]
        tasks[newIndex] = task
    }

    fun deleteTask(task: Task) {
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
    }
}

class Task(var name: String, durationStr: String) {
    private var originalDuration: Long = stringMinutesToLongMillis(durationStr)
    private var remainingDuration: Long by mutableStateOf(originalDuration)
    private var taskTimerTickTime: Long = 0
    var taskTimerRunning: Boolean  by mutableStateOf(false)

    fun resetElapsedMillis() {
        this.remainingDuration = originalDuration
    }

    fun setRemainingDurationForTesting(remainingDuration: Long): Task {
        this.remainingDuration = remainingDuration
        return this
    }


    fun checkpointStartTime() {
        this.taskTimerTickTime = System.currentTimeMillis()
        this.taskTimerRunning = true
    }

    fun stop() {
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

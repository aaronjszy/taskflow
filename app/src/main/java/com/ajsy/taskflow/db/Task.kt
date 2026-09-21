package com.ajsy.taskflow.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Task(
    @PrimaryKey(autoGenerate = false)
    var id: String = "",
    var position: Int = 0,
    var name: String = "",
    var durationStr: String = "",
    var originalDuration: Long = 0,
    var remainingDurationAtResume: Long = 0,
    var lastResumedTime: Long = 0,
    var taskTimerRunning: Boolean = false,
    var fixedStartTime: Long? = null
) {
    override fun toString(): String {
        return "Task(id=$id, position=$position, running=$taskTimerRunning, name=$name)"
    }
}

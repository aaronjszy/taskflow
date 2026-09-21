package com.ajsy.taskflow.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert

@Dao
interface TaskDao {
    @Upsert
    suspend fun upsertTask(task: Task)

    @Update
    suspend fun updateTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)

    @Query("SELECT * FROM Task ORDER BY position")
    suspend fun getTasks(): List<Task>

    @Query("DELETE FROM Task")
    suspend fun deleteAllTasks()

    @Query("UPDATE Task SET position = :newPosition WHERE id = :taskId")
    suspend fun updateTaskPosition(taskId: String, newPosition: Int)

    @Query("UPDATE Task SET remainingDurationAtResume = :newRemainingDurationAtResume, lastResumedTime = :newLastResumedTime WHERE id = :taskId")
    suspend fun updateTaskTimerProgress(taskId: String, newRemainingDurationAtResume: Long, newLastResumedTime: Long)

    @Query("UPDATE Task SET taskTimerRunning = :newTaskTimerRunning WHERE id = :taskId")
    suspend fun updateTaskTimerRunningState(taskId: String, newTaskTimerRunning: Boolean)

    @Query("UPDATE Task SET fixedStartTime = :fixedStartTime WHERE id = :taskId")
    suspend fun updateTaskFixedStartTime(taskId: String, fixedStartTime: Long?)

    @Transaction
    suspend fun updateTaskPositions(tasks: List<Task>) {
        tasks.forEachIndexed { index, task ->
            updateTaskPosition(task.id, index)
        }
    }

    @Transaction
    suspend fun replaceAllTasks(tasks: List<Task>) {
        deleteAllTasks()
        tasks.forEach { upsertTask(it) }
    }
}

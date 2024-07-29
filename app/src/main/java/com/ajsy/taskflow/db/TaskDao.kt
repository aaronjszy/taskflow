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

    @Query("SELECT * from task ORDER BY position")
    suspend fun getTasks(): List<Task>

    @Query("UPDATE task SET position = :newPosition WHERE id = :taskId")
    suspend fun updateTaskPosition(taskId: String, newPosition: Int)

    @Transaction
    suspend fun updateTaskPositions(tasks: List<Task>) {
        tasks.forEachIndexed { index, task ->
            updateTaskPosition(task.id, index)
        }
    }

    @Transaction
    suspend fun updateTasks(tasks: List<Task>) {
        tasks.forEach {
            updateTask(it)
        }
    }
}

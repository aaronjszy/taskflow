package com.ajsy.taskflow.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface SavedListDao {
    @Insert
    suspend fun insertList(list: SavedTaskList): Long

    @Insert
    suspend fun insertTask(task: SavedTask)

    @Insert
    suspend fun insertTasks(tasks: List<SavedTask>)

    @Query("DELETE FROM SavedTaskList WHERE id = :listId")
    suspend fun deleteList(listId: Int)

    @Query("DELETE FROM SavedTask WHERE listId = :listId")
    suspend fun deleteTasksForList(listId: Int)

    @Query("SELECT * FROM SavedTaskList ORDER BY createdAt")
    suspend fun getAllLists(): List<SavedTaskList>

    @Query("SELECT * FROM SavedTask WHERE listId = :listId ORDER BY position")
    suspend fun getTasksForList(listId: Int): List<SavedTask>

    @Query("UPDATE SavedTaskList SET name = :name WHERE id = :listId")
    suspend fun updateListName(listId: Int, name: String)

    @Transaction
    suspend fun saveNewList(name: String, tasks: List<Task>): Long {
        val listId = insertList(SavedTaskList(name = name))
        tasks.forEachIndexed { index, task ->
            insertTask(
                SavedTask(
                    listId = listId.toInt(),
                    name = task.name,
                    durationStr = task.durationStr,
                    position = index,
                    fixedStartTime = task.fixedStartTime
                )
            )
        }
        return listId
    }
}

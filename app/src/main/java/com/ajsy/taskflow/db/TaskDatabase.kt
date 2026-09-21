package com.ajsy.taskflow.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [Task::class, SavedTaskList::class, SavedTask::class],
    version = 9
)
abstract class TaskDatabase : RoomDatabase() {
    abstract val dao: TaskDao
    abstract val savedListDao: SavedListDao
}

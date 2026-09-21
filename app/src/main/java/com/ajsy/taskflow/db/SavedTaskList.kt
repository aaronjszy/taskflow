package com.ajsy.taskflow.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class SavedTaskList(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

package com.ajsy.taskflow.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class SavedTask(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val listId: Int,
    val name: String,
    val durationStr: String,
    val position: Int,
    val fixedStartTime: Long? = null
)

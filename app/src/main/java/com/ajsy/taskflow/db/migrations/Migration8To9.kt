package com.ajsy.taskflow.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE Task RENAME COLUMN remainingDuration TO remainingDurationAtResume")
        database.execSQL("ALTER TABLE Task RENAME COLUMN taskTimerTickTime TO lastResumedTime")
    }
}

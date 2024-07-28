package com.ajsy.taskflow

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class NotificationApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val notificationChannel = NotificationChannel(
            "test_channel",
            "Test Channel",
            NotificationManager.IMPORTANCE_HIGH
        )

        notificationChannel.description = "A notification channel to test notifications"

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(notificationChannel)


    }
}
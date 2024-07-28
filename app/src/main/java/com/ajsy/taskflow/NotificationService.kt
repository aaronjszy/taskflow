package com.ajsy.taskflow

import android.app.NotificationManager
import android.content.Context
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import kotlin.random.Random

class NotificationService(
    private val context: Context
) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    fun showBasicNotification(content: String) {
        val notification = NotificationCompat.Builder(context, "test_channel")
            .setContentTitle("My Test Notification")
            .setContentText(content)
            .setSmallIcon(R.drawable.baseline_av_timer_24)
            .setPriority(NotificationManager.IMPORTANCE_HIGH)
            .setSilent(true)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(
            1234,
            notification
        )
    }
//
//    fun showExpandableNotification() {
//        val image = context.bitmapFromResource(R.drawable.baseline_av_timer_24)
//        val notification = NotificationCompat.Builder(context, "test_channel")
//            .setContentTitle("My Test Notification")
//            .setContentText("My content")
//            .setSmallIcon(R.drawable.baseline_av_timer_24)
//            .setPriority(NotificationManager.IMPORTANCE_HIGH)
//            .setLargeIcon(image)
//            .setStyle()
//            .setAutoCancel(true)
//            .build()
//        notificationManager.notify(
//            Random.nextInt(),
//            notification
//        )
//    }

    private fun Context.bitmapFromResource(
        @DrawableRes resId: Int
    ) = BitmapFactory.decodeResource(
        resources,
        resId
    )
}
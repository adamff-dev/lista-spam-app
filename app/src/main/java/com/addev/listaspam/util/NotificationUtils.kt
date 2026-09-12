package com.addev.listaspam.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.addev.listaspam.MainActivity
import com.addev.listaspam.R

private const val NOTIFICATION_CHANNEL_ID = "NOTIFICATION_CHANNEL"
private const val NOTIFICATION_ID = 1

fun sendBlockedCallNotification(context: Context, number: String, reason: String) {
    sendNotification(
        context,
        context.getString(R.string.notification_title_spam_blocked, number),
        context.getString(R.string.block_reason) + " " + reason
    )
}

fun sendNotification(context: Context, title: String, message: String) {
    sendNotification(context, title, message, 0);
}

fun sendNotification(context: Context, title: String, message: String, durationMs: Long) {
    createNotificationChannel(context)

    if (ActivityCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED || !shouldShowNotification(context)
    ) {
        return
    }

    val contentIntent = PendingIntent.getActivity(
        context,
        NOTIFICATION_ID,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher).setContentTitle(title).setContentText(message)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(contentIntent)
        .setAutoCancel(true)

    if (durationMs != 0L) {
        builder.setTimeoutAfter(durationMs)
    }

    val notification = builder.build()

    NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
}

fun createNotificationChannel(context: Context) {
    val name = context.getString(R.string.notification_channel_name)
    val descriptionText = context.getString(R.string.notification_channel_name)
    val importance = NotificationManager.IMPORTANCE_HIGH
    val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
        description = descriptionText
    }

    val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.createNotificationChannel(channel)
}
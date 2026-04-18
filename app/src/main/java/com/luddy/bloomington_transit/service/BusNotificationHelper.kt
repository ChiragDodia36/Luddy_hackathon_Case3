package com.luddy.bloomington_transit.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.luddy.bloomington_transit.BloomingtonTransitApp.Companion.CHANNEL_BUS_TRACKING
import com.luddy.bloomington_transit.BloomingtonTransitApp.Companion.CHANNEL_FOREGROUND
import com.luddy.bloomington_transit.MainActivity
import com.luddy.bloomington_transit.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BusNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun buildForegroundNotification(): Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_FOREGROUND)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle("BT Transit")
            .setContentText("Tracking your buses in background")
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    fun showBusArrivalNotification(
        notificationId: Int,
        routeShortName: String,
        stopName: String,
        minutesAway: Long
    ) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val title = "Bus $routeShortName arriving in ${minutesAway}m"
        val text = "At $stopName"

        val notification = NotificationCompat.Builder(context, CHANNEL_BUS_TRACKING)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    fun cancelNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }

    fun cancelAll() {
        notificationManager.cancelAll()
    }
}

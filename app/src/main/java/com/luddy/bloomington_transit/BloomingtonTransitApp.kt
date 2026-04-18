package com.luddy.bloomington_transit

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BloomingtonTransitApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val trackingChannel = NotificationChannel(
                CHANNEL_BUS_TRACKING,
                "Bus Tracking",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for tracked bus arrivals"
            }

            val foregroundChannel = NotificationChannel(
                CHANNEL_FOREGROUND,
                "Background Tracking Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent notification while tracking buses"
            }

            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannels(listOf(trackingChannel, foregroundChannel))
        }
    }

    companion object {
        const val CHANNEL_BUS_TRACKING = "bus_tracking"
        const val CHANNEL_FOREGROUND = "foreground_service"
    }
}

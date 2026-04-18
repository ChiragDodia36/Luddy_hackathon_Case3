package com.luddy.bloomington_transit.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.luddy.bloomington_transit.domain.repository.TransitRepository
import com.google.android.gms.location.*
import kotlinx.coroutines.tasks.await
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class BusTrackingService : Service() {

    @Inject lateinit var repository: TransitRepository
    @Inject lateinit var notificationHelper: BusNotificationHelper

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Track which notifications have already been fired to avoid spam
    private val firedNotifications = mutableSetOf<String>()

    companion object {
        private const val TAG = "BusTrackingService"
        const val FOREGROUND_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        startForeground(FOREGROUND_ID, notificationHelper.buildForegroundNotification())
        startTracking()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startTracking() {
        serviceScope.launch {
            while (isActive) {
                try {
                    val trackedIds = repository.getTrackedBusIds().first()
                    if (trackedIds.isEmpty()) {
                        delay(10_000L)
                        continue
                    }

                    val userLocation = getUserLocation()
                    val buses = repository.getLiveBuses()
                    val thresholdMinutes = repository.getNotificationThresholdMinutes().first()

                    trackedIds.forEach { vehicleId ->
                        val bus = buses.find { it.vehicleId == vehicleId } ?: return@forEach
                        val routeId = bus.routeId

                        // Get stops for this route sorted by proximity to user
                        if (userLocation != null) {
                            val nearestStops = repository.getNearestStops(
                                userLocation.first, userLocation.second, radiusMeters = 1000.0
                            )
                            val nearestStop = nearestStops.firstOrNull() ?: return@forEach

                            val arrivals = repository.getArrivalsForStop(nearestStop.id)
                                .filter { it.vehicleId == vehicleId || it.routeId == routeId }
                                .minByOrNull { it.displayArrivalMs }

                            arrivals?.let { arrival ->
                                val minutes = arrival.minutesUntil()
                                val notifKey = "$vehicleId-${nearestStop.id}-$minutes"

                                if (minutes <= thresholdMinutes && notifKey !in firedNotifications) {
                                    notificationHelper.showBusArrivalNotification(
                                        notificationId = vehicleId.hashCode(),
                                        routeShortName = arrival.routeShortName,
                                        stopName = nearestStop.name,
                                        minutesAway = minutes
                                    )
                                    firedNotifications.add(notifKey)
                                    Log.d(TAG, "Fired notification: Bus ${arrival.routeShortName} in ${minutes}m at ${nearestStop.name}")
                                }

                                // Clear fired cache when bus has passed
                                if (minutes == 0L) {
                                    firedNotifications.removeAll { it.startsWith(vehicleId) }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Tracking error", e)
                }
                delay(10_000L)
            }
        }
    }

    private suspend fun getUserLocation(): Pair<Double, Double>? {
        return try {
            val location = fusedLocationClient.lastLocation.await()
            location?.let { Pair(it.latitude, it.longitude) }
        } catch (_: SecurityException) {
            null
        }
    }
}

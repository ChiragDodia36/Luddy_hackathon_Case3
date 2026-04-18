package com.luddy.bloomington_transit.domain.model

data class Arrival(
    val routeId: String,
    val routeShortName: String,
    val routeColor: String,
    val headsign: String,
    val stopId: String,
    val stopName: String,
    val predictedArrivalMs: Long,   // epoch millis; -1 if only scheduled
    val scheduledArrivalMs: Long,   // epoch millis from static GTFS
    val vehicleId: String = "",
    val tripId: String = ""
) {
    val isRealtime: Boolean get() = predictedArrivalMs > 0

    val displayArrivalMs: Long get() = if (isRealtime) predictedArrivalMs else scheduledArrivalMs

    fun minutesUntil(): Long {
        val diff = displayArrivalMs - System.currentTimeMillis()
        return maxOf(0L, diff / 60_000L)
    }
}

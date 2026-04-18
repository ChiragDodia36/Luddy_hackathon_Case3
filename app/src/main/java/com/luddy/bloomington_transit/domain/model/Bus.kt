package com.luddy.bloomington_transit.domain.model

data class Bus(
    val vehicleId: String,
    val tripId: String,
    val routeId: String,
    val lat: Double,
    val lon: Double,
    val bearing: Float,
    val speed: Float,
    val timestamp: Long,
    val currentStopSequence: Int = 0,
    val label: String = ""
)

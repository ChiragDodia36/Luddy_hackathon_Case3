package com.luddy.bloomington_transit.data.local.entity

import androidx.room.Entity

@Entity(tableName = "stop_times", primaryKeys = ["tripId", "stopId", "stopSequence"])
data class StopTimeEntity(
    val tripId: String,
    val stopId: String,
    val arrivalTime: String,   // "HH:MM:SS" from GTFS
    val departureTime: String,
    val stopSequence: Int
)

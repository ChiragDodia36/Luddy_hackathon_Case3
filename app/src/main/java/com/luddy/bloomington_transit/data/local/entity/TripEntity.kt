package com.luddy.bloomington_transit.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey val tripId: String,
    val routeId: String,
    val shapeId: String,
    val headsign: String,
    val serviceId: String
)

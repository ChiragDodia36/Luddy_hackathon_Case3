package com.luddy.bloomington_transit.data.local.entity

import androidx.room.Entity

// Join table: which stops belong to which route
@Entity(tableName = "route_stops", primaryKeys = ["routeId", "stopId"])
data class RouteStopEntity(
    val routeId: String,
    val stopId: String
)

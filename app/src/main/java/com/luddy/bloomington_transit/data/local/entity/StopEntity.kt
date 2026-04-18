package com.luddy.bloomington_transit.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.luddy.bloomington_transit.domain.model.Stop

@Entity(tableName = "stops")
data class StopEntity(
    @PrimaryKey val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val code: String
) {
    fun toDomain() = Stop(id, name, lat, lon, code)
}

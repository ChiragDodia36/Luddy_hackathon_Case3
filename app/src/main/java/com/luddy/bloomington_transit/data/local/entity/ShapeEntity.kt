package com.luddy.bloomington_transit.data.local.entity

import androidx.room.Entity
import com.luddy.bloomington_transit.domain.model.ShapePoint

@Entity(tableName = "shapes", primaryKeys = ["shapeId", "sequence"])
data class ShapeEntity(
    val shapeId: String,
    val lat: Double,
    val lon: Double,
    val sequence: Int
) {
    fun toDomain() = ShapePoint(shapeId, lat, lon, sequence)
}

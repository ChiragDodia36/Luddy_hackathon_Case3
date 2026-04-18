package com.luddy.bloomington_transit.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.luddy.bloomington_transit.domain.model.Route

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey val id: String,
    val shortName: String,
    val longName: String,
    val color: String,
    val textColor: String
) {
    fun toDomain() = Route(id, shortName, longName, color, textColor)
}

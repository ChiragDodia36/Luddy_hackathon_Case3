package com.luddy.bloomington_transit.domain.model

data class ShapePoint(
    val shapeId: String,
    val lat: Double,
    val lon: Double,
    val sequence: Int
)

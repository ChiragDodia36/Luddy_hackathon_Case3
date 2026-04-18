package com.luddy.bloomington_transit.domain.model

data class Stop(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val code: String = ""
)

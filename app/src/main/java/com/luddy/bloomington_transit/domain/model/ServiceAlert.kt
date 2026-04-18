package com.luddy.bloomington_transit.domain.model

data class ServiceAlert(
    val id: String,
    val headerText: String,
    val descriptionText: String,
    val affectedRouteIds: List<String>
)

package com.luddy.bloomington_transit.domain.model

data class Route(
    val id: String,
    val shortName: String,
    val longName: String,
    val color: String,       // hex without '#', e.g. "FF0000"
    val textColor: String
)

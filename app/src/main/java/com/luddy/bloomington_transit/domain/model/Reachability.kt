package com.luddy.bloomington_transit.domain.model

import com.google.android.gms.maps.model.LatLng

data class Reachability(
    val walkSeconds: Int,
    val nextBusSeconds: Long,       // ETA of the soonest bus in seconds from now
    val routeShortName: String,
    val canMakeIt: Boolean,
    val spareSeconds: Long,         // positive = spare time, negative = how late you'd be
    val walkPolyline: List<LatLng>  // decoded walking route for map overlay
)

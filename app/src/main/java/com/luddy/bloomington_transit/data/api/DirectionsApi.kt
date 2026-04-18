package com.luddy.bloomington_transit.data.api

import com.google.android.gms.maps.model.LatLng
import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query


data class DirectionsResponse(
    val status: String,
    val routes: List<DirectionsRoute> = emptyList()
)

data class DirectionsRoute(
    @SerializedName("overview_polyline") val overviewPolyline: DirectionsPolyline,
    val legs: List<DirectionsLeg>
)

data class DirectionsPolyline(val points: String)

data class DirectionsLeg(
    val duration: DirectionsDuration,
    val distance: DirectionsDistance
)

data class DirectionsDuration(val value: Int, val text: String)
data class DirectionsDistance(val value: Int, val text: String)


interface DirectionsApi {
    @GET("maps/api/directions/json")
    suspend fun getWalkingDirections(
        @Query("origin") origin: String,          // "lat,lon"
        @Query("destination") destination: String, // "lat,lon"
        @Query("mode") mode: String = "walking",
        @Query("key") key: String
    ): DirectionsResponse
}


fun decodePolyline(encoded: String): List<LatLng> {
    val result = mutableListOf<LatLng>()
    var index = 0
    val len = encoded.length
    var lat = 0
    var lng = 0

    while (index < len) {
        var b: Int
        var shift = 0
        var result2 = 0
        do {
            b = encoded[index++].code - 63
            result2 = result2 or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        val dLat = if (result2 and 1 != 0) (result2 shr 1).inv() else result2 shr 1
        lat += dLat

        shift = 0
        result2 = 0
        do {
            b = encoded[index++].code - 63
            result2 = result2 or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        val dLng = if (result2 and 1 != 0) (result2 shr 1).inv() else result2 shr 1
        lng += dLng

        result.add(LatLng(lat / 1e5, lng / 1e5))
    }
    return result
}

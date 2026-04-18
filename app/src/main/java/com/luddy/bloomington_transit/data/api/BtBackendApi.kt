package com.luddy.bloomington_transit.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path


data class BusDto(
    @SerializedName("vehicle_id") val vehicleId: String,
    @SerializedName("trip_id") val tripId: String,
    @SerializedName("route_id") val routeId: String,
    val lat: Double,
    val lon: Double,
    val bearing: Float,
    val speed: Float,
    val label: String,
    val timestamp: Long,
    @SerializedName("current_stop_sequence") val currentStopSequence: Int,
)

data class ArrivalDto(
    @SerializedName("trip_id") val tripId: String,
    @SerializedName("route_id") val routeId: String,
    @SerializedName("route_short_name") val routeShortName: String,
    val headsign: String,
    @SerializedName("eta_seconds") val etaSeconds: Long,
    @SerializedName("delay_seconds") val delaySeconds: Long,
    @SerializedName("is_realtime") val isRealtime: Boolean,
    @SerializedName("scheduled_unix") val scheduledUnix: Long,
)

data class AlertDto(
    val id: String,
    val header: String,
    val description: String,
    @SerializedName("route_ids") val routeIds: List<String>,
)


interface BtBackendApi {
    @GET("buses")
    suspend fun getBuses(): List<BusDto>

    @GET("arrivals/{stopId}")
    suspend fun getArrivals(@Path("stopId") stopId: String): List<ArrivalDto>

    @GET("alerts")
    suspend fun getAlerts(): List<AlertDto>
}

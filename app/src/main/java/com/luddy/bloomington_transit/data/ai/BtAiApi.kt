package com.luddy.bloomington_transit.data.ai

import com.luddy.bloomington_transit.data.ai.dto.AlertDto
import com.luddy.bloomington_transit.data.ai.dto.BunchingResponseDto
import com.luddy.bloomington_transit.data.ai.dto.HealthResponseDto
import com.luddy.bloomington_transit.data.ai.dto.NlqResponseDto
import com.luddy.bloomington_transit.data.ai.dto.PredictionsResponseDto
import com.luddy.bloomington_transit.data.ai.dto.RouteDto
import com.luddy.bloomington_transit.data.ai.dto.StatsResponseDto
import com.luddy.bloomington_transit.data.ai.dto.StopDto
import com.luddy.bloomington_transit.data.ai.dto.TripEtaTrajectoryResponseDto
import com.luddy.bloomington_transit.data.ai.dto.VehicleDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** Retrofit interface for the bt-ml FastAPI inference service. */
interface BtAiApi {

    @GET("healthz")
    suspend fun healthz(): HealthResponseDto

    @GET("routes")
    suspend fun routes(): List<RouteDto>

    @GET("stops")
    suspend fun stops(
        @Query("route_id") routeId: String? = null,
        @Query("q") query: String? = null,
    ): List<StopDto>

    @GET("vehicles")
    suspend fun vehicles(): List<VehicleDto>

    @GET("alerts")
    suspend fun alerts(): List<AlertDto>

    @GET("predictions")
    suspend fun predictions(
        @Query("stop_id") stopId: String,
        @Query("horizon_minutes") horizonMinutes: Int = 30,
    ): PredictionsResponseDto

    @GET("predictions/trip/{trip_id}")
    suspend fun tripEta(@Path("trip_id") tripId: String): TripEtaTrajectoryResponseDto

    @GET("detections/bunching")
    suspend fun bunching(): BunchingResponseDto

    @GET("stats")
    suspend fun stats(): StatsResponseDto

    @GET("nlq")
    suspend fun nlq(@Query("q") query: String): NlqResponseDto
}

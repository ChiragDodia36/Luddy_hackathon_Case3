package com.luddy.bloomington_transit.data.ai

import android.util.Log
import com.luddy.bloomington_transit.data.ai.dto.BunchingResponseDto
import com.luddy.bloomington_transit.data.ai.dto.HealthResponseDto
import com.luddy.bloomington_transit.data.ai.dto.NlqResponseDto
import com.luddy.bloomington_transit.data.ai.dto.PredictionsResponseDto
import com.luddy.bloomington_transit.data.ai.dto.StatsResponseDto
import com.luddy.bloomington_transit.data.ai.dto.StopDto
import com.luddy.bloomington_transit.data.ai.dto.TripEtaTrajectoryResponseDto
import com.luddy.bloomington_transit.data.ai.dto.TripPlanResponseDto
import com.luddy.bloomington_transit.data.ai.dto.VehicleDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result envelope — keeps call sites honest about the failure path without
 * adding a kotlin.Result shim. Inherits FROM NOTHING: lives only in the AI
 * integration and does not touch Chirag's domain layer.
 */
sealed class AiResult<out T> {
    data class Ok<T>(val value: T) : AiResult<T>()
    data class Err(val message: String, val cause: Throwable? = null) : AiResult<Nothing>()
}

inline fun <T, R> AiResult<T>.map(transform: (T) -> R): AiResult<R> = when (this) {
    is AiResult.Ok -> AiResult.Ok(transform(value))
    is AiResult.Err -> this
}

@Singleton
class BtAiRepository @Inject constructor(private val api: BtAiApi) {

    companion object { private const val TAG = "BtAi" }

    private suspend fun <T> call(block: suspend () -> T): AiResult<T> = try {
        AiResult.Ok(block())
    } catch (e: Exception) {
        Log.e(TAG, "AI call failed: ${e.message}", e)
        AiResult.Err(e.message ?: "unknown error", e)
    }

    suspend fun healthz(): AiResult<HealthResponseDto> = call { api.healthz() }

    suspend fun searchStops(query: String): AiResult<List<StopDto>> =
        call { api.stops(query = query.trim().takeIf { it.isNotEmpty() }) }

    suspend fun vehicles(): AiResult<List<VehicleDto>> = call { api.vehicles() }

    suspend fun predictionsFor(stopId: String, horizonMinutes: Int = 30): AiResult<PredictionsResponseDto> =
        call { api.predictions(stopId, horizonMinutes) }

    suspend fun tripEta(tripId: String): AiResult<TripEtaTrajectoryResponseDto> =
        call { api.tripEta(tripId) }

    suspend fun bunching(): AiResult<BunchingResponseDto> = call { api.bunching() }

    suspend fun stats(): AiResult<StatsResponseDto> = call { api.stats() }

    suspend fun nlq(query: String): AiResult<NlqResponseDto> = call { api.nlq(query) }

    /** Google Directions transit routes enriched with A1+A2 boarding ETAs. */
    suspend fun plan(
        originLat: Double, originLng: Double,
        destLat: Double, destLng: Double,
        departureTimeEpochSec: Long? = null,
    ): AiResult<TripPlanResponseDto> = call {
        api.plan(originLat, originLng, destLat, destLng, departureTimeEpochSec)
    }
}

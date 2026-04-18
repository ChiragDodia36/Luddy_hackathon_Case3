package com.luddy.bloomington_transit.data.ai.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Response shapes mirrored from bt-ml/service/app/models/schemas.py

@Serializable
data class HealthResponseDto(
    val status: String,
    val service: String,
    @SerialName("model_source") val modelSource: String,
    @SerialName("model_loaded") val modelLoaded: Boolean,
    @SerialName("a1_abort") val a1Abort: Boolean,
    @SerialName("n_routes_with_intercept") val nRoutesWithIntercept: Int,
    val version: String,
)

@Serializable
data class RouteDto(
    @SerialName("route_id") val routeId: String,
    @SerialName("short_name") val shortName: String,
    @SerialName("long_name") val longName: String,
    val color: String,
    @SerialName("text_color") val textColor: String? = null,
)

@Serializable
data class StopDto(
    @SerialName("stop_id") val stopId: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val code: String? = null,
)

@Serializable
data class VehicleDto(
    @SerialName("vehicle_id") val vehicleId: String,
    val label: String? = null,
    @SerialName("trip_id") val tripId: String? = null,
    @SerialName("route_id") val routeId: String? = null,
    val lat: Double,
    val lon: Double,
    val bearing: Float,
    val timestamp: Long,
    @SerialName("current_stop_sequence") val currentStopSequence: Int? = null,
    @SerialName("current_status") val currentStatus: Int? = null,
    @SerialName("is_stale") val isStale: Boolean,
    @SerialName("staleness_seconds") val stalenessSeconds: Int,
)

@Serializable
data class AlertDto(
    @SerialName("alert_id") val alertId: String,
    val header: String? = null,
    val description: String? = null,
    @SerialName("route_ids") val routeIds: List<String> = emptyList(),
)

@Serializable
data class PredictionDto(
    @SerialName("stop_id") val stopId: String,
    @SerialName("stop_sequence") val stopSequence: Int,
    @SerialName("trip_id") val tripId: String,
    @SerialName("route_id") val routeId: String? = null,
    @SerialName("route_short_name") val routeShortName: String? = null,
    val headsign: String? = null,
    @SerialName("vehicle_id") val vehicleId: String? = null,
    @SerialName("scheduled_arrival_utc") val scheduledArrivalUtc: String,
    @SerialName("bt_delay_seconds") val btDelaySeconds: Int,
    @SerialName("bt_predicted_arrival_utc") val btPredictedArrivalUtc: String,
    @SerialName("ours_predicted_arrival_utc") val oursPredictedArrivalUtc: String,
    @SerialName("correction_seconds") val correctionSeconds: Double,
    @SerialName("horizon_seconds") val horizonSeconds: Int,
    val confidence: String,
    @SerialName("model_source") val modelSource: String,
    @SerialName("is_realtime") val isRealtime: Boolean,
)

@Serializable
data class PredictionsResponseDto(
    @SerialName("stop_id") val stopId: String,
    @SerialName("stop_name") val stopName: String? = null,
    @SerialName("horizon_minutes") val horizonMinutes: Int,
    @SerialName("generated_at_utc") val generatedAtUtc: String,
    @SerialName("feed_header_ts_utc") val feedHeaderTsUtc: String? = null,
    @SerialName("feed_header_age_seconds") val feedHeaderAgeSeconds: Int? = null,
    val predictions: List<PredictionDto>,
)

@Serializable
data class TripStopEtaDto(
    @SerialName("stop_id") val stopId: String,
    @SerialName("stop_sequence") val stopSequence: Int,
    @SerialName("stop_name") val stopName: String? = null,
    @SerialName("scheduled_arrival_utc") val scheduledArrivalUtc: String,
    @SerialName("bt_predicted_arrival_utc") val btPredictedArrivalUtc: String,
    @SerialName("ours_predicted_arrival_utc") val oursPredictedArrivalUtc: String,
    @SerialName("correction_seconds") val correctionSeconds: Double,
    val confidence: String,
)

@Serializable
data class TripEtaTrajectoryResponseDto(
    @SerialName("trip_id") val tripId: String,
    @SerialName("route_id") val routeId: String? = null,
    @SerialName("vehicle_id") val vehicleId: String? = null,
    @SerialName("current_stop_sequence") val currentStopSequence: Int? = null,
    @SerialName("generated_at_utc") val generatedAtUtc: String,
    val stops: List<TripStopEtaDto>,
)

@Serializable
data class BunchingEventDto(
    @SerialName("route_id") val routeId: String,
    @SerialName("vehicle_ids") val vehicleIds: List<String>,
    @SerialName("distance_m") val distanceM: Double,
    val lat: Double,
    val lon: Double,
    val severity: String,
)

@Serializable
data class BunchingResponseDto(
    @SerialName("generated_at_utc") val generatedAtUtc: String,
    val events: List<BunchingEventDto>,
)

@Serializable
data class StatsResponseDto(
    @SerialName("generated_at_utc") val generatedAtUtc: String,
    @SerialName("bt_headline_mae_s") val btHeadlineMaeS: Double,
    @SerialName("a1_cv_headline_mae_s") val a1CvHeadlineMaeS: Double? = null,
    @SerialName("a1_cv_improvement_s") val a1CvImprovementS: Double? = null,
    @SerialName("a1_cv_improvement_pct") val a1CvImprovementPct: Double? = null,
    @SerialName("model_source") val modelSource: String,
    @SerialName("routes_with_intercept") val routesWithIntercept: Int,
    @SerialName("live_fleet_size") val liveFleetSize: Int,
    @SerialName("live_stale_vehicle_count") val liveStaleVehicleCount: Int,
)

@Serializable
data class TripPlanLocationDto(
    val lat: Double? = null,
    val lng: Double? = null,
)

@Serializable
data class TripPlanStopDto(
    val name: String? = null,
    val location: TripPlanLocationDto? = null,
    @SerialName("bt_stop_id") val btStopId: String? = null,
    @SerialName("bt_snap_distance_m") val btSnapDistanceM: Double? = null,
    @SerialName("time_text") val timeText: String? = null,
    @SerialName("time_value") val timeValue: Long? = null,
    @SerialName("ai_adjusted_time_value") val aiAdjustedTimeValue: Long? = null,
    @SerialName("ai_correction_seconds") val aiCorrectionSeconds: Double? = null,
    val confidence: String? = null,
)

@Serializable
data class TripPlanStepDto(
    val mode: String,
    @SerialName("duration_s") val durationS: Int,
    @SerialName("distance_m") val distanceM: Int,
    @SerialName("html_instructions") val htmlInstructions: String? = null,
    @SerialName("line_short_name") val lineShortName: String? = null,
    @SerialName("line_color") val lineColor: String? = null,
    @SerialName("line_name") val lineName: String? = null,
    @SerialName("bt_route_id") val btRouteId: String? = null,
    val headsign: String? = null,
    @SerialName("num_stops") val numStops: Int? = null,
    @SerialName("departure_stop") val departureStop: TripPlanStopDto? = null,
    @SerialName("arrival_stop") val arrivalStop: TripPlanStopDto? = null,
    val polyline: String? = null,
    @SerialName("start_location") val startLocation: TripPlanLocationDto? = null,
    @SerialName("end_location") val endLocation: TripPlanLocationDto? = null,
)

@Serializable
data class TripPlanRouteDto(
    val summary: String? = null,
    @SerialName("duration_s") val durationS: Int,
    @SerialName("distance_m") val distanceM: Int,
    @SerialName("departure_time_value") val departureTimeValue: Long? = null,
    @SerialName("departure_time_text") val departureTimeText: String? = null,
    @SerialName("arrival_time_value") val arrivalTimeValue: Long? = null,
    @SerialName("arrival_time_text") val arrivalTimeText: String? = null,
    @SerialName("start_address") val startAddress: String? = null,
    @SerialName("end_address") val endAddress: String? = null,
    @SerialName("start_location") val startLocation: TripPlanLocationDto? = null,
    @SerialName("end_location") val endLocation: TripPlanLocationDto? = null,
    val warnings: List<String> = emptyList(),
    @SerialName("overview_polyline") val overviewPolyline: String? = null,
    val steps: List<TripPlanStepDto> = emptyList(),
)

@Serializable
data class TripPlanMetaDto(
    @SerialName("cache_hit") val cacheHit: Boolean? = null,
    @SerialName("latency_ms") val latencyMs: Double? = null,
    @SerialName("upstream_status") val upstreamStatus: String? = null,
    @SerialName("enrich_ms") val enrichMs: Double? = null,
    @SerialName("total_ms") val totalMs: Double? = null,
    @SerialName("model_source") val modelSource: String? = null,
)

@Serializable
data class TripPlanResponseDto(
    val status: String,
    val routes: List<TripPlanRouteDto> = emptyList(),
    val meta: TripPlanMetaDto? = null,
)

@Serializable
data class NlqResponseDto(
    val query: String,
    val intent: String,
    @SerialName("route_id") val routeId: String? = null,
    @SerialName("stop_id") val stopId: String? = null,
    val direction: String? = null,
    @SerialName("parse_source") val parseSource: String,
    @SerialName("latency_ms") val latencyMs: Double,
)

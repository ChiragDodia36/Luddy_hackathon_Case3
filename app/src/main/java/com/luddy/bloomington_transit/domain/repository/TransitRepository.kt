package com.luddy.bloomington_transit.domain.repository

import com.luddy.bloomington_transit.domain.model.*
import kotlinx.coroutines.flow.Flow

interface TransitRepository {

    // Static GTFS
    suspend fun initStaticData()
    fun getRoutes(): Flow<List<Route>>
    fun getStops(): Flow<List<Stop>>
    fun getStopsForRoute(routeId: String): Flow<List<Stop>>
    fun getShapePointsForRoute(routeId: String): Flow<List<ShapePoint>>
    suspend fun searchStops(query: String): List<Stop>
    suspend fun getNearestStops(lat: Double, lon: Double, radiusMeters: Double = 500.0): List<Stop>

    // Realtime
    suspend fun getLiveBuses(): List<Bus>
    suspend fun getArrivalsForStop(stopId: String): List<Arrival>
    suspend fun getArrivalsForRoute(routeId: String, stopId: String): List<Arrival>
    suspend fun getServiceAlerts(): List<ServiceAlert>

    // Favourites (persisted via DataStore)
    fun getFavouriteStopIds(): Flow<Set<String>>
    suspend fun addFavouriteStop(stopId: String)
    suspend fun removeFavouriteStop(stopId: String)

    // Tracked buses (persisted via DataStore)
    fun getTrackedBusIds(): Flow<Set<String>>
    suspend fun addTrackedBus(vehicleId: String)
    suspend fun removeTrackedBus(vehicleId: String)

    // Notification threshold (minutes)
    fun getNotificationThresholdMinutes(): Flow<Int>
    suspend fun setNotificationThresholdMinutes(minutes: Int)

    // Favourite route (for route picker flow)
    fun getFavouriteRouteId(): Flow<String?>
    suspend fun setFavouriteRouteId(routeId: String?)
}

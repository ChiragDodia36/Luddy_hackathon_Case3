package com.luddy.bloomington_transit.data.repository

import android.util.Log
import com.luddy.bloomington_transit.data.api.BtBackendApi
import com.luddy.bloomington_transit.data.api.DirectionsApi
import com.luddy.bloomington_transit.data.api.GtfsStaticParser
import com.luddy.bloomington_transit.data.api.decodePolyline
import com.luddy.bloomington_transit.BuildConfig
import com.luddy.bloomington_transit.data.local.*
import com.luddy.bloomington_transit.domain.model.*
import com.luddy.bloomington_transit.domain.repository.TransitRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransitRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val backendApi: BtBackendApi,
    private val directionsApi: DirectionsApi,
    private val staticParser: GtfsStaticParser,
    private val prefs: UserPreferencesDataStore
) : TransitRepository {

    companion object {
        private const val TAG = "TransitRepo"
        private const val GTFS_REFRESH_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24h
    }

    private val initMutex = Mutex()
    private var isInitialized = false

    // trip_id → route_id — populated on first init; used to resolve routeId when RT feed omits it
    @Volatile private var tripToRouteCache: Map<String, String> = emptyMap()
    // route_id → RouteEntity — used to look up color in getArrivalsForStop
    @Volatile private var routeEntityCache: Map<String, com.luddy.bloomington_transit.data.local.entity.RouteEntity> = emptyMap()

    override suspend fun initStaticData() {
        // Mutex prevents concurrent calls from HomeViewModel + MapViewModel both downloading
        initMutex.withLock {
            if (isInitialized) return

            val lastUpdated = prefs.gtfsLastUpdated.first()
            val isStale = System.currentTimeMillis() - lastUpdated > GTFS_REFRESH_INTERVAL_MS
            val isEmpty = db.routeDao().count() == 0

            if (!isEmpty && !isStale) {
                Log.d(TAG, "GTFS static data is fresh (${db.routeDao().count()} routes in DB)")
                if (tripToRouteCache.isEmpty()) {
                    tripToRouteCache = db.tripDao().getAllTrips().associate { it.tripId to it.routeId }
                    Log.d(TAG, "Trip→route cache built from DB: ${tripToRouteCache.size} trips")
                }
                if (routeEntityCache.isEmpty()) {
                    routeEntityCache = db.routeDao().getAllRoutesList().associateBy { it.id }
                }
                isInitialized = true
                return
            }

            Log.d(TAG, "Downloading GTFS static data...")
            try {
                val data = staticParser.downloadAndParse()

                // Clear old data
                db.routeDao().deleteAll()
                db.stopDao().deleteAll()
                db.shapeDao().deleteAll()
                db.tripDao().deleteAllTrips()
                db.tripDao().deleteAllStopTimes()
                db.tripDao().deleteAllRouteStops()

                // Build in-memory caches from parsed data
                tripToRouteCache = data.trips.associate { it.tripId to it.routeId }
                routeEntityCache = data.routes.associateBy { it.id }
                Log.d(TAG, "Trip→route cache built: ${tripToRouteCache.size} trips")

                // Insert core data first (routes + stops appear immediately in UI)
                db.routeDao().insertAll(data.routes)
                db.stopDao().insertAll(data.stops)
                db.tripDao().insertTrips(data.trips)

                // Insert shapes in batches
                data.shapes.chunked(1000).forEach { db.shapeDao().insertAll(it) }

                // Insert route_stops (needed for stop→route lookup)
                data.routeStops.chunked(500).forEach { db.tripDao().insertRouteStops(it) }

                // Insert stop_times in large batches (this is the big one)
                data.stopTimes.chunked(1000).forEach { db.tripDao().insertStopTimes(it) }

                prefs.setGtfsLastUpdated(System.currentTimeMillis())
                isInitialized = true
                Log.d(TAG, "GTFS loaded: ${data.routes.size} routes, ${data.stops.size} stops, ${data.stopTimes.size} stop_times")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load GTFS static data", e)
            }
        }
    }

    override fun getRoutes(): Flow<List<Route>> =
        db.routeDao().getAllRoutes().map { list -> list.map { it.toDomain() } }

    override fun getStops(): Flow<List<Stop>> =
        db.stopDao().getAllStops().map { list -> list.map { it.toDomain() } }

    override fun getStopsForRoute(routeId: String): Flow<List<Stop>> =
        db.stopDao().getStopsForRoute(routeId).map { list -> list.map { it.toDomain() } }

    override fun getShapePointsForRoute(routeId: String): Flow<List<ShapePoint>> =
        db.shapeDao().getShapesForRoute(routeId).map { list -> list.map { it.toDomain() } }

    override suspend fun searchStops(query: String): List<Stop> =
        db.stopDao().searchStops(query).map { it.toDomain() }

    override suspend fun getNearestStops(lat: Double, lon: Double, radiusMeters: Double): List<Stop> {
        val all = db.stopDao().getNearestStops(lat, lon)
        // Filter by actual Haversine distance
        return all.map { it.toDomain() }.filter { stop ->
            haversineMeters(lat, lon, stop.lat, stop.lon) <= radiusMeters
        }
    }

    override suspend fun getLiveBuses(): List<Bus> {
        return try {
            val dtos = backendApi.getBuses()
            val buses = dtos.map { dto ->
                Bus(
                    vehicleId = dto.vehicleId,
                    tripId = dto.tripId,
                    routeId = dto.routeId,
                    lat = dto.lat,
                    lon = dto.lon,
                    bearing = dto.bearing,
                    speed = dto.speed,
                    timestamp = dto.timestamp,
                    currentStopSequence = dto.currentStopSequence,
                    label = dto.label
                )
            }
            Log.d(TAG, "getLiveBuses: ${buses.size} buses, routeIds=${buses.map { it.routeId }.distinct()}")
            buses
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch vehicle positions", e)
            emptyList()
        }
    }

    override suspend fun getArrivalsForStop(stopId: String): List<Arrival> {
        return try {
            val dtos = backendApi.getArrivals(stopId)
            val stop = db.stopDao().getStopById(stopId)
            val now = System.currentTimeMillis()
            dtos.map { dto ->
                val route = routeEntityCache[dto.routeId]
                val scheduledMs = dto.scheduledUnix
                val displayMs = now + dto.etaSeconds * 1000
                Arrival(
                    routeId = dto.routeId,
                    routeShortName = dto.routeShortName.ifBlank { route?.shortName ?: dto.routeId },
                    routeColor = route?.color ?: "0057A8",
                    headsign = dto.headsign,
                    stopId = stopId,
                    stopName = stop?.name ?: stopId,
                    predictedArrivalMs = if (dto.isRealtime) displayMs else -1L,
                    scheduledArrivalMs = scheduledMs,
                    tripId = dto.tripId
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get arrivals for stop $stopId", e)
            emptyList()
        }
    }

    override suspend fun getArrivalsForRoute(routeId: String, stopId: String): List<Arrival> =
        getArrivalsForStop(stopId).filter { it.routeId == routeId }

    override suspend fun getServiceAlerts(): List<ServiceAlert> {
        return try {
            backendApi.getAlerts().map { dto ->
                ServiceAlert(dto.id, dto.header, dto.description, dto.routeIds)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get service alerts", e)
            emptyList()
        }
    }

    override suspend fun getReachability(
        userLat: Double, userLon: Double,
        stop: Stop,
        arrivals: List<Arrival>
    ): Reachability? {
        return try {
            val origin = "$userLat,$userLon"
            val dest = "${stop.lat},${stop.lon}"
            val resp = directionsApi.getWalkingDirections(origin, dest, key = BuildConfig.MAPS_API_KEY)
            if (resp.status != "OK" || resp.routes.isEmpty()) return null

            val leg = resp.routes[0].legs[0]
            val walkSeconds = leg.duration.value
            val polyline = decodePolyline(resp.routes[0].overviewPolyline.points)

            val nextArrival = arrivals.firstOrNull() ?: return null
            val nextBusSeconds = maxOf(0L, nextArrival.displayArrivalMs - System.currentTimeMillis()) / 1000L
            val spareSeconds = nextBusSeconds - walkSeconds

            Reachability(
                walkSeconds = walkSeconds,
                nextBusSeconds = nextBusSeconds,
                routeShortName = nextArrival.routeShortName,
                canMakeIt = spareSeconds >= 0,
                spareSeconds = spareSeconds,
                walkPolyline = polyline
            )
        } catch (e: Exception) {
            Log.e(TAG, "getReachability failed", e)
            null
        }
    }

    // Favourites
    override fun getFavouriteStopIds(): Flow<Set<String>> = prefs.favouriteStopIds
    override suspend fun addFavouriteStop(stopId: String) = prefs.addFavouriteStop(stopId)
    override suspend fun removeFavouriteStop(stopId: String) = prefs.removeFavouriteStop(stopId)

    // Tracked buses
    override fun getTrackedBusIds(): Flow<Set<String>> = prefs.trackedBusIds
    override suspend fun addTrackedBus(vehicleId: String) = prefs.addTrackedBus(vehicleId)
    override suspend fun removeTrackedBus(vehicleId: String) = prefs.removeTrackedBus(vehicleId)

    // Notification threshold
    override fun getNotificationThresholdMinutes(): Flow<Int> = prefs.notificationThresholdMinutes
    override suspend fun setNotificationThresholdMinutes(minutes: Int) =
        prefs.setNotificationThreshold(minutes)

    // Favourite route
    override fun getFavouriteRouteId(): Flow<String?> = prefs.favouriteRouteId
    override suspend fun setFavouriteRouteId(routeId: String?) = prefs.setFavouriteRouteId(routeId)

    override fun getRecentStopIds(): Flow<List<String>> = prefs.recentStopIds
    override suspend fun addRecentStop(stopId: String) = prefs.addRecentStop(stopId)

    // Helpers
    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dPhi = Math.toRadians(lat2 - lat1)
        val dLambda = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dPhi / 2).let { it * it } +
                Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2).let { it * it }
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

}

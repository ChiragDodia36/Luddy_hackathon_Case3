package com.luddy.bloomington_transit.data.repository

import android.util.Log
import com.google.transit.realtime.GtfsRealtime
import com.luddy.bloomington_transit.data.api.GtfsRealtimeApi
import com.luddy.bloomington_transit.data.api.GtfsStaticParser
import com.luddy.bloomington_transit.data.local.*
import com.luddy.bloomington_transit.domain.model.*
import com.luddy.bloomington_transit.domain.repository.TransitRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransitRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val realtimeApi: GtfsRealtimeApi,
    private val staticParser: GtfsStaticParser,
    private val prefs: UserPreferencesDataStore
) : TransitRepository {

    companion object {
        private const val TAG = "TransitRepo"
        private const val GTFS_REFRESH_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24h
    }

    private val initMutex = Mutex()
    private var isInitialized = false

    override suspend fun initStaticData() {
        // Mutex prevents concurrent calls from HomeViewModel + MapViewModel both downloading
        initMutex.withLock {
            if (isInitialized) return

            val lastUpdated = prefs.gtfsLastUpdated.first()
            val isStale = System.currentTimeMillis() - lastUpdated > GTFS_REFRESH_INTERVAL_MS
            val isEmpty = db.routeDao().count() == 0

            if (!isEmpty && !isStale) {
                Log.d(TAG, "GTFS static data is fresh (${db.routeDao().count()} routes in DB)")
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
            val body = realtimeApi.getVehiclePositions()
            val feed = GtfsRealtime.FeedMessage.parseFrom(body.byteStream())
            feed.entityList.mapNotNull { entity ->
                if (!entity.hasVehicle()) return@mapNotNull null
                val v = entity.vehicle
                if (!v.hasPosition()) return@mapNotNull null
                Bus(
                    vehicleId = v.vehicle?.id ?: entity.id,
                    tripId = v.trip?.tripId ?: "",
                    routeId = v.trip?.routeId ?: "",
                    lat = v.position.latitude.toDouble(),
                    lon = v.position.longitude.toDouble(),
                    bearing = v.position.bearing,
                    speed = v.position.speed,
                    timestamp = v.timestamp,
                    currentStopSequence = v.currentStopSequence,
                    label = v.vehicle?.label ?: ""
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch vehicle positions", e)
            emptyList()
        }
    }

    override suspend fun getArrivalsForStop(stopId: String): List<Arrival> {
        return try {
            // Get realtime trip updates
            val body = realtimeApi.getTripUpdates()
            val feed = GtfsRealtime.FeedMessage.parseFrom(body.byteStream())

            val realtimeMap = mutableMapOf<String, Long>() // tripId -> predicted arrival ms

            feed.entityList.forEach { entity ->
                if (!entity.hasTripUpdate()) return@forEach
                val tu = entity.tripUpdate
                val tripId = tu.trip?.tripId ?: return@forEach
                tu.stopTimeUpdateList.forEach { stu ->
                    if (stu.stopId == stopId) {
                        val arrivalTime = if (stu.hasArrival()) stu.arrival.time else
                            if (stu.hasDeparture()) stu.departure.time else 0L
                        if (arrivalTime > 0) realtimeMap[tripId] = arrivalTime * 1000L
                    }
                }
            }

            // Get static schedule for today
            val staticTimes = db.tripDao().getStopTimesForStop(stopId)
            val todayMs = getTodayBaseMs()

            val arrivals = mutableListOf<Arrival>()

            staticTimes.forEach { st ->
                val trip = db.tripDao().getTripById(st.tripId) ?: return@forEach
                val route = db.routeDao().getAllRoutes().first()
                    .find { it.id == trip.routeId } ?: return@forEach

                val scheduledMs = parseGtfsTimeToMs(st.arrivalTime, todayMs)
                    .takeIf { it > 0 } ?: return@forEach

                // Only show upcoming arrivals (within next 2 hours)
                val now = System.currentTimeMillis()
                if (scheduledMs < now - 60_000 || scheduledMs > now + 2 * 3600_000) return@forEach

                val stop = db.stopDao().getStopById(stopId)

                arrivals.add(
                    Arrival(
                        routeId = trip.routeId,
                        routeShortName = route.shortName,
                        routeColor = route.color,
                        headsign = trip.headsign,
                        stopId = stopId,
                        stopName = stop?.name ?: stopId,
                        predictedArrivalMs = realtimeMap[st.tripId] ?: -1L,
                        scheduledArrivalMs = scheduledMs,
                        tripId = st.tripId
                    )
                )
            }

            arrivals.sortedBy { it.displayArrivalMs }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get arrivals for stop $stopId", e)
            emptyList()
        }
    }

    override suspend fun getArrivalsForRoute(routeId: String, stopId: String): List<Arrival> =
        getArrivalsForStop(stopId).filter { it.routeId == routeId }

    override suspend fun getServiceAlerts(): List<ServiceAlert> {
        return try {
            val body = realtimeApi.getAlerts()
            val feed = GtfsRealtime.FeedMessage.parseFrom(body.byteStream())
            feed.entityList.mapNotNull { entity ->
                if (!entity.hasAlert()) return@mapNotNull null
                val alert = entity.alert
                val header = alert.headerTextOrBuilder.translationList
                    .firstOrNull()?.text ?: return@mapNotNull null
                val desc = alert.descriptionTextOrBuilder.translationList
                    .firstOrNull()?.text ?: ""
                val routeIds = alert.informedEntityList
                    .mapNotNull { it.routeId.takeIf { r -> r.isNotBlank() } }
                ServiceAlert(entity.id, header, desc, routeIds)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get service alerts", e)
            emptyList()
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

    private fun getTodayBaseMs(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    // GTFS times can exceed 24h (e.g. "25:30:00" for next-day trips)
    private fun parseGtfsTimeToMs(timeStr: String, todayBaseMs: Long): Long {
        return try {
            val parts = timeStr.split(":").map { it.toInt() }
            val hours = parts[0]
            val minutes = parts[1]
            val seconds = parts[2]
            todayBaseMs + (hours * 3600L + minutes * 60L + seconds) * 1000L
        } catch (e: Exception) {
            -1L
        }
    }
}

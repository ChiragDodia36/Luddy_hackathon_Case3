package com.luddy.bloomington_transit.data.api

import android.content.Context
import com.luddy.bloomington_transit.data.local.entity.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

data class GtfsStaticData(
    val routes: List<RouteEntity>,
    val stops: List<StopEntity>,
    val trips: List<TripEntity>,
    val shapes: List<ShapeEntity>,
    val stopTimes: List<StopTimeEntity>,
    val routeStops: List<RouteStopEntity>
)

@Singleton
class GtfsStaticParser @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val GTFS_ZIP_URL =
            "https://s3.amazonaws.com/etatransit.gtfs/bloomingtontransit.etaspot.net/gtfs.zip"
    }

    suspend fun downloadAndParse(): GtfsStaticData = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(GTFS_ZIP_URL).build()
        // Buffer all bytes before closing the network response to avoid "Stream closed" errors
        val responseBytes = okHttpClient.newCall(request).execute().use { response ->
            response.body?.bytes() ?: error("Empty GTFS response")
        }

        val files = mutableMapOf<String, List<String>>()

        ZipInputStream(ByteArrayInputStream(responseBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name.substringAfterLast("/")
                if (name in NEEDED_FILES) {
                    // Read entry bytes first — don't wrap zis in a reader that closes it
                    val entryBytes = zis.readBytes()
                    files[name] = entryBytes.inputStream().bufferedReader(Charsets.UTF_8).readLines()
                }
                entry = zis.nextEntry
            }
        }

        val routes = parseRoutes(files["routes.txt"] ?: emptyList())
        val stops = parseStops(files["stops.txt"] ?: emptyList())
        val trips = parseTrips(files["trips.txt"] ?: emptyList())
        val shapes = parseShapes(files["shapes.txt"] ?: emptyList())
        val (stopTimes, routeStops) = parseStopTimes(
            files["stop_times.txt"] ?: emptyList(), trips
        )

        GtfsStaticData(routes, stops, trips, shapes, stopTimes, routeStops)
    }

    private fun parseRoutes(lines: List<String>): List<RouteEntity> {
        if (lines.size < 2) return emptyList()
        val header = lines[0].split(",").map { it.trim().removeSurrounding("\"") }
        val idxId = header.indexOf("route_id")
        val idxShort = header.indexOf("route_short_name")
        val idxLong = header.indexOf("route_long_name")
        val idxColor = header.indexOf("route_color")
        val idxText = header.indexOf("route_text_color")

        return lines.drop(1).mapNotNull { line ->
            val cols = parseCsvLine(line)
            if (cols.size <= maxOf(idxId, idxShort)) return@mapNotNull null
            RouteEntity(
                id = cols.getOrElse(idxId) { "" }.trim(),
                shortName = cols.getOrElse(idxShort) { "" }.trim(),
                longName = cols.getOrElse(idxLong) { "" }.trim(),
                color = cols.getOrElse(idxColor) { "0057A8" }.trim().ifEmpty { "0057A8" },
                textColor = cols.getOrElse(idxText) { "FFFFFF" }.trim().ifEmpty { "FFFFFF" }
            )
        }.filter { it.id.isNotBlank() }
    }

    private fun parseStops(lines: List<String>): List<StopEntity> {
        if (lines.size < 2) return emptyList()
        val header = lines[0].split(",").map { it.trim().removeSurrounding("\"") }
        val idxId = header.indexOf("stop_id")
        val idxName = header.indexOf("stop_name")
        val idxLat = header.indexOf("stop_lat")
        val idxLon = header.indexOf("stop_lon")
        val idxCode = header.indexOf("stop_code")

        return lines.drop(1).mapNotNull { line ->
            val cols = parseCsvLine(line)
            if (cols.size <= maxOf(idxId, idxLat, idxLon)) return@mapNotNull null
            StopEntity(
                id = cols.getOrElse(idxId) { "" }.trim(),
                name = cols.getOrElse(idxName) { "Unknown Stop" }.trim(),
                lat = cols.getOrElse(idxLat) { "0" }.trim().toDoubleOrNull() ?: return@mapNotNull null,
                lon = cols.getOrElse(idxLon) { "0" }.trim().toDoubleOrNull() ?: return@mapNotNull null,
                code = if (idxCode >= 0) cols.getOrElse(idxCode) { "" }.trim() else ""
            )
        }.filter { it.id.isNotBlank() }
    }

    private fun parseTrips(lines: List<String>): List<TripEntity> {
        if (lines.size < 2) return emptyList()
        val header = lines[0].split(",").map { it.trim().removeSurrounding("\"") }
        val idxTripId = header.indexOf("trip_id")
        val idxRouteId = header.indexOf("route_id")
        val idxShapeId = header.indexOf("shape_id")
        val idxHeadsign = header.indexOf("trip_headsign")
        val idxServiceId = header.indexOf("service_id")

        return lines.drop(1).mapNotNull { line ->
            val cols = parseCsvLine(line)
            if (cols.size <= maxOf(idxTripId, idxRouteId)) return@mapNotNull null
            TripEntity(
                tripId = cols.getOrElse(idxTripId) { "" }.trim(),
                routeId = cols.getOrElse(idxRouteId) { "" }.trim(),
                shapeId = if (idxShapeId >= 0) cols.getOrElse(idxShapeId) { "" }.trim() else "",
                headsign = if (idxHeadsign >= 0) cols.getOrElse(idxHeadsign) { "" }.trim() else "",
                serviceId = if (idxServiceId >= 0) cols.getOrElse(idxServiceId) { "" }.trim() else ""
            )
        }.filter { it.tripId.isNotBlank() }
    }

    private fun parseShapes(lines: List<String>): List<ShapeEntity> {
        if (lines.size < 2) return emptyList()
        val header = lines[0].split(",").map { it.trim().removeSurrounding("\"") }
        val idxId = header.indexOf("shape_id")
        val idxLat = header.indexOf("shape_pt_lat")
        val idxLon = header.indexOf("shape_pt_lon")
        val idxSeq = header.indexOf("shape_pt_sequence")

        return lines.drop(1).mapNotNull { line ->
            val cols = parseCsvLine(line)
            if (cols.size <= maxOf(idxId, idxLat, idxLon, idxSeq)) return@mapNotNull null
            ShapeEntity(
                shapeId = cols.getOrElse(idxId) { "" }.trim(),
                lat = cols.getOrElse(idxLat) { "0" }.trim().toDoubleOrNull() ?: return@mapNotNull null,
                lon = cols.getOrElse(idxLon) { "0" }.trim().toDoubleOrNull() ?: return@mapNotNull null,
                sequence = cols.getOrElse(idxSeq) { "0" }.trim().toIntOrNull() ?: 0
            )
        }
    }

    private fun parseStopTimes(
        lines: List<String>,
        trips: List<TripEntity>
    ): Pair<List<StopTimeEntity>, List<RouteStopEntity>> {
        if (lines.size < 2) return Pair(emptyList(), emptyList())
        val header = lines[0].split(",").map { it.trim().removeSurrounding("\"") }
        val idxTrip = header.indexOf("trip_id")
        val idxArr = header.indexOf("arrival_time")
        val idxDep = header.indexOf("departure_time")
        val idxStop = header.indexOf("stop_id")
        val idxSeq = header.indexOf("stop_sequence")

        val tripRouteMap = trips.associate { it.tripId to it.routeId }
        val routeStopSet = mutableSetOf<Pair<String, String>>()
        val stopTimes = mutableListOf<StopTimeEntity>()

        lines.drop(1).forEach { line ->
            val cols = parseCsvLine(line)
            if (cols.size <= maxOf(idxTrip, idxStop)) return@forEach
            val tripId = cols.getOrElse(idxTrip) { "" }.trim()
            val stopId = cols.getOrElse(idxStop) { "" }.trim()
            if (tripId.isBlank() || stopId.isBlank()) return@forEach

            stopTimes.add(
                StopTimeEntity(
                    tripId = tripId,
                    stopId = stopId,
                    arrivalTime = cols.getOrElse(idxArr) { "" }.trim(),
                    departureTime = cols.getOrElse(idxDep) { "" }.trim(),
                    stopSequence = cols.getOrElse(idxSeq) { "0" }.trim().toIntOrNull() ?: 0
                )
            )

            tripRouteMap[tripId]?.let { routeId ->
                routeStopSet.add(routeId to stopId)
            }
        }

        val routeStops = routeStopSet.map { (routeId, stopId) ->
            RouteStopEntity(routeId, stopId)
        }
        return Pair(stopTimes, routeStops)
    }

    // Simple CSV parser that handles quoted fields
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> { result.add(current.toString()); current = StringBuilder() }
                else -> current.append(ch)
            }
        }
        result.add(current.toString())
        return result
    }

    private val NEEDED_FILES = setOf(
        "routes.txt", "stops.txt", "trips.txt", "shapes.txt", "stop_times.txt"
    )
}

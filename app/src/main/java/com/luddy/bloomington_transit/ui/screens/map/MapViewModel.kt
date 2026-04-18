package com.luddy.bloomington_transit.ui.screens.map

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.luddy.bloomington_transit.domain.model.*
import com.luddy.bloomington_transit.domain.repository.TransitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import kotlin.math.*

data class PlaceSuggestion(
    val placeId: String,
    val primaryText: String,
    val secondaryText: String
)

data class MapUiState(
    val buses: List<Bus> = emptyList(),
    val routes: List<Route> = emptyList(),
    val stops: List<Stop> = emptyList(),
    val selectedRouteIds: Set<String> = emptySet(),
    val selectedBus: Bus? = null,
    val selectedStop: Stop? = null,
    val stopArrivals: List<Arrival> = emptyList(),
    val trackedBusIds: Set<String> = emptySet(),
    val shapesByRoute: Map<String, List<List<LatLng>>> = emptyMap(),
    val isLoadingArrivals: Boolean = false,
    val isInitializing: Boolean = true,
    // Search & routing
    val searchQuery: String = "",
    val isSearchFocused: Boolean = false,
    val placeSuggestions: List<PlaceSuggestion> = emptyList(),
    val isSearchLoading: Boolean = false,
    val destinationLatLng: LatLng? = null,
    val destinationName: String? = null,
    val userLocation: LatLng? = null,
    // Route plan result — 1 routeId = direct, 2 = transfer
    val suggestedRouteIds: Set<String> = emptySet(),
    val isTransferRoute: Boolean = false,
    val transferStop: Stop? = null,
    val transferRoute: Route? = null,
    val walkInMeters: Double? = null,
    val walkOutMeters: Double? = null,
    val isRoutingLoading: Boolean = false,
    val routingError: String? = null,
    val boardingStop: Stop? = null,
    val alightingStop: Stop? = null,
    val boardingArrivals: List<Arrival> = emptyList(),
    val transferArrivals: List<Arrival> = emptyList(),
    val alightingArrivals: List<Arrival> = emptyList()
)

// Walking constants
private const val WALK_M_PER_MIN = 80.0          // 80 m/min ≈ avg pedestrian speed
private const val MAX_WALK_METERS = 12 * WALK_M_PER_MIN   // 960 m → 12 min cap
private const val TRANSFER_PENALTY_M = 400.0      // Penalise 1 transfer ≈ 5 extra min walk
private const val HALF_MILE_M = 804.67            // 0.5 mile in metres (for stop display)

fun metersToWalkMinutes(meters: Double): Int = ceil(meters / WALK_M_PER_MIN).toInt()

@HiltViewModel
class MapViewModel @Inject constructor(
    private val repository: TransitRepository,
    private val placesClient: PlacesClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            repository.initStaticData()
            _uiState.update { it.copy(isInitializing = false) }
            Log.d("MapVM", "✅ CP1: Static data ready")
            observeStaticData()
            observeTrackedBuses()
            startPolling()
        }
    }

    private fun observeStaticData() {
        viewModelScope.launch {
            repository.getRoutes().collect { routes ->
                _uiState.update { state -> state.copy(routes = routes) }
                routes.forEach { loadShapeForRoute(it.id) }
            }
        }
        viewModelScope.launch {
            repository.getStops().collect { stops ->
                _uiState.update { it.copy(stops = stops) }
            }
        }
    }

    private fun loadShapeForRoute(routeId: String) {
        viewModelScope.launch {
            repository.getShapePointsForRoute(routeId).collect { points ->
                val segments = points
                    .groupBy { it.shapeId }
                    .values
                    .map { seg -> seg.map { LatLng(it.lat, it.lon) } }
                _uiState.update { state ->
                    state.copy(shapesByRoute = state.shapesByRoute + (routeId to segments))
                }
            }
        }
    }

    private fun observeTrackedBuses() {
        viewModelScope.launch {
            repository.getTrackedBusIds().collect { ids ->
                _uiState.update { it.copy(trackedBusIds = ids) }
            }
        }
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                val buses = repository.getLiveBuses()
                _uiState.update { it.copy(buses = buses) }
                refreshRoutingEtas()
                delay(10_000L)
            }
        }
    }

    private suspend fun refreshRoutingEtas() {
        val s = _uiState.value
        if (s.suggestedRouteIds.isEmpty()) return
        val firstRouteId = s.suggestedRouteIds.first()
        val boarding = s.boardingStop ?: return
        val alighting = s.alightingStop ?: return
        try {
            val boardingArrivals = repository.getArrivalsForStop(boarding.id)
                .filter { it.routeId == firstRouteId }
            val alightingRouteId = if (s.isTransferRoute) s.transferRoute?.id ?: firstRouteId else firstRouteId
            val alightingArrivals = repository.getArrivalsForStop(alighting.id)
                .filter { it.routeId == alightingRouteId }
            val transferArrivals = if (s.isTransferRoute && s.transferStop != null && s.transferRoute != null) {
                repository.getArrivalsForStop(s.transferStop.id).filter { it.routeId == s.transferRoute.id }
            } else emptyList()
            _uiState.update { it.copy(
                boardingArrivals = boardingArrivals,
                alightingArrivals = alightingArrivals,
                transferArrivals = transferArrivals
            ) }
        } catch (_: Exception) {}
    }

    // ── Manual route selection ────────────────────────────────────────────────

    fun toggleRoute(routeId: String) {
        _uiState.update { state ->
            val current = state.selectedRouteIds
            state.copy(
                selectedRouteIds = if (routeId in current) current - routeId else current + routeId,
                suggestedRouteIds = emptySet()
            )
        }
    }

    fun toggleAllRoutes() {
        _uiState.update { state ->
            val allIds = state.routes.map { it.id }.toSet()
            state.copy(
                selectedRouteIds = if (state.selectedRouteIds.size == allIds.size) emptySet() else allIds,
                suggestedRouteIds = emptySet()
            )
        }
    }

    fun selectOnlyRoute(routeId: String) {
        _uiState.update { it.copy(selectedRouteIds = setOf(routeId)) }
    }

    fun selectBus(bus: Bus) {
        _uiState.update { it.copy(selectedBus = bus, selectedStop = null) }
    }

    fun selectStop(stop: Stop) {
        _uiState.update { it.copy(selectedStop = stop, selectedBus = null, isLoadingArrivals = true) }
        viewModelScope.launch {
            val arrivals = repository.getArrivalsForStop(stop.id)
            _uiState.update { it.copy(stopArrivals = arrivals, isLoadingArrivals = false) }
        }
    }

    fun dismissBottomSheet() {
        _uiState.update { it.copy(selectedBus = null, selectedStop = null) }
    }

    fun trackBus(vehicleId: String) { viewModelScope.launch { repository.addTrackedBus(vehicleId) } }
    fun untrackBus(vehicleId: String) { viewModelScope.launch { repository.removeTrackedBus(vehicleId) } }

    // ── Location ──────────────────────────────────────────────────────────────

    fun updateUserLocation(lat: Double, lon: Double) {
        _uiState.update { it.copy(userLocation = LatLng(lat, lon)) }
        Log.d("MapVM", "✅ CP3: User location → $lat, $lon")
    }

    // ── Places search ─────────────────────────────────────────────────────────

    fun onSearchFocusChanged(focused: Boolean) {
        _uiState.update { it.copy(isSearchFocused = focused) }
    }

    fun searchPlaces(query: String) {
        _uiState.update { it.copy(searchQuery = query, isSearchLoading = query.isNotBlank()) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(placeSuggestions = emptyList(), isSearchLoading = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300L)
            try {
                val request = FindAutocompletePredictionsRequest.builder()
                    .setQuery(query)
                    .setLocationBias(
                        RectangularBounds.newInstance(
                            LatLng(39.10, -86.60),
                            LatLng(39.22, -86.45)
                        )
                    )
                    .setCountries(listOf("US"))
                    .build()
                val response = placesClient.findAutocompletePredictions(request).await()
                val suggestions = response.autocompletePredictions.map { p ->
                    PlaceSuggestion(
                        placeId = p.placeId,
                        primaryText = p.getPrimaryText(null).toString(),
                        secondaryText = p.getSecondaryText(null).toString()
                    )
                }
                _uiState.update { it.copy(placeSuggestions = suggestions, isSearchLoading = false) }
                Log.d("MapVM", "✅ CP2: ${suggestions.size} suggestions for \"$query\"")
            } catch (e: Exception) {
                Log.e("MapVM", "Places error: ${e.message}")
                _uiState.update { it.copy(placeSuggestions = emptyList(), isSearchLoading = false) }
            }
        }
    }

    fun selectPlace(suggestion: PlaceSuggestion) {
        _uiState.update { it.copy(
            searchQuery = suggestion.primaryText,
            placeSuggestions = emptyList(),
            isSearchFocused = false,
            isRoutingLoading = true,
            routingError = null,
            suggestedRouteIds = emptySet(),
            selectedRouteIds = emptySet()
        ) }
        viewModelScope.launch {
            try {
                val request = FetchPlaceRequest.newInstance(
                    suggestion.placeId, listOf(Place.Field.NAME, Place.Field.LAT_LNG)
                )
                val response = placesClient.fetchPlace(request).await()
                val latLng = response.place.latLng
                val name = response.place.name ?: suggestion.primaryText
                if (latLng == null) {
                    _uiState.update { it.copy(routingError = "Could not get coordinates for this place", isRoutingLoading = false) }
                    return@launch
                }
                _uiState.update { it.copy(destinationLatLng = latLng, destinationName = name) }
                Log.d("MapVM", "✅ CP3: Destination → $name at $latLng")
                computeOptimalRoute(latLng)
            } catch (e: Exception) {
                Log.e("MapVM", "FetchPlace error: ${e.message}")
                _uiState.update { it.copy(routingError = "Could not fetch place details", isRoutingLoading = false) }
            }
        }
    }

    // ── Routing algorithm: direct + 1-transfer, 12-min walk cap ──────────────

    private suspend fun computeOptimalRoute(dest: LatLng) {
        val userLoc = _uiState.value.userLocation
        if (userLoc == null) {
            _uiState.update { it.copy(routingError = "Enable location permission to find a route", isRoutingLoading = false) }
            return
        }
        try {
            val destStops = repository.getNearestStops(dest.latitude, dest.longitude, 2000.0)
            val userStops = repository.getNearestStops(userLoc.latitude, userLoc.longitude, 1500.0)

            if (destStops.isEmpty()) { _uiState.update { it.copy(routingError = "No bus stops near the destination", isRoutingLoading = false) }; return }
            if (userStops.isEmpty()) { _uiState.update { it.copy(routingError = "No bus stops near your location", isRoutingLoading = false) }; return }

            val userStopIds = userStops.map { it.id }.toSet()
            val destStopIds  = destStops.map { it.id }.toSet()

            // Build route→stopIds map (one DB query per route)
            val allRoutes = _uiState.value.routes
            val routeStopIds  = mutableMapOf<String, Set<String>>()
            val routeStopObjs = mutableMapOf<String, List<Stop>>()
            for (route in allRoutes) {
                val stopsForRoute = repository.getStopsForRoute(route.id).first()
                routeStopIds[route.id]  = stopsForRoute.map { it.id }.toSet()
                routeStopObjs[route.id] = stopsForRoute
            }

            val allStopsById = _uiState.value.stops.associateBy { it.id }

            // ── Direct route candidates ───────────────────────────────────────
            data class DirectCandidate(val route: Route, val boardingStop: Stop, val alightingStop: Stop, val walkIn: Double, val walkOut: Double)

            val directCandidates = mutableListOf<DirectCandidate>()
            for (route in allRoutes) {
                val rStopIds = routeStopIds[route.id] ?: continue
                val boardingOptions = userStops.filter { it.id in rStopIds }
                val alightingOptions = destStops.filter { it.id in rStopIds }
                if (boardingOptions.isNotEmpty() && alightingOptions.isNotEmpty()) {
                    val boarding = boardingOptions.first()
                    val alighting = alightingOptions.first()
                    val walkIn  = haversineMeters(userLoc.latitude, userLoc.longitude, boarding.lat, boarding.lon)
                    val walkOut = haversineMeters(dest.latitude, dest.longitude, alighting.lat, alighting.lon)
                    directCandidates.add(DirectCandidate(route, boarding, alighting, walkIn, walkOut))
                }
            }

            // Filter: both legs must be under 12-min walk cap
            val validDirect = directCandidates.filter { it.walkIn <= MAX_WALK_METERS && it.walkOut <= MAX_WALK_METERS }
            Log.d("MapVM", "Direct: ${directCandidates.size} raw, ${validDirect.size} within walk cap")

            // ── Transfer route candidates ─────────────────────────────────────
            data class TransferCandidate(
                val route1: Route, val boardingStop: Stop, val transferStop: Stop,
                val route2: Route, val alightingStop: Stop, val walkIn: Double, val walkOut: Double
            )

            val transferCandidates = mutableListOf<TransferCandidate>()

            // Routes that can board from user area
            val boardableRoutes = allRoutes.filter { r -> routeStopIds[r.id]?.any { it in userStopIds } == true }
            // Routes that can alight at dest area
            val alightableRouteIds = allRoutes.filter { r -> routeStopIds[r.id]?.any { it in destStopIds } == true }.map { it.id }.toSet()

            for (route1 in boardableRoutes) {
                val r1StopIds = routeStopIds[route1.id] ?: continue
                val boardingStop = userStops.firstOrNull { it.id in r1StopIds } ?: continue
                val walkIn = haversineMeters(userLoc.latitude, userLoc.longitude, boardingStop.lat, boardingStop.lon)
                if (walkIn > MAX_WALK_METERS) continue

                // Stops on route1 that are also served by an alightable route = transfer candidates
                for (transferStopId in r1StopIds) {
                    val transferStop = allStopsById[transferStopId] ?: continue
                    val route2Options = allRoutes.filter { r2 ->
                        r2.id != route1.id &&
                        r2.id in alightableRouteIds &&
                        routeStopIds[r2.id]?.contains(transferStopId) == true
                    }
                    for (route2 in route2Options) {
                        val alightingStop = destStops.firstOrNull { it.id in (routeStopIds[route2.id] ?: emptySet()) } ?: continue
                        val walkOut = haversineMeters(dest.latitude, dest.longitude, alightingStop.lat, alightingStop.lon)
                        if (walkOut > MAX_WALK_METERS) continue
                        transferCandidates.add(TransferCandidate(route1, boardingStop, transferStop, route2, alightingStop, walkIn, walkOut))
                    }
                }
            }
            Log.d("MapVM", "Transfer candidates: ${transferCandidates.size}")

            // ── Score and pick best ───────────────────────────────────────────
            // Score = walkIn + walkOut (+ penalty for transfer). Lower is better.
            data class ScoredPlan(val score: Double, val isDirect: Boolean, val direct: DirectCandidate? = null, val transfer: TransferCandidate? = null)

            val scoredPlans = mutableListOf<ScoredPlan>()
            validDirect.forEach { scoredPlans.add(ScoredPlan(it.walkIn + it.walkOut, true, direct = it)) }
            transferCandidates.forEach { scoredPlans.add(ScoredPlan(it.walkIn + it.walkOut + TRANSFER_PENALTY_M, false, transfer = it)) }

            if (scoredPlans.isEmpty()) {
                val hadCandidates = directCandidates.isNotEmpty() || transferCandidates.isNotEmpty()
                val error = if (hadCandidates)
                    "Routes exist but walking distance exceeds 12 min. Try a nearby bus stop."
                else
                    "No bus route found for this trip."
                _uiState.update { it.copy(routingError = error, isRoutingLoading = false) }
                return
            }

            val best = scoredPlans.minByOrNull { it.score }!!

            if (best.isDirect) {
                val d = best.direct!!
                Log.d("MapVM", "✅ Direct: ${d.route.shortName} | board: ${d.boardingStop.name} | alight: ${d.alightingStop.name} | walk: ${d.walkIn.toInt()}m+${d.walkOut.toInt()}m")
                val boardingArrivals  = repository.getArrivalsForStop(d.boardingStop.id).filter { it.routeId == d.route.id }
                val alightingArrivals = repository.getArrivalsForStop(d.alightingStop.id).filter { it.routeId == d.route.id }
                _uiState.update { it.copy(
                    suggestedRouteIds  = setOf(d.route.id),
                    selectedRouteIds   = setOf(d.route.id),
                    isTransferRoute    = false,
                    transferStop       = null,
                    transferRoute      = null,
                    boardingStop       = d.boardingStop,
                    alightingStop      = d.alightingStop,
                    walkInMeters       = d.walkIn,
                    walkOutMeters      = d.walkOut,
                    boardingArrivals   = boardingArrivals,
                    transferArrivals   = emptyList(),
                    alightingArrivals  = alightingArrivals,
                    isRoutingLoading   = false,
                    routingError       = null
                ) }
            } else {
                val t = best.transfer!!
                Log.d("MapVM", "✅ Transfer: ${t.route1.shortName}→${t.route2.shortName} | board: ${t.boardingStop.name} | xfer: ${t.transferStop.name} | alight: ${t.alightingStop.name}")
                val boardingArrivals  = repository.getArrivalsForStop(t.boardingStop.id).filter { it.routeId == t.route1.id }
                val transferArrivals  = repository.getArrivalsForStop(t.transferStop.id).filter { it.routeId == t.route2.id }
                val alightingArrivals = repository.getArrivalsForStop(t.alightingStop.id).filter { it.routeId == t.route2.id }
                _uiState.update { it.copy(
                    suggestedRouteIds  = setOf(t.route1.id, t.route2.id),
                    selectedRouteIds   = setOf(t.route1.id, t.route2.id),
                    isTransferRoute    = true,
                    transferStop       = t.transferStop,
                    transferRoute      = t.route2,
                    boardingStop       = t.boardingStop,
                    alightingStop      = t.alightingStop,
                    walkInMeters       = t.walkIn,
                    walkOutMeters      = t.walkOut,
                    boardingArrivals   = boardingArrivals,
                    transferArrivals   = transferArrivals,
                    alightingArrivals  = alightingArrivals,
                    isRoutingLoading   = false,
                    routingError       = null
                ) }
            }
        } catch (e: Exception) {
            Log.e("MapVM", "Routing error: ${e.message}")
            _uiState.update { it.copy(routingError = "Routing failed — please try again", isRoutingLoading = false) }
        }
    }

    // ── Clear ─────────────────────────────────────────────────────────────────

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.update { it.copy(
            searchQuery        = "",
            placeSuggestions   = emptyList(),
            isSearchFocused    = false,
            isSearchLoading    = false,
            destinationLatLng  = null,
            destinationName    = null,
            suggestedRouteIds  = emptySet(),
            selectedRouteIds   = emptySet(),
            isTransferRoute    = false,
            transferStop       = null,
            transferRoute      = null,
            walkInMeters       = null,
            walkOutMeters      = null,
            isRoutingLoading   = false,
            routingError       = null,
            boardingStop       = null,
            alightingStop      = null,
            boardingArrivals   = emptyList(),
            transferArrivals   = emptyList(),
            alightingArrivals  = emptyList()
        ) }
    }

    // ── Maths ─────────────────────────────────────────────────────────────────

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return R * 2 * atan2(sqrt(a), sqrt(1.0 - a))
    }
}

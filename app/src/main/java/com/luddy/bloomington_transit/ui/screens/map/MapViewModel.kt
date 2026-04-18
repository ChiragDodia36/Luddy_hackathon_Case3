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
import com.luddy.bloomington_transit.data.ai.AiResult
import com.luddy.bloomington_transit.data.ai.BtAiRepository
import com.luddy.bloomington_transit.data.ai.dto.PredictionDto
import com.luddy.bloomington_transit.data.ai.dto.TripPlanResponseDto
import com.luddy.bloomington_transit.domain.model.*
import com.luddy.bloomington_transit.domain.model.Reachability
import com.luddy.bloomington_transit.domain.repository.TransitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import kotlin.math.*


private const val WALK_M_PER_MIN   = 80.0
private const val MAX_WALK_METERS  = 10 * WALK_M_PER_MIN   // 800 m = 10 min hard cap per leg
private const val TRANSFER_PENALTY = 400.0                  // ≈ 5 min penalty for 1 transfer
private const val HALF_MILE_M      = 804.67                 // for stop display radius
private const val MAX_PLAN_OPTIONS = 3

fun metersToWalkMinutes(meters: Double): Int = ceil(meters / WALK_M_PER_MIN).toInt()


data class RoutePlan(
    val index: Int,
    val isTransfer: Boolean,
    val firstRoute: Route,
    val secondRoute: Route? = null,
    val boardingStop: Stop,
    val transferStop: Stop? = null,
    val alightingStop: Stop,
    val walkInMeters: Double,
    val walkOutMeters: Double,
    val routeIds: Set<String>,
    val boardingArrivals: List<Arrival> = emptyList(),
    val transferArrivals: List<Arrival> = emptyList(),
    val alightingArrivals: List<Arrival> = emptyList(),
    val aiBoardingPredictions: List<PredictionDto> = emptyList(),
) {
    val walkInMinutes: Int  get() = metersToWalkMinutes(walkInMeters)
    val walkOutMinutes: Int get() = metersToWalkMinutes(walkOutMeters)
    val nextBusMinutes: Long? get() = boardingArrivals.firstOrNull()?.minutesUntil()

    val estimatedTotalMinutes: Long
        get() {
            val walkIn  = walkInMinutes.toLong()
            val busWait = nextBusMinutes ?: 60L
            val walkOut = walkOutMinutes.toLong()
            return if (isTransfer) {
                val xferWait = transferArrivals.firstOrNull()?.minutesUntil() ?: 30L
                walkIn + busWait + xferWait + walkOut
            } else {
                walkIn + busWait + walkOut
            }
        }
}


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
    val stopsForSelectedRoutes: List<Stop> = emptyList(),
    val selectedBus: Bus? = null,
    val selectedStop: Stop? = null,
    val stopArrivals: List<Arrival> = emptyList(),
    val trackedBusIds: Set<String> = emptySet(),
    val shapesByRoute: Map<String, List<List<LatLng>>> = emptyMap(),
    val isLoadingArrivals: Boolean = false,
    val reachability: Reachability? = null,
    val isLoadingReachability: Boolean = false,
    val isInitializing: Boolean = true,
    // Search
    val searchQuery: String = "",
    val isSearchFocused: Boolean = false,
    val placeSuggestions: List<PlaceSuggestion> = emptyList(),
    val isSearchLoading: Boolean = false,
    val destinationLatLng: LatLng? = null,
    val destinationName: String? = null,
    val userLocation: LatLng? = null,
    val routePlans: List<RoutePlan> = emptyList(),
    val selectedPlanIndex: Int = 0,
    val isPlanExpanded: Boolean = true,
    val planRouteStopsByIndex: Map<Int, List<Stop>> = emptyMap(),
    val isRoutingLoading: Boolean = false,
    val routingError: String? = null,
    val aiPlan: TripPlanResponseDto? = null,
) {
    val activePlan: RoutePlan? get() = routePlans.getOrNull(selectedPlanIndex)
    val suggestedRouteIds: Set<String> get() = activePlan?.routeIds ?: emptySet()
    val activePlanStops: List<Stop> get() = planRouteStopsByIndex[selectedPlanIndex] ?: emptyList()
}


@HiltViewModel
class MapViewModel @Inject constructor(
    private val repository: TransitRepository,
    private val placesClient: PlacesClient,
    private val aiRepo: BtAiRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var routeStopsJob: Job? = null

    init {
        viewModelScope.launch {
            repository.initStaticData()
            _uiState.update { it.copy(isInitializing = false) }
            observeStaticData()
            observeTrackedBuses()
            startPolling()
        }
    }

    private fun observeStaticData() {
        viewModelScope.launch {
            repository.getRoutes().collect { routes ->
                _uiState.update { it.copy(routes = routes) }
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
                val segments = points.groupBy { it.shapeId }.values
                    .map { seg -> seg.map { LatLng(it.lat, it.lon) } }
                _uiState.update { s -> s.copy(shapesByRoute = s.shapesByRoute + (routeId to segments)) }
            }
        }
    }

    private fun observeTrackedBuses() {
        viewModelScope.launch {
            repository.getTrackedBusIds().collect { ids -> _uiState.update { it.copy(trackedBusIds = ids) } }
        }
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                val buses = repository.getLiveBuses()
                _uiState.update { it.copy(buses = buses) }
                val sel = _uiState.value.selectedRouteIds
                val visible = buses.count { it.routeId in sel }
                Log.d("MapVM", "Poll: ${buses.size} buses total, $visible visible (selectedRoutes=$sel)")
                refreshAllPlanEtas()
                delay(10_000L)
            }
        }
    }

    private suspend fun refreshAllPlanEtas() {
        val plans = _uiState.value.routePlans
        if (plans.isEmpty()) return
        val updated = plans.map { plan ->
            try {
                val boardingArrivals = repository.getArrivalsForStop(plan.boardingStop.id)
                    .filter { it.routeId == plan.firstRoute.id }
                val alightingRouteId = plan.secondRoute?.id ?: plan.firstRoute.id
                val alightingArrivals = repository.getArrivalsForStop(plan.alightingStop.id)
                    .filter { it.routeId == alightingRouteId }
                val transferArrivals = if (plan.isTransfer && plan.transferStop != null && plan.secondRoute != null)
                    repository.getArrivalsForStop(plan.transferStop.id).filter { it.routeId == plan.secondRoute.id }
                else emptyList()

                val aiPreds: List<PredictionDto> = when (val r = aiRepo.predictionsFor(plan.boardingStop.id, horizonMinutes = 60)) {
                    is AiResult.Ok -> r.value.predictions.filter { it.routeId == plan.firstRoute.id }
                    is AiResult.Err -> emptyList()
                }

                plan.copy(
                    boardingArrivals = boardingArrivals,
                    alightingArrivals = alightingArrivals,
                    transferArrivals = transferArrivals,
                    aiBoardingPredictions = aiPreds,
                )
            } catch (_: Exception) { plan }
        }
        _uiState.update { it.copy(routePlans = updated) }
    }

    private fun fetchAiPlanFor(destination: LatLng) {
        val origin = _uiState.value.userLocation ?: return
        viewModelScope.launch {
            when (val r = aiRepo.plan(
                originLat = origin.latitude, originLng = origin.longitude,
                destLat = destination.latitude, destLng = destination.longitude,
            )) {
                is AiResult.Ok  -> _uiState.update { it.copy(aiPlan = r.value) }
                is AiResult.Err -> _uiState.update { it.copy(aiPlan = null) }
            }
        }
    }


    fun toggleRoute(routeId: String) {
        _uiState.update { s ->
            val cur = s.selectedRouteIds
            s.copy(selectedRouteIds = if (routeId in cur) cur - routeId else cur + routeId, routePlans = emptyList())
        }
        loadStopsForSelectedRoutes(_uiState.value.selectedRouteIds)
    }

    fun toggleAllRoutes() {
        _uiState.update { s ->
            val all = s.routes.map { it.id }.toSet()
            s.copy(selectedRouteIds = if (s.selectedRouteIds.size == all.size) emptySet() else all, routePlans = emptyList())
        }
        loadStopsForSelectedRoutes(_uiState.value.selectedRouteIds)
    }

    fun selectOnlyRoute(routeId: String) {
        _uiState.update { it.copy(selectedRouteIds = setOf(routeId)) }
        loadStopsForSelectedRoutes(setOf(routeId))
    }

    private fun loadStopsForSelectedRoutes(routeIds: Set<String>) {
        routeStopsJob?.cancel()
        if (routeIds.isEmpty()) {
            _uiState.update { it.copy(stopsForSelectedRoutes = emptyList()) }
            return
        }
        routeStopsJob = viewModelScope.launch {
            try {
                val stops = routeIds.flatMap { routeId ->
                    repository.getStopsForRoute(routeId).first()
                }.distinctBy { it.id }
                _uiState.update { it.copy(stopsForSelectedRoutes = stops) }
            } catch (_: Exception) {}
        }
    }

    fun selectBus(bus: Bus)  { _uiState.update { it.copy(selectedBus = bus, selectedStop = null) } }
    fun selectStop(stop: Stop) {
        _uiState.update {
            it.copy(
                selectedStop = stop, selectedBus = null,
                isLoadingArrivals = true, reachability = null, isLoadingReachability = false
            )
        }
        viewModelScope.launch {
            val arrivals = repository.getArrivalsForStop(stop.id)
            _uiState.update { it.copy(stopArrivals = arrivals, isLoadingArrivals = false) }
            // Load reachability if we already have user location
            val loc = _uiState.value.userLocation
            if (loc != null && arrivals.isNotEmpty()) {
                _uiState.update { it.copy(isLoadingReachability = true) }
                val reach = repository.getReachability(loc.latitude, loc.longitude, stop, arrivals)
                _uiState.update { it.copy(reachability = reach, isLoadingReachability = false) }
            }
        }
    }
    fun dismissBottomSheet() {
        _uiState.update { it.copy(selectedBus = null, selectedStop = null, reachability = null) }
    }
    fun trackBus(vehicleId: String)   { viewModelScope.launch { repository.addTrackedBus(vehicleId) } }
    fun untrackBus(vehicleId: String) { viewModelScope.launch { repository.removeTrackedBus(vehicleId) } }


    fun updateUserLocation(lat: Double, lon: Double) {
        _uiState.update { it.copy(userLocation = LatLng(lat, lon)) }
        Log.d("MapVM", "Location → $lat, $lon")
    }


    fun onSearchFocusChanged(focused: Boolean) { _uiState.update { it.copy(isSearchFocused = focused) } }

    fun searchPlaces(query: String) {
        _uiState.update { it.copy(searchQuery = query, isSearchLoading = query.isNotBlank()) }
        searchJob?.cancel()
        if (query.isBlank()) { _uiState.update { it.copy(placeSuggestions = emptyList(), isSearchLoading = false) }; return }
        searchJob = viewModelScope.launch {
            delay(300L)
            try {
                val req = FindAutocompletePredictionsRequest.builder()
                    .setQuery(query)
                    .setLocationBias(RectangularBounds.newInstance(LatLng(39.10, -86.60), LatLng(39.22, -86.45)))
                    .setCountries(listOf("US"))
                    .build()
                val resp = placesClient.findAutocompletePredictions(req).await()
                val suggestions = resp.autocompletePredictions.map {
                    PlaceSuggestion(it.placeId, it.getPrimaryText(null).toString(), it.getSecondaryText(null).toString())
                }
                _uiState.update { it.copy(placeSuggestions = suggestions, isSearchLoading = false) }
            } catch (e: Exception) {
                Log.e("MapVM", "Places: ${e.message}")
                _uiState.update { it.copy(placeSuggestions = emptyList(), isSearchLoading = false) }
            }
        }
    }

    fun selectPlace(suggestion: PlaceSuggestion) {
        _uiState.update { it.copy(
            searchQuery = suggestion.primaryText, placeSuggestions = emptyList(),
            isSearchFocused = false, isRoutingLoading = true, routingError = null,
            routePlans = emptyList(), selectedPlanIndex = 0, selectedRouteIds = emptySet()
        ) }
        viewModelScope.launch {
            try {
                val req = FetchPlaceRequest.newInstance(suggestion.placeId, listOf(Place.Field.NAME, Place.Field.LAT_LNG))
                val resp = placesClient.fetchPlace(req).await()
                val latLng = resp.place.latLng ?: run {
                    _uiState.update { it.copy(routingError = "Could not get coordinates for this place", isRoutingLoading = false) }; return@launch
                }
                val name = resp.place.name ?: suggestion.primaryText
                _uiState.update { it.copy(destinationLatLng = latLng, destinationName = name) }
                computeRoutePlans(latLng)
            } catch (e: Exception) {
                Log.e("MapVM", "FetchPlace: ${e.message}")
                _uiState.update { it.copy(routingError = "Could not fetch place details", isRoutingLoading = false) }
            }
        }
    }


    fun selectPlan(index: Int) {
        val plan = _uiState.value.routePlans.getOrNull(index) ?: return
        _uiState.update { it.copy(selectedPlanIndex = index, selectedRouteIds = plan.routeIds, isPlanExpanded = false) }
    }

    fun expandPlans() { _uiState.update { it.copy(isPlanExpanded = true) } }


    private suspend fun computeRoutePlans(dest: LatLng) {
        val userLoc = _uiState.value.userLocation ?: run {
            _uiState.update { it.copy(routingError = "Enable location permission to find a route", isRoutingLoading = false) }; return
        }
        try {
            val destStops = repository.getNearestStops(dest.latitude, dest.longitude, 2000.0)
            val userStops = repository.getNearestStops(userLoc.latitude, userLoc.longitude, 1500.0)

            if (destStops.isEmpty()) { _uiState.update { it.copy(routingError = "No bus stops near the destination", isRoutingLoading = false) }; return }
            if (userStops.isEmpty()) { _uiState.update { it.copy(routingError = "No bus stops near your location", isRoutingLoading = false) }; return }

            val userStopIds = userStops.map { it.id }.toSet()
            val destStopIds  = destStops.map { it.id }.toSet()
            val allRoutes    = _uiState.value.routes

            // Build route→stops map once (1 DB call per route)
            val routeStopIds  = mutableMapOf<String, Set<String>>()
            val routeStopObjs = mutableMapOf<String, List<Stop>>()
            for (route in allRoutes) {
                val s = repository.getStopsForRoute(route.id).first()
                routeStopIds[route.id]  = s.map { it.id }.toSet()
                routeStopObjs[route.id] = s
            }
            val allStopsById = _uiState.value.stops.associateBy { it.id }


            // Direct
            data class RawDirect(val route: Route, val boarding: Stop, val alighting: Stop, val walkIn: Double, val walkOut: Double)
            val rawDirect = mutableListOf<RawDirect>()
            for (route in allRoutes) {
                val rIds = routeStopIds[route.id] ?: continue
                val bo = userStops.firstOrNull { it.id in rIds } ?: continue
                val al = destStops.firstOrNull  { it.id in rIds } ?: continue
                val wi = haversineM(userLoc.latitude, userLoc.longitude, bo.lat, bo.lon)
                val wo = haversineM(dest.latitude, dest.longitude, al.lat, al.lon)
                if (wi <= MAX_WALK_METERS && wo <= MAX_WALK_METERS) rawDirect.add(RawDirect(route, bo, al, wi, wo))
            }

            // Transfer
            data class RawTransfer(val r1: Route, val boarding: Stop, val transfer: Stop, val r2: Route, val alighting: Stop, val walkIn: Double, val walkOut: Double)
            val rawTransfer = mutableListOf<RawTransfer>()

            val boardableRoutes   = allRoutes.filter { routeStopIds[it.id]?.any { id -> id in userStopIds } == true }
            val alightableRouteIds = allRoutes.filter { routeStopIds[it.id]?.any { id -> id in destStopIds } == true }.map { it.id }.toSet()

            for (r1 in boardableRoutes) {
                val r1Ids = routeStopIds[r1.id] ?: continue
                val boarding = userStops.firstOrNull { it.id in r1Ids } ?: continue
                val wi = haversineM(userLoc.latitude, userLoc.longitude, boarding.lat, boarding.lon)
                if (wi > MAX_WALK_METERS) continue
                for (xferStopId in r1Ids) {
                    val xferStop = allStopsById[xferStopId] ?: continue
                    for (r2 in allRoutes.filter { it.id != r1.id && it.id in alightableRouteIds && routeStopIds[it.id]?.contains(xferStopId) == true }) {
                        val alighting = destStops.firstOrNull { it.id in (routeStopIds[r2.id] ?: emptySet()) } ?: continue
                        val wo = haversineM(dest.latitude, dest.longitude, alighting.lat, alighting.lon)
                        if (wo > MAX_WALK_METERS) continue
                        rawTransfer.add(RawTransfer(r1, boarding, xferStop, r2, alighting, wi, wo))
                    }
                }
            }

            Log.d("MapVM", "Raw: ${rawDirect.size} direct, ${rawTransfer.size} transfer candidates")

            val hadAnyCandidates = rawDirect.isNotEmpty() || rawTransfer.isNotEmpty()
            if (!hadAnyCandidates) {
                // Widen search didn't help — truly no route
                _uiState.update { it.copy(routingError = "No bus route found for this trip", isRoutingLoading = false) }
                return
            }


            val directByRoute   = rawDirect.groupBy { it.route.id }
                .mapValues { (_, v) -> v.minByOrNull { it.walkIn + it.walkOut }!! }
            val transferByCombo = rawTransfer.groupBy { "${it.r1.id}+${it.r2.id}" }
                .mapValues { (_, v) -> v.minByOrNull { it.walkIn + it.walkOut }!! }


            // Score without arrivals first, take top 6 to fetch arrivals for
            data class Scored(val score: Double, val direct: RawDirect? = null, val transfer: RawTransfer? = null)
            val prescored = (
                directByRoute.values.map { Scored(it.walkIn + it.walkOut, direct = it) } +
                transferByCombo.values.map { Scored(it.walkIn + it.walkOut + TRANSFER_PENALTY, transfer = it) }
            ).sortedBy { it.score }.take(6)

            // Build RoutePlan objects with arrivals
            val plansWithArrivals = prescored.mapIndexed { i, scored ->
                if (scored.direct != null) {
                    val d = scored.direct
                    val boardingArrivals  = repository.getArrivalsForStop(d.boarding.id).filter { it.routeId == d.route.id }
                    val alightingArrivals = repository.getArrivalsForStop(d.alighting.id).filter { it.routeId == d.route.id }
                    RoutePlan(
                        index = i, isTransfer = false, firstRoute = d.route,
                        boardingStop = d.boarding, alightingStop = d.alighting,
                        walkInMeters = d.walkIn, walkOutMeters = d.walkOut,
                        routeIds = setOf(d.route.id),
                        boardingArrivals = boardingArrivals, alightingArrivals = alightingArrivals
                    )
                } else {
                    val t = scored.transfer!!
                    val boardingArrivals  = repository.getArrivalsForStop(t.boarding.id).filter { it.routeId == t.r1.id }
                    val transferArrivals  = repository.getArrivalsForStop(t.transfer.id).filter { it.routeId == t.r2.id }
                    val alightingArrivals = repository.getArrivalsForStop(t.alighting.id).filter { it.routeId == t.r2.id }
                    RoutePlan(
                        index = i, isTransfer = true, firstRoute = t.r1, secondRoute = t.r2,
                        boardingStop = t.boarding, transferStop = t.transfer, alightingStop = t.alighting,
                        walkInMeters = t.walkIn, walkOutMeters = t.walkOut,
                        routeIds = setOf(t.r1.id, t.r2.id),
                        boardingArrivals = boardingArrivals, transferArrivals = transferArrivals, alightingArrivals = alightingArrivals
                    )
                }
            }


            val sorted = plansWithArrivals
                .sortedBy { it.estimatedTotalMinutes }
                .take(MAX_PLAN_OPTIONS)
                .mapIndexed { i, p -> p.copy(index = i) }  // re-index after sort

            if (sorted.isEmpty()) {
                _uiState.update { it.copy(routingError = "Walking distance exceeds 10 min per leg. Try a closer location.", isRoutingLoading = false) }
                return
            }

            Log.d("MapVM", "✅ ${sorted.size} plans: ${sorted.map { "${if (it.isTransfer) "${it.firstRoute.shortName}→${it.secondRoute?.shortName}" else it.firstRoute.shortName} ~${it.estimatedTotalMinutes}min" }}")

            // Build per-plan stop lists so the map only shows stops for the active plan
            val planRouteStopsByIndex = sorted.associate { plan ->
                val stops = mutableListOf<Stop>()
                stops += routeStopObjs[plan.firstRoute.id] ?: emptyList()
                plan.secondRoute?.let { stops += routeStopObjs[it.id] ?: emptyList() }
                plan.index to stops.distinctBy { it.id }
            }

            _uiState.update { it.copy(
                routePlans             = sorted,
                selectedPlanIndex      = 0,
                selectedRouteIds       = sorted.first().routeIds,
                planRouteStopsByIndex  = planRouteStopsByIndex,
                isRoutingLoading       = false,
                routingError           = null
            ) }

            // Kick off AI enrichment immediately so UI doesn't wait for the 10s poll.
            viewModelScope.launch { refreshAllPlanEtas() }
            // One-shot /plan call against our backend for the optional Google-side companion.
            _uiState.value.destinationLatLng?.let { fetchAiPlanFor(it) }
        } catch (e: Exception) {
            Log.e("MapVM", "Routing error: ${e.message}")
            _uiState.update { it.copy(routingError = "Routing failed — please try again", isRoutingLoading = false) }
        }
    }


    fun clearSearch() {
        searchJob?.cancel()
        _uiState.update { it.copy(
            searchQuery = "", placeSuggestions = emptyList(), isSearchFocused = false,
            isSearchLoading = false, destinationLatLng = null, destinationName = null,
            routePlans = emptyList(), selectedPlanIndex = 0, isPlanExpanded = true,
            planRouteStopsByIndex = emptyMap(), selectedRouteIds = emptySet(),
            isRoutingLoading = false, routingError = null,
            aiPlan = null,
        ) }
    }


    private fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return R * 2 * atan2(sqrt(a), sqrt(1.0 - a))
    }
}

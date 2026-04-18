package com.luddy.bloomington_transit.ui.screens.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.luddy.bloomington_transit.domain.model.*
import com.luddy.bloomington_transit.domain.repository.TransitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MapUiState(
    val buses: List<Bus> = emptyList(),
    val routes: List<Route> = emptyList(),
    val stops: List<Stop> = emptyList(),
    val selectedRouteIds: Set<String> = emptySet(),
    val selectedBus: Bus? = null,
    val selectedStop: Stop? = null,
    val stopArrivals: List<Arrival> = emptyList(),
    val trackedBusIds: Set<String> = emptySet(),
    // routeId -> list of segments (each segment is one shape_id's ordered points)
    val shapesByRoute: Map<String, List<List<LatLng>>> = emptyMap(),
    val isLoadingArrivals: Boolean = false,
    val isInitializing: Boolean = true
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val repository: TransitRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.initStaticData()  // fast (mutex skips if HomeViewModel already ran it)
            _uiState.update { it.copy(isInitializing = false) }
            observeStaticData()
            observeTrackedBuses()
            startPolling()
        }
    }

    private fun observeStaticData() {
        viewModelScope.launch {
            repository.getRoutes().collect { routes ->
                // Keep existing selection (set by selectOnlyRoute or toggleAllRoutes); don't auto-select all
                _uiState.update { state ->
                    state.copy(routes = routes)
                }
                // Pre-load shapes for all routes (so they're ready when selected)
                routes.forEach { route -> loadShapeForRoute(route.id) }
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
                // Group by shapeId so each segment (e.g. outbound/inbound) is a separate polyline
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
                delay(10_000L)
            }
        }
    }

    fun toggleRoute(routeId: String) {
        _uiState.update { state ->
            val current = state.selectedRouteIds
            state.copy(
                selectedRouteIds = if (routeId in current) current - routeId else current + routeId
            )
        }
    }

    fun toggleAllRoutes() {
        _uiState.update { state ->
            val allIds = state.routes.map { it.id }.toSet()
            state.copy(
                selectedRouteIds = if (state.selectedRouteIds.size == allIds.size) emptySet() else allIds
            )
        }
    }

    private var initialRouteApplied = false

    fun selectOnlyRoute(routeId: String) {
        if (initialRouteApplied) return
        initialRouteApplied = true
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

    fun trackBus(vehicleId: String) {
        viewModelScope.launch { repository.addTrackedBus(vehicleId) }
    }

    fun untrackBus(vehicleId: String) {
        viewModelScope.launch { repository.removeTrackedBus(vehicleId) }
    }
}

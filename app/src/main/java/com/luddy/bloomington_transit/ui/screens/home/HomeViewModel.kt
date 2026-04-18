package com.luddy.bloomington_transit.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luddy.bloomington_transit.data.ai.AiResult
import com.luddy.bloomington_transit.data.ai.BtAiRepository
import com.luddy.bloomington_transit.data.ai.dto.StatsResponseDto
import com.luddy.bloomington_transit.domain.model.*
import com.luddy.bloomington_transit.domain.repository.TransitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

enum class TimeWindow { MORNING, DAY, EVENING, NIGHT }

data class PinnedRouteData(
    val route: Route,
    val nearestStop: Stop?,
    val arrivals: List<Arrival>   // filtered to this route only, capped at 3
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val routes: List<Route> = emptyList(),
    val pinnedRouteIds: Set<String> = emptySet(),
    val pinnedRoutes: List<PinnedRouteData> = emptyList(),
    val serviceAlerts: List<ServiceAlert> = emptyList(),
    val errorMessage: String? = null,
    val timeWindow: TimeWindow = TimeWindow.DAY,
    val hasLocation: Boolean = false,
    val favouriteRouteArrivals: List<Arrival> = emptyList(),
    val nearestStop: Stop? = null,
    val nearestStopArrivals: List<Arrival> = emptyList(),
    val isNearSavedStop: Boolean = false,
    val stats: StatsResponseDto? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: TransitRepository,
    private val aiRepo: BtAiRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var userLat: Double? = null
    private var userLon: Double? = null

    init {
        _uiState.update { it.copy(timeWindow = currentTimeWindow()) }
        viewModelScope.launch {
            repository.initStaticData()
            observeData()
        }
        viewModelScope.launch {
            when (val r = aiRepo.stats()) {
                is AiResult.Ok  -> _uiState.update { it.copy(stats = r.value) }
                is AiResult.Err -> Unit
            }
        }
    }

    private fun currentTimeWindow(): TimeWindow {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour in 6..8   -> TimeWindow.MORNING
            hour in 9..15  -> TimeWindow.DAY
            hour in 16..18 -> TimeWindow.EVENING
            else           -> TimeWindow.NIGHT
        }
    }

    private fun observeData() {
        // Observe routes + pinned IDs together so cards update when either changes
        viewModelScope.launch {
            combine(
                repository.getRoutes(),
                repository.getPinnedRouteIds()
            ) { routes, pinnedIds -> routes to pinnedIds }
                .collect { (routes, pinnedIds) ->
                    _uiState.update {
                        it.copy(isLoading = false, routes = routes, pinnedRouteIds = pinnedIds)
                    }
                    refreshPinnedRoutes(routes, pinnedIds)
                }
        }
        // Poll service alerts every 30s
        viewModelScope.launch {
            while (true) {
                try {
                    val alerts = repository.getServiceAlerts()
                    _uiState.update { it.copy(serviceAlerts = alerts) }
                } catch (_: Exception) {}
                kotlinx.coroutines.delay(30_000L)
            }
        }
        // Refresh pinned route arrivals every 10s
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(10_000L)
                val state = _uiState.value
                refreshPinnedRoutes(state.routes, state.pinnedRouteIds)
            }
        }
    }

    private suspend fun refreshPinnedRoutes(routes: List<Route>, pinnedIds: Set<String>) {
        val lat = userLat ?: return
        val lon = userLon ?: return
        if (pinnedIds.isEmpty()) {
            _uiState.update { it.copy(pinnedRoutes = emptyList()) }
            return
        }

        val pinnedData = pinnedIds.mapNotNull { routeId ->
            val route = routes.find { it.id == routeId } ?: return@mapNotNull null
            try {
                // Find the stop on this route closest to the user
                val stopsForRoute = repository.getStopsForRoute(routeId).first()
                val nearestStop = stopsForRoute.minByOrNull { stop ->
                    val dLat = stop.lat - lat
                    val dLon = stop.lon - lon
                    dLat * dLat + dLon * dLon
                }
                // Get arrivals at that stop, filter to this route only
                val arrivals = nearestStop
                    ?.let {
                        repository.getArrivalsForStop(it.id)
                            .filter { a -> a.routeId == routeId }
                            .take(3)
                    }
                    ?: emptyList()
                PinnedRouteData(route, nearestStop, arrivals)
            } catch (_: Exception) {
                PinnedRouteData(route, null, emptyList())
            }
        }
        _uiState.update { it.copy(pinnedRoutes = pinnedData) }
    }

    fun updateLocation(lat: Double, lon: Double) {
        userLat = lat
        userLon = lon
        _uiState.update { it.copy(hasLocation = true) }
        viewModelScope.launch {
            val state = _uiState.value
            refreshPinnedRoutes(state.routes, state.pinnedRouteIds)
        }
    }

    fun addPin(routeId: String) {
        viewModelScope.launch { repository.addPinnedRoute(routeId) }
    }

    fun removePin(routeId: String) {
        viewModelScope.launch { repository.removePinnedRoute(routeId) }
    }
}

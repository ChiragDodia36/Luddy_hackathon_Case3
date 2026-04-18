package com.luddy.bloomington_transit.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luddy.bloomington_transit.domain.model.*
import com.luddy.bloomington_transit.domain.repository.TransitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

enum class TimeWindow { MORNING, DAY, EVENING, NIGHT }

data class HomeUiState(
    val isLoading: Boolean = true,
    val routes: List<Route> = emptyList(),
    val favouriteRouteId: String? = null,
    val serviceAlerts: List<ServiceAlert> = emptyList(),
    val errorMessage: String? = null,
    val timeWindow: TimeWindow = TimeWindow.DAY,
    val favouriteRouteArrivals: List<Arrival> = emptyList(),
    val nearestStop: Stop? = null,
    val nearestStopArrivals: List<Arrival> = emptyList(),
    val isNearSavedStop: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: TransitRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(timeWindow = currentTimeWindow()) }
        viewModelScope.launch {
            repository.initStaticData()
            observeData()
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
        viewModelScope.launch {
            combine(
                repository.getRoutes(),
                repository.getFavouriteRouteId()
            ) { routes, favRouteId -> routes to favRouteId }
                .collect { (routes, favRouteId) ->
                    _uiState.update {
                        it.copy(isLoading = false, routes = routes, favouriteRouteId = favRouteId)
                    }
                    favRouteId?.let { loadFavouriteRouteArrivals(it) }
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
        // Refresh fav route arrivals every 10s
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(10_000L)
                _uiState.value.favouriteRouteId?.let { loadFavouriteRouteArrivals(it) }
            }
        }
    }

    fun updateLocation(lat: Double, lon: Double) {
        viewModelScope.launch {
            try {
                val nearestStops = repository.getNearestStops(lat, lon, radiusMeters = 400.0)
                val nearestStop = nearestStops.firstOrNull()
                val arrivals = nearestStop
                    ?.let { repository.getArrivalsForStop(it.id).take(3) }
                    ?: emptyList()
                val savedIds = repository.getFavouriteStopIds().first()
                val isNearSaved = nearestStops.any { it.id in savedIds }
                _uiState.update {
                    it.copy(
                        nearestStop = nearestStop,
                        nearestStopArrivals = arrivals,
                        isNearSavedStop = isNearSaved
                    )
                }
                _uiState.value.favouriteRouteId?.let { loadFavouriteRouteArrivals(it) }
            } catch (_: Exception) {}
        }
    }

    private fun loadFavouriteRouteArrivals(routeId: String) {
        viewModelScope.launch {
            try {
                val nearestStop = _uiState.value.nearestStop
                val stopsForRoute = repository.getStopsForRoute(routeId).first()
                val targetStop = if (nearestStop != null && stopsForRoute.any { it.id == nearestStop.id }) {
                    nearestStop
                } else {
                    stopsForRoute.firstOrNull()
                }
                val arrivals = targetStop
                    ?.let { repository.getArrivalsForRoute(routeId, it.id).take(2) }
                    ?: emptyList()
                _uiState.update { it.copy(favouriteRouteArrivals = arrivals) }
            } catch (_: Exception) {}
        }
    }

    fun setFavouriteRoute(routeId: String?) {
        viewModelScope.launch { repository.setFavouriteRouteId(routeId) }
    }
}

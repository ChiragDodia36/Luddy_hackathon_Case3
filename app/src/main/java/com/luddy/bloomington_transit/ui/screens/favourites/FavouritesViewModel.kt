package com.luddy.bloomington_transit.ui.screens.favourites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luddy.bloomington_transit.domain.model.Arrival
import com.luddy.bloomington_transit.domain.model.Route
import com.luddy.bloomington_transit.domain.model.Stop
import com.luddy.bloomington_transit.domain.repository.TransitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FavouriteStopData(
    val stop: Stop,
    val arrivals: List<Arrival>
)

data class FavouritesUiState(
    val favourites: List<FavouriteStopData> = emptyList(),
    val isLoading: Boolean = true,
    val favouriteRoute: Route? = null,
    val favouriteRouteArrivals: List<Arrival> = emptyList()
)

@HiltViewModel
class FavouritesViewModel @Inject constructor(
    private val repository: TransitRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavouritesUiState())
    val uiState: StateFlow<FavouritesUiState> = _uiState.asStateFlow()

    init {
        observeFavourites()
        observeFavouriteRoute()
        startPolling()
    }

    private fun observeFavourites() {
        viewModelScope.launch {
            repository.getFavouriteStopIds().collect { ids ->
                loadFavourites(ids)
            }
        }
    }

    private fun observeFavouriteRoute() {
        viewModelScope.launch {
            combine(
                repository.getFavouriteRouteId(),
                repository.getRoutes()
            ) { routeId, routes -> routeId to routes }
                .collect { (routeId, routes) ->
                    val route = routes.find { it.id == routeId }
                    _uiState.update { it.copy(favouriteRoute = route) }
                    route?.let { loadFavouriteRouteArrivals(it.id) }
                }
        }
    }

    private suspend fun loadFavourites(ids: Set<String>) {
        val data = ids.mapNotNull { stopId ->
            val stop = repository.searchStops(stopId).firstOrNull() ?: return@mapNotNull null
            val arrivals = repository.getArrivalsForStop(stopId).take(2)
            FavouriteStopData(stop, arrivals)
        }
        _uiState.update { it.copy(favourites = data, isLoading = false) }
    }

    private fun loadFavouriteRouteArrivals(routeId: String) {
        viewModelScope.launch {
            try {
                val stops = repository.getStopsForRoute(routeId).first()
                val arrivals = stops.firstOrNull()
                    ?.let { repository.getArrivalsForRoute(routeId, it.id).take(2) }
                    ?: emptyList()
                _uiState.update { it.copy(favouriteRouteArrivals = arrivals) }
            } catch (_: Exception) {}
        }
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                delay(10_000L)
                val ids = repository.getFavouriteStopIds().first()
                loadFavourites(ids)
                _uiState.value.favouriteRoute?.let { loadFavouriteRouteArrivals(it.id) }
            }
        }
    }

    fun removeFavourite(stopId: String) {
        viewModelScope.launch { repository.removeFavouriteStop(stopId) }
    }
}

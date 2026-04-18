package com.luddy.bloomington_transit.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luddy.bloomington_transit.domain.model.*
import com.luddy.bloomington_transit.domain.repository.TransitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val routes: List<Route> = emptyList(),
    val favouriteRouteId: String? = null,
    val serviceAlerts: List<ServiceAlert> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: TransitRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.initStaticData()
            observeData()
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
                        it.copy(
                            isLoading = false,
                            routes = routes,
                            favouriteRouteId = favRouteId
                        )
                    }
                }
        }
        viewModelScope.launch {
            while (true) {
                try {
                    val alerts = repository.getServiceAlerts()
                    _uiState.update { it.copy(serviceAlerts = alerts) }
                } catch (_: Exception) {}
                kotlinx.coroutines.delay(30_000L)
            }
        }
    }

    fun setFavouriteRoute(routeId: String?) {
        viewModelScope.launch { repository.setFavouriteRouteId(routeId) }
    }
}

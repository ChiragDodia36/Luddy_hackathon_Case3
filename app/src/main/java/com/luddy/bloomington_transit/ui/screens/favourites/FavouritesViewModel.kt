package com.luddy.bloomington_transit.ui.screens.favourites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luddy.bloomington_transit.domain.model.Arrival
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
    val isLoading: Boolean = true
)

@HiltViewModel
class FavouritesViewModel @Inject constructor(
    private val repository: TransitRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavouritesUiState())
    val uiState: StateFlow<FavouritesUiState> = _uiState.asStateFlow()

    init {
        observeFavourites()
        startPolling()
    }

    private fun observeFavourites() {
        viewModelScope.launch {
            repository.getFavouriteStopIds().collect { ids ->
                loadFavourites(ids)
            }
        }
    }

    private suspend fun loadFavourites(ids: Set<String>) {
        val data = ids.mapNotNull { stopId ->
            val stop = repository.searchStops(stopId).firstOrNull() ?: return@mapNotNull null
            val arrivals = repository.getArrivalsForStop(stopId).take(3)
            FavouriteStopData(stop, arrivals)
        }
        _uiState.update { it.copy(favourites = data, isLoading = false) }
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                delay(10_000L)
                val ids = repository.getFavouriteStopIds().first()
                loadFavourites(ids)
            }
        }
    }

    fun removeFavourite(stopId: String) {
        viewModelScope.launch { repository.removeFavouriteStop(stopId) }
    }
}

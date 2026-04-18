package com.luddy.bloomington_transit.ui.screens.schedule

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

data class ScheduleUiState(
    val searchQuery: String = "",
    val searchResults: List<Stop> = emptyList(),
    val selectedStop: Stop? = null,
    val arrivals: List<Arrival> = emptyList(),
    val showRealtimeOnly: Boolean = false,
    val isLoading: Boolean = false,
    val recentStops: List<Stop> = emptyList()
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repository: TransitRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    init {
        observeRecentStops()
        startPolling()
    }

    private fun observeRecentStops() {
        viewModelScope.launch {
            repository.getRecentStopIds().collect { ids ->
                val stops = ids.mapNotNull { id ->
                    repository.searchStops(id).firstOrNull { it.id == id }
                }
                _uiState.update { it.copy(recentStops = stops) }
            }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        viewModelScope.launch {
            val results = if (query.length >= 2) repository.searchStops(query) else emptyList()
            _uiState.update { it.copy(searchResults = results) }
        }
    }

    fun selectStop(stop: Stop) {
        _uiState.update {
            it.copy(
                selectedStop = stop,
                searchQuery = stop.name,
                searchResults = emptyList(),
                isLoading = true
            )
        }
        viewModelScope.launch { repository.addRecentStop(stop.id) }
        loadArrivals(stop.id)
    }

    fun useNearestStop(lat: Double, lon: Double) {
        viewModelScope.launch {
            val stops = repository.getNearestStops(lat, lon, radiusMeters = 500.0)
            stops.firstOrNull()?.let { selectStop(it) }
        }
    }

    fun toggleRealtimeFilter() {
        _uiState.update { it.copy(showRealtimeOnly = !it.showRealtimeOnly) }
    }

    private fun loadArrivals(stopId: String) {
        viewModelScope.launch {
            val arrivals = repository.getArrivalsForStop(stopId)
            _uiState.update { it.copy(arrivals = arrivals, isLoading = false) }
        }
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                delay(10_000L)
                _uiState.value.selectedStop?.id?.let { loadArrivals(it) }
            }
        }
    }
}

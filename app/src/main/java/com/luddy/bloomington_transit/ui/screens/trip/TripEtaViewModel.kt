package com.luddy.bloomington_transit.ui.screens.trip

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luddy.bloomington_transit.data.ai.AiResult
import com.luddy.bloomington_transit.data.ai.BtAiRepository
import com.luddy.bloomington_transit.data.ai.dto.TripEtaTrajectoryResponseDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TripEtaUiState(
    val tripId: String,
    val trajectory: TripEtaTrajectoryResponseDto? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

@HiltViewModel
class TripEtaViewModel @Inject constructor(
    private val repo: BtAiRepository,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val tripId: String = savedState.get<String>("tripId").orEmpty()

    private val _uiState = MutableStateFlow(TripEtaUiState(tripId = tripId))
    val uiState: StateFlow<TripEtaUiState> = _uiState.asStateFlow()

    private var job: Job? = null

    fun start() {
        if (tripId.isBlank()) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "missing tripId") }
            return
        }
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            while (true) {
                when (val r = repo.tripEta(tripId)) {
                    is AiResult.Ok -> _uiState.update {
                        it.copy(trajectory = r.value, isLoading = false, errorMessage = null)
                    }
                    is AiResult.Err -> _uiState.update {
                        it.copy(isLoading = false, errorMessage = r.message)
                    }
                }
                delay(10_000L)
            }
        }
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }
}

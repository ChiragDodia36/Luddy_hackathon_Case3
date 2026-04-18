package com.luddy.bloomington_transit.ui.screens.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luddy.bloomington_transit.data.ai.AiResult
import com.luddy.bloomington_transit.data.ai.BtAiRepository
import com.luddy.bloomington_transit.data.ai.dto.NlqResponseDto
import com.luddy.bloomington_transit.data.ai.dto.PredictionsResponseDto
import com.luddy.bloomington_transit.data.ai.dto.StopDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiStopUiState(
    val searchQuery: String = "",
    val searchResults: List<StopDto> = emptyList(),
    val selectedStop: StopDto? = null,
    val predictions: PredictionsResponseDto? = null,
    val nlqResult: NlqResponseDto? = null,
    val isSearching: Boolean = false,
    val isLoadingPredictions: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class AiStopViewModel @Inject constructor(
    private val repo: BtAiRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiStopUiState())
    val uiState: StateFlow<AiStopUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.length < 2) {
            _uiState.update { it.copy(searchResults = emptyList(), nlqResult = null) }
            return
        }
        _uiState.update { it.copy(isSearching = true) }
        viewModelScope.launch {
            // Fire stop-name search and NL intent in parallel; both are cheap.
            val stopJob = launch {
                when (val r = repo.searchStops(query)) {
                    is AiResult.Ok -> _uiState.update {
                        it.copy(searchResults = r.value.take(12), isSearching = false, errorMessage = null)
                    }
                    is AiResult.Err -> _uiState.update {
                        it.copy(isSearching = false, errorMessage = r.message)
                    }
                }
            }
            val nlqJob = launch {
                when (val r = repo.nlq(query)) {
                    is AiResult.Ok -> _uiState.update {
                        if (r.value.intent != "unknown") it.copy(nlqResult = r.value)
                        else it.copy(nlqResult = null)
                    }
                    is AiResult.Err -> _uiState.update { it.copy(nlqResult = null) }
                }
            }
            stopJob.join(); nlqJob.join()
        }
    }

    fun clearNlq() {
        _uiState.update { it.copy(nlqResult = null) }
    }

    fun selectStop(stop: StopDto) {
        pollJob?.cancel()
        _uiState.update {
            it.copy(
                selectedStop = stop,
                searchQuery = stop.name,
                searchResults = emptyList(),
                predictions = null,
                isLoadingPredictions = true,
                errorMessage = null,
            )
        }
        pollJob = viewModelScope.launch {
            while (true) {
                when (val r = repo.predictionsFor(stop.stopId, horizonMinutes = 60)) {
                    is AiResult.Ok -> _uiState.update {
                        it.copy(predictions = r.value, isLoadingPredictions = false, errorMessage = null)
                    }
                    is AiResult.Err -> _uiState.update {
                        it.copy(isLoadingPredictions = false, errorMessage = r.message)
                    }
                }
                delay(10_000L) // match feed cadence
            }
        }
    }

    fun clearSelection() {
        pollJob?.cancel()
        _uiState.update {
            it.copy(selectedStop = null, predictions = null, searchQuery = "", searchResults = emptyList())
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }
}

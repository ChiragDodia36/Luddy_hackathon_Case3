package com.luddy.bloomington_transit.ui.screens.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luddy.bloomington_transit.data.ai.AiResult
import com.luddy.bloomington_transit.data.ai.BtAiRepository
import com.luddy.bloomington_transit.data.ai.dto.HealthResponseDto
import com.luddy.bloomington_transit.data.ai.dto.StatsResponseDto
import com.luddy.bloomington_transit.data.ai.dto.VehicleDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiagnosticsUiState(
    val health: HealthResponseDto? = null,
    val stats: StatsResponseDto? = null,
    val vehicles: List<VehicleDto> = emptyList(),
    val lastUpdatedEpochMs: Long = 0L,
    val errorMessage: String? = null,
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val repo: BtAiRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiagnosticsUiState())
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            while (true) {
                val h = repo.healthz()
                val s = repo.stats()
                val v = repo.vehicles()

                val err = listOfNotNull(
                    (h as? AiResult.Err)?.message,
                    (s as? AiResult.Err)?.message,
                    (v as? AiResult.Err)?.message,
                ).firstOrNull()

                _uiState.update {
                    it.copy(
                        health = (h as? AiResult.Ok)?.value ?: it.health,
                        stats = (s as? AiResult.Ok)?.value ?: it.stats,
                        vehicles = (v as? AiResult.Ok)?.value ?: it.vehicles,
                        lastUpdatedEpochMs = System.currentTimeMillis(),
                        errorMessage = err,
                    )
                }
                delay(15_000L)
            }
        }
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }
}

package com.luddy.bloomington_transit.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luddy.bloomington_transit.data.ai.AiResult
import com.luddy.bloomington_transit.data.ai.BtAiRepository
import com.luddy.bloomington_transit.data.ai.dto.BunchingEventDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BunchingBannerViewModel @Inject constructor(
    private val repo: BtAiRepository,
) : ViewModel() {

    private val _events = MutableStateFlow<List<BunchingEventDto>>(emptyList())
    val events: StateFlow<List<BunchingEventDto>> = _events.asStateFlow()

    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            while (true) {
                when (val r = repo.bunching()) {
                    is AiResult.Ok -> _events.value = r.value.events
                    is AiResult.Err -> Unit // keep last known; fail loud in logs, quiet in UI
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

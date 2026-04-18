package com.luddy.bloomington_transit.domain.usecase

import com.luddy.bloomington_transit.domain.model.Arrival
import com.luddy.bloomington_transit.domain.repository.TransitRepository
import javax.inject.Inject

class GetArrivalsForStopUseCase @Inject constructor(
    private val repository: TransitRepository
) {
    suspend operator fun invoke(stopId: String): List<Arrival> =
        repository.getArrivalsForStop(stopId)
            .sortedBy { it.displayArrivalMs }
}

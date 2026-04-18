package com.luddy.bloomington_transit.domain.usecase

import com.luddy.bloomington_transit.domain.repository.TransitRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class TrackBusUseCase @Inject constructor(
    private val repository: TransitRepository
) {
    fun getTrackedBuses(): Flow<Set<String>> = repository.getTrackedBusIds()

    suspend fun trackBus(vehicleId: String) = repository.addTrackedBus(vehicleId)

    suspend fun untrackBus(vehicleId: String) = repository.removeTrackedBus(vehicleId)
}

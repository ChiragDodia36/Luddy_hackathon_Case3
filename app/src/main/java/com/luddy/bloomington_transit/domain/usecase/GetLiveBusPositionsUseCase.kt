package com.luddy.bloomington_transit.domain.usecase

import com.luddy.bloomington_transit.domain.model.Bus
import com.luddy.bloomington_transit.domain.repository.TransitRepository
import javax.inject.Inject

class GetLiveBusPositionsUseCase @Inject constructor(
    private val repository: TransitRepository
) {
    suspend operator fun invoke(): List<Bus> = repository.getLiveBuses()
}

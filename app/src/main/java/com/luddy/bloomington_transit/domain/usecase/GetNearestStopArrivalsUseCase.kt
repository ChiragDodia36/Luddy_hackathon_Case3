package com.luddy.bloomington_transit.domain.usecase

import com.luddy.bloomington_transit.domain.model.Arrival
import com.luddy.bloomington_transit.domain.model.Stop
import com.luddy.bloomington_transit.domain.repository.TransitRepository
import javax.inject.Inject

data class NearestStopArrivals(
    val stop: Stop,
    val arrivals: List<Arrival>
)

class GetNearestStopArrivalsUseCase @Inject constructor(
    private val repository: TransitRepository
) {
    suspend operator fun invoke(lat: Double, lon: Double): NearestStopArrivals? {
        val nearestStops = repository.getNearestStops(lat, lon, radiusMeters = 600.0)
        val nearest = nearestStops.firstOrNull() ?: return null
        val arrivals = repository.getArrivalsForStop(nearest.id)
            .sortedBy { it.displayArrivalMs }
            .take(5)
        return NearestStopArrivals(nearest, arrivals)
    }
}

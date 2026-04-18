package com.luddy.bloomington_transit.domain.usecase

import com.luddy.bloomington_transit.domain.model.ShapePoint
import com.luddy.bloomington_transit.domain.repository.TransitRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetRouteShapesUseCase @Inject constructor(
    private val repository: TransitRepository
) {
    operator fun invoke(routeId: String): Flow<List<ShapePoint>> =
        repository.getShapePointsForRoute(routeId)
}

package com.luddy.bloomington_transit.data.local.dao

import androidx.room.*
import com.luddy.bloomington_transit.data.local.entity.ShapeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShapeDao {
    @Query("""
        SELECT DISTINCT sh.shapeId, sh.lat, sh.lon, sh.sequence
        FROM shapes sh
        INNER JOIN trips t ON t.shapeId = sh.shapeId
        WHERE t.routeId = :routeId
        ORDER BY sh.shapeId ASC, sh.sequence ASC
    """)
    fun getShapesForRoute(routeId: String): Flow<List<ShapeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(shapes: List<ShapeEntity>)

    @Query("DELETE FROM shapes")
    suspend fun deleteAll()
}

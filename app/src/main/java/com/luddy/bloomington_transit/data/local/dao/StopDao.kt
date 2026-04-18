package com.luddy.bloomington_transit.data.local.dao

import androidx.room.*
import com.luddy.bloomington_transit.data.local.entity.StopEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StopDao {
    @Query("SELECT * FROM stops ORDER BY name ASC")
    fun getAllStops(): Flow<List<StopEntity>>

    @Query("SELECT * FROM stops WHERE id = :stopId")
    suspend fun getStopById(stopId: String): StopEntity?

    @Query("""
        SELECT s.* FROM stops s
        INNER JOIN route_stops rs ON s.id = rs.stopId
        WHERE rs.routeId = :routeId
        ORDER BY s.name ASC
    """)
    fun getStopsForRoute(routeId: String): Flow<List<StopEntity>>

    @Query("""
        SELECT * FROM stops
        WHERE name LIKE '%' || :query || '%'
           OR code LIKE '%' || :query || '%'
        ORDER BY name ASC
        LIMIT 20
    """)
    suspend fun searchStops(query: String): List<StopEntity>

    @Query("""
        SELECT *, (
            (lat - :lat) * (lat - :lat) + (lon - :lon) * (lon - :lon)
        ) AS distSq
        FROM stops
        ORDER BY distSq ASC
        LIMIT 10
    """)
    suspend fun getNearestStops(lat: Double, lon: Double): List<StopEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stops: List<StopEntity>)

    @Query("DELETE FROM stops")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM stops")
    suspend fun count(): Int
}

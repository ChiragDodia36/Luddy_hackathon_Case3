package com.luddy.bloomington_transit.data.local.dao

import androidx.room.*
import com.luddy.bloomington_transit.data.local.entity.RouteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteDao {
    @Query("SELECT * FROM routes ORDER BY shortName ASC")
    fun getAllRoutes(): Flow<List<RouteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(routes: List<RouteEntity>)

    @Query("DELETE FROM routes")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM routes")
    suspend fun count(): Int
}

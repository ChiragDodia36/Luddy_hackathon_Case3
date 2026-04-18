package com.luddy.bloomington_transit.data.local.dao

import androidx.room.*
import com.luddy.bloomington_transit.data.local.entity.TripEntity
import com.luddy.bloomington_transit.data.local.entity.StopTimeEntity
import com.luddy.bloomington_transit.data.local.entity.RouteStopEntity

@Dao
interface TripDao {
    @Query("SELECT * FROM trips WHERE tripId = :tripId")
    suspend fun getTripById(tripId: String): TripEntity?

    @Query("SELECT * FROM trips WHERE routeId = :routeId")
    suspend fun getTripsForRoute(routeId: String): List<TripEntity>

    @Query("""
        SELECT st.* FROM stop_times st
        INNER JOIN trips t ON t.tripId = st.tripId
        WHERE st.stopId = :stopId
        ORDER BY st.arrivalTime ASC
    """)
    suspend fun getStopTimesForStop(stopId: String): List<StopTimeEntity>

    @Query("""
        SELECT st.* FROM stop_times st
        WHERE st.tripId = :tripId
        ORDER BY st.stopSequence ASC
    """)
    suspend fun getStopTimesForTrip(tripId: String): List<StopTimeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrips(trips: List<TripEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStopTimes(stopTimes: List<StopTimeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRouteStops(routeStops: List<RouteStopEntity>)

    @Query("DELETE FROM trips")
    suspend fun deleteAllTrips()

    @Query("DELETE FROM stop_times")
    suspend fun deleteAllStopTimes()

    @Query("DELETE FROM route_stops")
    suspend fun deleteAllRouteStops()

    @Query("SELECT COUNT(*) FROM trips")
    suspend fun count(): Int
}

package com.luddy.bloomington_transit.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.luddy.bloomington_transit.data.local.dao.*
import com.luddy.bloomington_transit.data.local.entity.*

@Database(
    entities = [
        RouteEntity::class,
        StopEntity::class,
        ShapeEntity::class,
        TripEntity::class,
        StopTimeEntity::class,
        RouteStopEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun routeDao(): RouteDao
    abstract fun stopDao(): StopDao
    abstract fun shapeDao(): ShapeDao
    abstract fun tripDao(): TripDao
}

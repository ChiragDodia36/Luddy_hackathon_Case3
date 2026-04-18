package com.luddy.bloomington_transit.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

@Singleton
class UserPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val FAVOURITE_STOP_IDS = stringSetPreferencesKey("favourite_stop_ids")
        val TRACKED_BUS_IDS = stringSetPreferencesKey("tracked_bus_ids")
        val NOTIFICATION_THRESHOLD_MINUTES = intPreferencesKey("notif_threshold_min")
        val GTFS_LAST_UPDATED = longPreferencesKey("gtfs_last_updated")
        val FAVOURITE_ROUTE_ID = stringPreferencesKey("favourite_route_id")
    }

    val favouriteStopIds: Flow<Set<String>> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[Keys.FAVOURITE_STOP_IDS] ?: emptySet() }

    val trackedBusIds: Flow<Set<String>> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[Keys.TRACKED_BUS_IDS] ?: emptySet() }

    val notificationThresholdMinutes: Flow<Int> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[Keys.NOTIFICATION_THRESHOLD_MINUTES] ?: 3 }

    val gtfsLastUpdated: Flow<Long> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[Keys.GTFS_LAST_UPDATED] ?: 0L }

    val favouriteRouteId: Flow<String?> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[Keys.FAVOURITE_ROUTE_ID] }

    suspend fun addFavouriteStop(stopId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.FAVOURITE_STOP_IDS] ?: emptySet()
            prefs[Keys.FAVOURITE_STOP_IDS] = current + stopId
        }
    }

    suspend fun removeFavouriteStop(stopId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.FAVOURITE_STOP_IDS] ?: emptySet()
            prefs[Keys.FAVOURITE_STOP_IDS] = current - stopId
        }
    }

    suspend fun addTrackedBus(vehicleId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.TRACKED_BUS_IDS] ?: emptySet()
            prefs[Keys.TRACKED_BUS_IDS] = current + vehicleId
        }
    }

    suspend fun removeTrackedBus(vehicleId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.TRACKED_BUS_IDS] ?: emptySet()
            prefs[Keys.TRACKED_BUS_IDS] = current - vehicleId
        }
    }

    suspend fun setNotificationThreshold(minutes: Int) {
        context.dataStore.edit { it[Keys.NOTIFICATION_THRESHOLD_MINUTES] = minutes }
    }

    suspend fun setGtfsLastUpdated(timestamp: Long) {
        context.dataStore.edit { it[Keys.GTFS_LAST_UPDATED] = timestamp }
    }

    suspend fun setFavouriteRouteId(routeId: String?) {
        context.dataStore.edit {
            if (routeId == null) it.remove(Keys.FAVOURITE_ROUTE_ID)
            else it[Keys.FAVOURITE_ROUTE_ID] = routeId
        }
    }
}

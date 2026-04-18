# data/ — Claude Context

## What this layer is
Implements `TransitRepository`. Contains all I/O: network, disk, preferences.

## Subfolders
- `api/GtfsRealtimeApi.kt` — Retrofit interface, 3 `.pb` endpoints, returns raw `ResponseBody`
- `api/GtfsStaticParser.kt` — Downloads GTFS zip, parses 5 CSV files, returns `GtfsStaticData`
- `local/AppDatabase.kt` — Room DB with 6 tables
- `local/UserPreferencesDataStore.kt` — DataStore for favourites, tracked buses, thresholds
- `local/entity/` — Room entity classes with `.toDomain()` mappers
- `local/dao/` — DAOs: RouteDao, StopDao, ShapeDao, TripDao
- `repository/TransitRepositoryImpl.kt` — Main implementation

## Critical: GTFS RT parsing
Uses `com.google.transit:gtfs-realtime-bindings:0.0.4` (ONLY available version on Maven Central).
Parse: `GtfsRealtime.FeedMessage.parseFrom(responseBody.byteStream())`

## Critical: Arrival merging
`getArrivalsForStop()` in `TransitRepositoryImpl`:
1. Fetch `trip_updates.pb` → build `Map<tripId, predictedArrivalMs>`
2. Load static `stop_times` from Room for today
3. Filter to next 2h window
4. Merge: if tripId in realtime map → `predictedArrivalMs = map[tripId]`, else `-1L`

## Critical: GTFS time parsing
`parseGtfsTimeToMs("25:30:00", todayBaseMs)` handles post-midnight trips (hours > 24).

## Test results
- APK builds successfully: `gradle assembleDebug` → BUILD SUCCESSFUL
- DB schema: 6 entities, version=1, `fallbackToDestructiveMigration`

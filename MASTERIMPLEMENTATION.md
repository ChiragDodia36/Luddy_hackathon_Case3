# BT Transit — Master Implementation Document

## Architecture
Clean Architecture with 3 layers:
1. **domain/** — Pure Kotlin, no Android deps. Models + Repository interface + Use cases
2. **data/** — Android/3rd-party implementations: Room, DataStore, Retrofit, GTFS parser
3. **ui/** — Jetpack Compose screens + ViewModels (MVVM)

DI glues everything via Hilt (`di/AppModule.kt`, `di/RepositoryModule.kt`).

---

## Layer 1: Domain

### Models (`domain/model/`)
| File | What it represents |
|------|--------------------|
| `Route.kt` | A bus route (id, name, hex color) |
| `Stop.kt` | A bus stop (id, name, lat/lon) |
| `Bus.kt` | A live bus position (vehicleId, tripId, lat/lon, bearing) |
| `Arrival.kt` | Predicted/scheduled arrival at a stop. `minutesUntil()` computes countdown. `isRealtime` = has live data |
| `ShapePoint.kt` | One point in a route's polyline path |
| `ServiceAlert.kt` | System-wide alert message from GTFS-RT alerts feed |

### Repository Interface (`domain/repository/TransitRepository.kt`)
All methods the app needs — static data, realtime, favourites, preferences.

### Use Cases (`domain/usecase/`)
| File | Purpose |
|------|---------|
| `GetArrivalsForStopUseCase.kt` | Returns arrivals sorted by display time |
| `GetLiveBusPositionsUseCase.kt` | Returns all live buses |
| `GetNearestStopArrivalsUseCase.kt` | Given lat/lon, finds nearest stop + next 5 arrivals |
| `GetRouteShapesUseCase.kt` | Returns Flow of shape points for a route |
| `TrackBusUseCase.kt` | Wraps track/untrack bus persistence |

---

## Layer 2: Data

### GTFS Static (`data/api/GtfsStaticParser.kt`)
- Downloads and unzips `gtfs.zip` via OkHttp
- Parses `routes.txt`, `stops.txt`, `trips.txt`, `shapes.txt`, `stop_times.txt`
- Custom CSV parser handles quoted fields
- Returns `GtfsStaticData` (all entities ready to insert into Room)
- Re-downloads if >24h old OR DB is empty

### Room Database (`data/local/`)
| Table | Entity |
|-------|--------|
| routes | RouteEntity |
| stops | StopEntity |
| shapes | ShapeEntity |
| trips | TripEntity |
| stop_times | StopTimeEntity |
| route_stops | RouteStopEntity (join table) |

### DataStore (`data/local/UserPreferencesDataStore.kt`)
Persists: `favourite_stop_ids`, `tracked_bus_ids`, `notif_threshold_min`, `gtfs_last_updated`
This is what fixes ETA Spot's #1 bug — favourites survive app restart.

### GTFS Realtime (`data/api/GtfsRealtimeApi.kt`)
Retrofit interface — 3 endpoints return `ResponseBody` (raw protobuf bytes).
Parsed via `GtfsRealtime.FeedMessage.parseFrom(body.byteStream())`.

### Repository (`data/repository/TransitRepositoryImpl.kt`)
Key logic:
- `getArrivalsForStop()`: fetches trip_updates.pb, builds arrival map (tripId→predictedMs), then joins with static stop_times filtered to next 2h window. Merges realtime+scheduled.
- `getNearestStops()`: SQL orders by squared Euclidean distance, then filters by Haversine for accuracy
- `parseGtfsTimeToMs()`: handles GTFS times >24h (e.g. "25:30:00" for post-midnight trips)

---

## Layer 3: UI

### Theme (`ui/theme/`)
- `Color.kt` — BT brand colors + countdown colors (green/amber/red) + `routeColor()` helper
- `Type.kt` — Material3 typography
- `Theme.kt` — Dynamic color (Android 12+) + dark mode auto-follow system

### Navigation (`ui/navigation/NavGraph.kt`, `ui/BtApp.kt`)
Bottom navigation bar: Home → Map → Schedule → Favourites
Compose Navigation with `saveState`/`restoreState` for tab switching without losing state.

### Components (`ui/components/`)
| Component | What it does |
|-----------|-------------|
| `CountdownChip.kt` | Live ticking countdown (1s tick via `LaunchedEffect`). Green→Amber→Red color shift |
| `ArrivalRow.kt` | Route color badge + headsign + CountdownChip in a row |
| `ServiceAlertBanner.kt` | Collapsible alert banner, red when active |

### Screens

#### HomeScreen + HomeViewModel
- Requests location permission on launch
- Gets nearest stop arrivals using `GetNearestStopArrivalsUseCase`
- Shows pinned stops from DataStore (all persisted)
- 10s polling loop in ViewModel
- Service alert banner at top

#### MapScreen + MapViewModel
- Google Maps with `maps-compose`
- Route polylines from GTFS shapes, colored by route
- Animated bus markers — each bus has a colored square chip
- Route filter chips (scrollable row) to show/hide routes
- Tap bus → bottom sheet with "Track This Bus" button
- Tap stop → bottom sheet with departure board
- Camera auto-follows selected bus
- `startPolling()` fetches `getLiveBuses()` every 10s

#### ScheduleScreen + ScheduleViewModel
- Stop search bar → calls `repository.searchStops()` as user types
- Selects stop → loads all arrivals
- Departure board list sorted by ETA
- "Live only" filter chip
- 10s polling when stop selected

#### FavouritesScreen + FavouritesViewModel
- Reads `getFavouriteStopIds()` Flow — auto-updates when stops added/removed
- Shows ALL routes for each stop (fixes ETA Spot's broken favourites)
- Remove with heart icon + confirmation dialog
- Empty state with helpful hint text

---

## Service: BusTrackingService

Foreground service that runs while user is tracking buses.
- `startForeground()` with low-priority persistent notification
- `startTracking()` coroutine: every 10s, for each tracked vehicleId:
  1. Get user location from `FusedLocationProviderClient`
  2. Get nearest stop within 1km of user
  3. Find arrival for tracked bus at that stop
  4. If arrival ≤ threshold minutes → fire notification
  5. `firedNotifications` Set prevents spam (keyed by vehicleId+stopId+minutes)
  6. Clears fired cache when bus passes (minutes == 0)

Notification channels:
- `bus_tracking` (HIGH priority) — arrival alerts
- `foreground_service` (LOW priority) — persistent "tracking" notification

---

## Widget: BusWidget (Glance)

`BusWidgetReceiver` → `BusWidget.provideGlance()` called on update.
Uses `WidgetEntryPoint` (Hilt @EntryPoint) to access `TransitRepository`.

Layout: Title | Tracked buses | Next arrival per pinned stop with countdown.
Countdown color: red if <3min, blue otherwise.
Tap → opens `MainActivity`.

---

## Build Notes
- `gradle assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- Must have `local.properties` with `MAPS_API_KEY=...`
- `gradle.properties` must have `android.useAndroidX=true`
- Kotlin compile: ~13s, full APK build: ~60s

## Test Plan
1. Launch → GTFS static download starts (watch Logcat for "GTFS loaded: X routes")
2. Map screen → route polylines appear, bus markers animate
3. Tap bus → bottom sheet with Track button
4. Track a bus → enable background, confirm notification fires near stop
5. Schedule → search "Indiana" → stop results appear, tap → departure board
6. Add favourite from stop sheet → Favourites tab shows it, kill/reopen app → still there
7. Home screen widget → long press home → add widget → next bus countdown shows
8. Dark mode → toggle system dark mode → app follows

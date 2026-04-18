# BT Transit — Master Claude Context

## Project Overview
A modern Android replacement for the ETA Spot app for Bloomington Transit (BT).
Built with Kotlin + Jetpack Compose + Clean Architecture + MVVM.

## APK Build Status
- **BUILDS SUCCESSFULLY** as of initial implementation
- Command: `gradle assembleDebug --no-daemon`
- Output: `app/build/outputs/apk/debug/app-debug.apk`

## CRITICAL: Before building, add your API key
1. Edit `local.properties`: replace `YOUR_GOOGLE_MAPS_API_KEY_HERE` with real key
2. Get key from Google Cloud Console → APIs → Maps SDK for Android

## Package Structure
```
com.luddy.bloomington_transit/
├── data/           → GTFS parsing, Room DB, DataStore, Retrofit
├── di/             → Hilt dependency injection modules
├── domain/         → Models, repository interface, use cases
├── service/        → BusTrackingService (foreground), BusNotificationHelper
├── ui/
│   ├── components/ → Shared Composables (CountdownChip, ArrivalRow, ServiceAlertBanner)
│   ├── navigation/ → NavGraph, BottomNavItems, Screen sealed class
│   ├── screens/    → home/, map/, schedule/, favourites/
│   └── theme/      → Color.kt, Type.kt, Theme.kt
└── widget/         → BusWidget (Glance), BusWidgetReceiver
```

## GTFS API Endpoints
| Feed | URL |
|------|-----|
| Static zip | `https://s3.amazonaws.com/etatransit.gtfs/bloomingtontransit.etaspot.net/gtfs.zip` |
| Vehicle positions | `...position_updates.pb` |
| Trip updates | `...trip_updates.pb` |
| Alerts | `...alerts.pb` |

## Key Dependencies (all in gradle/libs.versions.toml)
- Maps: `com.google.maps.android:maps-compose:6.1.0`
- GTFS-RT: `com.google.transit:gtfs-realtime-bindings:0.0.4` (ONLY version on Maven Central)
- Room: `2.6.1`, DataStore: `1.1.1`, Hilt: `2.52`
- Glance widget: `androidx.glance:glance-appwidget:1.1.0`

## Known Issues / Session Restart Notes
- GTFS version MUST be 0.0.4 — higher versions don't exist on Maven Central
- `androidx.useAndroidX=true` MUST be in gradle.properties
- `kotlinx-coroutines-play-services` is needed for `fusedLocationClient.lastLocation.await()`
- Glance `dp`/`sp` must import from `androidx.compose.ui.unit`, not Compose UI context

## Feature Implementation Status
| Feature | Status | File(s) |
|---------|--------|---------|
| GTFS Static download + parse | Done | `data/api/GtfsStaticParser.kt` |
| 10s realtime polling | Done | ViewModels (all screens) |
| Arrival time calculation | Done | `data/repository/TransitRepositoryImpl.kt` |
| Individual bus tracking | Done | `MapScreen.kt`, `MapViewModel.kt` |
| Route view / interactive map | Done | `MapScreen.kt` |
| Schedule table / departure board | Done | `ScheduleScreen.kt` |
| Background notifications | Done | `BusTrackingService.kt` |
| MVVM + Clean Architecture | Done | All layers |
| Live countdown timer | Done | `CountdownChip.kt` |
| Home screen widget | Done | `BusWidget.kt`, `BusWidgetReceiver.kt` |
| Persistent favourites | Done | `UserPreferencesDataStore.kt` |
| Service alerts banner | Done | `ServiceAlertBanner.kt` |
| Dark mode / Material3 | Done | `Theme.kt` |

## What Still Needs Work (if resuming)
1. Add `local.properties` with real Google Maps API key
2. Test on physical device / emulator
3. Add launcher icons (currently placeholder PNGs — replace with proper BDM adaptive icons)
4. Add notification permission request at runtime (Android 13+)
5. Add `FavouriteButton` composable to MapScreen stop detail sheet
6. Polish: add loading shimmer effect on initial GTFS download

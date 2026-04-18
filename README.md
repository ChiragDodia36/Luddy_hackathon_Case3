# BT Transit — Bloomington Transit Android App

A modern, real-time Android app for Bloomington Transit (BT) built as a replacement for the ETA Spot app.

---

## Features
- **Live Bus Tracking** — Real-time bus positions on an interactive Google Map, updated every 10 seconds
- **Arrival Countdown** — Live ticking countdown timers (not static times) that turn amber <3min and red <1min
- **Departure Board** — Airport-style upcoming bus list for any stop
- **Route Map View** — Color-coded route polylines from GTFS shapes data
- **Persistent Favourites** — Save stops that survive app restarts (fixes ETA Spot's #1 bug)
- **Background Notifications** — Alerts when your tracked bus is within N minutes of your nearest stop
- **Home Screen Widget** — Next bus countdown without opening the app
- **Service Alert Banner** — GTFS-RT alert feed surfaced prominently at the top
- **Dark Mode** — Follows system setting automatically

---

## Setup Instructions

### 1. Get a Google Maps API Key
1. Go to [Google Cloud Console](https://console.cloud.google.com)
2. Create a project → Enable "Maps SDK for Android"
3. Create credentials → API Key
4. Restrict the key to "Android apps" with your package name: `com.luddy.bloomington_transit`

### 2. Add the key to local.properties
```
sdk.dir=/Users/YOUR_USERNAME/Library/Android/sdk
MAPS_API_KEY=YOUR_KEY_HERE
```

### 3. Build and Run
```bash
# Debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Or open in Android Studio and press Run
```

---

## Architecture

This app follows **Clean Architecture + MVVM** as required for graduate teams.

```
┌─────────────────────────────────────┐
│           UI Layer (MVVM)           │
│   Composables ←→ ViewModels         │
│   (screens/, components/, theme/)   │
└──────────────┬──────────────────────┘
               │ Use Cases
┌──────────────▼──────────────────────┐
│          Domain Layer               │
│   Models | Repository (interface)   │
│   Use Cases (pure Kotlin)           │
└──────────────┬──────────────────────┘
               │ Implementation
┌──────────────▼──────────────────────┐
│           Data Layer                │
│   Room DB | DataStore | Retrofit    │
│   GtfsStaticParser | RealtimeApi    │
└─────────────────────────────────────┘
```

### Dependency Injection
Hilt (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`) wires all layers.

---

## GTFS API Endpoints Used

| Feed | URL | Purpose |
|------|-----|---------|
| Static GTFS | `https://s3.amazonaws.com/etatransit.gtfs/bloomingtontransit.etaspot.net/gtfs.zip` | Routes, stops, schedules, shapes |
| Vehicle Positions | `.../position_updates.pb` | Live bus lat/lon, bearing |
| Trip Updates | `.../trip_updates.pb` | Predicted arrival times |
| Service Alerts | `.../alerts.pb` | Service disruptions |

All realtime feeds polled every **10 seconds**.

---

## Tech Stack

| Technology | Purpose |
|---|---|
| Kotlin | Primary language |
| Jetpack Compose + Material3 | UI framework |
| Hilt | Dependency injection |
| Room | Local SQLite for GTFS static data |
| DataStore | User preferences (favourites, settings) |
| Retrofit + OkHttp | GTFS realtime protobuf fetching |
| gtfs-realtime-bindings 0.0.4 | Protobuf parsing |
| Google Maps SDK + maps-compose | Interactive map |
| Glance AppWidget | Home screen widget |
| kotlinx-coroutines | Async + Flow |

---

## Project Structure
```
app/src/main/java/com/luddy/bloomington_transit/
├── BloomingtonTransitApp.kt     Hilt app + notification channels
├── MainActivity.kt              Entry point
├── data/
│   ├── api/                     GTFS Realtime Retrofit + Static parser
│   ├── local/                   Room DB + DataStore
│   └── repository/              TransitRepositoryImpl
├── di/                          Hilt modules
├── domain/
│   ├── model/                   Route, Stop, Bus, Arrival, etc.
│   ├── repository/              TransitRepository interface
│   └── usecase/                 Business logic
├── service/                     Foreground service + notifications
├── ui/
│   ├── components/              CountdownChip, ArrivalRow, AlertBanner
│   ├── navigation/              NavGraph, BottomNavItems
│   ├── screens/                 home, map, schedule, favourites
│   └── theme/                   Colors, Typography, Theme
└── widget/                      Glance home screen widget
```

---

## Known Limitations
- Location permission required for nearest stop detection
- Notification permission required (Android 13+) for arrival alerts
- Google Maps API key required to display map tiles
- GTFS static data download (~2-5MB) on first launch

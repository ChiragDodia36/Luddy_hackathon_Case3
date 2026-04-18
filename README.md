# BT Transit — Bloomington Transit Android App

A modern, real-time Android app for Bloomington Transit (BT) built as a replacement for the ETA Spot app.
Light glassmorphism UI with live bus tracking, arrival countdowns, and adaptive home screen.

---

## Requirements

### Desktop / Machine
| Requirement | Version | Notes |
|---|---|---|
| OS | macOS / Windows / Linux | All supported |
| RAM | 8 GB minimum | 16 GB recommended for emulator |
| Disk | 10 GB free | Android Studio + SDK + emulator |

### Software to Install
| Tool | Version | Download |
|---|---|---|
| Android Studio | Ladybug (2024.2.1) or newer | https://developer.android.com/studio |
| JDK | 17 (bundled with Android Studio) | No separate install needed |
| Android SDK | API 35 (Android 15) | Installed via Android Studio |
| Android Build Tools | 35.0.0 | Installed via Android Studio |
| Google Play Services | Latest | Needed for Maps + Location |

> **Tip:** Android Studio bundles the JDK automatically. You do **not** need to install Java separately.

---

## Prerequisite: Google Maps API Key

This app uses Google Maps. Each developer needs their own key (free tier is sufficient).

1. Go to [Google Cloud Console](https://console.cloud.google.com)
2. Create a new project (or use an existing one)
3. Enable these APIs:
   - **Maps SDK for Android**
   - **Places API** (optional, for future features)
4. Go to **Credentials → Create Credentials → API Key**
5. (Recommended) Restrict the key:
   - Application restrictions → Android apps
   - Package name: `com.luddy.bloomington_transit`
   - SHA-1: run `./gradlew signingReport` to get your debug SHA-1

---

## Setup Steps

### 1. Clone the repository
```bash
git clone <your-repo-url>
cd luddy_hackathon_case3
```

### 2. Create your `local.properties` file

Copy the template and fill in your values:
```bash
cp local.properties.template local.properties
```

Then open `local.properties` and set:
```properties
# Replace with your actual Android SDK path
# macOS default:
sdk.dir=/Users/YOUR_USERNAME/Library/Android/sdk

# Windows default:
# sdk.dir=C\:\\Users\\YOUR_USERNAME\\AppData\\Local\\Android\\Sdk

# Linux default:
# sdk.dir=/home/YOUR_USERNAME/Android/Sdk

# Your Google Maps API key (see Prerequisite above)
MAPS_API_KEY=YOUR_GOOGLE_MAPS_API_KEY_HERE

# URL of the bt-ml FastAPI inference service (AI integration).
# Emulator → host machine uses 10.0.2.2. Physical devices on LAN use the
# machine's IP. If pointing at a deployed service, use the https URL.
BACKEND_BASE_URL=http://10.0.2.2:8000/
```

> **Note:** `local.properties` is in `.gitignore` — never commit it. Each developer has their own.

### 3. Open in Android Studio

- Open Android Studio → **Open** → select the `luddy_hackathon_case3` folder
- Wait for Gradle sync to complete (first sync downloads ~300 MB of dependencies)
- Android Studio will automatically detect the SDK and suggest installing missing components

### 4. Set up an emulator or physical device

**Option A — Physical Android device (recommended for performance):**
1. On your phone: Settings → About Phone → tap "Build Number" 7 times to enable Developer Options
2. Settings → Developer Options → enable "USB Debugging"
3. Connect via USB cable
4. Accept the "Allow USB debugging" prompt on the phone

**Option B — Android Emulator:**
1. In Android Studio: Tools → Device Manager → Create Device
2. Select a phone (e.g., Pixel 8)
3. System Image: API 35 (Android 15) — download if not present
4. Finish and start the emulator

> **Minimum Android version:** API 26 (Android 8.0 Oreo)

### 5. Build and run

**Via Android Studio:**
- Press the green **Run** button (▶) or `Shift+F10`

**Via command line:**
```bash
# Build debug APK
./gradlew assembleDebug

# Build + install on connected device/emulator
./gradlew installDebug

# Clean build (if you hit weird issues)
./gradlew clean assembleDebug
```

---

## First Launch Behaviour

On first launch the app will:
1. Download GTFS static data (~2–5 MB) from the Bloomington Transit server — this takes ~10–30 seconds
2. Show a loading state on the Home and Map screens until data is ready
3. All subsequent launches use the cached database (refreshed every 24 hours)

Grant these permissions when prompted:
- **Location** — for nearest stop detection and "Use my nearest stop" feature
- **Notifications** — for arrival alerts (Android 13+)

---

## Project Dependencies (auto-downloaded by Gradle)

All dependencies are declared in `gradle/libs.versions.toml` and downloaded automatically on first sync. No manual installation required.

| Library | Version | Purpose |
|---|---|---|
| Kotlin | 2.0.21 | Language |
| Jetpack Compose BOM | 2024.09.03 | UI framework |
| Material3 | (via BOM) | Design components |
| Hilt | 2.52 | Dependency injection |
| Room | 2.6.1 | Local SQLite (GTFS static data) |
| DataStore | 1.1.1 | User preferences |
| Retrofit + OkHttp | 2.11.0 / 4.12.0 | Network requests |
| maps-compose | 6.1.0 | Google Maps in Compose |
| play-services-maps | 19.0.0 | Google Maps SDK |
| play-services-location | 21.3.0 | FusedLocationProvider |
| gtfs-realtime-bindings | 0.0.4 | GTFS protobuf parsing |
| Glance AppWidget | 1.1.0 | Home screen widget |
| kotlinx-coroutines | 1.8.1 | Async + Flow |
| WorkManager | 2.9.1 | Background widget updates |

---

## GTFS API Endpoints Used

| Feed | Purpose |
|---|---|
| `gtfs.zip` (S3) | Routes, stops, schedules, shapes (static, cached 24h) |
| `position_updates.pb` | Live bus lat/lon + bearing (polled every 10s) |
| `trip_updates.pb` | Predicted arrival times (polled every 10s) |
| `alerts.pb` | Service disruptions (polled every 10s) |

---

## Project Structure

```
app/src/main/java/com/luddy/bloomington_transit/
├── BloomingtonTransitApp.kt     Hilt app entry + notification channels
├── MainActivity.kt              Single activity host
├── data/
│   ├── api/                     GTFS Realtime Retrofit + Static zip parser
│   ├── local/                   Room DB, DAOs, DataStore, entities
│   └── repository/              TransitRepositoryImpl
├── di/                          Hilt modules (NetworkModule, DatabaseModule)
├── domain/
│   ├── model/                   Route, Stop, Bus, Arrival, ShapePoint, ServiceAlert
│   ├── repository/              TransitRepository interface
│   └── usecase/                 Business logic use cases
├── service/                     Foreground service + arrival notifications
├── ui/
│   ├── BtApp.kt                 Root composable: gradient + nav + scaffold
│   ├── components/              CountdownChip, ArrivalRow, HomeCards, AlertBanner
│   ├── navigation/              NavGraph, BottomNavItems
│   ├── screens/
│   │   ├── home/                HomeScreen + HomeViewModel (adaptive context)
│   │   ├── map/                 MapScreen + MapViewModel (live buses + routes)
│   │   ├── schedule/            ScheduleScreen + ScheduleViewModel (search + arrivals)
│   │   └── favourites/          FavouritesScreen + FavouritesViewModel (saved stops)
│   └── theme/                   Colors, Typography, Theme, GlassComponents
└── widget/                      Glance home screen widget
```

---

## Architecture

This app follows **Clean Architecture + MVVM** with an additional AI-inference layer.

```
┌────────────────────────────────────────────────────────────────────┐
│                         UI Layer (MVVM)                            │
│   Composables  ←→  @HiltViewModel                                  │
│   ├─ screens/{home, map, ai, schedule, favourites, diagnostics,    │
│   │            trip}                                               │
│   └─ components/{CountdownChip, ArrivalRow, AiArrivalRow,          │
│                  ConfidenceBadge, BunchingBanner, ServiceAlertBanner}│
└──────────────────┬────────────────────────┬────────────────────────┘
                   │                        │
                   │ Use Cases              │ AI calls
                   ▼                        ▼
┌──────────────────────────────┐  ┌──────────────────────────────────┐
│         Domain Layer         │  │       data/ai/ (new)             │
│  Models | Repository (intf)  │  │  BtAiApi    (Retrofit)           │
│  Use Cases (pure Kotlin)     │  │  BtAiRepository (AiResult<T>)    │
└──────────────────┬───────────┘  │  dto/ @Serializable              │
                   │              │  BtAiModule (@Named OkHttp +     │
                   │              │               kotlinx-serialization)│
                   │              └──────────────────────────────────┘
                   │ Implementation                │
┌──────────────────▼───────────┐                   │ HTTPS
│        Data Layer            │                   ▼
│  Room DB | DataStore |       │    ┌──────────────────────────────┐
│  Retrofit (GTFS-RT .pb)      │    │  bt-ml FastAPI service       │
│  GtfsStaticParser            │    │  (separate repo, Ayan-owned) │
└──────────────────────────────┘    │  /predictions  /stats        │
                                    │  /detections/bunching  /nlq  │
                                    └──────────────────────────────┘
```

### Dependency Injection
Hilt (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`) wires all layers. The AI layer has its own `@Named("bt_ai_okhttp")` OkHttpClient and a dedicated Retrofit instance keyed on `BuildConfig.BACKEND_BASE_URL`, so AI-backed features don't touch the GTFS-RT path.

### AI-backed features (wired via `data/ai/` + `ui/screens/ai/`)

- **Per-stop delay correction (A1 + A2)** — the FastAPI service returns `Scheduled / BT / Ours` for each arrival at a stop, with a per-prediction `confidence` tier (high/medium/low). Rendered by `ui/components/AiArrivalRow.kt` + `ConfidenceBadge.kt`, on `ui/screens/ai/AiStopScreen.kt`.
- **Bunching detection (B1)** — 15 s polled `BunchingBanner` composable; appears on the Home screen when two same-route buses are within 200 m.
- **Stale-vehicle flag (B2)** — the `VehicleDto.isStale` field from the backend is surfaced on the Diagnostics screen.
- **Diagnostics dashboard (D1)** — `ui/screens/diagnostics/DiagnosticsScreen.kt`, reachable from the Home top-bar action. Shows live BT vs. our A1 CV MAE, fleet size, stale vehicle count, last feed refresh.
- **Trip ETA propagation (B3)** — tap any AI arrival row → `ui/screens/trip/TripEtaScreen.kt` shows per-stop adjusted ETAs across the remaining stops of that trip.
- **Natural-language query (C2)** — `BtAiRepository.nlq(query)` is called in parallel with stop-search; recognised intents (e.g. "next 6", "route 3E") show a hint chip under the search bar. Backend uses regex first and an optional Claude Haiku 4.5 fallback.

---

## Troubleshooting

| Problem | Fix |
|---|---|
| Gradle sync fails | File → Invalidate Caches → Restart; then re-sync |
| `MAPS_API_KEY` missing error | Make sure `local.properties` exists with your key |
| Map shows grey tiles | Check your Maps API key is valid and Maps SDK is enabled |
| App crashes on launch | Grant location permission; check Logcat for `TransitRepo` tag |
| "No routes found" on Home | Wait 30s for GTFS static download on first launch |
| Build error on emulator | Use a physical device — emulators can be slow on Gradle 8+ |
| `sdk.dir` not found | Open local.properties and fix the path to your Android SDK |

---

## Known Limitations

- Location permission required for nearest stop detection and "Use my nearest stop" button
- Notification permission required (Android 13+) for arrival alerts
- Google Maps API key required for map tiles (app works without it but map will be blank)
- GTFS static data downloads on first launch (~2–5 MB, ~10–30 seconds on WiFi)
- App targets Android 8.0+ (API 26) — older devices not supported

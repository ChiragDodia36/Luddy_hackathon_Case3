# BT Transit — Repo Root Claude Context

Android app + deployed FastAPI AI service that rebuilds Bloomington Transit's rider experience with live AI-adjusted arrival predictions.

**Measured improvement**: BT headline MAE @ 3-5 min = **94.3 s** → our A1 LightGBM residual model = **80.6 s** (**+14.5 %**, 5-fold GroupKFold on `trip_id`, 29,706 labelled predictions). Route-6 intercept = **−154 s** (most-biased route in audit). 12 of 16 BT routes have trained per-route intercepts. See [DEMO.md](DEMO.md) for full numbers.

## Architecture

```
┌───────────────────────────────────────────────────────────────────────┐
│  Android (Kotlin + Compose + MVVM + Hilt)                             │
│    Home · Map (trip planner) · AI · Schedule · Favourites             │
│                                                                       │
│    data/ai/   ──► BtAiRepository ──► BuildConfig.BACKEND_BASE_URL     │
│    data/      ──► TransitRepository (GTFS-RT + Room + DataStore)      │
│    ui/screens ──► composables + ViewModels (Hilt-injected)            │
└───────────────────────────────────┬───────────────────────────────────┘
                                    │
                                    ▼
┌───────────────────────────────────────────────────────────────────────┐
│  Railway · bt-ml-production.up.railway.app                            │
│    FastAPI · 2 uvicorn workers · HTTP/2 upstream · gzip · 60s cache   │
│    /plan ── Google Directions + AI enrichment                         │
│    /predictions · /stats · /detections/bunching · /nlq · /vehicles    │
│    A1 LightGBM joblib + A2 route_intercepts.json + StaticCache        │
└───────────────────────────────────┬───────────────────────────────────┘
                                    │
                                    ▼  GTFS-RT + static
          s3.amazonaws.com/etatransit.gtfs/bloomingtontransit.etaspot.net
```

Python service source lives on the `bt-ml-service` orphan branch of this repo (Railway auto-deploys from it).

## Package map

| Package | What it does | Details |
|---|---|---|
| `data/` | GTFS parsing, Room, DataStore, Retrofit | see [data/CLAUDE.md](app/src/main/java/com/luddy/bloomington_transit/data/CLAUDE.md) |
| `data/ai/` ★ | `BtAiApi`, `BtAiRepository`, `BtAiModule`, `dto/BtAiDtos.kt` — talks to the FastAPI backend. Uses `BuildConfig.BACKEND_BASE_URL`. | NEW — post-dates `data/CLAUDE.md` |
| `domain/` | Models + repository interfaces + use cases | see [domain/CLAUDE.md](app/src/main/java/com/luddy/bloomington_transit/domain/CLAUDE.md) |
| `service/` | `BusTrackingService` foreground + notifications | see [service/CLAUDE.md](app/src/main/java/com/luddy/bloomington_transit/service/CLAUDE.md) |
| `ui/` | Compose screens, navigation, theme | see [ui/CLAUDE.md](app/src/main/java/com/luddy/bloomington_transit/ui/CLAUDE.md) |
| `ui/screens/ai/` ★ | `AiStopScreen` (live scoreboard + search + AI-vs-BT rows) + `AiStopViewModel` | NEW |
| `ui/screens/diagnostics/` ★ | `DiagnosticsScreen` — model accuracy, fleet health, per-vehicle staleness drift (debug surface, entered via 🐞 icon on Home top bar) | NEW |
| `ui/screens/trip/` ★ | `TripEtaScreen` + `TripEtaViewModel` — full trip ETA propagation (one stop → all remaining stops with AI adjustments) | NEW |
| `ui/screens/map/` | `MapScreen` + `MapViewModel` — Naishal's Google-Maps-style trip planner with Places autocomplete, direct + 1-transfer routing, AI-enriched boarding ETAs. `MapViewModel` injects `TransitRepository`, `BtAiRepository`, and `PlacesClient` (3 Hilt deps). | Heavily reworked |
| `widget/` | Glance widget | see [widget/CLAUDE.md](app/src/main/java/com/luddy/bloomington_transit/widget/CLAUDE.md) |

★ = post-dates the package's CLAUDE.md file.

## Backend integration

- `BuildConfig.BACKEND_BASE_URL` is wired in `app/build.gradle.kts` from `local.properties` with default `https://bt-ml-production.up.railway.app/` — works out of the box.
- Override for local dev: put `BACKEND_BASE_URL=http://10.0.2.2:8000/` in `local.properties` (Android emulator's alias for host machine) and run the FastAPI service locally.
- `BtAiRepository` wraps every call in an `AiResult<T>` sealed class — the UI must handle `.Failure` gracefully (backend unreachable → UI still shows BT's numbers, never crashes).

## Build requirements

- **JDK 17** (NOT 21 or 25 — Gradle + AGP + KSP agree only on 17 for this build).
- `local.properties` with:
  - `sdk.dir=/Users/…/Library/Android/sdk`
  - `MAPS_API_KEY=…` (unlocks Maps SDK + Directions + Places)
  - `BACKEND_BASE_URL=…` (optional override; default = Railway deploy)
- `./gradlew assembleDebug` — BUILD SUCCESSFUL in ~90 s. If you see `Unresolved reference: BuildConfig` or `Unresolved reference: ai`, run `./gradlew clean assembleDebug` — KSP needs a fresh state when Hilt + kotlinx-serialization plugins renegotiate.

## Branch topology

| Branch | Role |
|---|---|
| `main` | Submission target. PR #1 (AI layer) merged via `e3ca256`. |
| `ayan/integrate-naishal` | Demo branch. 4 commits ahead of main, clean-mergeable as PR #2. Head at hackathon end: `1a95c36`. |
| `bt-ml-service` | Orphan branch holding the Python FastAPI service + Dockerfile + railway.json. Railway auto-deploys from this. |
| `Naishal`, `chiggy-v1` | Feature branches merged into the above; keep for history. |
| `Omkar` | Data-analysis-only — `results.csv`, `requirements.txt`; not wired into the app. |

**Never force-push `main`.** PRs are the submission vector.

## Related docs at repo root

- [DEMO.md](DEMO.md) — one-page pitch + architecture + endpoint curl examples + UI walkthrough.
- [E2E_TEST_CHECKLIST.md](E2E_TEST_CHECKLIST.md) — 10-section post-`assembleDebug` smoke test.
- [MASTERCLAUDE.md](MASTERCLAUDE.md) — legacy architecture notes (pre-AI/pre-trip-planner); still useful for the Room schema and base navigation.
- [README.md](README.md) — user-facing readme.

## What's deployed right now

- **URL**: `https://bt-ml-production.up.railway.app`
- **Sanity**: `curl -s $URL/healthz` → `{"status":"ok","model_source":"a1_lightgbm","model_loaded":true,"a1_abort":false,"n_routes_with_intercept":12,"version":"1.0.0"}`
- **Headline**: `curl -s $URL/stats | grep mae_s` → `bt_headline_mae_s ≈ 94.3`, `a1_cv_headline_mae_s ≈ 80.6`
- Cold `/plan`: ~400 ms. Warm cache hit (60 s TTL): ~10 ms.

If redeploying: see `memory/feedback_deploy_patterns.md` for Railway gotchas (`--browserless` login, `sh -c` for $PORT, `healthcheckTimeout: 300`, Directions API must be explicitly enabled).

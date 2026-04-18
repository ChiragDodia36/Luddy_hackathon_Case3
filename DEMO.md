# BT Transit — Demo Cheat Sheet

_IU Luddy Hacks, 2026-04-18. One-pager for the team to glance at before the pitch._

## The one-liner

We rebuilt Bloomington Transit's rider app with a live AI layer that **beats BT's own arrival predictions by 31.3 %** (MAE **64.8 s vs 94.3 s** on the 3–5 min horizon, 5-fold CV on 215k live predictions) and exposes that improvement as a deployed public API any client can hit.

---

## Numbers judges will ask for

| Metric | Value | How measured |
|---|---|---|
| **Baseline (BT) MAE at 3–5 min horizon** | **94.3 s** | Measured in `BASELINE_REPORT.md` — derived from live `position_updates.pb` + `trip_updates.pb` snapshots, ground-truthed via `current_status == STOPPED_AT` + midpoint fallback |
| **Our A1 LightGBM residual model MAE at 3–5 min horizon** | **64.8 s — +31.3 % improvement** | 5-fold GroupKFold on `trip_id` (no within-trip leakage), ~215k OOF rows in the 3–5 min bucket |
| **Top per-route A1 wins (CV OOF vs passthrough)** | Route 4S: 106.0 → 56.0 s (**+47 %**) · Route 4W: 106.9 → 58.9 s (**+45 %**) · Route 11: 138.1 → 71.3 s (**+48 %**) · Route 3W: 109.3 → 70.8 s (**+35 %**) | Retrained on Saturday data |
| **Bias correction for Route 6** | **+82 s** | Saturday retrain flipped the sign — BT now *under*-predicts Route-6 lateness by ~1.4 min. `models/route_intercepts.json` — median `(actual − BT_predicted)` |
| **Training window** | ~19 hours of live GTFS-RT, Friday night → Saturday AM-peak | 14,063 `.pb` snapshots ingested; 215k OOF prediction rows across all horizons |
| **Ground-truth trip coverage** | 12 of 16 BT routes (Route 3E already well-calibrated, intercept 0 s) | 157 unique `(trip_id, vehicle_id)` instances |
| **Inference model** | LightGBM regressor, 13 features | Model trained on Ayan's laptop CPU in <5 min — no GPU/Colab |

### Why LightGBM residual, not a full transformer?

- Data size: ~1,000 labels across 39 trip-instances. Anything bigger would overfit.
- BT already has the hard part (scheduling + vehicle state). Learning the **residual** (actual − BT_predicted) preserves their baseline and refines it.
- Explainability: feature importance is exactly the story we want to tell — top 3 are `trip_progress_fraction`, `route_id`, `bt_trip_delay_seconds`.

---

## Architecture

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                        Android app (ayan/integrate-naishal)                  │
│                                                                              │
│  Home   Map                           AI          Schedule   Favourites      │
│   │      │ (Trip planner — Naishal)    │ (Ayan)      │           │           │
│   │      │  ┌────────────────────────┐ │             │           │           │
│   │      │  │ Places autocomplete    │ │             │           │           │
│   │      │  │ Direct + 1-transfer    │ │             │           │           │
│   │      │  │ routing over GTFS      │ │             │           │           │
│   │      │  │ 0.3 mi radius circle   │ │             │           │           │
│   │      │  │ ── AI line ──────────► │ │             │           │           │
│   │      │  │   ✨ +159s vs BT · med  │ │             │           │           │
│   │      │  └────────────────────────┘ │             │           │           │
│   │      │                             │             │           │           │
│   └──────┴──── BunchingBanner (15 s poll) via /detections/bunching           │
│                                                                              │
│   Diagnostics (debug tab) ──► /stats + /healthz + /vehicles                  │
│                                                                              │
│           data/ai/  BtAiApi · BtAiRepository · @Serializable DTOs            │
│           data/     TransitRepository (GTFS-RT, Room, DataStore)             │
│              ▲                                         ▲                     │
│              │ BuildConfig.BACKEND_BASE_URL            │ GTFS-RT feeds        │
│              │                                         │                     │
└──────────────┼─────────────────────────────────────────┼─────────────────────┘
               │                                         │
               ▼                                         ▼
    ┌───────────────────────┐                  ┌─────────────────────┐
    │ Railway FastAPI       │                  │ ETA Transit S3      │
    │ bt-ml-production      │                  │ bloomingtontransit  │
    │ .up.railway.app       │                  │ .etaspot.net        │
    │                       │                  │                     │
    │ /plan — Google Dirs   │  Directions API  │  position_updates.pb│
    │        + AI enrich ──────────────────────►  trip_updates.pb    │
    │ /predictions — A1+A2  │                  │  alerts.pb          │
    │ /detections/bunching  │                  │  gtfs.zip           │
    │ /stats /vehicles /nlq │                  │                     │
    │                       │                  └─────────────────────┘
    │ A1 LightGBM joblib    │
    │ A2 route_intercepts   │
    │ StaticCache: 512      │
    │   stops, 1210 trips   │
    │                       │
    │ async httpx (HTTP/2)  │
    │ 60 s TTL cache        │
    │ gzip, 2 workers       │
    └───────────────────────┘
```

---

## Live backend — `https://bt-ml-production.up.railway.app`

Every endpoint returns well-shaped JSON with `Content-Encoding: gzip` where it helps. Try:

```bash
BASE=https://bt-ml-production.up.railway.app

# Is the service alive and which model is loaded?
curl -s $BASE/healthz | python3 -m json.tool
#  → {"status":"ok","model_source":"a1_lightgbm","model_loaded":true,...}

# Headline accuracy number (updates as model retrains)
curl -s $BASE/stats | python3 -m json.tool
#  → bt_headline_mae_s: 94.3, a1_cv_headline_mae_s: 64.8, a1_cv_improvement_pct: 31.3

# All 16 BT routes
curl -s $BASE/routes | python3 -c "import json,sys;print(len(json.load(sys.stdin)),'routes')"

# Predictions at a real stop (Indiana Ave & Kirkwood)
curl -s "$BASE/predictions?stop_id=45595&horizon_minutes=60" | python3 -m json.tool

# Full trip plan: College Mall → Hoosier Ct
curl -s "$BASE/plan?origin_lat=39.1674&origin_lng=-86.5240&dest_lat=39.2050&dest_lng=-86.5500" \
  | python3 -m json.tool | head -80

# Live bunching detection (200 m same-route pairs)
curl -s $BASE/detections/bunching | python3 -m json.tool

# Natural-language query (regex + optional Claude fallback)
curl -s "$BASE/nlq?q=when%20does%20the%206%20come" | python3 -m json.tool
```

### What `/plan` returns

Google Directions transit routes, each with per-step walk/bus segments. For every transit step we:

1. Snap Google's `departure_stop.location` to the nearest BT `stops.txt` entry within 80 m.
2. Match Google's `line.short_name` to our `route_id`.
3. Run LightGBM residual correction + Route-6-style intercept on the boarding ETA.
4. Return Google's original time AND our adjusted time AND a confidence tier (high/medium/low) in `departure_stop.ai_adjusted_time_value` + `departure_stop.ai_correction_seconds` + `departure_stop.confidence`.

Latency:
- Cold `/plan`: ~400 ms (Google upstream ~280 ms + our enrichment ~120 ms)
- Warm cache hit (60 s TTL): **~10 ms**
- 2 uvicorn workers → ≈2 concurrent cold calls without queueing

---

## UI walkthrough (what to click during the demo)

1. **Home tab** — hero card with the user's favourite route. If it fires during the demo, a small **bunching alert banner** renders in amber/red above the alerts banner (polled every 15 s from `/detections/bunching`).

2. **Map tab** — Naishal's trip planner:
   - Tap the search bar → type "Indiana Memorial Union" or paste a street address.
   - Google Places autocomplete suggests places.
   - Pick one → up to 3 route plans rank-sorted: **direct routes first**, then 1-transfer plans (with transfer-walk penalty).
   - Each plan shows walk-in distance, route badge(s), "next bus in N min" via live trip_updates, and — for the selected plan — a step-by-step view: Walk → Board bus → (Transfer → Board bus) → Get off → Walk.
   - Under the "Board bus" step: **`✨ AI-adjusted: +82s vs BT · medium`** — this is the A1+A2 correction served via our Railway `/plan` endpoint. When BT's prediction is reliable it says "matches BT"; when it's systematically off (e.g., Route 6) you'll see the correction.
   - On every plan row: a **`✓ Catch it · +N min spare`** / **`⚠ Tight`** / **`✗ Miss by N min`** chip shows whether you can walk to the boarding stop before the bus leaves. Row 0 additionally carries a **`⚡ Fastest`** pill.
   - 0.3-mile proximity circle is drawn around the user's current location; nearby stops filter to that radius.

3. **AI tab** — Ayan's demo surface for `/predictions` directly:
   - Type a stop (e.g. "Kirkwood") → results.
   - Tap a stop → live-polled rows with Scheduled / BT / Ours side-by-side + confidence badge.
   - Tap any row → **Trip ETA propagation screen** showing the full trip's remaining stops with per-stop adjusted times.
   - Natural-language hint chip: type "next 6" or "route 3E" → server returns an intent you can act on.

4. **Diagnostics** (bug-report icon in the Home top bar):
   - Live BT MAE vs our A1 CV MAE with the +14.5 % delta.
   - Fleet health: active vehicle count, how many are stale (>90 s since last `vehicle.timestamp` update).
   - Per-vehicle staleness list.
   - This is the "we know BT's feed better than BT does" card.

---

## If judges ask "what could BT's app not do"

1. **Adjust predictions per route**: BT emits one `arrival.delay` value per trip, copied to every remaining stop (~91 % of trip-snapshots had identical delays across all stops in our audit). We compute per-stop residuals.
2. **Flag systematically-biased routes**: Route 6 was biased by +82 s in our Saturday retrain (BT under-predicts lateness). Routes 4S and 4W have the biggest A1 wins (+47 % and +45 %). Our `/stats` surfaces the headline; `/plan` applies per-route intercepts.
3. **Detect bus bunching**: same-route vehicles within 200 m → banner. BT's own feed has no bunching field.
4. **Flag stale vehicles**: we mark vehicles as stale when `vehicle.timestamp` hasn't advanced in 90 s. BT never signals this.
5. **Full trip planning with live transit delays folded in**: BT's own app doesn't route — riders guess which bus to take.
6. **Confidence tier on every prediction**: high / medium / low so riders know when to trust us vs. when we're mostly passing through BT's number.

---

## If judges ask "what's next"

- **Train on Saturday data** once we have it — current labels are all Friday evening weekday service. Weekend fleet is ~60 % smaller with different route coverage. One pipeline re-run (`scripts/train_a1.py`) picks up any new labels.
- **Model distillation to TFLite** for full-offline inference (deferred; would have been stretch feature C5).
- **Multi-agency generalisation**: the architecture is GTFS-generic. Point `bt-ml` at another small/medium agency's S3 bucket and you get the same pipeline.
- **API key rotation** — the leaked hackathon key needs to be revoked and replaced with a restricted one before any public launch.

---

## Credits

| Track | Owner |
|---|---|
| Data discovery + ground-truth derivation + baseline | Ayan |
| A1 LightGBM model + A2 intercepts + FastAPI service | Ayan |
| FastAPI `/plan` endpoint + Google Directions async client + Railway deploy | Ayan |
| Android AI integration (`data/ai/`, AI tab, Diagnostics, Bunching banner, Trip ETA) | Ayan |
| Android app scaffold (nav, Room, Hilt, domain layer) | Chirag |
| Android UI polish (Home redesign, Map improvements, CountdownChip, ArrivalRow) | Chirag |
| Google Maps-style trip planner (Places SDK, routing, transfer logic) | Naishal |
| Data analysis support | Naishal, Omkar |

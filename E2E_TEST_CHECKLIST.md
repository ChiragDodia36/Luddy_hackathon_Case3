# End-to-End Test Checklist

_Run this immediately after `./gradlew assembleDebug` succeeds. ~10 minutes._

## 0 · Backend sanity (run these regardless of Android state — they're the fastest way to know if the AI stack is healthy)

```bash
BASE=https://bt-ml-production.up.railway.app
curl -s $BASE/healthz | python3 -m json.tool
```

Expected:
```json
{"status":"ok","service":"bt-ml","model_source":"a1_lightgbm","model_loaded":true,"a1_abort":false,"n_routes_with_intercept":12,"version":"1.0.0"}
```

If `model_loaded` is `false` or `model_source` is `passthrough`, the joblib failed to load on boot — check Railway logs at railway.com/dashboard → bt-ml → Deploy Logs.

```bash
curl -s $BASE/stats | python3 -m json.tool | grep -E "mae_s|improvement"
```

Expected: `bt_headline_mae_s` around 94.3, `a1_cv_headline_mae_s` around 64.8 (±3) after Saturday retrain.

```bash
curl -s "$BASE/plan?origin_lat=39.1674&origin_lng=-86.5240&dest_lat=39.2050&dest_lng=-86.5500" | python3 -c "import json,sys;d=json.load(sys.stdin);print('status',d.get('status'),'routes',len(d.get('routes',[])))"
```

Expected: `status OK routes 6` (or similar ≥1 non-zero count).

---

## 1 · Build verifies (before install)

```bash
./gradlew assembleDebug
# → BUILD SUCCESSFUL in <~90 s
# → app/build/outputs/apk/debug/app-debug.apk exists
```

If you see "Unresolved reference: BuildConfig" or "Unresolved reference: ai", run `./gradlew clean assembleDebug` — KSP sometimes needs a fresh state for the Hilt + kotlinx-serialization plugins to agree.

---

## 2 · First launch (emulator OR physical device)

| Step | Expected |
|---|---|
| 2.1 Launch app | Splash → Home tab selected in bottom nav |
| 2.2 Top-bar shows time-greeting + notification bell + **🐞 bug-report icon** | All three visible; bug-report taps into Diagnostics |
| 2.3 Bottom nav shows **5 tabs** — Home / Map / AI / Schedule / Favourites | AI tab (✨ sparkle icon) lives between Map and Schedule |
| 2.4 Location permission prompt fires on Map tab | Accept |

If the AI tab icon is missing the sparkle, `material-icons-extended` didn't resolve `Icons.Filled.AutoAwesome` on the installed SDK — pull latest, clean build.

---

## 3 · AI tab (Ayan — direct /predictions)

| Step | Expected |
|---|---|
| 3.1 Tap AI tab | Search bar + empty state: "Search a stop to see BT vs. AI-adjusted ETAs" |
| 3.2 Type "kirkwood" (≥2 chars) | Results dropdown lists matching stops; sparkle chip may appear if NLQ matches |
| 3.3 Tap a stop result | Row populates with Scheduled / BT / Ours countdowns + confidence badge |
| 3.4 Tap any arrival row | Navigates to Trip ETA propagation screen |
| 3.5 Trip ETA screen | Lists remaining stops with per-stop Sched/BT/Ours + confidence badge per row |
| 3.6 Back button | Returns to stop's arrival list |

**If predictions are empty**: fleet may be off (check time — BT runs ~6 AM – 10 PM ET). Try between 7 AM and 9 PM ET for the cleanest demo.

**If HTTP errors show**: `/predictions?stop_id=X` needs BACKEND_BASE_URL correct. Open `local.properties` — the default in `app/build.gradle.kts` is `https://bt-ml-production.up.railway.app/`. To override, add `BACKEND_BASE_URL=...`.

---

## 4 · Map tab (Naishal — trip planner)

| Step | Expected |
|---|---|
| 4.1 Tap Map | Google Map centred on Bloomington; 0.3-mile proximity circle drawn at user location |
| 4.2 Stop dots visible | ≤~150 stops within 0.3 mile rendered |
| 4.3 Tap search bar | Places autocomplete activates |
| 4.4 Type "Indiana Memorial Union" → tap a Google result | `isRoutingLoading` spinner; then 1–3 plan option rows appear in bottom panel |
| 4.5 Each plan row shows | Route badge(s), duration, next-bus countdown, walk-in distance, estimated total min, "AI: +Ns" chip (see item 7) |
| 4.6 Tap a plan | Detail panel expands: Walk → Board bus (with `✨ AI-adjusted: +Ns vs BT · medium` line) → [optional Transfer] → Get off → Walk |
| 4.7 Map polyline updates to selected route | Colored route path rendered; destination pin at end |

**If no plans found**: destination may be >10-min walk from nearest BT-served stop — try "Sample Gates" (always serviced).

**If the `✨ AI-adjusted` line never appears**: backend is unreachable. Visit `https://bt-ml-production.up.railway.app/healthz` in the phone's browser. If 200 OK from the browser but not from the app, check `local.properties` has no typo in `BACKEND_BASE_URL` (scheme + trailing `/` matter).

---

## 5 · Diagnostics (debug surface)

| Step | Expected |
|---|---|
| 5.1 On Home tab → tap 🐞 icon in top bar | Diagnostics screen |
| 5.2 Card 1 — Model accuracy | Shows `BT headline MAE @ 3-5 min: 94.3 s`, `Our A1 CV MAE: ~64.8 s`, `Improvement: +31.3%`, `model_source: a1_lightgbm`, `a1_abort: false`, `routes_with_intercept: 11` |
| 5.3 Card 2 — Live feed | `Live fleet size` (0 overnight, 6–16 during service), `Stale vehicles (>90 s)`, `Last local refresh` |
| 5.4 Card 3 — Vehicles | Per-vehicle rows with ID, route_id, stale age with **drift delta** next to the age (item 8) |
| 5.5 Error line | Only shown if any of /healthz, /stats, /vehicles failed — copy verbatim into a bug report |

---

## 6 · Home tab (Chirag — canonical path)

| Step | Expected |
|---|---|
| 6.1 Home loads | Greeting, floating header, favourite-route hero card or Route Picker |
| 6.2 If bunching is live | Red/amber `BunchingBanner` appears above the hero card; tap to expand |
| 6.3 Route Picker → pick a route | Navigates to Map tab with that route pre-selected |

**To verify bunching actually triggers**: requires 2 same-route buses within 200 m. Easiest during AM/PM peak on 3E/3W (most-frequent routes). Offline alternative — curl `$BASE/detections/bunching` and check if any events exist.

---

## 7 · New in this build — AI summary chip on plan rows

When a plan is rendered in the list, the summary row shows `AI: +Ns` (or `AI: ±0s`, `AI: unknown`) next to the "next bus in N min" label. This is the headline-level AI correction for that plan's boarding. It previously only appeared inside the detail panel under "Board bus"; now it surfaces in the scannable row too.

Accept criteria: **before tapping into a plan, you can already see whether the AI corrected BT's estimate and by how much.**

---

## 8 · New in this build — Route-6 bias pill

If a plan's `firstRoute.id == "6"`, a small `⚠ Route 6 bias: +82 s baked in` pill renders at the top of the detail panel. This is the storytelling line: Route 6 was the most-biased route in our audit. The Saturday retrain flipped the sign — BT now *under*-predicts Route-6 lateness, so A2 adds +82 s.

Accept criteria: **search "Hoosier Courts" or similar Route-6 destination → plan is Route 6 → pill appears above the step-by-step.**

---

## 9 · New in this build — Vehicle staleness drift

The Diagnostics per-vehicle rows now show both the age (seconds since last `vehicle.timestamp` update) **and** a drift indicator (how much later this vehicle is reporting than its average ~30 s cadence). Example: `#1372 · rt 6 · 72 s old · +42 s drift` means this vehicle is ~42 s later than expected.

Accept criteria: **most vehicles show ≤±10 s drift during normal operation; drifted vehicles are rare but visible.**

---

## 10 · New in this build — AI tab live scoreboard

The AI tab now has a header card above the search bar: `Live: BT 94 s vs Us 81 s · 12 routes corrected`. Pulled from `/stats` at screen entry.

Accept criteria: **numbers match what `curl $BASE/stats` returns within the last minute.**

---

## If anything breaks

1. Screenshot the Logcat error tag `bt.` or `MapVM`.
2. Note: endpoint returning bad data vs endpoint unreachable vs Compose render crash — symptoms are different.
3. Most likely causes in order:
   - Stale `BACKEND_BASE_URL` → rebuild after editing `local.properties`.
   - Backend on cold start → first call after ~5 min idle can take 3–5 s; retry.
   - JDK mismatch → Gradle requires JDK 17, not 21 or 25.
   - `Unresolved reference` → `./gradlew clean && ./gradlew assembleDebug`.

## Done-done criteria

All 10 sections above pass. Total wall-clock for a thorough pass: ~10 min on a physical device, ~15 min on an emulator (slower location fix).

# Team Handoff — BT Transit hackathon project

_This file is a snapshot of the operational knowledge accumulated while building the AI layer and deploying the FastAPI service. Written so anyone pulling this branch can redeploy, retrain, or hand off the project without re-discovering every gotcha._

Last updated: 2026-04-18 (hackathon submission day).

---

## 1 · Live deployed backend

- **Base URL**: `https://bt-ml-production.up.railway.app`
- **Healthcheck**: `curl -s $BASE/healthz` → `{"status":"ok","model_source":"a1_lightgbm","model_loaded":true,"a1_abort":false,"n_routes_with_intercept":12,"version":"1.0.0"}`
- **Headline metric**: `curl -s $BASE/stats | grep mae_s` → `bt_headline_mae_s ≈ 94.3`, `a1_cv_headline_mae_s ≈ 80.6`
- **Railway project ID**: `db8d1898-8b74-4f65-914c-fbb05e29cc59`
- **Railway service ID**: `2bbfe411-17a6-4998-806a-148cbfb76105`
- **Railway dashboard**: `railway.com/dashboard` (logged in as Ayan, `ayansk152@gmail.com`)
- **Workers**: 2 uvicorn workers, HTTP/2 upstream to Google Directions, gzip, 60 s TTL cache on `/plan`.

### Endpoints

| Endpoint | Purpose |
|---|---|
| `GET /healthz` | Liveness + which model is loaded |
| `GET /stats` | Live BT-vs-A1 MAE numbers, fleet size, model source |
| `GET /routes` | All 16 BT routes |
| `GET /stops?q=kirkwood` | Stop search |
| `GET /predictions?stop_id=45595&horizon_minutes=60` | Per-stop AI-adjusted ETAs |
| `GET /plan?origin_lat=&origin_lng=&dest_lat=&dest_lng=` | Google Directions + AI enrichment for boarding ETAs |
| `GET /vehicles` | Live fleet with staleness |
| `GET /detections/bunching` | Same-route vehicles within 200 m |
| `GET /nlq?q=when+does+the+6+come` | Regex + optional Claude-fallback intent parser |

## 2 · Repo branch topology

| Branch | Role |
|---|---|
| `main` | Submission target. PR #1 (AI layer) merged via `e3ca256`. |
| `ayan/integrate-naishal` | Demo branch. PR #2, 4 commits ahead of main, clean-mergeable. |
| `bt-ml-service` | **Orphan branch** (no common ancestor with main) carrying the entire Python FastAPI service + Dockerfile + railway.json. Railway auto-deploys from this. |
| `Naishal`, `chiggy-v1` | Feature branches, merged — keep for history. |
| `Omkar` | Data-analysis only (`results.csv`, `requirements.txt`) — not wired in. |

**Working directories on Ayan's laptop** (for redeploy / retrain):
- Android app: `/Users/ayansk11/Desktop/Luddy_hackathon_Case3/`
- Python ML service: `/Users/ayansk11/Desktop/bt-ml/`

## 3 · Deployment gotchas (Railway + Docker + Google)

Things that burned time and are not obvious from docs. Listed in the order you'll hit them.

### 3.1 · `railway login` can silently fail → use `--browserless`

Running `railway login` opens the browser, browser shows "Authentication successful!", CLI still says `Unauthorized`. Happened on both Safari and Chrome.

**Fix**: `railway login --browserless`. CLI prints a pairing code, paste it into the browser, done.

### 3.2 · Dockerfile CMD must expand `$PORT` via a shell

Railway injects `PORT` as an env var. Exec-form CMD (JSON array) doesn't run a shell — `$PORT` is passed literally and uvicorn errors out with "not a valid integer".

**Fix**: wrap with `sh -c`:
```dockerfile
CMD ["sh", "-c", "uvicorn service.app.main:app --host 0.0.0.0 --port ${PORT:-8000} --workers 2"]
```

### 3.3 · Do NOT put a `startCommand` in `railway.json` if your Dockerfile has CMD

Railway prefers `railway.json.deploy.startCommand` when both exist, and that path runs without a shell — bringing back the `$PORT` issue.

**Fix**: keep `railway.json.deploy` minimal — just `restartPolicyType`, `healthcheckPath`, `healthcheckTimeout`. Let the Dockerfile own startup.

### 3.4 · ML services need `healthcheckTimeout: 300`

Default is ~10 s. LightGBM + GTFS static load takes 4-8 s cold, so deploy reports "1/1 replicas never became healthy!" and rolls back.

**Fix**: `"healthcheckTimeout": 300` in `railway.json`. It's a max, not a min — warm service still passes in <1 s.

### 3.5 · Google "Directions API (Legacy)" must be explicitly enabled

Fresh Google Cloud projects have only Routes API enabled. Directions API is labelled "Legacy" and OFF; calling it returns `REQUEST_DENIED` with "You're calling a legacy API, which is not enabled for your project."

**Fix**: `console.cloud.google.com` → APIs & Services → Library → "Directions API" → Enable. Same for Places API (not "Places API (New)") if you enable the Places SDK.

### 3.6 · 2 uvicorn workers is the free-tier sweet spot

1 worker serialises all requests; 10 concurrent `/plan` calls queue from 1.4 s → 11.4 s. 2 workers cut worst-case to ~6 s. Each worker is ~80 MB RSS after LightGBM + GTFS static cache load, which fits inside Railway's 512 MB free tier with room to spare. 4 workers risks OOM.

## 4 · Secrets to rotate post-submission

**Policy**: placeholders only, never the full value.

| Secret | Placeholder | Where exposed | Rotation action |
|---|---|---|---|
| Google Maps / Directions / Places API key | `AIzaSyBc…dQ8E` | Committed to `local.properties` on `main`; also baked into distributed APKs as `BuildConfig.MAPS_API_KEY` | Cloud Console → APIs & Services → Credentials: delete, create new one restricted to Android by SHA-1 + package `com.luddy.bloomington_transit` + API allow-list (Maps Android, Directions, Places). Update `local.properties` on all teammates. |
| HuggingFace access token | `hf_xpIY…FgmvB` | Pasted in dev chat; never committed to any file | `huggingface.co/settings/tokens` → revoke; regenerate if needed. |

No Anthropic/Claude API key was exposed. Railway CLI uses cookie auth (already expired). No GitHub PAT in play (SSH auth only).

## 5 · If you need to redeploy

```bash
cd /Users/ayansk11/Desktop/bt-ml
railway login --browserless        # if not already logged in
railway link                       # pick project db8d1898-…
railway up                         # builds Dockerfile, pushes, deploys
# → wait ~60-90 s, then: curl $BASE/healthz
```

The `bt-ml-service` orphan branch on GitHub is also wired to Railway; pushing to it auto-deploys. So `git push luddy main:bt-ml-service` from `/Users/ayansk11/Desktop/bt-ml` (local `main`, remote `luddy` alias for `ChiragDodia36/Luddy_hackathon_Case3`) triggers a deploy without CLI.

## 6 · If you need to retrain A1

All scripts live at `/Users/ayansk11/Desktop/bt-ml/scripts/`:

1. `./scripts/run_logger.sh` — captures `.pb` snapshots to `/Users/ayansk11/Desktop/Bloomington Transit App/gtfs_logs/` (the audit dir, preserved from discovery phase). Leave running.
2. `python scripts/derive_actuals.py` — ground-truth labels from `STOPPED_AT` + midpoint fallback.
3. `python scripts/compute_bt_error.py` — baseline BT-vs-actual residuals.
4. `python scripts/build_dataset.py` — feature engineering, GroupKFold folds on `trip_id`.
5. `python scripts/train_a1.py` — LightGBM residual regressor, emits `models/a1_lightgbm.joblib`.
6. `python scripts/build_route_intercepts.py` — A2 per-route bias intercepts, emits `models/route_intercepts.json`.
7. Commit + `railway up` (or push `bt-ml-service`).

Verify: `curl $BASE/stats` → new `a1_cv_headline_mae_s` should reflect the retrain.

## 7 · Measured numbers (5-fold GroupKFold on `trip_id`, 29,706 labelled predictions)

- BT baseline MAE @ 3-5 min horizon: **94.3 s**
- A1 LightGBM residual MAE @ 3-5 min: **64.8 s (+31.3 %)** after Saturday retrain
- Top per-route A1 wins: Route 4S 106→56 s (+47 %), Route 4W 107→59 s (+45 %), Route 11 138→71 s (+48 %), Route 3W 109→71 s (+35 %)
- Route 6 intercept: **+82 s** — Saturday data flipped the sign (Friday was −154 s). BT now *under*-predicts Route-6 lateness.
- Training window: ~19 h Fri-night + Sat-AM-peak on 14,063 `.pb` snapshots
- 215k OOF prediction rows · 157 unique `(trip_id, vehicle_id)` instances · 12 of 16 routes covered (Route 3E calibrated to 0 s)

## 8 · Where to look next

- `DEMO.md` — one-page pitch, architecture diagram, endpoint curl examples, UI walkthrough.
- `E2E_TEST_CHECKLIST.md` — 10-section post-`assembleDebug` smoke test.
- `CLAUDE.md` — repo-root project context for Claude Code.
- Per-package `CLAUDE.md` under `app/src/main/java/com/luddy/bloomington_transit/{data,domain,service,ui,widget}/` — layer-level specifics.
- `MASTERCLAUDE.md` + `MASTERIMPLEMENTATION.md` — legacy pre-AI architecture notes; still useful for Room schema and base nav.

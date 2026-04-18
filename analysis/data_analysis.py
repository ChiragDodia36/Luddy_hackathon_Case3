"""
Bloomington Transit GTFS Dataset Analyzer
==========================================
Analyzes:
  - Static:    gtfs.zip          (GTFS schedule)
  - RT-1:      alerts.pb         (Service Alerts)
  - RT-2:      position_updates.pb (Vehicle Positions)
  - RT-3:      trip_updates.pb   (Trip Updates)

Run:
    pip install gtfs-realtime-bindings requests pandas tabulate
    python analyze_gtfs.py
"""

import requests
import zipfile
import io
import time
import json
import csv
import datetime
from collections import defaultdict
from typing import Any

# ── pip install these ──────────────────────────────────────────────────────────
import pandas as pd
from google.transit import gtfs_realtime_pb2
from tabulate import tabulate
# ──────────────────────────────────────────────────────────────────────────────

STATIC_URL = "https://s3.amazonaws.com/etatransit.gtfs/bloomingtontransit.etaspot.net/gtfs.zip"
RT_URLS = {
    "alerts":           "https://s3.amazonaws.com/etatransit.gtfs/bloomingtontransit.etaspot.net/alerts.pb",
    "vehicle_positions":"https://s3.amazonaws.com/etatransit.gtfs/bloomingtontransit.etaspot.net/position_updates.pb",
    "trip_updates":     "https://s3.amazonaws.com/etatransit.gtfs/bloomingtontransit.etaspot.net/trip_updates.pb",
}

SEP = "\n" + "="*80 + "\n"

# ─────────────────────────────────────────────────────────────────────────────
# HELPERS
# ─────────────────────────────────────────────────────────────────────────────

def fetch_with_timing(url: str, label: str) -> tuple[bytes | None, float, float]:
    """Returns (content_bytes, http_latency_ms, total_latency_ms)"""
    print(f"\n[FETCH] {label} ...")
    t0 = time.perf_counter()
    try:
        resp = requests.get(url, timeout=30)
        t1 = time.perf_counter()
        resp.raise_for_status()
        t2 = time.perf_counter()
        http_ms   = (t1 - t0) * 1000
        total_ms  = (t2 - t0) * 1000
        print(f"  Status: {resp.status_code}  |  Size: {len(resp.content)/1024:.1f} KB"
              f"  |  HTTP latency: {http_ms:.0f} ms  |  Total: {total_ms:.0f} ms")
        return resp.content, http_ms, total_ms
    except Exception as e:
        print(f"  ERROR: {e}")
        return None, -1, -1


def ts_to_str(ts: int) -> str:
    if ts == 0:
        return "N/A"
    try:
        return datetime.datetime.utcfromtimestamp(ts).strftime("%Y-%m-%d %H:%M:%S UTC")
    except Exception:
        return str(ts)


def age_seconds(ts: int) -> float | str:
    if ts == 0:
        return "N/A"
    return round(time.time() - ts, 1)


# ─────────────────────────────────────────────────────────────────────────────
# 1. STATIC GTFS
# ─────────────────────────────────────────────────────────────────────────────

GTFS_REQUIRED_FILES = {
    "agency.txt":       ["agency_id","agency_name","agency_url","agency_timezone"],
    "routes.txt":       ["route_id","route_short_name","route_long_name","route_type"],
    "trips.txt":        ["route_id","service_id","trip_id"],
    "stop_times.txt":   ["trip_id","arrival_time","departure_time","stop_id","stop_sequence"],
    "stops.txt":        ["stop_id","stop_name","stop_lat","stop_lon"],
    "calendar.txt":     ["service_id","monday","tuesday","wednesday","thursday","friday","saturday","sunday","start_date","end_date"],
}
GTFS_OPTIONAL_FILES = [
    "calendar_dates.txt","fare_attributes.txt","fare_rules.txt",
    "shapes.txt","frequencies.txt","transfers.txt","feed_info.txt"
]

def analyze_static(content: bytes):
    print(SEP + "STATIC GTFS ANALYSIS (gtfs.zip)")
    zf = zipfile.ZipFile(io.BytesIO(content))
    names = zf.namelist()
    print(f"\nFiles inside zip ({len(names)} total):")

    dfs: dict[str, pd.DataFrame] = {}
    for name in names:
        with zf.open(name) as f:
            try:
                df = pd.read_csv(f, dtype=str, low_memory=False)
                df.columns = [c.strip().lstrip('\ufeff') for c in df.columns]
                dfs[name] = df
                kb = len(zf.read(name)) / 1024
                print(f"  {name:<30} rows={len(df):<8} cols={len(df.columns):<4} size={kb:.1f}KB")
            except Exception as e:
                print(f"  {name:<30} PARSE ERROR: {e}")

    # --- per-file details ---
    for fname, expected_cols in GTFS_REQUIRED_FILES.items():
        if fname not in dfs:
            print(f"\n[MISSING REQUIRED FILE] {fname}")
            continue
        df = dfs[fname]
        print(f"\n{'─'*60}")
        print(f"FILE: {fname}")
        print(f"  Columns : {list(df.columns)}")
        print(f"  Rows    : {len(df)}")

        # nulls
        null_summary = df.isnull().sum()
        null_cols = null_summary[null_summary > 0]
        if not null_cols.empty:
            print(f"  Nulls   : {dict(null_cols)}")
        else:
            print(f"  Nulls   : none")

        # duplicates on primary key heuristic
        pk_candidates = [c for c in ["trip_id","stop_id","route_id","service_id","agency_id"] if c in df.columns]
        if pk_candidates:
            pk = pk_candidates[0]
            dups = df[pk].duplicated().sum()
            print(f"  Dup '{pk}': {dups}")

        # optional: show sample
        # print(df.head(2).to_string())

    # --- optional files presence ---
    print(f"\nOptional files present:")
    for f in GTFS_OPTIONAL_FILES:
        status = "YES" if f in dfs else "no"
        print(f"  {f:<30} {status}")

    # --- shapes coverage ---
    if "shapes.txt" in dfs and "trips.txt" in dfs:
        trips = dfs["trips.txt"]
        has_shape = "shape_id" in trips.columns
        if has_shape:
            missing_shape = trips["shape_id"].isnull().sum()
            print(f"\nTrips missing shape_id: {missing_shape} / {len(trips)}")

    # --- stop_times integrity ---
    if "stop_times.txt" in dfs and "stops.txt" in dfs:
        st  = dfs["stop_times.txt"]
        stops = dfs["stops.txt"]
        orphan_stops = set(st["stop_id"]) - set(stops["stop_id"])
        print(f"\nOrphan stop_ids in stop_times (not in stops.txt): {len(orphan_stops)}")

    # --- calendar date range ---
    if "calendar.txt" in dfs:
        cal = dfs["calendar.txt"]
        if "start_date" in cal.columns and "end_date" in cal.columns:
            print(f"\nCalendar date range: {cal['start_date'].min()} → {cal['end_date'].max()}")

    # --- coordinate sanity (Bloomington IN bbox) ---
    if "stops.txt" in dfs:
        stops = dfs["stops.txt"]
        stops["stop_lat"] = pd.to_numeric(stops["stop_lat"], errors="coerce")
        stops["stop_lon"] = pd.to_numeric(stops["stop_lon"], errors="coerce")
        lat_ok = stops["stop_lat"].between(39.0, 40.0)
        lon_ok = stops["stop_lon"].between(-87.0, -86.0)
        bad_coords = (~(lat_ok & lon_ok)).sum()
        print(f"\nStops with suspicious coordinates (outside Bloomington IN bbox): {bad_coords}")
        print(f"  Lat range: {stops['stop_lat'].min():.4f} → {stops['stop_lat'].max():.4f}")
        print(f"  Lon range: {stops['stop_lon'].min():.4f} → {stops['stop_lon'].max():.4f}")


# ─────────────────────────────────────────────────────────────────────────────
# 2. REALTIME: VEHICLE POSITIONS
# ─────────────────────────────────────────────────────────────────────────────

def analyze_vehicle_positions(content: bytes):
    print(SEP + "REALTIME: VEHICLE POSITIONS (position_updates.pb)")
    feed = gtfs_realtime_pb2.FeedMessage()
    feed.ParseFromString(content)

    hdr = feed.header
    feed_ts = hdr.timestamp
    now = time.time()
    feed_age = round(now - feed_ts, 1)

    print(f"\nFeed header:")
    print(f"  GTFS-RT version : {hdr.gtfs_realtime_version}")
    print(f"  Incrementality  : {hdr.incrementality}")
    print(f"  Feed timestamp  : {ts_to_str(feed_ts)}  (age: {feed_age}s)")
    print(f"  Total entities  : {len(feed.entity)}")

    records = []
    issues  = defaultdict(list)

    for ent in feed.entity:
        r: dict[str, Any] = {"entity_id": ent.id}

        if ent.HasField("vehicle"):
            vp = ent.vehicle
            # trip descriptor
            r["trip_id"]        = vp.trip.trip_id     if vp.HasField("trip") else None
            r["route_id"]       = vp.trip.route_id    if vp.HasField("trip") else None
            r["vehicle_id"]     = vp.vehicle.id        if vp.HasField("vehicle") else None
            r["vehicle_label"]  = vp.vehicle.label     if vp.HasField("vehicle") else None
            # position
            r["latitude"]       = vp.position.latitude  if vp.HasField("position") else None
            r["longitude"]      = vp.position.longitude if vp.HasField("position") else None
            r["bearing"]        = vp.position.bearing   if vp.HasField("position") else None
            r["speed_mps"]      = vp.position.speed     if vp.HasField("position") else None
            # status
            r["current_status"] = vp.current_status     # enum int
            r["timestamp"]      = vp.timestamp
            r["ts_str"]         = ts_to_str(vp.timestamp)
            r["age_s"]          = age_seconds(vp.timestamp)

            # --- inconsistency checks ---
            if r["latitude"] is None:
                issues["missing_position"].append(ent.id)
            if r["trip_id"] is None or r["trip_id"] == "":
                issues["missing_trip_id"].append(ent.id)
            if r["timestamp"] == 0:
                issues["missing_vehicle_timestamp"].append(ent.id)
            if isinstance(r["age_s"], float) and r["age_s"] > 120:
                issues["stale_vehicle_gt_120s"].append(ent.id)
            if r["bearing"] is not None and not (0 <= r["bearing"] <= 360):
                issues["invalid_bearing"].append(ent.id)
        else:
            issues["entity_no_vehicle_field"].append(ent.id)

        records.append(r)

    if records:
        df = pd.DataFrame(records)
        print(f"\nFields per vehicle entity:")
        print(f"  {list(df.columns)}")
        print(f"\nSample (first 5):")
        print(df.head(5).to_string(index=False))
        print(f"\nAge stats (seconds since vehicle timestamp):")
        ages = pd.to_numeric(df["age_s"], errors="coerce").dropna()
        if not ages.empty:
            print(f"  min={ages.min():.1f}  max={ages.max():.1f}  mean={ages.mean():.1f}  median={ages.median():.1f}")

    print(f"\nInconsistencies found:")
    if not issues:
        print("  None detected")
    for k, v in issues.items():
        print(f"  {k}: {len(v)} entities  {v[:5]}{'...' if len(v)>5 else ''}")


# ─────────────────────────────────────────────────────────────────────────────
# 3. REALTIME: TRIP UPDATES
# ─────────────────────────────────────────────────────────────────────────────

def analyze_trip_updates(content: bytes):
    print(SEP + "REALTIME: TRIP UPDATES (trip_updates.pb)")
    feed = gtfs_realtime_pb2.FeedMessage()
    feed.ParseFromString(content)

    hdr = feed.header
    feed_ts = hdr.timestamp
    feed_age = round(time.time() - feed_ts, 1)

    print(f"\nFeed header:")
    print(f"  GTFS-RT version : {hdr.gtfs_realtime_version}")
    print(f"  Feed timestamp  : {ts_to_str(feed_ts)}  (age: {feed_age}s)")
    print(f"  Total entities  : {len(feed.entity)}")

    records   = []
    stu_rows  = []
    issues    = defaultdict(list)

    for ent in feed.entity:
        if not ent.HasField("trip_update"):
            issues["entity_no_trip_update"].append(ent.id)
            continue

        tu = ent.trip_update
        r: dict[str, Any] = {
            "entity_id":  ent.id,
            "trip_id":    tu.trip.trip_id,
            "route_id":   tu.trip.route_id,
            "start_date": tu.trip.start_date,
            "start_time": tu.trip.start_time,
            "vehicle_id": tu.vehicle.id if tu.HasField("vehicle") else None,
            "timestamp":  tu.timestamp,
            "ts_str":     ts_to_str(tu.timestamp),
            "age_s":      age_seconds(tu.timestamp),
            "n_stop_time_updates": len(tu.stop_time_update),
        }

        # checks
        if r["trip_id"] == "":
            issues["missing_trip_id"].append(ent.id)
        if r["timestamp"] == 0:
            issues["missing_update_timestamp"].append(ent.id)
        if isinstance(r["age_s"], float) and r["age_s"] > 120:
            issues["stale_update_gt_120s"].append(ent.id)
        if r["n_stop_time_updates"] == 0:
            issues["no_stop_time_updates"].append(ent.id)

        for stu in tu.stop_time_update:
            stu_rec = {
                "entity_id":   ent.id,
                "trip_id":     r["trip_id"],
                "stop_id":     stu.stop_id,
                "stop_sequence": stu.stop_sequence,
                "arr_delay":   stu.arrival.delay   if stu.HasField("arrival") else None,
                "dep_delay":   stu.departure.delay if stu.HasField("departure") else None,
                "arr_time":    ts_to_str(stu.arrival.time)   if stu.HasField("arrival")   and stu.arrival.time else None,
                "dep_time":    ts_to_str(stu.departure.time) if stu.HasField("departure") and stu.departure.time else None,
                "schedule_rel": stu.schedule_relationship,
            }
            # negative delay = early, check extreme values
            for delay_field in ["arr_delay","dep_delay"]:
                d = stu_rec[delay_field]
                if d is not None and abs(d) > 3600:
                    issues[f"extreme_{delay_field}_gt_1hr"].append(f"{ent.id}@{stu.stop_id}")
            stu_rows.append(stu_rec)

        records.append(r)

    if records:
        df = pd.DataFrame(records)
        print(f"\nTrip-level fields: {list(df.columns)}")
        print(f"\nSample (first 5):")
        print(df.head(5).to_string(index=False))

        ages = pd.to_numeric(df["age_s"], errors="coerce").dropna()
        if not ages.empty:
            print(f"\nUpdate age stats (seconds):")
            print(f"  min={ages.min():.1f}  max={ages.max():.1f}  mean={ages.mean():.1f}")

    if stu_rows:
        sdf = pd.DataFrame(stu_rows)
        print(f"\nStop-time-update fields: {list(sdf.columns)}")
        print(f"Total stop-time-updates: {len(sdf)}")
        arr_delays = sdf["arr_delay"].dropna()
        if not arr_delays.empty:
            print(f"\nArrival delay distribution (seconds):")
            print(f"  min={arr_delays.min():.0f}  p25={arr_delays.quantile(.25):.0f}  "
                  f"median={arr_delays.median():.0f}  p75={arr_delays.quantile(.75):.0f}  max={arr_delays.max():.0f}")

    print(f"\nInconsistencies found:")
    if not issues:
        print("  None detected")
    for k, v in issues.items():
        print(f"  {k}: {len(v)}  {v[:5]}{'...' if len(v)>5 else ''}")


# ─────────────────────────────────────────────────────────────────────────────
# 4. REALTIME: SERVICE ALERTS
# ─────────────────────────────────────────────────────────────────────────────

def analyze_alerts(content: bytes):
    print(SEP + "REALTIME: SERVICE ALERTS (alerts.pb)")
    feed = gtfs_realtime_pb2.FeedMessage()
    feed.ParseFromString(content)

    hdr = feed.header
    feed_ts = hdr.timestamp
    feed_age = round(time.time() - feed_ts, 1)

    print(f"\nFeed header:")
    print(f"  GTFS-RT version : {hdr.gtfs_realtime_version}")
    print(f"  Feed timestamp  : {ts_to_str(feed_ts)}  (age: {feed_age}s)")
    print(f"  Total entities  : {len(feed.entity)}")

    records = []
    issues  = defaultdict(list)
    now     = time.time()

    for ent in feed.entity:
        if not ent.HasField("alert"):
            issues["entity_no_alert"].append(ent.id)
            continue

        alert = ent.alert
        effect     = alert.effect        # enum int
        cause      = alert.cause         # enum int
        header_txt = " | ".join(t.text for t in alert.header_text.translation) if alert.header_text else ""
        desc_txt   = " | ".join(t.text for t in alert.description_text.translation) if alert.description_text else ""
        url_txt    = " | ".join(t.text for t in alert.url.translation) if alert.url else ""

        # active periods
        active_periods = [(ap.start, ap.end) for ap in alert.active_period]
        # are any active now?
        is_active_now = any(
            (s == 0 or s <= now) and (e == 0 or e >= now)
            for s, e in active_periods
        ) if active_periods else True  # no period = always active

        # informed entities
        informed = [
            {"route_id": ie.route_id, "stop_id": ie.stop_id, "trip_id": ie.trip.trip_id if ie.HasField("trip") else ""}
            for ie in alert.informed_entity
        ]

        r = {
            "entity_id":         ent.id,
            "effect":            effect,
            "cause":             cause,
            "n_active_periods":  len(active_periods),
            "is_active_now":     is_active_now,
            "n_informed_entities": len(informed),
            "header":            header_txt[:80],
            "description":       desc_txt[:100],
        }

        # checks
        if not header_txt:
            issues["missing_header_text"].append(ent.id)
        if not informed:
            issues["no_informed_entity"].append(ent.id)

        records.append(r)

    if records:
        df = pd.DataFrame(records)
        print(f"\nAlert fields: {list(df.columns)}")
        print(f"\nAll alerts:")
        print(df.to_string(index=False))

    print(f"\nInconsistencies found:")
    if not issues:
        print("  None detected")
    for k, v in issues.items():
        print(f"  {k}: {len(v)}  {v[:5]}{'...' if len(v)>5 else ''}")


# ─────────────────────────────────────────────────────────────────────────────
# 5. LATENCY: fetch N times to measure consistency
# ─────────────────────────────────────────────────────────────────────────────

def measure_latency(n_samples: int = 5):
    print(SEP + f"LATENCY MEASUREMENT ({n_samples} samples each endpoint)")
    all_urls = {"gtfs_static": STATIC_URL, **RT_URLS}
    results = {}

    for label, url in all_urls.items():
        latencies = []
        for i in range(n_samples):
            t0 = time.perf_counter()
            try:
                r = requests.get(url, timeout=30)
                r.raise_for_status()
                latencies.append((time.perf_counter() - t0) * 1000)
            except Exception as e:
                latencies.append(None)
            time.sleep(0.5)

        valid = [l for l in latencies if l is not None]
        results[label] = {
            "samples":  latencies,
            "min_ms":   round(min(valid),1) if valid else None,
            "max_ms":   round(max(valid),1) if valid else None,
            "mean_ms":  round(sum(valid)/len(valid),1) if valid else None,
            "errors":   latencies.count(None),
        }
        print(f"\n  {label}:")
        print(f"    samples : {latencies}")
        print(f"    min/mean/max ms : {results[label]['min_ms']} / {results[label]['mean_ms']} / {results[label]['max_ms']}")
        print(f"    errors  : {results[label]['errors']}/{n_samples}")

    return results


# ─────────────────────────────────────────────────────────────────────────────
# MAIN
# ─────────────────────────────────────────────────────────────────────────────

def main():
    print("\n" + "="*80)
    print("  BLOOMINGTON TRANSIT GTFS DATASET ANALYSIS")
    print("  Run at:", datetime.datetime.utcnow().strftime("%Y-%m-%d %H:%M:%S UTC"))
    print("="*80)

    # --- Static ---
    static_bytes, _, _ = fetch_with_timing(STATIC_URL, "Static GTFS (gtfs.zip)")
    if static_bytes:
        analyze_static(static_bytes)

    # --- Realtime ---
    rt_data = {}
    for label, url in RT_URLS.items():
        content, http_ms, total_ms = fetch_with_timing(url, label)
        if content:
            rt_data[label] = (content, http_ms, total_ms)

    if "vehicle_positions" in rt_data:
        analyze_vehicle_positions(rt_data["vehicle_positions"][0])

    if "trip_updates" in rt_data:
        analyze_trip_updates(rt_data["trip_updates"][0])

    if "alerts" in rt_data:
        analyze_alerts(rt_data["alerts"][0])

    # --- Latency (only RT, skip static for speed) ---
    print(SEP + "LATENCY MEASUREMENT (RT feeds, 3 samples each)")
    for label, url in RT_URLS.items():
        latencies = []
        for _ in range(3):
            t0 = time.perf_counter()
            try:
                r = requests.get(url, timeout=15)
                r.raise_for_status()
                latencies.append(round((time.perf_counter()-t0)*1000, 1))
            except:
                latencies.append(None)
            time.sleep(0.3)
        valid = [l for l in latencies if l is not None]
        print(f"\n  {label}:")
        print(f"    raw (ms): {latencies}")
        if valid:
            print(f"    mean: {sum(valid)/len(valid):.1f} ms  |  min: {min(valid):.1f}  |  max: {max(valid):.1f}")


if __name__ == "__main__":
    main()
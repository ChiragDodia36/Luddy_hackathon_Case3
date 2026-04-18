"""
Bloomington Transit — Static GTFS Deep Analyzer
================================================
Source: https://s3.amazonaws.com/etatransit.gtfs/bloomingtontransit.etaspot.net/gtfs.zip

Sections:
  0. Fetch & inventory
  1. Schema validation     — required files / columns present?
  2. Per-file profiling    — row counts, nulls, dtypes, uniques
  3. Referential integrity — cross-file FK checks
  4. Coordinate sanity     — bbox, zero-coords, duplicates
  5. Time / calendar       — date ranges, expired services, overnight times
  6. Trip / shape coverage — trips without shapes, orphan shapes
  7. Route / stop stats    — quick summary for downstream use
  8. Summary report        — one-page health score

Install:
    pip install requests pandas tabulate colorama
Run:
    python analyze_static_gtfs.py
"""

import io
import re
import sys
import time
import zipfile
import datetime
import requests
import pandas as pd
from collections import defaultdict
from tabulate import tabulate

# optional colour — gracefully degrades if not installed
try:
    from colorama import Fore, Style, init as colorama_init
    colorama_init(autoreset=True)
    RED   = Fore.RED
    YEL   = Fore.YELLOW
    GRN   = Fore.GREEN
    CYN   = Fore.CYAN
    RST   = Style.RESET_ALL
except ImportError:
    RED = YEL = GRN = CYN = RST = ""

STATIC_URL = (
    "https://s3.amazonaws.com/etatransit.gtfs/"
    "bloomingtontransit.etaspot.net/gtfs.zip"
)

# Bloomington, IN approximate bounding box
LAT_MIN, LAT_MAX =  39.00,  39.35
LON_MIN, LON_MAX = -86.65, -86.35

# ── GTFS spec: required vs optional files and their required columns ──────────
GTFS_SCHEMA: dict[str, dict] = {
    "agency.txt": {
        "required": True,
        "required_cols": ["agency_name", "agency_url", "agency_timezone"],
        "optional_cols": ["agency_id", "agency_lang", "agency_phone", "agency_fare_url", "agency_email"],
    },
    "routes.txt": {
        "required": True,
        "required_cols": ["route_id", "route_type"],
        "optional_cols": ["agency_id", "route_short_name", "route_long_name", "route_desc",
                          "route_url", "route_color", "route_text_color", "route_sort_order"],
    },
    "trips.txt": {
        "required": True,
        "required_cols": ["route_id", "service_id", "trip_id"],
        "optional_cols": ["trip_headsign", "trip_short_name", "direction_id",
                          "block_id", "shape_id", "wheelchair_accessible", "bikes_allowed"],
    },
    "stop_times.txt": {
        "required": True,
        "required_cols": ["trip_id", "stop_id", "stop_sequence"],
        "optional_cols": ["arrival_time", "departure_time", "stop_headsign",
                          "pickup_type", "drop_off_type", "shape_dist_traveled", "timepoint"],
    },
    "stops.txt": {
        "required": True,
        "required_cols": ["stop_id"],
        "optional_cols": ["stop_code", "stop_name", "stop_desc", "stop_lat", "stop_lon",
                          "zone_id", "stop_url", "location_type", "parent_station",
                          "stop_timezone", "wheelchair_boarding", "level_id", "platform_code"],
    },
    "calendar.txt": {
        "required": False,   # required unless calendar_dates.txt covers all services
        "required_cols": ["service_id", "monday", "tuesday", "wednesday", "thursday",
                          "friday", "saturday", "sunday", "start_date", "end_date"],
        "optional_cols": [],
    },
    "calendar_dates.txt": {
        "required": False,
        "required_cols": ["service_id", "date", "exception_type"],
        "optional_cols": [],
    },
    "shapes.txt": {
        "required": False,
        "required_cols": ["shape_id", "shape_pt_lat", "shape_pt_lon", "shape_pt_sequence"],
        "optional_cols": ["shape_dist_traveled"],
    },
    "fare_attributes.txt": {
        "required": False,
        "required_cols": ["fare_id", "price", "currency_type", "payment_method", "transfers"],
        "optional_cols": ["agency_id", "transfer_duration"],
    },
    "fare_rules.txt": {
        "required": False,
        "required_cols": ["fare_id"],
        "optional_cols": ["route_id", "origin_id", "destination_id", "contains_id"],
    },
    "frequencies.txt": {
        "required": False,
        "required_cols": ["trip_id", "start_time", "end_time", "headway_secs"],
        "optional_cols": ["exact_times"],
    },
    "transfers.txt": {
        "required": False,
        "required_cols": ["from_stop_id", "to_stop_id", "transfer_type"],
        "optional_cols": ["min_transfer_time"],
    },
    "feed_info.txt": {
        "required": False,
        "required_cols": ["feed_publisher_name", "feed_publisher_url", "feed_lang"],
        "optional_cols": ["feed_start_date", "feed_end_date", "feed_version", "feed_contact_email", "feed_contact_url"],
    },
    "pathways.txt": {
        "required": False,
        "required_cols": ["pathway_id", "from_stop_id", "to_stop_id", "pathway_mode", "is_bidirectional"],
        "optional_cols": [],
    },
}

SEP  = "\n" + "═" * 80
SEP2 = "\n" + "─" * 60


# ─────────────────────────────────────────────────────────────────────────────
# UTILITIES
# ─────────────────────────────────────────────────────────────────────────────

def header(title: str):
    print(f"{SEP}\n  {CYN}{title}{RST}")


def subheader(title: str):
    print(f"{SEP2}\n  {title}")


def ok(msg):   print(f"  {GRN}✔  {msg}{RST}")
def warn(msg): print(f"  {YEL}⚠  {msg}{RST}")
def err(msg):  print(f"  {RED}✘  {msg}{RST}")
def info(msg): print(f"     {msg}")


def parse_gtfs_time(t: str) -> int | None:
    """Parse HH:MM:SS (hours may exceed 23) → total seconds. Returns None on failure."""
    if not isinstance(t, str) or t.strip() == "":
        return None
    parts = t.strip().split(":")
    if len(parts) != 3:
        return None
    try:
        h, m, s = int(parts[0]), int(parts[1]), int(parts[2])
        return h * 3600 + m * 60 + s
    except ValueError:
        return None


# ─────────────────────────────────────────────────────────────────────────────
# SECTION 0 — FETCH
# ─────────────────────────────────────────────────────────────────────────────

def fetch_zip(url: str) -> tuple[bytes, float]:
    header("SECTION 0 · Fetch")
    t0 = time.perf_counter()
    print(f"  URL : {url}")
    resp = requests.get(url, timeout=60)
    resp.raise_for_status()
    elapsed = (time.perf_counter() - t0) * 1000
    size_kb = len(resp.content) / 1024
    ok(f"HTTP {resp.status_code}  |  {size_kb:.1f} KB  |  {elapsed:.0f} ms")
    return resp.content, elapsed


# ─────────────────────────────────────────────────────────────────────────────
# SECTION 1 — SCHEMA VALIDATION
# ─────────────────────────────────────────────────────────────────────────────

def validate_schema(zf: zipfile.ZipFile, dfs: dict[str, pd.DataFrame]) -> dict[str, list[str]]:
    """Returns dict of file → list of error strings."""
    header("SECTION 1 · Schema Validation")
    present = set(zf.namelist())
    schema_errors: dict[str, list[str]] = defaultdict(list)

    for fname, meta in GTFS_SCHEMA.items():
        if fname not in present:
            if meta["required"]:
                err(f"MISSING required file: {fname}")
                schema_errors[fname].append("file missing")
            else:
                info(f"optional file absent : {fname}")
            continue

        df = dfs.get(fname)
        if df is None:
            err(f"Could not parse: {fname}")
            schema_errors[fname].append("parse failure")
            continue

        missing_req = [c for c in meta["required_cols"] if c not in df.columns]
        if missing_req:
            err(f"{fname} — missing required columns: {missing_req}")
            schema_errors[fname].extend([f"missing col: {c}" for c in missing_req])
        else:
            ok(f"{fname:<30} all required columns present")

        extra = [c for c in df.columns
                 if c not in meta["required_cols"] and c not in meta["optional_cols"]]
        if extra:
            warn(f"{fname} — unexpected extra columns (not in GTFS spec): {extra}")

    # special: at least one of calendar.txt / calendar_dates.txt must be present
    if "calendar.txt" not in present and "calendar_dates.txt" not in present:
        err("Neither calendar.txt nor calendar_dates.txt present — service cannot be determined")
        schema_errors["calendar"].append("both calendar files missing")

    return schema_errors


# ─────────────────────────────────────────────────────────────────────────────
# SECTION 2 — PER-FILE PROFILING
# ─────────────────────────────────────────────────────────────────────────────

def profile_files(dfs: dict[str, pd.DataFrame]):
    header("SECTION 2 · Per-File Profiling")

    summary_rows = []
    for fname, df in dfs.items():
        null_pct = df.isnull().mean().mean() * 100
        dup_rows = df.duplicated().sum()
        summary_rows.append({
            "file":     fname,
            "rows":     len(df),
            "cols":     len(df.columns),
            "null_%":   f"{null_pct:.1f}",
            "dup_rows": dup_rows,
        })

    print()
    print(tabulate(summary_rows, headers="keys", tablefmt="rounded_outline"))

    # detailed null breakdown per file
    for fname, df in dfs.items():
        null_counts = df.isnull().sum()
        null_cols   = null_counts[null_counts > 0]
        if not null_cols.empty:
            subheader(f"Nulls in {fname}")
            for col, cnt in null_cols.items():
                pct = cnt / len(df) * 100
                lvl = warn if pct > 10 else info
                lvl(f"{col:<40} {cnt:>6} nulls  ({pct:.1f}%)")


# ─────────────────────────────────────────────────────────────────────────────
# SECTION 3 — REFERENTIAL INTEGRITY
# ─────────────────────────────────────────────────────────────────────────────

def check_referential_integrity(dfs: dict[str, pd.DataFrame]):
    header("SECTION 3 · Referential Integrity")

    def fk_check(child_file, child_col, parent_file, parent_col, label=None):
        lbl = label or f"{child_file}.{child_col} → {parent_file}.{parent_col}"
        if child_file not in dfs or parent_file not in dfs:
            info(f"[skip — file missing] {lbl}")
            return
        child_df  = dfs[child_file]
        parent_df = dfs[parent_file]
        if child_col not in child_df.columns or parent_col not in parent_df.columns:
            warn(f"[skip — column missing] {lbl}")
            return
        child_vals  = set(child_df[child_col].dropna().unique())
        parent_vals = set(parent_df[parent_col].dropna().unique())
        orphans = child_vals - parent_vals
        if orphans:
            err(f"{lbl}")
            info(f"  {len(orphans)} orphan values: {sorted(orphans)[:10]}{'...' if len(orphans)>10 else ''}")
        else:
            ok(f"{lbl}")

    # core FK checks
    fk_check("routes.txt",     "agency_id",    "agency.txt",    "agency_id")
    fk_check("trips.txt",      "route_id",     "routes.txt",    "route_id")
    fk_check("trips.txt",      "service_id",   "calendar.txt",  "service_id")
    fk_check("stop_times.txt", "trip_id",      "trips.txt",     "trip_id")
    fk_check("stop_times.txt", "stop_id",      "stops.txt",     "stop_id")
    fk_check("fare_rules.txt", "fare_id",      "fare_attributes.txt", "fare_id")
    fk_check("transfers.txt",  "from_stop_id", "stops.txt",     "stop_id")
    fk_check("transfers.txt",  "to_stop_id",   "stops.txt",     "stop_id")

    # shapes: every shape_id in trips.txt should have points in shapes.txt
    if "trips.txt" in dfs and "shapes.txt" in dfs:
        trips  = dfs["trips.txt"]
        shapes = dfs["shapes.txt"]
        if "shape_id" in trips.columns and "shape_id" in shapes.columns:
            trip_shapes   = set(trips["shape_id"].dropna().unique())
            shape_points  = set(shapes["shape_id"].dropna().unique())
            missing_shapes = trip_shapes - shape_points
            orphan_shapes  = shape_points - trip_shapes
            if missing_shapes:
                err(f"trips.txt references shape_ids with no points in shapes.txt: "
                    f"{len(missing_shapes)}  {sorted(missing_shapes)[:5]}")
            else:
                ok("All trip shape_ids have geometry in shapes.txt")
            if orphan_shapes:
                warn(f"shapes.txt has {len(orphan_shapes)} shape_ids not referenced by any trip")

    # calendar_dates service_ids should exist in calendar.txt (if both present)
    if "calendar_dates.txt" in dfs and "calendar.txt" in dfs:
        cal_svcs  = set(dfs["calendar.txt"]["service_id"].dropna())
        cd_svcs   = set(dfs["calendar_dates.txt"]["service_id"].dropna())
        new_svcs  = cd_svcs - cal_svcs
        if new_svcs:
            warn(f"calendar_dates.txt introduces {len(new_svcs)} service_ids not in calendar.txt "
                 f"(valid if used as sole definition): {sorted(new_svcs)[:5]}")
        else:
            ok("calendar_dates.txt service_ids all exist in calendar.txt")

    # duplicate primary keys
    subheader("Duplicate primary keys")
    pk_map = {
        "agency.txt":    "agency_id",
        "routes.txt":    "route_id",
        "trips.txt":     "trip_id",
        "stops.txt":     "stop_id",
        "shapes.txt":    "shape_id",   # intentionally repeated, so just count unique
        "fare_attributes.txt": "fare_id",
    }
    for fname, pk in pk_map.items():
        if fname not in dfs: continue
        df = dfs[fname]
        if pk not in df.columns: continue
        if fname == "shapes.txt":
            # shape_id appears many times (one row per point) — skip dup check
            info(f"shapes.txt: {df['shape_id'].nunique()} unique shape_ids, "
                 f"{len(df)} total points")
            continue
        dups = df[pk].duplicated().sum()
        if dups:
            err(f"{fname}: {dups} duplicate {pk} values")
        else:
            ok(f"{fname}: no duplicate {pk}")


# ─────────────────────────────────────────────────────────────────────────────
# SECTION 4 — COORDINATE SANITY
# ─────────────────────────────────────────────────────────────────────────────

def check_coordinates(dfs: dict[str, pd.DataFrame]):
    header("SECTION 4 · Coordinate Sanity")

    for fname, lat_col, lon_col in [
        ("stops.txt",  "stop_lat",     "stop_lon"),
        ("shapes.txt", "shape_pt_lat", "shape_pt_lon"),
    ]:
        if fname not in dfs: continue
        df = dfs[fname].copy()
        if lat_col not in df.columns or lon_col not in df.columns:
            warn(f"{fname}: lat/lon columns missing")
            continue

        df[lat_col] = pd.to_numeric(df[lat_col], errors="coerce")
        df[lon_col] = pd.to_numeric(df[lon_col], errors="coerce")

        n_total    = len(df)
        n_null     = df[[lat_col, lon_col]].isnull().any(axis=1).sum()
        n_zero     = ((df[lat_col] == 0) | (df[lon_col] == 0)).sum()
        n_out_bbox = (~df[lat_col].between(LAT_MIN, LAT_MAX) |
                      ~df[lon_col].between(LON_MIN, LON_MAX)).sum()

        # duplicate lat/lon (stops at exact same position)
        if fname == "stops.txt" and "stop_id" in df.columns:
            dup_coords = df.duplicated(subset=[lat_col, lon_col]).sum()
        else:
            dup_coords = None

        subheader(fname)
        info(f"Total rows      : {n_total}")
        info(f"Lat range       : {df[lat_col].min():.5f} → {df[lat_col].max():.5f}")
        info(f"Lon range       : {df[lon_col].min():.5f} → {df[lon_col].max():.5f}")

        (err if n_null  > 0 else ok)(f"Null coordinates    : {n_null}")
        (err if n_zero  > 0 else ok)(f"Zero coordinates    : {n_zero}")
        (err if n_out_bbox > 0 else ok)(
            f"Outside Bloomington bbox ({LAT_MIN}–{LAT_MAX}, {LON_MIN}–{LON_MAX}): {n_out_bbox}")
        if dup_coords is not None:
            (warn if dup_coords > 0 else ok)(f"Duplicate lat/lon (stops at same point): {dup_coords}")

        if n_out_bbox > 0:
            bad = df[~df[lat_col].between(LAT_MIN, LAT_MAX) | ~df[lon_col].between(LON_MIN, LON_MAX)]
            id_col = "stop_id" if "stop_id" in df.columns else df.columns[0]
            info(f"  Offending IDs: {list(bad[id_col].head(10))}")


# ─────────────────────────────────────────────────────────────────────────────
# SECTION 5 — TIME & CALENDAR
# ─────────────────────────────────────────────────────────────────────────────

def check_time_and_calendar(dfs: dict[str, pd.DataFrame]):
    header("SECTION 5 · Time & Calendar")
    today = datetime.date.today()
    today_int = int(today.strftime("%Y%m%d"))

    # ── calendar.txt date range ──
    if "calendar.txt" in dfs:
        subheader("calendar.txt")
        cal = dfs["calendar.txt"].copy()
        cal["start_date"] = pd.to_numeric(cal["start_date"], errors="coerce")
        cal["end_date"]   = pd.to_numeric(cal["end_date"],   errors="coerce")

        min_start = cal["start_date"].min()
        max_end   = cal["end_date"].max()
        info(f"Service date range: {min_start} → {max_end}  (today={today_int})")

        expired = (cal["end_date"] < today_int).sum()
        future  = (cal["start_date"] > today_int).sum()
        active  = len(cal) - expired - future

        (err  if expired > 0 else ok)(f"Expired services (end_date < today): {expired}")
        (warn if future  > 0 else ok)(f"Future-only services (start_date > today): {future}")
        ok(f"Currently active services: {active}")

        # day-of-week coverage
        dow_cols = ["monday","tuesday","wednesday","thursday","friday","saturday","sunday"]
        present_dow = [c for c in dow_cols if c in cal.columns]
        if present_dow:
            info(f"\nDay-of-week service count (services with flag=1):")
            for day in present_dow:
                n = (cal[day] == "1").sum()
                info(f"  {day:<12}: {n}")

    # ── calendar_dates.txt ──
    if "calendar_dates.txt" in dfs:
        subheader("calendar_dates.txt")
        cd = dfs["calendar_dates.txt"].copy()
        cd["date"] = pd.to_numeric(cd["date"], errors="coerce")
        info(f"Rows: {len(cd)}  |  date range: {cd['date'].min()} → {cd['date'].max()}")
        removed = (cd["exception_type"] == "2").sum() if "exception_type" in cd.columns else "?"
        added   = (cd["exception_type"] == "1").sum() if "exception_type" in cd.columns else "?"
        info(f"exception_type=1 (added):   {added}")
        info(f"exception_type=2 (removed): {removed}")

    # ── stop_times time checks ──
    if "stop_times.txt" in dfs:
        subheader("stop_times.txt — time field analysis")
        st = dfs["stop_times.txt"]

        for tcol in ["arrival_time", "departure_time"]:
            if tcol not in st.columns:
                warn(f"{tcol} column missing")
                continue

            parsed   = st[tcol].apply(parse_gtfs_time)
            n_null   = parsed.isnull().sum()
            # overnight: hour >= 24 (next-day service)
            overnight = (parsed // 3600 >= 24).sum()
            # arrival after departure (data error)
            if "arrival_time" in st.columns and "departure_time" in st.columns:
                arr = st["arrival_time"].apply(parse_gtfs_time)
                dep = st["departure_time"].apply(parse_gtfs_time)
                arr_after_dep = ((arr > dep) & arr.notna() & dep.notna()).sum()
            else:
                arr_after_dep = None

            info(f"{tcol}:")
            info(f"  Unparseable / blank : {n_null}")
            (warn if overnight > 0 else ok)(f"  Overnight (hour≥24) : {overnight}  "
                                             f"(valid in GTFS but needs careful parsing)")
            if arr_after_dep is not None:
                (err if arr_after_dep > 0 else ok)(
                    f"  arrival > departure : {arr_after_dep}  ← data error")

        # stop_sequence ordering: for each trip, seq should be strictly increasing
        if "trip_id" in st.columns and "stop_sequence" in st.columns:
            st2 = st[["trip_id","stop_sequence"]].copy()
            st2["stop_sequence"] = pd.to_numeric(st2["stop_sequence"], errors="coerce")
            bad_seq = (
                st2.groupby("trip_id")["stop_sequence"]
                   .apply(lambda s: s.is_monotonic_increasing == False)
                   .sum()
            )
            (err if bad_seq > 0 else ok)(
                f"  Trips with non-monotonic stop_sequence: {bad_seq}")


# ─────────────────────────────────────────────────────────────────────────────
# SECTION 6 — TRIP / SHAPE COVERAGE
# ─────────────────────────────────────────────────────────────────────────────

def check_trip_shape_coverage(dfs: dict[str, pd.DataFrame]):
    header("SECTION 6 · Trip & Shape Coverage")

    if "trips.txt" not in dfs:
        warn("trips.txt not available — skipping")
        return

    trips = dfs["trips.txt"]
    total_trips = len(trips)
    info(f"Total trips: {total_trips}")

    # trips with no shape
    if "shape_id" in trips.columns:
        no_shape = trips["shape_id"].isnull().sum() + (trips["shape_id"] == "").sum()
        (warn if no_shape > 0 else ok)(
            f"Trips missing shape_id: {no_shape} / {total_trips}")
    else:
        warn("shape_id column absent from trips.txt — no polyline rendering possible")

    # direction_id coverage
    if "direction_id" in trips.columns:
        dirs = trips["direction_id"].value_counts()
        info(f"direction_id distribution: {dirs.to_dict()}")
        missing_dir = trips["direction_id"].isnull().sum()
        (warn if missing_dir > 0 else ok)(f"Trips missing direction_id: {missing_dir}")

    # trips not appearing in stop_times (dead trips)
    if "stop_times.txt" in dfs:
        trip_ids_in_st = set(dfs["stop_times.txt"]["trip_id"].dropna().unique())
        trip_ids       = set(trips["trip_id"].dropna().unique())
        dead_trips     = trip_ids - trip_ids_in_st
        (err if dead_trips else ok)(
            f"Trips with zero stop_times entries: {len(dead_trips)}"
            + (f"  {sorted(dead_trips)[:5]}" if dead_trips else ""))

    # stops not served by any trip
    if "stop_times.txt" in dfs and "stops.txt" in dfs:
        served_stops = set(dfs["stop_times.txt"]["stop_id"].dropna().unique())
        all_stops    = set(dfs["stops.txt"]["stop_id"].dropna().unique())
        unserved     = all_stops - served_stops
        (warn if unserved else ok)(
            f"Stops defined but never served by any trip: {len(unserved)}"
            + (f"  {sorted(unserved)[:5]}" if unserved else ""))

    # shapes with very few points (< 3 → basically useless polyline)
    if "shapes.txt" in dfs and "shape_id" in dfs["shapes.txt"].columns:
        shape_pt_counts = dfs["shapes.txt"]["shape_id"].value_counts()
        thin_shapes = (shape_pt_counts < 3).sum()
        (warn if thin_shapes > 0 else ok)(
            f"Shapes with < 3 points (degenerate geometry): {thin_shapes}")
        info(f"Shape point count — "
             f"min: {shape_pt_counts.min()}  "
             f"max: {shape_pt_counts.max()}  "
             f"mean: {shape_pt_counts.mean():.1f}")

    # block_id coverage (for through-service / interlining)
    if "block_id" in trips.columns:
        has_block = trips["block_id"].notna().sum()
        info(f"Trips with block_id (through-service): {has_block} / {total_trips}")


# ─────────────────────────────────────────────────────────────────────────────
# SECTION 7 — ROUTE / STOP STATS
# ─────────────────────────────────────────────────────────────────────────────

def route_and_stop_stats(dfs: dict[str, pd.DataFrame]):
    header("SECTION 7 · Route & Stop Stats")

    # route type distribution
    if "routes.txt" in dfs:
        ROUTE_TYPES = {
            "0": "Tram/Streetcar", "1": "Subway/Metro", "2": "Rail",
            "3": "Bus", "4": "Ferry", "5": "Cable Car",
            "6": "Gondola", "7": "Funicular",
            "11": "Trolleybus", "12": "Monorail",
        }
        routes = dfs["routes.txt"]
        subheader("Routes")
        info(f"Total routes: {len(routes)}")
        if "route_type" in routes.columns:
            rtype_counts = routes["route_type"].value_counts()
            for rt, cnt in rtype_counts.items():
                label = ROUTE_TYPES.get(str(rt), f"type {rt}")
                info(f"  route_type {rt} ({label}): {cnt}")
        if "route_short_name" in routes.columns:
            info(f"Route short names: {sorted(routes['route_short_name'].dropna().unique().tolist())}")

    # stop stats
    if "stops.txt" in dfs:
        subheader("Stops")
        stops = dfs["stops.txt"]
        info(f"Total stops: {len(stops)}")

        if "location_type" in stops.columns:
            lt = stops["location_type"].fillna("0").value_counts()
            LOCATION_TYPES = {"0":"Stop","1":"Station","2":"Station entrance","3":"Generic node","4":"Boarding area"}
            for k, v in lt.items():
                info(f"  location_type {k} ({LOCATION_TYPES.get(str(k),'?')}): {v}")

        if "wheelchair_boarding" in stops.columns:
            wb = stops["wheelchair_boarding"].value_counts()
            info(f"  wheelchair_boarding distribution: {wb.to_dict()}")

    # trips per route
    if "trips.txt" in dfs and "routes.txt" in dfs:
        subheader("Trips per route")
        tpr = (dfs["trips.txt"].groupby("route_id")["trip_id"]
               .count().reset_index()
               .rename(columns={"trip_id":"n_trips"})
               .sort_values("n_trips", ascending=False))
        # join route name if available
        if "route_short_name" in dfs["routes.txt"].columns:
            tpr = tpr.merge(
                dfs["routes.txt"][["route_id","route_short_name"]],
                on="route_id", how="left"
            )
        print(f"\n{tabulate(tpr.head(20), headers='keys', tablefmt='simple', showindex=False)}")

    # stop times per stop (busiest stops)
    if "stop_times.txt" in dfs and "stops.txt" in dfs:
        subheader("Busiest stops (by stop_time appearances)")
        stp = (dfs["stop_times.txt"].groupby("stop_id")["trip_id"]
               .count().reset_index()
               .rename(columns={"trip_id":"n_arrivals"})
               .sort_values("n_arrivals", ascending=False))
        if "stop_name" in dfs["stops.txt"].columns:
            stp = stp.merge(
                dfs["stops.txt"][["stop_id","stop_name"]],
                on="stop_id", how="left"
            )
        print(f"\n{tabulate(stp.head(15), headers='keys', tablefmt='simple', showindex=False)}")


# ─────────────────────────────────────────────────────────────────────────────
# SECTION 8 — SUMMARY REPORT
# ─────────────────────────────────────────────────────────────────────────────

def summary_report(dfs: dict[str, pd.DataFrame], schema_errors: dict, fetch_ms: float):
    header("SECTION 8 · Summary Report")

    today = datetime.date.today()
    today_int = int(today.strftime("%Y%m%d"))

    rows = []

    # file presence
    total_files    = len(GTFS_SCHEMA)
    present_files  = sum(1 for f in GTFS_SCHEMA if f in dfs)
    required_files = [f for f, m in GTFS_SCHEMA.items() if m["required"]]
    missing_req    = [f for f in required_files if f not in dfs]
    rows.append(("Files present",     f"{present_files}/{total_files}",
                 "OK" if not missing_req else f"MISSING: {missing_req}"))

    # schema errors
    total_schema_errs = sum(len(v) for v in schema_errors.values())
    rows.append(("Schema errors",     str(total_schema_errs),
                 "OK" if total_schema_errs == 0 else "ISSUES FOUND"))

    # row counts
    for fname in ["routes.txt","trips.txt","stops.txt","stop_times.txt"]:
        cnt = len(dfs[fname]) if fname in dfs else "N/A"
        rows.append((f"  {fname} rows", str(cnt), ""))

    # calendar freshness
    if "calendar.txt" in dfs:
        cal = dfs["calendar.txt"].copy()
        cal["end_date"] = pd.to_numeric(cal["end_date"], errors="coerce")
        max_end = int(cal["end_date"].max()) if not cal["end_date"].isna().all() else 0
        expired_svcs = (cal["end_date"] < today_int).sum()
        rows.append(("Calendar max end_date", str(max_end),
                     "OK" if max_end >= today_int else f"EXPIRED (today={today_int})"))
        rows.append(("Expired services", str(expired_svcs),
                     "OK" if expired_svcs == 0 else "WARNING"))

    # coordinate issues
    if "stops.txt" in dfs:
        stops = dfs["stops.txt"].copy()
        for c in ["stop_lat","stop_lon"]:
            if c in stops.columns:
                stops[c] = pd.to_numeric(stops[c], errors="coerce")
        if "stop_lat" in stops.columns and "stop_lon" in stops.columns:
            bad = (~stops["stop_lat"].between(LAT_MIN, LAT_MAX) |
                   ~stops["stop_lon"].between(LON_MIN, LON_MAX)).sum()
            rows.append(("Stops outside bbox", str(bad),
                         "OK" if bad == 0 else "CHECK COORDS"))

    rows.append(("Fetch latency (ms)", f"{fetch_ms:.0f}", ""))

    print()
    print(tabulate(rows, headers=["Check","Value","Status"], tablefmt="rounded_outline"))
    print()
    info(f"Analysis run at: {datetime.datetime.utcnow().strftime('%Y-%m-%d %H:%M:%S UTC')}")
    info(f"Static GTFS URL: {STATIC_URL}")


# ─────────────────────────────────────────────────────────────────────────────
# MAIN
# ─────────────────────────────────────────────────────────────────────────────

def load_dfs(zip_bytes: bytes) -> tuple[zipfile.ZipFile, dict[str, pd.DataFrame]]:
    zf   = zipfile.ZipFile(io.BytesIO(zip_bytes))
    dfs  = {}
    for name in zf.namelist():
        if not name.endswith(".txt"):
            continue
        with zf.open(name) as f:
            try:
                df = pd.read_csv(f, dtype=str, low_memory=False)
                df.columns = [c.strip().lstrip("\ufeff") for c in df.columns]
                dfs[name] = df
            except Exception as e:
                print(f"{RED}  parse error {name}: {e}{RST}")
    return zf, dfs


def main():
    print("\n" + "═"*80)
    print("  BLOOMINGTON TRANSIT — STATIC GTFS DEEP ANALYZER")
    print("  " + datetime.datetime.utcnow().strftime("%Y-%m-%d %H:%M:%S UTC"))
    print("═"*80)

    zip_bytes, fetch_ms = fetch_zip(STATIC_URL)
    zf, dfs = load_dfs(zip_bytes)

    info(f"\nFiles found in ZIP: {zf.namelist()}")

    schema_errors = validate_schema(zf, dfs)
    profile_files(dfs)
    check_referential_integrity(dfs)
    check_coordinates(dfs)
    check_time_and_calendar(dfs)
    check_trip_shape_coverage(dfs)
    route_and_stop_stats(dfs)
    summary_report(dfs, schema_errors, fetch_ms)


if __name__ == "__main__":
    main()
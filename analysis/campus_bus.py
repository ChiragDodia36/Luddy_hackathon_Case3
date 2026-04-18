"""
Campus Bus Detection in Bloomington Transit Static GTFS
=======================================================
Checks whether the gtfs.zip feed contains IU Campus Bus routes
alongside regular Bloomington Transit routes.

What we sniff for:
  - agency.txt       → multiple agencies? IU / campus-related agency names?
  - routes.txt       → route names/colors known to be campus routes (Blue, Red, Green, etc.)
  - stops.txt        → stop names / coords inside IU campus bbox
  - trips.txt        → headsigns referencing campus landmarks
  - feed_info.txt    → publisher name

Install:
    pip install requests pandas tabulate
Run:
    python detect_campus_buses.py
"""

import io
import zipfile
import datetime
import requests
import pandas as pd
from tabulate import tabulate

STATIC_URL = (
    "https://s3.amazonaws.com/etatransit.gtfs/"
    "bloomingtontransit.etaspot.net/gtfs.zip"
)

# ── IU Campus bounding box (main Bloomington campus) ─────────────────────────
# Roughly: Jordan Ave corridor, 10th St to 3rd St, Fee Lane to Indiana Ave
IU_LAT_MIN, IU_LAT_MAX =  39.155,  39.180
IU_LON_MIN, IU_LON_MAX = -86.535, -86.505

# ── Keywords that suggest campus / IU presence ────────────────────────────────
CAMPUS_AGENCY_KEYWORDS = [
    "indiana university", "iu", "campus", "iubus", "iubloomington"
]

# Known IU Campus Bus route short names (as of recent schedules)
# Src: https://iubus.indiana.edu/routes/index.html
IU_KNOWN_ROUTE_NAMES = {
    "blue", "red", "green", "yellow", "silver", "purple", "gold",
    "b", "r", "g", "y", "s", "p",
    "campus express", "ce", "x",
    "late night", "ln",
    "bloomington shuttle",
}

CAMPUS_STOP_KEYWORDS = [
    "union", "sample gates", "swain", "wells", "bloomington transit",
    "information technology", "assembly hall", "memorial stadium",
    "simon skjodt", "fee lane", "jordan ave", "10th street",
    "3rd street garage", "read hall", "teter", "foster", "wright",
    "willkie", "eigenmann", "forrest", "briscoe", "rogers",
    "collins", "mcnutt", "sue young", "indiana memorial",
    "chemistry", "ballantine", "sycamore hall", "ernie pyle",
    "kirkwood", "dunn meadow", "lindley", "rawles", "franklin hall",
    "global international", "health center", "iheal",
    "indiana avenue", "10th & fee", "atwater",
]

CAMPUS_HEADSIGN_KEYWORDS = [
    "union", "campus", "iu", "indiana university", "sample gates",
    "fee lane", "memorial", "assembly", "stadium", "wells library",
]


# ─────────────────────────────────────────────────────────────────────────────
def fetch_and_load(url: str) -> dict[str, pd.DataFrame]:
    print(f"Fetching {url} ...")
    resp = requests.get(url, timeout=60)
    resp.raise_for_status()
    print(f"  {len(resp.content)/1024:.1f} KB downloaded\n")

    zf   = zipfile.ZipFile(io.BytesIO(resp.content))
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
                print(f"  parse error {name}: {e}")
    return dfs


def kw_match(value: str, keywords: list[str]) -> bool:
    """Case-insensitive substring match against any keyword."""
    v = str(value).lower()
    return any(kw in v for kw in keywords)


SEP = "\n" + "═" * 70


# ─────────────────────────────────────────────────────────────────────────────
# CHECK 1 — agency.txt
# ─────────────────────────────────────────────────────────────────────────────
def check_agency(dfs):
    print(SEP)
    print("CHECK 1 · agency.txt — how many agencies, any campus-related?")
    if "agency.txt" not in dfs:
        print("  agency.txt not found in feed")
        return {}

    ag = dfs["agency.txt"]
    print(f"\n  Total agencies: {len(ag)}")
    print(f"\n{ag.to_string(index=False)}\n")

    campus_agencies = {}
    for _, row in ag.iterrows():
        combined = " ".join(str(v) for v in row.values)
        if kw_match(combined, CAMPUS_AGENCY_KEYWORDS):
            aid = row.get("agency_id", "NO_ID")
            campus_agencies[aid] = row.get("agency_name", "")
            print(f"  *** CAMPUS-RELATED AGENCY DETECTED: id={aid}  name={row.get('agency_name','')}")

    if not campus_agencies:
        print("  No campus-related agency keywords found in agency.txt")

    return campus_agencies


# ─────────────────────────────────────────────────────────────────────────────
# CHECK 2 — routes.txt
# ─────────────────────────────────────────────────────────────────────────────
def check_routes(dfs, campus_agency_ids: dict):
    print(SEP)
    print("CHECK 2 · routes.txt — campus route names / agency_id / colors")
    if "routes.txt" not in dfs:
        print("  routes.txt not found")
        return set()

    routes = dfs["routes.txt"]
    print(f"\n  Total routes: {len(routes)}")
    print(f"\n{routes.to_string(index=False)}\n")

    campus_route_ids = set()

    for _, row in routes.iterrows():
        reasons = []

        # agency_id matches a campus agency
        if campus_agency_ids and str(row.get("agency_id","")) in campus_agency_ids:
            reasons.append(f"agency_id={row.get('agency_id')}")

        # short name or long name matches known IU route
        for col in ["route_short_name", "route_long_name"]:
            val = str(row.get(col, "")).strip().lower()
            if val in IU_KNOWN_ROUTE_NAMES:
                reasons.append(f"{col}='{val}' matches known IU route name")

        # keyword sniff on name fields
        combined_name = f"{row.get('route_short_name','')} {row.get('route_long_name','')}".lower()
        if kw_match(combined_name, ["campus", "iu ", "indiana university"]):
            reasons.append("name contains campus keyword")

        if reasons:
            campus_route_ids.add(row["route_id"])
            print(f"  *** CAMPUS ROUTE: route_id={row['route_id']}  "
                  f"short='{row.get('route_short_name','')}' "
                  f"long='{row.get('route_long_name','')}' "
                  f"→ {reasons}")

    if not campus_route_ids:
        print("  No campus-specific routes detected by name/agency")

    return campus_route_ids


# ─────────────────────────────────────────────────────────────────────────────
# CHECK 3 — stops.txt (name keywords + coordinates inside IU bbox)
# ─────────────────────────────────────────────────────────────────────────────
def check_stops(dfs):
    print(SEP)
    print("CHECK 3 · stops.txt — stop names & coordinates inside IU campus bbox")
    if "stops.txt" not in dfs:
        print("  stops.txt not found")
        return set()

    stops = dfs["stops.txt"].copy()
    stops["stop_lat"] = pd.to_numeric(stops.get("stop_lat", pd.Series(dtype=float)), errors="coerce")
    stops["stop_lon"] = pd.to_numeric(stops.get("stop_lon", pd.Series(dtype=float)), errors="coerce")

    print(f"\n  Total stops: {len(stops)}")

    # name-based detection
    name_matches = pd.Series([False] * len(stops), index=stops.index)
    if "stop_name" in stops.columns:
        name_matches = stops["stop_name"].apply(
            lambda n: kw_match(str(n), CAMPUS_STOP_KEYWORDS)
        )

    # coordinate-based detection
    coord_matches = (
        stops["stop_lat"].between(IU_LAT_MIN, IU_LAT_MAX) &
        stops["stop_lon"].between(IU_LON_MIN, IU_LON_MAX)
    )

    either = name_matches | coord_matches
    campus_stops = stops[either]
    campus_stop_ids = set(campus_stops["stop_id"].dropna().unique())

    print(f"\n  Stops matching campus name keywords  : {name_matches.sum()}")
    print(f"  Stops inside IU campus bbox          : {coord_matches.sum()}")
    print(f"  Stops flagged by either criterion    : {len(campus_stops)}")

    if not campus_stops.empty:
        cols = [c for c in ["stop_id","stop_name","stop_lat","stop_lon"] if c in campus_stops.columns]
        print(f"\n  Campus-flagged stops:")
        print(campus_stops[cols].to_string(index=False))
    else:
        print("\n  No stops flagged as campus-related")

    return campus_stop_ids


# ─────────────────────────────────────────────────────────────────────────────
# CHECK 4 — trips.txt headsigns
# ─────────────────────────────────────────────────────────────────────────────
def check_trip_headsigns(dfs, campus_route_ids: set):
    print(SEP)
    print("CHECK 4 · trips.txt — headsigns referencing campus landmarks")
    if "trips.txt" not in dfs:
        print("  trips.txt not found")
        return set()

    trips = dfs["trips.txt"]
    campus_trip_ids = set()

    # already-known campus routes
    if campus_route_ids:
        from_route = trips[trips["route_id"].isin(campus_route_ids)]
        campus_trip_ids.update(from_route["trip_id"].tolist())
        print(f"\n  Trips on known campus routes: {len(from_route)}")

    # headsign keyword sniff
    headsign_matches = pd.Series([False]*len(trips), index=trips.index)
    if "trip_headsign" in trips.columns:
        headsign_matches = trips["trip_headsign"].apply(
            lambda h: kw_match(str(h), CAMPUS_HEADSIGN_KEYWORDS)
        )
        matched = trips[headsign_matches]
        campus_trip_ids.update(matched["trip_id"].tolist())
        print(f"  Trips with campus headsign keywords  : {len(matched)}")
        if not matched.empty:
            cols = [c for c in ["trip_id","route_id","trip_headsign","direction_id"] if c in matched.columns]
            print(matched[cols].drop_duplicates(subset=["route_id","trip_headsign"]).to_string(index=False))

    if not campus_trip_ids:
        print("  No campus-related headsigns found")

    return campus_trip_ids


# ─────────────────────────────────────────────────────────────────────────────
# CHECK 5 — feed_info.txt
# ─────────────────────────────────────────────────────────────────────────────
def check_feed_info(dfs):
    print(SEP)
    print("CHECK 5 · feed_info.txt — publisher / feed metadata")
    if "feed_info.txt" not in dfs:
        print("  feed_info.txt not present in this feed")
        return
    print(f"\n{dfs['feed_info.txt'].to_string(index=False)}")


# ─────────────────────────────────────────────────────────────────────────────
# CHECK 6 — cross-check: do campus stops appear in stop_times for non-campus routes?
# ─────────────────────────────────────────────────────────────────────────────
def check_stop_route_overlap(dfs, campus_stop_ids: set, campus_route_ids: set):
    print(SEP)
    print("CHECK 6 · Cross-check — do BT routes serve campus stops? (shared infrastructure)")

    if "stop_times.txt" not in dfs or "trips.txt" not in dfs:
        print("  stop_times.txt or trips.txt missing — skipping")
        return

    st    = dfs["stop_times.txt"]
    trips = dfs["trips.txt"]

    # map trip_id → route_id
    trip_to_route = trips.set_index("trip_id")["route_id"].to_dict()
    st = st.copy()
    st["route_id"] = st["trip_id"].map(trip_to_route)

    bt_routes_at_campus_stops = (
        st[st["stop_id"].isin(campus_stop_ids) & ~st["route_id"].isin(campus_route_ids)]
        [["route_id","stop_id"]]
        .drop_duplicates()
    )

    if not bt_routes_at_campus_stops.empty:
        print(f"\n  BT routes that also serve campus-flagged stops ({len(bt_routes_at_campus_stops)} combos):")
        # join stop name
        if "stops.txt" in dfs and "stop_name" in dfs["stops.txt"].columns:
            bt_routes_at_campus_stops = bt_routes_at_campus_stops.merge(
                dfs["stops.txt"][["stop_id","stop_name"]], on="stop_id", how="left"
            )
        print(bt_routes_at_campus_stops.to_string(index=False))
    else:
        print("  No BT routes found serving campus-flagged stops")


# ─────────────────────────────────────────────────────────────────────────────
# VERDICT
# ─────────────────────────────────────────────────────────────────────────────
def verdict(campus_agencies, campus_route_ids, campus_stop_ids, campus_trip_ids):
    print(SEP)
    print("VERDICT")

    signals = {
        "Campus-related agency in agency.txt"     : bool(campus_agencies),
        "Campus-named routes in routes.txt"       : bool(campus_route_ids),
        "Campus-flagged stops (name or bbox)"     : bool(campus_stop_ids),
        "Campus headsigns / trips"                : bool(campus_trip_ids),
    }

    any_signal = any(signals.values())

    rows = [[k, "YES ✔" if v else "no"] for k, v in signals.items()]
    print(f"\n{tabulate(rows, headers=['Signal','Found'], tablefmt='rounded_outline')}\n")

    if any_signal:
        print("  RESULT: Campus bus data IS likely present in this GTFS feed.")
        print("          At least one signal points to IU campus routes or stops.")
    else:
        print("  RESULT: No campus bus data detected.")
        print("          This feed appears to contain Bloomington Transit only.")
        print("          IU Campus Bus likely uses a separate GTFS endpoint.")
        print("          Check: https://iubus.indiana.edu  or  https://gtfs.indiana.edu")

    print(f"\n  Run at: {datetime.datetime.utcnow().strftime('%Y-%m-%d %H:%M:%S UTC')}")


# ─────────────────────────────────────────────────────────────────────────────
# MAIN
# ─────────────────────────────────────────────────────────────────────────────
def main():
    print("\n" + "═"*70)
    print("  CAMPUS BUS DETECTION — Bloomington Transit GTFS")
    print("═"*70)

    dfs = fetch_and_load(STATIC_URL)

    campus_agencies  = check_agency(dfs)
    campus_route_ids = check_routes(dfs, campus_agencies)
    campus_stop_ids  = check_stops(dfs)
    campus_trip_ids  = check_trip_headsigns(dfs, campus_route_ids)
    check_feed_info(dfs)
    check_stop_route_overlap(dfs, campus_stop_ids, campus_route_ids)
    verdict(campus_agencies, campus_route_ids, campus_stop_ids, campus_trip_ids)


if __name__ == "__main__":
    main()
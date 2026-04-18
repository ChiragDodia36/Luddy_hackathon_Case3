"""
BT Transit FastAPI backend.
Downloads GTFS static data once on startup; caches RT feeds on a short TTL.
Serves JSON to the Android app so the device doesn't need direct S3 access.
"""

import csv
import io
import logging
import time
import zipfile
from typing import Optional

import httpx
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from google.transit import gtfs_realtime_pb2

logging.basicConfig(level=logging.INFO)
log = logging.getLogger("bt_backend")

app = FastAPI(title="BT Transit API")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])

GTFS_ZIP_URL = "https://s3.amazonaws.com/etatransit.gtfs/bloomingtontransit.etaspot.net/gtfs.zip"
RT_BASE = "https://s3.amazonaws.com/etatransit.gtfs/bloomingtontransit.etaspot.net/"


routes_cache: dict = {}       # route_id → route dict
stops_cache: dict = {}        # stop_id → stop dict
trips_cache: dict = {}        # trip_id → trip dict (has route_id, headsign)
shapes_cache: dict = {}       # route_id → list of shape points
stop_times_cache: dict = {}   # stop_id → list of {trip_id, arrival_secs}


class RtCache:
    def __init__(self, ttl_s: int):
        self.ttl = ttl_s
        self.data = None
        self.fetched_at = 0.0

    def is_fresh(self) -> bool:
        return self.data is not None and (time.time() - self.fetched_at) < self.ttl

rt_buses = RtCache(ttl_s=10)
rt_trips = RtCache(ttl_s=20)
rt_alerts = RtCache(ttl_s=60)



@app.on_event("startup")
async def load_static_data():
    log.info("Downloading GTFS zip…")
    try:
        async with httpx.AsyncClient(timeout=60) as client:
            r = await client.get(GTFS_ZIP_URL)
            r.raise_for_status()

        zf = zipfile.ZipFile(io.BytesIO(r.content))
        files = {name.split("/")[-1]: zf.read(name).decode("utf-8", errors="replace")
                 for name in zf.namelist()}

        _parse_routes(files.get("routes.txt", ""))
        _parse_stops(files.get("stops.txt", ""))
        _parse_trips(files.get("trips.txt", ""))
        _parse_shapes(files.get("shapes.txt", ""), files.get("trips.txt", ""))
        _parse_stop_times(files.get("stop_times.txt", ""))

        log.info(f"GTFS loaded: {len(routes_cache)} routes, {len(stops_cache)} stops, "
                 f"{len(trips_cache)} trips, {len(stop_times_cache)} stops with times")
    except Exception as e:
        log.error(f"Failed to load GTFS: {e}")


def _csv_rows(text: str):
    reader = csv.DictReader(io.StringIO(text.strip()))
    return [row for row in reader]


def _parse_routes(text: str):
    for row in _csv_rows(text):
        rid = row.get("route_id", "").strip()
        if rid:
            routes_cache[rid] = {
                "route_id": rid,
                "route_short_name": row.get("route_short_name", "").strip(),
                "route_long_name": row.get("route_long_name", "").strip(),
                "route_color": row.get("route_color", "0057A8").strip() or "0057A8",
                "route_text_color": row.get("route_text_color", "FFFFFF").strip() or "FFFFFF",
            }


def _parse_stops(text: str):
    for row in _csv_rows(text):
        sid = row.get("stop_id", "").strip()
        if sid:
            try:
                stops_cache[sid] = {
                    "stop_id": sid,
                    "stop_name": row.get("stop_name", "").strip(),
                    "stop_lat": float(row.get("stop_lat", 0)),
                    "stop_lon": float(row.get("stop_lon", 0)),
                    "stop_code": row.get("stop_code", "").strip(),
                }
            except ValueError:
                pass


def _parse_trips(text: str):
    for row in _csv_rows(text):
        tid = row.get("trip_id", "").strip()
        if tid:
            trips_cache[tid] = {
                "trip_id": tid,
                "route_id": row.get("route_id", "").strip(),
                "shape_id": row.get("shape_id", "").strip(),
                "headsign": row.get("trip_headsign", "").strip(),
                "service_id": row.get("service_id", "").strip(),
            }


def _parse_shapes(shapes_text: str, trips_text: str):
    # Build shape_id → route_id mapping via trips
    shape_to_route: dict[str, str] = {}
    for row in _csv_rows(trips_text):
        sid = row.get("shape_id", "").strip()
        rid = row.get("route_id", "").strip()
        if sid and rid:
            shape_to_route[sid] = rid

    # Group shape points by route_id
    raw: dict[str, list] = {}  # shape_id → points
    for row in _csv_rows(shapes_text):
        shid = row.get("shape_id", "").strip()
        if not shid:
            continue
        try:
            raw.setdefault(shid, []).append({
                "shape_id": shid,
                "lat": float(row.get("shape_pt_lat", 0)),
                "lon": float(row.get("shape_pt_lon", 0)),
                "sequence": int(row.get("shape_pt_sequence", 0)),
            })
        except ValueError:
            pass

    # Sort each shape by sequence and group by route
    for shid, pts in raw.items():
        pts.sort(key=lambda p: p["sequence"])
        route_id = shape_to_route.get(shid)
        if route_id:
            shapes_cache.setdefault(route_id, []).extend(pts)


def _parse_stop_times(text: str):
    for row in _csv_rows(text):
        sid = row.get("stop_id", "").strip()
        tid = row.get("trip_id", "").strip()
        arr = row.get("arrival_time", "").strip()
        if not sid or not tid or not arr:
            continue
        try:
            parts = arr.split(":")
            secs = int(parts[0]) * 3600 + int(parts[1]) * 60 + int(parts[2])
            stop_times_cache.setdefault(sid, []).append({"trip_id": tid, "arrival_secs": secs})
        except (ValueError, IndexError):
            pass



async def _fetch_feed(cache: RtCache, filename: str) -> gtfs_realtime_pb2.FeedMessage:
    if not cache.is_fresh():
        async with httpx.AsyncClient(timeout=15) as client:
            r = await client.get(RT_BASE + filename)
            r.raise_for_status()
        feed = gtfs_realtime_pb2.FeedMessage()
        feed.ParseFromString(r.content)
        cache.data = feed
        cache.fetched_at = time.time()
    return cache.data



@app.get("/routes")
async def get_routes():
    return list(routes_cache.values())


@app.get("/stops")
async def get_stops():
    return list(stops_cache.values())


@app.get("/stops/{stop_id}")
async def get_stop(stop_id: str):
    stop = stops_cache.get(stop_id)
    if not stop:
        raise HTTPException(404, "Stop not found")
    return stop


@app.get("/shapes/{route_id}")
async def get_shapes(route_id: str):
    return shapes_cache.get(route_id, [])


@app.get("/buses")
async def get_buses():
    try:
        feed = await _fetch_feed(rt_buses, "position_updates.pb")
    except Exception as e:
        log.error(f"/buses fetch failed: {e}")
        raise HTTPException(503, "RT feed unavailable")

    result = []
    for entity in feed.entity:
        if not entity.HasField("vehicle"):
            continue
        v = entity.vehicle
        if not v.HasField("position"):
            continue
        trip_id = v.trip.trip_id if v.HasField("trip") else ""
        route_id = v.trip.route_id if v.HasField("trip") else ""
        # Fall back to static trips table if RT omits route_id
        if not route_id and trip_id:
            route_id = trips_cache.get(trip_id, {}).get("route_id", "")
        result.append({
            "vehicle_id": v.vehicle.id if v.HasField("vehicle") else entity.id,
            "trip_id": trip_id,
            "route_id": route_id,
            "lat": v.position.latitude,
            "lon": v.position.longitude,
            "bearing": v.position.bearing,
            "speed": v.position.speed,
            "label": v.vehicle.label if v.HasField("vehicle") else "",
            "timestamp": v.timestamp,
            "current_stop_sequence": v.current_stop_sequence,
        })
    return result


@app.get("/arrivals/{stop_id}")
async def get_arrivals(stop_id: str):
    import datetime

    try:
        feed = await _fetch_feed(rt_trips, "trip_updates.pb")
    except Exception as e:
        log.error(f"/arrivals RT fetch failed: {e}")
        feed = None

    # Build realtime map: trip_id → predicted arrival unix ms
    rt_map: dict[str, int] = {}
    if feed:
        for entity in feed.entity:
            if not entity.HasField("trip_update"):
                continue
            tu = entity.trip_update
            trip_id = tu.trip.trip_id if tu.HasField("trip") else ""
            for stu in tu.stop_time_update:
                if stu.stop_id == stop_id:
                    arr_time = 0
                    if stu.HasField("arrival"):
                        arr_time = stu.arrival.time
                    elif stu.HasField("departure"):
                        arr_time = stu.departure.time
                    if arr_time > 0:
                        rt_map[trip_id] = arr_time * 1000

    # Static schedule
    static = stop_times_cache.get(stop_id, [])
    now = time.time()
    today_base = datetime.datetime.now().replace(hour=0, minute=0, second=0, microsecond=0).timestamp()
    window_end = now + 2 * 3600  # next 2 hours

    result = []
    for entry in static:
        tid = entry["trip_id"]
        secs = entry["arrival_secs"]
        scheduled_unix = today_base + secs
        # Handle post-midnight trips
        if scheduled_unix < now - 300:
            scheduled_unix += 86400

        if scheduled_unix > window_end:
            continue

        trip = trips_cache.get(tid, {})
        route_id = trip.get("route_id", "")
        route = routes_cache.get(route_id, {})

        predicted_ms = rt_map.get(tid, -1)
        is_realtime = predicted_ms > 0
        eta_ms = predicted_ms if is_realtime else int(scheduled_unix * 1000)
        eta_secs = (eta_ms // 1000) - int(now)

        result.append({
            "trip_id": tid,
            "route_id": route_id,
            "route_short_name": route.get("route_short_name", ""),
            "headsign": trip.get("headsign", ""),
            "eta_seconds": eta_secs,
            "delay_seconds": (predicted_ms // 1000 - int(scheduled_unix)) if is_realtime else 0,
            "is_realtime": is_realtime,
            "scheduled_unix": int(scheduled_unix * 1000),
        })

    result.sort(key=lambda x: x["eta_seconds"])
    return result[:20]


@app.get("/alerts")
async def get_alerts():
    try:
        feed = await _fetch_feed(rt_alerts, "alerts.pb")
    except Exception as e:
        log.error(f"/alerts fetch failed: {e}")
        return []

    result = []
    for entity in feed.entity:
        if not entity.HasField("alert"):
            continue
        a = entity.alert
        header = a.header_text.translation[0].text if a.header_text.translation else ""
        desc = a.description_text.translation[0].text if a.description_text.translation else ""
        route_ids = [
            ie.route_id for ie in a.informed_entity if ie.route_id
        ]
        result.append({
            "id": entity.id,
            "header": header,
            "description": desc,
            "route_ids": route_ids,
        })
    return result


@app.get("/health")
async def health():
    return {
        "routes": len(routes_cache),
        "stops": len(stops_cache),
        "trips": len(trips_cache),
    }

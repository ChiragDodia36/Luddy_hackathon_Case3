import requests
from google.transit import gtfs_realtime_pb2
import json
from datetime import datetime
import time

ALERTS_URL = "https://s3.amazonaws.com/etatransit.gtfs/bloomingtontransit.etaspot.net/alerts.pb"
TRIP_UPDATES_URL = "https://s3.amazonaws.com/etatransit.gtfs/bloomingtontransit.etaspot.net/trip_updates.pb"


def _parse_feed(url: str) -> gtfs_realtime_pb2.FeedMessage:
    response = requests.get(url, timeout=10)
    response.raise_for_status()
    feed = gtfs_realtime_pb2.FeedMessage()
    feed.ParseFromString(bytes(response.content))
    return feed


def fetch_gtfs_alerts(url: str = ALERTS_URL) -> list[dict]:
    feed = _parse_feed(url)
    alerts = []
    for entity in feed.entity:
        if entity.HasField("alert"):
            alert = entity.alert
            informed = [
                {
                    "route_id": ie.route_id or None,
                    "stop_id": ie.stop_id or None,
                    "trip_id": ie.trip.trip_id or None,
                }
                for ie in alert.informed_entity
            ]
            periods = [
                {
                    "start": datetime.fromtimestamp(ap.start).isoformat() if ap.start else None,
                    "end": datetime.fromtimestamp(ap.end).isoformat() if ap.end else None,
                }
                for ap in alert.active_period
            ]
            alerts.append({
                "id": entity.id,
                "cause": gtfs_realtime_pb2.Alert.Cause.Name(alert.cause),
                "effect": gtfs_realtime_pb2.Alert.Effect.Name(alert.effect),
                "header": alert.header_text.translation[0].text if alert.header_text.translation else None,
                "description": alert.description_text.translation[0].text if alert.description_text.translation else None,
                "url": alert.url.translation[0].text if alert.url.translation else None,
                "active_periods": periods,
                "informed_entities": informed,
            })
    return alerts


def fetch_trip_updates(url: str = TRIP_UPDATES_URL) -> list[dict]:
    feed = _parse_feed(url)
    updates = []
    for entity in feed.entity:
        if entity.HasField("trip_update"):
            tu = entity.trip_update
            stop_times = [
                {
                    "stop_sequence": stu.stop_sequence,
                    "stop_id": stu.stop_id or None,
                    "arrival_delay_sec": stu.arrival.delay if stu.HasField("arrival") else None,
                    "departure_delay_sec": stu.departure.delay if stu.HasField("departure") else None,
                }
                for stu in tu.stop_time_update
            ]
            updates.append({
                "id": entity.id,
                "trip_id": tu.trip.trip_id,
                "route_id": tu.trip.route_id or None,
                "vehicle_id": tu.vehicle.id or None,
                "timestamp": datetime.fromtimestamp(tu.timestamp).isoformat() if tu.timestamp else None,
                "stop_time_updates": stop_times,
            })
    return updates


def poll_alerts(url: str = ALERTS_URL, interval: int = 30):
    seen_ids = set()
    while True:
        alerts = fetch_gtfs_alerts(url)
        for alert in alerts:
            if alert["id"] not in seen_ids:
                print(f"[NEW] {alert['id']}: {alert['header']}")
                seen_ids.add(alert["id"])
        print(f"[{datetime.now().isoformat()}] {len(alerts)} active alerts")
        time.sleep(interval)


if __name__ == "__main__":
    alerts = fetch_gtfs_alerts()
    print(f"=== Alerts: {len(alerts)} ===")
    for a in alerts:
        print(json.dumps(a, indent=2))

    print()
    updates = fetch_trip_updates()
    print(f"=== Trip Updates: {len(updates)} ===")
    for u in updates:
        print(json.dumps(u, indent=2))

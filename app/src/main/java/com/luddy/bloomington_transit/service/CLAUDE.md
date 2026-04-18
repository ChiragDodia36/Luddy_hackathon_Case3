# service/ — Claude Context

## Files
- `BusTrackingService.kt` — Foreground service for background bus tracking
- `BusNotificationHelper.kt` — Builds + fires notifications

## BusTrackingService flow
1. `onCreate()` → `startForeground(FOREGROUND_ID, foregroundNotification)`
2. `startTracking()` coroutine loop every 10s:
   - Get tracked bus IDs from DataStore
   - Get user GPS from `FusedLocationProviderClient.lastLocation.await()`
   - Get nearest stop within 1km
   - For each tracked bus: find arrival at nearest stop
   - If `minutesUntil() <= threshold` → fire notification
   - `firedNotifications` set prevents duplicate alerts per vehicle+stop+minute
3. `onDestroy()` → `serviceScope.cancel()`

## Manifest requirements
```xml
android:foregroundServiceType="location"
```
Requires `FOREGROUND_SERVICE_LOCATION` permission.

## Notification channels (created in BloomingtonTransitApp)
- `bus_tracking` — HIGH importance, auto-cancels
- `foreground_service` — LOW importance, ongoing

## Test
Start the service by tracking a bus from MapScreen → tapping "Track This Bus".
Verify persistent notification appears. Move near a stop → arrival notification fires.

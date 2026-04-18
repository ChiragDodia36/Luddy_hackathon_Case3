# domain/ — Claude Context

## What this layer is
Pure Kotlin. Zero Android or 3rd-party dependencies.
Defines WHAT the app does, not HOW.

## Files
- `model/` — Data classes: Route, Stop, Bus, Arrival, ShapePoint, ServiceAlert
- `repository/TransitRepository.kt` — Interface only. All data access goes through this
- `usecase/` — Single-responsibility use cases that wrap repository calls

## Key design decision
`Arrival.minutesUntil()` computes countdown from `displayArrivalMs` (realtime if available, else scheduled).
`isRealtime` = `predictedArrivalMs > 0`.

## Test: If resuming
Check that all use cases inject `TransitRepository` correctly via Hilt.

# ui/ — Claude Context

## What this layer is
All Jetpack Compose UI. Follows MVVM: each screen has a paired ViewModel.

## Structure
- `BtApp.kt` — Root composable: Scaffold + bottom nav + NavHost
- `theme/` — Material3 theme, BT brand colors, countdown colors
- `navigation/NavGraph.kt` — Screen sealed class, BottomNavItem list
- `components/` — Reusable: CountdownChip, ArrivalRow, ServiceAlertBanner
- `screens/home/` — HomeScreen + HomeViewModel
- `screens/map/` — MapScreen + MapViewModel
- `screens/schedule/` — ScheduleScreen + ScheduleViewModel
- `screens/favourites/` — FavouritesScreen + FavouritesViewModel

## Theme notes
- `BloomingtonTransitTheme` in Theme.kt — dynamic color on Android 12+, dark mode auto
- `routeColor(hexString)` in Color.kt — converts GTFS hex to Compose Color
- Countdown colors: CountdownGreen (>3min), CountdownAmber (1-3min), CountdownRed (<1min)

## CountdownChip
Ticks every second via `LaunchedEffect(arrivalMs)` + `delay(1000L)`.
Color animates with `animateColorAsState(tween(500))`.
Shows `Nm *` for scheduled (non-realtime) arrivals.

## Navigation
`saveState`/`restoreState` = true → tab state survives navigation.
Animations: `fadeIn + slideInHorizontally` between screens.

## Polling pattern (all screens)
```kotlin
viewModelScope.launch {
    while (true) {
        refresh()
        delay(10_000L)
    }
}
```

## Test: If resuming
All screens compile. MapScreen requires Google Maps API key in local.properties.
The `isMyLocationEnabled` property on MapProperties requires location permission to be granted.

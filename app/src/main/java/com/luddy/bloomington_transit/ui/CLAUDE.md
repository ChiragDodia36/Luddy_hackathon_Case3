# ui/ — Claude Context

## What this layer is
All Jetpack Compose UI. Follows MVVM: each screen has a paired ViewModel.
Design system: **light glassmorphism + time-adaptive gradient** canvas.

## Structure
- `BtApp.kt` — Root composable: `TimeGradientBackground` → transparent Scaffold + glass nav bar
- `theme/` — Material3 theme, BT brand colors, countdown colors
- `theme/GlassComponents.kt` — `GlassCard`, `TimeGradientBackground`, `timeGreeting()`
- `navigation/NavGraph.kt` — Screen sealed class, BottomNavItem list
- `components/CountdownChip.kt` — Live ticking countdown pill
- `components/ArrivalRow.kt` — Single departure row
- `components/ServiceAlertBanner.kt` — Alert banner strip
- `components/HomeCards.kt` — `CountdownHeroCard`, `NearestStopCard`, `ContextCard`, `RoutePickerPromptCard`
- `screens/home/` — HomeScreen + HomeViewModel (adaptive context: time, location, route)
- `screens/map/` — MapScreen + MapViewModel (glass pill filter bar, persistent bottom panel)
- `screens/schedule/` — ScheduleScreen + ScheduleViewModel (recently viewed stops, live pulse dot)
- `screens/favourites/` — FavouritesScreen + FavouritesViewModel (swipe-to-dismiss, pinned route card)

## Glass Design System
- `GlassCard` — `Color.White.copy(alpha = 0.82f)` + white border 55% + 20dp radius + 2dp elevation
- `TimeGradientBackground` — soft vertical gradient driven by hour-of-day (warm morning → cool day → amber evening → soft night)
- `NavigationBar` — `Color.White.copy(alpha = 0.88f)` for frosted glass nav bar
- No actual backdrop blur (requires API 33+); semi-transparent white on gradient achieves the glass look

## Theme notes
- `BloomingtonTransitTheme` in Theme.kt — dynamic color on Android 12+, dark mode auto
- `routeColor(hexString)` in Color.kt — converts GTFS hex to Compose Color
- Countdown colors: CountdownGreen (>3min), CountdownAmber (1-3min), CountdownRed (<1min)
- `timeGreeting()` in GlassComponents.kt returns time-aware greeting string

## CountdownChip
Ticks every second via `LaunchedEffect(arrivalMs)` + `delay(1000L)`.
Color animates with `animateColorAsState(tween(500))`.
Shows `Nm *` for scheduled (non-realtime) arrivals.

## Navigation
`saveState`/`restoreState` = true → tab state survives navigation.
Animations: `fadeIn + slideInHorizontally` between screens.
All screens use floating title (no TopAppBar) to let gradient show through.

## Polling pattern (all screens)
```kotlin
viewModelScope.launch {
    while (true) {
        delay(10_000L)
        refresh()
    }
}
```

## HomeScreen adaptive context
`HomeViewModel` observes `TimeWindow` (MORNING/DAY/EVENING/NIGHT), nearest stop via
`FusedLocationProviderClient.lastLocation` → `updateLocation(lat, lon)`, and favourite route arrivals.
Three cards in `LazyColumn`: `CountdownHeroCard`, `NearestStopCard`, `ContextCard`.

## FavouritesScreen
- `PinnedRouteCard` — route-colored border card with live countdown for pinned route
- Each favourite stop wrapped in `SwipeToDismissBox` (EndToStart swipe → red delete background → `removeFavourite()`)
- `FavouriteStopData(stop, arrivals)` holds top 2 arrivals per stop

## ScheduleScreen
- `GlassCard`-wrapped `TextField` (transparent colors) for search
- Recently viewed stops shown as `AssistChip` horizontal strip
- `LivePulseDot` — `rememberInfiniteTransition` + `animateFloat` scale 0.85→1.3 pulsing green dot
- "Use my nearest stop" `OutlinedButton` in empty state

## MapScreen
- Glass pill filter bar: `Card(shape = RoundedCornerShape(24.dp), containerColor = White 88%)`
- Bus markers: pill-shaped `Box(height=28dp, RoundedCornerShape(14dp))`; tracked bus has pulse ring
- Stop markers: 16dp white circle + 10dp BtBlue inner; visible at zoom > 13f
- Persistent bottom panel (no `AnimatedVisibility`)

## Test: If resuming
All screens compile. MapScreen requires Google Maps API key in local.properties.
The `isMyLocationEnabled` property on MapProperties requires location permission to be granted.
`ServiceAlert` fields are `headerText` / `descriptionText` (NOT `header` / `description`).

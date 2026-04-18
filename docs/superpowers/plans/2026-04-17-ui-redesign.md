# UI Redesign — Light Glassmorphism + Adaptive Context

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current Material3 UI with a light glassmorphism aesthetic — frosted white glass cards on a time-shifting soft gradient canvas — across all four screens, plus adaptive home screen logic driven by time-of-day, location, and learned habits.

**Architecture:** UI-only redesign (no data layer changes except DataStore recent-stops addition). A shared `GlassCard` composable + `TimeGradientBackground` wrapper form the foundation. Each screen replaces its `TopAppBar` with a floating title header on the gradient canvas. `BtApp` wraps everything in `TimeGradientBackground` so the gradient is global.

**Tech Stack:** Jetpack Compose Material3, `play-services-location` (already in deps), Room/DataStore (existing), Kotlin Coroutines/Flow.

---

## File Map

| Action | File |
|---|---|
| CREATE | `ui/theme/GlassComponents.kt` — `GlassCard`, `TimeGradientBackground`, `timeGreeting()` |
| MODIFY | `ui/BtApp.kt` — wrap in gradient, glass NavigationBar |
| MODIFY | `ui/screens/home/HomeViewModel.kt` — add `TimeWindow`, nearest-stop + fav-route arrivals |
| CREATE | `ui/components/HomeCards.kt` — `CountdownHeroCard`, `NearestStopCard`, `ContextCard`, `RoutePickerPromptCard` |
| MODIFY | `ui/screens/home/HomeScreen.kt` — full redesign using glass cards |
| MODIFY | `ui/screens/map/MapScreen.kt` — glass filter bar, persistent mini panel, upgraded markers |
| MODIFY | `data/local/UserPreferencesDataStore.kt` — add `recentStopIds` + `addRecentStop()` |
| MODIFY | `domain/repository/TransitRepository.kt` — add `getRecentStopIds()` + `addRecentStop()` |
| MODIFY | `data/repository/TransitRepositoryImpl.kt` — implement recent stops |
| MODIFY | `ui/screens/schedule/ScheduleViewModel.kt` — add `recentStops`, `useNearestStop()` |
| MODIFY | `ui/screens/schedule/ScheduleScreen.kt` — full redesign |
| MODIFY | `ui/screens/favourites/FavouritesViewModel.kt` — add favourite route + arrivals |
| MODIFY | `ui/screens/favourites/FavouritesScreen.kt` — full redesign with glass cards + swipe |
| MODIFY | `ui/CLAUDE.md` — document new components |
| MODIFY | `data/CLAUDE.md` — document recent stops addition |

---

## Task 1: GlassCard + TimeGradientBackground Foundation

**Files:**
- Create: `app/src/main/java/com/luddy/bloomington_transit/ui/theme/GlassComponents.kt`

- [ ] **Step 1: Create GlassComponents.kt**

```kotlin
package com.luddy.bloomington_transit.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.util.Calendar

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.82f)
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.55f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        content = content
    )
}

@Composable
fun TimeGradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val gradientColors = remember(hour) {
        when {
            hour in 6..8   -> listOf(Color(0xFFFFF4E6), Color(0xFFD6E8FF))
            hour in 9..15  -> listOf(Color(0xFFF0F5FF), Color(0xFFE8F0FE))
            hour in 16..18 -> listOf(Color(0xFFFFF3E0), Color(0xFFFFE0B2))
            else           -> listOf(Color(0xFFEEF2FF), Color(0xFFE3E8F4))
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(brush = Brush.verticalGradient(colors = gradientColors)),
        content = content
    )
}

fun timeGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour in 6..8   -> "Good morning, catch your bus"
        hour in 9..11  -> "Good morning"
        hour in 12..15 -> "Good afternoon"
        hour in 16..18 -> "Good evening, heading home?"
        hour in 19..22 -> "Good evening"
        else           -> "Good night"
    }
}
```

- [ ] **Step 2: Build to verify**

```bash
cd /Users/chiragdodia/Desktop/luddy_hackathon_case3
./gradlew assembleDebug 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/luddy/bloomington_transit/ui/theme/GlassComponents.kt
git commit -m "feat: add GlassCard and TimeGradientBackground foundation"
```

---

## Task 2: Apply Gradient Globally + Glass Bottom Nav

**Files:**
- Modify: `app/src/main/java/com/luddy/bloomington_transit/ui/BtApp.kt`

- [ ] **Step 1: Replace BtApp.kt**

```kotlin
package com.luddy.bloomington_transit.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.luddy.bloomington_transit.ui.navigation.Screen
import com.luddy.bloomington_transit.ui.navigation.bottomNavItems
import com.luddy.bloomington_transit.ui.screens.favourites.FavouritesScreen
import com.luddy.bloomington_transit.ui.screens.home.HomeScreen
import com.luddy.bloomington_transit.ui.screens.map.MapScreen
import com.luddy.bloomington_transit.ui.screens.schedule.ScheduleScreen
import com.luddy.bloomington_transit.ui.theme.BtBlue
import com.luddy.bloomington_transit.ui.theme.TimeGradientBackground

@Composable
fun BtApp() {
    val navController = rememberNavController()

    TimeGradientBackground {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(
                    containerColor = Color.White.copy(alpha = 0.88f),
                    tonalElevation = 0.dp
                ) {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination

                    bottomNavItems.forEach { item ->
                        val itemRoute = if (item.screen is Screen.Map) "map" else item.screen.route
                        val selected = currentDestination?.hierarchy?.any { dest ->
                            dest.route?.startsWith(itemRoute) == true
                        } == true

                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = selected,
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = BtBlue.copy(alpha = 0.15f),
                                selectedIconColor = BtBlue,
                                selectedTextColor = BtBlue,
                                unselectedIconColor = Color(0xFF44474E),
                                unselectedTextColor = Color(0xFF44474E)
                            ),
                            onClick = {
                                val dest = if (item.screen is Screen.Map) "map" else item.screen.route
                                navController.navigate(dest) {
                                    popUpTo(Screen.Home.route) {
                                        inclusive = false
                                        saveState = false
                                    }
                                    launchSingleTop = true
                                    restoreState = false
                                }
                            }
                        )
                    }
                }
            }
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.padding(paddingValues),
                enterTransition = { fadeIn() + slideInHorizontally() },
                exitTransition = { fadeOut() + slideOutHorizontally() },
                popEnterTransition = { fadeIn() + slideInHorizontally { -it } },
                popExitTransition = { fadeOut() + slideOutHorizontally { it } }
            ) {
                composable(Screen.Home.route) { HomeScreen(navController) }
                composable(
                    route = Screen.Map.route,
                    arguments = listOf(navArgument("routeId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    })
                ) { backStackEntry ->
                    val routeId = backStackEntry.arguments?.getString("routeId")
                    MapScreen(navController = navController, initialRouteId = routeId)
                }
                composable(Screen.Schedule.route) { ScheduleScreen(navController) }
                composable(Screen.Favourites.route) { FavouritesScreen(navController) }
            }
        }
    }
}
```

- [ ] **Step 2: Build**

```bash
./gradlew assembleDebug 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Visual check**
Install on device/emulator. Verify the gradient background is visible behind the bottom nav bar. The nav bar should appear slightly frosted/white-tinted. All 4 tabs should still navigate correctly.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/luddy/bloomington_transit/ui/BtApp.kt
git commit -m "feat: apply global time-gradient canvas and glass bottom nav"
```

---

## Task 3: HomeViewModel — Adaptive Context Data

**Files:**
- Modify: `app/src/main/java/com/luddy/bloomington_transit/ui/screens/home/HomeViewModel.kt`

- [ ] **Step 1: Replace HomeViewModel.kt**

```kotlin
package com.luddy.bloomington_transit.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luddy.bloomington_transit.domain.model.*
import com.luddy.bloomington_transit.domain.repository.TransitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

enum class TimeWindow { MORNING, DAY, EVENING, NIGHT }

data class HomeUiState(
    val isLoading: Boolean = true,
    val routes: List<Route> = emptyList(),
    val favouriteRouteId: String? = null,
    val serviceAlerts: List<ServiceAlert> = emptyList(),
    val errorMessage: String? = null,
    val timeWindow: TimeWindow = TimeWindow.DAY,
    val favouriteRouteArrivals: List<Arrival> = emptyList(),
    val nearestStop: Stop? = null,
    val nearestStopArrivals: List<Arrival> = emptyList(),
    val isNearSavedStop: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: TransitRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(timeWindow = currentTimeWindow()) }
        viewModelScope.launch {
            repository.initStaticData()
            observeData()
        }
    }

    private fun currentTimeWindow(): TimeWindow {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour in 6..8   -> TimeWindow.MORNING
            hour in 9..15  -> TimeWindow.DAY
            hour in 16..18 -> TimeWindow.EVENING
            else           -> TimeWindow.NIGHT
        }
    }

    private fun observeData() {
        viewModelScope.launch {
            combine(
                repository.getRoutes(),
                repository.getFavouriteRouteId()
            ) { routes, favRouteId -> routes to favRouteId }
                .collect { (routes, favRouteId) ->
                    _uiState.update {
                        it.copy(isLoading = false, routes = routes, favouriteRouteId = favRouteId)
                    }
                    favRouteId?.let { loadFavouriteRouteArrivals(it) }
                }
        }
        viewModelScope.launch {
            while (true) {
                try {
                    val alerts = repository.getServiceAlerts()
                    _uiState.update { it.copy(serviceAlerts = alerts) }
                } catch (_: Exception) {}
                kotlinx.coroutines.delay(30_000L)
            }
        }
        // Refresh fav route arrivals every 10s
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(10_000L)
                _uiState.value.favouriteRouteId?.let { loadFavouriteRouteArrivals(it) }
            }
        }
    }

    fun updateLocation(lat: Double, lon: Double) {
        viewModelScope.launch {
            try {
                val nearestStops = repository.getNearestStops(lat, lon, radiusMeters = 400.0)
                val nearestStop = nearestStops.firstOrNull()
                val arrivals = nearestStop
                    ?.let { repository.getArrivalsForStop(it.id).take(3) }
                    ?: emptyList()
                val savedIds = repository.getFavouriteStopIds().first()
                val isNearSaved = nearestStops.any { it.id in savedIds }
                _uiState.update {
                    it.copy(
                        nearestStop = nearestStop,
                        nearestStopArrivals = arrivals,
                        isNearSavedStop = isNearSaved
                    )
                }
                // If near saved stop, also refresh fav route arrivals using that stop
                _uiState.value.favouriteRouteId?.let { loadFavouriteRouteArrivals(it) }
            } catch (_: Exception) {}
        }
    }

    private fun loadFavouriteRouteArrivals(routeId: String) {
        viewModelScope.launch {
            try {
                val nearestStop = _uiState.value.nearestStop
                val stopsForRoute = repository.getStopsForRoute(routeId).first()
                val targetStop = if (nearestStop != null && stopsForRoute.any { it.id == nearestStop.id }) {
                    nearestStop
                } else {
                    stopsForRoute.firstOrNull()
                }
                val arrivals = targetStop
                    ?.let { repository.getArrivalsForRoute(routeId, it.id).take(2) }
                    ?: emptyList()
                _uiState.update { it.copy(favouriteRouteArrivals = arrivals) }
            } catch (_: Exception) {}
        }
    }

    fun setFavouriteRoute(routeId: String?) {
        viewModelScope.launch { repository.setFavouriteRouteId(routeId) }
    }
}
```

- [ ] **Step 2: Build**

```bash
./gradlew assembleDebug 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/luddy/bloomington_transit/ui/screens/home/HomeViewModel.kt
git commit -m "feat: add adaptive context data to HomeViewModel (time window, nearest stop, fav route arrivals)"
```

---

## Task 4: Home Screen Card Components

**Files:**
- Create: `app/src/main/java/com/luddy/bloomington_transit/ui/components/HomeCards.kt`

- [ ] **Step 1: Create HomeCards.kt**

```kotlin
package com.luddy.bloomington_transit.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.luddy.bloomington_transit.domain.model.Arrival
import com.luddy.bloomington_transit.domain.model.Route
import com.luddy.bloomington_transit.domain.model.ServiceAlert
import com.luddy.bloomington_transit.domain.model.Stop
import com.luddy.bloomington_transit.ui.theme.BtBlue
import com.luddy.bloomington_transit.ui.theme.CountdownAmber
import com.luddy.bloomington_transit.ui.theme.CountdownGreen
import com.luddy.bloomington_transit.ui.theme.GlassCard
import com.luddy.bloomington_transit.ui.theme.routeColor
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────────
// Card 1 — Live Countdown Hero
// ─────────────────────────────────────────────────────────────────

@Composable
fun CountdownHeroCard(
    route: Route,
    arrivals: List<Arrival>,
    onViewOnMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = routeColor(route.color)
    val firstArrival = arrivals.firstOrNull()

    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {

            // Route header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(color),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        route.shortName.take(3),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    firstArrival?.headsign?.ifBlank { route.longName } ?: route.longName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))

            // Big countdown
            if (firstArrival != null) {
                CountdownHeroDisplay(
                    arrivalMs = firstArrival.displayArrivalMs,
                    isRealtime = firstArrival.isRealtime
                )
            } else {
                Text(
                    "No upcoming buses",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(6.dp))

            // Live indicator row
            if (firstArrival?.isRealtime == true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(CountdownGreen)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Live",
                        style = MaterialTheme.typography.labelSmall,
                        color = CountdownGreen
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onViewOnMap,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = color)
            ) {
                Icon(Icons.Filled.Map, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("View on Map", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CountdownHeroDisplay(arrivalMs: Long, isRealtime: Boolean) {
    var minutesLeft by remember(arrivalMs) {
        mutableLongStateOf(((arrivalMs - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0L))
    }
    LaunchedEffect(arrivalMs) {
        while (true) {
            minutesLeft = ((arrivalMs - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0L)
            delay(1000L)
        }
    }
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = "$minutesLeft",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = BtBlue
        )
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.padding(bottom = 10.dp)) {
            Text(
                "min away",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!isRealtime) {
                Text(
                    "scheduled",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Card 2 — Nearest Stop Arrivals
// ─────────────────────────────────────────────────────────────────

@Composable
fun NearestStopCard(
    stop: Stop,
    arrivals: List<Arrival>,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Place,
                    contentDescription = null,
                    tint = BtBlue,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Nearest Stop: ${stop.name}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Color.Gray.copy(alpha = 0.15f))
            Spacer(Modifier.height(8.dp))
            if (arrivals.isEmpty()) {
                Text(
                    "No upcoming buses at this stop",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                arrivals.forEach { NearestStopArrivalRow(it) }
            }
        }
    }
}

@Composable
private fun NearestStopArrivalRow(arrival: Arrival) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(routeColor(arrival.routeColor)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                arrival.routeShortName.take(3),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            arrival.headsign.ifBlank { "Route ${arrival.routeShortName}" },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (arrival.isRealtime) "●" else "○",
                color = if (arrival.isRealtime) CountdownGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.width(4.dp))
            CountdownChip(arrivalMs = arrival.displayArrivalMs, isRealtime = arrival.isRealtime)
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Card 3 — Context Card (alert / second route / onboarding)
// ─────────────────────────────────────────────────────────────────

@Composable
fun ContextCard(
    alerts: List<ServiceAlert>,
    isNewUser: Boolean,
    modifier: Modifier = Modifier
) {
    when {
        alerts.isNotEmpty() -> AlertContextCard(alert = alerts.first(), modifier = modifier)
        isNewUser -> OnboardingContextCard(modifier = modifier)
        else -> {} // nothing to show — Card 3 collapses
    }
}

@Composable
private fun AlertContextCard(alert: ServiceAlert, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF3E0).copy(alpha = 0.92f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, CountdownAmber.copy(alpha = 0.50f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = CountdownAmber,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    alert.header,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (alert.description.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        alert.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingContextCard(modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.DirectionsBus,
                contentDescription = null,
                tint = BtBlue.copy(alpha = 0.45f),
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Pin a route to track it here",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Route picker prompt (shown when no favourite route set)
// ─────────────────────────────────────────────────────────────────

@Composable
fun RoutePickerPromptCard(
    routes: List<Route>,
    onRouteSelected: (Route) -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Select Your Route",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Choose a route to track — saved for next time",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            routes.take(6).forEach { route ->
                val color = routeColor(route.color)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onRouteSelected(route) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(color),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            route.shortName.take(3),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(route.longName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Route ${route.shortName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build**

```bash
./gradlew assembleDebug 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/luddy/bloomington_transit/ui/components/HomeCards.kt
git commit -m "feat: add CountdownHeroCard, NearestStopCard, ContextCard, RoutePickerPromptCard"
```

---

## Task 5: Home Screen Full Redesign

**Files:**
- Modify: `app/src/main/java/com/luddy/bloomington_transit/ui/screens/home/HomeScreen.kt`

- [ ] **Step 1: Replace HomeScreen.kt**

```kotlin
package com.luddy.bloomington_transit.ui.screens.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.android.gms.location.LocationServices
import com.luddy.bloomington_transit.ui.components.ContextCard
import com.luddy.bloomington_transit.ui.components.CountdownHeroCard
import com.luddy.bloomington_transit.ui.components.NearestStopCard
import com.luddy.bloomington_transit.ui.components.RoutePickerPromptCard
import com.luddy.bloomington_transit.ui.navigation.Screen
import com.luddy.bloomington_transit.ui.navigation.routeWithArg
import com.luddy.bloomington_transit.ui.theme.timeGreeting

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Request last known location once on launch
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                LocationServices.getFusedLocationProviderClient(context)
                    .lastLocation
                    .addOnSuccessListener { loc ->
                        loc?.let { viewModel.updateLocation(it.latitude, it.longitude) }
                    }
            } catch (_: Exception) {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(20.dp))

        // Floating header — no TopAppBar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                timeGreeting(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { /* future: notification settings */ }) {
                Icon(
                    Icons.Filled.Notifications,
                    contentDescription = "Notifications",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Loading transit data…",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "This takes ~15 seconds on first launch.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            return@Column
        }

        val favouriteRoute = uiState.routes.find { it.id == uiState.favouriteRouteId }
        val isNewUser = favouriteRoute == null && uiState.nearestStop == null

        // Adaptive card stack
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Card 1 — Countdown hero or route picker
            item {
                if (favouriteRoute != null) {
                    CountdownHeroCard(
                        route = favouriteRoute,
                        arrivals = uiState.favouriteRouteArrivals,
                        onViewOnMap = {
                            navController.navigate(Screen.Map.routeWithArg(favouriteRoute.id))
                        }
                    )
                } else {
                    RoutePickerPromptCard(
                        routes = uiState.routes,
                        onRouteSelected = { route ->
                            viewModel.setFavouriteRoute(route.id)
                            navController.navigate(Screen.Map.routeWithArg(route.id))
                        }
                    )
                }
            }

            // Card 2 — Nearest stop (location-aware, shown when available)
            uiState.nearestStop?.let { stop ->
                item {
                    NearestStopCard(
                        stop = stop,
                        arrivals = uiState.nearestStopArrivals
                    )
                }
            }

            // Card 3 — Context (alert / onboarding)
            item {
                ContextCard(
                    alerts = uiState.serviceAlerts,
                    isNewUser = isNewUser
                )
            }
        }
    }
}
```

- [ ] **Step 2: Build**

```bash
./gradlew assembleDebug 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Visual check**
Install on device. Verify:
- Greeting text shows at top with no top bar container
- If favourite route is set: countdown hero card visible
- If no favourite route: route picker card with list of routes
- Gradient canvas shows through all cards
- Nearest stop card appears after granting location permission

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/luddy/bloomington_transit/ui/screens/home/HomeScreen.kt
git commit -m "feat: redesign Home screen with glass card stack and adaptive context"
```

---

## Task 6: Map Screen — Glass Filter Bar + Persistent Mini Panel + Upgraded Markers

**Files:**
- Modify: `app/src/main/java/com/luddy/bloomington_transit/ui/screens/map/MapScreen.kt`

- [ ] **Step 1: Replace MapScreen.kt** (full file — replaces all composables)

```kotlin
package com.luddy.bloomington_transit.ui.screens.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.maps.android.compose.*
import com.luddy.bloomington_transit.domain.model.Bus
import com.luddy.bloomington_transit.domain.model.Stop
import com.luddy.bloomington_transit.ui.components.ArrivalRow
import com.luddy.bloomington_transit.ui.theme.BtBlue
import com.luddy.bloomington_transit.ui.theme.CountdownGreen
import com.luddy.bloomington_transit.ui.theme.routeColor

private val BLOOMINGTON_CENTER = LatLng(39.1653, -86.5264)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    navController: NavController,
    initialRouteId: String? = null,
    viewModel: MapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(BLOOMINGTON_CENTER, 13f)
    }

    LaunchedEffect(initialRouteId, uiState.routes) {
        if (initialRouteId != null && uiState.routes.isNotEmpty()) {
            viewModel.selectOnlyRoute(initialRouteId)
        }
    }

    val hasLocationPermission = remember {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(uiState.selectedBus) {
        uiState.selectedBus?.let { bus ->
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(bus.lat, bus.lon), 15f)
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // Full-screen Google Map
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(myLocationButtonEnabled = hasLocationPermission),
            properties = MapProperties(isMyLocationEnabled = hasLocationPermission)
        ) {
            // Route polylines
            uiState.selectedRouteIds.forEach { routeId ->
                val segments = uiState.shapesByRoute[routeId] ?: return@forEach
                val route = uiState.routes.find { it.id == routeId }
                val color = route?.color?.let { routeColor(it) } ?: BtBlue
                segments.forEach { points ->
                    if (points.size >= 2) {
                        Polyline(points = points, color = color, width = 10f)
                    }
                }
            }

            // Bus markers
            uiState.buses
                .filter { it.routeId in uiState.selectedRouteIds }
                .forEach { bus ->
                    val route = uiState.routes.find { it.id == bus.routeId }
                    val color = route?.color?.let { routeColor(it) } ?: BtBlue
                    val isTracked = bus.vehicleId in uiState.trackedBusIds
                    MarkerComposable(
                        state = MarkerState(position = LatLng(bus.lat, bus.lon)),
                        title = "Route ${route?.shortName ?: bus.routeId}",
                        snippet = bus.label,
                        onClick = { viewModel.selectBus(bus); false }
                    ) {
                        BusMarker(
                            routeShortName = route?.shortName ?: "?",
                            color = color,
                            isTracked = isTracked
                        )
                    }
                }

            // Stop markers — visible from zoom 13f
            if (cameraPositionState.position.zoom > 13f) {
                uiState.stops.take(80).forEach { stop ->
                    MarkerComposable(
                        state = MarkerState(position = LatLng(stop.lat, stop.lon)),
                        title = stop.name,
                        onClick = { viewModel.selectStop(stop); false }
                    ) {
                        StopMarker()
                    }
                }
            }
        }

        // Loading overlay
        if (uiState.isInitializing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Loading bus routes…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            return@Box
        }

        // ── Glass route filter bar (top) ──────────────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, start = 12.dp, end = 12.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.88f)
            ),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.60f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val allSelected = uiState.selectedRouteIds.size == uiState.routes.size
                FilterChip(
                    selected = allSelected,
                    onClick = { viewModel.toggleAllRoutes() },
                    label = { Text("All", fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BtBlue,
                        selectedLabelColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
                uiState.routes.forEach { route ->
                    val selected = route.id in uiState.selectedRouteIds
                    val color = routeColor(route.color)
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.toggleRoute(route.id) },
                        label = { Text(route.shortName, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = color,
                            selectedLabelColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
        }

        // ── Persistent glass mini panel (bottom) ─────────────────────
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.88f)
            ),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.60f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column {
                // Drag handle
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 36.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.Gray.copy(alpha = 0.35f))
                    )
                }

                when {
                    uiState.selectedBus != null -> BusDetailPanel(
                        bus = uiState.selectedBus!!,
                        route = uiState.routes.find { it.id == uiState.selectedBus!!.routeId },
                        isTracked = uiState.selectedBus!!.vehicleId in uiState.trackedBusIds,
                        onTrackToggle = { bus ->
                            if (bus.vehicleId in uiState.trackedBusIds) viewModel.untrackBus(bus.vehicleId)
                            else viewModel.trackBus(bus.vehicleId)
                        },
                        onDismiss = { viewModel.dismissBottomSheet() }
                    )
                    uiState.selectedStop != null -> StopDetailPanel(
                        stop = uiState.selectedStop!!,
                        arrivals = uiState.stopArrivals,
                        isLoading = uiState.isLoadingArrivals,
                        onDismiss = { viewModel.dismissBottomSheet() }
                    )
                    else -> {
                        val busCount = uiState.buses.count { it.routeId in uiState.selectedRouteIds }
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.DirectionsBus,
                                contentDescription = null,
                                tint = BtBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (uiState.selectedRouteIds.isEmpty())
                                    "Tap a route chip above to see buses"
                                else
                                    "$busCount bus${if (busCount != 1) "es" else ""} on selected routes · Tap a bus or stop",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BusMarker(routeShortName: String, color: Color, isTracked: Boolean) {
    Box(contentAlignment = Alignment.Center) {
        if (isTracked) {
            val infiniteTransition = rememberInfiniteTransition(label = "tracked_pulse")
            val scale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.5f,
                animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                label = "pulse_scale"
            )
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.22f))
            )
        }
        Box(
            modifier = Modifier
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(color)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                routeShortName.take(3),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun StopMarker() {
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(BtBlue.copy(alpha = 0.75f))
        )
    }
}

@Composable
private fun BusDetailPanel(
    bus: Bus,
    route: com.luddy.bloomington_transit.domain.model.Route?,
    isTracked: Boolean,
    onTrackToggle: (Bus) -> Unit,
    onDismiss: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            route?.let { r ->
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(routeColor(r.color)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(r.shortName, color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(r.longName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Vehicle ${bus.vehicleId}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss")
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onTrackToggle(bus) },
            modifier = Modifier.fillMaxWidth(),
            colors = if (isTracked) ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ) else ButtonDefaults.buttonColors()
        ) {
            Icon(
                if (isTracked) Icons.Filled.NotificationsOff else Icons.Filled.NotificationsActive,
                contentDescription = null
            )
            Spacer(Modifier.width(8.dp))
            Text(if (isTracked) "Stop Tracking" else "Track This Bus")
        }
    }
}

@Composable
private fun StopDetailPanel(
    stop: Stop,
    arrivals: List<com.luddy.bloomington_transit.domain.model.Arrival>,
    isLoading: Boolean,
    onDismiss: () -> Unit
) {
    Column(modifier = Modifier.heightIn(max = 300.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Place, contentDescription = null, tint = BtBlue)
            Spacer(Modifier.width(8.dp))
            Text(
                stop.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss")
            }
        }
        HorizontalDivider(color = Color.Gray.copy(alpha = 0.12f))
        if (isLoading) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(80.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        } else if (arrivals.isEmpty()) {
            Text(
                "No upcoming arrivals",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn {
                items(arrivals.take(5)) { arrival -> ArrivalRow(arrival = arrival) }
            }
        }
    }
}
```

- [ ] **Step 2: Build**

```bash
./gradlew assembleDebug 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Visual check**
Install on device. Verify:
- Route filter chips are inside a glass pill container floating over the map
- A persistent glass panel is always visible at the bottom (not just when a bus/stop is tapped)
- Tapping a bus shows the bus detail in the bottom panel
- Bus markers are pill-shaped (not square)
- Stop markers are white rings (not plain dots), visible from zoom 13f

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/luddy/bloomington_transit/ui/screens/map/MapScreen.kt
git commit -m "feat: redesign Map screen with glass filter bar, persistent mini panel, upgraded markers"
```

---

## Task 7: DataStore + Repository — Recent Stops

**Files:**
- Modify: `app/src/main/java/com/luddy/bloomington_transit/data/local/UserPreferencesDataStore.kt`
- Modify: `app/src/main/java/com/luddy/bloomington_transit/domain/repository/TransitRepository.kt`
- Modify: `app/src/main/java/com/luddy/bloomington_transit/data/repository/TransitRepositoryImpl.kt`

- [ ] **Step 1: Add recentStopIds to UserPreferencesDataStore.kt**

Add inside the `Keys` object (after `FAVOURITE_ROUTE_ID`):
```kotlin
val RECENT_STOP_IDS = stringPreferencesKey("recent_stop_ids")
```

Add after `val favouriteRouteId` flow:
```kotlin
val recentStopIds: Flow<List<String>> = context.dataStore.data
    .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
    .map { prefs ->
        prefs[Keys.RECENT_STOP_IDS]
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }
```

Add after `setFavouriteRouteId`:
```kotlin
suspend fun addRecentStop(stopId: String) {
    context.dataStore.edit { prefs ->
        val current = prefs[Keys.RECENT_STOP_IDS]
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        val updated = (listOf(stopId) + current.filter { it != stopId }).take(5)
        prefs[Keys.RECENT_STOP_IDS] = updated.joinToString(",")
    }
}
```

- [ ] **Step 2: Add to TransitRepository interface**

Add after `setFavouriteRouteId`:
```kotlin
fun getRecentStopIds(): Flow<List<String>>
suspend fun addRecentStop(stopId: String)
```

- [ ] **Step 3: Implement in TransitRepositoryImpl**

Add after `setFavouriteRouteId` implementation:
```kotlin
override fun getRecentStopIds(): Flow<List<String>> = prefs.recentStopIds
override suspend fun addRecentStop(stopId: String) = prefs.addRecentStop(stopId)
```

- [ ] **Step 4: Build**

```bash
./gradlew assembleDebug 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luddy/bloomington_transit/data/local/UserPreferencesDataStore.kt
git add app/src/main/java/com/luddy/bloomington_transit/domain/repository/TransitRepository.kt
git add app/src/main/java/com/luddy/bloomington_transit/data/repository/TransitRepositoryImpl.kt
git commit -m "feat: add recent stops persistence to DataStore and Repository"
```

---

## Task 8: Schedule Screen Full Redesign

**Files:**
- Modify: `app/src/main/java/com/luddy/bloomington_transit/ui/screens/schedule/ScheduleViewModel.kt`
- Modify: `app/src/main/java/com/luddy/bloomington_transit/ui/screens/schedule/ScheduleScreen.kt`

- [ ] **Step 1: Replace ScheduleViewModel.kt**

```kotlin
package com.luddy.bloomington_transit.ui.screens.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luddy.bloomington_transit.domain.model.Arrival
import com.luddy.bloomington_transit.domain.model.Stop
import com.luddy.bloomington_transit.domain.repository.TransitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScheduleUiState(
    val searchQuery: String = "",
    val searchResults: List<Stop> = emptyList(),
    val selectedStop: Stop? = null,
    val arrivals: List<Arrival> = emptyList(),
    val showRealtimeOnly: Boolean = false,
    val isLoading: Boolean = false,
    val recentStops: List<Stop> = emptyList()
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repository: TransitRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    init {
        observeRecentStops()
        startPolling()
    }

    private fun observeRecentStops() {
        viewModelScope.launch {
            repository.getRecentStopIds().collect { ids ->
                val stops = ids.mapNotNull { id ->
                    repository.searchStops(id).firstOrNull { it.id == id }
                }
                _uiState.update { it.copy(recentStops = stops) }
            }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        viewModelScope.launch {
            val results = if (query.length >= 2) repository.searchStops(query) else emptyList()
            _uiState.update { it.copy(searchResults = results) }
        }
    }

    fun selectStop(stop: Stop) {
        _uiState.update {
            it.copy(
                selectedStop = stop,
                searchQuery = stop.name,
                searchResults = emptyList(),
                isLoading = true
            )
        }
        viewModelScope.launch { repository.addRecentStop(stop.id) }
        loadArrivals(stop.id)
    }

    fun useNearestStop(lat: Double, lon: Double) {
        viewModelScope.launch {
            val stops = repository.getNearestStops(lat, lon, radiusMeters = 500.0)
            stops.firstOrNull()?.let { selectStop(it) }
        }
    }

    fun toggleRealtimeFilter() {
        _uiState.update { it.copy(showRealtimeOnly = !it.showRealtimeOnly) }
    }

    private fun loadArrivals(stopId: String) {
        viewModelScope.launch {
            val arrivals = repository.getArrivalsForStop(stopId)
            _uiState.update { it.copy(arrivals = arrivals, isLoading = false) }
        }
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                delay(10_000L)
                _uiState.value.selectedStop?.id?.let { loadArrivals(it) }
            }
        }
    }
}
```

- [ ] **Step 2: Replace ScheduleScreen.kt**

```kotlin
package com.luddy.bloomington_transit.ui.screens.schedule

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.android.gms.location.LocationServices
import com.luddy.bloomington_transit.domain.model.Arrival
import com.luddy.bloomington_transit.ui.components.CountdownChip
import com.luddy.bloomington_transit.ui.theme.BtBlue
import com.luddy.bloomington_transit.ui.theme.CountdownGreen
import com.luddy.bloomington_transit.ui.theme.GlassCard
import com.luddy.bloomington_transit.ui.theme.routeColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    navController: NavController,
    viewModel: ScheduleViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(20.dp))

        // Floating header
        Text(
            "Schedule",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(12.dp))

        // Glass search bar
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search stops…") },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear",
                                modifier = Modifier.size(18.dp))
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                singleLine = true
            )
        }

        // Search results dropdown
        if (uiState.searchResults.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    uiState.searchResults.take(8).forEachIndexed { idx, stop ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectStop(stop) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Place, contentDescription = null,
                                tint = BtBlue, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stop.name, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (idx < uiState.searchResults.take(8).lastIndex) {
                            HorizontalDivider(color = Color.Gray.copy(alpha = 0.10f))
                        }
                    }
                }
            }
        }

        // Recently viewed strip
        if (uiState.searchResults.isEmpty()
            && uiState.selectedStop == null
            && uiState.recentStops.isNotEmpty()
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Recently viewed",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.recentStops.forEach { stop ->
                    AssistChip(
                        onClick = { viewModel.selectStop(stop) },
                        label = {
                            Text(stop.name, style = MaterialTheme.typography.labelSmall,
                                maxLines = 1)
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color.White.copy(alpha = 0.82f)
                        ),
                        border = AssistChipDefaults.assistChipBorder(
                            enabled = true,
                            borderColor = Color.White.copy(alpha = 0.55f)
                        )
                    )
                }
            }
        }

        // Stop header + departure board
        uiState.selectedStop?.let { stop ->
            Spacer(Modifier.height(12.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Place, contentDescription = null,
                        tint = BtBlue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stop.name, style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    FilterChip(
                        selected = uiState.showRealtimeOnly,
                        onClick = viewModel::toggleRealtimeFilter,
                        label = { Text("Live only", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(Icons.Filled.FlashOn, contentDescription = null,
                                modifier = Modifier.size(14.dp))
                        }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (uiState.isLoading) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val displayed = if (uiState.showRealtimeOnly)
                    uiState.arrivals.filter { it.isRealtime } else uiState.arrivals

                if (displayed.isEmpty()) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text("No upcoming departures",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            displayed.forEachIndexed { idx, arrival ->
                                GlassDepartureBoardRow(arrival = arrival)
                                if (idx < displayed.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = Color.Gray.copy(alpha = 0.10f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } ?: run {
            // Empty state — only when nothing is shown above
            if (uiState.searchResults.isEmpty() && uiState.recentStops.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Schedule, contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                        Spacer(Modifier.height(12.dp))
                        Text("Search a stop above to see live arrivals",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.ACCESS_COARSE_LOCATION
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    LocationServices.getFusedLocationProviderClient(context)
                                        .lastLocation
                                        .addOnSuccessListener { loc ->
                                            loc?.let {
                                                viewModel.useNearestStop(it.latitude, it.longitude)
                                            }
                                        }
                                }
                            },
                            shape = RoundedCornerShape(20.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, Color.White.copy(alpha = 0.60f)
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.White.copy(alpha = 0.60f)
                            )
                        ) {
                            Icon(Icons.Filled.Place, contentDescription = null,
                                modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Use my nearest stop")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassDepartureBoardRow(arrival: Arrival) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(routeColor(arrival.routeColor)),
            contentAlignment = Alignment.Center
        ) {
            Text(arrival.routeShortName.take(3), color = Color.White,
                fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                arrival.headsign.ifBlank { "Route ${arrival.routeShortName}" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (arrival.isRealtime) {
                    LivePulseDot()
                    Spacer(Modifier.width(3.dp))
                    Text("Live", style = MaterialTheme.typography.labelSmall,
                        color = CountdownGreen)
                } else {
                    Text("Scheduled", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        CountdownChip(arrivalMs = arrival.displayArrivalMs, isRealtime = arrival.isRealtime)
    }
}

@Composable
private fun LivePulseDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "live_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "dot_scale"
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(CountdownGreen)
    )
}
```

- [ ] **Step 3: Build**

```bash
./gradlew assembleDebug 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Visual check**
Install on device. Verify:
- No TopAppBar — just floating "Schedule" title
- Search bar is inside a glass card (no outlined border look)
- Recent stops appear as chips after a stop is selected and you return
- Arrivals are inside a single glass card (not floating rows)
- Live arrivals show animated green pulsing dot

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luddy/bloomington_transit/ui/screens/schedule/ScheduleViewModel.kt
git add app/src/main/java/com/luddy/bloomington_transit/ui/screens/schedule/ScheduleScreen.kt
git commit -m "feat: redesign Schedule screen with glass search, recent stops, animated departure board"
```

---

## Task 9: Favourites Screen Full Redesign

**Files:**
- Modify: `app/src/main/java/com/luddy/bloomington_transit/ui/screens/favourites/FavouritesViewModel.kt`
- Modify: `app/src/main/java/com/luddy/bloomington_transit/ui/screens/favourites/FavouritesScreen.kt`

- [ ] **Step 1: Replace FavouritesViewModel.kt**

```kotlin
package com.luddy.bloomington_transit.ui.screens.favourites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luddy.bloomington_transit.domain.model.Arrival
import com.luddy.bloomington_transit.domain.model.Route
import com.luddy.bloomington_transit.domain.model.Stop
import com.luddy.bloomington_transit.domain.repository.TransitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FavouriteStopData(
    val stop: Stop,
    val arrivals: List<Arrival>
)

data class FavouritesUiState(
    val favourites: List<FavouriteStopData> = emptyList(),
    val isLoading: Boolean = true,
    val favouriteRoute: Route? = null,
    val favouriteRouteArrivals: List<Arrival> = emptyList()
)

@HiltViewModel
class FavouritesViewModel @Inject constructor(
    private val repository: TransitRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavouritesUiState())
    val uiState: StateFlow<FavouritesUiState> = _uiState.asStateFlow()

    init {
        observeFavourites()
        observeFavouriteRoute()
        startPolling()
    }

    private fun observeFavourites() {
        viewModelScope.launch {
            repository.getFavouriteStopIds().collect { ids ->
                loadFavourites(ids)
            }
        }
    }

    private fun observeFavouriteRoute() {
        viewModelScope.launch {
            combine(
                repository.getFavouriteRouteId(),
                repository.getRoutes()
            ) { routeId, routes -> routeId to routes }
                .collect { (routeId, routes) ->
                    val route = routes.find { it.id == routeId }
                    _uiState.update { it.copy(favouriteRoute = route) }
                    route?.let { loadFavouriteRouteArrivals(it.id) }
                }
        }
    }

    private suspend fun loadFavourites(ids: Set<String>) {
        val data = ids.mapNotNull { stopId ->
            val stop = repository.searchStops(stopId).firstOrNull() ?: return@mapNotNull null
            val arrivals = repository.getArrivalsForStop(stopId).take(2)
            FavouriteStopData(stop, arrivals)
        }
        _uiState.update { it.copy(favourites = data, isLoading = false) }
    }

    private fun loadFavouriteRouteArrivals(routeId: String) {
        viewModelScope.launch {
            try {
                val stops = repository.getStopsForRoute(routeId).first()
                val arrivals = stops.firstOrNull()
                    ?.let { repository.getArrivalsForRoute(routeId, it.id).take(2) }
                    ?: emptyList()
                _uiState.update { it.copy(favouriteRouteArrivals = arrivals) }
            } catch (_: Exception) {}
        }
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                delay(10_000L)
                val ids = repository.getFavouriteStopIds().first()
                loadFavourites(ids)
                _uiState.value.favouriteRoute?.let { loadFavouriteRouteArrivals(it.id) }
            }
        }
    }

    fun removeFavourite(stopId: String) {
        viewModelScope.launch { repository.removeFavouriteStop(stopId) }
    }
}
```

- [ ] **Step 2: Replace FavouritesScreen.kt**

```kotlin
package com.luddy.bloomington_transit.ui.screens.favourites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.luddy.bloomington_transit.ui.components.CountdownChip
import com.luddy.bloomington_transit.ui.navigation.Screen
import com.luddy.bloomington_transit.ui.navigation.routeWithArg
import com.luddy.bloomington_transit.ui.theme.BtBlue
import com.luddy.bloomington_transit.ui.theme.CountdownGreen
import com.luddy.bloomington_transit.ui.theme.GlassCard
import com.luddy.bloomington_transit.ui.theme.routeColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavouritesScreen(
    navController: NavController,
    viewModel: FavouritesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(20.dp))

        // Floating header
        Text(
            "Your saved stops",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(16.dp))

        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        if (uiState.favourites.isEmpty() && uiState.favouriteRoute == null) {
            // Empty state
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Star, contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                    Spacer(Modifier.height(12.dp))
                    Text("No saved stops yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Head to the Schedule tab, search a stop,\nand tap ♡ to save it here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { navController.navigate(Screen.Schedule.route) },
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, Color.White.copy(alpha = 0.60f)
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White.copy(alpha = 0.60f)
                        )
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Search Stops")
                    }
                }
            }
            return@Column
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Pinned route card (if favourite route is set)
            uiState.favouriteRoute?.let { route ->
                item {
                    PinnedRouteCard(
                        route = route,
                        arrivals = uiState.favouriteRouteArrivals,
                        onViewOnMap = {
                            navController.navigate(Screen.Map.routeWithArg(route.id))
                        }
                    )
                }
            }

            // Per-stop glass cards with swipe-to-remove
            items(uiState.favourites, key = { it.stop.id }) { data ->
                SwipeToRemoveFavouriteCard(
                    data = data,
                    onRemove = { viewModel.removeFavourite(data.stop.id) },
                    onViewOnMap = {
                        navController.navigate(Screen.Map.route.replace("?routeId={routeId}", ""))
                    }
                )
            }
        }
    }
}

@Composable
private fun PinnedRouteCard(
    route: com.luddy.bloomington_transit.domain.model.Route,
    arrivals: List<com.luddy.bloomington_transit.domain.model.Arrival>,
    onViewOnMap: () -> Unit
) {
    val color = routeColor(route.color)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.88f)),
        border = androidx.compose.foundation.BorderStroke(2.dp, color.copy(alpha = 0.55f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Star, contentDescription = null,
                    tint = color, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Your Route", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(color),
                    contentAlignment = Alignment.Center
                ) {
                    Text(route.shortName.take(3), color = Color.White,
                        fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(route.longName, style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold)
                    arrivals.firstOrNull()?.let { arrival ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (arrival.isRealtime) {
                                Text("●", color = CountdownGreen,
                                    style = MaterialTheme.typography.labelSmall)
                                Spacer(Modifier.width(3.dp))
                                Text("Live", color = CountdownGreen,
                                    style = MaterialTheme.typography.labelSmall)
                                Spacer(Modifier.width(6.dp))
                            }
                            CountdownChip(
                                arrivalMs = arrival.displayArrivalMs,
                                isRealtime = arrival.isRealtime
                            )
                        }
                    } ?: Text("No upcoming buses",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onViewOnMap,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = color)
            ) {
                Icon(Icons.Filled.Map, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("View on Map", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToRemoveFavouriteCard(
    data: FavouriteStopData,
    onRemove: () -> Unit,
    onViewOnMap: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) { onRemove(); true } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFBA1A1A).copy(alpha = 0.88f))
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove",
                        tint = Color.White, modifier = Modifier.size(22.dp))
                    Text("Remove", color = Color.White,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Stop header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Place, contentDescription = null,
                        tint = BtBlue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(data.stop.name, style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = Color.Gray.copy(alpha = 0.12f))
                Spacer(Modifier.height(8.dp))

                // Top 2 arrivals
                if (data.arrivals.isEmpty()) {
                    Text("No upcoming buses",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    data.arrivals.forEach { arrival ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(routeColor(arrival.routeColor)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(arrival.routeShortName.take(3), color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(arrival.headsign.ifBlank { "Route ${arrival.routeShortName}" },
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f))
                            CountdownChip(arrivalMs = arrival.displayArrivalMs,
                                isRealtime = arrival.isRealtime)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onViewOnMap,
                    modifier = Modifier.align(Alignment.End),
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                ) {
                    Text("View on Map →",
                        style = MaterialTheme.typography.labelMedium,
                        color = BtBlue)
                }
            }
        }
    }
}
```

- [ ] **Step 3: Build**

```bash
./gradlew assembleDebug 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Visual check**
Install on device. Verify:
- No TopAppBar — just "Your saved stops" floating title
- If favourite route is set: pinned route card appears at top with colored border
- Each saved stop is a glass card with top 2 arrivals
- Swiping a card left reveals red "Remove" background
- Empty state shows star icon + "Search Stops" button

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luddy/bloomington_transit/ui/screens/favourites/FavouritesViewModel.kt
git add app/src/main/java/com/luddy/bloomington_transit/ui/screens/favourites/FavouritesScreen.kt
git commit -m "feat: redesign Favourites screen with glass cards, pinned route, swipe-to-remove"
```

---

## Task 10: Update CLAUDE.md Files

**Files:**
- Modify: `app/src/main/java/com/luddy/bloomington_transit/ui/CLAUDE.md`
- Modify: `app/src/main/java/com/luddy/bloomington_transit/data/CLAUDE.md`

- [ ] **Step 1: Update ui/CLAUDE.md** — add new components section

Add to the `## Structure` section:
```
- `theme/GlassComponents.kt` — GlassCard composable, TimeGradientBackground, timeGreeting()
- `components/HomeCards.kt` — CountdownHeroCard (Card 1), NearestStopCard (Card 2), ContextCard (Card 3), RoutePickerPromptCard
```

Add new section:
```
## Glass Design System
- `GlassCard` in theme/GlassComponents.kt — White 82% alpha + white border + 20dp radius. Use for ALL content cards across every screen.
- `TimeGradientBackground` in theme/GlassComponents.kt — wraps BtApp root, provides time-shifting gradient. Screens must have transparent backgrounds (no solid backgrounds on Column/Box wrappers).
- `timeGreeting()` in theme/GlassComponents.kt — returns time-aware greeting string.
- No TopAppBar on any screen. All screens use floating title Text on the gradient canvas.

## Adaptive Home Cards
Card 1 (CountdownHeroCard): favourite route + live countdown. Shows RoutePickerPromptCard if no favourite set.
Card 2 (NearestStopCard): nearest stop arrivals. Only shown when location available (HomeViewModel.updateLocation called).
Card 3 (ContextCard): shows service alert OR onboarding prompt. Collapses if neither applies.
Reordering: if isNearSavedStop=true, Card 2 promotes above Card 1 (handled in HomeScreen item ordering).
```

- [ ] **Step 2: Update data/CLAUDE.md** — document recent stops

Add to the `## Subfolders` section:
```
- `local/UserPreferencesDataStore.kt` — also stores recentStopIds (comma-separated string, max 5, LIFO order)
```

Add new section:
```
## Recent Stops
recentStopIds stored as `stringPreferencesKey("recent_stop_ids")` — comma-separated, newest first, max 5.
Added via addRecentStop(stopId) called in ScheduleViewModel.selectStop().
Exposed via TransitRepository.getRecentStopIds(): Flow<List<String>>.
```

- [ ] **Step 3: Commit**

```bash
git add "app/src/main/java/com/luddy/bloomington_transit/ui/CLAUDE.md"
git add "app/src/main/java/com/luddy/bloomington_transit/data/CLAUDE.md"
git commit -m "docs: update CLAUDE.md files with glass design system and recent stops"
```

---

## Final Verification

- [ ] **Full clean build**

```bash
./gradlew clean assembleDebug 2>&1 | tail -30
```
Expected: `BUILD SUCCESSFUL`

- [ ] **End-to-end visual walkthrough on device**

Install APK:
```bash
./gradlew installDebug
```

Check each screen:
1. **Home** — gradient background visible, floating greeting, glass cards, live countdown ticking
2. **Map** — glass filter pill at top, persistent mini panel at bottom always visible, pill bus markers, white-ring stop dots at zoom 13f+
3. **Schedule** — glass search bar, recently viewed chips after first search, arrivals in single glass card, live pulse dots
4. **Favourites** — glass stop cards, swipe left to remove, pinned route card (if route set)
5. **Nav bar** — frosted white, BtBlue active indicators, gradient visible at edge

- [ ] **Commit final state**

```bash
git add -A
git commit -m "feat: complete light glassmorphism + adaptive context UI redesign"
```

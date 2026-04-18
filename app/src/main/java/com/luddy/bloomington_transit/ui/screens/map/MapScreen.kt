package com.luddy.bloomington_transit.ui.screens.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.maps.android.compose.*
import com.luddy.bloomington_transit.domain.model.Arrival
import com.luddy.bloomington_transit.domain.model.Bus
import com.luddy.bloomington_transit.domain.model.Stop
import com.luddy.bloomington_transit.ui.components.ArrivalRow
import com.luddy.bloomington_transit.ui.components.CountdownChip
import com.luddy.bloomington_transit.ui.theme.BtBlue
import com.luddy.bloomington_transit.ui.theme.routeColor
import kotlinx.coroutines.tasks.await
import kotlin.math.*

private val BLOOMINGTON_CENTER = LatLng(39.1653, -86.5264)

private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val R = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return R * 2 * atan2(sqrt(a), sqrt(1.0 - a))
}

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

    // ── CP3: Location permission + fetch ─────────────────────────────────────
    val focusManager = LocalFocusManager.current

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasLocationPermission = granted }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            try {
                // lastLocation can be null if GPS hasn't been used recently; fall back to getCurrentLocation
                var loc = fusedLocationClient.lastLocation.await()
                if (loc == null) {
                    loc = fusedLocationClient.getCurrentLocation(
                        com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY, null
                    ).await()
                }
                loc?.let { viewModel.updateUserLocation(it.latitude, it.longitude) }
            } catch (_: Exception) {}
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // Clear keyboard focus when destination is confirmed — lets user re-tap the search bar cleanly
    LaunchedEffect(uiState.destinationLatLng) {
        if (uiState.destinationLatLng != null) {
            focusManager.clearFocus()
        }
    }

    // Apply deep-link initial route only once
    LaunchedEffect(initialRouteId, uiState.routes) {
        if (initialRouteId != null && uiState.routes.isNotEmpty()) {
            viewModel.selectOnlyRoute(initialRouteId)
        }
    }

    // Animate camera to selected bus
    LaunchedEffect(uiState.selectedBus) {
        uiState.selectedBus?.let { bus ->
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(bus.lat, bus.lon), 15f)
            )
        }
    }

    // Animate camera to destination when route is suggested
    LaunchedEffect(uiState.destinationLatLng, uiState.suggestedRouteIds) {
        if (uiState.destinationLatLng != null && uiState.suggestedRouteIds.isNotEmpty()) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(uiState.destinationLatLng!!, 13f)
            )
        }
    }

    // Closest bus on the FIRST suggested route (nearest boarding stop) — gets the info box
    val closestSuggestedBusId = remember(uiState.buses, uiState.suggestedRouteIds, uiState.boardingStop) {
        val routeId = uiState.suggestedRouteIds.firstOrNull() ?: return@remember null
        val boardingStop = uiState.boardingStop ?: return@remember null
        uiState.buses
            .filter { it.routeId == routeId }
            .minByOrNull { bus -> haversineMeters(bus.lat, bus.lon, boardingStop.lat, boardingStop.lon) }
            ?.vehicleId
    }

    // Feature 2: stops within 0.5 mile of user (only computed when userLocation changes)
    val nearbyStops = remember(uiState.stops, uiState.userLocation) {
        val loc = uiState.userLocation ?: return@remember emptyList<com.luddy.bloomington_transit.domain.model.Stop>()
        uiState.stops
            .filter { stop -> haversineMeters(stop.lat, stop.lon, loc.latitude, loc.longitude) <= 804.67 }
            .take(150)
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Full-screen Google Map ─────────────────────────────────────────────
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(myLocationButtonEnabled = hasLocationPermission),
            properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
            onMapClick = { focusManager.clearFocus(); viewModel.onSearchFocusChanged(false) }
        ) {
            // Route polylines
            uiState.selectedRouteIds.forEach { routeId ->
                val segments = uiState.shapesByRoute[routeId] ?: return@forEach
                val route = uiState.routes.find { it.id == routeId }
                val color = route?.color?.let { routeColor(it) } ?: BtBlue
                val isHighlighted = routeId in uiState.suggestedRouteIds
                segments.forEach { points ->
                    if (points.size >= 2) {
                        Polyline(
                            points = points,
                            color = color,
                            width = if (isHighlighted) 14f else 10f
                        )
                    }
                }
            }

            // Bus markers — suggested route gets the info box (CP5)
            uiState.buses
                .filter { it.routeId in uiState.selectedRouteIds }
                .forEach { bus ->
                    val route = uiState.routes.find { it.id == bus.routeId }
                    val color = route?.color?.let { routeColor(it) } ?: BtBlue
                    val isTracked = bus.vehicleId in uiState.trackedBusIds
                    val isClosest = bus.vehicleId == closestSuggestedBusId

                    MarkerComposable(
                        state = MarkerState(position = LatLng(bus.lat, bus.lon)),
                        title = "Route ${route?.shortName ?: bus.routeId}",
                        snippet = bus.label,
                        onClick = { viewModel.selectBus(bus); false }
                    ) {
                        if (isClosest) {
                            // CP5: Closest bus on suggested route gets the ETA info box
                            SuggestedBusMarker(
                                routeShortName = route?.shortName ?: "?",
                                color = color,
                                isTracked = isTracked,
                                boardingArrival = uiState.boardingArrivals.firstOrNull(),
                                alightingArrival = uiState.alightingArrivals.firstOrNull()
                            )
                        } else {
                            BusMarker(
                                routeShortName = route?.shortName ?: "?",
                                color = color,
                                isTracked = isTracked
                            )
                        }
                    }
                }

            // Stop markers — only stops within 0.5 mile of user, visible from zoom 12f
            if (cameraPositionState.position.zoom >= 12f) {
                nearbyStops.forEach { stop ->
                    MarkerComposable(
                        state = MarkerState(position = LatLng(stop.lat, stop.lon)),
                        title = stop.name,
                        onClick = { viewModel.selectStop(stop); false }
                    ) {
                        StopMarker()
                    }
                }
            }

            // Destination pin marker (CP3)
            uiState.destinationLatLng?.let { dest ->
                MarkerComposable(
                    state = MarkerState(position = dest),
                    title = uiState.destinationName ?: "Destination"
                ) {
                    DestinationPin()
                }
            }
        }

        // ── Loading overlay ───────────────────────────────────────────────────
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

        // ── Top section: search bar + dropdown + filter chips ─────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, start = 12.dp, end = 12.dp)
        ) {
            // CP2: Places search bar
            PlacesSearchBar(
                query = uiState.searchQuery,
                isLoading = uiState.isSearchLoading || uiState.isRoutingLoading,
                hasDestination = uiState.destinationLatLng != null,
                onQueryChange = { viewModel.searchPlaces(it) },
                onFocusChange = { focused ->
                    // If user re-taps while a destination is active, clear it for a fresh search
                    if (focused && uiState.destinationLatLng != null) {
                        viewModel.clearSearch()
                    }
                    viewModel.onSearchFocusChanged(focused)
                },
                onClear = { viewModel.clearSearch() }
            )

            // Autocomplete dropdown (CP2)
            if (uiState.isSearchFocused && uiState.placeSuggestions.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                AutocompleteDropdown(
                    suggestions = uiState.placeSuggestions,
                    onSelect = { viewModel.selectPlace(it) }
                )
            }

            // Route filter chips — hidden when routing is active or search is focused
            if (uiState.suggestedRouteIds.isEmpty() && uiState.destinationLatLng == null && !uiState.isSearchFocused) {
                Spacer(Modifier.height(8.dp))
                RouteFilterBar(
                    routes = uiState.routes,
                    selectedRouteIds = uiState.selectedRouteIds,
                    onToggleAll = { viewModel.toggleAllRoutes() },
                    onToggleRoute = { viewModel.toggleRoute(it) }
                )
            }
        }

        // ── Bottom panel ──────────────────────────────────────────────────────
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.88f)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.60f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column {
                // Drag handle
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
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
                    uiState.isRoutingLoading -> RoutingLoadingPanel()
                    uiState.suggestedRouteIds.isNotEmpty() -> {
                        val firstRouteId = uiState.suggestedRouteIds.first()
                        val firstRoute   = uiState.routes.find { it.id == firstRouteId }
                        RouteSuggestionPanel(
                            firstRoute       = firstRoute,
                            isTransfer       = uiState.isTransferRoute,
                            transferRoute    = uiState.transferRoute,
                            destinationName  = uiState.destinationName ?: "",
                            boardingStop     = uiState.boardingStop,
                            transferStop     = uiState.transferStop,
                            alightingStop    = uiState.alightingStop,
                            walkInMeters     = uiState.walkInMeters,
                            walkOutMeters    = uiState.walkOutMeters,
                            boardingArrival  = uiState.boardingArrivals.firstOrNull(),
                            transferArrival  = uiState.transferArrivals.firstOrNull(),
                            alightingArrival = uiState.alightingArrivals.firstOrNull(),
                            onDismiss        = { viewModel.clearSearch() }
                        )
                    }
                    uiState.routingError != null -> RoutingErrorPanel(
                        message = uiState.routingError!!,
                        onDismiss = { viewModel.clearSearch() }
                    )
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
                            Icon(Icons.Filled.Search, contentDescription = null, tint = BtBlue, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (uiState.selectedRouteIds.isEmpty())
                                    "Search a destination or tap a route above"
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

// ── CP2: Places search bar ─────────────────────────────────────────────────────

@Composable
private fun PlacesSearchBar(
    query: String,
    isLoading: Boolean,
    hasDestination: Boolean,
    onQueryChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.96f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.60f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (hasDestination) Icons.Filled.Place else Icons.Filled.Search,
                contentDescription = null,
                tint = if (hasDestination) BtBlue else Color.Gray,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.Black),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { onFocusChange(it.isFocused) },
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                "Search a destination…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray.copy(alpha = 0.6f)
                            )
                        }
                        inner()
                    }
                }
            )
            Spacer(Modifier.width(8.dp))
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = BtBlue)
            } else if (query.isNotEmpty()) {
                IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ── CP2: Autocomplete dropdown ────────────────────────────────────────────────

@Composable
private fun AutocompleteDropdown(
    suggestions: List<PlaceSuggestion>,
    onSelect: (PlaceSuggestion) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.97f)),
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Column {
            suggestions.forEachIndexed { index, suggestion ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(suggestion) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Place,
                        contentDescription = null,
                        tint = BtBlue.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = suggestion.primaryText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        if (suggestion.secondaryText.isNotBlank()) {
                            Text(
                                text = suggestion.secondaryText,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray,
                                maxLines = 1
                            )
                        }
                    }
                }
                if (index < suggestions.lastIndex) {
                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.08f))
                }
            }
        }
    }
}

// ── Route filter chips (unchanged behaviour) ──────────────────────────────────

@Composable
private fun RouteFilterBar(
    routes: List<com.luddy.bloomington_transit.domain.model.Route>,
    selectedRouteIds: Set<String>,
    onToggleAll: () -> Unit,
    onToggleRoute: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.88f)),
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
            val allSelected = selectedRouteIds.size == routes.size
            FilterChip(
                selected = allSelected,
                onClick = onToggleAll,
                label = { Text("All", fontWeight = FontWeight.Bold) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = BtBlue,
                    selectedLabelColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            )
            routes.forEach { route ->
                val selected = route.id in selectedRouteIds
                val color = routeColor(route.color)
                FilterChip(
                    selected = selected,
                    onClick = { onToggleRoute(route.id) },
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
}

// ── Route suggestion panel: direct + transfer, with walking distance ──────────

@Composable
private fun RouteSuggestionPanel(
    firstRoute: com.luddy.bloomington_transit.domain.model.Route?,
    isTransfer: Boolean,
    transferRoute: com.luddy.bloomington_transit.domain.model.Route?,
    destinationName: String,
    boardingStop: Stop?,
    transferStop: Stop?,
    alightingStop: Stop?,
    walkInMeters: Double?,
    walkOutMeters: Double?,
    boardingArrival: Arrival?,
    transferArrival: Arrival?,
    alightingArrival: Arrival?,
    onDismiss: () -> Unit
) {
    val firstColor  = firstRoute?.color?.let { routeColor(it) } ?: BtBlue
    val secondColor = transferRoute?.color?.let { routeColor(it) } ?: BtBlue

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        // Header row
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Route badge(s)
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(firstColor),
                contentAlignment = Alignment.Center
            ) { Text(firstRoute?.shortName?.take(3) ?: "?", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium) }
            if (isTransfer && transferRoute != null) {
                Icon(Icons.Filled.ArrowForward, null, tint = Color.Gray, modifier = Modifier.size(14.dp).padding(horizontal = 2.dp))
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(secondColor),
                    contentAlignment = Alignment.Center
                ) { Text(transferRoute.shortName.take(3), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium) }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isTransfer && transferRoute != null) "${firstRoute?.shortName} → ${transferRoute.shortName}"
                    else firstRoute?.longName ?: "Route",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Place, null, tint = BtBlue, modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(destinationName, style = MaterialTheme.typography.labelSmall, color = Color.Gray, maxLines = 1)
                }
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Dismiss") }
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = Color.Gray.copy(alpha = 0.12f))
        Spacer(Modifier.height(8.dp))

        // Walk to stop
        if (walkInMeters != null) {
            WalkRow(meters = walkInMeters, label = "Walk to stop")
            StepConnector(color = firstColor)
        }

        // Board bus
        RouteStepRow(
            icon = Icons.Filled.DirectionsBus, iconTint = firstColor,
            label = "Board ${firstRoute?.shortName ?: "bus"}",
            stopName = boardingStop?.name ?: "—",
            arrival = boardingArrival
        )

        if (isTransfer && transferStop != null && transferRoute != null) {
            StepConnector(color = firstColor)
            // Transfer step
            RouteStepRow(
                icon = Icons.Filled.SyncAlt, iconTint = secondColor,
                label = "Transfer → ${transferRoute.shortName}",
                stopName = transferStop.name,
                arrival = transferArrival
            )
            StepConnector(color = secondColor)
            // Destination stop on route 2
            RouteStepRow(
                icon = Icons.Filled.Flag, iconTint = secondColor,
                label = "Get off",
                stopName = alightingStop?.name ?: "—",
                arrival = alightingArrival
            )
        } else {
            StepConnector(color = firstColor)
            RouteStepRow(
                icon = Icons.Filled.Flag, iconTint = firstColor,
                label = "Get off",
                stopName = alightingStop?.name ?: "—",
                arrival = alightingArrival
            )
        }

        // Walk to destination
        if (walkOutMeters != null) {
            StepConnector(color = Color.Gray.copy(alpha = 0.4f))
            WalkRow(meters = walkOutMeters, label = "Walk to destination")
        }
    }
}

@Composable
private fun WalkRow(meters: Double, label: String) {
    val mins = metersToWalkMinutes(meters)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Icon(Icons.Filled.DirectionsWalk, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text("$label · ~${mins} min (${meters.toInt()} m)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}

@Composable
private fun StepConnector(color: Color) {
    Box(modifier = Modifier.padding(start = 7.dp, top = 2.dp, bottom = 2.dp).size(width = 2.dp, height = 12.dp).background(color.copy(alpha = 0.35f)))
}

@Composable
private fun RouteStepRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    label: String,
    stopName: String,
    arrival: Arrival?
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text(stopName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        if (arrival != null) {
            CountdownChip(arrivalMs = arrival.displayArrivalMs, isRealtime = arrival.isRealtime)
        } else {
            Text("—", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

@Composable
private fun RoutingLoadingPanel() {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = BtBlue)
        Spacer(Modifier.width(12.dp))
        Text("Finding the best route…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RoutingErrorPanel(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
        }
    }
}

// ── CP5: Bus marker with ETA info box ─────────────────────────────────────────

@Composable
private fun SuggestedBusMarker(
    routeShortName: String,
    color: Color,
    isTracked: Boolean,
    boardingArrival: Arrival?,
    alightingArrival: Arrival?
) {
    // Ticker so the countdown stays live inside the marker bitmap
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000L)
            currentTime = System.currentTimeMillis()
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BusMarker(routeShortName = routeShortName, color = color, isTracked = isTracked)
        Spacer(Modifier.height(3.dp))
        // CP5: Info box below the bus icon
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
            elevation = CardDefaults.cardElevation(3.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                val boardMin = boardingArrival?.let {
                    maxOf(0L, (it.displayArrivalMs - currentTime) / 60_000L)
                }
                val alightMin = alightingArrival?.let {
                    maxOf(0L, (it.displayArrivalMs - currentTime) / 60_000L)
                }
                if (boardMin != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.DirectionsBus, null, tint = color, modifier = Modifier.size(10.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(
                            "~${boardMin}min to your stop",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
                if (alightMin != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Flag, null, tint = Color.Gray, modifier = Modifier.size(10.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(
                            "~${alightMin}min to dest",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                }
                if (boardMin == null && alightMin == null) {
                    Text("No schedule data", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
        }
    }
}

// ── Destination pin marker (CP3) ──────────────────────────────────────────────

@Composable
private fun DestinationPin() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(BtBlue),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Place, contentDescription = "Destination", tint = Color.White, modifier = Modifier.size(20.dp))
        }
        // Pin tail
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 8.dp)
                .background(BtBlue)
        )
    }
}

// ── Existing marker composables (unchanged) ───────────────────────────────────

@Composable
private fun BusMarker(routeShortName: String, color: Color, isTracked: Boolean) {
    Box(contentAlignment = Alignment.Center) {
        if (isTracked) {
            val infiniteTransition = rememberInfiniteTransition(label = "tracked_pulse")
            val scale by infiniteTransition.animateFloat(
                initialValue = 1f, targetValue = 1.5f,
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
            Text(routeShortName.take(3), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun StopMarker() {
    Box(
        modifier = Modifier.size(16.dp).clip(CircleShape).background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(BtBlue.copy(alpha = 0.75f)))
    }
}

// ── Existing detail panels (unchanged) ───────────────────────────────────────

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
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(routeColor(r.color)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(r.shortName, color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(r.longName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Vehicle ${bus.vehicleId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Dismiss") }
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
            Icon(if (isTracked) Icons.Filled.NotificationsOff else Icons.Filled.NotificationsActive, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (isTracked) "Stop Tracking" else "Track This Bus")
        }
    }
}

@Composable
private fun StopDetailPanel(
    stop: Stop,
    arrivals: List<Arrival>,
    isLoading: Boolean,
    onDismiss: () -> Unit
) {
    Column(modifier = Modifier.heightIn(max = 360.dp)) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(BtBlue),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.DirectionsBus, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stop.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("Bus Stop · Tap a bus for its full route", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Dismiss") }
        }
        HorizontalDivider(color = Color.Gray.copy(alpha = 0.12f))
        if (isLoading) {
            Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        } else if (arrivals.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                Text("No buses in the next 2 hours", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(horizontal = 16.dp, vertical = 5.dp)
            ) {
                Text("Route", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(44.dp))
                Spacer(Modifier.width(10.dp))
                Text("Direction", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text("Arrives", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn {
                items(arrivals.take(6)) { arrival ->
                    DepartureBoardRow(arrival = arrival)
                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.07f))
                }
            }
        }
    }
}

@Composable
private fun DepartureBoardRow(arrival: Arrival) {
    val color = routeColor(arrival.routeColor)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(color),
            contentAlignment = Alignment.Center
        ) {
            Text(arrival.routeShortName.take(3), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(arrival.headsign.ifBlank { "Route ${arrival.routeShortName}" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (arrival.isRealtime) {
                    val infiniteTransition = rememberInfiniteTransition(label = "live")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f, targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                        label = "live_alpha"
                    )
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF2ECC71).copy(alpha = alpha)))
                    Spacer(Modifier.width(4.dp))
                    Text("Live", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2ECC71), fontWeight = FontWeight.Medium)
                } else {
                    Text("Scheduled", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        CountdownChip(arrivalMs = arrival.displayArrivalMs, isRealtime = arrival.isRealtime)
    }
}

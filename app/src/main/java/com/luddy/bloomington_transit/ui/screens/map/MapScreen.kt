package com.luddy.bloomington_transit.ui.screens.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.*
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
import com.luddy.bloomington_transit.ui.theme.routeColor

// Bloomington, IN center
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

    // Apply initial route filter when routes load
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

    // Follow selected bus on map
    LaunchedEffect(uiState.selectedBus) {
        uiState.selectedBus?.let { bus ->
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(bus.lat, bus.lon), 15f)
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // Google Map
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(myLocationButtonEnabled = hasLocationPermission),
            properties = MapProperties(isMyLocationEnabled = hasLocationPermission)
        ) {
            // Route polylines — draw each segment (shape_id) separately
            uiState.selectedRouteIds.forEach { routeId ->
                val segments = uiState.shapesByRoute[routeId] ?: return@forEach
                val route = uiState.routes.find { it.id == routeId }
                val color = route?.color?.let { routeColor(it) } ?: Color(0xFF0057A8)
                segments.forEach { points ->
                    if (points.size >= 2) {
                        Polyline(points = points, color = color, width = 8f)
                    }
                }
            }

            // Bus markers
            uiState.buses
                .filter { it.routeId in uiState.selectedRouteIds }
                .forEach { bus ->
                    val route = uiState.routes.find { it.id == bus.routeId }
                    val color = route?.color?.let { routeColor(it) } ?: Color(0xFF0057A8)
                    val isTracked = bus.vehicleId in uiState.trackedBusIds

                    MarkerComposable(
                        state = MarkerState(position = LatLng(bus.lat, bus.lon)),
                        title = "Route ${route?.shortName ?: bus.routeId}",
                        snippet = bus.label,
                        onClick = {
                            viewModel.selectBus(bus)
                            false
                        }
                    ) {
                        BusMarker(
                            routeShortName = route?.shortName ?: "?",
                            color = color,
                            isTracked = isTracked
                        )
                    }
                }

            // Stop markers — always visible when zoomed in (regardless of route selection)
            if (cameraPositionState.position.zoom > 14f) {
                uiState.stops
                    .take(80)
                    .forEach { stop ->
                        MarkerComposable(
                            state = MarkerState(position = LatLng(stop.lat, stop.lon)),
                            title = stop.name,
                            onClick = {
                                viewModel.selectStop(stop)
                                false
                            }
                        ) {
                            StopMarker()
                        }
                    }
            }
        }

        // Loading overlay while GTFS initializes
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

        // Route filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, start = 8.dp, end = 8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // "All" toggle chip
            val allSelected = uiState.selectedRouteIds.size == uiState.routes.size
            FilterChip(
                selected = allSelected,
                onClick = { viewModel.toggleAllRoutes() },
                label = { Text("All", fontWeight = FontWeight.Bold) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
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

        // Empty state hint when no routes selected
        if (uiState.selectedRouteIds.isEmpty() && !uiState.isInitializing) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                    ),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Filled.DirectionsBus, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Tap a route chip above to see buses & routes",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }

        // Bottom sheet: bus detail or stop detail
        AnimatedVisibility(
            visible = uiState.selectedBus != null || uiState.selectedStop != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            when {
                uiState.selectedBus != null -> BusDetailSheet(
                    bus = uiState.selectedBus!!,
                    route = uiState.routes.find { it.id == uiState.selectedBus!!.routeId },
                    isTracked = uiState.selectedBus!!.vehicleId in uiState.trackedBusIds,
                    onTrackToggle = { bus ->
                        if (bus.vehicleId in uiState.trackedBusIds)
                            viewModel.untrackBus(bus.vehicleId)
                        else viewModel.trackBus(bus.vehicleId)
                    },
                    onDismiss = { viewModel.dismissBottomSheet() }
                )
                uiState.selectedStop != null -> StopDetailSheet(
                    stop = uiState.selectedStop!!,
                    arrivals = uiState.stopArrivals,
                    isLoading = uiState.isLoadingArrivals,
                    onDismiss = { viewModel.dismissBottomSheet() }
                )
            }
        }
    }
}

@Composable
private fun BusMarker(
    routeShortName: String,
    color: Color,
    isTracked: Boolean
) {
    Box(contentAlignment = Alignment.Center) {
        if (isTracked) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.25f))
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = routeShortName.take(3),
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
            .size(10.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BusDetailSheet(
    bus: Bus,
    route: com.luddy.bloomington_transit.domain.model.Route?,
    isTracked: Boolean,
    onTrackToggle: (Bus) -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(8.dp)
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
}

@Composable
private fun StopDetailSheet(
    stop: Stop,
    arrivals: List<com.luddy.bloomington_transit.domain.model.Arrival>,
    isLoading: Boolean,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 350.dp)
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stop.name, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Dismiss")
                }
            }
            Divider()
            if (isLoading) {
                Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            } else if (arrivals.isEmpty()) {
                Text("No upcoming arrivals", modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn {
                    items(arrivals.take(5)) { arrival -> ArrivalRow(arrival = arrival) }
                }
            }
        }
    }
}

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

        // ── Persistent glass mini panel (bottom — always visible) ────
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
        // Pill-shaped marker
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
                    Text(r.longName, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
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
            Text(stop.name, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss")
            }
        }
        HorizontalDivider(color = Color.Gray.copy(alpha = 0.12f))
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

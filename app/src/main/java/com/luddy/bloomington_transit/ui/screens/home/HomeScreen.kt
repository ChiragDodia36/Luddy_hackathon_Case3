package com.luddy.bloomington_transit.ui.screens.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.android.gms.location.LocationServices
import com.luddy.bloomington_transit.domain.model.Route
import com.luddy.bloomington_transit.ui.components.CountdownChip
import com.luddy.bloomington_transit.ui.theme.BtBlue
import com.luddy.bloomington_transit.ui.theme.CountdownGreen
import com.luddy.bloomington_transit.ui.theme.GlassCard
import com.luddy.bloomington_transit.ui.theme.routeColor
import com.luddy.bloomington_transit.ui.theme.timeGreeting

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showAddPinSheet by remember { mutableStateOf(false) }

    // Fetch last-known location once on launch
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

    if (showAddPinSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddPinSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    "Pin a Route",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Shows nearest stop + live times for that route on your home screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                LazyColumn(
                    contentPadding = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(uiState.routes, key = { it.id }) { route ->
                        RoutePickerRow(
                            route = route,
                            isPinned = route.id in uiState.pinnedRouteIds,
                            onToggle = {
                                if (route.id in uiState.pinnedRouteIds) {
                                    viewModel.removePin(route.id)
                                } else {
                                    viewModel.addPin(route.id)
                                }
                            }
                        )
                        HorizontalDivider(color = Color.Gray.copy(alpha = 0.08f))
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(20.dp))

        // Header row
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
            IconButton(onClick = { /* notifications */ }) {
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
                    Text("Loading transit data…", style = MaterialTheme.typography.bodyMedium)
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

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Card 0 — Live BT-vs-us scoreboard (pitch headline, fetched once from /stats).
            // Quietly absent when the backend is unreachable — rest of Home still loads.
            uiState.stats?.let { stats ->
                val bt = stats.btHeadlineMaeS
                val us = stats.a1CvHeadlineMaeS
                val pct = stats.a1CvImprovementPct
                val routes = stats.routesWithIntercept
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Live: BT vs Us",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        "${us?.let { "%.0f".format(it) } ?: "—"} s",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "vs BT ${"%.0f".format(bt)} s",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(bottom = 3.dp),
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    pct?.let { p ->
                                        if (p > 0) "+%.1f%% better".format(p)
                                        else "%.1f%% worse".format(p)
                                    } ?: "—",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if ((pct ?: 0.0) > 0.0) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.error,
                                )
                                Text(
                                    "$routes routes corrected",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.pinnedRoutes.isNotEmpty()) {
                items(uiState.pinnedRoutes, key = { it.route.id }) { pinData ->
                    PinnedRouteCard(
                        data = pinData,
                        hasLocation = uiState.hasLocation,
                        onRemove = { viewModel.removePin(pinData.route.id) }
                    )
                }
            }

            item {
                if (uiState.pinnedRoutes.isEmpty()) {
                    EmptyPinsCard(onAddPin = { showAddPinSheet = true })
                } else {
                    OutlinedButton(
                        onClick = { showAddPinSheet = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White.copy(alpha = 0.55f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, Color.White.copy(alpha = 0.60f)
                        )
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add route pin", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (uiState.serviceAlerts.isNotEmpty()) {
                item {
                    ServiceAlertCard(alert = uiState.serviceAlerts.first())
                }
            }
        }
    }
}


@Composable
private fun RoutePickerRow(route: Route, isPinned: Boolean, onToggle: () -> Unit) {
    val color = routeColor(route.color)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onToggle() }
            .padding(vertical = 10.dp, horizontal = 8.dp),
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
            Text(
                route.longName.ifBlank { "Route ${route.shortName}" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                "Route ${route.shortName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isPinned) {
            Icon(Icons.Filled.PushPin, contentDescription = "Pinned", tint = color, modifier = Modifier.size(18.dp))
        } else {
            Icon(Icons.Filled.PushPin, contentDescription = "Not pinned",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.size(18.dp))
        }
    }
}


@Composable
private fun PinnedRouteCard(
    data: PinnedRouteData,
    hasLocation: Boolean,
    onRemove: () -> Unit
) {
    val routeCol = routeColor(data.route.color)

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: route badge + name + remove button
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(routeCol),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        data.route.shortName.take(3),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        data.route.longName.ifBlank { "Route ${data.route.shortName}" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    // Nearest stop name under route title
                    if (data.nearestStop != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Place,
                                contentDescription = null,
                                tint = BtBlue.copy(alpha = 0.7f),
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                data.nearestStop.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (!hasLocation) {
                        Text(
                            "Enable location for nearest stop",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Remove pin",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Color.Gray.copy(alpha = 0.13f))
            Spacer(Modifier.height(8.dp))

            // Arrival rows
            if (!hasLocation) {
                Text(
                    "Enable location to see live times",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (data.arrivals.isEmpty()) {
                Text(
                    "No upcoming buses",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                data.arrivals.forEach { arrival ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Direction arrow + headsign
                        Icon(
                            Icons.Filled.ArrowForward,
                            contentDescription = null,
                            tint = routeCol,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            arrival.headsign.ifBlank { "Route ${arrival.routeShortName}" },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        // Live dot
                        if (arrival.isRealtime) {
                            Text(
                                "●",
                                color = CountdownGreen,
                                style = MaterialTheme.typography.labelSmall
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        CountdownChip(
                            arrivalMs = arrival.displayArrivalMs,
                            isRealtime = arrival.isRealtime
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun EmptyPinsCard(onAddPin: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.PushPin,
                contentDescription = null,
                tint = BtBlue.copy(alpha = 0.35f),
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "No pinned routes yet",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Pin the routes you ride — see the nearest stop and live times right here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onAddPin,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BtBlue)
            ) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Pin a route")
            }
        }
    }
}


@Composable
private fun ServiceAlertCard(alert: com.luddy.bloomington_transit.domain.model.ServiceAlert) {
    val amber = com.luddy.bloomington_transit.ui.theme.CountdownAmber
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0).copy(alpha = 0.92f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, amber.copy(alpha = 0.50f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Warning, null, tint = amber, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(alert.headerText, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
                if (alert.descriptionText.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(alert.descriptionText, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

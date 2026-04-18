package com.luddy.bloomington_transit.ui.screens.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
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
import com.luddy.bloomington_transit.ui.components.BunchingBanner
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

    // Request last-known location once on launch for nearest stop
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
            IconButton(onClick = { navController.navigate(Screen.Diagnostics.route) }) {
                Icon(
                    Icons.Filled.BugReport,
                    contentDescription = "Diagnostics",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { /* future: notification settings */ }) {
                Icon(
                    Icons.Filled.Notifications,
                    contentDescription = "Notifications",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Bunching alerts (AI-driven) — quietly hidden when no events
        BunchingBanner()

        Spacer(Modifier.height(4.dp))

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

        val favouriteRoute = uiState.routes.find { it.id == uiState.favouriteRouteId }
        val isNewUser = favouriteRoute == null && uiState.nearestStop == null

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
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

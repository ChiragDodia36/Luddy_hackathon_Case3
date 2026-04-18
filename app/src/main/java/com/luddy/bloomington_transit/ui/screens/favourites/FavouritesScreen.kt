package com.luddy.bloomington_transit.ui.screens.favourites

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import com.luddy.bloomington_transit.domain.model.Arrival
import com.luddy.bloomington_transit.domain.model.Route
import com.luddy.bloomington_transit.ui.components.CountdownChip
import com.luddy.bloomington_transit.ui.theme.BtBlue
import com.luddy.bloomington_transit.ui.theme.CountdownGreen
import com.luddy.bloomington_transit.ui.theme.GlassCard
import com.luddy.bloomington_transit.ui.theme.routeColor
import kotlinx.coroutines.delay

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

        // Floating header — no TopAppBar
        Text(
            "Favourites",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(12.dp))

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
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.30f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No favourites yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Tap the star on any stop to save it here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { navController.navigate("schedule") },
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.60f)),
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
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // Pinned favourite route card (if set)
                uiState.favouriteRoute?.let { route ->
                    item(key = "pinned_route_${route.id}") {
                        PinnedRouteCard(
                            route = route,
                            arrivals = uiState.favouriteRouteArrivals
                        )
                    }
                }

                // Swipe-to-dismiss stop cards
                items(
                    items = uiState.favourites,
                    key = { it.stop.id }
                ) { data ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                viewModel.removeFavourite(data.stop.id)
                                true
                            } else false
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            val color by animateColorAsState(
                                targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart)
                                    Color(0xFFFF5252) else Color.Transparent,
                                animationSpec = tween(200),
                                label = "swipe_bg"
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(color),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Remove",
                                    tint = Color.White,
                                    modifier = Modifier.padding(end = 20.dp)
                                )
                            }
                        }
                    ) {
                        FavouriteStopCard(data = data)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Pinned route glass card with route-colored left border
// ─────────────────────────────────────────────────────────────────

@Composable
private fun PinnedRouteCard(route: Route, arrivals: List<Arrival>) {
    val routeCol = routeColor(route.color)
    val firstArrival = arrivals.firstOrNull()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.88f)),
        border = BorderStroke(1.5.dp, routeCol.copy(alpha = 0.70f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(routeCol),
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        route.longName.ifBlank { "Route ${route.shortName}" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Pinned route",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Filled.Star, contentDescription = null,
                    tint = routeCol, modifier = Modifier.size(18.dp))
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Color.Gray.copy(alpha = 0.10f))
            Spacer(Modifier.height(10.dp))

            // Next arrival countdown
            if (firstArrival != null) {
                PinnedRouteCountdown(arrival = firstArrival)
                if (arrivals.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Then: ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        CountdownChip(
                            arrivalMs = arrivals[1].displayArrivalMs,
                            isRealtime = arrivals[1].isRealtime
                        )
                    }
                }
            } else {
                Text(
                    "No upcoming buses",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PinnedRouteCountdown(arrival: Arrival) {
    var minutesLeft by remember(arrival.displayArrivalMs) {
        mutableLongStateOf(
            ((arrival.displayArrivalMs - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0L)
        )
    }
    LaunchedEffect(arrival.displayArrivalMs) {
        while (true) {
            minutesLeft = ((arrival.displayArrivalMs - System.currentTimeMillis()) / 60_000L)
                .coerceAtLeast(0L)
            delay(1000L)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "$minutesLeft",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = BtBlue
        )
        Spacer(Modifier.width(6.dp))
        Column {
            Text("min", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (arrival.isRealtime) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(6.dp).clip(androidx.compose.foundation.shape.CircleShape)
                            .background(CountdownGreen)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text("Live", style = MaterialTheme.typography.labelSmall, color = CountdownGreen)
                }
            } else {
                Text("scheduled", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            arrival.headsign.ifBlank { "→ ${arrival.routeShortName}" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─────────────────────────────────────────────────────────────────
// Individual favourite stop glass card
// ─────────────────────────────────────────────────────────────────

@Composable
private fun FavouriteStopCard(data: FavouriteStopData) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Stop name row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Place,
                    contentDescription = null,
                    tint = BtBlue,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    data.stop.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Color.Gray.copy(alpha = 0.10f))
            Spacer(Modifier.height(8.dp))

            // Top 2 arrivals
            if (data.arrivals.isEmpty()) {
                Text(
                    "No upcoming buses",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                data.arrivals.forEachIndexed { idx, arrival ->
                    FavouriteArrivalRow(arrival = arrival)
                    if (idx < data.arrivals.lastIndex) {
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Swipe hint
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.SwipeLeft,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    "Swipe to remove",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                )
            }
        }
    }
}

@Composable
private fun FavouriteArrivalRow(arrival: Arrival) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                arrival.headsign.ifBlank { "Route ${arrival.routeShortName}" },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            if (arrival.isRealtime) {
                Text("Live", style = MaterialTheme.typography.labelSmall, color = CountdownGreen)
            } else {
                Text("Scheduled", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        CountdownChip(arrivalMs = arrival.displayArrivalMs, isRealtime = arrival.isRealtime)
    }
}

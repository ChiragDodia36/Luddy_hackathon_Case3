package com.luddy.bloomington_transit.ui.screens.schedule

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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

        // Floating header — no TopAppBar
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
                        if (idx < minOf(uiState.searchResults.size, 8) - 1) {
                            HorizontalDivider(color = Color.Gray.copy(alpha = 0.10f))
                        }
                    }
                }
            }
        }

        // Recently viewed stops strip
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
            // Empty state — shown only when no search results and no recent stops
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
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.60f)),
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

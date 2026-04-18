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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────────
// Live Status Board — nearest stop + filtered arrivals
// ─────────────────────────────────────────────────────────────────

@Composable
fun LiveStatusCard(
    nearestStop: Stop?,
    arrivals: List<Arrival>,
    favouriteRouteId: String?,
    favouriteRoute: com.luddy.bloomington_transit.domain.model.Route?,
    onPinRoute: () -> Unit,
    onChangeRoute: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayedArrivals = remember(arrivals, favouriteRouteId) {
        if (favouriteRouteId != null) arrivals.filter { it.routeId == favouriteRouteId }
        else arrivals
    }

    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Place,
                    contentDescription = null,
                    tint = BtBlue,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (nearestStop != null) nearestStop.name else "Nearest Stop",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (favouriteRouteId != null) {
                    TextButton(
                        onClick = onChangeRoute,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            "Change",
                            style = MaterialTheme.typography.labelSmall,
                            color = BtBlue
                        )
                    }
                }
            }

            if (favouriteRoute != null) {
                val color = routeColor(favouriteRoute.color)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(color),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            favouriteRoute.shortName.take(3),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        favouriteRoute.longName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Color.Gray.copy(alpha = 0.15f))
            Spacer(Modifier.height(8.dp))

            if (nearestStop == null) {
                Text(
                    "Enable location to see nearby stops",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (displayedArrivals.isEmpty()) {
                Text(
                    "No upcoming buses at this stop",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                displayedArrivals.take(3).forEach { LiveStatusArrivalRow(it) }
            }

            if (favouriteRouteId == null && nearestStop != null) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = Color.Gray.copy(alpha = 0.15f))
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.DirectionsBus,
                        contentDescription = null,
                        tint = BtBlue.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Pin a route to track only its times",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = onPinRoute,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("Pin", style = MaterialTheme.typography.labelSmall, color = BtBlue)
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveStatusArrivalRow(arrival: Arrival) {
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
                color = if (arrival.isRealtime) CountdownGreen
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.width(4.dp))
            CountdownChip(arrivalMs = arrival.displayArrivalMs, isRealtime = arrival.isRealtime)
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Card 3 — Context Card (alert / onboarding)
// ─────────────────────────────────────────────────────────────────

@Composable
fun ContextCard(
    alerts: List<ServiceAlert>,
    isNewUser: Boolean,
    modifier: Modifier = Modifier
) {
    when {
        alerts.isNotEmpty() -> AlertContextCard(alert = alerts.first(), modifier = modifier)
        isNewUser           -> OnboardingContextCard(modifier = modifier)
        else                -> {} // collapse when nothing to show
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
        border = androidx.compose.foundation.BorderStroke(1.dp, CountdownAmber.copy(alpha = 0.50f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = CountdownAmber,
                modifier = Modifier.size(20.dp)
            )
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

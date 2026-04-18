package com.luddy.bloomington_transit.ui.screens.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.luddy.bloomington_transit.data.ai.dto.TripStopEtaDto
import com.luddy.bloomington_transit.ui.components.ConfidenceBadge
import java.time.OffsetDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripEtaScreen(
    navController: NavController,
    viewModel: TripEtaViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.start() }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Trip ETA propagation", fontWeight = FontWeight.Bold)
                    ui.trajectory?.let { t ->
                        Text(
                            text = "Trip ${t.tripId}  ·  Route ${t.routeId ?: "—"}" +
                                   (t.vehicleId?.let { "  ·  Bus $it" } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        )

        when {
            ui.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            ui.errorMessage != null -> Text(
                "Error: ${ui.errorMessage}",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.error,
            )
            ui.trajectory == null || ui.trajectory!!.stops.isEmpty() -> Text(
                "No ETA data for this trip.",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> {
                val stops = ui.trajectory!!.stops
                val current = ui.trajectory!!.currentStopSequence
                LazyColumn(modifier = Modifier.padding(horizontal = 12.dp)) {
                    items(stops) { s ->
                        TripStopCard(stop = s, isCurrent = s.stopSequence == current)
                        Spacer(Modifier.height(6.dp))
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun TripStopCard(stop: TripStopEtaDto, isCurrent: Boolean) {
    val now = System.currentTimeMillis() / 1000L
    val sched = runCatching { OffsetDateTime.parse(stop.scheduledArrivalUtc).toEpochSecond() }.getOrDefault(0L)
    val bt = runCatching { OffsetDateTime.parse(stop.btPredictedArrivalUtc).toEpochSecond() }.getOrDefault(0L)
    val ours = runCatching { OffsetDateTime.parse(stop.oursPredictedArrivalUtc).toEpochSecond() }.getOrDefault(0L)

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(if (isCurrent) 4.dp else 1.dp),
        colors = if (isCurrent)
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        else CardDefaults.cardColors(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCurrent) {
                        Icon(
                            Icons.Filled.DirectionsBus,
                            contentDescription = "current stop",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.height(14.dp),
                        )
                    }
                    Text(
                        "${stop.stopSequence}. ${stop.stopName ?: stop.stopId}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Sched ${fmt(sched, now)}",
                         style = MaterialTheme.typography.labelSmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("BT ${fmt(bt, now)}",
                         style = MaterialTheme.typography.labelSmall,
                         color = MaterialTheme.colorScheme.secondary)
                    Text("Ours ${fmt(ours, now)}",
                         style = MaterialTheme.typography.labelMedium,
                         fontWeight = FontWeight.Bold,
                         color = MaterialTheme.colorScheme.primary)
                }
                val corr = stop.correctionSeconds
                if (kotlin.math.abs(corr) >= 5.0) {
                    Text(
                        "Adjusted ${if (corr >= 0) "+" else ""}${corr.toInt()}s vs BT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.padding(end = 8.dp))
            ConfidenceBadge(stop.confidence)
        }
    }
}

private fun fmt(epochSec: Long, now: Long): String {
    if (epochSec <= 0) return "—"
    val d = epochSec - now
    return when {
        d <= 0 -> "now"
        d < 60 -> "${d}s"
        d < 3600 -> "${d / 60}m"
        else -> "${d / 3600}h${(d % 3600) / 60}m"
    }
}

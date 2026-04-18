package com.luddy.bloomington_transit.ui.screens.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    navController: NavController,
    viewModel: DiagnosticsViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.start() }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.BugReport, contentDescription = null,
                         tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.height(4.dp))
                    Text("  Diagnostics", fontWeight = FontWeight.Bold)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = "Model accuracy (CV)") {
                LabelValue("BT headline MAE @ 3-5 min", ui.stats?.btHeadlineMaeS?.let { "${"%.1f".format(it)} s" } ?: "—")
                LabelValue("Our A1 CV MAE @ 3-5 min", ui.stats?.a1CvHeadlineMaeS?.let { "${"%.1f".format(it)} s" } ?: "—")
                LabelValue(
                    "Improvement vs BT",
                    ui.stats?.a1CvImprovementS?.let {
                        val pct = ui.stats?.a1CvImprovementPct ?: 0.0
                        "${if (it >= 0) "+" else ""}${"%.1f".format(it)} s (${"%.1f".format(pct)}%)"
                    } ?: "—"
                )
                LabelValue("Model source", ui.health?.modelSource ?: ui.stats?.modelSource ?: "—")
                LabelValue("A1 abort flag", ui.health?.a1Abort?.toString() ?: "—")
                LabelValue("Routes with intercept", ui.stats?.routesWithIntercept?.toString() ?: "—")
            }

            SectionCard(title = "Live feed") {
                LabelValue("Live fleet size", ui.stats?.liveFleetSize?.toString() ?: "—")
                LabelValue("Stale vehicles (>90 s)", ui.stats?.liveStaleVehicleCount?.toString() ?: "—")
                LabelValue("Last local refresh", if (ui.lastUpdatedEpochMs > 0) {
                    val ageSec = (System.currentTimeMillis() - ui.lastUpdatedEpochMs) / 1000L
                    "${ageSec}s ago"
                } else "—")
                ui.errorMessage?.let {
                    Text(
                        text = "error: $it",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            SectionCard(
                title = "Vehicles (${ui.vehicles.size})",
                subtitle = "Drift = staleness − 30 s (measured median vehicle.timestamp cadence). " +
                    "Positive = vehicle is lagging BT's own publish schedule.",
            ) {
                if (ui.vehicles.isEmpty()) {
                    Text("No live vehicles right now.",
                         style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column {
                        ui.vehicles.forEach { v ->
                            // Expected cadence median = 30 s.
                            val drift = v.stalenessSeconds - 30
                            val driftText = when {
                                drift <= 0 -> "on schedule"
                                drift < 20 -> "+${drift}s drift"
                                else -> "+${drift}s drift ⚡"
                            }
                            val driftColor = when {
                                v.isStale -> MaterialTheme.colorScheme.error
                                drift > 30 -> MaterialTheme.colorScheme.error
                                drift > 10 -> Color(0xFF8E4F00)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "#${v.vehicleId}",
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "rt ${v.routeId ?: "—"} · " +
                                        (if (v.isStale) "⚠ stale " else "") +
                                        "${v.stalenessSeconds}s old · $driftText",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = driftColor,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            subtitle?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Divider()
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

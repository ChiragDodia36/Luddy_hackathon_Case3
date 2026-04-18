package com.luddy.bloomington_transit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.luddy.bloomington_transit.data.ai.dto.BunchingEventDto

@Composable
fun BunchingBanner(
    modifier: Modifier = Modifier,
    viewModel: BunchingBannerViewModel = hiltViewModel(),
) {
    val events by viewModel.events.collectAsState()
    LaunchedEffect(Unit) { viewModel.start() }

    if (events.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    val critical = events.any { it.severity == "critical" }
    val bg = if (critical) Color(0xFFFFEAEA) else Color(0xFFFFF4E0)
    val fg = if (critical) Color(0xFFB71C1C) else Color(0xFF8E4F00)

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(bg)
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.DirectionsBus,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${events.size} bunching alert${if (events.size > 1) "s" else ""}"
                        + " — tap to ${if (expanded) "hide" else "view"}",
                style = MaterialTheme.typography.labelLarge,
                color = fg,
                modifier = Modifier.fillMaxWidth().weight(1f, fill = true),
            )
        }

        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(modifier = Modifier.fillMaxWidth().background(bg.copy(alpha = 0.5f))) {
                events.forEach { e ->
                    BunchingRow(event = e, fg = fg)
                    if (e != events.last()) Divider(Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun BunchingRow(event: BunchingEventDto, fg: Color) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = "Route ${event.routeId} — ${event.severity.uppercase()}",
            style = MaterialTheme.typography.bodyMedium,
            color = fg,
        )
        Text(
            text = "Buses ${event.vehicleIds.joinToString(", ")} within ${event.distanceM.toInt()} m",
            style = MaterialTheme.typography.bodySmall,
            color = fg.copy(alpha = 0.8f),
        )
    }
}

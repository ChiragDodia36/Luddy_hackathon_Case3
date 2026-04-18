package com.luddy.bloomington_transit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luddy.bloomington_transit.domain.model.Arrival
import com.luddy.bloomington_transit.ui.theme.routeColor

@Composable
fun ArrivalRow(
    arrival: Arrival,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Route color badge
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(routeColor(arrival.routeColor)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = arrival.routeShortName.take(3),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = arrival.headsign.ifBlank { "Route ${arrival.routeShortName}" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(
                text = if (arrival.isRealtime) "Live" else "Scheduled",
                style = MaterialTheme.typography.labelSmall,
                color = if (arrival.isRealtime)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        CountdownChip(
            arrivalMs = arrival.displayArrivalMs,
            isRealtime = arrival.isRealtime
        )
    }
}

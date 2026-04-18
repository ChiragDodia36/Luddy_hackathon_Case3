package com.luddy.bloomington_transit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.*
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
    val color = routeColor(arrival.routeColor)

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
                .background(color),
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
            // Direction arrow + headsign
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.ArrowForward,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = arrival.headsign.ifBlank { "Route ${arrival.routeShortName}" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
            // Live / Scheduled indicator
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (arrival.isRealtime) {
                    val infiniteTransition = rememberInfiniteTransition(label = "live_pulse")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f, targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                        label = "live_alpha"
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2ECC71).copy(alpha = alpha))
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Live",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF2ECC71),
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Text(
                        "Scheduled",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        CountdownChip(
            arrivalMs = arrival.displayArrivalMs,
            isRealtime = arrival.isRealtime
        )
    }
}

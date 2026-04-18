package com.luddy.bloomington_transit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.luddy.bloomington_transit.data.ai.dto.PredictionDto
import com.luddy.bloomington_transit.ui.theme.routeColor
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** Row showing a single AI-backed arrival prediction with Scheduled/BT/Ours side by side. */
@Composable
fun AiArrivalRow(
    prediction: PredictionDto,
    routeColorHex: String? = null,
    modifier: Modifier = Modifier,
) {
    val nowSec = System.currentTimeMillis() / 1000L
    val scheduledSec = runCatching { OffsetDateTime.parse(prediction.scheduledArrivalUtc).toEpochSecond() }.getOrDefault(0L)
    val btSec = runCatching { OffsetDateTime.parse(prediction.btPredictedArrivalUtc).toEpochSecond() }.getOrDefault(0L)
    val oursSec = runCatching { OffsetDateTime.parse(prediction.oursPredictedArrivalUtc).toEpochSecond() }.getOrDefault(0L)

    val color = routeColorHex?.let { routeColor(it) } ?: MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Route badge
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                (prediction.routeShortName ?: prediction.routeId ?: "?").take(3),
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    prediction.headsign ?: ("Route " + (prediction.routeShortName ?: "")),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                ConfidenceBadge(prediction.confidence)
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TimeTriplet(label = "Sched", epochSec = scheduledSec, now = nowSec,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TimeTriplet(label = "BT",    epochSec = btSec,        now = nowSec,
                    color = MaterialTheme.colorScheme.secondary)
                TimeTriplet(label = "Ours",  epochSec = oursSec,      now = nowSec,
                    color = MaterialTheme.colorScheme.primary,
                    emphasize = true)
                if (prediction.isRealtime) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.FlashOn, contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(2.dp))
            val correction = prediction.correctionSeconds
            if (kotlin.math.abs(correction) >= 5.0) {
                Text(
                    text = "Adjusted ${if (correction >= 0) "+" else ""}${correction.toInt()}s vs BT · ${prediction.modelSource}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "Matches BT · ${prediction.modelSource}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TimeTriplet(label: String, epochSec: Long, now: Long,
                        color: Color, emphasize: Boolean = false) {
    val delta = epochSec - now
    val text = when {
        epochSec <= 0 -> "—"
        delta <= 0 -> "now"
        delta < 60 -> "${delta}s"
        delta < 3600 -> "${delta / 60}m"
        else -> "${delta / 3600}h${(delta % 3600) / 60}m"
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        Text(
            text,
            style = if (emphasize) MaterialTheme.typography.bodyLarge
                    else MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal,
            color = color,
        )
    }
}

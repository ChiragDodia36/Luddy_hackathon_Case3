package com.luddy.bloomington_transit.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luddy.bloomington_transit.ui.theme.CountdownAmber
import com.luddy.bloomington_transit.ui.theme.CountdownGreen
import com.luddy.bloomington_transit.ui.theme.CountdownRed
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val clockFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

@Composable
fun CountdownChip(
    arrivalMs: Long,
    isRealtime: Boolean,
    modifier: Modifier = Modifier
) {
    var secondsLeft by remember(arrivalMs) {
        mutableLongStateOf(((arrivalMs - System.currentTimeMillis()) / 1000L).coerceAtLeast(0))
    }

    // Adaptive tick rate:
    //  < 2 min away  → every second (live urgency)
    //  2–30 min      → every 30 s (minute label)
    //  > 30 min      → every 60 s (clock time, changes once per minute)
    LaunchedEffect(arrivalMs) {
        while (true) {
            secondsLeft = ((arrivalMs - System.currentTimeMillis()) / 1000L).coerceAtLeast(0)
            val tickMs = when {
                secondsLeft < 120L  -> 1_000L
                secondsLeft < 1800L -> 30_000L
                else                -> 60_000L
            }
            delay(tickMs)
        }
    }

    val minutes = secondsLeft / 60

    // Display logic:
    //  > 30 min  → 24h clock time, e.g. "22:45"
    //  1–30 min  → minute countdown, e.g. "14m"
    //  < 1 min   → "45s" (realtime) or "Now" (scheduled)
    val label = when {
        minutes > 30  -> clockFmt.format(Date(arrivalMs))
        minutes == 0L -> if (isRealtime) "${secondsLeft % 60}s" else "Now"
        else          -> "${minutes}m"
    }

    val bgColor by animateColorAsState(
        targetValue = when {
            minutes > 30  -> MaterialTheme.colorScheme.surfaceVariant
            !isRealtime   -> MaterialTheme.colorScheme.surfaceVariant
            minutes < 1L  -> CountdownRed
            minutes < 3L  -> CountdownAmber
            else          -> CountdownGreen
        },
        animationSpec = tween(500),
        label = "countdownColor"
    )

    val textColor: Color = when {
        minutes > 30  -> MaterialTheme.colorScheme.onSurfaceVariant
        !isRealtime   -> MaterialTheme.colorScheme.onSurfaceVariant
        else          -> Color.White
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luddy.bloomington_transit.ui.theme.CountdownAmber
import com.luddy.bloomington_transit.ui.theme.CountdownGreen
import com.luddy.bloomington_transit.ui.theme.CountdownRed
import kotlinx.coroutines.delay

@Composable
fun CountdownChip(
    arrivalMs: Long,
    isRealtime: Boolean,
    modifier: Modifier = Modifier
) {
    var secondsLeft by remember(arrivalMs) {
        mutableLongStateOf(((arrivalMs - System.currentTimeMillis()) / 1000L).coerceAtLeast(0))
    }

    LaunchedEffect(arrivalMs) {
        while (true) {
            secondsLeft = ((arrivalMs - System.currentTimeMillis()) / 1000L).coerceAtLeast(0)
            delay(1000L)
        }
    }

    val minutes = secondsLeft / 60
    val seconds = secondsLeft % 60

    val bgColor by animateColorAsState(
        targetValue = when {
            !isRealtime -> MaterialTheme.colorScheme.surfaceVariant
            minutes < 1L -> CountdownRed
            minutes < 3L -> CountdownAmber
            else -> CountdownGreen
        },
        animationSpec = tween(500),
        label = "countdownColor"
    )

    val textColor = when {
        !isRealtime -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> androidx.compose.ui.graphics.Color.White
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (minutes == 0L && seconds < 60) {
            Text(
                text = if (isRealtime) "${seconds}s" else "Now",
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        } else {
            Text(
                text = if (isRealtime) "${minutes}m" else "${minutes}m *",
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

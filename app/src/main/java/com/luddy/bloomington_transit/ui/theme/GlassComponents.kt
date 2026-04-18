package com.luddy.bloomington_transit.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.util.Calendar

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.82f)
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.55f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        content = content
    )
}

@Composable
fun TimeGradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val gradientColors = remember(hour) {
        when {
            hour in 6..8   -> listOf(Color(0xFFFFF4E6), Color(0xFFD6E8FF))
            hour in 9..15  -> listOf(Color(0xFFF0F5FF), Color(0xFFE8F0FE))
            hour in 16..18 -> listOf(Color(0xFFFFF3E0), Color(0xFFFFE0B2))
            else           -> listOf(Color(0xFFEEF2FF), Color(0xFFE3E8F4))
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(brush = Brush.verticalGradient(colors = gradientColors)),
        content = content
    )
}

fun timeGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour in 6..8   -> "Good morning, catch your bus"
        hour in 9..11  -> "Good morning"
        hour in 12..15 -> "Good afternoon"
        hour in 16..18 -> "Good evening, heading home?"
        hour in 19..22 -> "Good evening"
        else           -> "Good night"
    }
}

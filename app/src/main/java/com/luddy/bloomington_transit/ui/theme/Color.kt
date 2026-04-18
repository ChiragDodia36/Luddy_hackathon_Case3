package com.luddy.bloomington_transit.ui.theme

import androidx.compose.ui.graphics.Color

// BT Brand colors
val BtBlue = Color(0xFF0057A8)
val BtLightBlue = Color(0xFF4A90D9)

// Material seed
val Primary = BtBlue
val OnPrimary = Color.White
val PrimaryContainer = Color(0xFFD6E4FF)
val OnPrimaryContainer = Color(0xFF001D3D)

val Secondary = Color(0xFF5B7FA6)
val SecondaryContainer = Color(0xFFD0E4F7)

val Surface = Color(0xFFF8FAFF)
val SurfaceVariant = Color(0xFFE1E8F4)
val OnSurface = Color(0xFF1A1C20)
val OnSurfaceVariant = Color(0xFF44474E)

val Background = Color(0xFFF5F7FF)
val Error = Color(0xFFBA1A1A)

// Dark theme
val PrimaryDark = Color(0xFFACC7FF)
val OnPrimaryDark = Color(0xFF00316B)
val SurfaceDark = Color(0xFF111318)
val BackgroundDark = Color(0xFF111318)
val OnSurfaceDark = Color(0xFFE2E2E9)

// Countdown colors
val CountdownGreen = Color(0xFF2ECC71)
val CountdownAmber = Color(0xFFF39C12)
val CountdownRed = Color(0xFFE74C3C)

// Route colors (used on map and chips)
fun routeColor(hexColor: String): Color {
    return try {
        val clean = hexColor.removePrefix("#").padStart(6, '0')
        Color(android.graphics.Color.parseColor("#$clean"))
    } catch (e: Exception) {
        BtBlue
    }
}

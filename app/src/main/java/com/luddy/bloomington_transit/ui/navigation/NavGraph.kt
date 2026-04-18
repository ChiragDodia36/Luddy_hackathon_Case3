package com.luddy.bloomington_transit.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Map : Screen("map?routeId={routeId}")
    object Schedule : Screen("schedule")
    object Favourites : Screen("favourites")
}

fun Screen.routeWithArg(routeId: String): String = when (this) {
    is Screen.Map -> "map?routeId=$routeId"
    else -> route
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Home, "Home", Icons.Filled.Home),
    BottomNavItem(Screen.Map, "Map", Icons.Filled.Map),
    BottomNavItem(Screen.Schedule, "Schedule", Icons.Filled.Schedule),
    BottomNavItem(Screen.Favourites, "Favourites", Icons.Filled.Favorite)
)

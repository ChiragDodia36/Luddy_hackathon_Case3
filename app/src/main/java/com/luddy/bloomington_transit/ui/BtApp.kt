package com.luddy.bloomington_transit.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.luddy.bloomington_transit.ui.navigation.Screen
import com.luddy.bloomington_transit.ui.navigation.bottomNavItems
import com.luddy.bloomington_transit.ui.screens.ai.AiStopScreen
import com.luddy.bloomington_transit.ui.screens.diagnostics.DiagnosticsScreen
import com.luddy.bloomington_transit.ui.screens.favourites.FavouritesScreen
import com.luddy.bloomington_transit.ui.screens.home.HomeScreen
import com.luddy.bloomington_transit.ui.screens.map.MapScreen
import com.luddy.bloomington_transit.ui.screens.schedule.ScheduleScreen
import com.luddy.bloomington_transit.ui.screens.trip.TripEtaScreen
import com.luddy.bloomington_transit.ui.theme.BtBlue
import com.luddy.bloomington_transit.ui.theme.TimeGradientBackground

@Composable
fun BtApp() {
    val navController = rememberNavController()

    TimeGradientBackground {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(
                    containerColor = Color.White.copy(alpha = 0.88f),
                    tonalElevation = 0.dp
                ) {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination

                    bottomNavItems.forEach { item ->
                        val itemRoute = if (item.screen is Screen.Map) "map" else item.screen.route
                        val selected = currentDestination?.hierarchy?.any { dest ->
                            dest.route?.startsWith(itemRoute) == true
                        } == true

                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = selected,
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = BtBlue.copy(alpha = 0.15f),
                                selectedIconColor = BtBlue,
                                selectedTextColor = BtBlue,
                                unselectedIconColor = Color(0xFF44474E),
                                unselectedTextColor = Color(0xFF44474E)
                            ),
                            onClick = {
                                val dest = if (item.screen is Screen.Map) "map" else item.screen.route
                                navController.navigate(dest) {
                                    popUpTo(Screen.Home.route) {
                                        inclusive = false
                                        saveState = false
                                    }
                                    launchSingleTop = true
                                    restoreState = false
                                }
                            }
                        )
                    }
                }
            }
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.padding(paddingValues),
                enterTransition = { fadeIn() + slideInHorizontally() },
                exitTransition = { fadeOut() + slideOutHorizontally() },
                popEnterTransition = { fadeIn() + slideInHorizontally { -it } },
                popExitTransition = { fadeOut() + slideOutHorizontally { it } }
            ) {
                composable(Screen.Home.route) { HomeScreen(navController) }
                composable(
                    route = Screen.Map.route,
                    arguments = listOf(navArgument("routeId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    })
                ) { backStackEntry ->
                    val routeId = backStackEntry.arguments?.getString("routeId")
                    MapScreen(navController = navController, initialRouteId = routeId)
                }
                composable(Screen.Ai.route) { AiStopScreen(navController) }
                composable(Screen.Schedule.route) { ScheduleScreen(navController) }
                composable(Screen.Favourites.route) { FavouritesScreen(navController) }
                composable(Screen.Diagnostics.route) { DiagnosticsScreen(navController) }
                composable(
                    route = Screen.TripEta.route,
                    arguments = listOf(navArgument("tripId") {
                        type = NavType.StringType
                        nullable = false
                    })
                ) { TripEtaScreen(navController) }
            }
        }
    }
}

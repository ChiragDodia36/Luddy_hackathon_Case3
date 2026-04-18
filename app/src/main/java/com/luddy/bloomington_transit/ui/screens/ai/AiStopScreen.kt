package com.luddy.bloomington_transit.ui.screens.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.luddy.bloomington_transit.ui.components.AiArrivalRow
import com.luddy.bloomington_transit.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiStopScreen(
    navController: NavController,
    viewModel: AiStopViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Insights, contentDescription = null,
                         tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.width(8.dp))
                    Text("AI Predictions", fontWeight = FontWeight.Bold)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        )

        // Live scoreboard — BT vs Us (headline number from /stats).
        uiState.stats?.let { stats ->
            val bt = stats.btHeadlineMaeS
            val us = stats.a1CvHeadlineMaeS
            val pct = stats.a1CvImprovementPct
            val routes = stats.routesWithIntercept
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Live: BT vs Us",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "${us?.let { "%.0f".format(it) } ?: "—"} s",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "vs BT ${"%.0f".format(bt)} s",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(bottom = 3.dp),
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            pct?.let { p ->
                                if (p > 0) "+%.1f%% better".format(p) else "%.1f%% worse".format(p)
                            } ?: "—",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if ((pct ?: 0.0) > 0.0) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error,
                        )
                        Text(
                            "$routes routes corrected",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }

        // Search bar
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search BT stops…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear")
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
            )

            // NL intent chip (C2) — shown when /nlq matches a recognised pattern
            uiState.nlqResult?.let { nlq ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = when (nlq.intent) {
                            "next_on_route" -> "Got it: find next arrival on Route ${nlq.routeId}"
                            "show_route" -> "Got it: Route ${nlq.routeId}"
                            "stop_search" -> "Got it: searching stops"
                            else -> "Parsed via ${nlq.parseSource}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (uiState.searchResults.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                    elevation = CardDefaults.cardElevation(4.dp),
                ) {
                    uiState.searchResults.forEach { stop ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectStop(stop) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Place, contentDescription = null,
                                 tint = MaterialTheme.colorScheme.primary,
                                 modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stop.name, style = MaterialTheme.typography.bodyMedium)
                        }
                        Divider()
                    }
                }
            }
        }

        uiState.selectedStop?.let { stop ->
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(stop.name, style = MaterialTheme.typography.titleMedium,
                     fontWeight = FontWeight.SemiBold)
                uiState.predictions?.feedHeaderAgeSeconds?.let { age ->
                    Text(
                        text = "Feed updated ${age}s ago",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Legend
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Sched = schedule · BT = agency prediction · Ours = our adjusted ETA",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))

            when {
                uiState.isLoadingPredictions -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }
                uiState.predictions == null -> Unit
                uiState.predictions!!.predictions.isEmpty() -> {
                    Text(
                        text = "No upcoming departures in the next hour.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    LazyColumn {
                        items(uiState.predictions!!.predictions) { p ->
                            AiArrivalRow(
                                prediction = p,
                                onClick = { navController.navigate(Screen.TripEta.routeFor(p.tripId)) },
                            )
                            Divider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        } ?: run {
            // Empty state
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Insights,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Search a stop to see BT vs. AI-adjusted ETAs",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        uiState.errorMessage?.let { msg ->
            Text(
                "Error: $msg",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

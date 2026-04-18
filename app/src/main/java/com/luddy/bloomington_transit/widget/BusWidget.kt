package com.luddy.bloomington_transit.widget

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.luddy.bloomington_transit.MainActivity
import com.luddy.bloomington_transit.domain.repository.TransitRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun transitRepository(): TransitRepository
}

class BusWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = EntryPointAccessors
            .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            .transitRepository()

        val pinnedIds = repo.getFavouriteStopIds().first()
        val trackedBusIds = repo.getTrackedBusIds().first()
        val items = mutableListOf<WidgetItem>()

        if (trackedBusIds.isNotEmpty()) {
            val buses = repo.getLiveBuses()
            buses.filter { it.vehicleId in trackedBusIds }.forEach { bus ->
                items.add(WidgetItem.TrackedBus(label = "Bus ${bus.label.ifBlank { bus.routeId }}"))
            }
        }

        pinnedIds.take(2).forEach { stopId ->
            val arrivals = repo.getArrivalsForStop(stopId)
            val next = arrivals.firstOrNull()
            val stop = repo.searchStops(stopId).firstOrNull()
            if (stop != null && next != null) {
                items.add(WidgetItem.StopArrival(
                    stopName = stop.name,
                    routeName = next.routeShortName,
                    minutesAway = next.minutesUntil(),
                    isRealtime = next.isRealtime
                ))
            }
        }

        provideContent { WidgetContent(items = items) }
    }
}

sealed class WidgetItem {
    data class TrackedBus(val label: String) : WidgetItem()
    data class StopArrival(
        val stopName: String,
        val routeName: String,
        val minutesAway: Long,
        val isRealtime: Boolean
    ) : WidgetItem()
}

@Composable
private fun WidgetContent(items: List<WidgetItem>) {
    val primaryColor = ColorProvider(AndroidColor.parseColor("#0057A8"))
    val textColor = ColorProvider(AndroidColor.parseColor("#1A1C20"))
    val subtextColor = ColorProvider(AndroidColor.parseColor("#44474E"))
    val bgColor = ColorProvider(AndroidColor.parseColor("#F8FAFF"))

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bgColor)
            .cornerRadius(16)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.TopStart
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Text(
                "BT Transit",
                style = TextStyle(
                    color = primaryColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            )
            Spacer(GlanceModifier.height(6.dp))

            if (items.isEmpty()) {
                Text(
                    "Open BT Transit to set up",
                    style = TextStyle(color = subtextColor, fontSize = 12.sp)
                )
            } else {
                items.forEach { item ->
                    when (item) {
                        is WidgetItem.StopArrival -> {
                            Row(
                                modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = GlanceModifier.defaultWeight()) {
                                    Text(
                                        item.stopName,
                                        style = TextStyle(color = textColor, fontSize = 12.sp),
                                        maxLines = 1
                                    )
                                    Text(
                                        "Route ${item.routeName}",
                                        style = TextStyle(color = subtextColor, fontSize = 10.sp)
                                    )
                                }
                                val countdownColor = if (item.minutesAway < 3)
                                    ColorProvider(AndroidColor.parseColor("#E74C3C"))
                                else primaryColor
                                Text(
                                    "${item.minutesAway}m",
                                    style = TextStyle(
                                        color = countdownColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                )
                            }
                        }
                        is WidgetItem.TrackedBus -> {
                            Text(
                                "Tracking: ${item.label}",
                                style = TextStyle(
                                    color = primaryColor,
                                    fontSize = 11.sp,
                                    fontStyle = FontStyle.Italic
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

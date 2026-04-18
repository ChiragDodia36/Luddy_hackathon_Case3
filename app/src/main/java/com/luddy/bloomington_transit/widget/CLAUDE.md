# widget/ — Claude Context

## Files
- `BusWidget.kt` — Glance AppWidget content + data loading
- `BusWidgetReceiver.kt` — `GlanceAppWidgetReceiver` → points to `BusWidget`

## Glance specifics
- Uses `androidx.glance:glance-appwidget:1.1.0` — Compose-based widget API
- `dp`/`sp` must be imported from `androidx.compose.ui.unit` (NOT auto-imported in Glance context)
- `clickable` must be imported from `androidx.glance.action.clickable`
- `GlanceModifier.background()` takes `ColorProvider`, not Compose `Color`
- Uses `@EntryPoint` + `EntryPointAccessors` to access Hilt-injected `TransitRepository`

## Widget layout
```
[BT Transit]
[Tracking: Bus 6]          (if tracking active)
[Stop Name | Route 1 | 4m] (per pinned stop)
[Stop Name | Route 4 | 11m]
```

## Widget info config
`res/xml/bus_widget_info.xml` → min size 250x110dp, updates every 30min (updatePeriodMillis).
For more frequent updates (during demo), trigger manually via `BusWidget().update(context, id)`.

## Test
Long-press home screen → Widgets → "BT Transit" → place widget.
Verify it shows pinned stops. Tap widget → opens MainActivity.

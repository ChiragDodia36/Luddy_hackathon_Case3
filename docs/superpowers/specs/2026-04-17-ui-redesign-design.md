# UI Redesign — BT Transit App
**Date:** 2026-04-17
**Status:** Approved

---

## Overview

A full UI redesign of the Bloomington Transit Android app targeting daily student and commuter users. The core concept is **light glassmorphism with adaptive context** — a soft time-shifting gradient canvas, frosted white glass cards, and a smart home screen that reshapes itself based on time of day, user location, and learned habits.

---

## Target Users

- Students catching buses to/from campus
- Regular daily commuters (work, errands)
- Primary need: glanceable, fast — know your next bus in under 3 seconds

---

## Visual Design System

### Background Canvas
Time-aware soft gradient — shifts across the day:

| Time Window | Gradient | Mood |
|---|---|---|
| 6am – 9am | `#FFF4E6` → `#D6E8FF` | Warm golden morning |
| 9am – 4pm | `#F0F5FF` → `#E8F0FE` | Clean cool daylight |
| 4pm – 7pm | `#FFF3E0` → `#FFE0B2` | Amber evening commute |
| 7pm – 6am | `#EEF2FF` → `#E3E8F4` | Soft muted night |

The gradient is applied as the root `Box` background of every screen, replacing the current flat `MaterialTheme.colorScheme.background`.

### Glass Card Spec
All cards follow this spec:
- Background: `Color.White.copy(alpha = 0.72f)`
- Blur: `RenderEffect` blur (`20dp`) via `graphicsLayer { renderEffect = BlurEffect(...) }` — requires API 31+
- Fallback (API < 31): `Color.White.copy(alpha = 0.88f)` solid white, no blur — still clean
- Border: `1dp` stroke `Color.White.copy(alpha = 0.40f)`
- Shadow: `12dp` blur, `Color.Black.copy(alpha = 0.08f)`
- Corner radius: `20dp`

### Typography
- Unchanged from current Material3 scale
- Hero countdown: `displayLarge`, bold, `BtBlue` tinted
- Card section labels: `labelLarge`, `onSurfaceVariant`

### Brand Colors
- `BtBlue = #0057A8` — unchanged, primary accent on CTAs and route badges
- `BtLightBlue = #4A90D9` — unchanged

### Countdown Colors
- `CountdownGreen` >3 min — unchanged
- `CountdownAmber` 1–3 min — unchanged
- `CountdownRed` <1 min — unchanged

### Top App Bar
Removed from all screens. Replaced with lightweight floating greeting/title text directly on the gradient canvas. No container background.

### Bottom Navigation
Frosted glass bar:
- Background: `Color.White.copy(alpha = 0.80f)` + `blur(16dp)`
- Top border: `1dp`, `Color.White.copy(alpha = 0.30f)`
- Active tab: route color dot indicator + label
- Inactive tab: icon only, muted `onSurfaceVariant`
- 4 tabs retained: Home, Map, Schedule, Favourites

---

## Screen Designs

### Home Screen

**Header (floating on gradient):**
- Left: time-aware greeting — `"Good morning, catch your bus"` / `"Good evening, heading home?"` etc.
- Right: notification bell icon + future avatar circle
- Style: `titleMedium`, no background container

**Card Stack (16dp vertical gaps, vertically scrollable):**

**Card 1 — Live Countdown Hero** (always top)
- Route badge (colored) + headsign
- Giant animated countdown: `displayLarge`, bold — ticks every second
- `"minutes away"` label below
- Live pill indicator + distance to stop (`"0.2 mi from stop"`)
- `"View on Map"` filled button, `BtBlue`

**Card 2 — Nearest Stop Arrivals** (location-aware)
- Stop name header with pin icon
- Top 3 arrivals: route badge (colored) + headsign + countdown chip + live/scheduled dot
- `●` = live realtime, `○` = scheduled

**Card 3 — Context Card** (one of three states):
1. **Service alert active:** Glass card with amber `1dp` border glow, alert text — replaces current `ServiceAlertBanner`
2. **Second saved route:** Compact countdown card for return/second route
3. **New user / no data:** Onboarding prompt — `"Pin a route to track it here"`

**Adaptive Reordering Logic:**

| Condition | Behavior |
|---|---|
| User within 300m of a saved stop | Card 2 promotes to top position |
| Service alert active | Card 3 becomes alert card with amber border |
| Evening (4–7pm) + return route saved | Card 1 flips to return route countdown |
| New user, no saved routes | Cards 2 + 3 show discovery/onboarding prompts |

---

### Map Screen

**Map Canvas:**
- Google Map fills full screen (no TopAppBar)
- Custom light desaturated map style to reduce visual noise
- Route polylines: GTFS colors, `width = 10f`, soft drop shadow

**Floating Glass Route Filter Bar (top):**
- Glass pill container, `White 75%` blur, `24dp` pill radius
- Scrollable `FilterChip` row: All + per-route chips
- Selected chip: solid `route_color` fill, white text
- Floats `12dp` from top of map

**Persistent Mini Panel (bottom, always visible):**
- Glass card anchored to bottom, `72dp` collapsed height
- Drag handle at top
- Collapsed state: `"X buses on selected routes · Tap a bus or stop"`
- Expands on bus/stop tap → Bus Detail or Stop Detail half-sheet
- Drag handle allows half-expand (arrivals) or dismiss

**Bus Markers (upgraded):**
- Pill-shaped marker: route color fill, white route short name, drop shadow
- Subtle bounce animation on position update
- Tracked bus: glowing pulse ring animation

**Stop Markers (upgraded):**
- White circle with colored ring matching nearest route
- `14dp` size, visible from zoom `13f` (down from current `14f`)

**Bus Detail Panel (expanded):**
- Route badge + route long name + vehicle ID
- Speed + heading info
- `"Track This Bus"` / `"Stop Tracking"` full-width button

**Stop Detail Panel (expanded):**
- Stop name with pin icon
- Top 5 arrivals: route badge + headsign + countdown chip + live dot

---

### Schedule Screen

**Floating Glass Search Bar (top, on gradient canvas):**
- Glass card, `20dp` radius, no TopAppBar above it
- On focus: card lifts with elevation animation
- Search results: attached glass list below the bar (not a separate card)

**Stop Header Card (after stop selected):**
- Glass card: stop name + `"X routes serve this stop"` subtitle
- `"Live only"` filter chip right-aligned

**Departure Board:**
- Entire arrivals list wrapped in one glass card (not floating divider rows)
- Row: route badge (colored) + headsign + countdown chip (green/amber/red) + live dot
- Live dot: subtle scale pulse animation every 2s
- Row tap: expands inline to show scheduled vs predicted delta

**Recently Viewed Strip:**
- Horizontal scrollable glass chip row below search bar
- Shows last 3 searched stops — one tap to reload

**Empty State:**
- Centered icon + `"Search a stop above to see live arrivals"`
- `"Use my nearest stop"` glass button — auto-detects location

---

### Favourites Screen

**Header (floating on gradient):**
- `"Your saved stops"` in `titleMedium`, no TopAppBar

**Pinned Route Card (top, if set):**
- Slightly larger glass card with route-colored `2dp` border
- Star icon + `"Your Route"` label
- Route badge + route name + live countdown + `"View on Map"` / `"Change Route"` buttons

**Per-Stop Glass Cards (vertically stacked):**
- Each saved stop = one glass card
- Stop name + top 2 live arrivals (route badge + countdown chip + live dot)
- `"View on Map →"` text button bottom-right
- Auto-refreshes every 10s (existing polling pattern)
- Tap card → expands inline to full arrival list (no navigation)

**Swipe to Remove:**
- Swipe left on any stop card → red glass overlay + `"Remove"` action
- Replaces current (no swipe-to-delete exists)

**Empty State:**
- Star icon + `"No saved stops yet"`
- Instruction copy + `"Search Stops →"` glass button linking to Schedule tab

---

## Adaptive Context System

Three layers combine to reshape the Home screen:

### Layer 1 — Time Aware
- Reads `Calendar.HOUR_OF_DAY` on each Home screen composition
- Maps to gradient + greeting text + card 1 route selection (morning = outbound, evening = return)

### Layer 2 — Location Aware
- Uses existing `getNearestStops()` use case
- If user within 300m of a saved stop → Card 2 promotes to top
- Triggers on `LocationManager` update (coarse location, low battery drain)

### Layer 3 — Habit Aware (Phase 2 — DataStore)
- Tracks which stop/route the user views most frequently per time window
- Stored in `UserPreferencesDataStore` as a frequency map
- After 3+ uses, auto-promotes that stop/route to Card 1 during its time window

---

## Components to Create / Modify

| Component | Action |
|---|---|
| `GlassCard` | New reusable composable — encapsulates glass spec |
| `TimeGradientBackground` | New composable — wraps screen in time-aware gradient |
| `GlassBottomNav` | Modify `BtApp.kt` bottom bar |
| `CountdownHeroCard` | New — large countdown, Card 1 |
| `NearestStopCard` | New — Card 2 arrival list |
| `ContextCard` | New — Card 3 adaptive slot |
| `GlassBusMarker` | Modify `MapScreen.kt` bus marker |
| `GlassFilterBar` | Modify route filter row on Map |
| `PersistentMapPanel` | New — replaces `AnimatedVisibility` bottom sheet |
| `FavouriteStopCard` | New — per-stop live card for Favourites |
| `RecentStopsStrip` | New — horizontal chip row for Schedule |
| `TopAppBar` (all screens) | Remove, replace with floating header text |

---

## What Does Not Change

- Navigation graph and 4-tab structure
- GTFS data layer, repository, use cases — no changes
- Countdown color system (green/amber/red)
- Route badge color system (GTFS `route_color`)
- 10s polling interval
- All ViewModels — UI-only redesign

---

## Out of Scope

- University bus route integration (separate feature)
- Habit tracking (Layer 3 adaptive) — Phase 2
- Authentication / user accounts
- Dark mode redesign (follows system, existing theme handles it)

# GTW (Going That Way) — Feature Specification & Technical Implementation Plan

## What is GTW (Going That Way)?

**GTW (Going That Way)** — also known in ride-hailing as **Destination Mode** — lets a driver who is heading toward a chosen destination (home, town/CBD, a rank, or another village) earn fares along the route instead of driving empty (**0% deadhead waste**).

When GTW is turned on, Vhaya routes the driver toward their chosen destination while filtering or prioritizing ride requests whose **pickup and drop-off align along the same corridor** within a chosen detour distance.

---

## Feature Parity & Capabilities

### 1. Setting a GTW Destination (3 Ways)
- **Live Place / Address Search:** Search any town, suburb, mall, clinic, or street using `searchPlaces`.
- **Nearby Destinations & Hubs:** One-tap selection from saved places, nearby places, and regional corridor hubs.
- **Drop Pin on Map:** Pan the map crosshair to select any rural drop-off point or road junction.

### 2. Detour Budget & Matching Modes
- **Max Detour Radius:** `Tight (4 km)`, `Standard (6 km)`, or `Wide (10 km)`.
- **Mode Toggle:**
  - **GTW Only (Strict):** Hides off-route offers and shows only rides heading toward the GTW target.
  - **Boost GTW (Prioritize):** Keeps all rate-eligible offers visible, ranks GTW-matched trips first, and badges them clearly.
- **Min Rate Compatibility:** Combines cleanly with the driver's minimum `R/km` filter.

### 3. Smart Along-the-Way Matching & Detour Scoring
- **Pickup Corridor Proximity:** Ensures pickup is near the driver or within detour distance of the `Driver → GTW Target` segment.
- **Directional Alignment Cosine:** Uses vector cosine similarity (`>= 0.10`) so perpendicular/opposite-direction trips are rejected.
- **Net Progress Toward Target:** Ensures the rider's drop-off leaves the driver closer to (or inside) the GTW destination.
- **Added Detour Calculation:**
  `Added Detour (km) = (Driver → Pickup + Pickup → Drop-off + Drop-off → GTW) − (Driver → GTW)`

### 4. Live GTW Route Navigation & Map UI
- **Active GTW Road Route:** Fetches and draws the real OSRM/offline route toward the GTW target while scanning for offers (`DriverGtwNavBanner` + `OsmRouteMap`).
- **Map Overlays:** Corridor band, GTW destination flag pill, and highlighted GTW-matched request markers.
- **Offer Card Badge:** `DriverIncomingOfferCard` shows **`GTW · On way to <Target> (+X.X km)`**.
- **Persistence & Sync:** Persists locally in `SessionStore` and syncs with the backend (`/api/driver/gtw`) when deployed, with silent local fallback.

---

## Technical Implementation Plan

### Phase 1: Backend API & Matching (`BACKEND_GTW_API.md`)
1. **Database (`driver_gtw_targets`):** Store `driver_id`, `label`, `dest_latitude`, `dest_longitude`, `max_detour_km`, `strict_filter`, `is_active`, `updated_at_ms`.
2. **Matching Service (`evaluateGtwMatch`):** Compute Haversine progress, segment distance, directional cosine alignment, added detour km, and ranking score.
3. **Endpoints (`/api/driver/gtw`):**
   - `GET /api/driver/gtw` — load active GTW state.
   - `PUT /api/driver/gtw` — activate/update destination, detour km, and `strictFilter`.
   - `DELETE /api/driver/gtw` — clear GTW target.
   - `GET /api/driver/gtw/matches` — return ranked GTW-matched open rides.

### Phase 2: Android Domain & Persistence
1. **`DriverMapFeatures.kt`:**
   - Extend `DriverCorridorTarget` with backward-compatible defaults (`strictFilter: Boolean = true`, `updatedAtMs: Long = 0L`).
   - Add `GtwTripMatch` and `DriverMapEngine.evaluateGtwMatch(...)` + `DriverMapEngine.rankTripsForGtw(...)`.
   - Keep `matchesCorridorFilter(...)` backed by `evaluateGtwMatch(...)` and preserve existing `MapRequestMarker` compatibility.
2. **`VhayaApiClient.kt` & `WayfareRepository.kt`:**
   - Add `fetchDriverGtwTarget` / `saveDriverGtwTarget` (`GET`/`PUT`/`DELETE /api/driver/gtw`), gracefully treating `404`/`405`/`501` as optional local-only mode so existing servers never break.
   - Sync GTW target in `WayfareRepository` (`setDriverCorridorTarget`, `refreshAll`).

### Phase 3: Android ViewModel & Driver UI
1. **`WayfareViewModel.kt`:**
   - Add `driverGtwRoute` and `driverGtwRouteLoading` to `LocalNavState` & `WayfareUiState`.
   - Update `driverOpenRequests` to rank GTW matches first and support both **GTW Only (`strictFilter = true`)** and **Boost GTW (`strictFilter = false`)**.
   - Fix `searchPlaces` proximity anchor to use `driverCurrentCoords` when active role is `DRIVER`.
   - Add `refreshDriverGtwRoute(...)` and `toggleDriverGtwStrictMode()`.
2. **`DriverMapFeatureSheets.kt`:**
   - Update `DriverMapFeaturePillRow` (`testTag("driver_corridor_pill")` preserved) to display `"GTW · Going That Way"` / `"GTW → <Target>"`.
   - Upgrade `CorridorFilterSheet` into **GTW · Going That Way** sheet with live destination search (`searchPlaces`), `GTW Only` / `Boost GTW` toggle, detour chips (`4 / 6 / 10 km`), map pin picker, and saved/nearby destinations.
   - Add `DriverGtwNavBanner` showing destination, live ETA/distance, strict/boost toggle, external maps launch, and 1-tap clear.
3. **`DriverHomeScreen.kt` & `DriverDispatchComponents.kt`:**
   - Draw `driverGtwRoute` on `OsmRouteMap` while scanning in GTW mode.
   - Wire `DriverGtwNavBanner` into the contextual top status slot.
   - Show **`GTW · On way to <Target> (+X.X km)`** on `DriverIncomingOfferCard`.

### Phase 4: Pocket (Long-Distance Corridor & Cargo Boot Matching)
- **Concept:** Solves cross-province & inter-town travel (e.g. Malamulele ↔ Tembisa, Thohoyandou ↔ Johannesburg, Giyani ↔ Polokwane) where senders want to send parcels or catch scheduled lifts with passing drivers.
- **For Drivers:**
  - Register scheduled long-distance route with departure time, seats, and parcel slots (`DriverCreatePocketRouteForm`).
  - Vehicle Pocket Manifest (`DriverPocketSheet`): Inspect packed items, pickup/dropoff points, and recipient contact.
  - Pre-departure alert banner: Alerts the driver before leaving with total cargo earnings and 1-tap "Notify Senders & Depart".
  - Cargo lifecycle: `RESERVED` → `COLLECTED` → `IN_TRANSIT` → `DELIVERED` (verified with recipient PIN).
- **For Riders / Senders:**
  - Dedicated **Pocket** button on `CompactWhereToSheet` (`rider_pocket_btn`).
  - Browse passing & scheduled drivers along corridors with live seats and pocket capacity (`DriverPocketRouteCard`).
  - Book parcel into Pocket (`BookParcelIntoPocketDialog`) with sizes: `ENVELOPE (R50)`, `SMALL_BOX (R90)`, `MEDIUM_BAG (R140)`, `LARGE_CARGO (R220)`.
  - Post requests to corridor board (`RiderPostPocketRequestForm`) so passing GTW drivers can claim them.
  - Track active and delivered parcels in "My Parcels" tab.

### Phase 5: Verification & Robolectric Tests
- Tests for `evaluateGtwMatch`, `WayfareRepository` GTW & Pocket persistence, and UI sheets (`DriverPocketSheet`, `RiderPocketSheet`).
- Verification via unit/Robolectric test suite and applet compilation.

---

### Phase 6: GTW v2 Enhancements (Implemented — Accuracy, Notifications, Pocket Bridge & Reliability)
1. **Polyline-Aware Corridor Matching (`DriverMapFeatures.kt`):**
   - Add `pointToPolylineDistanceKm(point, polyline)` and optional `corridorRoute: RideRoute? = null` parameter on `evaluateGtwMatch`, `matchesCorridorFilter`, `rankTripsForGtw`, and `buildRequestMarkers`.
   - Measure pickup/drop-off lateral detour against the real OSRM road polyline (`PolylineCodec.decode(corridorRoute.encodedPolyline)`) when available, falling back seamlessly to the straight-line `Driver → GTW Target` segment offline.
2. **GTW-Aware Background Offer Notifications (`RideNotificationHelper.kt`, `VhayaLiveSyncService.kt`):**
   - Update `pickNextEligibleOffer(...)` and `buildDriverOfferSpec(...)` to accept `corridorTarget` and `gtwMatch`, suppressing off-route background alerts when `strictFilter = true`, prioritizing GTW-matched offers, and surfacing `GTW · +X.X km detour` in lockscreen/heads-up notifications.
3. **Auto-Arrival, Stale Expiry & "Arrive By" Time Budget (`DriverMapFeatures.kt`, `WayfareViewModel.kt`, `DriverMapFeatureSheets.kt`):**
   - Extend `DriverCorridorTarget` with backward-compatible `arriveByEpochMs: Long? = null` and `expiresAtMs: Long? = null` plus `effectiveMaxDetourKm(nowMs)` (tightening detour allowance as the deadline approaches).
   - Auto-expire stale GTW targets (`> 8h` or past `expiresAtMs`) and auto-clear with feedback when the driver dwells within `500 m` of the GTW destination.
   - Add "Arrive by" chips (`Flexible`, `+45 min`, `+90 min`, `+2 hr`) in `CorridorFilterSheet`.
4. **GTW ↔ Pocket Parcel Integration (`DriverMapFeatures.kt`, `WayfareViewModel.kt`, `DriverMapFeatureSheets.kt`, `PocketComponents.kt`):**
   - Add `GtwPocketMatch` and `DriverMapEngine.evaluatePocketGtwMatch(...)` to match open `LISTED` Pocket items along the active GTW corridor.
   - Surface matching Pocket parcel counts on `DriverGtwNavBanner` and `CorridorFilterSheet` with a 1-tap jump straight to the `DriverPocketSheet` Corridor Board tab.
5. **1-Tap Presets, Offline Corridor Caching & Sync Conflict Guard (`SessionStore.kt`, `WayfareRepository.kt`, `DriverHomeScreen.kt`):**
   - Persist recent GTW destinations in `SessionStore` and show 1-tap **Recent GTW** chips at the top of `CorridorFilterSheet`, plus a 1-tap **"Cache corridor offline"** action.
   - Guard `WayfareRepository.refreshRoleScopedData` with `updatedAtMs` comparison so older server snapshots never overwrite newer offline GTW edits.
   - Show an **"After drop-off → X km to GTW"** indicator during active trips while GTW is enabled.
6. **Expanded Verification & Compose UI Tests (`DriverMapFeaturesRobolectricTest.kt`, `RideNotificationsRobolectricTest.kt`, `FrontendSimplificationTest.kt`):**
   - Tests for curved-route polyline matching, arrive-by tightening & expiry, GTW-Pocket parcel matching, GTW notification filtering/labeling, and Compose UI tests for `CorridorFilterSheet`, `DriverPocketSheet`, and `RiderPocketSheet`.



# Vhaya Android — Rider & Driver Improvements Plan

This checklist captures concrete improvements across existing Rider (Requester), Driver (Service Provider), and shared flows identified in the codebase audit.

---

## 1. Cross-Role & Shared Improvements

- [x] **Real Rider Identity & Call/SMS on Driver Trips (`WayfareModels.kt`, `DriverHomeScreen.kt`, `DriverActiveTripSheets.kt`)**
  - Add optional `riderName` and `riderPhone` fields to `RideTrip` so `resolveRequesterName(...)` displays the actual requester name instead of defaulting to `"Vhaya Requester"` outside Admin mode.
  - Surface **Call / SMS Rider** quick-action cards on `DriverActiveTripSheet` (just like beneficiary trips already have) when `riderPhone` is available, plus fallback to in-app chat.
- [x] **Wire Post-Trip Feedback Tags (`RiderHomeScreen.kt`, `WayfareViewModel.kt`, `WayfareRepository.kt`)**
  - Pass `tags: List<String>` from `RiderPostTripRatingSheet` through `WayfareViewModel.rateTrip(tripId, stars, tags)` into `WayfareRepository.rateTrip(...)` and serialize `feedbackTags` in `POST /api/rides/:tripId/actions` (`action = "rate"`), plus store them in `PendingOfflineAction` for offline sync.
  - Persist & sync `driverRateRider` (`WayfareViewModel.driverRateRider` / `WayfareRepository.saveDriverRiderRating`) via `POST /api/rides/:tripId/actions` (`action = "rateRider"`) with graceful local fallback if the backend endpoint returns 404/405/501 or offline.
- [x] **Complete Pocket & Cargo End-to-End Flows (`PocketComponents.kt`, `RiderHomeScreen.kt`, `WayfareRepository.kt`, `WayfareViewModel.kt`)**
  - **Real Delivery PIN Verification:** In `PocketItemCard`, tapping `"Verify PIN & Deliver"` opens a 4-digit PIN confirmation dialog verifying `item.recipientPin` (with inline error feedback) before marking `PocketItemStatus.DELIVERED`.
  - **Real Pocket Seat Reservation:** Replace the `"Reserve Seat"` shortcut in `RiderPocketSheet` (which opened a regular trip booking) with actual seat reservation on the `DriverPocketRoute` (`availableSeats` decrement + reservation entry + confirmation PIN/status), plus keep a secondary `"Book private ride"` option if needed.
  - **Real Geocoded Coordinates in `RiderPostPocketRequestForm`:** Replace hardcoded `"Malamulele Main Plaza & Rank"` / `"Tembisa Plaza & Mall"` placeholders and `DEFAULT_MAP_CENTER` fallback with live place search / geocoded pickup & destination coordinates from the user's inputs.
  - **Backend Sync Hooks:** Add optional Pocket sync calls (`GET/POST /api/pocket/routes`, `GET/POST /api/pocket/items`, `PATCH /api/pocket/items/:id`) with safe local `SessionStore` fallback when offline or before backend routes are deployed, and document them in `BACKEND_API_REFERENCE.md`.

---

## 2. Driver (Service Provider) Improvements

- [x] **Surface Scheduled Rides (`pickupAt`) Clearly (`DriverDispatchComponents.kt`, `DriverActiveTripSheets.kt`)**
  - Display a high-visibility **"Scheduled · HH:mm"** (or `"Scheduled · Today 06:00"`) badge on both `DriverIncomingOfferCard` and `DriverActiveTripSheet` whenever `trip.pickupAt != null`, distinguishing scheduled bookings from immediate `"Now"` requests.
- [x] **Counter-Offer Revise / Withdraw (`DriverDispatchComponents.kt`, `WayfareViewModel.kt`, `WayfareRepository.kt`)**
  - Allow drivers with a pending counter-offer (`metrics.myPendingOffer != null`) to **Revise** or **Withdraw** their offer cleanly, instead of leaving only `Accept` active.
  - Keep `Decline` crisp and ensure `Skip` / `Decline` cycling handles edge cases smoothly.
- [x] **Fix GPS Arrival & Pickup PIN Modal Friction (`DriverActiveTripSheets.kt`)**
  - Wait for the fresh GPS fix (or timeout gracefully) before calling `driverMarkArrived(trip.id)` when tapping `"Confirm GPS arrival"`, and auto-dismiss the modal cleanly once confirmed.
  - Add inline error & submitting state inside the pickup PIN verification modal so a wrong 6-digit PIN shows a clear error message right inside the dialog instead of silently failing behind it.
- [x] **Fix Go-Online Location Permission Race (`DriverHomeScreen.kt`)**
  - Do not call `viewModel.setDriverOnline(true)` in parallel while launching the location permission prompt; instead remember pending go-online intent and activate online scanning once `ACCESS_FINE_LOCATION` is granted.
- [x] **Driver In-App Document Upload & Earnings Tab Polish (`DriverHomeScreen.kt`, `DriverSecondaryTabs.kt`, `DriverEarningsTab.kt`)**
  - Expose the existing `WayfareRepository.uploadDriverDocument(...)` flow directly inside `DriverProfileTab` (for ID, Driver's Licence, Vehicle Registration, Operating Permit) with per-document status/expiry/staff-note display, and update `DriverPendingGateSheet` copy to point to in-app upload (keeping email as a secondary fallback).
  - In `DriverEarningsTab`:
    - Allow tapping **any** completed trip in the ledger to view details or open **Fare help / Dispute** for that specific trip (instead of tying `"Fare help"` only to the newest trip).
    - Add a **Daily Earnings Goal** progress card and a **Weekly Pass Expiry Reminder** banner when `<= 2` days remain.

---

## 3. Rider (Requester) Improvements

- [x] **In-Place Fare Boost & Payment Switch While Searching (`RiderActiveTripSheet.kt`, `WayfareViewModel.kt`, `WayfareRepository.kt`)**
  - In `SearchingDriversRadarCard` (especially after `30s+` / `60s+` wait), let riders tap **"+R10 Boost" / "+R20 Boost"** (with `boostFare` action + graceful fallback if backend doesn't support `boostFare` yet) or switch payment method without cancelling and rebooking.
- [x] **Custom Date & Time Scheduling (`RiderBookingSheets.kt`, `RiderHomeScreen.kt`, `WayfareViewModel.kt`)**
  - Beyond `+30m` and `+1h` quick chips, add a **Custom Date & Time** picker (Today / Tomorrow / pick date + hour/minute selector) for early-morning clinic, pension, and work commutes.
- [x] **Two-Way Saved Place Bookmark Toggle (`RiderHomeScreen.kt`, `RiderBookingSheets.kt`)**
  - Let tapping the bookmark icon on `ChooseRideAndFareSheet` **both save and unsave** a destination (currently `if (!isSaved)` only adds and ignores taps when already saved).
- [x] **Clarify `"Return Request (-18%)"` vs `"Wait & return"` (`RiderBookingSheets.kt`)**
  - Add clear, plain-language subtitles and visual distinction between **"Return Request (-18%)"** (*Catch a driver heading back empty*) and **"Wait & return"** (*Driver waits while you finish an errand*).
- [x] **Offer Trust Badges & Quick Pickup Landmark Note (`RiderActiveTripSheet.kt`)**
  - Prominently highlight **Community Circle / Trusted Driver** badges and sort/flag them clearly when multiple counter-offers arrive.
  - Allow editing/adding a quick **Pickup landmark note** right from the active waiting card (useful where street addresses don't exist).


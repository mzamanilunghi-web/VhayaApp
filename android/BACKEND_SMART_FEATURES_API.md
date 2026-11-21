# Vhaya Backend API Specification — Smart Rider & Driver Protection Features

This document specifies all **7 smart product, safety, and UX upgrades** implemented in the Vhaya Android app (`com.wayfare.ride`) to address real-world ride-hailing pain points across South Africa (driver cancellations/stalling, route deviations & forgotten trip endings, off-app fare extortion, low-rate offer friction, cash change shortages, mall/complex pickup confusion & waiting time disputes, and vehicle/document trust & slow support resolution).

All Android client changes are **backward-compatible**: the app already sends new optional fields safely, computes client-side fallbacks when older server payloads omit timestamps/trust flags, and gracefully handles `404`/`405`/`501` if a new endpoint has not yet been deployed.

---

## Feature Summary Matrix

| # | Feature | User Pain Point Solved | Primary Endpoint(s) |
|---|---------|------------------------|---------------------|
| 1 | Anti-Stall & "Driving the Wrong Way" Auto-Switch | Driver accepts trip then stalls, sits at petrol station, or drives opposite direction forcing rider to pay a cancel fee | `POST /api/rides/{id}/actions` (`action: "cancel"`) + optional `POST /api/rides/{id}/reassign` |
| 2 | Off-Route (`>500m`) & "Forgot to End Trip" (`>300m`) Guard | Mid-trip unsafe detours; driver forgets to tap complete after drop-off causing inflated/stuck trips | `GET /api/rides`, `PATCH /api/rides/{id}`, optional `POST /api/rides/{id}/actions` (`action: "safety_checkin"`) |
| 3 | Anti-Extortion Fare Lock & Chat Guard | Driver messages asking for "extra cash", "petrol money", or "cancel and pay outside app" | `POST /api/rides/{id}/actions` (`channel: "support"`, `category: "safety"`, `priority: "urgent"`) |
| 4 | Smart Offer Acceptance Coach & Driver Min `R/km` Filter | Riders bid unrealistically low & wait forever; drivers spammed with sub-cost requests | `GET /api/rides` + optional `GET /api/pricing/market-benchmark` |
| 5 | Cash Change Declaration, Cash-to-Wallet Bridge & Verified Rider Badge | "No change for R200" disputes at drop-off; driver anxiety over unverified cash riders | `POST /api/rides`, `GET /api/rides`, **NEW** `POST /api/wallet/cash-change-credit` |
| 6 | 3-Minute Arrival Wait Timer & Mall/Complex Pickup Micro-Tags | Riders lost at large malls/estates; endless unpaid driver waiting at gates | `POST /api/rides`, `GET /api/rides`, `PATCH /api/rides/{id}` (`ARRIVED`) |
| 7 | Docs & Permit Verified Shield, Wrong-Car Free Cancel & Auto-Evidence Support | Unverified vehicles/drivers; rider penalized for refusing mismatched car; repetitive support forms | `GET /api/rides`, `POST /api/rides/{id}/actions` (`cancel`, `support` with `evidence`) |

---

## 1. Anti-Stall & "Driving the Wrong Way" Auto-Switch

### Android Behaviour
- Tracks driver distance to pickup once `status == ACCEPTED` (`SmartRideGuards.DriverStallTracker`).
- Triggers free switch offer if:
  - Driver fails to reduce distance to pickup by at least `60 m` for **`90 seconds`** while still `> 150 m` away (`isStalled`), OR
  - Driver moves **`>= 400 m` farther away** from their closest observed point (`isDrivingAway`).
- Rider taps **"Switch driver (R 0.00 fee)"**. Android cancels the current ride with reason `"Driver stalled or moving away from pickup"` and immediately re-opens the booking sheet with preserved route, fare, payment method, `pickupNote`, and `cashChangeForZar`.

### Backend Contract
1. **Immediate Fee-Waiver Rule on `POST /api/rides/{id}/actions` (`action: "cancel"`):**
   - Request body:
     ```json
     {
       "action": "cancel",
       "reason": "Driver stalled or moving away from pickup",
       "telemetry": {
         "idleSeconds": 94,
         "movedAwayMeters": 420.5,
         "driverCoords": { "lat": -23.9045, "lng": 29.4688 }
       }
     }
     ```
   - **Server rule:** When `status == "accepted"` and server telemetry confirms the driver did not approach within `150m` and either stalled `>= 90s` or retreated `>= 400m` (or `reason` matches `"Driver stalled or moving away from pickup"` verified against GPS history), **waive all rider cancellation fees (`cancellationFeeMinor = 0`)** and decrement the driver's acceptance/reliability score instead of penalizing the rider.
2. **Optional 1-Tap Reassign Endpoint (Recommended Phase 2): `POST /api/rides/{id}/reassign`**
   - Cancels the stalled driver assignment, keeps the same `tripId` (or creates a linked `SEARCHING` ride), excludes `driverId` from matching for 10 minutes, preserves locked `fare`, `paymentMethod`, `pickupNote`, and `cashChangeForZar`, and broadcasts immediately to nearby drivers.
   - Response `200 OK`: `{ "ride": { ...updatedRideWithStatusSearching } }`.

---

## 2. Off-Route (`>500m`) & "Forgot to End Trip" (`>300m`) Safety Guard

### Android Behaviour
- **Rider side (`IN_PROGRESS`):** Compares `trip.liveDriverLocation` against the decoded OSRM route `encodedPolyline` (fallback: origin→destination segment). If deviation exceeds **`500 m`**, shows a discreet **"Route changed — are you okay?"** card with **"I'm safe"**, **"Alert"**, and **`112`**.
- **Driver side (`IN_PROGRESS`):** When driver taps **"Complete trip"** while GPS is **`> 300 m`** from `destinationCoordinates`, prompts a warning dialog to prevent accidentally ending early or forgetting to end at the real drop-off.

### Backend Contract
1. Ensure `GET /api/rides` and `GET /api/rides/{id}` consistently include:
   - `route.encodedPolyline` (Google-encoded polyline, precision 5)
   - `pickupCoordinates` (`{ lat, lng }`)
   - `destinationCoordinates` (`{ lat, lng }`)
   - `liveDriverLocation` (`{ lat, lng }`) updated every 3–5s during `ACCEPTED`/`ARRIVED`/`IN_PROGRESS`.
2. **Server-Side Auto-Close / Anomaly Detection:**
   - If a trip remains `IN_PROGRESS` for `> 10 min` after passing within `150m` of `destinationCoordinates` and moving away again, send a push notification / prompt to the driver to complete the trip and cap metered/fare adjustments at the agreed drop-off timestamp.
3. **Optional Telemetry Event on `POST /api/rides/{id}/actions`:**
   ```json
   {
     "action": "safety_checkin",
     "status": "safe" | "distress",
     "deviationMeters": 640.2,
     "coords": { "lat": -23.8921, "lng": 29.4510 }
   }
   ```

---

## 3. Anti-Extortion Fare Lock & Chat Guard

### Android Behaviour
- Pins a permanent **"Fare locked at R XX.XX · Never pay extra outside Vhaya"** banner at the top of `DriverChatSheetContent`.
- Scans incoming messages on `channel == "driver"` from the counterparty against extortion patterns (`extra cash/money/fare`, `add R...`, `more money`, `cancel and...`, `outside/off app`, `cash only`, `petrol/fuel money`, `pay me more/extra`).
- Surfaces an urgent **"Report fare pressure"** button which submits a high-priority support ticket directly with message snippet + telemetry.

### Backend Contract
1. **Chat Moderation & Strike Pipeline (`POST /api/rides/{id}/actions`, `action: "message"`):**
   - Mirror the extortion regex check server-side when persisting messages on `channel: "driver"`.
   - When flagged, attach `{ "flaggedReason": "fare_extortion_suspected" }` to the message record, notify trust & safety moderators, and prevent any driver-initiated cancellation fee if the rider cancels afterward.
2. **Urgent Support Escalation (`POST /api/rides/{id}/actions`, `action: "support"`):**
   - Android sends:
     ```json
     {
       "action": "support",
       "category": "safety",
       "priority": "urgent",
       "requestRefund": false,
       "message": "[Anti-Extortion Chat Guard] Driver asked for extra/off-app payment: \"...\"\n\n[Trip Telemetry] ...",
       "evidence": {
         "tripId": "trip_123",
         "rideStatus": "accepted",
         "paymentMethod": "cash",
         "paymentStatus": "pending",
         "lockedFareZar": 75.0,
         "chatExtortionFlagged": true,
         "chatExtortionCue": "extra cash request"
       }
     }
     ```
   - **Server rule:** Route `priority == "urgent"` + `evidence.chatExtortionFlagged == true` directly to the real-time Trust & Safety queue with SLA < 5 min, and automatically waive any cancel fee if the trip is cancelled within 5 minutes of the flagged message.

---

## 4. Smart Offer Acceptance Coach & Driver Min `R/km` Filter

### Android Behaviour
- **Rider booking:** Computes acceptance tier (`HIGH`, `MODERATE`, `LOW`), live `R X.X/km`, peak-hour demand adjustment (`06:30–09:00` and `16:00–18:30`, `+10%`), and recommended fair fare (`>= R 20.00`). Offers 1-tap **"Match R XX"** button when `tier == LOW`.
- **Driver dashboard:** Provides persistent `MinRateFilterStrip` (`All rides`, `R 15+/km`, `R 20+/km`, `R 25+/km`), hiding below-threshold requests client-side while displaying the count hidden and a 1-tap `"Show all"` reset.

### Backend Contract
1. **Server-Side Filtering (Optional Optimization on `GET /api/rides`):**
   - Support optional query parameter `minRatePerKmZar` (e.g. `GET /api/rides?role=driver&minRatePerKmZar=20`) so drivers only receive qualifying push/poll dispatches and save mobile data.
2. **Dynamic Market Benchmark Endpoint (Optional Phase 2): `GET /api/pricing/market-benchmark`**
   - Query params: `?originLat=-23.9045&originLng=29.4688&destLat=-23.8900&destLng=29.4500&distanceMeters=4800`
   - Response:
     ```json
     {
       "recommendedFareZar": 68.0,
       "acceptanceRateAtRecommended": 0.89,
       "peakMultiplier": 1.10,
       "activeDriversNearby": 14
     }
     ```

---

## 5. Cash Change Declaration, Cash-to-Wallet Bridge & Verified Rider Badge

### Android Behaviour
- **At booking (when `paymentMethod == "CASH"`):**
  - Rider selects `Exact cash`, `Change for R100`, or `Change for R200` (`cashChangeForZar: Int?`, `null` = exact cash).
- **On driver incoming offer & active sheet:**
  - Displays `"Bring change for R100"` / `"Bring change for R200"` chip.
  - Displays `"Verified rider · N rides"` shield badge when `riderVerified == true` or `riderCompletedRides >= 3`.
- **On driver post-trip summary (when `paymentMethod == "CASH"`):**
  - Driver can tap `R 10`, `R 20`, or `R 50` (or enter custom amount `1..500 ZAR`) to credit the rider's in-app wallet instead of handing over physical coins/notes.

### Backend Contract
1. **Extend `POST /api/rides` (Create Ride):**
   - Accept optional fields:
     ```json
     {
       "pickupNote": "Main gate · Call on arrival",
       "cashChangeForZar": 200
     }
     ```
   - Constraints:
     - `pickupNote`: optional string, trim and truncate to **max 140 chars**.
     - `cashChangeForZar`: optional nullable integer (`null`, `100`, `200`, or `50..500`); force `null` if `paymentMethod != "cash"`.
2. **Extend `RideTrip` Response (`GET /api/rides`, `POST /api/rides`, `GET /api/rides/{id}`):**
   - Include:
     ```json
     {
       "pickupNote": "Main gate · Call on arrival",
       "cashChangeForZar": 200,
       "riderCompletedRides": 12,
       "riderVerified": true
     }
     ```
   - Compute `riderVerified = true` when rider has verified phone/email AND zero unresolved chargeback/no-show flags AND `riderCompletedRides >= 3` (or SA ID/KYC verified).
3. **NEW Endpoint: `POST /api/wallet/cash-change-credit`**
   - **Auth:** Authenticated driver session (`Authorization: Bearer <token>`), must match `trip.driverId`.
   - **Preconditions:**
     - `trip.status == "completed"` and `trip.paymentMethod == "cash"`.
     - `100 <= amountMinor <= 50000` (R 1.00 to R 500.00).
     - Idempotent per `tripId` (reject duplicate credit for same `tripId` with `409 Conflict` or return existing ledger record cleanly).
   - **Request Body:**
     ```json
     {
       "tripId": "trip_abc123",
       "amountMinor": 2000,
       "note": "Cash trip change credited by driver"
     }
     ```
   - **Atomic Server Action:**
     1. Credit `amountMinor` (ZAR) to `trip.riderId` wallet ledger (`type = "cash_change_credit"`, `tripId`).
     2. Record matching debit/settlement deduction against `trip.driverId` payout balance / platform cash ledger.
     3. Send push notification + receipt to rider: `"Driver credited R 20.00 change to your Vhaya wallet"`.
   - **Response `200 OK`:**
     ```json
     {
       "creditedAmountMinor": 2000,
       "tripId": "trip_abc123",
       "wallet": {
         "balanceMinor": 4500,
         "currency": "ZAR"
       }
     }
     ```
   - **Android Resilience Note:** Until this endpoint is deployed, the Android app catches `404`/`405`/`501` and surfaces a clear info message without blocking trip completion.

---

## 6. 3-Minute Arrival Wait Timer & Mall/Complex Pickup Micro-Tags

### Android Behaviour
- **At booking:** Rider taps 1-tap micro-tags (`Main gate`, `Food court`, `Security boom`, `Call on arrival`) and/or types a short pickup landmark note (`max 140 chars`).
- **On `ARRIVED`:** Both rider and driver active sheets display a live **`Free wait 02:45`** countdown chip turning amber **`Wait exceeded +00:32`** after 3:00 (`180,000 ms`). Android uses `trip.driverArrivedAt ?: trip.arrivalConfirmedAt ?: localFallbackTimestamp`.

### Backend Contract
1. **Persist & Return `pickupNote`** on `POST /api/rides` and `GET /api/rides` (see §5.1–5.2).
2. **Return Server-Authoritative `driverArrivedAt` (epoch ms):**
   - When driver transitions trip to `status = "arrived"` (via `PATCH /api/rides/{id}` or `POST /api/rides/{id}/actions`), record server UTC timestamp `driverArrivedAt` (epoch milliseconds, e.g. `1763294400000`) and return it on every `GET /api/rides` response while `status` is `arrived`, `in_progress`, or `completed`.
   - *(Client compatibility note: Android accepts `driverArrivedAt` as epoch ms Long in JSON; also keep `arrivalConfirmedAt` for audit history.)*
3. **Driver No-Show Protection Rule:**
   - Once `now - driverArrivedAt >= 180_000` (3 minutes) and GPS shows driver remained within `150m` of `pickupCoordinates`, allow driver to cancel with reason `"Rider no-show after 3-min wait"` without harming driver acceptance/completion rating.

---

## 7. Docs & Permit Verified Shield, Wrong-Car Free Cancel & Auto-Evidence Support

### Android Behaviour
- **Driver Offer & Matched Driver Card:** Shows emerald **`Docs & Permit Verified`** badge when `driverProfile.operatingPermitVerified == true` or `driverProfile.verifiedDocsCount >= 3` or `driverVerified == true`.
- **Pre-boarding Wrong-Vehicle Free Cancel (`ACCEPTED` / `ARRIVED`):** 1-tap **"Wrong car · Cancel free"** action button inside the pickup PIN card cancels the ride immediately with `reason = "Vehicle or driver did not match app profile"` and escalates safely.
- **Auto-Evidence Support Cases:** `createSupportCase(...)` automatically appends a human-readable `[Trip Telemetry]` summary line to `message` AND attaches a structured `evidence` JSON object to the request payload.

### Backend Contract
1. **Extend `driverProfile` inside `DriverOffer` / `RideTrip` payloads:**
   ```json
   "driverProfile": {
     "driverId": "drv_42",
     "name": "Thabo M.",
     "rating": 4.92,
     "completedTrips": 418,
     "vehicle": "Toyota Corolla Quest (White)",
     "registration": "KBY 412 L",
     "verifiedDocsCount": 4,
     "operatingPermitVerified": true
   }
   ```
   - `verifiedDocsCount`: count of approved compliance documents (SA PrDP, Driver Licence, Vehicle Licence Disc, Operating Permit / Double-Disc).
   - `operatingPermitVerified`: `true` when provincial operating licence / metered-e-hailing permit is verified and unexpired.
2. **Wrong-Vehicle Cancel Waiver (`POST /api/rides/{id}/actions`, `action: "cancel"`):**
   - When `reason == "Vehicle or driver did not match app profile"` during `accepted` or `arrived`:
     - **Force `cancellationFeeMinor = 0`** for rider.
     - Automatically flag `driverId` for mandatory vehicle selfie + registration disc re-verification before next shift.
3. **Structured `evidence` Payload on `POST /api/rides/{id}/actions` (`action: "support"`):**
   - Accept optional `evidence` JSON object alongside `category`, `priority`, `message`, `requestRefund`, and `attachmentUrl`:
     ```json
     {
       "action": "support",
       "category": "fare_dispute",
       "priority": "normal",
       "requestRefund": true,
       "message": "Driver demanded extra cash\n\n[Trip Telemetry] id=trip_123 | status=completed | fare=R 85.00 | method=cash | payStatus=paid | route=6.4 km | arrival=gps_auto | pin=generated",
       "evidence": {
         "tripId": "trip_123",
         "rideStatus": "completed",
         "paymentMethod": "cash",
         "paymentStatus": "paid",
         "lockedFareZar": 85.0,
         "routeKm": 6.4,
         "arrivalMethod": "gps_auto",
         "pinIssued": true,
         "pickupNote": "Main gate",
         "cashChangeForZar": 100,
         "offRouteFlagged": false,
         "chatExtortionFlagged": true,
         "chatExtortionCue": "extra cash request"
       }
     }
     ```
   - Store `evidence` as queryable JSONB/object on the support ticket so agents see locked fare, route distance, PIN verification, GPS arrival proof, off-route status, and chat flags without asking the user to repeat details.

---

## 8. Rural Categories (`bakkie`, `parcel`)
- **What the Android app sends now:**
  - `POST /api/rides`: `category` may now be `"standard"`, `"comfort"`, `"xl"`, **`"bakkie"`** (`1.25x` estimate multiplier), or **`"parcel"`** (`0.85x` estimate multiplier, floored at `R 20.00`).
- **Expected Backend Support:**
  1. Allow `"bakkie"` and `"parcel"` in request validation and fare estimator multipliers.
  2. Filter or highlight `bakkie` requests for drivers whose vehicle profile supports bakkies/loads.

---

## 9. Shared Seats, Wait & Return & Gravel Road Flags
- **What the Android app sends now:**
  - `POST /api/rides`: optional rural trip flags:
    - `isSharedRide: boolean` (default `false`)
    - `seatCount: int` (`1..4`, default `1`)
    - `isReturnTrip: boolean` (default `false` — town wait-and-return round trip)
    - `includesGravelRoad: boolean` (default `false` — unpaved/village road surcharge)
- **Pricing Rule (Android Smart Offer Coach):**
  - `shared_seat`: scales fare by `(0.45 + 0.25 * seatCount).coerceAtMost(1.20)` when `isSharedRide == true` (`0.70x` for 1 seat, `0.95x` for 2 seats, `1.20x` for 3–4 seats).
  - `wait_and_return`: `1.85x` multiplier for town wait-and-return round trips.
  - `gravel_road`: `+12%` (`1.12x`) gravel wear-and-tear adjustment (supersedes rural deadhead).
  - `rural_deadhead`: `+8%` (`1.08x`) adjustment on trips starting/ending beyond `9 km` from Polokwane CBD or exceeding `12 km` total distance (when `includesGravelRoad == false`).
  - Floor: `R 30.00` for `bakkie`, `R 20.00` for all other categories.
- **Expected Backend Support:**
  1. Persist `is_shared_ride`, `seat_count`, `is_return_trip`, and `includes_gravel_road` on `rides` and echo them in `GET /api/rides` and `SingleTripEnvelope`.

---

## 10. Book for Someone Else (Family / Gogo Mode)
- **What the Android app sends & reads now:**
  - `POST /api/rides`: optional `beneficiaryName` (`String?`, max 80 chars) and `beneficiaryPhone` (`String?`, max 32 chars).
  - Active driver & rider screens surface the beneficiary contact with zero-permission `ACTION_DIAL` (`tel:`) and `ACTION_SENDTO` (`smsto:`) buttons so the booker can text the Pickup PIN (`tripPin`) directly to their family member.
- **Expected Backend Support:**
  1. Store `beneficiary_name` and `beneficiary_phone` on `rides` and return them in both rider and driver trip payloads.
  2. Optional SMS gateway enhancement: automatically send the 6-digit `tripPin`, plate, and vehicle description to `beneficiaryPhone` upon `accept` / `chooseOffer`.

---

## 11. Driver Gravel-Road Readiness (`gravelReady`)
- **What the Android app sends & reads now:**
  - `PATCH /POST /api/driver/application` now sends `gravelReady: boolean`.
  - `DriverProfileDto` / `DriverPublicProfile` reads `gravelReady: boolean?` (nullable/default `false`) to show a `"Gravel ready"` badge on driver offers and matched trip cards.
- **Expected Backend Support:**
  1. Store `gravel_ready BOOLEAN DEFAULT FALSE` on `driver_profiles` and include `gravelReady` in `DriverPublicProfile` projections inside `offers[]` and `trip.driverProfile`.

---

## 12. Spaza / Cash-Voucher Wallet Redemption (`POST /api/wallet/redeem-voucher`)
- **What the Android app calls now:**
  ```json
  POST /api/wallet/redeem-voucher
  Authorization: Bearer <idToken>
  {
    "code": "VHAYA-SPZA-9042"
  }
  ```
- **Expected Response (`WalletEnvelope` — HTTP 200):**
  ```json
  {
    "wallet": { "balanceMinor": 15000, "currency": "ZAR" },
    "transactions": [ ... ]
  }
  ```
- **Graceful Fallback:** While unimplemented (`404`/`405`/`501`), Android displays `"Spaza voucher redemption is not enabled on this server yet."`

---

## 13. Family Ride Credit Transfer (`POST /api/wallet/transfer`)
- **What the Android app calls now:**
  ```json
  POST /api/wallet/transfer
  Authorization: Bearer <idToken>
  {
    "recipient": "0821234567",
    "amountMinor": 5000,
    "note": "Ride credit"
  }
  ```
- **Expected Response (`WalletEnvelope` — HTTP 200):**
  - Atomically debits sender `wallet_accounts` by `amountMinor` (min `500` minor units = `R 5.00`), credits recipient resolved by phone or email, and returns updated sender `WalletEnvelope`.

---

## 14. Play Store Account Deletion & Legal Pages (`DELETE /api/account`, `/privacy`, `/delete-account`)
- **What the Android app calls now:**
  - When a user confirms **Delete account now** in `Account -> Privacy & POPIA`, the app calls:
    ```json
    DELETE /api/account
    Authorization: Bearer <idToken>
    {
      "reason": "Optional user reason"
    }
    ```
  - Following backend erasure (or fallback POPIA support ticket if unimplemented `404`/`405`/`501`), Android deletes the user's Firebase Identity Toolkit account (`accounts:delete`) and wipes all Room tables and local preferences.
- **Static Legal Pages Included (`/web-legal/`):**
  - Serve `web-legal/privacy-policy.html` at `GET /privacy` and `web-legal/delete-account.html` at `GET /delete-account` over HTTPS so the Android app links and Play Console Data Safety URLs resolve cleanly.

---

## 15. Offline-First Sync Idempotency (`POST /api/rides` -> `clientRequestId`)
- **What the Android app sends now:**
  ```json
  POST /api/rides
  Authorization: Bearer <idToken>
  {
    "clientRequestId": "550e8400-e29b-41d4-a716-446655440000",
    "pickup": "...",
    "destination": "...",
    "route": { "quoteToken": "..." }
  }
  ```
- **Why it matters:** When a rural rider creates a request in a dead zone, Android persists the optimistic ride in Room (`pending_ride_sync` + `trips` with `syncStatus = "PENDING_UPLOAD"`) and automatically retries when connectivity returns via `ConnectivityManager.NetworkCallback`.
- **Expected Server Behavior:** Deduplicate `POST /api/rides` by `(riderId, clientRequestId)` within a 24-hour window and echo `"clientRequestId"` back on serialized `RideTrip` objects so the Android client cleanly swaps its local placeholder without creating duplicate dispatches.

---

## Rollout Checklist for Backend Team
- [ ] Add nullable columns `pickup_note VARCHAR(140)`, `cash_change_for_zar INT`, `driver_arrived_at BIGINT`, `beneficiary_name VARCHAR(80)`, `beneficiary_phone VARCHAR(32)`, `is_shared_ride BOOLEAN DEFAULT FALSE`, `seat_count INT DEFAULT 1`, `is_return_trip BOOLEAN DEFAULT FALSE`, `includes_gravel_road BOOLEAN DEFAULT FALSE` to `rides` table.
- [ ] Accept `"bakkie"` and `"parcel"` in `POST /api/rides` validation and pricing calculators.
- [ ] Add `verified_docs_count INT`, `operating_permit_verified BOOLEAN`, `gravel_ready BOOLEAN DEFAULT FALSE` to driver profile projections; add `rider_completed_rides INT`, `rider_verified BOOLEAN` to ride dispatch projections.
- [ ] Waive cancellation fees on `"Driver stalled or moving away from pickup"` and `"Vehicle or driver did not match app profile"`.
- [ ] Implement `POST /api/wallet/cash-change-credit` with idempotency on `tripId`.
- [ ] Implement `POST /api/wallet/redeem-voucher` (single-use atomic voucher redemption) and `POST /api/wallet/transfer` (atomic P2P family credit transfer).
- [ ] Persist `evidence` JSON object on `POST /api/rides/{id}/actions` (`action: "support"`) and auto-escalate `chatExtortionFlagged == true`.
- [ ] Implement `DELETE /api/account` (delete user PII/tokens, anonymize required statutory tax/trip ledger rows) and host `/privacy` + `/delete-account` static HTML pages.
- [ ] Deduplicate `POST /api/rides` by `(riderId, clientRequestId)` and echo `clientRequestId` on returned `RideTrip` payloads for idempotent offline queue recovery.

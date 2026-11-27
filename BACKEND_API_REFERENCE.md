# Vhaya Android App — Complete Backend API Reference

This document lists **every HTTP endpoint called by the Vhaya Android app** (`VhayaApiClient.kt` + `WayfareRepository.kt`), along with exact headers, request payloads, response JSON envelopes, and enum values expected by `kotlinx.serialization`.

> **See also:** [`BACKEND_GTW_API.md`](./BACKEND_GTW_API.md) for the ready-to-paste PostgreSQL migration, polyline/corridor math (`services/gtwMatcher.js`), and Express handler (`routes/driverGtw.js`) for `/api/driver/gtw`.

---

## 1. Base URL, Headers & Conventions

- **Base URL:** Configured via `VHAYA_API_BASE_URL` (trailing `/` and `/api` are automatically stripped by the client, so routes always resolve as `<host>/api/...`).
- **Timeouts:** `15s` connect / `25s` read (`20s` / `45s` for document upload).
- **Timestamps:** All epoch timestamps (`createdAt`, `pickupAt`, `expiresAt`, `updatedAtMs`, `arriveByEpochMs`, etc.) are **Unix milliseconds (`Long`)**, unless explicitly noted as ISO date strings (`licenceExpiry`, `periodStart`, `periodEnd`).
- **Currency:** `ZAR` (`Double` in Rands for ride fares; `Long` in cents/minor units e.g. `19900` = `R 199.00` for wallet, payments, earnings, subscriptions, and payouts).

### Standard Request Headers
| Header | Value | Notes |
|---|---|---|
| `Accept` | `application/json` | Always sent |
| `User-Agent` | `VhayaAndroid/1.0` | Always sent |
| `Authorization` | `Bearer <firebase_id_token>` | Sent on all authenticated backend calls (optional on `/api/places/search` and `/api/places/reverse`) |
| `x-wayfare-session` | `<sessionId>` | Sent on all same-origin backend calls after `POST /api/auth/session/claim` (omitted on `session/claim` itself) |
| `Content-Type` | `application/json` | Sent when request has a JSON body (or file MIME type on document upload) |

### Error Envelope
Any non-`2xx` response is parsed by `extractApiError` in this order:
```json
{ "error": "Human-readable message" }
```
Also accepted:
- `{ "error": { "message": "..." } }` or `{ "error": { "error": "..." } }`
- `{ "message": "..." }`
- `{ "detail": "..." }`

---

## 2. Quick Endpoint Index (51 Backend Routes)

| Domain | Method & Path | Purpose |
|---|---|---|
| **Auth** | `POST /api/auth/session/claim` | Claim server session ID for Firebase user |
| **Auth** | `DELETE /api/auth/session` | Release server session on sign-out |
| **Account** | `DELETE /api/account` | Delete user account & data |
| **Places** | `POST /api/places/search` | Autocomplete / search places |
| **Places** | `POST /api/places/reverse` | Reverse geocode coordinates to label |
| **Routes** | `POST /api/routes/preview` | Route geometry, distance, ETA, fare estimate & `quoteToken` |
| **Rides** | `GET /api/rides` | Fetch user's role and visible trips |
| **Rides** | `POST /api/rides` | Create a ride / parcel request |
| **Rides** | `POST /api/rides/:tripId/actions` | Accept, counter, chooseOffer, flagOffer, confirmArrival, advance, cancel, rate, message |
| **Rides** | `PATCH /api/rides/:tripId/location` | Driver live trip GPS update |
| **Payments** | `POST /api/payments/initialize` | Start card hold/payment for trip |
| **Payments** | `POST /api/payments/mock-result` | Debug/dev mock payment outcome |
| **Payments** | `POST /api/payments/verify` | Verify card authorization/capture (`200..299` or `202`) |
| **Payments** | `POST /api/payments/release` | Release card hold on cancelled trip |
| **Wallet** | `GET /api/wallet` | Fetch wallet balance & ledger |
| **Wallet** | `POST /api/wallet/top-up` | Initialize wallet top-up |
| **Wallet** | `GET /api/wallet/top-ups/:reference/verify` | Verify top-up & return updated wallet |
| **Wallet** | `POST /api/wallet/cash-change-credit` | Driver credits rider's cash change to wallet |
| **Wallet** | `POST /api/wallet/redeem-voucher` | Redeem promo/voucher code |
| **Wallet** | `POST /api/wallet/transfer` | Transfer wallet credit to another user |
| **Driver** | `GET /api/driver/profile` | Fetch driver profile & compliance docs |
| **Driver** | `PATCH /api/driver/profile/online` | Toggle driver online/offline status |
| **Driver** | `PATCH /api/driver/profile/location` | Idle/standby driver GPS location update |
| **Driver** | `GET /api/driver/gtw` | Fetch active GTW corridor target |
| **Driver** | `PUT /api/driver/gtw` | Save/update active GTW corridor target |
| **Driver** | `DELETE /api/driver/gtw` | Clear active GTW corridor target |
| **Driver** | `POST /api/driver/application` | Submit driver onboarding application |
| **Driver** | `PATCH /api/driver/application` | Save driver onboarding draft |
| **Driver** | `POST /api/driver/documents?type=<docType>` | Upload raw binary KYC document |
| **Driver** | `GET /api/driver/earnings` | Fetch driver earnings & commission summary |
| **Driver** | `GET /api/driver/subscription` | Fetch active driver pass/subscription |
| **Driver** | `POST /api/driver/subscription/initialize` | Start driver subscription checkout |
| **Support** | `GET /api/support/cases` | Fetch current user's support cases |
| **Admin** | `GET /api/admin/overview` | Dashboard KPI summary counts |
| **Admin** | `GET /api/admin/activity?limit=50` | Recent platform audit/activity log |
| **Admin** | `GET /api/admin/people?q=&type=all&page=0&limit=50` | List user accounts |
| **Admin** | `GET /api/admin/driver-applications` | List driver applications for review |
| **Admin** | `PATCH /api/admin/people/:userId/compliance` | Update single document check status |
| **Admin** | `POST /api/admin/people/:userId/reviews` | Approve/reject/follow-up driver application |
| **Admin** | `PATCH /api/admin/people/:userId/role` | Change user role (`rider` / `driver` / `admin`) |
| **Admin** | `PATCH /api/admin/people/:userId/suspend` | Suspend or unsuspend user |
| **Admin** | `GET /api/admin/support-cases` | List all support cases |
| **Admin** | `PATCH /api/admin/support-cases/:caseId` | Update support case status & priority |
| **Admin** | `POST /api/admin/support-cases/:caseId/replies` | Post staff reply on support case |
| **Admin** | `GET /api/admin/payouts` | List recorded driver payouts |
| **Admin** | `POST /api/admin/payouts` | Record manual/external driver payout |
| **Admin** | `PATCH /api/admin/payouts/:payoutId` | Reconcile payout (`unreconciled`/`matched`/`exception`) |
| **Admin** | `GET /api/admin/payouts/batches` | List weekly payout batches |
| **Admin** | `POST /api/admin/payouts/run-weekly` | Generate weekly payout batch |
| **Admin** | `PATCH /api/admin/payouts/batches/:batchId/approve` | Approve weekly payout batch |
| **Admin** | `POST /api/admin/payouts/batches/:batchId/external-payment` | Confirm external payment for batch |

---

## 3. Auth & Account Endpoints

### `POST /api/auth/session/claim`
Called right after Firebase Sign-In / Sign-Up (`includeApiSession = false`).
- **Request Body:** None
- **Response (`200`):**
```json
{
  "sessionId": "sess_abc123"
}
```
> `sessionId` must be non-blank; the client stores it and sends it as `x-wayfare-session` on all subsequent requests.

### `DELETE /api/auth/session`
Called on user sign-out.
- **Request Body:** None
- **Response (`2xx`):** `{}` or `204 No Content`.

### `DELETE /api/account`
Called during POPIA / account deletion prior to deleting the Firebase Auth user. `404`, `405`, or `501` are treated gracefully as endpoint-unavailable.
- **Request Body:**
```json
{
  "confirmation": "DELETE",
  "reason": "Optional user reason (max 240 chars)"
}
```
- **Response (`2xx`):** `{ "deleted": true }` (or `204 No Content`).

---

## 4. Places & Route Preview Endpoints

### `POST /api/places/search`
Called during pickup/destination typing. If the backend returns `places: []` or errors (on non-loopback URLs), the app falls back to OpenStreetMap Nominatim.
- **Auth:** Optional (`Bearer` sent if signed in)
- **Request Body:**
```json
{
  "query": "Polokwane Hospital",
  "proximity": {
    "latitude": -23.9045,
    "longitude": 29.4689
  }
}
```
*(Note: `proximity` is omitted if user GPS is unavailable.)*
- **Response (`200`):**
```json
{
  "places": [
    {
      "id": "plc_123",
      "label": "Polokwane Provincial Hospital",
      "latitude": -23.9052,
      "longitude": 29.4614,
      "distanceMeters": 820.5,
      "provider": "mapbox",
      "category": "clinic"
    }
  ]
}
```
> Valid `category` hints: `"clinic"`, `"shopping"`, `"pension"`, `"transit"`, or `null`.

### `POST /api/places/reverse`
Called when pinning a pickup/destination on the map or using current GPS.
- **Auth:** Optional (`Bearer` sent if signed in)
- **Request Body:**
```json
{
  "coordinates": {
    "latitude": -23.9045,
    "longitude": 29.4689
  }
}
```
- **Response (`200`):**
```json
{
  "place": {
    "label": "14 Kerk St, Polokwane",
    "latitude": -23.9045,
    "longitude": 29.4689
  }
}
```
*(Note: `place` may be `null`.)*

### `POST /api/routes/preview`
Computes route distance, duration, encoded polyline, base fare estimate, and a `quoteToken` used when creating the ride.
- **Request Body:**
```json
{
  "pickup": "Seshego Zone 4",
  "destination": "Mall of the North",
  "pickupCoordinates": { "latitude": -23.841, "longitude": 29.389 },
  "destinationCoordinates": { "latitude": -23.881, "longitude": 29.512 }
}
```
*(Coordinate objects are omitted if not resolved yet.)*
- **Response (`200`):** Accepts either `{ "route": { ... } }` or root-level fields:
```json
{
  "route": {
    "distanceMeters": 14250,
    "durationSeconds": 1180,
    "encodedPolyline": "p~f~B...",
    "provider": "osrm",
    "fareEstimate": 145.0,
    "quoteToken": "qt_signed_or_uuid_token",
    "originCoordinates": { "latitude": -23.841, "longitude": 29.389 },
    "destinationCoordinates": { "latitude": -23.881, "longitude": 29.512 }
  }
}
```
> Also accepts `polyline` or GeoJSON `geometry` (`string` or `{ coordinates: [[lon, lat], ...] }`), `estimate`/`fare` for `fareEstimate`, and `pickupCoordinates` for `originCoordinates`.

---

## 5. Rides & Trip Lifecycle Endpoints

### `GET /api/rides`
Polled periodically (`Rider`/`Driver`/`Admin`) to sync active trips, open ride requests for drivers, offers, chat messages, and authoritative server role.
- **Response (`200`):**
```json
{
  "role": "rider",
  "trips": [ /* Array of RideTrip objects (see Section 12) */ ]
}
```
> `role` must be one of `"rider"`, `"driver"`, or `"admin"`.

### `POST /api/rides`
Creates a new ride or parcel delivery request.
- **Request Body:**
```json
{
  "pickup": "Seshego Zone 4",
  "destination": "Mall of the North",
  "category": "standard",
  "pricingMode": "estimate",
  "fare": 145.0,
  "paymentMethod": "cash",
  "pickupAt": 1764172800000,
  "pickupNote": "Wait by the main gate",
  "cashChangeForZar": 200,
  "beneficiaryName": "Gogo Maria",
  "beneficiaryPhone": "0821234567",
  "isSharedRide": true,
  "seatCount": 2,
  "isReturnTrip": false,
  "isReturnRequest": false,
  "includesGravelRoad": true,
  "clientRequestId": "offline_req_abc123",
  "route": {
    "quoteToken": "qt_signed_or_uuid_token"
  }
}
```
> Optional fields omitted when unset/false: `pickupAt` (null = immediate), `pickupNote` (max 140 chars), `cashChangeForZar` (`> 0`), `beneficiaryName` (max 80 chars), `beneficiaryPhone` (max 32 chars), `isSharedRide` + `seatCount` (`1..4`), `isReturnTrip`, `isReturnRequest`, `includesGravelRoad`, `clientRequestId` (for idempotent offline retry).
- **Enums:**
  - `category`: `"standard"` | `"comfort"` | `"xl"` | `"bakkie"` | `"parcel"`
  - `pricingMode`: `"estimate"` | `"offer"`
  - `paymentMethod`: `"cash"` | `"wallet"` | `"card"`
- **Response (`200`/`201`):**
```json
{
  "trip": { /* RideTrip object */ }
}
```

### `POST /api/rides/:tripId/actions`
Single multiplexed endpoint for all rider & driver mutations on a trip. Always returns `{ "trip": RideTrip }`.

| `action` | Who | Request Payload | Backend Behavior |
|---|---|---|---|
| `accept` | Driver | `{ "action": "accept", "location"?: { "latitude": Double, "longitude": Double } }` | Driver accepts at rider's fare; status -> `accepted`; generate 6-digit `tripPin`; store `location` if present |
| `counter` | Driver | `{ "action": "counter", "amount": 160.0 }` | Driver submits counter-offer (`>= 20.0`); status -> `countered`; appends `RideOffer` |
| `chooseOffer` | Rider | `{ "action": "chooseOffer", "offerId": "off_1" }` | Rider accepts a counter-offer; updates `fare`, `driverId`, `tripPin`, status -> `accepted` |
| `flagOffer` | Rider | `{ "action": "flagOffer", "offerId": "off_1", "reason": "..." }` | Flags/declines a suspicious counter-offer |
| `confirmArrival` | Rider | `{ "action": "confirmArrival" }` | Marks `arrivalConfirmationMethod = "rider"`, `arrivalConfirmedAt = now` |
| `advance` | Driver | `{ "action": "advance" }` | `accepted` -> `arrived` (sets `driverArrivedAt`); OR `in_progress` -> `completed` (triggers payment capture / earnings ledger) |
| `advance` (with PIN) | Driver | `{ "action": "advance", "pin": "482910" }` | Verifies 6-digit `tripPin`; `arrived` -> `in_progress` (returns `400`/`422` if PIN mismatch) |
| `cancel` | Rider / Driver | `{ "action": "cancel", "reason"?: "...", "waiveFee"?: true, "cancellationFeeZar"?: 20.0, "cancellationFeeMinor"?: 2000 }` | Cancels trip; status -> `cancelled`; releases card hold / applies cancellation fee if applicable |
| `rate` | Rider | `{ "action": "rate", "rating": 5 }` | Sets `riderRating` (`1..5`) |
| `message` (`driver` chat) | Rider / Driver | `{ "action": "message", "channel": "driver", "message": "I am outside" }` | Appends `RideMessage` on `"driver"` channel |
| `message` (`support` case) | Rider / Driver | `{ "action": "message", "channel": "support", "category": "safety", "priority": "urgent", "message": "...", "evidence"?: { ... } }` | Creates or replies to `SupportCase` on `"support"` channel; stores optional `evidence` telemetry object |

#### `evidence` Object Sent on New Support Case Creation
```json
{
  "tripId": "trip_123",
  "rideStatus": "in_progress",
  "category": "standard",
  "paymentMethod": "card",
  "paymentStatus": "authorized",
  "lockedFareZar": 145.0,
  "routeKm": 14.25,
  "arrivalMethod": "gps",
  "pinIssued": true,
  "pickupNote": "Main gate",
  "cashChangeForZar": 200,
  "includesGravelRoad": false,
  "isReturnTrip": false,
  "isReturnRequest": false,
  "isSharedRide": false,
  "seatCount": 1,
  "beneficiaryName": "Gogo Maria",
  "offRouteFlagged": false,
  "offRouteMeters": 650.0,
  "chatExtortionFlagged": false,
  "chatExtortionCue": "extra cash request"
}
```
*(Optional keys like `routeKm`, `arrivalMethod`, `pickupNote`, `cashChangeForZar`, `seatCount`, `beneficiaryName`, `offRouteMeters`, `chatExtortionCue` are omitted when null/false.)*

### `PATCH /api/rides/:tripId/location`
Called by the assigned driver during an active trip (`accepted`, `arrived`, `in_progress`).
- **Request Body:**
```json
{
  "location": {
    "latitude": -23.8992,
    "longitude": 29.4501
  }
}
```
- **Response (`2xx`):** `{ "ok": true }` (body ignored; client checks `200..299`).

---

## 6. Payments Endpoints (Card Holds & Paystack)

### `POST /api/payments/initialize`
Initializes card pre-authorization for a ride.
- **Request Body:** `{ "tripId": "trip_123" }`
- **Response (`200`):**
```json
{
  "provider": "paystack",
  "mode": "paystack",
  "authorizationUrl": "https://checkout.paystack.com/...",
  "payment": {
    "reference": "pay_ref_123",
    "amountMinor": 14500,
    "status": "pending"
  }
}
```
*(Note: `authorizationUrl` may be `null` in mock/test mode.)*

### `POST /api/payments/mock-result`
Used in development/mock payment mode only.
- **Request Body:** `{ "reference": "pay_ref_123", "success": true }`
- **Response (`200`):**
```json
{
  "payment": {
    "status": "authorized",
    "amountMinor": 14500
  }
}
```
> Valid `status`: `"authorized"`, `"paid"`, `"pending"`, `"failed"`.

### `POST /api/payments/verify`
Verifies card payment/hold status. Client accepts `200..299` **and `202`**.
- **Request Body:** `{ "reference": "pay_ref_123" }`
- **Response (`200` / `202`):**
```json
{
  "verified": true,
  "authorized": true,
  "payment": {
    "tripId": "trip_123",
    "amountMinor": 14500,
    "status": "authorized"
  }
}
```
> Client state mapping:
> - `status == "failed"` or `"expired"` -> `FAILED`
> - `verified == true && authorized == true` -> `AUTHORIZED`
> - `verified == true` -> `PAID`
> - otherwise -> `PENDING`

### `POST /api/payments/release`
Releases an authorized card hold (e.g., cancelled ride).
- **Request Body:** `{ "tripId": "trip_123" }`
- **Response (`2xx`):** `{}`

---

## 7. Wallet Endpoints

### `GET /api/wallet`
Fetches user's wallet balance and transaction history.
- **Response (`200`):**
```json
{
  "wallet": {
    "id": "wal_123",
    "userId": "uid_123",
    "balanceMinor": 25000,
    "currency": "ZAR"
  },
  "transactions": [
    {
      "id": "wtx_1",
      "type": "topup",
      "amountMinor": 25000,
      "currency": "ZAR",
      "reference": "topup_ref_1",
      "description": "Wallet top-up",
      "createdAt": 1764172800000
    }
  ]
}
```
> Transaction `type` values: `"topup"`, `"credit"`, `"debit"`, `"refund"`, `"payout"`.

### `POST /api/wallet/top-up`
Initializes a wallet top-up via Paystack.
- **Request Body:** `{ "amountMinor": 10000 }`
- **Response (`200`):**
```json
{
  "topup": {
    "reference": "topup_ref_1",
    "amountMinor": 10000,
    "authorizationUrl": "https://checkout.paystack.com/..."
  }
}
```

### `GET /api/wallet/top-ups/:reference/verify`
Verifies a wallet top-up and returns updated wallet balance.
- **Response (`200`):**
```json
{
  "wallet": {
    "id": "wal_123",
    "userId": "uid_123",
    "balanceMinor": 35000,
    "currency": "ZAR"
  }
}
```

### `POST /api/wallet/cash-change-credit`
Driver credits owed cash change from a cash trip directly to the rider's Vhaya Wallet.
- **Request Body:**
```json
{
  "tripId": "trip_123",
  "amountMinor": 5500,
  "note": "Change from R200 note"
}
```
- **Response (`200`):** `{ "wallet": { ... }, "transactions": [ ... ] }` (optional body; client reads `wallet` if present).

### `POST /api/wallet/redeem-voucher`
Redeems a wallet voucher/promo code (client uppercases & trims `code`).
- **Request Body:** `{ "code": "VHAYA50" }`
- **Response (`200`):** `{ "wallet": { ... }, "transactions": [ ... ] }`

### `POST /api/wallet/transfer`
Transfers wallet credit to another rider/driver by email, phone, or user ID.
- **Request Body:**
```json
{
  "recipient": "rider@example.com",
  "amountMinor": 5000,
  "note": "Split ride fare"
}
```
- **Response (`200`):** `{ "wallet": { ... }, "transactions": [ ... ] }`

---

## 8. Driver Endpoints

### `GET /api/driver/profile`
Fetches driver profile, onboarding status, uploaded document metadata, and compliance checks.
- **Response (`200`):** Accepts `{ "profile": DriverProfileDto | null }` (or `{ "application": DriverProfileDto | null }`).

### `PATCH /api/driver/profile/online`
Sets driver online/offline availability.
- **Request Body:** `{ "online": true }`
- **Response (`2xx`):** `{ "online": true }`

### `PATCH /api/driver/profile/location`
Updates driver GPS location while online/idle (not attached to a specific trip).
- **Request Body:**
```json
{
  "latitude": -23.9045,
  "longitude": 29.4689
}
```
- **Response (`2xx`):** `{}`

### `GET /api/driver/gtw`, `PUT /api/driver/gtw`, `DELETE /api/driver/gtw`
Going That Way (GTW) corridor target persistence. `404`, `405`, or `501` are handled gracefully by falling back to device-local storage.
- **See [`BACKEND_GTW_API.md`](./BACKEND_GTW_API.md)** for full SQL schema, matching engine, and Express routes.
- **`GET /api/driver/gtw` Response (`200`):**
```json
{
  "gtwTarget": {
    "label": "Polokwane CBD",
    "destination": { "latitude": -23.9045, "longitude": 29.4689 },
    "maxDetourKm": 6.0,
    "strictFilter": true,
    "updatedAtMs": 1764172800000,
    "arriveByEpochMs": 1764176400000,
    "expiresAtMs": 1764201600000
  }
}
```
*(Or `{ "gtwTarget": null }` when cleared/expired.)*
- **`PUT /api/driver/gtw` Request Body:**
```json
{
  "label": "Polokwane CBD",
  "destination": { "latitude": -23.9045, "longitude": 29.4689 },
  "maxDetourKm": 6.0,
  "strictFilter": true,
  "arriveByEpochMs": 1764176400000,
  "expiresAtMs": 1764201600000,
  "updatedAtMs": 1764172800000
}
```
*(Optional `arriveByEpochMs`, `expiresAtMs`, `updatedAtMs` omitted if null/0.)*
- **`DELETE /api/driver/gtw`:** Clears target; returns `200 { "gtwTarget": null }` or `204`.

### `POST /api/driver/application` & `PATCH /api/driver/application`
- `PATCH` = save draft; `POST` = submit application for review.
- **Request Body:**
```json
{
  "contactPhone": "0821234567",
  "city": "Polokwane",
  "vehicle": "Toyota Corolla Quest",
  "vehicleColor": "White",
  "registration": "ABC 123 L",
  "licenceExpiry": "2028-05-01",
  "operatingLicenceReference": "OL-99812",
  "gravelReady": true,
  "documentsConsent": true
}
```
*(`licenceExpiry`, `operatingLicenceReference` omitted if blank; `documentsConsent` only sent on `POST` submit.)*
- **Response (`200`):** `{ "profile": DriverProfileDto }` (or `{ "application": DriverProfileDto }`). If absent, client immediately calls `GET /api/driver/profile`.

### `POST /api/driver/documents?type=<docType>`
Raw binary file upload (`application/octet-stream`, `image/jpeg`, `image/png`, `application/pdf`, etc.).
- **Query Param:** `type` = `"identity"` | `"driver_license"` | `"vehicle_registration"` | `"operating_permit"`
- **Extra Header:** `x-document-name: <sanitized_filename>`
- **Body:** Raw file bytes (`ByteArray`)
- **Response (`200`):** `{ "profile": DriverProfileDto }` (or `2xx` followed by `GET /api/driver/profile`).

### `GET /api/driver/earnings`
- **Response (`200`):**
```json
{
  "driverId": "uid_123",
  "currency": "ZAR",
  "estimatedEarningsMinor": 145000,
  "paidEarningsMinor": 95000,
  "totalCommissionMinor": 14500,
  "totalNetMinor": 130500,
  "outstandingEarningsMinor": 35500,
  "rideCount": 12,
  "paidRideCount": 8,
  "records": [
    {
      "rideId": "trip_123",
      "pickup": "Seshego",
      "destination": "Polokwane CBD",
      "fareMinor": 12000,
      "currency": "ZAR",
      "completedAt": 1764172800000,
      "commissionMinor": 1200,
      "netMinor": 10800,
      "paidMinor": 10800
    }
  ]
}
```

### `GET /api/driver/subscription`
- **Response (`200`):**
```json
{
  "subscription": {
    "id": "sub_123",
    "provider": "paystack",
    "externalReference": "sub_ref_1",
    "amountMinor": 19900,
    "currency": "ZAR",
    "status": "active",
    "startedAt": 1764000000000,
    "expiresAt": 1764604800000
  }
}
```
*(`subscription` may be `null`.)*

### `POST /api/driver/subscription/initialize`
- **Request Body:** `{ "amountMinor": 19900 }`
- **Response (`200`):**
```json
{
  "provider": "paystack",
  "mode": "paystack",
  "authorizationUrl": "https://checkout.paystack.com/...",
  "payment": {
    "reference": "sub_ref_1",
    "amountMinor": 19900,
    "status": "pending"
  }
}
```

---

## 9. Support Endpoints

### `GET /api/support/cases`
Fetches support cases belonging to the signed-in user. *(To create or reply to a support case as a rider/driver, the app calls `POST /api/rides/:tripId/actions` with `action: "message"` and `channel: "support"`.)*
- **Response (`200`):**
```json
{
  "cases": [ /* Array of SupportCase objects */ ]
}
```

---

## 10. Admin Operations Endpoints

All `/api/admin/*` endpoints require an authenticated user whose role is `"admin"`.

### `GET /api/admin/overview`
- **Response (`200`):**
```json
{
  "summary": {
    "users": 420,
    "suspendedUsers": 3,
    "rideRequests": 14,
    "activeRides": 9,
    "pendingDriverReviews": 5,
    "paymentIssues": 2,
    "activeSupportCases": 6,
    "urgentSupportCases": 1
  }
}
```

### `GET /api/admin/activity?limit=50`
- **Response (`200`):**
```json
{
  "activity": [
    {
      "id": "act_1",
      "kind": "driver_review",
      "actor": "admin@vhaya.co.za",
      "subject": "uid_driver_1",
      "summary": "Approved driver application",
      "createdAt": 1764172800000
    }
  ]
}
```

### `GET /api/admin/people?q=&type=all&page=0&limit=50`
- **Response (`200`):**
```json
{
  "people": [
    {
      "userId": "uid_123",
      "displayName": "Thabo Mokoena",
      "email": "thabo@example.com",
      "role": "driver",
      "isSuspended": false,
      "suspensionReason": null,
      "rideCount": 34,
      "completedRideCount": 31
    }
  ]
}
```

### `GET /api/admin/driver-applications`
- **Response (`200`):**
```json
{
  "applications": [ /* Array of DriverProfileDto objects */ ]
}
```

### `PATCH /api/admin/people/:userId/compliance`
Updates a single compliance check on a driver application.
- **Request Body:**
```json
{
  "documentType": "operating_permit",
  "status": "verified",
  "note": "Verified against Limpopo permit registry"
}
```
> `documentType`: `"identity"` | `"driver_license"` | `"vehicle_registration"` | `"operating_permit"`
> `status`: `"not_reviewed"` | `"verified"` | `"needs_follow_up"` | `"not_required"`
- **Response (`2xx`):** `{}`

### `POST /api/admin/people/:userId/reviews`
Final decision on a driver's application.
- **Request Body:**
```json
{
  "subjectType": "driver",
  "outcome": "approved",
  "checksConfirmed": true,
  "note": "All 4 compliance documents verified"
}
```
> `outcome`: `"approved"` | `"needs_follow_up"` | `"rejected"`
- **Response (`2xx`):** `{}`

### `PATCH /api/admin/people/:userId/role`
- **Request Body:** `{ "role": "driver" }` (`"rider"` | `"driver"` | `"admin"`)
- **Response (`2xx`):** `{}`

### `PATCH /api/admin/people/:userId/suspend`
- **Request Body:**
```json
{
  "suspend": true,
  "reason": "Safety complaint pending review"
}
```
- **Response (`2xx`):** `{}`

### `GET /api/admin/support-cases`
- **Response (`200`):** `{ "cases": [ /* Array of SupportCase objects */ ] }`

### `PATCH /api/admin/support-cases/:caseId`
- **Request Body:**
```json
{
  "status": "in_progress",
  "priority": "high"
}
```
> `status`: `"open"` | `"in_progress"` | `"resolved"`
> `priority` (optional): `"normal"` | `"high"` | `"urgent"`
- **Response (`2xx`):** `{}`

### `POST /api/admin/support-cases/:caseId/replies`
- **Request Body:** `{ "message": "We have contacted the driver and issued a fare adjustment." }`
- **Response (`2xx`):** `{}`

### `GET /api/admin/payouts`
- **Response (`200`):**
```json
{
  "payouts": [
    {
      "id": 101,
      "driverId": "uid_driver_1",
      "driverName": "Thabo Mokoena",
      "provider": "bank_transfer",
      "externalReference": "EFT-2026-0091",
      "amountMinor": 95000,
      "currency": "ZAR",
      "periodLabel": "18 Nov – 24 Nov",
      "periodStart": "2026-11-18",
      "periodEnd": "2026-11-24",
      "reconciliationStatus": "matched",
      "note": "FNB batch EFT",
      "recordedAt": 1764172800000,
      "reconciledAt": 1764176400000
    }
  ]
}
```

### `POST /api/admin/payouts`
- **Request Body:**
```json
{
  "driverId": "uid_driver_1",
  "provider": "bank_transfer",
  "externalReference": "EFT-2026-0091",
  "amountMinor": 95000,
  "currency": "ZAR",
  "periodStart": "2026-11-18",
  "periodEnd": "2026-11-24",
  "note": "Settled manually",
  "confirmedExternalTransfer": true
}
```
- **Response (`2xx`):** `{}`

### `PATCH /api/admin/payouts/:payoutId`
- **Request Body:**
```json
{
  "reconciliationStatus": "matched",
  "note": "Verified on bank statement"
}
```
> `reconciliationStatus`: `"unreconciled"` | `"matched"` | `"exception"`
- **Response (`2xx`):** `{}`

### `GET /api/admin/payouts/batches`
- **Response (`200`):**
```json
{
  "batches": [
    {
      "id": "batch_2026w47",
      "periodLabel": "18 Nov – 24 Nov",
      "periodStart": 1763424000000,
      "periodEnd": 1764028800000,
      "status": "open",
      "totalAmountMinor": 420000,
      "driverCount": 14,
      "createdAt": 1764028900000,
      "paidAt": null
    }
  ]
}
```
> `status`: `"open"` | `"submitted"` | `"paid"` | `"reconciled"`

### `POST /api/admin/payouts/run-weekly`
- **Request Body:**
```json
{
  "periodStart": "2026-11-18",
  "periodEnd": "2026-11-24"
}
```
- **Response (`2xx`):** `{}`

### `PATCH /api/admin/payouts/batches/:batchId/approve`
- **Request Body:** None
- **Response (`2xx`):** `{}`

### `POST /api/admin/payouts/batches/:batchId/external-payment`
- **Request Body:** `{ "confirmed": true }`
- **Response (`2xx`):** `{}`

---

## 11. Canonical Object Schemas

### `RideTrip`
```json
{
  "id": "trip_123",
  "riderId": "uid_rider",
  "driverId": "uid_driver",
  "pickup": "Seshego Zone 4",
  "destination": "Mall of the North",
  "category": "standard",
  "currency": "ZAR",
  "fare": 145.0,
  "pricingMode": "estimate",
  "status": "accepted",
  "paymentStatus": "none",
  "paymentMethod": "cash",
  "paymentReference": null,
  "createdAt": 1764172000000,
  "pickupAt": null,
  "expiresAt": 1764172900000,
  "pickupCoordinates": { "latitude": -23.841, "longitude": 29.389 },
  "destinationCoordinates": { "latitude": -23.881, "longitude": 29.512 },
  "route": {
    "distanceMeters": 14250,
    "durationSeconds": 1180,
    "encodedPolyline": "p~f~B...",
    "provider": "osrm",
    "originCoordinates": { "latitude": -23.841, "longitude": 29.389 },
    "destinationCoordinates": { "latitude": -23.881, "longitude": 29.512 },
    "fareEstimate": 145.0,
    "quoteToken": "qt_123"
  },
  "offers": [
    {
      "id": "off_1",
      "tripId": "trip_123",
      "driverId": "uid_driver",
      "amount": 160.0,
      "status": "pending",
      "createdAt": 1764172050000,
      "driverProfile": { /* DriverProfileSnapshot */ }
    }
  ],
  "messages": [
    {
      "id": "msg_1",
      "tripId": "trip_123",
      "sender": "rider",
      "channel": "driver",
      "message": "Waiting at the gate",
      "createdAt": 1764172100000
    }
  ],
  "riderRating": null,
  "riderRatingTags": [],
  "tripPin": "482910",
  "arrivalConfirmationMethod": null,
  "arrivalConfirmedAt": null,
  "driverProfile": {
    "id": "uid_driver",
    "name": "Thabo M.",
    "vehicle": "Toyota Corolla",
    "vehicleColor": "White",
    "registration": "ABC 123 L",
    "fare": 145.0,
    "verificationStatus": "approved",
    "rating": 4.9,
    "completedRides": 84,
    "acceptanceRate": 0.95,
    "lastLocation": { "latitude": -23.860, "longitude": 29.410 },
    "verifiedDocsCount": 4,
    "operatingPermitVerified": true,
    "gravelReady": true,
    "homeArea": "Polokwane"
  },
  "liveDriverLocation": { "latitude": -23.860, "longitude": 29.410 },
  "unfulfillmentReason": null,
  "pickupNote": "Wait by the main gate",
  "cashChangeForZar": 200,
  "riderCompletedRides": 12,
  "riderVerified": true,
  "driverArrivedAt": null,
  "beneficiaryName": null,
  "beneficiaryPhone": null,
  "isSharedRide": false,
  "seatCount": 1,
  "isReturnTrip": false,
  "isReturnRequest": false,
  "includesGravelRoad": false,
  "clientRequestId": null
}
```
- **`RideStatus` values:** `"requested"`, `"countered"`, `"accepted"`, `"arrived"`, `"in_progress"`, `"completed"`, `"cancelled"`, `"unfulfilled"`
- **`PaymentStatus` values:** `"none"`, `"pending"`, `"authorized"`, `"capture_pending"`, `"capture_failed"`, `"release_pending"`, `"release_failed"`, `"paid"`, `"failed"`, `"released"`, `"expired"`, `"refunded"`

### `DriverProfileDto` (for `/api/driver/profile`, `/api/driver/application`, `/api/admin/driver-applications`)
```json
{
  "userId": "uid_driver",
  "displayName": "Thabo Mokoena",
  "email": "thabo@example.com",
  "contactPhone": "0821234567",
  "city": "Polokwane",
  "applicationStatus": "approved",
  "verificationStatus": "approved",
  "vehicle": "Toyota Corolla Quest",
  "vehicleColor": "White",
  "registration": "ABC 123 L",
  "licenceExpiry": "2028-05-01",
  "operatingLicenceReference": "OL-99812",
  "isOnline": true,
  "gravelReady": true,
  "staffNote": null,
  "submittedAt": 1764000000000,
  "reviewedAt": 1764050000000,
  "documents": [
    { "documentType": "identity" },
    { "documentType": "driver_license" },
    { "documentType": "vehicle_registration" },
    { "documentType": "operating_permit" }
  ],
  "complianceChecks": [
    {
      "userId": "uid_driver",
      "documentType": "identity",
      "status": "verified",
      "expiresOn": null,
      "staffNote": null,
      "reviewedAt": 1764050000000
    }
  ]
}
```
- **`applicationStatus`:** `"draft"` | `"submitted"` | `"under_review"` | `"needs_follow_up"` | `"approved"` | `"rejected"`
- **`verificationStatus`:** `"unverified"` | `"pending"` | `"approved"` | `"rejected"`

### `SupportCase`
```json
{
  "id": 42,
  "rideId": "trip_123",
  "reporterName": "Lerato",
  "category": "safety",
  "priority": "urgent",
  "status": "open",
  "assignedTo": "admin@vhaya.co.za",
  "staffNote": null,
  "message": "...",
  "pickup": "Seshego Zone 4",
  "destination": "Mall of the North",
  "rideStatus": "in_progress",
  "createdAt": 1764172800000,
  "updatedAt": 1764172900000,
  "replies": [
    {
      "id": "rep_1",
      "caseId": 42,
      "senderLabel": "Vhaya Support",
      "message": "Checking with the driver now.",
      "createdAt": 1764172900000
    }
  ]
}
```

---

## 12. External Third-Party APIs Called Directly by Android (No Backend Proxy Required)

1. **Firebase Identity Toolkit & Secure Token API** (`identitytoolkit.googleapis.com`, `securetoken.googleapis.com`) — Email/password sign-in, sign-up, Google ID token sign-in, password reset, email verification, token refresh, account deletion.
2. **OpenStreetMap Overpass** (`https://overpass-api.de/api/interpreter`) — Nearby clinics, malls, paypoints, and taxi ranks.
3. **OpenStreetMap Nominatim** (`https://nominatim.openstreetmap.org/search`) — Client-side fallback when `POST /api/places/search` returns empty.
4. **OSRM Public Routing** (`https://router.project-osrm.org/route/v1/driving/...`) — Visual road polyline & turn-by-turn navigation steps (`NavigationStep`).
5. **OSM Tile Server** (`OfflineMapTileCache.kt`) — Raster map tile caching for offline corridor coverage.

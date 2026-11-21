# Wayfare — To-do

Use this file as the working checklist for Wayfare. Check it before starting new work and update it as items are completed or priorities change.

## Current baseline

- [x] Build the native Expo app with Firebase sign-in and server-returned rider, driver, and operations roles.
- [x] Add server-backed ride requests with category-based demo fares or rider-entered offers.
- [x] Add server-backed driver acceptance, counteroffers, trip status updates, and shared trip history.
- [x] Add foreground pickup location, manual pickup entry, ride sharing, and ratings.
- [x] Add a server-protected operations overview, driver application review, and staff account provisioning.

## Competitor research — gaps to consider

Checked 18 October 2026 against official Uber, Bolt, and inDrive feature pages. This is a focused feature-gap list, not a promise that every feature is available in every country. Competitor features and payment methods vary by market.

### Already represented in Wayfare

- Rider-set fares and driver counteroffers (inDrive-style); fares remain illustrative, while requests and offers are stored server-side.
- Basic ride categories, server-saved trip status and ratings, and local trusted-contact/safety panels.
- A selectable currency code and foreground pickup location; neither is connected to market pricing or live mapping.

### Core ride-hailing prototype status

- [x] **Scheduled/reserved rides — server-backed prototype:** riders can book 30 minutes to 90 days ahead, schedule device-local reminders, cancel, and view server-updated status. Drivers and staff see shared request changes through polling.
- [x] **Choose between driver offers — server-backed prototype:** approved online drivers can submit offers, and riders can compare live driver profiles, vehicles, and fares. Real ETAs are not connected.
- [x] **Driver request controls — server-backed prototype:** approved online drivers see shared pickup and destination details before accepting and can skip without affecting other drivers. This is an address preview, not live routing.
- [x] **Live trip safety toolkit — local prototype:** includes trusted-contact setup, a trip-sharing preview, vehicle/driver details, a 112 dial action, and local support screens. It does not share live location, notify contacts, dispatch emergency help, or reach staffed support.
- [x] **Identity and trip verification — staff-reviewed prototype:** drivers submit vehicle and licence details, staff record verification decisions and grant driver access, and a pickup PIN gates trip start. Document upload and third-party checks are not connected.
- [x] **Private communication and support — server-saved prototype:** in-app messages and support requests are stored without exposing personal phone numbers. Messages are not delivered, and the support inbox is not staffed.
- [x] **Rural Return Rides — Android companion:** 8 km return-corridor matching, Return Request priority, discounted backhaul fares, approaching-driver hints, queued trip chaining, Room/offline persistence, and Android tests are reported complete.
- [x] **Return Ride API round-trip:** preserve `isReturnRequest` during `POST /rides` and return it in the authenticated rider and driver `GET /rides` feeds; older records default to `false`.

### Market-dependent or later expansion

- [ ] **Special ride preferences:** assess options such as women-requesting-women rides only after checking local demand, legal requirements, driver availability, and the safety implications.
- [ ] **Airport trips:** add airport pickup/drop-off guidance and supported airport locations if the launch market needs them.
- [ ] **Beyond city rides:** evaluate scheduled intercity trips and parcel/courier delivery only after the core ride marketplace is working. Uber, Bolt, and inDrive each describe additional ride or delivery services, but availability differs by country.
- [ ] **Market-specific payment choices:** support the methods actually used in the launch market (for example cash, cards, or supported mobile payments), with clear reserved-ride and cancellation rules.

### Official sources

- [Uber — rider safety](https://www.uber.com/us/en/ride/safety/) — emergency button, trip details, trusted contacts, driver screening, and urgent safety support.
- [Uber — ride options and Reserve](https://www.uber.com/us/en/ride/) — advance reservations and additional ride/delivery options.
- [Bolt — ride safety](https://bolt.eu/en/rides/safety/) — emergency assist, live location sharing, women-for-women, support, number privacy, and identity checks.
- [Bolt — ride options and availability](https://bolt.eu/en-is/rides) — reservations, payment options, and market-availability caveat.
- [Bolt — reservation timing and trip handling](https://bolt.eu/en/support/articles/7392556548114) — advance-booking window, reminders/status, cancellation, and support.
- [inDrive — city rides](https://indrive.com/city-rides) — rider fare offers, driver counteroffers, driver comparison, and identity checks.
- [inDrive — driver features](https://indrive.com/en/driver) — route visibility, request choice, safety, and support.
- [inDrive — safety](https://indrive.com/safety) — passenger/driver safety responsibilities, trusted contacts, and emergency help.

### South Africa-specific findings

South Africa is the selected launch country. Polokwane is the first launch city, with kilometres as the distance unit and **ZAR** as the launch currency. The exact service boundary and operating rules still need to be established.

- **Uber South Africa:** its local pages advertise advance booking, airport rides, rider emergency assistance, trusted contacts, driver screening, and 24/7 driver support. Confirm which features are enabled in the chosen city before promising them.
- **Bolt South Africa:** its local pages advertise advance booking, emergency assistance, live trip sharing, 24/7 support, and a Women for Women ride option; Bolt states some features vary by country. Its local driver guides also cover vehicle categories, document checks, inspections, airport waiting areas, ratings, and driver payments.
- **inDrive South Africa:** its local pages advertise fare offers, driver choice, verified drivers, and no surge pricing. It also advertises card payments, city-to-city rides, and delivery/courier services.
- **Wayfare implications:** scheduled rides, driver comparison, verification, real safety/support, and payments are relevant to the SA comparison. Intercity and delivery should remain optional until the first city and core service are established. Do not treat another platform's driver rules as South African legal advice.

South African source pages:

- [Uber South Africa — rider safety features](https://www.uber.com/za/en/ride/safety/rider-safety-features)
- [Uber South Africa — rides and services](https://www.uber.com/za/en/)
- [Uber South Africa — driver features and support](https://www.uber.com/za/en/drive)
- [Bolt South Africa — rides, booking, and safety](https://bolt.eu/en-za/rides)
- [Bolt South Africa — driver guides and vehicle checks](https://bolt.eu/en-za/driver/guide/)
- [Bolt South Africa — driver safety](https://bolt.eu/en-sa/driver/safety)
- [inDrive South Africa — city rides](https://indrive.com/en-za/city-rides)
- [inDrive South Africa — safety](https://indrive.com/en-za/safety)
- [inDrive South Africa — cashless/card payments](https://indrive.com/en-za/cashless)
- [inDrive South Africa — city-to-city rides](https://indrive.com/en-za/intercity-rides)

## Next — prepare the launch service

### 1. Decide the launch market and operating rules

- [x] Confirm launch country: South Africa; planned launch currency: ZAR.
- [x] Confirm first launch city and units: Polokwane; kilometres. Map the exact operating boundary when the map/routing provider is selected.
- [ ] Confirm ride categories, South African fare rules, cash/card availability, driver onboarding, and cancellation rules. The app now records the existing demo behavior as draft settings only; confirm market rules before treating them as launch policy.
- [ ] Verify current South African e-hailing, vehicle, driver-permit, airport, and municipal requirements with the relevant authorities or qualified local advisers.
- [x] Select OpenStreetMap/Nominatim for maps and place search, and OSRM for routing.
- [x] Select card payments through Paystack for ZAR; use mock outcomes during development.
- [ ] Use the competitor gap list above to select an initial market-appropriate feature set; defer optional services until the core rides launch.

### 2. Add real accounts and secure role permissions

- [x] Add Firebase email/password sign-in and account creation; register from the app with your own email and password (there are no shared demo credentials). New accounts default to rider.
- [x] Read roles from trusted Firestore profiles through the API; users cannot choose or grant themselves driver or operations access.
- [x] Build the prototype driver application, staff review, verification status, and role-grant workflow; real document checks remain a launch blocker.
- [x] Finish the protected operations overview and staff provisioning workflow in the prototype.
- [x] Resolve account-verification profile loading stalls with fresh API responses and a retryable timeout.
- [x] Show new accounts the rider home first; keep driver onboarding behind an explicit opt-in.

### 3. Share trip data across devices

- [x] Store users, driver profiles, ride requests, offers, and trip status on the server for the current prototype.
- [x] Let approved drivers see rider requests from their own devices through the server-backed prototype.
- [x] Synchronize trip changes across rider, driver, and operations prototype clients through polling.
- [x] Keep prior unowned device-only trips untouched and hidden; new local ride state is keyed by authenticated user and is not migrated.

### 4. Connect real maps, routing, and dispatch

- [x] Replace the illustrative route graphic with a live OpenStreetMap map.
- [x] Add address search, pickup/drop-off pins, route distance, and route-duration estimate.
- [ ] Calculate fare estimates from the route and the confirmed market rules.
- [x] Use server-backed offers from eligible online drivers and synchronize acceptance and trip status across rider, driver, and operations prototype clients.
- [ ] Connect real ETAs; the current prototype does not provide live arrival estimates.
- [x] Add a local pickup/destination preview and an explicit driver skip action with a no-penalty prototype consequence.
- [x] Connect driver skip/decline to shared dispatch without removing requests from other eligible drivers.
- [x] Replace the address-only preview with live route data and map-based routing.
- [x] Add clear permission-denied and manual-address flows.

### 4a. Add scheduled rides

- [x] Let riders choose a future pickup date and time; show the local reservation confirmation and schedule native-device reminders.
- [x] Add local cancellation and driver-unavailable states. Keep the no-driver path and status changes synchronized once server dispatch is connected.
- [ ] Apply the launch market's reservation window and payment rules.

### 5. Add payments and driver settlements

- [x] Implement Paystack card checkout for completed rides with development-only mock success/failure outcomes.
- [ ] Record the final agreed fare, payment status, receipts, and refunds/cancellations.
  - [x] Persist final fare, payment status, and verified receipt reference.
  - [ ] Refunds: eligibility + audit trail implemented (`POST /admin/payments/:paymentId/refund`); provider-backed completion pending payment provider decision.
- [x] Show driver earnings and settlement status based on completed paid trips.
  - [x] Server: `GET /driver/earnings` distinguishes estimated vs paid earnings with source records.

### 6. Complete safety and support flows

- [ ] Verify rider and driver identity and documents through a staff-reviewed service; keep sample vehicle details visibly unverified.
  - [ ] Blocked on document storage provider + verification provider decision. No document upload or third-party checks connected.
- [x] Add the local pickup PIN gate and a 112 dial action.
- [ ] Connect emergency escalation and staffed support before presenting either as a live response service.
  - [x] Staff replies stored; rider support-case list (`GET /support/cases`) added; staff notes private.
  - [ ] Delivery channel (in-app polling vs push) still needs confirmation.
- [x] Add trusted-contact setup, trip-sharing preview, and local in-app messages that keep phone numbers out of the chat UI.
- [x] Share assigned driver foreground GPS during an in-progress ride and show the live marker on the requester's road map.
- [x] Show the driver road guidance to pickup and the trip destination, with location permission and foreground-sharing status.
- [x] Add an operations snapshot with live account, ride, driver-review, payment, and support totals, plus 30-day support breakdowns and recent staff activity.
- [ ] Add background driver location sharing and notify trusted contacts during a shared trip; keep phone numbers private in any real communication service.
- [ ] Add trip issue reporting and post-trip support.
- [ ] Review location sharing, retention, and user privacy controls.
- [ ] Evaluate special ride preferences, such as women-requesting-women, for the confirmed market before adding them.

### 7. Prepare for launch

- [ ] Test the full rider-to-driver trip flow on iOS and Android, including failures and cancellations.
- [ ] Test role access, location permission states, offline behavior, and payment outcomes.
- [ ] Add monitoring, support operations, and market-specific app policies.
- [ ] Publish only after live services and safety-critical flows are verified.

### 8. Maps provider abstraction (plan: Workstream 1 / Phase 1)

- [x] Replace direct public Nominatim + Project OSRM usage with a server-side `MapsProvider` abstraction (`artifacts/api-server/src/lib/maps/index.ts`) so vendors can be replaced independently.
- [x] Implement the `OpenStreetMapProvider` (`artifacts/api-server/src/lib/maps/openstreetmap.ts`) wrapping places search, geocoding, reverse geocoding, and routing with bounded retries, jitter, circuit breaker, timeouts, and provider health checks.
- [x] Wire `POST /places/search` and `POST /places/reverse` (`routes/places-search.ts`) and `POST /routes/preview` (`routes/routes-preview.ts`) through `getMapsProvider()`; provider selection via `MAPS_PROVIDER`, credentials server-side only.
- [x] Loosened `routeQuote.verifyRouteQuote` provider check to any non-empty provider string so signed route quotes are vendor-agnostic while keeping HMAC tamper protection.
- [x] `pnpm typecheck` for `@workspace/api-server` passes.

### 9. Realtime transport, normalized messaging, push registry (plan: Workstreams 4/5, Phase 1)

- [x] `lib/notifications.ts` — server notification event pipeline: active push-token lookup, type-safe ride/support event payloads with deep-link contract (`wayfare://ride/<id>`), delivery-audit via `auditEvents`, and a pluggable `deliverToTokens` integration point for a real push provider (FCM/APNS).
- [x] `routes/push-tokens.ts` — `POST/GET/DELETE /push-tokens`: register/upsert, list, and invalidate device push tokens (platform: ios/android/web, opt-in).
- [x] `routes/conversations.ts` — `POST/GET /conversations` and `GET /conversations/:id`: create conversations with participants, list user's conversations, access-controlled conversation detail (participants only).
- [x] `routes/messages.ts` — `POST /conversations/:id/messages` (send), `GET .../messages` (cursor pagination via `before`), `PATCH /messages/:id` (delivery-state transitions: sent/delivered/read/failed).
- [x] Wired all three new routers into `routes/index.ts`.
- [x] `pnpm typecheck` for `@workspace/api-server` passes.

### 10. Operations map for online drivers

- [x] Add a staff-only live map for approved drivers who are currently online; only fresh locations are plotted.
- [x] Store only the latest online location, clear it when a driver goes offline or loses eligibility, and withhold coordinates after 90 seconds.
- [ ] Verify the working Android companion sends foreground location updates while online and appears on the admin map.


# Wayfare

A ride-service backend with Firebase identity, PostgreSQL operations data, ride management, route estimates, and trip tracking APIs.

## Run & Operate

- `pnpm --filter @workspace/api-server run dev` — run the shared API used by Wayfare on the configured port
- `pnpm run typecheck` — typecheck the workspace
- The workspace-root `.env` is loaded by the API development server.
- Android and backend source are kept in separate folders and are not launched or built together.

## Stack

- pnpm workspace and TypeScript
- Express API, PostgreSQL, and Drizzle; the API verifies Firebase ID tokens and verified email addresses

## Where things live

- `backend/api-server/src/middlewares/firebaseIdentity.ts` — Firebase token verification and user provisioning
- `backend/api-server/src/routes/` — authenticated ride, route, and payment APIs
- `backend/db/src/schema/index.ts` — shared PostgreSQL schema
- `backend/api-spec/openapi.yaml` — API contract; the Android app belongs in `android/` and connects over HTTP

## Architecture decisions

- Firebase Authentication is the app's only sign-in provider. The API verifies Firebase ID tokens and verified emails, uses Firebase UIDs as Wayfare account IDs, and reads trusted roles from Firestore profiles.
- Each Firebase account is limited to one active device session. Signing in on another device replaces the previous session; the earlier device is signed out when it next checks with the API.
- PostgreSQL remains authoritative for rides, payments, wallets, and operational records; `wayfare_users` is a server-maintained projection of Firebase identity. Existing account and ride records are preserved and are not automatically linked by matching email.
- The API authorizes rider, driver, and operations-admin actions from server-managed Firestore roles; clients never choose or grant a role. Bootstrap the first operations admin with the server-only `WAYFARE_INITIAL_ADMIN_EMAILS` allowlist, then use protected admin provisioning. The last admin cannot remove their own or another administrator's access.
- Backend admin endpoints support driver-application review, document-compliance checks, account provisioning, internal support cases, and external payout reconciliation. Staff review pending driver applications and record checks for documents received outside Wayfare, such as by email; no document upload is required or available. Application approval remains a separate staff decision after the required checks are complete.
- Trusted contacts and reservation reminders remain device-specific. Riders can open a user-confirmed share sheet with a trip snapshot; live contact tracking and emergency dispatch are not connected. Third-party identity checks are not connected.
- The R20/km route estimate and rider-entered offers are draft values, not market-calibrated prices. Cancellation fees are disabled until local rules are approved.
- Keep OpenStreetMap as Wayfare's map base; do not reintroduce Google Maps. The current stack uses OSM tiles, Nominatim search/reverse geocoding, and OSRM routing; keep providers replaceable. OSM Foundation's public tiles and Nominatim have usage limits and best-effort availability, so assess a scalable OSM-derived service or self-hosting before public launch.
- Pickup and destination search use OpenStreetMap Nominatim; route previews use OSRM. The API requires arrival confirmation and a one-use pickup PIN before a trip can start. Driver location is shared with the rider only during an active assigned trip through an authenticated API; updates are foreground-only and the rider polls every five seconds. This backend provides route guidance data, not turn-by-turn navigation or live contact tracking.
- Paystack checkout requires a verified account email. Development defaults to mock payment outcomes; driver settlements and refunds are not connected.

## Product

Backend source and backend-only workspace packages live under `backend/`. The Android app has its own source/build environment under `android/`; it does not import backend packages or run as part of the backend service.

The API supports immediate and scheduled ride requests, eligible cancellations, trip PINs, route previews, rider and driver trip states, driver-interest submissions, document-compliance tracking, support cases, payment visibility, and external payout recording/reconciliation. Driver dispatch and live offers remain unavailable in this preview.

Ride records are stored on the server and scoped by the verified Firebase UID. Route previews depend on the public OSRM service. Driver identity, vehicle, and permit checks must be completed manually from documents emailed to `hlulanigo@gmail.com` before staff approves an application; document upload, live contact tracking, delivered messaging, staffed support, automated payout transfers, refunds, and emergency dispatch are not connected. Admin support cases are internal records only, and support messages are kept private from the other trip participant.

The staff-only Drivers map lists approved, unsuspended drivers while online. The server stores one current foreground location per online driver, clears it when they go offline or lose eligibility, and exposes coordinates to staff only when the latest update is no more than 90 seconds old. Locations older than five minutes are cleared when the admin feed runs. The user confirmed the Android companion is already working and is maintained in its own project. Treat it as an existing client, not a missing feature; verify that its online-location updates reach this admin feed before treating map integration as complete.

The Android companion also has a tested Rural Return Ride feature: it matches requests along an outbound driver's return corridor, offers discounted backhaul pricing, and queues the next trip. The API preserves `isReturnRequest` through `POST /api/rides` and returns it in the authenticated rider and driver `GET /api/rides` feeds; older records default to `false`. Android client UI and end-to-end validation remain in the separate Android project.

## User preferences

Use an original Wayfare identity and combine familiar ride-hailing patterns without copying another app's branding or exact screens.

## Gotchas

- Be explicit that shared trips use five-second polling rather than push; fares remain illustrative, cancellation fees are disabled pending approval, and refunds/driver settlements, document verification, staffed support, and emergency response are not connected. Mock payments do not collect money. Trip sharing sends only a user-approved snapshot, not live location.
- The API development service runs `pnpm --filter @workspace/db run push` before it starts accepting requests. The dashboard waits for API readiness and its initial account data before rendering; production schema changes remain on Replit's Publish flow.
- For the first admin login, set `WAYFARE_INITIAL_ADMIN_EMAILS` on the API server to one or more verified Firebase email addresses (comma-, semicolon-, or whitespace-separated). Role changes are stored server-side and are never accepted from sign-up or client metadata.

## Pointers

- See the `pnpm-workspace` skill for workspace structure, TypeScript setup, and package details

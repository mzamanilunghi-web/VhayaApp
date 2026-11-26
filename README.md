# Vhaya (Android)

Native Kotlin and Jetpack Compose ride-request, driver dispatch, and operations oversight application for Polokwane and South Africa.

## Core Features

- **Authentication & Role Routing**: Firebase Email/Password sign-in, 1-tap Google Sign-In via Jetpack Credential Manager, account creation, email verification gate, password reset, single-active-device session enforcement, and automatic role routing across **Requester**, **Driver**, and **Admin** experiences.
- **Requester (Rider) Flow**: Street-level OpenStreetMap canvas (`OsmRouteMap`) with interactive pin picking, place search, route previews, fare estimates & custom fare offers, smart offer acceptance coach, rural ride options (`bakkie`, `parcel`, shared seats, wait-and-return, gravel road, return ride matching, book for someone else), scheduled pickups, Paystack card/wallet/cash payment options (with cash change declaration & Spaza voucher / P2P family credit), live driver tracking, anti-stall & off-route safety guards, 6-digit pickup PIN verification, in-trip driver messaging with anti-extortion chat guard, support case reporting, and post-trip ratings.
- **Driver Flow**: Onboarding application with vehicle catalog search, SA registration formatting, licence expiry tracking, and compliance status tracking; online/offline dispatch toggle with foreground live sync, incoming ride request alerts with counter-offer/accept actions, minimum `R/km` filter, heading-home corridor & hazard reporting, active trip progression with turn-by-turn banner & voice/chime alerts, 3-minute arrival wait timer, Weekly Driver Pass subscription, and earnings analytics.
- **Admin Operations Console**: Platform KPIs, activity log, user & role management, suspension controls, driver compliance & application reviews, support case replies/triage, and driver payout recording & weekly batch reconciliation.
- **Offline Caching & POPIA Privacy**: Local Room database (`WayfareDatabase`) with offline sync queue (`OfflineSyncCoordinator` + WorkManager), offline map tile cache, POPIA personal data JSON export, and self-service account deletion.

## Configuration

Configure the following environment variables in the **Secrets** panel in AI Studio (or in a local `.env` file, never committed):

- `VHAYA_API_BASE_URL`: Base URL of the existing Vhaya backend API server (verifies Firebase ID tokens and serves roles, rides, wallet, and driver/admin APIs).
- `FIREBASE_WEB_API_KEY`: Firebase Web API key used for Firebase Auth (`accounts:createAuthUri` Google Client ID discovery, Google Sign-In, email/password, verification, and password reset), falling back to `app/google-services.json` if omitted.

## Release safety

- Mock payment UI and result calls are enabled only in debug builds (`ALLOW_MOCK_PAYMENTS`). Release builds fail closed if the payment provider does not return a checkout URL; the backend must also reject mock results in production.
- Release APK/AAB packaging is blocked until a dedicated release keystore is available. Configure `VHAYA_RELEASE_KEYSTORE_PATH`, `VHAYA_RELEASE_KEYSTORE_PASSWORD`, `VHAYA_RELEASE_KEY_ALIAS`, and `VHAYA_RELEASE_KEY_PASSWORD` in the build environment.

## Build & Run

- Built with Gradle (Kotlin DSL), AGP 9.1.1, Kotlin 2.3.20, Jetpack Compose, Material 3, Room (KSP), WorkManager, Kotlinx Serialization, Credential Manager, and Coil.
- Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`.
- Unit & Robolectric tests: `gradle :app:testDebugUnitTest`.

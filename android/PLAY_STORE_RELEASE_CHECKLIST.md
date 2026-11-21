# Vhaya Google Play Store Release Checklist

## 1. Environment & Release Signing (`.aab`)
Before building the production Android App Bundle (`.aab`), configure these secrets in your build environment (AI Studio **Secrets** panel or CI/CD):

- `VHAYA_API_BASE_URL` = `https://your-production-domain.co.za` (HTTPS required)
- `FIREBASE_WEB_API_KEY` = Your production Firebase Web API key
- `VHAYA_RELEASE_KEYSTORE_PATH` = Absolute path to your `.jks`/`.keystore` upload key (outside source control)
- `VHAYA_RELEASE_KEYSTORE_PASSWORD`
- `VHAYA_RELEASE_KEY_ALIAS`
- `VHAYA_RELEASE_KEY_PASSWORD`

Generate signed Play Store bundle:
```bash
gradle :app:bundleRelease
```
Output: `app/build/outputs/bundle/release/app-release.aab` (with `ALLOW_MOCK_PAYMENTS = false` and `isMinifyEnabled = true`).

> Confirm `applicationId` (`com.wayfare.ride` in `app/build.gradle.kts`) is your final Play Console package name before your very first upload—Google Play locks it permanently after publishing.

---

## 2. Host Legal & Account Deletion Pages
Deploy the two files in `/web-legal/` over HTTPS on your domain:
- `https://<your-domain>/privacy` -> `web-legal/privacy-policy.html`
- `https://<your-domain>/delete-account` -> `web-legal/delete-account.html`

The Android app automatically links to these endpoints on the Sign-In/Create Account screen and inside `Account -> Privacy & POPIA`.

---

## 3. Google Play Console App Content Declarations
- **Privacy Policy URL:** `https://<your-domain>/privacy`
- **Account Deletion URL (Data Safety):** `https://<your-domain>/delete-account`
  - **In-app deletion supported:** **Yes** (`Account -> Privacy & POPIA -> Delete account or wipe data -> Delete account now`).
- **Data Safety Collected Types:**
  - **Personal Info:** Name, Email address, Phone number (account auth, driver/rider coordination, optional beneficiary contact).
  - **Location:** Precise & Approximate location (pickup pinning, fare calculation, active trip safety tracking; optional for user, encrypted in transit).
  - **Financial Info:** Purchase/wallet history & Paystack transaction references (card CVVs not stored by Vhaya).
  - **Messages & Photos:** In-trip chat messages & user-chosen support attachments (zero-permission Photo Picker).
  - **Data Sold / Ads:** No data sold, no third-party advertising SDKs.
- **Foreground Services Declaration (`App Content -> Foreground services`):**
  - **`location` (`RideTrackingService`):** Core ride-hailing safety feature requiring continuous GPS updates with a persistent user-visible notification during an active trip or while a driver chooses to remain Online for dispatch.
  - **`dataSync` (`VhayaLiveSyncService`):** Synchronizes live ride offers, PIN verification status, and driver-rider dispatch updates while a trip or driver shift is active.
- **App Access (Play Reviewer Credentials):**
  - Create one verified **Rider** test account and one approved **Driver** test account in your production backend, and enter both email/password pairs under **Play Console -> App content -> App access** so automated and human reviewers can test booking, driver dispatch, and self-service account deletion without registering a real vehicle.

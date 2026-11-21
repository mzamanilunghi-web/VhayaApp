# Vhaya Frontend UX Simplification Plan

A complete, role-by-role plan to declutter Vhaya's Compose interface using progressive disclosure, clearer visual hierarchy, and consistent 48dp touch targets—without removing any existing rider, driver, rural, or safety capabilities.

## User Review & Critical Decisions

> [!IMPORTANT]
> **Confirmed Phase 1 Decisions**:
> - **Scope**: Cover all user-facing areas (Auth, Rider, Driver, Account/Settings, and Admin).
> - **Information Architecture**: Use **progressive disclosure** (collapse advanced options cleanly behind summary rows and expandable drawers rather than hiding features in separate screens or keeping every control expanded).
> - **Deliverable**: Complete UX audit and actionable build plan ready for approval.
> - **Google Sign-In Integration**: Include a 1-tap **"Continue with Google"** button on the Auth screen alongside the simplified email/password flow.

- **Open Choice 1 — Preserve Existing Automation Tags**: Keep all existing Compose test tags and accessibility labels intact while reorganizing layout hierarchy so existing Robolectric UI tests continue to pass (Recommended).
- **Open Choice 2 — Google Sign-In Web Client ID**: The existing codebase implements Firebase Identity Toolkit email/password endpoints (`FIREBASE_WEB_API_KEY`), so enabling live Google Sign-In requires a `GOOGLE_WEB_CLIENT_ID` configured via Secrets/`BuildConfig` (and the Google Sign-In provider enabled in Firebase Auth). While `GOOGLE_WEB_CLIENT_ID` is empty, the button can surface a clear setup message or trigger once configured.

---

## 1. Overview & Core Concept

- **What It Does**: Streamlines Vhaya's screens so first-time and everyday riders and drivers see only the next essential decision on screen, while power-user and rural-specific tools (Return Request, Shared Seat Split, Gravel Road, Beneficiary Booking, Corridor Filter, Road Hazards, Offline Map Packs, Spaza Vouchers) stay one tap away.
- **Target Audience / Persona**: Everyday commuters, rural passengers, and drivers in Limpopo/Polokwane operating under variable signal, one-handed phone use, and time-sensitive dispatch countdowns.
- **Key Value**: Cuts visual noise in half on the Rider booking sheet and Driver map overlay, reduces accidental taps, increases visible map area, and standardizes all interactive controls to accessible `48.dp` targets.

### Complete Frontend Simplification Inventory (All Roles)

#### A. Authentication & Verification
1. **Add 1-Tap "Continue with Google" Button**: Place a clean, full-width `"Continue with Google"` button at the top of the Auth card (above an `"or"` divider) on both **Sign in** and **Create account** modes—eliminating typing, password creation, and email-verification waiting for Google users.
2. **Remove duplicate Sign-In / Create-Account switcher**: Currently shows both a segmented pill at the top of the card and a redundant text-button switcher at the bottom. Keep only the top segmented pill (plus `Forgot password?`).
3. **Progressive password checklist**: Hide the 4-rule password checklist card until the password field is focused or non-empty during account creation, preventing a tall wall of checks on first impression.
4. **Compact hero card proportions**: Reduce the hero header minimum height so the Google Sign-In button, email/password inputs, and primary action button fit comfortably above the keyboard on smaller phones.
5. **Crisper CTA copy**: Shorten `"Create account & verify email"` to `"Create account"` with a short single-line note explaining the verification email.

#### B. Rider Flow (Map, Search, Booking, Active Trip, Wallet, Trips)
6. **Streamline the Compact Home Sheet**: Combine the separate Saved/Recent/Nearby horizontal row, the Rural Presets horizontal row, and the fallback Pin-on-Map/Saved-Places tiles into two crisp layers: a prominent `"Where to?"` + `"Later"` action bar and one unified `"Quick destinations & rural runs"` carousel (with Pin-on-Map placed neatly inside the search bar/header).
7. **Unify Pickup Editing**: Clarify the top GPS pickup pill so tapping it opens the same connected `Pickup -> Destination` planner with pickup pre-focused, avoiding confusion between top chrome and bottom sheet inputs.
8. **Declutter Search Sheet Empty State**: Keep the `Pickup -> Destination` spine pinned at the top, place `"Pin on map"` cleanly inside the destination row, and collapse Saved / Recent / Nearby sections with clear counts rather than stacking long ungrouped lists.
9. **Simplify the Ride & Fare Sheet (`ChooseRideAndFareSheet`)**: Currently stacks 6+ heavy cards in a scrollable sheet. Reorganize into **4 clean blocks**:
   - **Route Header**: Destination title, distance/ETA, bookmark toggle, and `Now / +30m / +1h` chip.
   - **Vehicle Carousel**: Compact horizontal cards (`Standard`, `Comfort`, `Bakkie`, `Parcel`) with upfront fare.
   - **Collapsible Price & Options Strip**:
     - Collapse the multi-line `FareEstimateSummaryCard` into a single tappable summary row (`Est. R 55 · 4.2 km · Tap for breakdown`) that expands line items on demand.
     - Group `Return Request (-18%)` and `Trip options & pickup note` (Shared seats + fare split, Wait & return, Gravel road, Book for family, Pickup tags/notes) into a single `"Customize ride"` expandable row showing active summary chips when collapsed.
     - Keep `Your offer price` as a compact `R 55` stepper pill; reveal the `Smart Offer Coach` card only when the user edits the price or enters a low offer.
   - **Payment & Primary CTA**: Keep `Cash / Wallet / Card` pills compact, hide `Cash change` options behind a single `"Need change?"` chip when Cash is active, and pin the primary Request button at the bottom of the sheet.
10. **De-clutter Active Rider Trip Sheet**: Once a driver is matched, collapse secondary trip-option badges (`Gravel`, `Wait & return`, `Return Request`, `For: Family`) under an expandable `"Trip details"` chip so **Driver info, Live ETA progress, 4-digit Pickup PIN, Chat, and Safety/112** dominate the sheet.
11. **Simplify Rider Wallet Tab**: Keep the Wallet card, instant Top-Up chips (`R50`, `R100`, `R250`, `Custom`), and Transaction Statement upfront. Move `Redeem Spaza Voucher` and `Send Family Credit` into two compact action tiles that expand their forms inline when tapped, and group `Money in / Money out` into a single subtle summary strip.

#### C. Driver Flow (Map Overlay, Dispatch Cards, Active Trip, Onboarding)
12. **Reduce Floating Top Map Overlays**: Currently stacks up to 5 separate floating rows over the map (`Account + Online Bar`, `Location Banner`, `Map Feature Pills`, `Incoming Alert Pill`, `Demand Summary Pill` / `Turn-by-Turn Card`).
    - Merge `IncomingRequestAlertPill` and `LiveDemandSummaryPill` into one contextual status pill (showing only the highest-priority state).
    - Consolidate `Offline maps` into Driver Profile/Settings (or a compact `"Map tools"` overflow sheet) so the top pill row only holds high-frequency live driving controls (`Heading Home` + `Report hazard`).
13. **Tuck Min-Rate Filter Behind a Chip**: Move the 4-chip `Min rate filter (R/km)` row in the Offline and Scanning sheets into a single compact filter chip (`Filter: All rides` / `R 12+/km`) that expands on tap, keeping `"Ready to drive"` and `"Scanning"` sheets much shorter so more of the map stays visible.
14. **Improve Incoming Ride Offer Card Scannability**: Cap the `FlowRow` badge cluster to at most 3 high-signal tags (`Pickup ETA`, `Trip Distance`, `Cash Change / Return Request`), keep the bold Fare + `R/km` + Paid-vs-Deadhead bar as the visual focal point, and place secondary tags inside an optional details toggle.
15. **Simplify Driver Active Trip & Post-Trip Summary**: Group secondary actions (`Share trip`, `Alert contacts`, `Report issue`, `Cancel ride`) consistently inside the `"More"` menu while keeping `Chat`, `Navigate`, and the primary stage button (`Arrived` / `Enter PIN` / `Complete`) large and unmistakable.

#### D. Account, Preferences & Admin Console
16. **Organize Account & Settings Sheet**: Currently mixes 9 flat sections in one long scroll. Group into 4 scannable blocks:
    - **Profile & Driver CTA** (Header + `Apply to drive` if Rider).
    - **My Places & Trusted Drivers** (Compact collapsible rows showing item counts and expanding on tap).
    - **App Preferences Card** (Compact segmented rows for `Theme`, `Language`, `Alert Sound`, and `Low-Data Map`).
    - **Privacy & Sign Out** (`Privacy & POPIA` + `Sign out`).
17. **Clean Up Admin User & Support Cards**: On `Operations` user cards, move the 3-role `FilterChip` row and `Suspend/Restore` button into the tap-to-open person detail sheet (or a single `"Manage"` action) so each user row scans cleanly like a contact card.
18. **Enforce 48dp Minimum Touch Targets Everywhere**: Upgrade undersized `40.dp` / `36.dp` interactive surfaces (top bar account pill, driver map feature pills, pickup GPS button, and inline reset buttons) to at least `48.dp` height/touch area.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  - **1-Tap Sign-In / Onboarding**: Open Auth card → Tap `"Continue with Google"` for instant verified access via Android Credential Manager (or use the simplified Email/Password card with progressive password strength feedback).
  - **Rider Booking**: Open app → Tap `"Where to?"` (or one quick-place/rural-run chip) → Pick destination → Review Vehicle + Price + Payment in a non-cramped 4-block sheet (expand `"Customize ride"` or `"Fare breakdown"` only if needed) → Tap Request.
  - **Driver Dispatch**: Toggle Online in a clean map view → See one contextual status pill + compact Scanning sheet → Receive high-contrast Offer Card with Price, Paid/Deadhead split, A→B route, and 1-tap `Accept / Counter / Decline`.
  - **Account & Wallet**: Open clean grouped cards where secondary tools (vouchers, family credit, saved lists, preferences) expand cleanly on demand.
- **Visual Identity & Theme**:
  - *Aesthetic Direction*: High-clarity, utilitarian mobility UI tuned for bright South African sunlight and dark-mode night driving, preserving Vhaya's existing brand palette and typography.
  - *Color Palette & Mood*: Preserve existing `MaterialTheme.colorScheme` and `LocalWayfareColors` semantic tokens (`primary`, `surface`, `surfaceVariant`, `positive` emerald, `warning` amber, `error` crimson) without restyling brand identity.
  - *Typography & Hierarchy*: Preserve bundled `Plus Jakarta Sans` / `Inter` headings & body with `JetBrains Mono` (`WayfareMonoFamily`) strictly for fares, distances, `R/km`, PINs, and license plates so numbers align and scan instantaneously.
- **Component Styling & Layout**:
  - Consistent `16.dp`–`24.dp` rounded containers, `1.dp` `outlineVariant` borders, generous `12.dp`–`16.dp` vertical spacing, and `widthIn(max = 600.dp)` centering for tablets/foldables.
  - Replace stacked inline forms with compact accordion rows (`AnimatedVisibility` + `ExpandMore/ExpandLess`) showing a 1-line summary of active selections when collapsed.
- **Interactive Feedback & Motion**:
  - Smooth `AnimatedVisibility` expand/collapse transitions under `220ms`, existing `tactilePressScale()` on primary actions, and haptic feedback on booking/accept/status transitions.
  - Keep `48.dp` minimum interactive touch targets and full `contentDescription` / `testTag` coverage across every simplified control.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1 — Progressive Disclosure Inside Existing Sheets vs. New Sub-Screens**
  - *Chosen Approach*: Collapse secondary options (fare line-items, rural trip extras, cash change, voucher/family wallet forms, driver rate filter, account preference groups) into 1-tap expandable rows inside existing sheets/tabs.
  - *Why*: Reduces initial visual density by ~40–50% while keeping every workflow fast (1 extra tap, zero navigation disorientation) and preserving existing screen routes and test flows.
  - *Alternatives Considered*: Splitting every option into multi-step wizard pages—rejected because it adds unnecessary screen transitions for frequent commuters.

- **Decision 2 — Prioritizing Top-of-Map Real Estate for Drivers & Riders**
  - *Chosen Approach*: Merge overlapping status pills into a single contextual slot and relocate infrequent configuration (`Offline maps` manager, `Min rate` chip row) out of always-visible overlays/sheets.
  - *Why*: Drivers and riders rely on the live street map for pickup alignment and safety; clearing stacked top banners prevents map occlusion on standard 6-inch screens.
  - *Alternatives Considered*: Shrinking fonts or paddings—rejected because small text and sub-48dp pills harm readability and driving safety.

- **Decision 3 — Retaining All Existing Domain Logic & Test Tags**
  - *Chosen Approach*: Pure presentation-layer refactoring inside existing Compose UI components without altering ViewModel state contracts or deleting `Modifier.testTag(...)` identifiers.
  - *Why*: Ensures zero regression across offline sync, Paystack checkout, POPIA export, fare estimation, and Robolectric test suites.
  - *Alternatives Considered*: Rewriting navigation or state models—rejected as out of scope and risky for stability.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architecture & Component Diagram**:

```
┌──────────────────────────────────────────────────────────────────────┐
│                        WayfareUiState (ViewModel)                    │
│  authState · booking · riderActiveTrip · driverActiveTrip · prefs    │
└───────────────┬───────────────────┬───────────────────┬──────────────┘
                │                   │                   │
                ▼                   ▼                   ▼
┌───────────────────────┐ ┌───────────────────┐ ┌──────────────────────┐
│      Auth Surface     │ │   Rider Surface   │ │    Driver Surface    │
│ ┌───────────────────┐ │ │ ┌───────────────┐ │ │ ┌──────────────────┐ │
│ │ Compact Hero      │ │ │ │ Unified Home  │ │ │ │ 1-Slot Top Pill  │ │
│ │ Google Sign-In    │ │ │ │ & Quick Strip │ │ │ │ + Map Tool Pills │ │
│ │ (Credential Mgr)  │ │ │ └───────┬───────┘ │ │ └────────┬─────────┘ │
│ │ + Segmented Email │ │ │         ▼         │ │          ▼           │
│ │   Auth + Checks   │ │ │ ┌───────────────┐ │ │ ┌──────────────────┐ │
│ └───────────────────┘ │ │ │ 4-Block Ride  │ │ │ │ Compact Scanning │ │
                          │ │ Sheet         │ │ │ │ + Collapsible    │ │
                          │ │ · Header      │ │ │ │   R/km Filter    │ │
                          │ │ · Vehicles    │ │ │ │ + 3-Tier Offer   │ │
                          │ │ · Expandable  │ │ │ │   Card           │ │
                          │ │   Fare/Extras │ │ │ └──────────────────┘ │
                          │ │ · Pay & CTA   │ │ └──────────────────────┘
                          │ └───────────────┘ │
                          └─────────┬─────────┘
                                    │
                                    ▼
                    ┌───────────────────────────────┐
                    │   Shared Account & Secondary  │
                    │  · Grouped Preferences Card   │
                    │  · Expandable Saved/Trusted   │
                    │  · Action-Gated Wallet Tools  │
                    │  · Clean Admin Detail Actions │
                    └───────────────────────────────┘
```

- **Data Model & State**:
  - All persistent and domain state remains unchanged in `WayfareUiState` (`BookingDraft`, `RideTrip`, `DriverApplication`, `PrivacyPreferences`, `WalletTransaction`).
  - UI-only expansion toggles (`showBreakdown`, `showCustomizeRide`, `showRateFilter`, `showVoucherDrawer`, `showFamilyTransferDrawer`, `showTripMetaDetails`, `showPreferencesGroup`) are managed via local `remember` / `rememberSaveable` state inside their respective Composables—auto-expanding automatically if a pre-selected state or error requires user attention (e.g., `hasTripExtras == true`, `lowWallet == true`, or `offerTier == LOW`).

- **Interactive Component & State Mapping**:
  - **Auth Card & Google Sign-In**: `"Continue with Google"` invokes Jetpack `CredentialManager` (`GetSignInWithGoogleOption` / `GetGoogleIdOption`), extracts the Google ID token, exchanges it with Firebase Identity Toolkit (`accounts:signInWithIdp` with `providerId=google.com`), and routes the verified `UserSession` into `fetchRidesAndRole()`—bypassing the email verification gate. Meanwhile, the top segmented pill switches `SIGN_IN` / `CREATE_ACCOUNT` and triggers `clearAuthMessages()`, and password input smoothly reveals `PasswordPolicyChecklistCard`.
  - **Rider Compact & Choose Sheets**: Quick chips (`Saved`, `Recent`, `Rural Presets`) populate `BookingDraft` in one tap; `FareEstimateSummaryCard` toggles between compact 1-line summary and full line-item breakdown; `"Customize ride"` row hosts `Return Request`, `Shared Ride + Split`, `Wait & Return`, `Gravel`, `Beneficiary`, and `Pickup Note` while displaying live active-chip summaries when collapsed.
  - **Rider Wallet & Active Trip**: `Voucher` and `Family Credit` action tiles toggle inline drawers with existing validation and ViewModel handlers; `RiderActiveTripSheet` collapses non-critical tags behind a `"Details"` toggle after driver assignment while keeping `PIN`, `ETA`, `Chat`, and `112` visible.
  - **Driver Map & Dispatch**: Top map overlay renders a single priority status pill (`Alert` > `Turn-by-Turn` > `Demand Summary`); `MinRateFilterStrip` collapses into a summary filter chip when idle; `DriverIncomingOfferCard` keeps top 3 tags visible and moves overflow tags behind an inline toggle.
  - **Account & Admin**: `AccountAndPlacesSheet` groups Theme, Language, Sound, and Low-Data into a clean Preferences card and collapses Saved Places / Trusted Drivers behind count headers; Admin user cards open `PersonHistoryDetailSheet` for role and suspension actions.

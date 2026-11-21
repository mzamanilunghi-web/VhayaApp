---
name: Wayfare ride flow
description: Product guidance for how Wayfare presents the rider's request, driver match, payment, and ride.
---

Wayfare's rider journey should make matching, driver confirmation, card authorization, and live ride tracking feel like distinct steps instead of putting them all into one dense active-trip screen. Keep secondary trip details, safety, and support available without crowding the main step.

**Why:** The user said the combined screen makes the driver response, waiting state, and payment too easy to miss and asked for a process that feels more like native ride-hailing apps.

**How to apply:** Future changes to Wayfare's active ride experience should preserve the staged progression and keep driver/payment status prominent at the moment the rider needs it.

For map-led mobile work, the user wants a full-screen map with actions layered above it rather than primary ride tasks buried in a long scrolling page. The user chose one changing sheet over the map for rider booking steps, a medium-height status-and-cancel sheet while matching, a compact tap-to-expand sheet when a driver accepts, one-level expansion when the driver arrives, a compact map-first sheet during the ride, a full-height Safety & share sheet over the map, and full-screen driver-chat/support threads. The user instructed us on 2026-11-03 to apply documented recommendations without repeated approval prompts and preserve existing behavior.

**Why:** The user said Wayfare is intended to be a native app, wants the map to use the full screen, and asked to discuss the page-by-page display plan before changing the app.

**How to apply:** Use `displayMobile.md` as the implementation guide. Keep the rider booking map sheet's content changing through its steps; keep root destinations and long lists as navigable screens where appropriate. Apply its recommendations while preserving existing logic and permissions; ask only if a change cannot be safely inferred.

The user chose an Apple-style mobile interface with restrained Linux-inspired utility accents. Apply this visual direction to each phase's completed screens before marking that phase complete; do not extend it to unfinished phases early.

**Why:** The user selected the combined style and asked for theme completion to be tracked in every phase.

**How to apply:** Keep future phase styling scoped to that phase, reuse the established visual direction, and preserve existing behavior.

The rider booking process should present route, ride choice and fare, pickup timing, and final review as separate sequential pages rather than one long screen.

**Why:** The user asked for booking decisions to be separated into pages, following the step-by-step process familiar from other ride-hailing apps.

**How to apply:** Keep each page focused on one decision, show clear progress, and preserve back-navigation and entered values between steps.

Rider navigation should remain available across Ride, Trips, Support, and Wallet while a ride is active. Open Trips when a new active ride first appears, but do not trap the rider there.

**Why:** The user asked to improve navigation between rider sections while keeping the active ride easy to find.

**How to apply:** Keep the active-ride request guard, show the active ride in Trips, and let the rider switch sections afterward.

When a rider reports a driver from the matching list, remove that driver from the current request and send a support report; do not block them from the rider's future requests.

**Why:** The user chose current-request removal and reporting rather than a future-request block.

**How to apply:** Keep this action scoped to the active request unless the user explicitly asks to add a persistent block.

The rider's booking entry should request device location automatically, show the rider on the full-screen map, and use one search field for the destination. Include a route-swap control for requests starting somewhere other than the rider's current location.

**Why:** The user asked to reduce the route-entry clutter while still supporting rides requested for someone at another pickup.

**How to apply:** Keep the current-location pickup as the default, preserve a manual-location fallback when permission is unavailable, and swap the selected route endpoints without losing their labels or coordinates.

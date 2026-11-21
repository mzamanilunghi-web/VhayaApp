---
name: Wayfare Return Rides
description: The Android companion's completed rural Return Ride behavior and the API compatibility requirement.
---

The user supplied the completed Android feature summary: Return Ride matching scans open requests during an active outbound trip, matches pickups within 8 km of the drop-off or return corridor when the destination progresses toward the outbound origin, and prioritizes explicit Return Requests. Backhaul pricing is about 18% below the standard/rural fare, with an R12/km floor and R20 standard / R30 bakkie minimums. The feature includes approaching-driver hints, queued next trips, counteroffers, offline Room persistence, and reported passing Android tests.

**Why:** the Android app is maintained separately, and the user confirmed this is already built; these product rules are not recoverable from the admin/API repository.

**How to apply:** do not recreate the Android feature. When evaluating or extending this backend, preserve `isReturnRequest` through server ride creation and the open-ride response, then verify the Android-to-API-to-driver round trip before calling the feature integrated.

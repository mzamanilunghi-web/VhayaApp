---
name: Managed Expo preview workflow
description: How this project's Expo artifact workflow is named in the Replit workspace
---

Use the exact managed workflow name shown in the current workflow list before starting or restarting the app. This project's Expo service is registered with the full `Wayfare-Mobile/artifacts/wayfare: expo` name; do not create a shorter alias when that managed service exists.

After `REPLIT_EXPO_SESSION_SECRET` is added or changed, restart this workflow so `create-launch login` runs with the current configuration. Verify the device connection by checking that the Expo Go manifest and its JavaScript bundle both return HTTP 200.

**Why:** The artifact workflow can appear after the artifact metadata is registered and uses the repository path as part of its name. Creating a short alias can start a duplicate server on the same preview port. A process started before the Expo session setting changes does not pick up the new login configuration.

**How to apply:** Check the live workflow list first, then restart the existing managed workflow instead of configuring a replacement.

For Replit's browser artifact preview, use Expo's web mode; keep the native/tunnel path separate because it requires an authenticated Expo session.

**Why:** The managed native tunnel failed without Expo login, while the web mode rendered the sign-in screen through the Replit preview.

**How to apply:** When the goal is browser-based artifact testing, point the managed app start at web mode and verify both the root page and `/api/healthz`. Keep Expo Go setup separate for physical-device testing.

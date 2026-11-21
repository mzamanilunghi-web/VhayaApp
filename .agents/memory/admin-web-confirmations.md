---
name: Admin web confirmations
description: Confirmation UI behavior shared by Wayfare's native app and Expo web preview
---

Use an in-app `Modal` for admin confirmations that must work in the Expo web preview. `Alert.alert` is native-only here; on web it can leave admin actions such as granting driver access or cancelling a ride with no visible confirmation and no action.

**Why:** The admin approval and ride-cancellation actions appeared unresponsive in the browser because their callback was only reachable through a native alert.

**How to apply:** When adding or changing web-visible admin actions, verify their confirmation flow uses app-rendered UI rather than `Alert.alert`.

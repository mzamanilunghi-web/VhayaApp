---
name: Wayfare Android project boundary
description: Which project owns the Android rider app and how it connects to this backend.
---

The user confirmed that Wayfare has a working Android companion app maintained in a separate project. Treat the Android implementation as existing; do not ask to import its source as a prerequisite for backend or admin work.

**Why:** the user said the Android app already works and accompanies this Wayfare app; the separate-project boundary describes source ownership, not a missing client.

**How to apply:** keep backend and admin work here, leave Android UI/build changes in its existing project, and verify compatibility through shared API behavior. When adding a contract, check whether the working client uses it before calling the client missing or complete.

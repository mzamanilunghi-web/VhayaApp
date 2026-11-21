---
name: Wayfare authentication and storage direction
description: User-confirmed boundary between Wayfare authentication, PostgreSQL, and Firestore.
---

Firebase Auth is the sign-in provider, and Firestore is authoritative for Firebase identity profiles and roles. PostgreSQL remains authoritative for rides and other operations; its user rows are a relational projection. Wayfare uses the `irgadgetsofficialweb` Firebase project in `Wayfare-Mobile/firebase.md`; the Admin SDK service account and Expo client settings must match it. See `Wayfare-Mobile/auth.md`.

Refreshing a user's email verification state with Firebase `reload()` mutates the existing User object. A token/auth listener may not cause React to render again when it receives that same object, so the verification flow must explicitly notify the parent to reevaluate the verified-user route.

Keep Firebase's built-in email-verification link for the Android rider app; do not replace it with a manually entered numeric code unless the user changes this decision.

**Why:** The user set Firebase/Firestore as Wayfare's identity source and explicitly chose the built-in verification link over typed numeric codes.

**How to apply:** Keep Firebase client and Admin SDK settings on `irgadgetsofficialweb`. Firebase is the only sign-in provider; do not restore Clerk as a fallback. After verification reloads or app resume, explicitly refresh the root auth UI. Never auto-link accounts by email or delete existing records.

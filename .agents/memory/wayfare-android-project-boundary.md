---
name: Wayfare Android project boundary
description: Which project owns the Android rider app and how it connects to this backend.
---

Wayfare's backend and Android client live in the same repository but remain separate projects with separate build and runtime environments. Backend source and server-side packages are under `backend/`; Android source is under `android/`. The Android client communicates with the backend through its HTTP API and must not import backend packages or run with the backend service.

**Why:** the user wants both codebases in one repository for convenience, without mixing their environments or runtime.

**How to apply:** keep Android out of the pnpm workspace and backend deployment; use the API contract for integration. When reviewing client compatibility, compare actual Android requests and payloads against backend handlers, not just the partial OpenAPI document.

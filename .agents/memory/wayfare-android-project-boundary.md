---
name: Wayfare Android project boundary
description: Which project owns the Android rider app and how it connects to this backend.
---

Wayfare's backend and Android client should live in the same repository but remain separate projects with separate build and runtime environments. Put backend source and server-side packages under `backend/`; keep Android source under `android/`. The Android client communicates with the backend through its HTTP API and must not import backend packages or run with the backend service.

**Why:** the user wants both codebases in one repository for convenience, without mixing their environments or runtime.

**How to apply:** keep Android out of the pnpm workspace and backend deployment; use the API contract for integration. Android source has not yet been copied into this repository, so do not imply that it has.

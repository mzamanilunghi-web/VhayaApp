---
name: Expo DevTools host dependencies
description: Distinguish optional React Native DevTools startup failures from app render failures in Replit's Nix environment.
---

Expo Metro can serve the app while the native React Native DevTools executable fails to load system libraries. Treat this as a tooling issue unless the app bundle itself reports a runtime exception; do not install a broad set of GUI packages just to clear the warning.

**Why:** In this environment, adding the missing GLib library exposed another missing NSPR library while the web preview and API continued to run.

**How to apply:** Check Expo's platform bundle and runtime errors separately from the DevTools launcher message. Install only specifically required dependencies if native debugging itself is necessary.

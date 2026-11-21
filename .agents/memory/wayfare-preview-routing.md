---
name: Wayfare preview routing
description: Preserve the root preview route for the mobile app and keep the API on its separate route and port.
---

The Wayfare front end owns the root preview route (`/`) on port 5000. The API is a separate service on local port 8080 and handles `/api`. Keep the mobile artifact's preview and service paths at `/`; keep the API mounted under `/api`. The root domain also needs a `.replit` port mapping from local port 5000 to external port 80. Expo Go/native requests must use the public HTTPS domain without `:8080`; port 8080 is internal and only used by Metro's local web proxy. Expo web can call the same-origin `/api` path.

**Why:** The root development URL can return the API's JSON health response while Expo is running correctly on port 5000. Restarting workflows or changing start order alone does not make the root route choose Expo; the explicit port mapping does. An artifact metadata refresh can regenerate `.replit` with port 8080 mapped to external port 80, restoring the conflict. Calling the public domain on `:8080` returned HTTP 502 HTML even while the path-routed `/api` endpoint returned HTTP 200 JSON.

**How to apply:** Keep root → front end and `/api` → API. Use the validated `.replit` replacement flow for the 5000→80 mapping; do not edit generated artifact TOML directly. Expo Go should target `https://<public-domain>/api/...` with no internal port, while Metro's web proxy can continue forwarding locally to `127.0.0.1:8080`. After artifact metadata changes, reread the root `.replit` and verify `/` returns frontend HTML and `/api/healthz` returns API health JSON on both the app and Expo preview domains.

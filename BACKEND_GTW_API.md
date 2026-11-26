# Vhaya Backend — GTW (Going That Way) API & Matching Implementation

This document provides the complete PostgreSQL migration, corridor-matching helpers, and Express route handlers to add **GTW (Going That Way)** persistence and server-side ride matching to the Vhaya backend (`VHAYA_API_BASE_URL`).

---

## 1. PostgreSQL Migration (`migrations/add_driver_gtw_targets.sql`)

```sql
-- Store each driver's active GTW (Going That Way) destination and preferences.
-- Can live in its own table so existing driver_profiles migrations stay clean.

CREATE TABLE IF NOT EXISTS driver_gtw_targets (
    driver_id       TEXT PRIMARY KEY,
    label           TEXT NOT NULL,
    dest_latitude   DOUBLE PRECISION NOT NULL,
    dest_longitude  DOUBLE PRECISION NOT NULL,
    max_detour_km   DOUBLE PRECISION NOT NULL DEFAULT 6.0,
    strict_filter   BOOLEAN NOT NULL DEFAULT TRUE,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at_ms   BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_driver_gtw_active
    ON driver_gtw_targets (is_active, updated_at_ms DESC);
```

---

## 2. Corridor & Detour Matching Engine (`services/gtwMatcher.js`)

Matches the Android `DriverMapEngine.evaluateGtwMatch` math identically so client and server produce consistent detour distances, directional alignment, and ranking:

```javascript
const EARTH_RADIUS_KM = 6371.0;

function toRad(deg) {
  return (deg * Math.PI) / 180;
}

function haversineKm(a, b) {
  if (!a || !b) return null;
  const dLat = toRad(b.latitude - a.latitude);
  const dLon = toRad(b.longitude - a.longitude);
  const lat1 = toRad(a.latitude);
  const lat2 = toRad(b.latitude);
  const sinDLat = Math.sin(dLat / 2);
  const sinDLon = Math.sin(dLon / 2);
  const h =
    sinDLat * sinDLat +
    Math.cos(lat1) * Math.cos(lat2) * sinDLon * sinDLon;
  return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(h)));
}

function pointToSegmentDistanceKm(point, segStart, segEnd) {
  const avgLatRad = toRad((segStart.latitude + segEnd.latitude) * 0.5);
  const cosLat = Math.max(0.2, Math.cos(avgLatRad));
  const ax = segStart.longitude * cosLat;
  const ay = segStart.latitude;
  const bx = segEnd.longitude * cosLat;
  const by = segEnd.latitude;
  const px = point.longitude * cosLat;
  const py = point.latitude;
  const dx = bx - ax;
  const dy = by - ay;
  const lenSq = dx * dx + dy * dy;
  if (lenSq <= 1e-12) {
    return haversineKm(point, segStart) ?? 0;
  }
  const t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / lenSq));
  const proj = {
    latitude: segStart.latitude + (segEnd.latitude - segStart.latitude) * t,
    longitude: segStart.longitude + (segEnd.longitude - segStart.longitude) * t,
  };
  return haversineKm(point, proj) ?? 0;
}

function directionalAlignmentCosine(corridorStart, corridorEnd, candStart, candEnd) {
  const avgLatRad = toRad((corridorStart.latitude + corridorEnd.latitude) * 0.5);
  const cosLat = Math.max(0.2, Math.cos(avgLatRad));
  const ax = (corridorEnd.longitude - corridorStart.longitude) * cosLat;
  const ay = corridorEnd.latitude - corridorStart.latitude;
  const bx = (candEnd.longitude - candStart.longitude) * cosLat;
  const by = candEnd.latitude - candStart.latitude;
  const normA = Math.hypot(ax, ay);
  const normB = Math.hypot(bx, by);
  if (normA <= 1e-9 || normB <= 1e-9) return 0;
  return (ax * bx + ay * by) / (normA * normB);
}

/**
 * Evaluates whether an open ride request matches a driver's GTW corridor target.
 * Returns null if off-route, or detailed match metrics if on-the-way.
 */
function evaluateGtwMatch(trip, driverLocation, target) {
  if (!target || !target.isActive) return null;

  const pickup = trip.pickupCoordinates || trip.route?.originCoordinates;
  const dest = trip.destinationCoordinates || trip.route?.destinationCoordinates;
  if (!pickup || !dest) return null;

  const targetCoords = {
    latitude: Number(target.destLatitude ?? target.destination?.latitude),
    longitude: Number(target.destLongitude ?? target.destination?.longitude),
  };
  const maxDetourKm = Math.min(25, Math.max(1.5, Number(target.maxDetourKm ?? 6.0)));
  const origin = driverLocation || pickup;

  const originToTargetKm = haversineKm(origin, targetCoords) ?? 0;
  const originToPickupKm = haversineKm(origin, pickup) ?? 0;
  const pickupToDestKm = haversineKm(pickup, dest) ?? 0;
  const destToTargetKm = haversineKm(dest, targetCoords) ?? Number.MAX_VALUE;
  const pickupToTargetKm = haversineKm(pickup, targetCoords) ?? Number.MAX_VALUE;

  const addedDetourKm = Math.max(
    0,
    originToPickupKm + pickupToDestKm + destToTargetKm - originToTargetKm
  );

  // Driver is already right near the GTW destination
  if (originToTargetKm < 0.8) {
    if (destToTargetKm > maxDetourKm) return null;
    return {
      tripId: trip.id,
      addedDetourKm: Number(addedDetourKm.toFixed(2)),
      directionalAlignment: 1,
      progressTowardTargetKm: Number((pickupToTargetKm - destToTargetKm).toFixed(2)),
      pickupToCorridorKm: Number(originToPickupKm.toFixed(2)),
      endsNearTargetKm: Number(destToTargetKm.toFixed(2)),
      matchScore: Number((100 - addedDetourKm * 4).toFixed(2)),
    };
  }

  const pickupLatDistKm = pointToSegmentDistanceKm(pickup, origin, targetCoords);
  if (pickupLatDistKm > maxDetourKm && originToPickupKm > maxDetourKm) {
    return null;
  }

  const destLatDistKm = pointToSegmentDistanceKm(dest, origin, targetCoords);
  const progressKm = pickupToTargetKm - destToTargetKm;
  const alignment = directionalAlignmentCosine(origin, targetCoords, pickup, dest);

  const progressesTowardTarget = destToTargetKm <= pickupToTargetKm + 1.0;
  const endsAlongCorridorOrNearTarget =
    destLatDistKm <= maxDetourKm * 1.25 || destToTargetKm <= maxDetourKm;
  const alignedForward = alignment >= 0.1 || destToTargetKm <= maxDetourKm;
  const detourReasonable =
    addedDetourKm <= maxDetourKm * 2.2 || destToTargetKm <= maxDetourKm;

  if (
    !progressesTowardTarget ||
    !endsAlongCorridorOrNearTarget ||
    !alignedForward ||
    !detourReasonable
  ) {
    return null;
  }

  const corridorFitBonus = Math.max(0, maxDetourKm - pickupLatDistKm) * 3.0;
  const matchScore =
    50.0 +
    alignment * 25.0 +
    corridorFitBonus +
    Math.max(0, progressKm) * 1.2 -
    addedDetourKm * 2.5;

  return {
    tripId: trip.id,
    addedDetourKm: Number(addedDetourKm.toFixed(2)),
    directionalAlignment: Number(alignment.toFixed(3)),
    progressTowardTargetKm: Number(progressKm.toFixed(2)),
    pickupToCorridorKm: Number(Math.min(pickupLatDistKm, originToPickupKm).toFixed(2)),
    endsNearTargetKm: Number(destToTargetKm.toFixed(2)),
    matchScore: Number(matchScore.toFixed(2)),
  };
}

module.exports = { evaluateGtwMatch };
```

---

## 3. Express API Routes (`routes/driverGtw.js`)

Mount behind your existing Firebase auth + driver verification middleware (`requireAuth`, `requireDriver`):

```javascript
const express = require("express");
const { evaluateGtwMatch } = require("../services/gtwMatcher");

function createDriverGtwRouter({ pool }) {
  const router = express.Router();

  const serializeTarget = (row) =>
    row && row.is_active
      ? {
          label: row.label,
          destination: {
            latitude: Number(row.dest_latitude),
            longitude: Number(row.dest_longitude),
          },
          maxDetourKm: Number(row.max_detour_km),
          strictFilter: Boolean(row.strict_filter),
          updatedAtMs: Number(row.updated_at_ms),
        }
      : null;

  // GET /api/driver/gtw — fetch current active GTW target
  router.get("/", async (req, res, next) => {
    try {
      const driverId = req.user.uid;
      const { rows } = await pool.query(
        `SELECT label, dest_latitude, dest_longitude, max_detour_km, strict_filter, is_active, updated_at_ms
         FROM driver_gtw_targets
         WHERE driver_id = $1`,
        [driverId]
      );
      res.json({ gtwTarget: serializeTarget(rows[0]) });
    } catch (err) {
      next(err);
    }
  });

  // PUT /api/driver/gtw — set or update active GTW destination & mode
  router.put("/", async (req, res, next) => {
    try {
      const driverId = req.user.uid;
      const { label, destination, maxDetourKm, strictFilter } = req.body || {};
      const lat = Number(destination?.latitude);
      const lon = Number(destination?.longitude);

      if (!Number.isFinite(lat) || !Number.isFinite(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
        return res.status(400).json({ error: "Valid GTW destination coordinates are required." });
      }

      const cleanLabel = String(label || "GTW Destination").trim().slice(0, 120) || "GTW Destination";
      const clampedDetour = Math.min(25, Math.max(1.5, Number(maxDetourKm ?? 6.0) || 6.0));
      const strict = strictFilter === undefined ? true : Boolean(strictFilter);
      const nowMs = Date.now();

      const { rows } = await pool.query(
        `INSERT INTO driver_gtw_targets (
            driver_id, label, dest_latitude, dest_longitude,
            max_detour_km, strict_filter, is_active, updated_at_ms
         ) VALUES ($1, $2, $3, $4, $5, $6, TRUE, $7)
         ON CONFLICT (driver_id) DO UPDATE SET
            label = EXCLUDED.label,
            dest_latitude = EXCLUDED.dest_latitude,
            dest_longitude = EXCLUDED.dest_longitude,
            max_detour_km = EXCLUDED.max_detour_km,
            strict_filter = EXCLUDED.strict_filter,
            is_active = TRUE,
            updated_at_ms = EXCLUDED.updated_at_ms
         RETURNING *`,
        [driverId, cleanLabel, lat, lon, clampedDetour, strict, nowMs]
      );

      res.json({ gtwTarget: serializeTarget(rows[0]) });
    } catch (err) {
      next(err);
    }
  });

  // DELETE /api/driver/gtw — turn off GTW mode
  router.delete("/", async (req, res, next) => {
    try {
      const driverId = req.user.uid;
      await pool.query(
        `UPDATE driver_gtw_targets
         SET is_active = FALSE, updated_at_ms = $2
         WHERE driver_id = $1`,
        [driverId, Date.now()]
      );
      res.json({ gtwTarget: null });
    } catch (err) {
      next(err);
    }
  });

  // GET /api/driver/gtw/matches — open ride requests evaluated against active GTW target
  router.get("/matches", async (req, res, next) => {
    try {
      const driverId = req.user.uid;
      const lat = Number(req.query.lat);
      const lon = Number(req.query.lon);
      const driverLocation =
        Number.isFinite(lat) && Number.isFinite(lon)
          ? { latitude: lat, longitude: lon }
          : null;

      const targetRes = await pool.query(
        `SELECT * FROM driver_gtw_targets WHERE driver_id = $1 AND is_active = TRUE`,
        [driverId]
      );
      const targetRow = targetRes.rows[0];
      if (!targetRow) {
        return res.json({ gtwTarget: null, matches: [] });
      }

      const target = serializeTarget(targetRow);
      const openTrips = await fetchOpenRideTripsFromDb(pool); // existing helper used by GET /api/rides

      const matches = openTrips
        .map((trip) => {
          const match = evaluateGtwMatch(trip, driverLocation, {
            ...target,
            isActive: true,
          });
          return match ? { trip, gtwMatch: match } : null;
        })
        .filter(Boolean)
        .sort((a, b) => b.gtwMatch.matchScore - a.gtwMatch.matchScore);

      res.json({ gtwTarget: target, matches });
    } catch (err) {
      next(err);
    }
  });

  return router;
}

module.exports = { createDriverGtwRouter };
```

### Mounting in `server.js` / `app.js`

```javascript
const { createDriverGtwRouter } = require("./routes/driverGtw");

app.use("/api/driver/gtw", requireAuth, requireApprovedDriver, createDriverGtwRouter({ pool }));
```

### Optional Enrichment on `GET /api/rides`
When `role === "driver"`, you can load `driver_gtw_targets` for `req.user.uid` and attach `gtwMatch: evaluateGtwMatch(trip, driverLoc, target)` onto each open trip in `trips` (or filter server-side if `strictFilter === true`), while Android also evaluates `DriverMapEngine.evaluateGtwMatch` locally for instant offline & low-latency filtering.

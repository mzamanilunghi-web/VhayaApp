# Vhaya Backend — GTW (Going That Way) API & Matching Implementation

This document provides the complete PostgreSQL migration, corridor-matching helpers, and Express route handlers to add **GTW (Going That Way)** persistence and server-side ride/Pocket matching to the Vhaya backend (`VHAYA_API_BASE_URL`).

---

## 1. PostgreSQL Migration (`migrations/add_driver_gtw_targets.sql`)

```sql
-- Store each driver's active GTW (Going That Way) destination, arrive-by deadline, and expiry.
-- Safe to run on both fresh databases and existing Phase 1 GTW deployments.

CREATE TABLE IF NOT EXISTS driver_gtw_targets (
    driver_id           TEXT PRIMARY KEY,
    label               TEXT NOT NULL,
    dest_latitude       DOUBLE PRECISION NOT NULL,
    dest_longitude      DOUBLE PRECISION NOT NULL,
    max_detour_km       DOUBLE PRECISION NOT NULL DEFAULT 6.0,
    strict_filter       BOOLEAN NOT NULL DEFAULT TRUE,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at_ms       BIGINT NOT NULL,
    arrive_by_epoch_ms  BIGINT NULL,
    expires_at_ms       BIGINT NULL
);

ALTER TABLE driver_gtw_targets
    ADD COLUMN IF NOT EXISTS arrive_by_epoch_ms BIGINT NULL,
    ADD COLUMN IF NOT EXISTS expires_at_ms BIGINT NULL;

CREATE INDEX IF NOT EXISTS idx_driver_gtw_active
    ON driver_gtw_targets (is_active, updated_at_ms DESC);
```

---

## 2. Corridor & Detour Matching Engine (`services/gtwMatcher.js`)

Matches the Android `DriverMapEngine.evaluateGtwMatch` and `evaluatePocketGtwMatch` math identically—supporting road-polyline matching, `arriveByEpochMs` detour tightening, and 8-hour TTL expiry—so client and server produce consistent detour distances, directional alignment, and ranking:

```javascript
const EARTH_RADIUS_KM = 6371.0;
const DEFAULT_GTW_TTL_MS = 8 * 60 * 60 * 1000;

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

function decodePolyline(encoded) {
  if (!encoded || typeof encoded !== "string") return [];
  const points = [];
  let index = 0;
  let lat = 0;
  let lng = 0;
  while (index < encoded.length) {
    let result = 0;
    let shift = 0;
    let b;
    do {
      b = encoded.charCodeAt(index++) - 63;
      result |= (b & 0x1f) << shift;
      shift += 5;
    } while (b >= 0x20 && index < encoded.length);
    lat += result & 1 ? ~(result >> 1) : result >> 1;

    result = 0;
    shift = 0;
    do {
      b = encoded.charCodeAt(index++) - 63;
      result |= (b & 0x1f) << shift;
      shift += 5;
    } while (b >= 0x20 && index < encoded.length);
    lng += result & 1 ? ~(result >> 1) : result >> 1;

    points.push({ latitude: lat / 1e5, longitude: lng / 1e5 });
  }
  return points;
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

function pointToPolylineDistanceKm(point, polylinePoints) {
  if (!Array.isArray(polylinePoints) || polylinePoints.length === 0) return Number.MAX_VALUE;
  if (polylinePoints.length === 1) return haversineKm(point, polylinePoints[0]) ?? Number.MAX_VALUE;
  let best = Number.MAX_VALUE;
  for (let i = 0; i < polylinePoints.length - 1; i++) {
    const dist = pointToSegmentDistanceKm(point, polylinePoints[i], polylinePoints[i + 1]);
    if (dist < best) best = dist;
  }
  return best;
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

function isTargetExpired(target, nowMs = Date.now()) {
  if (!target) return true;
  if (target.expiresAtMs != null && nowMs >= Number(target.expiresAtMs)) return true;
  if (Number(target.updatedAtMs) > 0 && nowMs - Number(target.updatedAtMs) >= DEFAULT_GTW_TTL_MS) {
    return true;
  }
  return false;
}

function effectiveMaxDetourKm(target, nowMs = Date.now()) {
  const base = Math.min(25, Math.max(1.5, Number(target.maxDetourKm ?? 6.0)));
  if (target.arriveByEpochMs == null) return base;
  const remainingMinutes = (Number(target.arriveByEpochMs) - nowMs) / 60000;
  if (remainingMinutes <= 0) return Math.min(base, 2.0);
  if (remainingMinutes <= 20) return Math.min(base, 3.0);
  if (remainingMinutes <= 45) return Math.min(base, 4.5);
  return base;
}

/**
 * Evaluates whether an open ride request matches a driver's GTW corridor target.
 * Supports optional corridorPolyline (decoded [{latitude, longitude}]) or encodedPolyline string.
 */
function evaluateGtwMatch(trip, driverLocation, target, options = {}) {
  const nowMs = options.nowMs ?? Date.now();
  if (!target || !target.isActive || isTargetExpired(target, nowMs)) return null;

  const pickup = trip.pickupCoordinates || trip.route?.originCoordinates;
  const dest = trip.destinationCoordinates || trip.route?.destinationCoordinates;
  if (!pickup || !dest) return null;

  const targetCoords = {
    latitude: Number(target.destLatitude ?? target.destination?.latitude),
    longitude: Number(target.destLongitude ?? target.destination?.longitude),
  };
  const maxDetourKm = effectiveMaxDetourKm(target, nowMs);
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

  const polylinePoints =
    Array.isArray(options.corridorPolyline) && options.corridorPolyline.length >= 2
      ? options.corridorPolyline
      : decodePolyline(options.encodedPolyline);

  const pickupLatDistKm =
    polylinePoints.length >= 2
      ? pointToPolylineDistanceKm(pickup, polylinePoints)
      : pointToSegmentDistanceKm(pickup, origin, targetCoords);
  if (pickupLatDistKm > maxDetourKm && originToPickupKm > maxDetourKm) {
    return null;
  }

  const destLatDistKm =
    polylinePoints.length >= 2
      ? pointToPolylineDistanceKm(dest, polylinePoints)
      : pointToSegmentDistanceKm(dest, origin, targetCoords);
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

/**
 * Matches open LISTED Pocket parcel items along a driver's active GTW corridor.
 */
function evaluatePocketGtwMatch(item, driverLocation, target, options = {}) {
  if (!item || String(item.status || "").toUpperCase() !== "LISTED") return null;
  const pseudoTrip = {
    id: item.id,
    pickupCoordinates: item.originCoordinates,
    destinationCoordinates: item.destinationCoordinates,
  };
  const match = evaluateGtwMatch(pseudoTrip, driverLocation, target, options);
  if (!match) return null;
  return {
    itemId: item.id,
    addedDetourKm: match.addedDetourKm,
    pickupToCorridorKm: match.pickupToCorridorKm,
    matchScore: match.matchScore + Number(item.fareZar || 0) * 0.05,
  };
}

module.exports = {
  DEFAULT_GTW_TTL_MS,
  decodePolyline,
  effectiveMaxDetourKm,
  evaluateGtwMatch,
  evaluatePocketGtwMatch,
  isTargetExpired,
};
```

---

## 3. Express API Routes (`routes/driverGtw.js`)

Mount behind your existing Firebase auth + driver verification middleware (`requireAuth`, `requireApprovedDriver`):

```javascript
const express = require("express");
const {
  DEFAULT_GTW_TTL_MS,
  evaluateGtwMatch,
  evaluatePocketGtwMatch,
  isTargetExpired,
} = require("../services/gtwMatcher");

function createDriverGtwRouter({ pool }) {
  const router = express.Router();

  const serializeTarget = (row) => {
    if (!row || !row.is_active) return null;
    const target = {
      label: row.label,
      destination: {
        latitude: Number(row.dest_latitude),
        longitude: Number(row.dest_longitude),
      },
      maxDetourKm: Number(row.max_detour_km),
      strictFilter: Boolean(row.strict_filter),
      updatedAtMs: Number(row.updated_at_ms),
      arriveByEpochMs: row.arrive_by_epoch_ms != null ? Number(row.arrive_by_epoch_ms) : null,
      expiresAtMs: row.expires_at_ms != null ? Number(row.expires_at_ms) : null,
    };
    return isTargetExpired(target) ? null : target;
  };

  // GET /api/driver/gtw — fetch current active non-expired GTW target
  router.get("/", async (req, res, next) => {
    try {
      const driverId = req.user.uid;
      const { rows } = await pool.query(
        `SELECT label, dest_latitude, dest_longitude, max_detour_km, strict_filter,
                is_active, updated_at_ms, arrive_by_epoch_ms, expires_at_ms
         FROM driver_gtw_targets
         WHERE driver_id = $1`,
        [driverId]
      );
      const serialized = serializeTarget(rows[0]);
      if (rows[0]?.is_active && !serialized) {
        await pool.query(
          `UPDATE driver_gtw_targets SET is_active = FALSE WHERE driver_id = $1`,
          [driverId]
        );
      }
      res.json({ gtwTarget: serialized });
    } catch (err) {
      next(err);
    }
  });

  // PUT /api/driver/gtw — set or update active GTW destination, deadline & TTL
  router.put("/", async (req, res, next) => {
    try {
      const driverId = req.user.uid;
      const {
        label,
        destination,
        maxDetourKm,
        strictFilter,
        arriveByEpochMs,
        expiresAtMs,
        updatedAtMs,
      } = req.body || {};
      const lat = Number(destination?.latitude);
      const lon = Number(destination?.longitude);

      if (!Number.isFinite(lat) || !Number.isFinite(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
        return res.status(400).json({ error: "Valid GTW destination coordinates are required." });
      }

      const nowMs = Date.now();
      const clientUpdatedAt = Number(updatedAtMs);
      const effectiveUpdatedAtMs =
        Number.isFinite(clientUpdatedAt) && clientUpdatedAt > 0
          ? Math.min(clientUpdatedAt, nowMs + 60_000)
          : nowMs;

      const parsedArriveBy =
        arriveByEpochMs != null && Number.isFinite(Number(arriveByEpochMs))
          ? Number(arriveByEpochMs)
          : null;
      const parsedExpiresAt =
        expiresAtMs != null && Number.isFinite(Number(expiresAtMs))
          ? Number(expiresAtMs)
          : effectiveUpdatedAtMs + DEFAULT_GTW_TTL_MS;

      const cleanLabel = String(label || "GTW Destination").trim().slice(0, 120) || "GTW Destination";
      const clampedDetour = Math.min(25, Math.max(1.5, Number(maxDetourKm ?? 6.0) || 6.0));
      const strict = strictFilter === undefined ? true : Boolean(strictFilter);

      const { rows } = await pool.query(
        `INSERT INTO driver_gtw_targets (
            driver_id, label, dest_latitude, dest_longitude,
            max_detour_km, strict_filter, is_active, updated_at_ms,
            arrive_by_epoch_ms, expires_at_ms
         ) VALUES ($1, $2, $3, $4, $5, $6, TRUE, $7, $8, $9)
         ON CONFLICT (driver_id) DO UPDATE SET
            label = EXCLUDED.label,
            dest_latitude = EXCLUDED.dest_latitude,
            dest_longitude = EXCLUDED.dest_longitude,
            max_detour_km = EXCLUDED.max_detour_km,
            strict_filter = EXCLUDED.strict_filter,
            is_active = TRUE,
            updated_at_ms = EXCLUDED.updated_at_ms,
            arrive_by_epoch_ms = EXCLUDED.arrive_by_epoch_ms,
            expires_at_ms = EXCLUDED.expires_at_ms
         WHERE driver_gtw_targets.updated_at_ms <= EXCLUDED.updated_at_ms
         RETURNING *`,
        [
          driverId,
          cleanLabel,
          lat,
          lon,
          clampedDetour,
          strict,
          effectiveUpdatedAtMs,
          parsedArriveBy,
          parsedExpiresAt,
        ]
      );

      if (!rows[0]) {
        const existing = await pool.query(
          `SELECT * FROM driver_gtw_targets WHERE driver_id = $1`,
          [driverId]
        );
        return res.json({ gtwTarget: serializeTarget(existing.rows[0]) });
      }

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

  // GET /api/driver/gtw/matches — open ride requests & Pocket parcels evaluated against active GTW target
  router.get("/matches", async (req, res, next) => {
    try {
      const driverId = req.user.uid;
      const lat = Number(req.query.lat);
      const lon = Number(req.query.lon);
      const encodedPolyline = typeof req.query.polyline === "string" ? req.query.polyline : undefined;
      const driverLocation =
        Number.isFinite(lat) && Number.isFinite(lon)
          ? { latitude: lat, longitude: lon }
          : null;

      const targetRes = await pool.query(
        `SELECT * FROM driver_gtw_targets WHERE driver_id = $1 AND is_active = TRUE`,
        [driverId]
      );
      const target = serializeTarget(targetRes.rows[0]);
      if (!target) {
        return res.json({ gtwTarget: null, matches: [], pocketMatches: [] });
      }

      const openTrips = await fetchOpenRideTripsFromDb(pool); // existing helper used by GET /api/rides
      const matchOptions = { encodedPolyline, nowMs: Date.now() };

      const matches = openTrips
        .map((trip) => {
          const match = evaluateGtwMatch(trip, driverLocation, { ...target, isActive: true }, matchOptions);
          return match ? { trip, gtwMatch: match } : null;
        })
        .filter(Boolean)
        .sort((a, b) => b.gtwMatch.matchScore - a.gtwMatch.matchScore);

      // Optional Pocket parcel matching if pocket_items table is enabled in your backend:
      const openPocketItems = typeof fetchOpenPocketItemsFromDb === "function"
        ? await fetchOpenPocketItemsFromDb(pool)
        : [];
      const pocketMatches = openPocketItems
        .map((item) => {
          const pocketMatch = evaluatePocketGtwMatch(
            item,
            driverLocation,
            { ...target, isActive: true },
            matchOptions
          );
          return pocketMatch ? { item, gtwPocketMatch: pocketMatch } : null;
        })
        .filter(Boolean)
        .sort((a, b) => b.gtwPocketMatch.matchScore - a.gtwPocketMatch.matchScore);

      res.json({ gtwTarget: target, matches, pocketMatches });
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
When `role === "driver"`, you can load `driver_gtw_targets` for `req.user.uid`, skip expired targets via `isTargetExpired`, and attach `gtwMatch: evaluateGtwMatch(trip, driverLoc, target)` onto each open trip in `trips` (or filter server-side if `strictFilter === true`), while Android also evaluates `DriverMapEngine.evaluateGtwMatch` locally for instant offline & low-latency filtering.

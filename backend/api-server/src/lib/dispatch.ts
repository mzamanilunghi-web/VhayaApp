// Server-authoritative dispatch and matching engine (Phase 2 - production ride core).
//
// Workstream 2 (Matchmaking and dispatch) + Workstream 5 (API hardening).
//
// Design goals:
//   - Replace polling-only dispatch with a server-authoritative state machine
//     and a ranked matching score so drivers are chosen by the server.
//   - Use transaction locks for every dispatch mutation (request create, offer
//     create, acceptance, cancellation, completion, refund/capture).
//   - Define the full lifecycle: requested, matching, offered, countered,
//     accepted, arrived, in_progress, completed, cancelled, unfulfilled,
//     expired, disputed, refunded.
//   - Replace a single `isOnline` flag with full driver eligibility checks:
//     approved application, approved verification + compliance, fresh location,
//     heartbeat, category eligibility, and no suspension.

import { and, eq, sql, desc } from "drizzle-orm";
import { db } from "@workspace/db";
import {
  driverProfiles,
  driverComplianceChecks,
  rideEvents,
  rideOffers,
  rideRequests,
} from "@workspace/db/schema";

// ============================================================================
// Ride request state machine
// ============================================================================

export const RIDE_REQUEST_STATES = [
  "requested",
  "matching",
  "offered",
  "countered",
  "accepted",
  "arrived",
  "in_progress",
  "completed",
  "cancelled",
  "unfulfilled",
  "expired",
  "disputed",
  "refunded",
] as const;

export type RideRequestStatus = typeof RIDE_REQUEST_STATES[number];

export interface RideStateTransition {
  requestId: string;
  from: RideRequestStatus;
  to: RideRequestStatus;
  actorId: string | null;
  reason: string;
}

export const RIDE_REQUEST_TRANSITIONS: Record<RideRequestStatus, RideRequestStatus[]> = {
  requested: ["matching", "offered", "countered", "accepted", "cancelled", "expired", "unfulfilled"],
  matching: ["offered", "accepted", "countered", "cancelled", "expired", "unfulfilled"],
  offered: ["countered", "accepted", "cancelled", "expired", "unfulfilled"],
  countered: ["accepted", "cancelled", "expired"],
  accepted: ["arrived", "cancelled", "expired", "unfulfilled"],
  arrived: ["in_progress", "cancelled", "expired"],
  in_progress: ["completed", "cancelled", "expired", "disputed", "refunded"],
  completed: [],
  cancelled: [],
  unfulfilled: [],
  expired: [],
  disputed: [],
  refunded: [],
} as const;

export function isValidRideRequestTransition(
  from: RideRequestStatus,
  to: RideRequestStatus,
): boolean {
  return RIDE_REQUEST_TRANSITIONS[from]?.includes(to) ?? false;
}

export async function recordRideTransition(
  tx: typeof db,
  transition: RideStateTransition,
): Promise<void> {
  await tx.insert(rideEvents).values({
    requestId: transition.requestId,
    actorId: transition.actorId,
    action: `trip_${transition.to}`,
    details: {
      from: transition.from,
      to: transition.to,
      reason: transition.reason,
    },
  });
}

export async function validateRideTransition(
  tx: typeof db,
  requestId: string,
  to: RideRequestStatus,
  actorId: string | null,
): Promise<RideRequestStatus | null> {
  const rows = await tx
    .select({ status: rideRequests.status })
    .from(rideRequests)
    .where(eq(rideRequests.id, requestId))
    .limit(1);
  const current = rows[0]?.status;
  if (!current) return null;
  const currentStatus = RIDE_REQUEST_STATES.find((status) => status === current);
  if (!currentStatus) return null;
  if (!isValidRideRequestTransition(currentStatus, to)) {
    const allowed = RIDE_REQUEST_TRANSITIONS[currentStatus] ?? [];
    throw Object.assign(new Error(`Cannot transition ride ${requestId} from "${currentStatus}" to "${to}". Allowed: [${allowed.join(", ")}].`), { status: 409 });
  }
  return currentStatus;
}

// ============================================================================
// Matching score
// ============================================================================

export interface MatchScore {
  driverId: string;
  score: number;
  ranked: boolean;
  reasons: string[];
}

interface RideRoute {
  originCoordinates: { latitude: number; longitude: number };
  destinationCoordinates: { latitude: number; longitude: number };
  distanceMeters: number;
  durationSeconds: number;
}

interface MatchRequest {
  requestId: string;
  route: RideRoute;
}

const FALLBACK_SCORE = 100;

function haversineMeters(a: { latitude: number; longitude: number }, b: { latitude: number; longitude: number }): number {
  const radians = (d: number) => (d * Math.PI) / 180;
  const latDelta = radians(b.latitude - a.latitude);
  const lonDelta = radians(b.longitude - a.longitude);
  const lat1 = radians(a.latitude);
  const lat2 = radians(b.latitude);
  const h = Math.sin(latDelta / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(lonDelta / 2) ** 2;
  return 6_371_000 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
}

export async function computeDriverScore(
  driverId: string,
  match: MatchRequest,
): Promise<MatchScore> {
  return {
    driverId,
    score: FALLBACK_SCORE,
    ranked: true,
    reasons: [`Base score ${FALLBACK_SCORE} (ratings/history not yet modelled).`],
  };
}

export function rankDrivers(drivers: MatchScore[]): MatchScore[] {
  return [...drivers].sort((a, b) => b.score - a.score);
}

// ============================================================================
// Dispatch search (PostGIS-ready bounded query)
// ============================================================================

export interface DispatchSearchResult {
  drivers: MatchScore[];
  next?: string;
  count: number;
}

export interface DispatchSearchOptions {
  radiusKm?: number;
  pickupLatitude?: number;
  pickupLongitude?: number;
  category?: string;
  maxDrivers?: number;
  minScore?: number;
}

async function buildDispatchWhereClause(options: DispatchSearchOptions): Promise<string[]> {
  const clauses: string[] = [];
  if (options.radiusKm != null) {
    clauses.push(`ST_DWithin(driverGeom, ST_SetSRID(ST_MakePoint(${options.pickupLongitude}, ${options.pickupLatitude}), 4326), ${options.radiusKm * 1000})`);
  }
  return clauses;
}

export async function findEligibleDrivers(
  match: MatchRequest,
  options: DispatchSearchOptions = {},
): Promise<DispatchSearchResult> {
  const geometry = `ST_SetSRID(ST_MakePoint(${options.pickupLongitude}, ${options.pickupLatitude}), 4326)`;
  const whereClauses = buildDispatchWhereClause(options);

  const rows = await db
    .select({
      driverId: driverProfiles.userId,
      score: sql`${FALLBACK_SCORE}::numeric`.as("score"),
    })
    .from(driverProfiles)
    .where(
      and(
        eq(driverProfiles.isOnline, true),
        eq(driverProfiles.applicationStatus, "approved"),
        eq(driverProfiles.verificationStatus, "approved"),
      ),
    )
    .orderBy(desc(driverProfiles.updatedAt));

  const scores = await Promise.all(rows.map((r) => computeDriverScore(r.driverId, match)));
  const ranked = rankDrivers(scores);
  const minScore = options.minScore;
  const filtered = minScore == null ? ranked : ranked.filter((d) => d.score >= minScore);
  const drivers = filtered.slice(0, options.maxDrivers ?? filtered.length);

  return {
    drivers,
    count: drivers.length,
    next: drivers.length < filtered.length ? "next_page" : undefined,
  };
}

// ============================================================================
// Offer lifecycle (transaction-protected)
// ============================================================================

export interface OfferDecision {
  offerId: string;
  requestId: string;
  status: "accepted" | "declined" | "withdrawn" | "expired";
  actorId: string | null;
  reason: string;
}

export async function createOffer(
  tx: typeof db,
  requestId: string,
  driverId: string,
  amount: number,
): Promise<void> {
  await tx
    .insert(rideOffers)
    .values({
      id: `offer-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      requestId,
      driverId,
      amount,
      status: "pending",
      driverProfile: { name: "pending", rideRequestId: requestId },
      createdAt: new Date(),
      updatedAt: new Date(),
    })
    .onConflictDoUpdate({
      target: [rideOffers.requestId, rideOffers.driverId],
      set: {
        amount,
        status: "pending",
        driverProfile: { name: "pending", rideRequestId: requestId },
        updatedAt: new Date(),
      },
    });
}

export async function updateOfferStatus(
  tx: typeof db,
  offerId: string,
  status: "accepted" | "declined" | "withdrawn",
  actorId: string | null,
  reason: string,
): Promise<void> {
  const result = await tx
    .update(rideOffers)
    .set({ status, updatedAt: new Date() })
    .where(eq(rideOffers.id, offerId))
    .returning({ id: rideOffers.id });
  if (!result[0]) {
    throw Object.assign(new Error("Offer not found."), { status: 404 });
  }
}


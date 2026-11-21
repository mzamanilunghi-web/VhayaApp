import { randomInt, randomUUID } from "node:crypto";
import { and, desc, eq, gte, inArray, notInArray, or, sql } from "drizzle-orm";
import { Router, type IRouter } from "express";
import { db } from "@workspace/db";
import {
  driverProfiles,
  driverRatingRecords,
  driverRequestSkips,
  rideEvents,
  rideOffers,
  ridePayments,
  rideRequests,
  supportCases,
  wallets,
  walletTransactions,
  wayfareUsers,
} from "@workspace/db/schema";
import {
  UpdateDriverOnlineLocationBody,
  UpdateDriverOnlineLocationResponse,
} from "@workspace/api-zod";
import { getWayfareUserId } from "../middlewares/firebaseIdentity";
import { getWayfareRole } from "../middlewares/wayfareRoles";
import { logger } from "../lib/logger";
import { recordBody, syncWayfareUser, toDate } from "../lib/wayfareData";
import { verifyRouteQuote } from "../lib/routeQuote";
import {
  captureRidePaymentForCompletion,
  releaseRidePaymentForCancellation,
} from "./payments";

type WayfareTrip = Record<string, unknown> & {
  id: string;
  status: string;
  fare: number;
  pickup: string;
  destination: string;
  isReturnRequest?: boolean;
  clientRequestId?: string;
};

const router: IRouter = Router();
const rideCategories = new Set(["standard", "comfort", "xl", "bakkie", "parcel"]);
const terminalStatuses = new Set(["completed", "cancelled", "unfulfilled"]);
const MIN_RIDE_DURATION_SECONDS = 5 * 60;
const ARRIVAL_RADIUS_METERS = 150;
const MAX_ARRIVAL_LOCATION_AGE_MS = 60_000;
// Keep unapproved launch fees disabled on the server as well as in the app.
const riderCancellationFeePercent = 0;

function safeTripData(value: Record<string, unknown>): WayfareTrip {
  return value as WayfareTrip;
}

function optionalTextField(
  body: Record<string, unknown>,
  key: string,
  maxLength: number,
): { valid: boolean; value: string | null } {
  const raw = body[key];
  if (raw === undefined || raw === null) return { valid: true, value: null };
  if (typeof raw !== "string") return { valid: false, value: null };
  const value = raw.trim();
  return {
    valid: value.length <= maxLength,
    value: value || null,
  };
}

function validCoordinates(value: unknown) {
  const coordinates = recordBody(value);
  const latitude = coordinates.latitude;
  const longitude = coordinates.longitude;
  if (
    typeof latitude !== "number" ||
    !Number.isFinite(latitude) ||
    Math.abs(latitude) > 90 ||
    typeof longitude !== "number" ||
    !Number.isFinite(longitude) ||
    Math.abs(longitude) > 180
  ) {
    return null;
  }
  return { latitude, longitude };
}

function distanceBetweenMeters(
  first: { latitude: number; longitude: number },
  second: { latitude: number; longitude: number },
) {
  const radians = Math.PI / 180;
  const latitudeDelta = (second.latitude - first.latitude) * radians;
  const longitudeDelta = (second.longitude - first.longitude) * radians;
  const firstLatitude = first.latitude * radians;
  const secondLatitude = second.latitude * radians;
  const haversine =
    Math.sin(latitudeDelta / 2) ** 2 +
    Math.cos(firstLatitude) *
      Math.cos(secondLatitude) *
      Math.sin(longitudeDelta / 2) ** 2;
  return (
    6_371_000 *
    2 *
    Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine))
  );
}

function publicDriverProfile(
  profile: typeof driverProfiles.$inferSelect | null,
) {
  if (!profile) return null;
  return {
    ...profile,
    submittedAt: profile.submittedAt?.getTime() ?? null,
    reviewedAt: profile.reviewedAt?.getTime() ?? null,
    updatedAt: profile.updatedAt.getTime(),
  };
}

function publicRoute(value: unknown) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return value;
  const route = value as Record<string, unknown>;
  return {
    ...route,
    ...(typeof route.distanceMeters === "number" && Number.isFinite(route.distanceMeters)
      ? { distanceMeters: Math.max(0, Math.round(route.distanceMeters)) }
      : {}),
    ...(typeof route.durationSeconds === "number" && Number.isFinite(route.durationSeconds)
      ? { durationSeconds: Math.max(0, Math.round(route.durationSeconds)) }
      : {}),
  };
}

function publicTrip(
  row: typeof rideRequests.$inferSelect,
  role: string,
  offers: (typeof rideOffers.$inferSelect)[] = [],
  viewerId?: string,
) {
  const trip = {
    ...row.tripData,
    id: row.id,
    riderId: row.riderId,
    driverId: row.driverId ?? null,
    status: row.status,
    fare: row.fare,
    pickup: row.pickup,
    destination: row.destination,
    isReturnRequest: row.tripData.isReturnRequest === true,
    route: publicRoute(row.route) ?? undefined,
    offers: offers
        .filter(
          (offer) =>
            ["pending", "accepted"].includes(offer.status) &&
            (role !== "driver" || offer.driverId === viewerId),
        )
      .map((offer) => ({
        id: offer.id,
        tripId: row.id,
        driverId: offer.driverId,
        amount: offer.amount,
        status: offer.status,
        createdAt: offer.createdAt.getTime(),
        driverProfile: { ...offer.driverProfile, fare: offer.amount },
      })),
  } as Record<string, unknown>;

  if (Array.isArray(trip.messages)) {
    trip.messages = trip.messages.filter((message) => {
      if (!message || typeof message !== "object") return false;
      const candidate = message as { channel?: unknown; sender?: unknown };
      if (candidate.channel !== "support" || role === "admin") return true;
      if (candidate.sender === "support") {
        const addressedTo = (message as { recipientUserId?: unknown })
          .recipientUserId;
        return addressedTo === viewerId;
      }
      if (candidate.sender !== role) return false;
      return role === "rider" || (role === "driver" && row.driverId === viewerId);
    });
  }

  if (role !== "rider" || row.status !== "arrived") delete trip.tripPin;
  if (role === "driver" && row.driverId !== viewerId) {
    delete trip.driverProfile;
    delete trip.messages;
  }
  return trip;
}

async function tripWithOffers(
  row: typeof rideRequests.$inferSelect,
  role: string,
  viewerId: string,
) {
  const offers = await db
    .select()
    .from(rideOffers)
    .where(eq(rideOffers.requestId, row.id))
    .orderBy(desc(rideOffers.createdAt));
  return publicTrip(row, role, offers, viewerId);
}

async function loadDriverProfile(userId: string) {
  const rows = await db
    .select()
    .from(driverProfiles)
    .where(eq(driverProfiles.userId, userId))
    .limit(1);
  return rows[0] ?? null;
}

async function isUserSuspended(userId: string): Promise<boolean> {
  const rows = await db
    .select({ isSuspended: wayfareUsers.isSuspended })
    .from(wayfareUsers)
    .where(eq(wayfareUsers.id, userId))
    .limit(1);
  return rows[0]?.isSuspended === true;
}

async function ensureDriverCanDispatch(userId: string) {
  if (await isUserSuspended(userId)) {
    return { profile: null, error: "Your Vhaya account is suspended." };
  }
  const profile = await loadDriverProfile(userId);
  if (!profile || profile.applicationStatus !== "approved" || profile.verificationStatus !== "approved") {
    return { profile: null, error: "Driver approval and verification are required before dispatch." };
  }
  if (!profile.isOnline) {
    return { profile: null, error: "Go online before responding to ride requests." };
  }
  return { profile, error: null };
}

async function hasDeclinedOfferForRequest(
  transaction: Parameters<Parameters<typeof db.transaction>[0]>[0],
  requestId: string,
  driverId: string,
) {
  const rows = await transaction
    .select({ id: rideOffers.id })
    .from(rideOffers)
    .where(
      and(
        eq(rideOffers.requestId, requestId),
        eq(rideOffers.driverId, driverId),
        eq(rideOffers.status, "declined"),
      ),
    )
    .limit(1);
  return rows.length > 0;
}

function driverSummary(
  displayName: string,
  userId: string,
  profile: NonNullable<Awaited<ReturnType<typeof loadDriverProfile>>>,
  fare: number,
) {
  return {
    id: userId,
    name: displayName,
    vehicle: profile.vehicle ?? "Vehicle details pending",
    vehicleColor: profile.vehicleColor ?? "",
    registration: profile.registration ?? "",
    gravelReady: profile.gravelReady,
    fare,
    verificationStatus: "approved",
  };
}

async function saveTrip(
  transaction: Parameters<Parameters<typeof db.transaction>[0]>[0],
  row: typeof rideRequests.$inferSelect,
  trip: WayfareTrip,
  status = row.status,
  driverId: string | null = row.driverId,
) {
  const fare = typeof trip.fare === "number" ? trip.fare : row.fare;
  await transaction
    .update(rideRequests)
    .set({
      status,
      driverId,
      fare,
      tripData: trip,
      route: (trip.route as Record<string, unknown> | undefined) ?? null,
      updatedAt: new Date(),
    })
    .where(eq(rideRequests.id, row.id));
}

router.get("/rides", async (req, res, next) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });

  try {
    const role = await getWayfareRole(userId);
    let rows: (typeof rideRequests.$inferSelect)[];
    if (role === "admin") {
      rows = await db.select().from(rideRequests).orderBy(desc(rideRequests.createdAt)).limit(500);
    } else if (role === "driver") {
      const dispatchReady = Boolean((await ensureDriverCanDispatch(userId)).profile);
      const assignedRequest = eq(rideRequests.driverId, userId);
      rows = await db
        .select()
        .from(rideRequests)
        .where(
          dispatchReady
            ? or(inArray(rideRequests.status, ["requested", "countered"]), assignedRequest)
            : assignedRequest,
        )
        .orderBy(desc(rideRequests.createdAt))
        .limit(250);
      const skips = await db
        .select({ requestId: driverRequestSkips.requestId })
        .from(driverRequestSkips)
        .where(eq(driverRequestSkips.driverId, userId));
      const skippedIds = new Set(skips.map((skip) => skip.requestId));
      const declinedOffers = rows.length
        ? await db
            .select({ requestId: rideOffers.requestId })
            .from(rideOffers)
            .where(
              and(
                eq(rideOffers.driverId, userId),
                eq(rideOffers.status, "declined"),
                inArray(
                  rideOffers.requestId,
                  rows.map((row) => row.id),
                ),
              ),
            )
        : [];
      const declinedRequestIds = new Set(
        declinedOffers.map((offer) => offer.requestId),
      );
      rows = rows.filter(
        (row) =>
          row.driverId === userId ||
          (!skippedIds.has(row.id) && !declinedRequestIds.has(row.id)),
      );
    } else {
      rows = await db
        .select()
        .from(rideRequests)
        .where(eq(rideRequests.riderId, userId))
        .orderBy(desc(rideRequests.createdAt))
        .limit(250);
    }
    const trips = await Promise.all(rows.map((row) => tripWithOffers(row, role, userId)));
    return res.json({ role, trips });
  } catch (error) {
    return next(error);
  }
});

router.post("/rides", async (req, res, next) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });

  try {
    const role = await getWayfareRole(userId);
    if (role !== "rider") return res.status(403).json({ error: "Only rider accounts can request rides." });
    if (await isUserSuspended(userId)) {
      return res.status(403).json({ error: "Your Vhaya account is suspended." });
    }
    const body = recordBody(req.body);
    if (
      body.isReturnRequest !== undefined &&
      typeof body.isReturnRequest !== "boolean"
    ) {
      return res.status(400).json({ error: "Return Request must be a boolean." });
    }
    const isReturnRequest = body.isReturnRequest === true;
    const pickup = typeof body.pickup === "string" ? body.pickup.trim() : "";
    const destination = typeof body.destination === "string" ? body.destination.trim() : "";
    if (!pickup || !destination || pickup.length > 300 || destination.length > 300) {
      return res.status(400).json({ error: "Enter a pickup and destination address." });
    }
    if (!rideCategories.has(String(body.category))) {
      return res.status(400).json({ error: "Choose a supported ride category." });
    }
    if (body.pricingMode !== "estimate" && body.pricingMode !== "offer") {
      return res.status(400).json({ error: "Choose a valid fare draft." });
    }
    const routeDraft = recordBody(body.route);
    const quote = verifyRouteQuote(routeDraft.quoteToken, { userId, pickup, destination });
    if (!quote) {
      return res.status(400).json({
        code: "ROUTE_QUOTE_EXPIRED_OR_INVALID",
        error: "Calculate the route again before requesting this ride.",
      });
    }
    const route = quote.route;
    if (route.durationSeconds <= MIN_RIDE_DURATION_SECONDS) {
      return res.status(400).json({
        code: "RIDE_DURATION_TOO_SHORT",
        error: "The driving estimate must be more than five minutes to request a ride.",
      });
    }
    const fare = body.pricingMode === "estimate" ? route.fareEstimate : body.fare;
    if (typeof fare !== "number" || !Number.isFinite(fare) || fare <= 0 || fare > 100000) {
      return res.status(400).json({ error: "Enter a valid positive fare amount." });
    }
    await syncWayfareUser(userId);
    const createdAt = Date.now();
    const id = randomUUID();
    const pickupAt = toDate(body.pickupAt);
    const routeForTrip = {
      distanceMeters: route.distanceMeters,
      durationSeconds: route.durationSeconds,
      encodedPolyline: route.encodedPolyline,
      originCoordinates: route.originCoordinates,
      destinationCoordinates: route.destinationCoordinates,
      provider: route.provider,
      fareEstimate: route.fareEstimate,
    };
    const rawPaymentMethod =
      typeof body.paymentMethod === "string"
        ? body.paymentMethod.trim().toLowerCase()
        : "card";
    if (!["card", "cash", "wallet"].includes(rawPaymentMethod)) {
      return res.status(400).json({ error: "Choose cash, wallet, or card payment." });
    }
    const paymentMethod = rawPaymentMethod as "card" | "cash" | "wallet";
    const pickupNote = optionalTextField(body, "pickupNote", 140);
    const beneficiaryName = optionalTextField(body, "beneficiaryName", 80);
    const beneficiaryPhone = optionalTextField(body, "beneficiaryPhone", 32);
    const booleanFields = [
      "isSharedRide",
      "isReturnTrip",
      "includesGravelRoad",
    ] as const;
    if (
      !pickupNote.valid ||
      !beneficiaryName.valid ||
      !beneficiaryPhone.valid ||
      booleanFields.some(
        (key) => body[key] !== undefined && typeof body[key] !== "boolean",
      )
    ) {
      return res.status(400).json({ error: "Some optional ride details are invalid." });
    }
    const cashChangeForZar =
      body.cashChangeForZar === undefined || body.cashChangeForZar === null
        ? null
        : body.cashChangeForZar;
    if (
      cashChangeForZar !== null &&
      (typeof cashChangeForZar !== "number" ||
        !Number.isInteger(cashChangeForZar) ||
        cashChangeForZar < 50 ||
        cashChangeForZar > 500 ||
        paymentMethod !== "cash")
    ) {
      return res.status(400).json({
        error: "Cash change can be selected only for cash rides, from R 50 to R 500.",
      });
    }
    const isSharedRide = body.isSharedRide === true;
    const seatCount = body.seatCount === undefined ? 1 : body.seatCount;
    if (
      typeof seatCount !== "number" ||
      !Number.isInteger(seatCount) ||
      seatCount < 1 ||
      seatCount > 4 ||
      (!isSharedRide && seatCount !== 1)
    ) {
      return res.status(400).json({ error: "Choose between one and four shared seats." });
    }
    const clientRequestId =
      typeof body.clientRequestId === "string"
        ? body.clientRequestId.trim()
        : body.clientRequestId === undefined
          ? null
          : "";
    if (clientRequestId !== null && (!clientRequestId || clientRequestId.length > 128)) {
      return res.status(400).json({ error: "The ride request reference is invalid." });
    }
    const trip: WayfareTrip = {
      id,
      riderId: userId,
      pickup,
      destination,
      category: String(body.category),
      currency: "ZAR",
      fare: Number(fare),
      pricingMode: String(body.pricingMode),
      status: "requested",
      createdAt,
      paymentMethod,
      paymentStatus: "none",
      pickupNote: pickupNote.value,
      cashChangeForZar,
      beneficiaryName: beneficiaryName.value,
      beneficiaryPhone: beneficiaryPhone.value,
      isSharedRide,
      seatCount: isSharedRide ? seatCount : 1,
      isReturnTrip: body.isReturnTrip === true,
      isReturnRequest,
      includesGravelRoad: body.includesGravelRoad === true,
      ...(clientRequestId ? { clientRequestId } : {}),
      ...(pickupAt ? { pickupAt: pickupAt.getTime() } : {}),
      pickupCoordinates: route.originCoordinates,
      destinationCoordinates: route.destinationCoordinates,
      route: routeForTrip,
    };

    let duplicateRequest: typeof rideRequests.$inferSelect | undefined;
    await db.transaction(async (tx) => {
      // Serialize concurrent requests from this rider before checking active rides.
      await tx
        .select({ id: wayfareUsers.id })
        .from(wayfareUsers)
        .where(eq(wayfareUsers.id, userId))
        .for("update");
      if (clientRequestId) {
        const duplicates = await tx
          .select()
          .from(rideRequests)
          .where(
            and(
              eq(rideRequests.riderId, userId),
              sql`${rideRequests.tripData}->>'clientRequestId' = ${clientRequestId}`,
              gte(rideRequests.createdAt, new Date(Date.now() - 24 * 60 * 60 * 1000)),
            ),
          )
          .orderBy(desc(rideRequests.createdAt))
          .limit(1)
          .for("update");
        if (duplicates[0]) {
          duplicateRequest = duplicates[0];
          return;
        }
      }
      const activeRequests = await tx
        .select({ id: rideRequests.id })
        .from(rideRequests)
        .where(
          and(
            eq(rideRequests.riderId, userId),
            notInArray(rideRequests.status, [...terminalStatuses]),
          ),
        )
        .limit(1);
      if (activeRequests[0]) {
        throw Object.assign(
          new Error("Finish or cancel your current ride request before requesting another ride."),
          { status: 409 },
        );
      }

      await tx.insert(rideRequests).values({
        id,
        riderId: userId,
        status: "requested",
        pickup,
        destination,
        category: String(body.category),
        currency: "ZAR",
        fare: Number(fare),
        pricingMode: String(body.pricingMode),
        pickupAt,
        route: routeForTrip,
        tripData: trip,
        createdAt: new Date(createdAt),
        updatedAt: new Date(createdAt),
      });
      await tx.insert(rideEvents).values({
        requestId: id,
        actorId: userId,
        action: "request_created",
        details: { market: "Vhaya", launchPolicyStatus: "active" },
      });
    });
    if (duplicateRequest) {
      const duplicateTrip = await tripWithOffers(duplicateRequest, role, userId);
      return res.status(200).json({ trip: duplicateTrip });
    }
    return res.status(201).json({ trip: { ...trip, offers: [] } });
  } catch (error) {
    const status =
      error && typeof error === "object" && "status" in error
        ? Number((error as { status: unknown }).status)
        : 0;
    if (status >= 400 && status < 600) {
      return res
        .status(status)
        .json({ error: error instanceof Error ? error.message : "The ride request could not be saved." });
    }
    return next(error);
  }
});

router.patch("/rides/:requestId/location", async (req, res, next) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });
  const requestId = req.params.requestId;
  if (typeof requestId !== "string") {
    return res.status(400).json({ error: "A valid ride request ID is required." });
  }

  try {
    const role = await getWayfareRole(userId);
    if (role !== "driver") {
      return res.status(403).json({ error: "Only the assigned driver can share trip location." });
    }
    const body = recordBody(req.body);
    const location = recordBody(body.location);
    const latitude = location.latitude;
    const longitude = location.longitude;
    const heading = location.heading;
    if (
      typeof latitude !== "number" ||
      !Number.isFinite(latitude) ||
      Math.abs(latitude) > 90 ||
      typeof longitude !== "number" ||
      !Number.isFinite(longitude) ||
      Math.abs(longitude) > 180 ||
      (heading !== undefined &&
        (typeof heading !== "number" ||
          !Number.isFinite(heading) ||
          heading < 0 ||
          heading > 360))
    ) {
      return res.status(400).json({ error: "A valid driver location is required." });
    }

    const updatedAt = Date.now();
    await db.transaction(async (tx) => {
      const selected = await tx
        .select()
        .from(rideRequests)
        .where(eq(rideRequests.id, requestId))
        .limit(1)
        .for("update");
      const row = selected[0];
      if (!row) throw Object.assign(new Error("Ride request not found."), { status: 404 });
      if (row.driverId !== userId) {
        throw Object.assign(new Error("This ride is not assigned to your driver account."), {
          status: 403,
        });
      }
      if (!["accepted", "arrived", "in_progress"].includes(row.status)) {
        throw Object.assign(new Error("Location sharing is available while a driver is heading to or carrying out a trip."), {
          status: 409,
        });
      }

      const trip = safeTripData(row.tripData);
      trip.liveDriverLocation = {
        latitude,
        longitude,
        ...(typeof heading === "number" ? { heading } : {}),
        updatedAt,
      };
      await tx
        .update(rideRequests)
        .set({ tripData: trip, updatedAt: new Date(updatedAt) })
        .where(eq(rideRequests.id, row.id));
    });
    return res.json({ updatedAt });
  } catch (error) {
    const status =
      error && typeof error === "object" && "status" in error
        ? Number((error as { status: unknown }).status)
        : 0;
    if (status >= 400 && status < 600) {
      return res
        .status(status)
        .json({ error: error instanceof Error ? error.message : "Location update failed." });
    }
    return next(error);
  }
});

router.post("/rides/:requestId/actions", async (req, res, next) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });
  const requestId = req.params.requestId;
  if (typeof requestId !== "string") {
    return res.status(400).json({ error: "A valid ride request ID is required." });
  }

  try {
    const role = await getWayfareRole(userId);
    const body = recordBody(req.body);
    const action = body.action;
    let resultTrip: Record<string, unknown> | null = null;
    let capturePaymentForRide: string | null = null;
    let releasePaymentForRide: string | null = null;

    await db.transaction(async (tx) => {
      const selected = await tx
        .select()
        .from(rideRequests)
        .where(eq(rideRequests.id, requestId))
        .limit(1)
        .for("update");
      const row = selected[0];
      if (!row) throw Object.assign(new Error("Ride request not found."), { status: 404 });
      if (role === "rider" && row.riderId !== userId) {
        throw Object.assign(new Error("You cannot change another rider’s request."), { status: 403 });
      }
      if (role === "driver" && !["requested", "countered"].includes(row.status) && row.driverId !== userId) {
        throw Object.assign(new Error("This request is not assigned to your driver account."), { status: 403 });
      }
      if (role === "admin" && action !== "adminCancel" && action !== "message") {
        throw Object.assign(new Error("Operations staff cannot perform this trip action."), { status: 403 });
      }
      const trip = safeTripData(row.tripData);

      if (action === "skip") {
        if (role !== "driver" || !["requested", "countered"].includes(row.status)) {
          throw Object.assign(new Error("This request cannot be skipped."), { status: 409 });
        }
        const ready = await ensureDriverCanDispatch(userId);
        if (!ready.profile) throw Object.assign(new Error(ready.error ?? "Driver is not ready."), { status: 403 });
        await tx
          .insert(driverRequestSkips)
          .values({ requestId: row.id, driverId: userId })
          .onConflictDoNothing();
        await tx.insert(rideEvents).values({ requestId: row.id, actorId: userId, action: "driver_skipped" });
      } else if (action === "accept") {
        if (role !== "driver" || !["requested", "countered"].includes(row.status)) {
          throw Object.assign(new Error("This request cannot be accepted with this action."), { status: 409 });
        }
        if (await hasDeclinedOfferForRequest(tx, row.id, userId)) {
          throw Object.assign(new Error("You are no longer able to respond to this ride request."), { status: 409 });
        }
        const ready = await ensureDriverCanDispatch(userId);
        if (!ready.profile) throw Object.assign(new Error(ready.error ?? "Driver is not ready."), { status: 403 });
        const user = await syncWayfareUser(userId);
        const profile = driverSummary(user.displayName, userId, ready.profile, row.fare);
        await tx
          .insert(rideOffers)
          .values({
            id: randomUUID(),
            requestId: row.id,
            driverId: userId,
            amount: row.fare,
            status: "pending",
            driverProfile: profile,
            createdAt: new Date(),
            updatedAt: new Date(),
          })
          .onConflictDoUpdate({
            target: [rideOffers.requestId, rideOffers.driverId],
            set: {
              amount: row.fare,
              status: "pending",
              driverProfile: profile,
              updatedAt: new Date(),
            },
          });
        trip.status = "countered";
        await saveTrip(tx, row, trip, "countered");
        await tx.insert(rideEvents).values({
          requestId: row.id,
          actorId: userId,
          action: "driver_accepted",
          details: { amount: row.fare, currency: row.currency },
        });
      } else if (action === "counter") {
        const amount = body.amount;
        if (
          role !== "driver" ||
          row.pricingMode !== "offer" ||
          !["requested", "countered"].includes(row.status) ||
          typeof amount !== "number" ||
          !Number.isFinite(amount) ||
          amount <= 0 ||
          amount > 100000
        ) {
          throw Object.assign(new Error("Enter a valid counteroffer for an open rider-offer request."), { status: 400 });
        }
        if (await hasDeclinedOfferForRequest(tx, row.id, userId)) {
          throw Object.assign(new Error("You are no longer able to respond to this ride request."), { status: 409 });
        }
        const ready = await ensureDriverCanDispatch(userId);
        if (!ready.profile) throw Object.assign(new Error(ready.error ?? "Driver is not ready."), { status: 403 });
        const user = await syncWayfareUser(userId);
        await tx
          .insert(rideOffers)
          .values({
            id: randomUUID(),
            requestId: row.id,
            driverId: userId,
            amount: Number(amount),
            status: "pending",
            driverProfile: driverSummary(user.displayName, userId, ready.profile, Number(amount)),
            createdAt: new Date(),
            updatedAt: new Date(),
          })
          .onConflictDoUpdate({
            target: [rideOffers.requestId, rideOffers.driverId],
            set: {
              amount: Number(amount),
              status: "pending",
              driverProfile: driverSummary(user.displayName, userId, ready.profile, Number(amount)),
              updatedAt: new Date(),
            },
          });
        trip.status = "countered";
        await saveTrip(tx, row, trip, "countered");
        await tx.insert(rideEvents).values({
          requestId: row.id,
          actorId: userId,
          action: "driver_countered",
          details: { amount: Number(amount), currency: row.currency },
        });
      } else if (action === "chooseOffer") {
        if (role !== "rider" || row.riderId !== userId || row.status !== "countered") {
          throw Object.assign(new Error("This offer is no longer available."), { status: 409 });
        }
        const offerId = typeof body.offerId === "string" ? body.offerId : "";
        const matchingOffers = await tx
          .select()
          .from(rideOffers)
          .where(and(eq(rideOffers.id, offerId), eq(rideOffers.requestId, row.id)))
          .limit(1);
        const offer = matchingOffers[0];
        if (!offer || offer.status !== "pending") {
          throw Object.assign(new Error("Choose an active driver offer."), { status: 404 });
        }
        const offerDriver = await loadDriverProfile(offer.driverId);
        if (
          (await getWayfareRole(offer.driverId)) !== "driver" ||
          !offerDriver ||
          offerDriver.applicationStatus !== "approved" ||
          offerDriver.verificationStatus !== "approved" ||
          !offerDriver.isOnline
        ) {
          throw Object.assign(new Error("This driver is no longer available for dispatch."), { status: 409 });
        }
        trip.status = "accepted";
        trip.fare = offer.amount;
        trip.driverProfile = { ...offer.driverProfile, fare: offer.amount };
        await saveTrip(tx, row, trip, "accepted", offer.driverId);
        await tx
          .update(rideOffers)
          .set({ status: "accepted", updatedAt: new Date() })
          .where(eq(rideOffers.id, offer.id));
        await tx
          .update(rideOffers)
          .set({ status: "declined", updatedAt: new Date() })
          .where(and(eq(rideOffers.requestId, row.id), eq(rideOffers.status, "pending")));
        await tx.insert(rideEvents).values({
          requestId: row.id,
          actorId: userId,
          action: "driver_offer_accepted",
          details: { offerId: offer.id },
        });
      } else if (action === "flagOffer") {
        const offerId = typeof body.offerId === "string" ? body.offerId : "";
        const message =
          typeof body.message === "string" ? body.message.trim() : "";
        if (
          role !== "rider" ||
          !["requested", "countered"].includes(row.status) ||
          !offerId ||
          !message ||
          message.length > 2000
        ) {
          throw Object.assign(
            new Error("Describe the issue with this driver before submitting the report."),
            { status: 400 },
          );
        }
        const matchingOffers = await tx
          .select()
          .from(rideOffers)
          .where(and(eq(rideOffers.id, offerId), eq(rideOffers.requestId, row.id)))
          .limit(1);
        const offer = matchingOffers[0];
        if (!offer || offer.status !== "pending") {
          throw Object.assign(new Error("This driver offer is no longer available."), { status: 409 });
        }
        const driverProfile = offer.driverProfile as Record<string, unknown>;
        const driverName =
          typeof driverProfile.name === "string" ? driverProfile.name : "the driver";
        const vehicleDetails = [
          driverProfile.vehicleColor,
          driverProfile.vehicle,
          driverProfile.registration,
        ]
          .filter((value): value is string => typeof value === "string" && Boolean(value))
          .join(" · ");
        const messageId = randomUUID();
        const createdAt = Date.now();
        const reportMessage = [
          `Driver report for ${driverName}${vehicleDetails ? ` (${vehicleDetails})` : ""}.`,
          message,
        ].join("\n");
        const messageList = Array.isArray(trip.messages) ? trip.messages : [];
        trip.messages = [
          ...messageList,
          {
            id: messageId,
            sender: "rider",
            channel: "support",
            message: reportMessage,
            category: "driver",
            priority: "normal",
            createdAt,
          },
        ];
        await tx
          .update(rideOffers)
          .set({ status: "declined", updatedAt: new Date() })
          .where(eq(rideOffers.id, offer.id));
        await saveTrip(tx, row, trip);
        await tx.insert(supportCases).values({
          requestId: row.id,
          sourceMessageId: messageId,
          reporterUserId: userId,
          category: "driver",
          priority: "normal",
        });
        await tx.insert(rideEvents).values({
          requestId: row.id,
          actorId: userId,
          action: "driver_reported",
          details: { offerId: offer.id, driverId: offer.driverId, sourceMessageId: messageId },
        });
      } else if (action === "confirmArrival") {
        if (role !== "rider" || row.riderId !== userId || row.status !== "accepted") {
          throw Object.assign(
            new Error("You can confirm the driver is here only while they are on the way."),
            { status: 409 },
          );
        }
        const confirmedAt = Date.now();
        trip.status = "arrived";
        trip.arrivalConfirmationMethod = "rider";
        trip.arrivalConfirmedAt = confirmedAt;
        trip.tripPin = String(randomInt(100000, 1000000));
        await saveTrip(tx, row, trip, "arrived");
        await tx.insert(rideEvents).values({
          requestId: row.id,
          actorId: userId,
          action: "rider_confirmed_driver_arrival",
          details: { method: "visual_confirmation", confirmedAt },
        });
      } else if (action === "advance") {
        if (role !== "driver" || row.driverId !== userId) {
          throw Object.assign(new Error("Only the assigned driver can advance this trip."), { status: 403 });
        }
        let nextStatus: string;
        if (row.status === "accepted") {
          const liveLocation = recordBody(trip.liveDriverLocation);
          const route = recordBody(trip.route ?? row.route);
          const pickupCoordinates =
            validCoordinates(trip.pickupCoordinates) ??
            validCoordinates(route.originCoordinates);
          const driverCoordinates = validCoordinates(liveLocation);
          const locationUpdatedAt = liveLocation.updatedAt;
          const now = Date.now();
          if (
            !pickupCoordinates ||
            !driverCoordinates ||
            typeof locationUpdatedAt !== "number" ||
            !Number.isFinite(locationUpdatedAt) ||
            now - locationUpdatedAt > MAX_ARRIVAL_LOCATION_AGE_MS ||
            locationUpdatedAt > now + 5_000
          ) {
            throw Object.assign(
              new Error("Refresh your location before confirming arrival at the pickup."),
              { status: 409 },
            );
          }
          if (
            distanceBetweenMeters(driverCoordinates, pickupCoordinates) >
            ARRIVAL_RADIUS_METERS
          ) {
            throw Object.assign(
              new Error("Move within 150 metres of the pickup before confirming arrival."),
              { status: 409 },
            );
          }
          nextStatus = "arrived";
          trip.arrivalConfirmationMethod = "gps";
          trip.arrivalConfirmedAt = now;
          trip.tripPin = String(randomInt(100000, 1000000));
        } else if (row.status === "arrived") {
          const route = recordBody(trip.route ?? row.route);
          const durationSeconds = route.durationSeconds;
          if (
            typeof durationSeconds !== "number" ||
            !Number.isFinite(durationSeconds) ||
            durationSeconds <= MIN_RIDE_DURATION_SECONDS
          ) {
            throw Object.assign(
              new Error("A verified route estimated at more than five minutes is required before starting this trip."),
              { status: 409 },
            );
          }
          if (typeof body.pin !== "string" || body.pin !== trip.tripPin) {
            throw Object.assign(new Error("The rider’s pickup PIN does not match."), { status: 400 });
          }
          const selectedPaymentMethod =
            typeof trip.paymentMethod === "string" ? trip.paymentMethod : "card";
          if (selectedPaymentMethod === "card") {
            const paymentRows = await tx
              .select()
              .from(ridePayments)
              .where(eq(ridePayments.requestId, row.id))
              .orderBy(desc(ridePayments.createdAt))
              .limit(1);
            const authorization = paymentRows[0];
            if (
              !authorization ||
              authorization.status !== "authorized" ||
              (authorization.expiresAt && authorization.expiresAt.getTime() <= Date.now())
            ) {
              throw Object.assign(
                new Error("Authorize the ride fare before the driver starts the trip."),
                { status: 409 },
              );
            }
          } else if (selectedPaymentMethod === "wallet") {
            const fareMinor = Math.round(row.fare * 100);
            const walletRows = await tx
              .select()
              .from(wallets)
              .where(eq(wallets.userId, row.riderId))
              .limit(1)
              .for("update");
            if (!walletRows[0] || walletRows[0].balanceMinor < fareMinor) {
              throw Object.assign(
                new Error("Rider wallet balance is insufficient to start this trip."),
                { status: 409 },
              );
            }
          }
          nextStatus = "in_progress";
          delete trip.tripPin;
        } else if (row.status === "in_progress") {
          nextStatus = "completed";
        } else {
          throw Object.assign(new Error("This trip has no next status."), { status: 409 });
        }
        trip.status = nextStatus;
        if (nextStatus === "completed") {
          delete trip.tripPin;
          delete trip.liveDriverLocation;
          const selectedPaymentMethod =
            typeof trip.paymentMethod === "string" ? trip.paymentMethod : "card";
          if (selectedPaymentMethod === "card") {
            capturePaymentForRide = row.id;
          } else if (selectedPaymentMethod === "wallet") {
            const fareMinor = Math.round(row.fare * 100);
            const walletRows = await tx
              .select()
              .from(wallets)
              .where(eq(wallets.userId, row.riderId))
              .limit(1)
              .for("update");
            const riderWallet = walletRows[0];
            if (riderWallet && trip.paymentStatus !== "paid") {
              await tx
                .update(wallets)
                .set({
                  balanceMinor: Math.max(0, riderWallet.balanceMinor - fareMinor),
                  updatedAt: new Date(),
                })
                .where(eq(wallets.id, riderWallet.id));
              await tx.insert(walletTransactions).values({
                id: `wtx-trip-${row.id}`,
                walletId: riderWallet.id,
                userId: row.riderId,
                type: "debit",
                amountMinor: -fareMinor,
                currency: row.currency,
                reference: `VH-${row.id.slice(0, 8)}`,
                description: `Ride · ${row.destination}`,
              });
            }
            trip.paymentStatus = "paid";
          } else {
            trip.paymentStatus = "paid";
          }
        }
        await saveTrip(tx, row, trip, nextStatus);
        await tx.insert(rideEvents).values({
          requestId: row.id,
          actorId: userId,
          action: `trip_${nextStatus}`,
          ...(nextStatus === "arrived"
            ? { details: { method: "gps", confirmedAt: trip.arrivalConfirmedAt } }
            : {}),
        });
      } else if (action === "cancel" || action === "adminCancel") {
        const allowed =
          action === "adminCancel"
            ? role === "admin"
            : role === "rider" &&
              row.riderId === userId &&
              ["requested", "countered", "accepted", "arrived"].includes(row.status);
        if (!allowed || terminalStatuses.has(row.status)) {
          throw Object.assign(new Error("This ride cannot be cancelled in its current status."), { status: 409 });
        }
        const driverWasOnTheWay =
          row.status === "arrived" ||
          (row.status === "accepted" &&
            (row.pickupAt === null || row.pickupAt.getTime() <= Date.now()));
        const cardWasSelected =
          trip.paymentMethod === "card" || trip.paymentMethod === undefined;
        const cancellationFee =
          role === "rider" && driverWasOnTheWay && cardWasSelected
            ? Math.round(row.fare * (riderCancellationFeePercent / 100) * 100) / 100
            : 0;
        if (cancellationFee > 0) {
          trip.cancellationFee = {
            amount: cancellationFee,
            currency: row.currency,
            percent: riderCancellationFeePercent,
            paymentMethod: "card",
            status: "due",
            collection: "not_charged",
          };
        } else {
          delete trip.cancellationFee;
        }
        trip.status = "cancelled";
        delete trip.counterOffer;
        delete trip.tripPin;
        releasePaymentForRide = row.id;
        await saveTrip(tx, row, trip, "cancelled", null);
        await tx
          .update(rideOffers)
          .set({ status: "withdrawn", updatedAt: new Date() })
          .where(and(eq(rideOffers.requestId, row.id), eq(rideOffers.status, "pending")));
        await tx.insert(rideEvents).values({
          requestId: row.id,
          actorId: userId,
          action: action === "adminCancel" ? "operations_cancelled" : "rider_cancelled",
          details:
            cancellationFee > 0
              ? {
                  cancellationFee,
                  currency: row.currency,
                  feePercent: riderCancellationFeePercent,
                  paymentMethod: "card",
                  collection: "not_charged",
                }
              : {},
        });
      } else if (action === "unfulfilled") {
        if (role !== "driver" || row.driverId !== userId || !row.pickupAt) {
          throw Object.assign(new Error("Only the assigned driver can update this reservation."), { status: 403 });
        }
        trip.status = "unfulfilled";
        trip.unfulfillmentReason = "Driver marked this reservation unable to fulfill.";
        delete trip.tripPin;
        releasePaymentForRide = row.id;
        await saveTrip(tx, row, trip, "unfulfilled");
        await tx.insert(rideEvents).values({ requestId: row.id, actorId: userId, action: "reservation_unfulfilled" });
      } else if (action === "rate") {
        const rating = body.rating;
        if (role !== "rider" || row.riderId !== userId || row.status !== "completed" ||
            typeof rating !== "number" || !Number.isInteger(rating) || rating < 1 || rating > 5) {
          throw Object.assign(new Error("A rating from 1 to 5 is available after a completed ride."), { status: 400 });
        }
        trip.riderRating = Number(rating);
        await saveTrip(tx, row, trip);
        await tx.insert(rideEvents).values({
          requestId: row.id,
          actorId: userId,
          action: "rider_rated",
          details: { rating: Number(rating) },
        });
        if (row.driverId) {
          const [aggregate] = await tx
            .select({
              averageRating: sql<number>`avg((${rideRequests.tripData}->>'riderRating')::numeric)::float8`,
              ratingCount: sql<number>`count(*)::int`,
            })
            .from(rideRequests)
            .where(
              and(
                eq(rideRequests.driverId, row.driverId),
                eq(rideRequests.status, "completed"),
                sql`${rideRequests.tripData}->>'riderRating' is not null`,
              ),
            );
          if (aggregate?.averageRating !== null && aggregate?.averageRating !== undefined) {
            await tx
              .insert(driverRatingRecords)
              .values({
                driverId: row.driverId,
                averageRating: aggregate.averageRating,
                ratingCount: aggregate.ratingCount,
                source: "verified_rider_ratings",
                updatedAt: new Date(),
              })
              .onConflictDoUpdate({
                target: driverRatingRecords.driverId,
                set: {
                  averageRating: aggregate.averageRating,
                  ratingCount: aggregate.ratingCount,
                  source: "verified_rider_ratings",
                  updatedAt: new Date(),
                },
              });
          }
        }
      } else if (action === "message") {
        const message = typeof body.message === "string" ? body.message.trim() : "";
        const channel = body.channel === "support" ? "support" : "driver";
        const supportCategory = body.category ?? "other";
        const supportPriority = body.priority ?? "normal";
        if (
          !message ||
          message.length > 2000 ||
          (role !== "rider" && role !== "driver") ||
          (role === "driver" && row.driverId !== userId) ||
          (channel === "support" &&
            (typeof supportCategory !== "string" ||
              ![
                "safety",
                "payment",
                "driver",
                "rider",
                "lost_item",
                "fare_review",
                "other",
              ].includes(supportCategory) ||
              typeof supportPriority !== "string" ||
              !["normal", "high", "urgent"].includes(supportPriority) ||
              (supportPriority === "urgent" && supportCategory !== "safety")))
        ) {
          throw Object.assign(new Error("Enter a message of 1 to 2,000 characters."), { status: 400 });
        }
        const messageList = Array.isArray(trip.messages) ? trip.messages : [];
        const messageId = randomUUID();
        const createdAt = Date.now();
        trip.messages = [
          ...messageList,
          {
            id: messageId,
            sender: role,
            channel,
            message,
            createdAt,
            ...(channel === "support"
              ? { category: supportCategory, priority: supportPriority }
              : {}),
          },
        ];
        await saveTrip(tx, row, trip);
        if (channel === "support") {
          await tx.insert(supportCases).values({
            requestId: row.id,
            sourceMessageId: messageId,
            reporterUserId: userId,
            category: supportCategory as
              | "safety"
              | "payment"
              | "driver"
              | "rider"
              | "lost_item"
              | "fare_review"
              | "other",
            priority: supportPriority as "normal" | "high" | "urgent",
          });
        }
        await tx.insert(rideEvents).values({
          requestId: row.id,
          actorId: userId,
          action: channel === "support" ? "support_message_saved" : "trip_message_sent",
          details:
            channel === "support"
              ? { supportCategory, supportPriority, sourceMessageId: messageId }
              : {},
        });
      } else {
        throw Object.assign(new Error("Unknown trip action."), { status: 400 });
      }

      const updated = await tx
        .select()
        .from(rideRequests)
        .where(eq(rideRequests.id, row.id))
        .limit(1);
      resultTrip = updated[0] ? await tripWithOffers(updated[0], role, userId) : null;
    });

    if (!resultTrip) return res.status(500).json({ error: "The ride update could not be loaded." });
    if (capturePaymentForRide) {
      try {
        await captureRidePaymentForCompletion(capturePaymentForRide, userId);
      } catch (error) {
        logger.error(
          { errorType: error instanceof Error ? error.name : "UnknownError", rideId: capturePaymentForRide },
          "Ride completed but card capture needs follow-up",
        );
      }
    }
    if (releasePaymentForRide) {
      try {
        await releaseRidePaymentForCancellation(releasePaymentForRide, userId);
      } catch (error) {
        logger.error(
          { errorType: error instanceof Error ? error.name : "UnknownError", rideId: releasePaymentForRide },
          "Ride cancelled but card hold release needs follow-up",
        );
      }
    }
    return res.json({ trip: resultTrip });
  } catch (error) {
    const status =
      error && typeof error === "object" && "status" in error
        ? Number((error as { status: unknown }).status)
        : 0;
    if (status >= 400 && status < 600) {
      res.status(status).json({ error: error instanceof Error ? error.message : "Ride update failed." });
      return;
    }
    return next(error);
  }
});

router.patch("/driver/profile/online", async (req, res, next) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });
  try {
    const role = await getWayfareRole(userId);
    if (role !== "driver") return res.status(403).json({ error: "Driver access has not been granted." });
    if (await isUserSuspended(userId)) {
      return res.status(403).json({ error: "Your Vhaya account is suspended." });
    }
    const body = recordBody(req.body);
    if (typeof body.online !== "boolean") return res.status(400).json({ error: "Choose online or offline." });
    const profile = await loadDriverProfile(userId);
    if (!profile || profile.applicationStatus !== "approved" || profile.verificationStatus !== "approved") {
      return res.status(403).json({ error: "Staff approval and verification are required before going online." });
    }
    await db
      .update(driverProfiles)
      .set({
        isOnline: body.online,
        ...(body.online
          ? {}
          : {
              locationLatitude: null,
              locationLongitude: null,
              locationUpdatedAt: null,
            }),
        updatedAt: new Date(),
      })
      .where(eq(driverProfiles.userId, userId));
    return res.json({ online: body.online });
  } catch (error) {
    return next(error);
  }
});

router.patch("/driver/profile/location", async (req, res, next) => {
  const userId = getWayfareUserId(req);
  if (!userId) {
    res.status(401).json({ error: "Sign in to continue." });
    return;
  }

  try {
    if ((await getWayfareRole(userId)) !== "driver") {
      res.status(403).json({ error: "Driver access has not been granted." });
      return;
    }
    if (await isUserSuspended(userId)) {
      res.status(403).json({ error: "Your Vhaya account is suspended." });
      return;
    }

    const parsedLocation = UpdateDriverOnlineLocationBody.safeParse(req.body);
    if (!parsedLocation.success) {
      res.status(400).json({ error: "A valid current location is required." });
      return;
    }

    const profile = await loadDriverProfile(userId);
    if (
      !profile ||
      profile.applicationStatus !== "approved" ||
      profile.verificationStatus !== "approved"
    ) {
      res.status(403).json({
        error: "Staff approval and verification are required before sharing location.",
      });
      return;
    }
    if (!profile.isOnline) {
      res.status(409).json({
        error: "Go online before sharing your current location.",
      });
      return;
    }

    const updatedAt = new Date();
    const [saved] = await db
      .update(driverProfiles)
      .set({
        locationLatitude: parsedLocation.data.latitude,
        locationLongitude: parsedLocation.data.longitude,
        locationUpdatedAt: updatedAt,
        updatedAt,
      })
      .where(
        and(
          eq(driverProfiles.userId, userId),
          eq(driverProfiles.isOnline, true),
          eq(driverProfiles.applicationStatus, "approved"),
          eq(driverProfiles.verificationStatus, "approved"),
        ),
      )
      .returning({ locationUpdatedAt: driverProfiles.locationUpdatedAt });

    if (!saved?.locationUpdatedAt) {
      res.status(409).json({
        error: "Your online status changed. Go online before sharing location.",
      });
      return;
    }

    res.json(
      UpdateDriverOnlineLocationResponse.parse({
        updatedAt: saved.locationUpdatedAt.getTime(),
      }),
    );
  } catch (error) {
    next(error);
  }
});

router.patch("/driver/application", async (req, res, next) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });
  try {
    if ((await getWayfareRole(userId)) !== "rider") {
      return res.status(403).json({
        error: "Only rider accounts can edit a driver application.",
      });
    }

    const body = recordBody(req.body);
    const contactPhone = optionalTextField(body, "contactPhone", 25);
    const city = optionalTextField(body, "city", 80);
    const vehicle = optionalTextField(body, "vehicle", 120);
    const vehicleColor = optionalTextField(body, "vehicleColor", 60);
    const registration = optionalTextField(body, "registration", 32);
    const licenceExpiry = optionalTextField(body, "licenceExpiry", 32);
    const operatingLicenceReference = optionalTextField(
      body,
      "operatingLicenceReference",
      100,
    );
    const gravelReady = body.gravelReady === true;
    if (
      !contactPhone.valid ||
      !city.valid ||
      !vehicle.valid ||
      !vehicleColor.valid ||
      !registration.valid ||
      !licenceExpiry.valid ||
      !operatingLicenceReference.valid ||
      (body.gravelReady !== undefined && typeof body.gravelReady !== "boolean") ||
      (body.documentsConsent !== undefined &&
        typeof body.documentsConsent !== "boolean")
    ) {
      return res.status(400).json({
        error: "The driver application details are invalid.",
      });
    }

    const existing = await loadDriverProfile(userId);
    if (
      existing &&
      !["draft", "rejected", "needs_follow_up"].includes(existing.applicationStatus)
    ) {
      return res.status(409).json({
        error: "This application is locked while it is being reviewed.",
      });
    }

    const now = new Date();
    const [profile] = await db
      .insert(driverProfiles)
      .values({
        userId,
        applicationStatus: "draft",
        verificationStatus: "unverified",
        contactPhone: contactPhone.value,
        city: city.value,
        vehicle: vehicle.value,
        vehicleColor: vehicleColor.value,
        registration: registration.value,
        licenceExpiry: licenceExpiry.value,
        operatingLicenceReference: operatingLicenceReference.value,
        gravelReady,
        documentsConsentAt:
          body.documentsConsent === true ? now : null,
        isOnline: false,
        locationLatitude: null,
        locationLongitude: null,
        locationUpdatedAt: null,
        updatedAt: now,
      })
      .onConflictDoUpdate({
        target: driverProfiles.userId,
        set: {
          applicationStatus: "draft",
          verificationStatus: "unverified",
          contactPhone: contactPhone.value,
          city: city.value,
          vehicle: vehicle.value,
          vehicleColor: vehicleColor.value,
          registration: registration.value,
          licenceExpiry: licenceExpiry.value,
          operatingLicenceReference: operatingLicenceReference.value,
          gravelReady,
          documentsConsentAt:
            body.documentsConsent === true ? now : null,
          isOnline: false,
          locationLatitude: null,
          locationLongitude: null,
          locationUpdatedAt: null,
          updatedAt: now,
        },
      })
      .returning();
    if (!profile) {
      return res.status(500).json({ error: "The application draft could not be saved." });
    }
    return res.json({
      profile: publicDriverProfile(profile),
    });
  } catch (error) {
    return next(error);
  }
});

router.post("/driver/application", async (req, res, next) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });
  try {
    if ((await getWayfareRole(userId)) !== "rider") {
      return res
        .status(403)
        .json({ error: "Only rider accounts can submit a driver application." });
    }

    const body = recordBody(req.body);
    const contactPhone = optionalTextField(body, "contactPhone", 25);
    const city = optionalTextField(body, "city", 80);
    const vehicle = optionalTextField(body, "vehicle", 120);
    const vehicleColor = optionalTextField(body, "vehicleColor", 60);
    const registration = optionalTextField(body, "registration", 32);
    const licenceExpiry = optionalTextField(body, "licenceExpiry", 32);
    const operatingLicenceReference = optionalTextField(
      body,
      "operatingLicenceReference",
      100,
    );
    const gravelReady = body.gravelReady === true;

    if (
      !contactPhone.valid ||
      !city.valid ||
      !vehicle.valid ||
      !vehicleColor.valid ||
      !registration.valid ||
      !licenceExpiry.valid ||
      !operatingLicenceReference.valid ||
      (body.gravelReady !== undefined && typeof body.gravelReady !== "boolean") ||
      (body.documentsConsent !== undefined &&
        typeof body.documentsConsent !== "boolean") ||
      !contactPhone.value ||
      !/^\+?[0-9()\s-]{7,25}$/.test(contactPhone.value) ||
      !city.value
    ) {
      return res.status(400).json({
        error: "The driver application details are invalid.",
      });
    }

    const existing = await loadDriverProfile(userId);
    if (
      existing &&
      !["draft", "rejected", "needs_follow_up"].includes(
        existing.applicationStatus,
      )
    ) {
      return res.status(409).json({
        error:
          "This interest has already been submitted or approved and cannot be edited.",
      });
    }

    const now = new Date();
    const [profile] = await db
      .insert(driverProfiles)
      .values({
        userId,
        applicationStatus: "submitted",
        verificationStatus: "pending",
        contactPhone: contactPhone.value,
        city: city.value,
        vehicle: vehicle.value,
        vehicleColor: vehicleColor.value,
        registration: registration.value,
        licenceExpiry: licenceExpiry.value,
        operatingLicenceReference: operatingLicenceReference.value,
        gravelReady,
        documentsConsentAt:
          body.documentsConsent === true ? new Date() : null,
        isOnline: false,
        locationLatitude: null,
        locationLongitude: null,
        locationUpdatedAt: null,
        staffNote: null,
        submittedAt: now,
        reviewedAt: null,
        updatedAt: now,
      })
      .onConflictDoUpdate({
        target: driverProfiles.userId,
        set: {
          applicationStatus: "submitted",
          verificationStatus: "pending",
          contactPhone: contactPhone.value,
          city: city.value,
          vehicle: vehicle.value,
          vehicleColor: vehicleColor.value,
          registration: registration.value,
          licenceExpiry: licenceExpiry.value,
          operatingLicenceReference: operatingLicenceReference.value,
          gravelReady,
          documentsConsentAt:
            body.documentsConsent === true ? new Date() : null,
          isOnline: false,
          locationLatitude: null,
          locationLongitude: null,
          locationUpdatedAt: null,
          staffNote: null,
          submittedAt: now,
          reviewedAt: null,
          updatedAt: now,
        },
      })
      .returning();
    if (!profile) {
      return res.status(500).json({ error: "The application could not be saved." });
    }
    return res.status(201).json({
      profile: publicDriverProfile(profile),
    });
  } catch (error) {
    return next(error);
  }
});

router.get("/driver/profile", async (req, res, next) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });
  try {
    const profile = await loadDriverProfile(userId);
    return res.json({
      profile: publicDriverProfile(profile),
    });
  } catch (error) {
    return next(error);
  }
});

router.get("/support/cases", async (req, res, next) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });
  try {
    const rows = await db
      .select({
        id: supportCases.id,
        rideId: supportCases.requestId,
        sourceMessageId: supportCases.sourceMessageId,
        category: supportCases.category,
        priority: supportCases.priority,
        status: supportCases.status,
        assignedTo: supportCases.assignedTo,
        staffNote: supportCases.staffNote,
        createdAt: supportCases.createdAt,
        updatedAt: supportCases.updatedAt,
        pickup: rideRequests.pickup,
        destination: rideRequests.destination,
        rideStatus: rideRequests.status,
        tripData: rideRequests.tripData,
      })
      .from(supportCases)
      .innerJoin(rideRequests, eq(rideRequests.id, supportCases.requestId))
      .where(eq(supportCases.reporterUserId, userId))
      .orderBy(desc(supportCases.createdAt));

    const cases = rows.map((row) => {
      const tripData = row.tripData as
        | { messages?: unknown }
        | null
        | undefined;
      const allMessages = Array.isArray(tripData?.messages)
        ? (tripData.messages as Array<{
            id?: string;
            channel?: string;
            sender?: string;
            recipientUserId?: string;
            message?: string;
            sourceMessageId?: string;
            createdAt?: number;
          }>)
        : [];
      const staffMessages = allMessages.filter(
        (message) =>
          message.channel === "support" &&
          message.sender === "support" &&
          (message.recipientUserId === undefined ||
            message.recipientUserId === userId),
      );
      const sourceMessage = allMessages.find(
        (message) => message.id === row.sourceMessageId,
      );
      const latest = staffMessages[staffMessages.length - 1];
      return {
        id: row.id,
        rideId: row.rideId,
        category: row.category,
        priority: row.priority,
        status: row.status,
        assignedTo: row.assignedTo,
        staffNote: row.staffNote,
        message: sourceMessage?.message ?? latest?.message ?? null,
        createdAt: row.createdAt.getTime(),
        updatedAt: row.updatedAt.getTime(),
        pickup: row.pickup,
        destination: row.destination,
        rideStatus: row.rideStatus,
        replies: staffMessages.map((message) => ({
          id: message.sourceMessageId ?? message.id ?? message.message ?? "",
          message: message.message ?? "",
          createdAt: message.createdAt ?? null,
        })),
      };
    });

    return res.json({ cases });
  } catch (error) {
    return next(error);
  }
});

export default router;
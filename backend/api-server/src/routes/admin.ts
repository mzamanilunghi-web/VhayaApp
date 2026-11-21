import { createHash, randomUUID } from "node:crypto";
import {
  and,
  asc,
  desc,
  eq,
  gte,
  isNull,
  lte,
  ilike,
  inArray,
  isNotNull,
  or,
  sql,
  type SQL,
} from "drizzle-orm";
import { alias } from "drizzle-orm/pg-core";
import {
  Router,
  type IRouter,
  type NextFunction,
  type Request,
  type Response,
} from "express";
import { db } from "@workspace/db";
import {
  accountDeletionRequests,
  adminReviewRecords,
  auditEvents,
  driverComplianceChecks,
  driverPayoutRecords,
  driverPayoutRecipients,
  driverProfiles,
  payoutBatches,
  ridePayments,
  rideEvents,
  rideRequests,
  supportCases,
  wayfareUsers,
  type PayoutTransferStatus,
} from "@workspace/db/schema";
import {
  CompleteAdminAccountDeletionRequestBody,
  CompleteAdminAccountDeletionRequestParams,
  CompleteAdminAccountDeletionRequestResponse,
  GetAdminAccountDeletionRequestsResponse,
  GetAdminOnlineDriversResponse,
  GetAdminRidesQueryParams,
  GetAdminRidesResponse,
  RejectAdminAccountDeletionRequestBody,
  RejectAdminAccountDeletionRequestParams,
  CreatePayoutRecipientBody,
  CreatePayoutRecipientResponse,
  GetPayoutBanksResponse,
  GetPayoutBatchDetailParams,
  GetPayoutBatchDetailResponse,
  GetPayoutRecipientsResponse,
  RecordExternalPayoutBatchPaymentBody,
  StartPayoutBatchTransfersBody,
  StartPayoutBatchTransfersParams,
  StartPayoutBatchTransfersResponse,
  VerifyPayoutTransferParams,
  VerifyPayoutTransferResponse,
} from "@workspace/api-zod";
import { getWayfareUserId } from "../middlewares/firebaseIdentity";
import {
  getWayfareRole,
  isWayfareRole,
  type WayfareRole,
  updateFirebaseRole,
} from "../middlewares/wayfareRoles";
import { recordBody } from "../lib/wayfareData";
import {
  complianceDocumentTypes,
  isDriverComplianceStale,
  missingDriverCompliance,
  normalizeComplianceExpiry,
} from "../lib/driverCompliance";
import { completeAccountDeletion } from "../lib/accountDeletion";
import { PaystackRequestError } from "../lib/paystackSubscriptions";
import {
  assertPaystackKeySafeForEnvironment,
  assertPaystackTransfersEnabled,
  createPaystackRecipient,
  createPaystackTransfer,
  listPaystackBanks,
  normalizePaystackTransferStatus,
  verifyPaystackTransfer,
} from "../lib/paystackTransfers";

const router: IRouter = Router();
const pageLimit = 50;
const maxPage = 1000;
const rideRider = alias(wayfareUsers, "ride_rider");
const rideDriver = alias(wayfareUsers, "ride_driver");
const pendingApplicationStatuses = [
  "submitted",
  "under_review",
  "needs_follow_up",
] as const;
const complianceStatuses = [
  "not_reviewed",
  "verified",
  "needs_follow_up",
  "not_required",
] as const;
const supportCategories = [
  "safety",
  "payment",
  "driver",
  "rider",
  "lost_item",
  "fare_review",
  "other",
] as const;
const supportPriorities = ["normal", "high", "urgent"] as const;
const supportStatuses = ["open", "in_progress", "resolved"] as const;
const payoutReconciliationStatuses = ["unreconciled", "matched", "exception"] as const;
const payoutProviders = ["paystack", "bank_transfer", "cash", "other"] as const;
const reviewOutcomes = [
  "reviewed",
  "needs_follow_up",
  "approved",
  "rejected",
] as const;

type ReviewOutcome = (typeof reviewOutcomes)[number];
type ReviewSubjectType = "requester" | "driver";

function routeError(
  status: number,
  message: string,
): Error & { status: number } {
  return Object.assign(new Error(message), { status });
}

function sendRouteError(
  error: unknown,
  res: Response,
  next: NextFunction,
): void {
  const status =
    error && typeof error === "object" && "status" in error
      ? Number((error as { status: unknown }).status)
      : 0;
  if (status >= 400 && status < 600) {
    res.status(status).json({
      error:
        error instanceof Error
          ? error.message
          : "The admin request could not be completed.",
    });
    return;
  }
  next(error);
}

async function requireAdmin(
  req: Request,
  res: Response,
): Promise<string | null> {
  const userId = getWayfareUserId(req);
  if (!userId) {
    res.status(401).json({ error: "Sign in to continue." });
    return null;
  }
  if ((await getWayfareRole(userId)) !== "admin") {
    res.status(403).json({ error: "Operations admin access is required." });
    return null;
  }
  return userId;
}

function stringParam(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function boundedInteger(
  value: unknown,
  fallback: number,
  minimum: number,
  maximum: number,
): number {
  if (typeof value !== "string" || !/^\d+$/.test(value)) return fallback;
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed >= minimum && parsed <= maximum
    ? parsed
    : fallback;
}

const activePayoutTransferStatuses: PayoutTransferStatus[] = [
  "sending",
  "pending",
  "otp",
  "unknown",
];

function toPayoutBatchResponse(
  batch: Pick<
    typeof payoutBatches.$inferSelect,
    | "id"
    | "periodStart"
    | "periodEnd"
    | "status"
    | "settlementMethod"
    | "totalAmountMinor"
    | "driverCount"
    | "paidAt"
    | "reconciledAt"
    | "createdAt"
  >,
  createdBy: string | null = null,
) {
  return {
    id: batch.id,
    periodStart: batch.periodStart.getTime(),
    periodEnd: batch.periodEnd.getTime(),
    status: batch.status,
    settlementMethod: batch.settlementMethod,
    totalAmountMinor: batch.totalAmountMinor,
    driverCount: batch.driverCount,
    paidAt: batch.paidAt?.getTime() ?? null,
    reconciledAt: batch.reconciledAt?.getTime() ?? null,
    createdAt: batch.createdAt.getTime(),
    createdBy,
  };
}

function toPayoutTransferOutcome(
  payout: typeof driverPayoutRecords.$inferSelect,
) {
  return {
    payoutId: payout.id,
    transferStatus: payout.transferStatus,
    transferReference: payout.transferReference,
    transferCode: payout.transferCode,
    transferError: payout.transferError,
    transferUpdatedAt: payout.transferUpdatedAt?.getTime() ?? null,
  };
}

type PayoutBatchReviewRow = {
  payoutId: number;
  driverId: string;
  driverName: string;
  amountMinor: number;
  currency: string;
  transferStatus: PayoutTransferStatus;
  transferReference: string | null;
  transferAttemptCount: number;
  recipientCode: string | null;
  recipientName: string | null;
  bankName: string | null;
  accountLast4: string | null;
};

function createPayoutBatchReviewToken(
  batchId: string,
  rows: PayoutBatchReviewRow[],
): string {
  const snapshot = rows
    .slice()
    .sort((a, b) => a.payoutId - b.payoutId)
    .map((row) => ({
      payoutId: row.payoutId,
      driverId: row.driverId,
      driverName: row.driverName,
      amountMinor: row.amountMinor,
      currency: row.currency,
      transferStatus: row.transferStatus,
      transferReference: row.transferReference,
      transferAttemptCount: row.transferAttemptCount,
      recipientCode: row.recipientCode,
      recipientName: row.recipientName,
      bankName: row.bankName,
      accountLast4: row.accountLast4,
    }));
  return createHash("sha256")
    .update(JSON.stringify({ batchId, payouts: snapshot }))
    .digest("hex");
}

async function attachLegacyBatchPayouts(
  batch: typeof payoutBatches.$inferSelect,
): Promise<void> {
  const periodStart = batch.periodStart.toISOString();
  const periodEnd = batch.periodEnd.toISOString();
  await db.transaction(async (tx) => {
    const legacyRows = await tx
      .select({ id: driverPayoutRecords.id })
      .from(driverPayoutRecords)
      .where(
        and(
          isNull(driverPayoutRecords.batchId),
          eq(driverPayoutRecords.provider, "paystack"),
          eq(driverPayoutRecords.periodStart, periodStart),
          eq(driverPayoutRecords.periodEnd, periodEnd),
          ilike(driverPayoutRecords.externalReference, "payout-%"),
        ),
      )
      .for("update");
    for (const row of legacyRows) {
      await tx
        .update(driverPayoutRecords)
        .set({
          batchId: batch.id,
          transferReference: `payout_${randomUUID()}`,
        })
        .where(
          and(
            eq(driverPayoutRecords.id, row.id),
            isNull(driverPayoutRecords.batchId),
          ),
        );
    }
  });
}

async function refreshPayoutBatchStatus(
  batchId: string,
): Promise<typeof payoutBatches.$inferSelect | undefined> {
  const [batch] = await db
    .select()
    .from(payoutBatches)
    .where(eq(payoutBatches.id, batchId))
    .limit(1);
  if (!batch || batch.settlementMethod === "external") return batch;
  const rows = await db
    .select({ transferStatus: driverPayoutRecords.transferStatus })
    .from(driverPayoutRecords)
    .where(eq(driverPayoutRecords.batchId, batchId));
  if (!rows.length) return batch;

  const statuses = rows.map((row) => row.transferStatus);
  const hasSuccess = statuses.includes("success");
  const hasActive = statuses.some((status) =>
    activePayoutTransferStatuses.includes(status),
  );
  const hasNotStarted = statuses.includes("not_started");
  let status: typeof batch.status;
  if (statuses.every((value) => value === "success")) {
    status = "paid";
  } else if (
    hasActive ||
    (hasNotStarted && statuses.some((value) => value !== "not_started"))
  ) {
    status = "processing";
  } else if (hasSuccess) {
    status = "partially_paid";
  } else if (statuses.every((value) => value === "failed" || value === "reversed")) {
    status = "failed";
  } else {
    status = "submitted";
  }

  const now = new Date();
  const [updated] = await db
    .update(payoutBatches)
    .set({
      status,
      settlementMethod:
        statuses.some((value) => value !== "not_started")
          ? "paystack"
          : batch.settlementMethod,
      paidAt: status === "paid" ? batch.paidAt ?? now : null,
      updatedAt: now,
    })
    .where(eq(payoutBatches.id, batchId))
    .returning();
  return updated;
}

function isIsoCalendarDate(value: unknown): value is string {
  if (typeof value !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return false;
  }
  const parsed = new Date(`${value}T00:00:00.000Z`);
  return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value;
}

function launchMarketToday(): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Africa/Johannesburg",
  }).format(new Date());
}

function complianceDocumentLabel(documentType: string): string {
  return documentType
    .split("_")
    .map((part) => part[0]?.toUpperCase() + part.slice(1))
    .join(" ");
}

function roleLabel(value: unknown): WayfareRole {
  return isWayfareRole(value) ? value : "rider";
}

function personTypeFor(role: WayfareRole): "requester" | "driver" {
  return role === "driver" ? "driver" : "requester";
}

async function loadPersonSummaries(where: SQL, limit: number, offset = 0) {
  const rows = await db
    .select({
      userId: wayfareUsers.id,
      displayName: wayfareUsers.displayName,
      email: wayfareUsers.email,
      role: wayfareUsers.role,
      isSuspended: wayfareUsers.isSuspended,
      suspendedReason: wayfareUsers.suspendedReason,
      updatedAt: wayfareUsers.updatedAt,
      applicationStatus: driverProfiles.applicationStatus,
      verificationStatus: driverProfiles.verificationStatus,
      vehicle: driverProfiles.vehicle,
      vehicleColor: driverProfiles.vehicleColor,
      registration: driverProfiles.registration,
      licenceExpiry: driverProfiles.licenceExpiry,
      operatingLicenceReference: driverProfiles.operatingLicenceReference,
      isOnline: driverProfiles.isOnline,
      staffNote: driverProfiles.staffNote,
      submittedAt: driverProfiles.submittedAt,
      reviewedAt: driverProfiles.reviewedAt,
      driverUpdatedAt: driverProfiles.updatedAt,
      rideCount: sql<number>`count(distinct ${rideRequests.id})::int`,
      completedRideCount: sql<number>`(count(distinct ${rideRequests.id}) filter (where ${rideRequests.status} = 'completed'))::int`,
      lastRideAt: sql<Date | null>`max(${rideRequests.createdAt})`,
    })
    .from(wayfareUsers)
    .leftJoin(driverProfiles, eq(driverProfiles.userId, wayfareUsers.id))
    .leftJoin(
      rideRequests,
      or(
        eq(rideRequests.riderId, wayfareUsers.id),
        eq(rideRequests.driverId, wayfareUsers.id),
      ),
    )
    .where(and(where, isNull(wayfareUsers.deletedAt)))
    .groupBy(wayfareUsers.id, driverProfiles.userId)
    .orderBy(desc(wayfareUsers.updatedAt), asc(wayfareUsers.id))
    .limit(limit)
    .offset(offset);

  const userIds = rows.map((row) => row.userId);
  const latestReviews = userIds.length
    ? await db
        .select({
          subjectUserId: adminReviewRecords.subjectUserId,
          outcome: adminReviewRecords.outcome,
          createdAt: adminReviewRecords.createdAt,
        })
        .from(adminReviewRecords)
        .where(inArray(adminReviewRecords.subjectUserId, userIds))
        .orderBy(desc(adminReviewRecords.createdAt))
    : [];
  const reviewsByUser = new Map<
    string,
    { outcome: ReviewOutcome; createdAt: Date }
  >();
  for (const review of latestReviews) {
    if (!reviewsByUser.has(review.subjectUserId)) {
      reviewsByUser.set(review.subjectUserId, {
        outcome: review.outcome,
        createdAt: review.createdAt,
      });
    }
  }

  return rows.map((row) => {
    const role = roleLabel(row.role);
    const driverProfile =
      row.applicationStatus === null
        ? null
        : {
            applicationStatus: row.applicationStatus,
            verificationStatus: row.verificationStatus ?? "unverified",
            vehicle: row.vehicle,
            vehicleColor: row.vehicleColor,
            registration: row.registration,
            licenceExpiry: row.licenceExpiry,
            operatingLicenceReference: row.operatingLicenceReference,
            isOnline: row.isOnline ?? false,
            staffNote: row.staffNote,
            submittedAt: row.submittedAt,
            reviewedAt: row.reviewedAt,
            updatedAt: row.driverUpdatedAt ?? row.updatedAt,
          };
    const latestReview = reviewsByUser.get(row.userId);
    return {
      userId: row.userId,
      displayName: row.displayName,
      email: row.email,
      role,
      isSuspended: row.isSuspended,
      suspendedReason: row.suspendedReason,
      personType: personTypeFor(role),
      rideCount: row.rideCount,
      completedRideCount: row.completedRideCount,
      lastRideAt: row.lastRideAt,
      driverProfile,
      lastReviewOutcome: latestReview?.outcome ?? null,
      lastReviewedAt: latestReview?.createdAt ?? null,
      updatedAt: row.updatedAt,
    };
  });
}

router.get("/admin/session", async (req, res, next) => {
  try {
    const userId = await requireAdmin(req, res);
    if (!userId) return;
    const users = await db
      .select({
        userId: wayfareUsers.id,
        displayName: wayfareUsers.displayName,
        email: wayfareUsers.email,
      })
      .from(wayfareUsers)
      .where(eq(wayfareUsers.id, userId))
      .limit(1);
    if (!users[0])
      return res.status(404).json({ error: "Vhaya account was not found." });
    return res.json(users[0]);
  } catch (error) {
    return sendRouteError(error, res, next);
  }
});

const onlineDriverLocationMaxAgeMs = 90_000;
const onlineDriverLocationRetentionMs = 5 * 60_000;

router.get("/admin/online-drivers", async (req, res, next) => {
  try {
    const actorId = await requireAdmin(req, res);
    if (!actorId) return;

    const generatedAt = Date.now();
    const expiredBefore = new Date(generatedAt - onlineDriverLocationRetentionMs);
    await db
      .update(driverProfiles)
      .set({
        locationLatitude: null,
        locationLongitude: null,
        locationUpdatedAt: null,
      })
      .where(
        and(
          eq(driverProfiles.isOnline, true),
          isNotNull(driverProfiles.locationUpdatedAt),
          lte(driverProfiles.locationUpdatedAt, expiredBefore),
        ),
      );

    const rows = await db
      .select({
        driverId: driverProfiles.userId,
        displayName: wayfareUsers.displayName,
        city: driverProfiles.city,
        vehicle: driverProfiles.vehicle,
        vehicleColor: driverProfiles.vehicleColor,
        latitude: driverProfiles.locationLatitude,
        longitude: driverProfiles.locationLongitude,
        locationUpdatedAt: driverProfiles.locationUpdatedAt,
      })
      .from(driverProfiles)
      .innerJoin(wayfareUsers, eq(wayfareUsers.id, driverProfiles.userId))
      .where(
        and(
          eq(driverProfiles.isOnline, true),
          eq(driverProfiles.applicationStatus, "approved"),
          eq(driverProfiles.verificationStatus, "approved"),
          eq(wayfareUsers.role, "driver"),
          eq(wayfareUsers.isSuspended, false),
          isNull(wayfareUsers.deletedAt),
        ),
      )
      .orderBy(asc(driverProfiles.city), asc(wayfareUsers.displayName))
      .limit(500);

    const response = GetAdminOnlineDriversResponse.parse({
      generatedAt,
      drivers: rows.map((row) => {
        const lastLocationAt = row.locationUpdatedAt?.getTime() ?? null;
        const ageMs =
          lastLocationAt === null ? null : generatedAt - lastLocationAt;
        const locationIsCurrent =
          ageMs !== null &&
          ageMs >= 0 &&
          ageMs <= onlineDriverLocationMaxAgeMs &&
          typeof row.latitude === "number" &&
          typeof row.longitude === "number";
        const locationState = locationIsCurrent
          ? "current"
          : lastLocationAt === null
            ? "waiting"
            : "stale";

        return {
          driverId: row.driverId,
          displayName: row.displayName || "Driver",
          city: row.city,
          vehicle: row.vehicle,
          vehicleColor: row.vehicleColor,
          location: locationIsCurrent
            ? {
                latitude: row.latitude,
                longitude: row.longitude,
                updatedAt: lastLocationAt,
              }
            : null,
          locationState,
          lastLocationAt,
        };
      }),
    });

    res.json(response);
  } catch (error) {
    sendRouteError(error, res, next);
  }
});

router.get("/admin/overview", async (req, res, next) => {
  try {
    const actorId = await requireAdmin(req, res);
    if (!actorId) return;

    const failedPaymentStatuses = [
      "capture_failed",
      "release_failed",
      "capture_pending",
      "release_pending",
    ];
    const thirtyDaysAgo = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000);

    const [
      [userCounts],
      [rideCounts],
      [driverCounts],
      [paymentCounts],
      [supportCounts],
      casesByCategory,
      casesByPriority,
    ] = await Promise.all([
      db
        .select({
          total: sql<number>`count(*)::int`,
          suspended: sql<number>`count(*) filter (where ${wayfareUsers.isSuspended})::int`,
        })
        .from(wayfareUsers)
        .where(isNull(wayfareUsers.deletedAt)),
      db
        .select({
          total: sql<number>`count(*)::int`,
          active: sql<number>`count(*) filter (where ${rideRequests.status} in ('accepted', 'arrived', 'in_progress'))::int`,
        })
        .from(rideRequests),
      db
        .select({ pending: sql<number>`count(*)::int` })
        .from(driverProfiles)
        .where(inArray(driverProfiles.applicationStatus, [...pendingApplicationStatuses])),
      db
        .select({ issues: sql<number>`count(*)::int` })
        .from(ridePayments)
        .where(inArray(ridePayments.status, failedPaymentStatuses)),
      db
        .select({
          active: sql<number>`count(*) filter (where ${supportCases.status} in ('open', 'in_progress'))::int`,
          urgent: sql<number>`count(*) filter (where ${supportCases.status} in ('open', 'in_progress') and ${supportCases.priority} = 'urgent')::int`,
          unassigned: sql<number>`count(*) filter (where ${supportCases.status} in ('open', 'in_progress') and ${supportCases.assignedTo} is null)::int`,
          last30Days: sql<number>`count(*) filter (where ${supportCases.createdAt} >= ${thirtyDaysAgo})::int`,
        })
        .from(supportCases),
      db
        .select({
          category: supportCases.category,
          count: sql<number>`count(*)::int`,
        })
        .from(supportCases)
        .where(gte(supportCases.createdAt, thirtyDaysAgo))
        .groupBy(supportCases.category)
        .orderBy(desc(sql`count(*)`)),
      db
        .select({
          priority: supportCases.priority,
          count: sql<number>`count(*)::int`,
        })
        .from(supportCases)
        .where(gte(supportCases.createdAt, thirtyDaysAgo))
        .groupBy(supportCases.priority)
        .orderBy(desc(sql`count(*)`)),
    ]);

    return res.json({
      generatedAt: Date.now(),
      summary: {
        users: userCounts?.total ?? 0,
        suspendedUsers: userCounts?.suspended ?? 0,
        rideRequests: rideCounts?.total ?? 0,
        activeRides: rideCounts?.active ?? 0,
        pendingDriverReviews: driverCounts?.pending ?? 0,
        paymentIssues: paymentCounts?.issues ?? 0,
        activeSupportCases: supportCounts?.active ?? 0,
        urgentSupportCases: supportCounts?.urgent ?? 0,
        unassignedSupportCases: supportCounts?.unassigned ?? 0,
      },
      support: {
        last30Days: supportCounts?.last30Days ?? 0,
        byCategory: casesByCategory.map((row) => ({
          category: row.category,
          count: row.count,
        })),
        byPriority: casesByPriority.map((row) => ({
          priority: row.priority,
          count: row.count,
        })),
      },
    });
  } catch (error) {
    return sendRouteError(error, res, next);
  }
});

router.get("/admin/activity", async (req, res, next) => {
  try {
    const actorId = await requireAdmin(req, res);
    if (!actorId) return;
    const limit = boundedInteger(stringParam(req.query.limit), 50, 1, 100);

    const [reviews, operationalEvents] = await Promise.all([
      db
        .select({
          id: adminReviewRecords.id,
          subjectUserId: adminReviewRecords.subjectUserId,
          actorUserId: adminReviewRecords.actorUserId,
          subjectType: adminReviewRecords.subjectType,
          outcome: adminReviewRecords.outcome,
          note: adminReviewRecords.note,
          roleBefore: adminReviewRecords.roleBefore,
          roleAfter: adminReviewRecords.roleAfter,
          applicationStatusBefore: adminReviewRecords.applicationStatusBefore,
          applicationStatusAfter: adminReviewRecords.applicationStatusAfter,
          createdAt: adminReviewRecords.createdAt,
        })
        .from(adminReviewRecords)
        .orderBy(desc(adminReviewRecords.createdAt))
        .limit(limit),
      db
        .select({
          id: rideEvents.id,
          requestId: rideEvents.requestId,
          actorUserId: rideEvents.actorId,
          action: rideEvents.action,
          details: rideEvents.details,
          createdAt: rideEvents.createdAt,
        })
        .from(rideEvents)
        .where(
          inArray(rideEvents.action, [
            "support_case_updated",
            "support_reply_sent",
            "refund_requested",
            "operations_cancelled",
          ]),
        )
        .orderBy(desc(rideEvents.createdAt))
        .limit(limit),
    ]);

    const userIds = [
      ...new Set([
        ...reviews.flatMap((event) => [event.actorUserId, event.subjectUserId]),
        ...operationalEvents.flatMap((event) =>
          event.actorUserId ? [event.actorUserId] : [],
        ),
      ]),
    ];
    const people = userIds.length
      ? await db
          .select({ id: wayfareUsers.id, displayName: wayfareUsers.displayName })
          .from(wayfareUsers)
          .where(inArray(wayfareUsers.id, userIds))
      : [];
    const names = new Map(people.map((person) => [person.id, person.displayName]));

    const activity = [
      ...reviews.map((event) => ({
        id: `review-${event.id}`,
        kind: event.subjectType === "driver" ? "Driver review" : "Account review",
        actor: names.get(event.actorUserId) ?? "Operations admin",
        subject: names.get(event.subjectUserId) ?? "Account",
        summary:
          event.roleBefore && event.roleAfter && event.roleBefore !== event.roleAfter
            ? `Role changed from ${event.roleBefore} to ${event.roleAfter}`
            : event.applicationStatusBefore &&
                event.applicationStatusAfter &&
                event.applicationStatusBefore !== event.applicationStatusAfter
              ? `Driver application moved from ${event.applicationStatusBefore} to ${event.applicationStatusAfter}`
              : event.note?.slice(0, 180) || event.outcome.replaceAll("_", " "),
        createdAt: event.createdAt.getTime(),
      })),
      ...operationalEvents.map((event) => {
        const details = event.details ?? {};
        const caseId =
          typeof details["caseId"] === "number" ? details["caseId"] : null;
        const paymentId =
          typeof details["paymentId"] === "string" ? details["paymentId"] : null;
        const isSupportEvent = event.action.startsWith("support_");
        const kind =
          event.action === "support_reply_sent"
            ? "Support reply"
            : event.action === "support_case_updated"
              ? "Support case update"
              : event.action === "refund_requested"
                ? "Refund requested"
                : "Ride cancelled by operations";
        return {
          id: `ride-${event.id}`,
          kind,
          actor: event.actorUserId
            ? names.get(event.actorUserId) ?? "Operations admin"
            : "Operations admin",
          subject: isSupportEvent
            ? caseId
              ? `Support case #${caseId}`
              : "Support case"
            : paymentId
              ? `Payment ${paymentId.slice(0, 8)}`
              : `Ride ${event.requestId.slice(0, 8)}`,
          summary: isSupportEvent
            ? event.action === "support_reply_sent"
              ? "Staff reply recorded"
              : "Case status, assignment, priority, or note updated"
            : event.action === "refund_requested"
              ? "Refund request recorded; provider completion is pending"
              : "Operations cancelled this ride request",
          createdAt: event.createdAt.getTime(),
        };
      }),
    ]
      .sort((left, right) => right.createdAt - left.createdAt)
      .slice(0, limit);

    return res.json({ activity });
  } catch (error) {
    return sendRouteError(error, res, next);
  }
});

router.get("/admin/rides", async (req, res, next) => {
  try {
    const actorId = await requireAdmin(req, res);
    if (!actorId) return;

    const parsedFilters = GetAdminRidesQueryParams.safeParse(req.query);
    if (!parsedFilters.success) {
      return res.status(400).json({ error: "Choose valid ride filters." });
    }
    const { q, status, page, limit } = parsedFilters.data;
    const filters: SQL[] = [];
    if (status !== "all") {
      filters.push(eq(rideRequests.status, status));
    }
    if (q) {
      const pattern = `%${q.trim().replace(/[\\%_]/g, "\\$&")}%`;
      filters.push(
        or(
          ilike(rideRequests.id, pattern),
          ilike(rideRequests.pickup, pattern),
          ilike(rideRequests.destination, pattern),
          ilike(rideRider.displayName, pattern),
          ilike(rideRider.email, pattern),
          ilike(rideDriver.displayName, pattern),
          ilike(rideDriver.email, pattern),
        )!,
      );
    }

    const rows = await db
      .select({
        id: rideRequests.id,
        riderId: rideRequests.riderId,
        riderName: rideRider.displayName,
        riderEmail: rideRider.email,
        driverId: rideRequests.driverId,
        driverName: rideDriver.displayName,
        driverEmail: rideDriver.email,
        status: rideRequests.status,
        pickupLabel: rideRequests.pickup,
        destinationLabel: rideRequests.destination,
        category: rideRequests.category,
        fare: rideRequests.fare,
        currency: rideRequests.currency,
        pickupAt: rideRequests.pickupAt,
        createdAt: rideRequests.createdAt,
        updatedAt: rideRequests.updatedAt,
      })
      .from(rideRequests)
      .innerJoin(rideRider, eq(rideRider.id, rideRequests.riderId))
      .leftJoin(rideDriver, eq(rideDriver.id, rideRequests.driverId))
      .where(filters.length ? and(...filters) : undefined)
      .orderBy(desc(rideRequests.createdAt))
      .limit(limit + 1)
      .offset(page * limit);

    const response = GetAdminRidesResponse.parse({
      rides: rows.slice(0, limit).map((ride) => ({
        id: ride.id,
        riderId: ride.riderId,
        riderName: ride.riderName || ride.riderEmail || "Unknown rider",
        riderEmail: ride.riderEmail,
        driverId: ride.driverId,
        driverName: ride.driverName || ride.driverEmail || null,
        driverEmail: ride.driverEmail,
        status: ride.status,
        pickupLabel: ride.pickupLabel,
        destinationLabel: ride.destinationLabel,
        category: ride.category,
        fareMinor: Math.round(ride.fare * 100),
        currency: ride.currency,
        pickupAt: ride.pickupAt?.getTime() ?? null,
        createdAt: ride.createdAt.getTime(),
        updatedAt: ride.updatedAt.getTime(),
      })),
      page,
      limit,
      hasMore: rows.length > limit,
    });
    return res.json(response);
  } catch (error) {
    return sendRouteError(error, res, next);
  }
});

router.get("/admin/people", async (req, res, next) => {
  try {
    const actorId = await requireAdmin(req, res);
    if (!actorId) return;
    const query = stringParam(req.query.q).trim();
    if (query.length === 1 || query.length > 100) {
      return res
        .status(400)
        .json({ error: "Search text must contain 2 to 100 characters." });
    }
    const type = stringParam(req.query.type) || "all";
    if (!["all", "requesters", "drivers"].includes(type)) {
      return res
        .status(400)
        .json({ error: "Choose all, requesters, or drivers." });
    }
    const page = boundedInteger(req.query.page, 0, 0, maxPage);
    const limit = boundedInteger(req.query.limit, 25, 1, pageLimit);
    const conditions: SQL[] = [];
    if (query.length >= 2) {
      const escapedQuery = query.replace(/[\\%_]/g, "\\$&");
      const queryPattern = `%${escapedQuery}%`;
      conditions.push(
        or(
          ilike(wayfareUsers.displayName, queryPattern),
          ilike(wayfareUsers.email, queryPattern),
        )!,
      );
    }
    if (type === "requesters") conditions.push(eq(wayfareUsers.role, "rider"));
    if (type === "drivers") conditions.push(eq(wayfareUsers.role, "driver"));

    const peopleWithExtra = await loadPersonSummaries(
      conditions.length > 0 ? and(...conditions)! : sql`true`,
      limit + 1,
      page * limit,
    );
    const hasMore = peopleWithExtra.length > limit;
    return res.json({
      people: peopleWithExtra.slice(0, limit),
      page,
      limit,
      hasMore,
    });
  } catch (error) {
    return sendRouteError(error, res, next);
  }
});

router.get("/admin/driver-applications", async (req, res, next) => {
  try {
    const actorId = await requireAdmin(req, res);
    if (!actorId) return;
    const applications = await db
      .select({
        userId: wayfareUsers.id,
        displayName: wayfareUsers.displayName,
        email: wayfareUsers.email,
        applicationStatus: driverProfiles.applicationStatus,
        verificationStatus: driverProfiles.verificationStatus,
        contactPhone: driverProfiles.contactPhone,
        city: driverProfiles.city,
        vehicle: driverProfiles.vehicle,
        vehicleColor: driverProfiles.vehicleColor,
        registration: driverProfiles.registration,
        licenceExpiry: driverProfiles.licenceExpiry,
        operatingLicenceReference: driverProfiles.operatingLicenceReference,
        staffNote: driverProfiles.staffNote,
        submittedAt: driverProfiles.submittedAt,
        reviewedAt: driverProfiles.reviewedAt,
      })
      .from(driverProfiles)
      .innerJoin(wayfareUsers, eq(wayfareUsers.id, driverProfiles.userId))
      .where(
        inArray(driverProfiles.applicationStatus, [
          ...pendingApplicationStatuses,
        ]),
      )
      .orderBy(asc(driverProfiles.submittedAt), asc(wayfareUsers.displayName))
      .limit(100);
    const userIds = applications.map((application) => application.userId);
    const complianceRows = userIds.length
      ? await db
          .select({
            userId: driverComplianceChecks.userId,
            documentType: driverComplianceChecks.documentType,
            status: driverComplianceChecks.status,
            expiresOn: driverComplianceChecks.expiresOn,
            staffNote: driverComplianceChecks.staffNote,
            reviewedAt: driverComplianceChecks.reviewedAt,
          })
          .from(driverComplianceChecks)
          .where(inArray(driverComplianceChecks.userId, userIds))
      : [];
    const checksByUser = new Map<
      string,
      Map<
        string,
        {
          status: string;
          expiresOn: string | null;
          staffNote: string | null;
          reviewedAt: Date | null;
        }
      >
    >();
    for (const check of complianceRows) {
      const checks = checksByUser.get(check.userId) ?? new Map();
      checks.set(check.documentType, {
        status: check.status,
        expiresOn: check.expiresOn,
        staffNote: check.staffNote,
        reviewedAt: check.reviewedAt,
      });
      checksByUser.set(check.userId, checks);
    }
    return res.json({
      applications: applications.map((application) => {
        const checks = checksByUser.get(application.userId);
        const complianceChecks = complianceDocumentTypes.map((documentType) => {
          const stored = checks?.get(documentType);
          const needsRecheck = isDriverComplianceStale(
            stored?.reviewedAt ?? null,
            application.submittedAt,
          );
          return {
            documentType,
            status: needsRecheck
              ? "needs_follow_up"
              : stored?.status ?? "not_reviewed",
            expiresOn:
              stored?.expiresOn ??
              (documentType === "driver_license"
                ? application.licenceExpiry
                : null),
            staffNote: stored?.staffNote ?? null,
            reviewedAt: stored?.reviewedAt?.getTime() ?? null,
          };
        });
        return {
          ...application,
          submittedAt: application.submittedAt?.getTime() ?? null,
          reviewedAt: application.reviewedAt?.getTime() ?? null,
          complianceChecks,
          incompleteComplianceDocuments: missingDriverCompliance(
            complianceChecks,
            launchMarketToday(),
          ),
        };
      }),
    });
  } catch (error) {
    return sendRouteError(error, res, next);
  }
});

router.get("/admin/driver-compliance", async (req, res, next) => {
  try {
    const actorId = await requireAdmin(req, res);
    if (!actorId) return;
    const drivers = await db
      .select({
        userId: wayfareUsers.id,
        displayName: wayfareUsers.displayName,
        email: wayfareUsers.email,
        role: wayfareUsers.role,
        applicationStatus: driverProfiles.applicationStatus,
        verificationStatus: driverProfiles.verificationStatus,
        vehicle: driverProfiles.vehicle,
        registration: driverProfiles.registration,
        licenceExpiry: driverProfiles.licenceExpiry,
        submittedAt: driverProfiles.submittedAt,
        updatedAt: driverProfiles.updatedAt,
      })
      .from(driverProfiles)
      .innerJoin(wayfareUsers, eq(wayfareUsers.id, driverProfiles.userId))
      .orderBy(desc(driverProfiles.updatedAt))
      .limit(300);
    const userIds = drivers.map((driver) => driver.userId);
    const checkRows = userIds.length
      ? await db
          .select()
          .from(driverComplianceChecks)
          .where(inArray(driverComplianceChecks.userId, userIds))
      : [];
    const checksByUser = new Map<
      string,
      Map<
        string,
        {
          status: string;
          expiresOn: string | null;
          staffNote: string | null;
          reviewedAt: Date | null;
        }
      >
    >();
    for (const check of checkRows) {
      const checks = checksByUser.get(check.userId) ?? new Map();
      checks.set(check.documentType, {
        status: check.status,
        expiresOn: check.expiresOn,
        staffNote: check.staffNote,
        reviewedAt: check.reviewedAt,
      });
      checksByUser.set(check.userId, checks);
    }
    return res.json({
      drivers: drivers.map((driver) => {
        const checks = checksByUser.get(driver.userId);
        const documents = complianceDocumentTypes.map((documentType) => {
          const stored = checks?.get(documentType);
          const needsRecheck = isDriverComplianceStale(
            stored?.reviewedAt ?? null,
            driver.submittedAt,
          );
          return {
            documentType,
            status: needsRecheck
              ? "needs_follow_up"
              : stored?.status ?? "not_reviewed",
            expiresOn:
              stored?.expiresOn ??
              (documentType === "driver_license"
                ? driver.licenceExpiry
                : null),
            staffNote: stored?.staffNote ?? null,
            reviewedAt: stored?.reviewedAt?.getTime() ?? null,
          };
        });
        return {
          userId: driver.userId,
          displayName: driver.displayName,
          email: driver.email,
          role: roleLabel(driver.role),
          applicationStatus: driver.applicationStatus,
          verificationStatus: driver.verificationStatus,
          vehicle: driver.vehicle,
          registration: driver.registration,
          updatedAt: driver.updatedAt.getTime(),
          incompleteDocuments: missingDriverCompliance(
            documents,
            launchMarketToday(),
          ),
          documents,
        };
      }),
    });
  } catch (error) {
    return sendRouteError(error, res, next);
  }
});

router.patch("/admin/people/:userId/compliance", async (req, res, next) => {
  try {
    const actorId = await requireAdmin(req, res);
    if (!actorId) return;
    const userId = stringParam(req.params.userId);
    const body = recordBody(req.body);
    const documentType = body.documentType;
    const status = body.status;
    const expiryResult = normalizeComplianceExpiry(
      typeof documentType === "string" ? documentType : "",
      typeof status === "string" ? status : "",
      body.expiresOn,
    );
    const note =
      body.note === undefined || body.note === null
        ? null
        : typeof body.note === "string"
          ? body.note.trim() || null
          : undefined;
    if (
      !userId ||
      userId.length > 128 ||
      typeof documentType !== "string" ||
      !complianceDocumentTypes.includes(
        documentType as (typeof complianceDocumentTypes)[number],
      ) ||
      typeof status !== "string" ||
      !complianceStatuses.includes(status as (typeof complianceStatuses)[number]) ||
      note === undefined ||
      (typeof note === "string" && note.length > 500) ||
      (status === "not_required" && !note)
    ) {
      return res.status(400).json({
        error:
          "Choose a valid check status. Add a note when a document is not required.",
      });
    }
    if (!expiryResult.ok) {
      return res.status(400).json({ error: expiryResult.message });
    }
    const expiresOn = expiryResult.expiresOn;
    const profiles = await db
      .select({
        userId: driverProfiles.userId,
        applicationStatus: driverProfiles.applicationStatus,
      })
      .from(driverProfiles)
      .where(eq(driverProfiles.userId, userId))
      .limit(1);
    if (!profiles[0]) {
      return res.status(404).json({ error: "Driver profile was not found." });
    }
    if (
      status === "verified" &&
      expiresOn &&
      expiresOn < launchMarketToday()
    ) {
      return res.status(409).json({
        error: "An expired document cannot be marked verified.",
      });
    }

    const now = new Date();
    const [check] = await db
      .insert(driverComplianceChecks)
      .values({
        userId,
        documentType: documentType as (typeof complianceDocumentTypes)[number],
        status: status as (typeof complianceStatuses)[number],
        expiresOn,
        staffNote: note,
        reviewedBy: actorId,
        reviewedAt: now,
        updatedAt: now,
      })
      .onConflictDoUpdate({
        target: [
          driverComplianceChecks.userId,
          driverComplianceChecks.documentType,
        ],
        set: {
          status: status as (typeof complianceStatuses)[number],
          expiresOn,
          staffNote: note,
          reviewedBy: actorId,
          reviewedAt: now,
          updatedAt: now,
        },
      })
      .returning();
    if (!check) throw new Error("The compliance check could not be saved.");
    await db.insert(adminReviewRecords).values({
      subjectUserId: userId,
      actorUserId: actorId,
      subjectType: "driver",
      outcome: status === "needs_follow_up" ? "needs_follow_up" : "reviewed",
      note: `Compliance check ${documentType}: ${status}${note ? ` · ${note}` : ""}`,
      checksConfirmed: status === "verified",
      applicationStatusBefore: profiles[0].applicationStatus,
      applicationStatusAfter: profiles[0].applicationStatus,
    });

    return res.json({
      document: {
        documentType: check.documentType,
        status: check.status,
        expiresOn: check.expiresOn,
        staffNote: check.staffNote,
        reviewedAt: check.reviewedAt?.getTime() ?? null,
      },
    });
  } catch (error) {
    return sendRouteError(error, res, next);
  }
});

router.get("/admin/payments", async (req, res, next) => {
  try {
    const actorId = await requireAdmin(req, res);
    if (!actorId) return;
    const limit = boundedInteger(req.query.limit, 50, 1, 100);
    const rows = await db
      .select({
        id: ridePayments.id,
        tripId: ridePayments.requestId,
        driverId: ridePayments.driverId,
        riderName: wayfareUsers.displayName,
        riderEmail: wayfareUsers.email,
        provider: ridePayments.provider,
        reference: ridePayments.reference,
        paymentMethod: ridePayments.paymentMethod,
        amountMinor: ridePayments.amountMinor,
        commissionMinor: ridePayments.commissionMinor,
        capturedAmountMinor: ridePayments.capturedAmountMinor,
        currency: ridePayments.currency,
        status: ridePayments.status,
        createdAt: ridePayments.createdAt,
        paidAt: ridePayments.paidAt,
        tripStatus: rideRequests.status,
      })
      .from(ridePayments)
      .innerJoin(rideRequests, eq(rideRequests.id, ridePayments.requestId))
      .innerJoin(wayfareUsers, eq(wayfareUsers.id, ridePayments.riderId))
      .orderBy(desc(ridePayments.createdAt))
      .limit(limit);
    return res.json({
      payments: rows.map((payment) => ({
        ...payment,
        createdAt: payment.createdAt.getTime(),
        paidAt: payment.paidAt?.getTime() ?? null,
      })),
    });
  } catch (error) {
    return sendRouteError(error, res, next);
  }
});

router.get("/admin/support-cases", async (req, res, next) => {
  try {
    const actorId = await requireAdmin(req, res);
    if (!actorId) return;
    const rows = await db
      .select({
        id: supportCases.id,
        rideId: supportCases.requestId,
        sourceMessageId: supportCases.sourceMessageId,
        reporterUserId: supportCases.reporterUserId,
        reporterName: wayfareUsers.displayName,
        reporterEmail: wayfareUsers.email,
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
      .innerJoin(wayfareUsers, eq(wayfareUsers.id, supportCases.reporterUserId))
      .innerJoin(rideRequests, eq(rideRequests.id, supportCases.requestId))
      .orderBy(desc(supportCases.createdAt))
      .limit(100);
    const cases = rows.map((row) => {
      const messages = Array.isArray(row.tripData["messages"])
        ? row.tripData["messages"]
        : [];
      const source = messages.find((candidate) => {
        if (!candidate || typeof candidate !== "object") return false;
        return (candidate as Record<string, unknown>)["id"] === row.sourceMessageId;
      });
      const sourceRecord =
        source && typeof source === "object"
          ? (source as Record<string, unknown>)
          : {};
      const replies = messages.flatMap((candidate) => {
        if (!candidate || typeof candidate !== "object") return [];
        const message = candidate as Record<string, unknown>;
        if (
          message["channel"] !== "support" ||
          message["sender"] !== "support" ||
          message["recipientUserId"] !== row.reporterUserId ||
          typeof message["id"] !== "string" ||
          typeof message["message"] !== "string" ||
          typeof message["createdAt"] !== "number"
        ) {
          return [];
        }
        return [{
          id: message["id"],
          message: message["message"],
          createdAt: message["createdAt"],
        }];
      });
      return {
        id: row.id,
        rideId: row.rideId,
        reporterUserId: row.reporterUserId,
        reporterName: row.reporterName,
        reporterEmail: row.reporterEmail,
        category: row.category,
        priority: row.priority,
        status: row.status,
        assignedTo: row.assignedTo,
        staffNote: row.staffNote,
        message:
          typeof sourceRecord["message"] === "string"
            ? sourceRecord["message"]
            : "Original support message is unavailable.",
        replies,
        createdAt:
          typeof sourceRecord["createdAt"] === "number"
            ? sourceRecord["createdAt"]
            : row.createdAt.getTime(),
        caseCreatedAt: row.createdAt.getTime(),
        updatedAt: row.updatedAt.getTime(),
        pickup: row.pickup,
        destination: row.destination,
        rideStatus: row.rideStatus,
      };
    });
    return res.json({ cases });
  } catch (error) {
    return sendRouteError(error, res, next);
  }
});

router.patch("/admin/support-cases/:caseId", async (req, res, next) => {
  try {
    const actorId = await requireAdmin(req, res);
    if (!actorId) return;
    const caseId = boundedInteger(stringParam(req.params.caseId), 0, 1, 2_147_483_647);
    const body = recordBody(req.body);
    const status = body.status;
    const priority = body.priority;
    const category = body.category;
    const assignedTo = body.assignedTo;
    const staffNote =
      body.staffNote === undefined || body.staffNote === null
        ? undefined
        : typeof body.staffNote === "string"
          ? body.staffNote.trim() || null
          : undefined;
    if (
      !caseId ||
      (status !== undefined &&
        (typeof status !== "string" ||
          !supportStatuses.includes(status as (typeof supportStatuses)[number]))) ||
      (priority !== undefined &&
        (typeof priority !== "string" ||
          !supportPriorities.includes(priority as (typeof supportPriorities)[number]))) ||
      (category !== undefined &&
        (typeof category !== "string" ||
          !supportCategories.includes(category as (typeof supportCategories)[number]))) ||
      (assignedTo !== undefined && assignedTo !== null && assignedTo !== "me") ||
      (body.staffNote !== undefined &&
        body.staffNote !== null &&
        (typeof body.staffNote !== "string" || body.staffNote.trim().length > 1000))
    ) {
      return res.status(400).json({ error: "Support case updates are invalid." });
    }
    const now = new Date();
    const updates: Partial<typeof supportCases.$inferInsert> = { updatedAt: now };
    if (status !== undefined) {
      updates.status = status as (typeof supportStatuses)[number];
    }
    if (priority !== undefined) {
      updates.priority = priority as (typeof supportPriorities)[number];
    }
    if (category !== undefined) {
      updates.category = category as (typeof supportCategories)[number];
    }
    if (assignedTo !== undefined) {
      updates.assignedTo = assignedTo === "me" ? actorId : null;
    }
    if (body.staffNote !== undefined) updates.staffNote = staffNote ?? null;

    const updated = await db.transaction(async (tx) => {
      const existing = await tx
        .select()
        .from(supportCases)
        .where(eq(supportCases.id, caseId))
        .limit(1);
      if (!existing[0]) throw routeError(404, "Support case was not found.");
      const [caseRecord] = await tx
        .update(supportCases)
        .set(updates)
        .where(eq(supportCases.id, caseId))
        .returning();
      if (!caseRecord) throw new Error("The support case could not be updated.");
      await tx.insert(rideEvents).values({
        requestId: caseRecord.requestId,
        actorId,
        action: "support_case_updated",
        details: {
          caseId,
          status: caseRecord.status,
          priority: caseRecord.priority,
          category: caseRecord.category,
          assignedTo: caseRecord.assignedTo,
          staffNote: staffNote ?? null,
        },
      });
      return caseRecord;
    });
    return res.json({
      case: {
        id: updated.id,
        status: updated.status,
        priority: updated.priority,
        category: updated.category,
        assignedTo: updated.assignedTo,
        staffNote: updated.staffNote,
        updatedAt: updated.updatedAt.getTime(),
      },
    });
  } catch (error) {
    return sendRouteError(error, res, next);
  }
});

router.post("/admin/support-cases/:caseId/replies", async (req, res, next) => {
  try {
    const actorId = await requireAdmin(req, res);
    if (!actorId) return;
    const caseId = boundedInteger(stringParam(req.params.caseId), 0, 1, 2_147_483_647);
    const messageText =
      typeof recordBody(req.body).message === "string"
        ? (recordBody(req.body).message as string).trim()
        : "";
    if (!caseId || !messageText || messageText.length > 2000) {
      return res.status(400).json({
        error: "Enter a reply containing 1 to 2,000 characters.",
      });
    }

    const reply = await db.transaction(async (tx) => {
      const cases = await tx
        .select()
        .from(supportCases)
        .where(eq(supportCases.id, caseId))
        .limit(1);
      const supportCase = cases[0];
      if (!supportCase) throw routeError(404, "Support case was not found.");
      if (supportCase.status === "resolved") {
        throw routeError(409, "Reopen the support case before sending a reply.");
      }
      const rides = await tx
        .select()
        .from(rideRequests)
        .where(eq(rideRequests.id, supportCase.requestId))
        .limit(1);
      const ride = rides[0];
      if (!ride) throw routeError(404, "The related ride was not found.");

      const replyRecord = {
        id: randomUUID(),
        sender: "support",
        channel: "support",
        message: messageText,
        createdAt: Date.now(),
        recipientUserId: supportCase.reporterUserId,
      };
      const tripData = {
        ...ride.tripData,
        messages: [
          ...(Array.isArray(ride.tripData["messages"])
            ? ride.tripData["messages"]
            : []),
          replyRecord,
        ],
      };
      await tx
        .update(rideRequests)
        .set({ tripData, updatedAt: new Date() })
        .where(eq(rideRequests.id, ride.id));
      await tx
        .update(supportCases)
        .set({ updatedAt: new Date() })
        .where(eq(supportCases.id, caseId));
      await tx.insert(rideEvents).values({
        requestId: ride.id,
        actorId,
        action: "support_reply_sent",
        details: { caseId, recipientUserId: supportCase.reporterUserId },
      });
      return replyRecord;
    });
    return res.status(201).json({ reply });
  } catch (error) {
    return sendRouteError(error, res, next);
  }
});

router.get("/admin/payouts", async (req, res, next) => {
  try {
    const actorId = await requireAdmin(req, res);
    if (!actorId) return;
    const rows = await db
      .select({
        id: driverPayoutRecords.id,
        driverId: driverPayoutRecords.driverId,
        driverName: wayfareUsers.displayName,
        driverEmail: wayfareUsers.email,
        provider: driverPayoutRecords.provider,
        externalReference: driverPayoutRecords.externalReference,
        amountMinor: driverPayoutRecords.amountMinor,
        currency: driverPayoutRecords.currency,
        periodStart: driverPayoutRecords.periodStart,
        periodEnd: driverPayoutRecords.periodEnd,
        reconciliationStatus: driverPayoutRecords.reconciliationStatus,
        transferStatus: driverPayoutRecords.transferStatus,
        transferReference: driverPayoutRecords.transferReference,
        transferCode: driverPayoutRecords.transferCode,
        transferError: driverPayoutRecords.transferError,
        transferUpdatedAt: driverPayoutRecords.transferUpdatedAt,
        note: driverPayoutRecords.note,
        recordedAt: driverPayoutRecords.recordedAt,
        reconciledAt: driverPayoutRecords.reconciledAt,
      })
      .from(driverPayoutRecords)
      .innerJoin(wayfareUsers, eq(wayfareUsers.id, driverPayoutRecords.driverId))
      .orderBy(desc(driverPayoutRecords.recordedAt))
      .limit(100);
    return res.json({
      payouts: rows.map((payout) => ({
        ...payout,
        transferUpdatedAt: payout.transferUpdatedAt?.getTime() ?? null,
        recordedAt: payout.recordedAt.getTime(),
        reconciledAt: payout.reconciledAt?.getTime() ?? null,
      })),
    });
  } catch (error) {
    return sendRouteError(error, res, next);
  }
});

router.post("/admin/payouts", async (req, res, next) => {
  try {
    const actorId = await requireAdmin(req, res);
    if (!actorId) return;
    const body = recordBody(req.body);
    const driverId = typeof body.driverId === "string" ? body.driverId.trim() : "";
    const provider = body.provider;
    const externalReference =
      typeof body.externalReference === "string"
        ? body.externalReference.trim()
        : "";
    const amountMinor = body.amountMinor;
    const currency =
      typeof body.currency === "string" ? body.currency.trim().toUpperCase() : "";
    const periodStart =
      body.periodStart === undefined || body.periodStart === null || body.periodStart === ""
        ? null
        : body.periodStart;
    const periodEnd =
      body.periodEnd === undefined || body.periodEnd === null || body.periodEnd === ""
        ? null
        : body.periodEnd;
    const note =
      body.note === undefined || body.note === null
        ? null
        : typeof body.note === "string"
          ? body.note.trim() || null
          : undefined;
    if (
      !driverId ||
      driverId.length > 128 ||
      typeof provider !== "string" ||
      !payoutProviders.includes(provider as (typeof payoutProviders)[number]) ||
      !/^[A-Za-z0-9._:-]{3,128}$/.test(externalReference) ||
      typeof amountMinor !== "number" ||
      !Number.isSafeInteger(amountMinor) ||
      amountMinor <= 0 ||
      amountMinor > 100_000_000_000 ||
      !/^[A-Z]{3}$/.test(currency) ||
      (periodStart !== null && !isIsoCalendarDate(periodStart)) ||
      (periodEnd !== null && !isIsoCalendarDate(periodEnd)) ||
      (periodStart !== null && periodEnd !== null && periodEnd < periodStart) ||
      note === undefined ||
      (typeof note === "string" && note.length > 500) ||
      body.confirmedExternalTransfer !== true
    ) {
      return res.status(400).json({
        error:
          "Enter a valid completed external transfer, provider reference, amount, currency, and optional payout period.",
      });
    }
    const drivers = await db
      .select({ id: wayfareUsers.id, isSuspended: wayfareUsers.isSuspended })
      .from(wayfareUsers)
      .where(and(eq(wayfareUsers.id, driverId), eq(wayfareUsers.role, "driver")))
      .limit(1);
    if (!drivers[0]) {
      return res.status(409).json({ error: "Choose an active Vhaya driver." });
    }
    if (drivers[0].isSuspended) {
      return res.status(409).json({ error: "Suspended accounts cannot receive payouts." });
    }
    const [payout] = await db.transaction(async (tx) => {
      const [record] = await tx
        .insert(driverPayoutRecords)
        .values({
          driverId,
          provider: provider as (typeof payoutProviders)[number],
          externalReference,
          amountMinor,
          currency,
          periodStart,
          periodEnd,
          reconciliationStatus: "unreconciled",
          note,
          recordedBy: actorId,
        })
        .returning();
      if (!record) throw new Error("The payout record could not be saved.");
      await tx.insert(adminReviewRecords).values({
        subjectUserId: driverId,
        actorUserId: actorId,
        subjectType: "driver",
        outcome: "reviewed",
        note: `External payout recorded: ${provider} · ${externalReference}`,
        checksConfirmed: true,
      });
      return [record];
    });
    if (!payout) throw new Error("The payout record could not be saved.");
    return res.status(201).json({
      payout: {
        id: payout.id,
        driverId: payout.driverId,
        provider: payout.provider,
        externalReference: payout.externalReference,
        amountMinor: payout.amountMinor,
        currency: payout.currency,
        periodStart: payout.periodStart,
        periodEnd: payout.periodEnd,
        reconciliationStatus: payout.reconciliationStatus,
        note: payout.note,
        recordedAt: payout.recordedAt.getTime(),
      },
    });
  } catch (error) {
    return sendRouteError(error, res, next);
  }
});

router.patch("/admin/payouts/:payoutId", async (req, res, next) => {
  try {
    const actorId = await requireAdmin(req, res);
    if (!actorId) return;
    const payoutId = boundedInteger(
      stringParam(req.params.payoutId),
      0,
      1,
      2_147_483_647,
    );
    const body = recordBody(req.body);
    const reconciliationStatus = body.reconciliationStatus;
    const note =
      body.note === undefined || body.note === null
        ? null
        : typeof body.note === "string"
          ? body.note.trim() || null
          : undefined;
    if (
      !payoutId ||
      typeof reconciliationStatus !== "string" ||
      !["matched", "exception"].includes(reconciliationStatus) ||
      note === undefined ||
      (typeof note === "string" && note.length > 1000) ||
      (reconciliationStatus === "exception" && !note)
    ) {
      return res.status(400).json({
        error: "Choose a reconciliation result and add a note for exceptions.",
      });
    }
    const existing = await db
      .select({
        id: driverPayoutRecords.id,
        driverId: driverPayoutRecords.driverId,
        reconciliationStatus: driverPayoutRecords.reconciliationStatus,
      })
      .from(driverPayoutRecords)
      .where(eq(driverPayoutRecords.id, payoutId))
      .limit(1);
    if (!existing[0]) return res.status(404).json({ error: "Payout record was not found." });
    const now = new Date();
    const [updated] = await db
      .update(driverPayoutRecords)
      .set({
        reconciliationStatus:
          reconciliationStatus as (typeof payoutReconciliationStatuses)[number],
        note,
        reconciledAt: now,
      })
      .where(eq(driverPayoutRecords.id, payoutId))
      .returning();
    if (!updated) throw new Error("The payout record could not be reconciled.");
    await db.insert(adminReviewRecords).values({
      subjectUserId: existing[0].driverId,
      actorUserId: actorId,
      subjectType: "driver",
      outcome: reconciliationStatus === "exception" ? "needs_follow_up" : "reviewed",
      note: `Payout ${payoutId} reconciliation: ${reconciliationStatus}${note ? ` · ${note}` : ""}`,
      checksConfirmed: reconciliationStatus === "matched",
    });
    return res.json({
      payout: {
        id: updated.id,
        reconciliationStatus: updated.reconciliationStatus,
        note: updated.note,
        reconciledAt: updated.reconciledAt?.getTime() ?? null,
      },
    });
  } catch (error) {
    return sendRouteError(error, res, next);
  }
});

/**
 * POST /admin/payouts/run-weekly
 * Runs the weekly payout cycle for all active drivers.
 * Sums captured paid ride payments in the window, deducts platform commission,
 * creates driverPayoutRecords and a payoutBatch. Idempotent per week.
 */
router.post("/admin/payouts/run-weekly", async (req, res, next) => {
  const actorId = await requireAdmin(req, res);
  if (!actorId) return;

  try {
    const body = recordBody(req.body);
    const periodStartRaw =
      typeof body.periodStart === "string" ? body.periodStart : "";
    const periodEndRaw =
      typeof body.periodEnd === "string" ? body.periodEnd : "";

    if (!periodStartRaw || !periodEndRaw) {
      return res.status(400).json({
        error: "periodStart and periodEnd (ISO dates) are required.",
      });
    }

    const periodStart = new Date(periodStartRaw);
    const periodEnd = new Date(periodEndRaw);
    if (isNaN(periodStart.getTime()) || isNaN(periodEnd.getTime())) {
      return res.status(400).json({ error: "Invalid period dates." });
    }
    if (periodEnd <= periodStart) {
      return res.status(400).json({
        error: "periodEnd must be after periodStart.",
      });
    }

    const existing = await db
      .select()
      .from(payoutBatches)
      .where(
        and(
          eq(payoutBatches.periodStart, periodStart),
          eq(payoutBatches.periodEnd, periodEnd),
        ),
      )
      .limit(1);
    if (existing[0]) {
      return res.status(409).json({
        error: "A payout batch already exists for this period.",
        batchId: existing[0].id,
      });
    }

    const batchId = `batch-${randomUUID()}`;
    const now = new Date();

    const payments = await db
      .select({
        id: ridePayments.id,
        requestId: ridePayments.requestId,
        driverId: ridePayments.driverId,
        riderId: ridePayments.riderId,
        amountMinor: ridePayments.amountMinor,
        commissionMinor: ridePayments.commissionMinor,
        capturedAmountMinor: ridePayments.capturedAmountMinor,
        currency: ridePayments.currency,
        status: ridePayments.status,
        driverPayoutId: ridePayments.driverPayoutId,
      })
      .from(ridePayments)
      .where(
        and(
          eq(ridePayments.status, "paid"),
          gte(ridePayments.paidAt, periodStart),
          lte(ridePayments.paidAt, periodEnd),
          isNull(ridePayments.driverPayoutId),
        ),
      );

    const byDriver = new Map<
      string,
      {
        driverId: string;
        totalAmountMinor: number;
        totalCommissionMinor: number;
        totalNetMinor: number;
        paymentIds: string[];
      }
    >();

    for (const p of payments) {
      if (!p.driverId) continue;
      const paid = p.capturedAmountMinor ?? p.amountMinor;
      const commission = p.commissionMinor ?? 0;
      const net = paid - commission;
      const existing = byDriver.get(p.driverId);
      if (existing) {
        existing.totalAmountMinor += paid;
        existing.totalCommissionMinor += commission;
        existing.totalNetMinor += net;
        existing.paymentIds.push(p.id);
      } else {
        byDriver.set(p.driverId, {
          driverId: p.driverId,
          totalAmountMinor: paid,
          totalCommissionMinor: commission,
          totalNetMinor: net,
          paymentIds: [p.id],
        });
      }
    }

    let totalAmountMinor = 0;
    let driverCount = 0;

    await db.transaction(async (tx) => {
      await tx.insert(payoutBatches).values({
        id: batchId,
        periodStart,
        periodEnd,
        status: "open",
        totalAmountMinor: 0,
        driverCount: 0,
        createdBy: actorId,
        createdAt: now,
        updatedAt: now,
      });

      for (const [, d] of byDriver) {
        const payoutId = `payout-${randomUUID()}`;
        await tx.insert(driverPayoutRecords).values({
          driverId: d.driverId,
          provider: "paystack",
          externalReference: payoutId,
          amountMinor: d.totalNetMinor,
          currency: payments[0]?.currency ?? "ZAR",
          periodStart: periodStart.toISOString(),
          periodEnd: periodEnd.toISOString(),
          reconciliationStatus: "unreconciled",
          batchId,
          transferReference: `payout_${randomUUID()}`,
          note: null,
          recordedBy: actorId,
          recordedAt: now,
        });

        for (const paymentId of d.paymentIds) {
          await tx
            .update(ridePayments)
            .set({ driverPayoutId: payoutId })
            .where(eq(ridePayments.id, paymentId));
        }

        totalAmountMinor += d.totalNetMinor;
        driverCount += 1;
      }

      await tx
        .update(payoutBatches)
        .set({ totalAmountMinor, driverCount, updatedAt: now })
        .where(eq(payoutBatches.id, batchId));
    });

    return res.status(201).json({
      batch: {
        id: batchId,
        periodStart: periodStart.getTime(),
        periodEnd: periodEnd.getTime(),
        status: "open",
        totalAmountMinor,
        driverCount,
        createdAt: now.getTime(),
      },
      drivers: Array.from(byDriver.values()).map((d) => ({
        driverId: d.driverId,
        totalAmountMinor: d.totalAmountMinor,
        totalCommissionMinor: d.totalCommissionMinor,
        totalNetMinor: d.totalNetMinor,
        paymentCount: d.paymentIds.length,
      })),
    });
  } catch (error) {
    return sendRouteError(error, res, next);
  }
});

router.get("/admin/payouts/batches", async (req, res, next) => {
  const actorId = await requireAdmin(req, res);
  if (!actorId) return;

  try {
    const rows = await db
      .select({
        id: payoutBatches.id,
        periodStart: payoutBatches.periodStart,
        periodEnd: payoutBatches.periodEnd,
        status: payoutBatches.status,
        settlementMethod: payoutBatches.settlementMethod,
        totalAmountMinor: payoutBatches.totalAmountMinor,
        driverCount: payoutBatches.driverCount,
        paidAt: payoutBatches.paidAt,
        reconciledAt: payoutBatches.reconciledAt,
        createdAt: payoutBatches.createdAt,
        createdBy: wayfareUsers.displayName,
      })
      .from(payoutBatches)
      .leftJoin(wayfareUsers, eq(wayfareUsers.id, payoutBatches.createdBy))
      .orderBy(desc(payoutBatches.createdAt))
      .limit(100);

    return res.json({
      batches: rows.map((r) => toPayoutBatchResponse(r, r.createdBy ?? null)),
    });
  } catch (error) {
    return sendRouteError(error, res, next);
  }
});

router.get("/admin/payouts/banks", async (req, res, next) => {
  try {
    const actorId = await requireAdmin(req, res);
    if (!actorId) return;
    assertPaystackKeySafeForEnvironment();
    const banks = await listPaystackBanks();
    return res.json(
      GetPayoutBanksResponse.parse({
        banks: banks.map(({ name, code }) => ({ name, code })),
      }),
    );
  } catch (error) {
    if (error instanceof PaystackRequestError) {
      return res.status(502).json({ error: "Paystack bank details are unavailable." });
    }
    return sendRouteError(error, res, next);
  }
});

router.get("/admin/payouts/recipients", async (req, res, next) => {
  try {
    const actorId = await requireAdmin(req, res);
    if (!actorId) return;
    const rows = await db
      .select({
        driverId: wayfareUsers.id,
        driverName: wayfareUsers.displayName,
        recipientName: driverPayoutRecipients.accountName,
        bankName: driverPayoutRecipients.bankName,
        accountLast4: driverPayoutRecipients.accountLast4,
        currency: driverPayoutRecipients.currency,
        updatedAt: driverPayoutRecipients.updatedAt,
      })
      .from(wayfareUsers)
      .leftJoin(
        driverPayoutRecipients,
        eq(driverPayoutRecipients.driverId, wayfareUsers.id),
      )
      .where(
        and(
          eq(wayfareUsers.role, "driver"),
          eq(wayfareUsers.isSuspended, false),
          isNull(wayfareUsers.deletedAt),
        ),
      )
      .orderBy(asc(wayfareUsers.displayName))
      .limit(500);
    return res.json(
      GetPayoutRecipientsResponse.parse({
        drivers: rows.map((row) => ({
          driverId: row.driverId,
          driverName: row.driverName,
          recipient: row.recipientName
            ? {
                driverId: row.driverId,
                driverName: row.driverName,
                recipientName: row.recipientName,
                bankName: row.bankName ?? "",
                accountLast4: row.accountLast4 ?? "",
                currency: row.currency ?? "ZAR",
                updatedAt: row.updatedAt?.getTime() ?? 0,
              }
            : null,
        })),
      }),
    );
  } catch (error) {
    return sendRouteError(error, res, next);
  }
});

router.post("/admin/payouts/recipients", async (req, res, next) => {
  try {
    const actorId = await requireAdmin(req, res);
    if (!actorId) return;
    const parsed = CreatePayoutRecipientBody.safeParse(req.body);
    if (!parsed.success) {
      return res.status(400).json({ error: "Enter a valid driver and South African bank account." });
    }
    const input = {
      driverId: parsed.data.driverId.trim(),
      name: parsed.data.name.trim(),
      accountNumber: parsed.data.accountNumber.trim(),
      bankCode: parsed.data.bankCode.trim(),
    };
    if (
      !input.driverId ||
      !input.name ||
      !/^\d{6,30}$/.test(input.accountNumber) ||
      !/^[A-Za-z0-9_-]{1,32}$/.test(input.bankCode)
    ) {
      return res.status(400).json({ error: "Enter a valid driver and South African bank account." });
    }
    const [driver] = await db
      .select({
        id: wayfareUsers.id,
        displayName: wayfareUsers.displayName,
        isSuspended: wayfareUsers.isSuspended,
      })
      .from(wayfareUsers)
      .where(
        and(
          eq(wayfareUsers.id, input.driverId),
          eq(wayfareUsers.role, "driver"),
          isNull(wayfareUsers.deletedAt),
        ),
      )
      .limit(1);
    if (!driver || driver.isSuspended) {
      return res.status(409).json({ error: "Choose an active Vhaya driver." });
    }

    assertPaystackKeySafeForEnvironment();
    const banks = await listPaystackBanks();
    const bank = banks.find((candidate) => candidate.code === input.bankCode);
    if (!bank) {
      return res.status(400).json({ error: "Choose a bank from Paystack's South African bank list." });
    }
    let providerRecipient;
    try {
      providerRecipient = await createPaystackRecipient({
        name: input.name,
        accountNumber: input.accountNumber,
        bankCode: input.bankCode,
      });
    } catch (error) {
      if (error instanceof PaystackRequestError) {
        return res.status(502).json({ error: "Paystack could not verify this bank account." });
      }
      throw error;
    }

    const details = providerRecipient.details;
    const providerAccountNumber = details?.account_number;
    const providerBankName = details?.bank_name?.trim();
    const providerAccountName =
      details?.account_name?.trim() || providerRecipient.name?.trim() || input.name;
    if (
      !providerRecipient.active ||
      !/^RCP_[A-Za-z0-9]+$/.test(providerRecipient.recipient_code ?? "") ||
      providerAccountNumber !== input.accountNumber ||
      !providerBankName ||
      providerAccountNumber.slice(-4) !== input.accountNumber.slice(-4)
    ) {
      return res.status(502).json({ error: "Paystack did not return a verified bank recipient." });
    }

    const now = new Date();
    await db.transaction(async (tx) => {
      const [lockedDriver] = await tx
        .select()
        .from(wayfareUsers)
        .where(
          and(
            eq(wayfareUsers.id, driver.id),
            eq(wayfareUsers.role, "driver"),
            eq(wayfareUsers.isSuspended, false),
            isNull(wayfareUsers.deletedAt),
          ),
        )
        .for("update")
        .limit(1);
      if (!lockedDriver) {
        throw routeError(409, "Choose an active Vhaya driver.");
      }
      await tx
        .insert(driverPayoutRecipients)
        .values({
          driverId: driver.id,
          recipientCode: providerRecipient.recipient_code!,
          accountName: providerAccountName.slice(0, 120),
          accountLast4: providerAccountNumber.slice(-4),
          bankName: providerBankName.slice(0, 120),
          currency: "ZAR",
          registeredBy: actorId,
          createdAt: now,
          updatedAt: now,
        })
        .onConflictDoUpdate({
          target: driverPayoutRecipients.driverId,
          set: {
            recipientCode: providerRecipient.recipient_code!,
            accountName: providerAccountName.slice(0, 120),
            accountLast4: providerAccountNumber.slice(-4),
            bankName: providerBankName.slice(0, 120),
            currency: "ZAR",
            registeredBy: actorId,
            updatedAt: now,
          },
        });
      await tx.insert(adminReviewRecords).values({
        subjectUserId: driver.id,
        actorUserId: actorId,
        subjectType: "driver",
        outcome: "reviewed",
        note: `Paystack payout recipient verified: ${providerBankName} ···· ${providerAccountNumber.slice(-4)}`,
        checksConfirmed: true,
      });
    });
    return res.status(201).json(
      CreatePayoutRecipientResponse.parse({
        recipient: {
          driverId: driver.id,
          driverName: driver.displayName,
          recipientName: providerAccountName.slice(0, 120),
          bankName: providerBankName.slice(0, 120),
          accountLast4: providerAccountNumber.slice(-4),
          currency: "ZAR",
          updatedAt: now.getTime(),
        },
      }),
    );
  } catch (error) {
    if (error instanceof PaystackRequestError) {
      return res.status(502).json({ error: "Paystack could not verify this bank account." });
    }
    return sendRouteError(error, res, next);
  }
});

router.get("/admin/payouts/batches/:batchId", async (req, res, next) => {
  try {
    const actorId = await requireAdmin(req, res);
    if (!actorId) return;
    const parsed = GetPayoutBatchDetailParams.safeParse(req.params);
    if (!parsed.success) {
      return res.status(400).json({ error: "A valid payout batch is required." });
    }
    const [batch] = await db
      .select()
      .from(payoutBatches)
      .where(eq(payoutBatches.id, parsed.data.batchId))
      .limit(1);
    if (!batch) return res.status(404).json({ error: "Payout batch was not found." });
    await attachLegacyBatchPayouts(batch);
    const rows = await db
      .select({
        payoutId: driverPayoutRecords.id,
        driverId: driverPayoutRecords.driverId,
        driverName: wayfareUsers.displayName,
        amountMinor: driverPayoutRecords.amountMinor,
        currency: driverPayoutRecords.currency,
        transferStatus: driverPayoutRecords.transferStatus,
        transferReference: driverPayoutRecords.transferReference,
        transferAttemptCount: driverPayoutRecords.transferAttemptCount,
        transferError: driverPayoutRecords.transferError,
        transferUpdatedAt: driverPayoutRecords.transferUpdatedAt,
        transferRecipientCode: driverPayoutRecords.transferRecipientCode,
        transferRecipientName: driverPayoutRecords.transferRecipientName,
        transferBankName: driverPayoutRecords.transferBankName,
        transferAccountLast4: driverPayoutRecords.transferAccountLast4,
        currentRecipientCode: driverPayoutRecipients.recipientCode,
        currentRecipientName: driverPayoutRecipients.accountName,
        currentBankName: driverPayoutRecipients.bankName,
        currentAccountLast4: driverPayoutRecipients.accountLast4,
      })
      .from(driverPayoutRecords)
      .innerJoin(wayfareUsers, eq(wayfareUsers.id, driverPayoutRecords.driverId))
      .leftJoin(
        driverPayoutRecipients,
        eq(driverPayoutRecipients.driverId, driverPayoutRecords.driverId),
      )
      .where(eq(driverPayoutRecords.batchId, batch.id))
      .orderBy(asc(driverPayoutRecords.id));
    const reviewRows: PayoutBatchReviewRow[] = rows.map((row) => ({
      payoutId: row.payoutId,
      driverId: row.driverId,
      driverName: row.driverName,
      amountMinor: row.amountMinor,
      currency: row.currency,
      transferStatus: row.transferStatus,
      transferReference: row.transferReference,
      transferAttemptCount: row.transferAttemptCount,
      recipientCode: row.transferRecipientCode ?? row.currentRecipientCode,
      recipientName: row.transferRecipientName ?? row.currentRecipientName,
      bankName: row.transferBankName ?? row.currentBankName,
      accountLast4: row.transferAccountLast4 ?? row.currentAccountLast4,
    }));
    return res.json(
      GetPayoutBatchDetailResponse.parse({
        batch: toPayoutBatchResponse(batch),
        reviewToken: createPayoutBatchReviewToken(batch.id, reviewRows),
        drivers: reviewRows.map((row, index) => ({
          payoutId: row.payoutId,
          driverId: row.driverId,
          driverName: row.driverName,
          amountMinor: row.amountMinor,
          currency: row.currency,
          transferStatus: row.transferStatus,
          transferError: rows[index]?.transferError ?? null,
          transferUpdatedAt: rows[index]?.transferUpdatedAt?.getTime() ?? null,
          recipientName: row.recipientName,
          bankName: row.bankName,
          accountLast4: row.accountLast4,
        })),
      }),
    );
  } catch (error) {
    return sendRouteError(error, res, next);
  }
});

router.post("/admin/payouts/batches/:batchId/transfers", async (req, res, next) => {
  try {
    const actorId = await requireAdmin(req, res);
    if (!actorId) return;
    const params = StartPayoutBatchTransfersParams.safeParse(req.params);
    const body = StartPayoutBatchTransfersBody.safeParse(req.body);
    if (!params.success || !body.success || body.data.confirmed !== true) {
      return res.status(400).json({ error: "Confirm the reviewed payout batch before sending." });
    }
    assertPaystackTransfersEnabled();
    const [batch] = await db
      .select()
      .from(payoutBatches)
      .where(eq(payoutBatches.id, params.data.batchId))
      .limit(1);
    if (!batch) return res.status(404).json({ error: "Payout batch was not found." });
    await attachLegacyBatchPayouts(batch);
    const preparedRows = await db.transaction(async (tx) => {
      const [lockedBatch] = await tx
        .select()
        .from(payoutBatches)
        .where(eq(payoutBatches.id, batch.id))
        .for("update")
        .limit(1);
      if (!lockedBatch) throw routeError(404, "Payout batch was not found.");
      if (
        !["submitted", "processing", "partially_paid", "failed"].includes(lockedBatch.status) ||
        lockedBatch.settlementMethod === "external"
      ) {
        throw routeError(409, "Approve the batch before starting Paystack transfers.");
      }
      const payouts = await tx
        .select()
        .from(driverPayoutRecords)
        .where(eq(driverPayoutRecords.batchId, lockedBatch.id))
        .orderBy(asc(driverPayoutRecords.id))
        .for("update");
      if (!payouts.length) {
        throw routeError(409, "This payout batch has no driver payments.");
      }
      const driverIds = Array.from(new Set(payouts.map((row) => row.driverId))).sort();
      const drivers = await tx
        .select({
          id: wayfareUsers.id,
          displayName: wayfareUsers.displayName,
          isSuspended: wayfareUsers.isSuspended,
        })
        .from(wayfareUsers)
        .where(inArray(wayfareUsers.id, driverIds))
        .orderBy(asc(wayfareUsers.id))
        .for("update");
      const recipients = await tx
        .select()
        .from(driverPayoutRecipients)
        .where(inArray(driverPayoutRecipients.driverId, driverIds))
        .orderBy(asc(driverPayoutRecipients.driverId))
        .for("update");
      const driverById = new Map(drivers.map((driver) => [driver.id, driver]));
      const recipientByDriver = new Map(
        recipients.map((recipient) => [recipient.driverId, recipient]),
      );
      const reviewRows: PayoutBatchReviewRow[] = payouts.map((payout) => {
        const driver = driverById.get(payout.driverId);
        const recipient = recipientByDriver.get(payout.driverId);
        return {
          payoutId: payout.id,
          driverId: payout.driverId,
          driverName: driver?.displayName ?? "",
          amountMinor: payout.amountMinor,
          currency: payout.currency,
          transferStatus: payout.transferStatus,
          transferReference: payout.transferReference,
          transferAttemptCount: payout.transferAttemptCount,
          recipientCode: payout.transferRecipientCode ?? recipient?.recipientCode ?? null,
          recipientName: payout.transferRecipientName ?? recipient?.accountName ?? null,
          bankName: payout.transferBankName ?? recipient?.bankName ?? null,
          accountLast4: payout.transferAccountLast4 ?? recipient?.accountLast4 ?? null,
        };
      });
      if (
        payouts.some((payout) => {
          const driver = driverById.get(payout.driverId);
          return (
            !driver ||
            driver.isSuspended ||
            payout.currency !== "ZAR" ||
            payout.amountMinor <= 0
          );
        }) ||
        reviewRows.some(
          (row) =>
            !row.recipientCode ||
            !row.recipientName ||
            !row.bankName ||
            !row.accountLast4,
        )
      ) {
        throw routeError(
          409,
          "Every payout needs an active driver and verified ZAR bank recipient before sending.",
        );
      }
      if (
        createPayoutBatchReviewToken(lockedBatch.id, reviewRows) !==
        body.data.reviewToken
      ) {
        throw routeError(
          409,
          "The batch or a recipient changed after review. Refresh the preview and confirm it again.",
        );
      }

      const prepared = [];
      for (let index = 0; index < payouts.length; index += 1) {
        const payout = payouts[index]!;
        const reviewRow = reviewRows[index]!;
        if (!payout.transferRecipientCode) {
          const [updatedPayout] = await tx
            .update(driverPayoutRecords)
            .set({
              transferRecipientCode: reviewRow.recipientCode!,
              transferRecipientName: reviewRow.recipientName!,
              transferBankName: reviewRow.bankName!,
              transferAccountLast4: reviewRow.accountLast4!,
            })
            .where(eq(driverPayoutRecords.id, payout.id))
            .returning();
          if (!updatedPayout) throw new Error("The payout recipient snapshot could not be saved.");
          prepared.push({
            payout: updatedPayout,
            recipientCode: reviewRow.recipientCode!,
          });
        } else {
          prepared.push({
            payout,
            recipientCode: reviewRow.recipientCode!,
          });
        }
      }
      return { batch: lockedBatch, rows: prepared };
    });

    for (const { payout, recipientCode } of preparedRows.rows) {
      const eligibleStatuses: PayoutTransferStatus[] = body.data.retryFailed
        ? ["not_started", "failed"]
        : ["not_started"];
      const transferReference =
        payout.transferReference ?? `payout_${randomUUID()}`;
      const now = new Date();
      const claimed = await db.transaction(async (tx) => {
        const [lockedBatch] = await tx
          .select()
          .from(payoutBatches)
          .where(eq(payoutBatches.id, preparedRows.batch.id))
          .for("update")
          .limit(1);
        if (
          !lockedBatch ||
          lockedBatch.status === "paid" ||
          lockedBatch.settlementMethod === "external"
        ) {
          return undefined;
        }
        const [claimedPayout] = await tx
          .update(driverPayoutRecords)
          .set({
            transferStatus: "sending",
            transferReference,
            transferAttemptCount: sql`${driverPayoutRecords.transferAttemptCount} + 1`,
            transferError: null,
            transferUpdatedAt: now,
          })
          .where(
            and(
              eq(driverPayoutRecords.id, payout.id),
              eq(driverPayoutRecords.batchId, preparedRows.batch.id),
              inArray(driverPayoutRecords.transferStatus, eligibleStatuses),
            ),
          )
          .returning();
        if (claimedPayout) {
          await tx
            .update(payoutBatches)
            .set({
              status: "processing",
              settlementMethod: "paystack",
              paidAt: null,
              updatedAt: now,
            })
            .where(eq(payoutBatches.id, preparedRows.batch.id));
        }
        return claimedPayout;
      });
      if (!claimed || !recipientCode) continue;

      let transferStatus: PayoutTransferStatus = "unknown";
      let transferCode: string | null = null;
      let transferError: string | null = null;
      try {
        const providerTransfer = await createPaystackTransfer({
          amountMinor: claimed.amountMinor,
          recipientCode,
          reference: transferReference,
          reason: `Vhaya driver payout ${preparedRows.batch.periodStart.toISOString().slice(0, 10)}`,
        });
        const responseMatches =
          providerTransfer.reference === transferReference &&
          providerTransfer.amount === claimed.amountMinor &&
          providerTransfer.currency === claimed.currency;
        transferCode = providerTransfer.transfer_code ?? null;
        if (!responseMatches) {
          transferError = "Paystack's response could not be matched to this payout.";
        } else {
          transferStatus = normalizePaystackTransferStatus(providerTransfer.status);
          if (transferStatus === "failed") {
            transferError = "Paystack reported that this transfer failed.";
          }
        }
      } catch (error) {
        const providerStatus =
          error instanceof PaystackRequestError ? error.providerHttpStatus : 0;
        transferStatus =
          providerStatus >= 400 && providerStatus < 500 && providerStatus !== 409
            ? "failed"
            : "unknown";
        transferError =
          transferStatus === "failed"
            ? "Paystack rejected this transfer request."
            : "The transfer result is uncertain. Check its Paystack status before retrying.";
      }

      const updatedAt = new Date();
      const [updated] = await db
        .update(driverPayoutRecords)
        .set({
          transferStatus,
          transferCode,
          transferError,
          transferUpdatedAt: updatedAt,
        })
        .where(
          and(
            eq(driverPayoutRecords.id, claimed.id),
            eq(driverPayoutRecords.transferStatus, "sending"),
          ),
        )
        .returning();
      if (updated) {
        await db.insert(adminReviewRecords).values({
          subjectUserId: updated.driverId,
          actorUserId: actorId,
          subjectType: "driver",
          outcome: transferStatus === "failed" || transferStatus === "unknown"
            ? "needs_follow_up"
            : "reviewed",
          note: `Paystack payout ${updated.id} in batch ${preparedRows.batch.id}: ${transferStatus}`,
          checksConfirmed: transferStatus === "success",
        });
      }
    }

    const updatedBatch = await refreshPayoutBatchStatus(preparedRows.batch.id);
    if (updatedBatch?.settlementMethod === "external") {
      return res.status(409).json({
        error: "This batch was recorded as externally paid before Paystack transfers could start.",
      });
    }
    const currentRows = await db
      .select()
      .from(driverPayoutRecords)
      .where(eq(driverPayoutRecords.batchId, preparedRows.batch.id))
      .orderBy(asc(driverPayoutRecords.id));
    return res.json(
      StartPayoutBatchTransfersResponse.parse({
        batch: toPayoutBatchResponse(updatedBatch ?? preparedRows.batch),
        outcomes: currentRows.map(toPayoutTransferOutcome),
      }),
    );
  } catch (error) {
    return sendRouteError(error, res, next);
  }
});

router.post("/admin/payouts/:payoutId/verify", async (req, res, next) => {
  try {
    const actorId = await requireAdmin(req, res);
    if (!actorId) return;
    const parsed = VerifyPayoutTransferParams.safeParse(req.params);
    if (!parsed.success) return res.status(400).json({ error: "A valid payout is required." });
    assertPaystackKeySafeForEnvironment();
    const [payout] = await db
      .select()
      .from(driverPayoutRecords)
      .where(eq(driverPayoutRecords.id, parsed.data.payoutId))
      .limit(1);
    if (!payout) return res.status(404).json({ error: "Payout was not found." });
    if (!payout.transferReference || payout.transferStatus === "not_started") {
      return res.status(409).json({ error: "This payout has not been sent to Paystack." });
    }
    let providerTransfer;
    try {
      providerTransfer = await verifyPaystackTransfer(payout.transferReference);
    } catch (error) {
      if (error instanceof PaystackRequestError) {
        return res.status(502).json({
          error: "Paystack could not confirm this transfer. Its saved status was not changed.",
        });
      }
      throw error;
    }
    if (
      providerTransfer.reference !== payout.transferReference ||
      providerTransfer.amount !== payout.amountMinor ||
      providerTransfer.currency !== payout.currency
    ) {
      return res.status(502).json({
        error: "Paystack's transfer details did not match this payout. Its saved status was not changed.",
      });
    }
    const transferStatus = normalizePaystackTransferStatus(providerTransfer.status);
    const [updated] = await db
      .update(driverPayoutRecords)
      .set({
        transferStatus,
        transferCode: providerTransfer.transfer_code ?? payout.transferCode,
        transferError:
          transferStatus === "failed" || transferStatus === "reversed"
            ? "Paystack reported a failed or reversed transfer."
            : null,
        transferUpdatedAt: new Date(),
      })
      .where(eq(driverPayoutRecords.id, payout.id))
      .returning();
    if (!updated) throw new Error("The payout status could not be updated.");
    if (updated.batchId) await refreshPayoutBatchStatus(updated.batchId);
    await db.insert(adminReviewRecords).values({
      subjectUserId: updated.driverId,
      actorUserId: actorId,
      subjectType: "driver",
      outcome: transferStatus === "failed" || transferStatus === "reversed"
        ? "needs_follow_up"
        : "reviewed",
      note: `Paystack payout ${updated.id} verified: ${transferStatus}`,
      checksConfirmed: transferStatus === "success",
    });
    return res.json(VerifyPayoutTransferResponse.parse(toPayoutTransferOutcome(updated)));
  } catch (error) {
    if (error instanceof PaystackRequestError) {
      return res.status(502).json({ error: "Paystack could not verify this transfer." });
    }
    return sendRouteError(error, res, next);
  }
});

router.post("/admin/payouts/batches/:batchId/external-payment", async (req, res, next) => {
  try {
    const actorId = await requireAdmin(req, res);
    if (!actorId) return;
    const batchId = stringParam(req.params.batchId);
    const body = RecordExternalPayoutBatchPaymentBody.safeParse(req.body);
    if (!batchId || !body.success || body.data.confirmed !== true) {
      return res.status(400).json({ error: "Confirm that the external payment is complete." });
    }
    const result = await db.transaction(async (tx) => {
      const [batch] = await tx
        .select()
        .from(payoutBatches)
        .where(eq(payoutBatches.id, batchId))
        .for("update")
        .limit(1);
      if (!batch) throw routeError(404, "Payout batch was not found.");
      if (batch.status !== "submitted" || batch.settlementMethod) {
        throw routeError(409, "Only an approved, unpaid batch can be recorded as externally paid.");
      }
      await attachLegacyBatchPayouts(batch);
      const rows = await tx
        .select({
          transferStatus: driverPayoutRecords.transferStatus,
          transferAttemptCount: driverPayoutRecords.transferAttemptCount,
        })
        .from(driverPayoutRecords)
        .where(eq(driverPayoutRecords.batchId, batchId))
        .for("update");
      if (
        !rows.length ||
        rows.some(
          (row) => row.transferStatus !== "not_started" || row.transferAttemptCount > 0,
        )
      ) {
        throw routeError(409, "This batch has already started Paystack transfers or has no payouts.");
      }
      const now = new Date();
      const [updated] = await tx
        .update(payoutBatches)
        .set({
          status: "paid",
          settlementMethod: "external",
          paidAt: now,
          updatedAt: now,
        })
        .where(eq(payoutBatches.id, batchId))
        .returning();
      await tx.insert(adminReviewRecords).values({
        subjectUserId: actorId,
        actorUserId: actorId,
        subjectType: "driver",
        outcome: "reviewed",
        note: `Payout batch ${batchId} recorded as paid externally`,
        checksConfirmed: true,
      });
      return updated;
    });
    return res.json({ batch: toPayoutBatchResponse(result) });
  } catch (error) {
    return sendRouteError(error, res, next);
  }
});

router.patch(
  "/admin/payouts/batches/:batchId/approve",
  async (req, res, next) => {
    const actorId = await requireAdmin(req, res);
    if (!actorId) return;

    try {
      const batchId =
        typeof req.params.batchId === "string" ? req.params.batchId : "";
      const [batch] = await db
        .select()
        .from(payoutBatches)
        .where(eq(payoutBatches.id, batchId))
        .limit(1);
      if (!batch) return res.status(404).json({ error: "Payout batch was not found." });
      if (batch.status !== "open") {
        return res.status(409).json({
          error: "Only open batches can be approved.",
        });
      }

      const now = new Date();
      const [updated] = await db
        .update(payoutBatches)
        .set({ status: "submitted", updatedAt: now })
        .where(eq(payoutBatches.id, batchId))
        .returning();

      await db.insert(adminReviewRecords).values({
        subjectUserId: actorId,
        actorUserId: actorId,
        subjectType: "driver",
        outcome: "reviewed",
        note: `Payout batch ${batchId} approved for disbursement`,
        checksConfirmed: true,
      });

      return res.json({
        batch: {
          id: updated.id,
          status: updated.status,
          updatedAt: updated.updatedAt.getTime(),
        },
      });
    } catch (error) {
      return sendRouteError(error, res, next);
    }
  },
);

router.get("/admin/people/:userId", async (req, res, next) => {
  try {
    const actorId = await requireAdmin(req, res);
    if (!actorId) return;
    const userId = stringParam(req.params.userId);
    if (!userId || userId.length > 128) {
      return res.status(400).json({ error: "A valid account ID is required." });
    }
    const people = await loadPersonSummaries(eq(wayfareUsers.id, userId), 1);
    const person = people[0];
    if (!person)
      return res.status(404).json({ error: "Vhaya account was not found." });

    const [rideRows, reviewRows] = await Promise.all([
      db
        .select({
          id: rideRequests.id,
          status: rideRequests.status,
          pickup: rideRequests.pickup,
          destination: rideRequests.destination,
          riderId: rideRequests.riderId,
          driverId: rideRequests.driverId,
          fare: rideRequests.fare,
          currency: rideRequests.currency,
          createdAt: rideRequests.createdAt,
        })
        .from(rideRequests)
        .where(
          or(
            eq(rideRequests.riderId, userId),
            eq(rideRequests.driverId, userId),
          ),
        )
        .orderBy(desc(rideRequests.createdAt))
        .limit(100),
      db
        .select({
          id: adminReviewRecords.id,
          subjectType: adminReviewRecords.subjectType,
          outcome: adminReviewRecords.outcome,
          note: adminReviewRecords.note,
          checksConfirmed: adminReviewRecords.checksConfirmed,
          actorUserId: adminReviewRecords.actorUserId,
          actorDisplayName: wayfareUsers.displayName,
          applicationStatusBefore: adminReviewRecords.applicationStatusBefore,
          applicationStatusAfter: adminReviewRecords.applicationStatusAfter,
          roleBefore: adminReviewRecords.roleBefore,
          roleAfter: adminReviewRecords.roleAfter,
          createdAt: adminReviewRecords.createdAt,
        })
        .from(adminReviewRecords)
        .innerJoin(
          wayfareUsers,
          eq(wayfareUsers.id, adminReviewRecords.actorUserId),
        )
        .where(eq(adminReviewRecords.subjectUserId, userId))
        .orderBy(desc(adminReviewRecords.createdAt))
        .limit(100),
    ]);

    return res.json({
      person,
      rides: rideRows.map((ride) => ({
        id: ride.id,
        status: ride.status,
        pickup: ride.pickup,
        destination: ride.destination,
        relatedAs: ride.riderId === userId ? "requester" : "driver",
        fare: ride.fare,
        currency: ride.currency,
        createdAt: ride.createdAt,
      })),
      reviews: reviewRows,
    });
  } catch (error) {
    return sendRouteError(error, res, next);
  }
});

router.patch("/admin/people/:userId/role", async (req, res, next) => {
  try {
    const actorId = await requireAdmin(req, res);
    if (!actorId) return;
    const userId = stringParam(req.params.userId);
    const requestedRole = recordBody(req.body).role;
    if (!userId || userId.length > 128 || !isWayfareRole(requestedRole)) {
      return res
        .status(400)
        .json({ error: "Choose a valid account and role." });
    }
    if (userId === actorId) {
      return res
        .status(409)
        .json({ error: "You cannot change your own role." });
    }

    await db.transaction(async (tx) => {
      await tx.execute(sql`select pg_advisory_xact_lock(741908, 31)`);
      const targets = await tx
        .select({ id: wayfareUsers.id, role: wayfareUsers.role })
        .from(wayfareUsers)
        .where(eq(wayfareUsers.id, userId))
        .limit(1);
      const target = targets[0];
      if (!target) throw routeError(404, "Vhaya account was not found.");
      const previousRole = await getWayfareRole(userId);
      if (previousRole === requestedRole) return;

      if (requestedRole === "driver") {
        const profiles = await tx
          .select({
            applicationStatus: driverProfiles.applicationStatus,
            verificationStatus: driverProfiles.verificationStatus,
          })
          .from(driverProfiles)
          .where(eq(driverProfiles.userId, userId))
          .limit(1);
        if (
          profiles[0]?.applicationStatus !== "approved" ||
          profiles[0]?.verificationStatus !== "approved"
        ) {
          throw routeError(
            409,
            "Driver access requires an approved and verified application.",
          );
        }
      }

      if (previousRole === "admin" && requestedRole !== "admin") {
        const admins = await tx
          .select({ id: wayfareUsers.id })
          .from(wayfareUsers)
          .where(eq(wayfareUsers.role, "admin"));
        if (admins.length <= 1) {
          throw routeError(409, "The last operations admin cannot be removed.");
        }
      }

      const now = new Date();
      await updateFirebaseRole(userId, requestedRole);
      await tx
        .update(wayfareUsers)
        .set({ role: requestedRole, updatedAt: now })
        .where(eq(wayfareUsers.id, userId));
      if (previousRole === "driver" && requestedRole !== "driver") {
        await tx
          .update(driverProfiles)
          .set({
            isOnline: false,
            locationLatitude: null,
            locationLongitude: null,
            locationUpdatedAt: null,
            updatedAt: now,
          })
          .where(eq(driverProfiles.userId, userId));
      }
      await tx.insert(adminReviewRecords).values({
        subjectUserId: userId,
        actorUserId: actorId,
        subjectType:
          previousRole === "driver" || requestedRole === "driver"
            ? "driver"
            : "requester",
        outcome: "reviewed",
        note: "Role changed through staff provisioning.",
        checksConfirmed: requestedRole === "driver",
        roleBefore: previousRole,
        roleAfter: requestedRole,
      });
    });

    const people = await loadPersonSummaries(eq(wayfareUsers.id, userId), 1);
    if (!people[0])
      return res.status(404).json({ error: "Vhaya account was not found." });
    return res.json({ person: people[0] });
  } catch (error) {
    return sendRouteError(error, res, next);
  }
});

/**
 * PATCH /admin/people/:userId/suspend
 * Suspends or unsuspends a user account. Suspended users cannot request rides
 * or receive payouts. Only admins can perform this action.
 */
router.patch("/admin/people/:userId/suspend", async (req, res, next) => {
  const actorId = await requireAdmin(req, res);
  if (!actorId) return;
  const userId = stringParam(req.params.userId);
  const body = recordBody(req.body);
  const suspend = body.suspend === true;
  const reason =
    typeof body.reason === "string" ? body.reason.trim() || null : null;

  if (!userId || userId.length > 128) {
    return res.status(400).json({ error: "A valid account ID is required." });
  }
  if (userId === actorId) {
    return res.status(409).json({ error: "You cannot suspend your own account." });
  }
  if (suspend && !reason) {
    return res.status(400).json({ error: "A suspension reason is required." });
  }

  try {
    await db.transaction(async (tx) => {
      await tx.execute(sql`select pg_advisory_xact_lock(741908, 32)`);
      const targets = await tx
        .select({ id: wayfareUsers.id, role: wayfareUsers.role, isSuspended: wayfareUsers.isSuspended })
        .from(wayfareUsers)
        .where(eq(wayfareUsers.id, userId))
        .limit(1);
      const target = targets[0];
      if (!target) throw routeError(404, "Vhaya account was not found.");
      if (target.isSuspended === suspend) return;

      const now = new Date();
      await tx
        .update(wayfareUsers)
        .set({
          isSuspended: suspend,
          suspendedAt: suspend ? now : null,
          suspendedReason: suspend ? reason : null,
          suspendedBy: suspend ? actorId : null,
          updatedAt: now,
        })
        .where(eq(wayfareUsers.id, userId));

      if (suspend && target.role === "driver") {
        await tx
          .update(driverProfiles)
          .set({
            isOnline: false,
            locationLatitude: null,
            locationLongitude: null,
            locationUpdatedAt: null,
            updatedAt: now,
          })
          .where(eq(driverProfiles.userId, userId));
      }

      await tx.insert(adminReviewRecords).values({
        subjectUserId: userId,
        actorUserId: actorId,
        subjectType: target.role === "driver" ? "driver" : "requester",
        outcome: suspend ? "rejected" : "reviewed",
        note: suspend
          ? `Account suspended: ${reason}`
          : "Account suspension lifted",
        checksConfirmed: !suspend,
      });
    });

    return res.json({
      userId,
      isSuspended: suspend,
      suspendedAt: suspend ? new Date().getTime() : null,
      suspendedReason: suspend ? reason : null,
    });
  } catch (error) {
    return sendRouteError(error, res, next);
  }
});

router.post("/admin/people/:userId/reviews", async (req, res, next) => {
  try {
    const actorId = await requireAdmin(req, res);
    if (!actorId) return;
    const userId = stringParam(req.params.userId);
    const body = recordBody(req.body);
    const subjectType = body.subjectType;
    const outcome = body.outcome;
    const checksConfirmed = body.checksConfirmed === true;
    const note =
      body.note === undefined || body.note === null
        ? null
        : typeof body.note === "string"
          ? body.note.trim() || null
          : undefined;
    if (
      !userId ||
      userId.length > 128 ||
      (subjectType !== "requester" && subjectType !== "driver") ||
      typeof outcome !== "string" ||
      !reviewOutcomes.includes(outcome as ReviewOutcome) ||
      note === undefined ||
      (typeof note === "string" && note.length > 1000) ||
      (typeof body.checksConfirmed !== "undefined" &&
        typeof body.checksConfirmed !== "boolean")
    ) {
      return res.status(400).json({ error: "Review details are invalid." });
    }
    if (
      subjectType === "driver" &&
      (outcome === "rejected" || outcome === "needs_follow_up") &&
      !note
    ) {
      return res.status(400).json({
        error: "Add a staff note explaining the rejection or follow-up request.",
      });
    }

    const review = await db.transaction(async (tx) => {
      const targets = await tx
        .select({ id: wayfareUsers.id, role: wayfareUsers.role })
        .from(wayfareUsers)
        .where(eq(wayfareUsers.id, userId))
        .limit(1);
      const target = targets[0];
      if (!target) throw routeError(404, "Vhaya account was not found.");

      let applicationStatusBefore: string | null = null;
      let applicationStatusAfter: string | null = null;
      const currentRole = await getWayfareRole(userId);
      let roleAfter = currentRole;
      let driverProfile:
        {
          applicationStatus: string;
          verificationStatus: string;
          licenceExpiry: string | null;
          submittedAt: Date | null;
        } | undefined;

      if (subjectType === "driver") {
        const profiles = await tx
          .select({
            applicationStatus: driverProfiles.applicationStatus,
            verificationStatus: driverProfiles.verificationStatus,
            licenceExpiry: driverProfiles.licenceExpiry,
            submittedAt: driverProfiles.submittedAt,
          })
          .from(driverProfiles)
          .where(eq(driverProfiles.userId, userId))
          .limit(1);
        driverProfile = profiles[0];
        if (!driverProfile)
          throw routeError(404, "Driver application was not found.");
        applicationStatusBefore = driverProfile.applicationStatus;

        if (
          outcome === "approved" ||
          outcome === "rejected" ||
          outcome === "needs_follow_up"
        ) {
          if (
            !pendingApplicationStatuses.includes(
              driverProfile.applicationStatus as (typeof pendingApplicationStatuses)[number],
            )
          ) {
            throw routeError(
              409,
              "This driver application is no longer awaiting a decision.",
            );
          }
          if (outcome === "approved" && !checksConfirmed) {
            throw routeError(
              400,
              "Confirm the required off-platform checks before approval.",
            );
          }
          if (outcome === "approved") {
            const complianceRows = await tx
              .select({
                documentType: driverComplianceChecks.documentType,
                status: driverComplianceChecks.status,
                expiresOn: driverComplianceChecks.expiresOn,
                staffNote: driverComplianceChecks.staffNote,
                reviewedAt: driverComplianceChecks.reviewedAt,
              })
              .from(driverComplianceChecks)
              .where(eq(driverComplianceChecks.userId, userId));
            const checksByType = new Map(
              complianceRows.map((check) => [check.documentType, check]),
            );
            const checks = complianceDocumentTypes.map((documentType) => {
              const stored = checksByType.get(documentType);
              const needsRecheck = isDriverComplianceStale(
                stored?.reviewedAt ?? null,
                driverProfile?.submittedAt ?? null,
              );
              return {
                documentType,
                status: needsRecheck
                  ? "needs_follow_up"
                  : stored?.status ?? "not_reviewed",
                expiresOn:
                  stored?.expiresOn ??
                  (documentType === "driver_license"
                    ? driverProfile?.licenceExpiry ?? null
                    : null),
                staffNote: stored?.staffNote ?? null,
                reviewedAt: stored?.reviewedAt ?? null,
              };
            });
            const incompleteDocuments = missingDriverCompliance(
              checks,
              launchMarketToday(),
            );
            if (incompleteDocuments.length > 0) {
              throw routeError(
                409,
                `Complete these driver checks before approval: ${incompleteDocuments
                  .map(complianceDocumentLabel)
                  .join(", ")}.`,
              );
            }
          }
          const now = new Date();
          const nextApplicationStatus =
            outcome === "needs_follow_up" ? "needs_follow_up" : outcome;
          applicationStatusAfter = nextApplicationStatus;
          await tx
            .update(driverProfiles)
            .set({
              applicationStatus: nextApplicationStatus,
              verificationStatus:
                outcome === "approved"
                  ? "approved"
                  : outcome === "rejected"
                    ? "rejected"
                    : "pending",
              staffNote: note,
              isOnline: false,
              locationLatitude: null,
              locationLongitude: null,
              locationUpdatedAt: null,
              reviewedAt: now,
              updatedAt: now,
            })
            .where(
              and(
                eq(driverProfiles.userId, userId),
                inArray(driverProfiles.applicationStatus, [
                  ...pendingApplicationStatuses,
                ]),
              ),
            );
          roleAfter =
            outcome === "approved"
              ? "driver"
              : outcome === "rejected" && currentRole === "driver"
                ? "rider"
                : currentRole;
          if (roleAfter !== currentRole) {
            await updateFirebaseRole(userId, roleAfter);
            await tx
              .update(wayfareUsers)
              .set({ role: roleAfter, updatedAt: now })
              .where(eq(wayfareUsers.id, userId));
          }
        }
      } else if (outcome === "approved" || outcome === "rejected") {
        throw routeError(
          400,
          "Requester reviews can only be marked reviewed or needing follow-up.",
        );
      }

      const [record] = await tx
        .insert(adminReviewRecords)
        .values({
          subjectUserId: userId,
          actorUserId: actorId,
          subjectType: subjectType as ReviewSubjectType,
          outcome: outcome as ReviewOutcome,
          note,
          checksConfirmed,
          applicationStatusBefore,
          applicationStatusAfter,
          roleBefore: currentRole,
          roleAfter,
        })
        .returning();
      if (!record)
        throw new Error("The review audit record could not be saved.");
      return record;
    });

    const [actor] = await db
      .select({ displayName: wayfareUsers.displayName })
      .from(wayfareUsers)
      .where(eq(wayfareUsers.id, actorId))
      .limit(1);
    const people = await loadPersonSummaries(eq(wayfareUsers.id, userId), 1);
    if (!people[0])
      return res.status(404).json({ error: "Vhaya account was not found." });
    return res.status(201).json({
      review: {
        ...review,
        actorDisplayName: actor?.displayName ?? "Operations admin",
      },
      person: people[0],
    });
  } catch (error) {
    return sendRouteError(error, res, next);
  }
});

router.get("/admin/account-deletion-requests", async (req, res, next) => {
  const actorId = await requireAdmin(req, res);
  if (!actorId) return;

  try {
    const rows = await db
      .select({
        id: accountDeletionRequests.id,
        userId: accountDeletionRequests.userId,
        displayName: wayfareUsers.displayName,
        email: wayfareUsers.email,
        role: wayfareUsers.role,
        status: accountDeletionRequests.status,
        requestedAt: accountDeletionRequests.requestedAt,
        retainedUserId: accountDeletionRequests.retainedUserId,
        reason: accountDeletionRequests.reason,
      })
      .from(accountDeletionRequests)
      .innerJoin(wayfareUsers, eq(wayfareUsers.id, accountDeletionRequests.userId))
      .where(inArray(accountDeletionRequests.status, ["requested", "processing"]))
      .orderBy(asc(accountDeletionRequests.requestedAt))
      .limit(100);

    res.json(
      GetAdminAccountDeletionRequestsResponse.parse({
        requests: rows.map((row) => ({
          ...row,
          requestedAt: row.requestedAt.toISOString(),
          pseudonymized: Boolean(row.retainedUserId),
        })),
      }),
    );
  } catch (error) {
    next(error);
  }
});

router.post(
  "/admin/account-deletion-requests/:requestId/complete",
  async (req, res, next) => {
    const actorId = await requireAdmin(req, res);
    if (!actorId) return;

    const params = CompleteAdminAccountDeletionRequestParams.safeParse(req.params);
    const body = CompleteAdminAccountDeletionRequestBody.safeParse(req.body);
    if (!params.success || !body.success) {
      res.status(400).json({
        error:
          "Confirm that you verified account ownership and checked provider subscriptions.",
      });
      return;
    }

    try {
      const result = await completeAccountDeletion(params.data.requestId, {
        actorUserId: actorId,
        externalSubscriptionsCancelled: body.data.externalSubscriptionsCancelled,
      });
      const response = CompleteAdminAccountDeletionRequestResponse.parse(result);
      res.status(response.deleted ? 200 : 202).json(response);
    } catch (error) {
      sendRouteError(error, res, next);
    }
  },
);

router.post(
  "/admin/account-deletion-requests/:requestId/reject",
  async (req, res, next) => {
    const actorId = await requireAdmin(req, res);
    if (!actorId) return;

    const params = RejectAdminAccountDeletionRequestParams.safeParse(req.params);
    const body = RejectAdminAccountDeletionRequestBody.safeParse(req.body);
    if (!params.success || !body.success) {
      res.status(400).json({ error: "A review reason of at least five characters is required." });
      return;
    }

    try {
      const [request] = await db
        .select({
          id: accountDeletionRequests.id,
          status: accountDeletionRequests.status,
          retainedUserId: accountDeletionRequests.retainedUserId,
        })
        .from(accountDeletionRequests)
        .where(eq(accountDeletionRequests.id, params.data.requestId))
        .limit(1);
      if (!request) {
        res.status(404).json({ error: "Deletion request was not found." });
        return;
      }
      if (request.status !== "requested" || request.retainedUserId) {
        res.status(409).json({ error: "Only unprocessed requests can be rejected." });
        return;
      }

      const [updatedRequest] = await db
        .update(accountDeletionRequests)
        .set({ status: "rejected", reason: body.data.reason.trim() })
        .where(
          and(
            eq(accountDeletionRequests.id, params.data.requestId),
            eq(accountDeletionRequests.status, "requested"),
          ),
        )
        .returning({ id: accountDeletionRequests.id });
      if (!updatedRequest) {
        res.status(409).json({ error: "This request has already started processing." });
        return;
      }
      await db.insert(auditEvents).values({
        id: randomUUID(),
        actorUserId: actorId,
        entityType: "account_deletion_request",
        entityId: params.data.requestId,
        action: "account_deletion_request_rejected",
        previousState: null,
        nextState: { status: "rejected" },
        reason: null,
      });
      res.json({ rejected: true });
    } catch (error) {
      sendRouteError(error, res, next);
    }
  },
);

export default router;

import { randomUUID } from "node:crypto";
import { and, eq, gt, inArray, isNotNull, isNull, notInArray, or, sql } from "drizzle-orm";
import { db } from "@workspace/db";
import {
  accountDeletionRequests,
  adminReviewRecords,
  auditEvents,
  authSessions,
  conversationParticipants,
  driverApplicationDocuments,
  driverComplianceChecks,
  driverPayoutRecords,
  driverProfiles,
  driverPromotionRedemptions,
  driverRatingRecords,
  driverRequestSkips,
  driverSubscriptionRecords,
  driverSubscriptions,
  idempotencyKeys,
  messages,
  payoutBatches,
  pushTokens,
  rideEvents,
  rideOffers,
  ridePayments,
  rideRequests,
  supportCases,
  walletTopups,
  walletTransactions,
  wallets,
  wayfareUsers,
} from "@workspace/db/schema";
import { deleteFirebaseWayfareAccount } from "./firebaseAdmin";
import { logger } from "./logger";
import { deleteAccountPrivateObjects } from "./privateObjectDeletion";

const terminalRideStatuses = [
  "completed",
  "cancelled",
  "unfulfilled",
  "expired",
  "disputed",
  "refunded",
] as const;

const retainedDataNotice = [
  "Ride and payment totals needed for reconciliation",
  "Safety, dispute, and audit records in pseudonymized form",
];

function routeError(status: number, message: string) {
  return Object.assign(new Error(message), { status });
}

function errorCode(error: unknown): string | undefined {
  if (
    error &&
    typeof error === "object" &&
    "code" in error &&
    /^[A-Za-z0-9/_-]{1,64}$/.test(String(error.code))
  ) {
    return String(error.code);
  }
  return undefined;
}

export async function createSelfDeletionRequest(
  userId: string,
  reason?: string,
): Promise<string> {
  const id = randomUUID();
  await db.insert(accountDeletionRequests).values({
    id,
    userId,
    status: "requested",
    ...(reason?.trim() ? { reason: reason.trim().slice(0, 500) } : {}),
  });
  return id;
}

export async function completeAccountDeletion(
  requestId: string,
  options: {
    actorUserId?: string | null;
    externalSubscriptionsCancelled: boolean;
  },
): Promise<{
  requestId: string;
  status: "completed" | "processing";
  deleted: boolean;
  retainedData: string[];
}> {
  const prepared = await db.transaction(async (tx) => {
    const [request] = await tx
      .select()
      .from(accountDeletionRequests)
      .where(eq(accountDeletionRequests.id, requestId))
      .for("update");

    if (!request) throw routeError(404, "Deletion request was not found.");
    if (request.status === "completed") {
      return {
        complete: true as const,
        originalUserId: "",
        retainedUserId: "",
        pendingDocumentObjectKeys: [] as string[],
        pendingMessageAttachmentReferences: [] as string[],
      };
    }
    if (request.status === "rejected") {
      throw routeError(409, "This deletion request was rejected.");
    }

    const originalUserId = request.userId;
    const [account] = await tx
      .select()
      .from(wayfareUsers)
      .where(eq(wayfareUsers.id, originalUserId))
      .for("update");

    if (!account) throw routeError(404, "The account was not found.");

    if (request.retainedUserId) {
      return {
        complete: false as const,
        originalUserId,
        retainedUserId: request.retainedUserId,
        pendingDocumentObjectKeys: request.pendingDocumentObjectKeys,
        pendingMessageAttachmentReferences:
          request.pendingMessageAttachmentReferences,
      };
    }

    if (account.deletedAt) {
      throw routeError(409, "This account has already been deleted.");
    }
    if (account.role !== "rider" && account.role !== "driver") {
      throw routeError(403, "Only rider and driver accounts can be deleted here.");
    }

    const activeRide = await tx
      .select({ id: rideRequests.id })
      .from(rideRequests)
      .where(
        and(
          or(
            eq(rideRequests.riderId, originalUserId),
            eq(rideRequests.driverId, originalUserId),
          ),
          notInArray(rideRequests.status, [...terminalRideStatuses]),
        ),
      )
      .limit(1);
    if (activeRide[0]) {
      throw routeError(
        409,
        "Finish or cancel your active ride before deleting your account.",
      );
    }

    const activeSubscription = await tx
      .select({
        id: driverSubscriptions.driverId,
        autoRenewActive: driverSubscriptions.autoRenewActive,
      })
      .from(driverSubscriptions)
      .where(
        and(
          eq(driverSubscriptions.driverId, originalUserId),
          or(
            eq(driverSubscriptions.status, "pending"),
            and(
              eq(driverSubscriptions.status, "active"),
              or(
                isNull(driverSubscriptions.expiresAt),
                gt(driverSubscriptions.expiresAt, new Date()),
              ),
            ),
            eq(driverSubscriptions.autoRenewActive, true),
          ),
        ),
      )
      .limit(1);
    const activeLedgerSubscription = await tx
      .select({ id: driverSubscriptionRecords.id })
      .from(driverSubscriptionRecords)
      .where(
        and(
          eq(driverSubscriptionRecords.driverId, originalUserId),
          inArray(driverSubscriptionRecords.status, ["pending", "active"]),
          or(
            isNull(driverSubscriptionRecords.expiresAt),
            gt(driverSubscriptionRecords.expiresAt, new Date()),
          ),
        ),
      )
      .limit(1);
    if (
      (activeSubscription[0] || activeLedgerSubscription[0]) &&
      !options.externalSubscriptionsCancelled
    ) {
      throw routeError(
        409,
        "Cancel any active driver subscription with its provider before deleting the account.",
      );
    }

    const documentRows = await tx
      .select({ objectKey: driverApplicationDocuments.objectKey })
      .from(driverApplicationDocuments)
      .where(eq(driverApplicationDocuments.userId, originalUserId));
    const messageAttachmentRows = await tx
      .select({ attachmentRef: messages.attachmentRef })
      .from(messages)
      .where(
        and(
          eq(messages.senderUserId, originalUserId),
          isNotNull(messages.attachmentRef),
        ),
      );
    const pendingDocumentObjectKeys = [
      ...new Set([
        ...request.pendingDocumentObjectKeys,
        ...documentRows.map((row) => row.objectKey),
      ]),
    ];
    const pendingMessageAttachmentReferences = [
      ...new Set([
        ...request.pendingMessageAttachmentReferences,
        ...messageAttachmentRows.flatMap((row) =>
          row.attachmentRef ? [row.attachmentRef] : [],
        ),
      ]),
    ];

    const now = new Date();
    const retainedUserId = `anon_${randomUUID()}`;
    await tx.insert(wayfareUsers).values({
      id: retainedUserId,
      displayName: "Deleted account",
      email: null,
      role: account.role,
      devVerified: false,
      isSuspended: true,
      suspendedAt: now,
      suspendedReason: null,
      suspendedBy: null,
      deletedAt: now,
      updatedAt: now,
    });

    if (activeSubscription[0]) {
      await tx
        .update(driverSubscriptions)
        .set({
          status: "cancelled",
          renewalMode: "manual",
          autoRenewActive: false,
          autoRenewIssue: null,
          cancelAtPeriodEnd: true,
          providerSubscriptionCode: null,
          providerEmailToken: null,
          cancelledAt: now,
          cancelledReason: "Cancelled as part of verified account deletion",
          updatedAt: now,
        })
        .where(
          and(
            eq(driverSubscriptions.driverId, originalUserId),
            inArray(driverSubscriptions.status, ["pending", "active"]),
          ),
        );
    }

    const relatedRideRows = await tx
      .select({ id: rideRequests.id, riderId: rideRequests.riderId })
      .from(rideRequests)
      .where(
        or(
          eq(rideRequests.riderId, originalUserId),
          eq(rideRequests.driverId, originalUserId),
        ),
      );
    const relatedRideIds = relatedRideRows.map((ride) => ride.id);
    const riderRideIds = relatedRideRows
      .filter((ride) => ride.riderId === originalUserId)
      .map((ride) => ride.id);

    await tx
      .delete(driverApplicationDocuments)
      .where(eq(driverApplicationDocuments.userId, originalUserId));
    await tx
      .delete(driverComplianceChecks)
      .where(eq(driverComplianceChecks.userId, originalUserId));
    await tx
      .update(driverComplianceChecks)
      .set({ reviewedBy: retainedUserId })
      .where(eq(driverComplianceChecks.reviewedBy, originalUserId));
    await tx
      .delete(driverProfiles)
      .where(eq(driverProfiles.userId, originalUserId));
    await tx.delete(authSessions).where(eq(authSessions.userId, originalUserId));
    await tx.delete(pushTokens).where(eq(pushTokens.userId, originalUserId));
    await tx
      .delete(idempotencyKeys)
      .where(eq(idempotencyKeys.userId, originalUserId));
    await tx
      .delete(conversationParticipants)
      .where(eq(conversationParticipants.userId, originalUserId));
    await tx
      .update(messages)
      .set({
        senderUserId: null,
        content: null,
        attachmentRef: null,
        deletedAt: now,
        retainedUntil: now,
      })
      .where(eq(messages.senderUserId, originalUserId));

    if (riderRideIds.length > 0) {
      await tx
        .delete(rideOffers)
        .where(inArray(rideOffers.requestId, riderRideIds));
      await tx
        .update(rideRequests)
        .set({
          riderId: retainedUserId,
          pickup: "Removed",
          destination: "Removed",
          pickupLatitude: null,
          pickupLongitude: null,
          destinationLatitude: null,
          destinationLongitude: null,
          route: null,
          tripData: {},
          updatedAt: now,
        })
        .where(eq(rideRequests.riderId, originalUserId));
      await tx
        .update(supportCases)
        .set({ staffNote: null })
        .where(inArray(supportCases.requestId, riderRideIds));
    }

    await tx
      .delete(rideOffers)
      .where(eq(rideOffers.driverId, originalUserId));
    await tx
      .delete(driverRequestSkips)
      .where(eq(driverRequestSkips.driverId, originalUserId));
    await tx
      .update(rideRequests)
      .set({
        driverId: null,
        tripData: sql`${rideRequests.tripData} - 'driverProfile' - 'offers' - 'messages' - 'driverId'`,
        updatedAt: now,
      })
      .where(eq(rideRequests.driverId, originalUserId));

    if (relatedRideIds.length > 0) {
      await tx
        .update(rideEvents)
        .set({ details: {} })
        .where(inArray(rideEvents.requestId, relatedRideIds));
      await tx
        .update(supportCases)
        .set({ staffNote: null })
        .where(inArray(supportCases.requestId, relatedRideIds));
    }
    await tx
      .update(rideEvents)
      .set({ actorId: null })
      .where(eq(rideEvents.actorId, originalUserId));
    await tx
      .update(ridePayments)
      .set({ riderId: retainedUserId, authorizationUrl: null })
      .where(eq(ridePayments.riderId, originalUserId));
    await tx
      .update(ridePayments)
      .set({ driverId: null, authorizationUrl: null })
      .where(eq(ridePayments.driverId, originalUserId));

    await tx
      .update(supportCases)
      .set({ reporterUserId: retainedUserId, staffNote: null })
      .where(eq(supportCases.reporterUserId, originalUserId));
    await tx
      .update(supportCases)
      .set({ assignedTo: null })
      .where(eq(supportCases.assignedTo, originalUserId));
    await tx
      .update(adminReviewRecords)
      .set({
        subjectUserId: retainedUserId,
        note: null,
      })
      .where(eq(adminReviewRecords.subjectUserId, originalUserId));
    await tx
      .update(adminReviewRecords)
      .set({ actorUserId: retainedUserId, note: null })
      .where(eq(adminReviewRecords.actorUserId, originalUserId));
    await tx
      .update(driverPayoutRecords)
      .set({ driverId: retainedUserId, note: null })
      .where(eq(driverPayoutRecords.driverId, originalUserId));
    await tx
      .update(driverPayoutRecords)
      .set({ recordedBy: retainedUserId })
      .where(eq(driverPayoutRecords.recordedBy, originalUserId));
    await tx
      .update(payoutBatches)
      .set({ createdBy: retainedUserId })
      .where(eq(payoutBatches.createdBy, originalUserId));
    await tx
      .update(wallets)
      .set({ userId: retainedUserId, updatedAt: now })
      .where(eq(wallets.userId, originalUserId));
    await tx
      .update(walletTransactions)
      .set({
        userId: retainedUserId,
        reference: null,
        description: null,
      })
      .where(eq(walletTransactions.userId, originalUserId));
    await tx
      .update(walletTopups)
      .set({ userId: retainedUserId, authorizationUrl: null, updatedAt: now })
      .where(eq(walletTopups.userId, originalUserId));
    await tx
      .update(driverSubscriptionRecords)
      .set({ driverId: retainedUserId, metadata: {} })
      .where(eq(driverSubscriptionRecords.driverId, originalUserId));
    await tx
      .update(driverSubscriptions)
      .set({
        driverId: retainedUserId,
        providerSubscriptionCode: null,
        providerEmailToken: null,
        autoRenewConsentAt: null,
        autoRenewActive: false,
        autoRenewIssue: null,
        updatedAt: now,
      })
      .where(eq(driverSubscriptions.driverId, originalUserId));
    await tx
      .update(driverPromotionRedemptions)
      .set({ driverId: retainedUserId })
      .where(eq(driverPromotionRedemptions.driverId, originalUserId));
    await tx
      .update(driverRatingRecords)
      .set({ driverId: retainedUserId })
      .where(eq(driverRatingRecords.driverId, originalUserId));
    await tx
      .update(auditEvents)
      .set({
        actorUserId: null,
        entityId: sql`case when ${auditEvents.entityId} = ${originalUserId} then ${retainedUserId} else ${auditEvents.entityId} end`,
        previousState: null,
        nextState: null,
        reason: null,
      })
      .where(
        or(
          eq(auditEvents.actorUserId, originalUserId),
          eq(auditEvents.entityId, originalUserId),
        ),
      );
    await tx
      .update(wayfareUsers)
      .set({ suspendedBy: null })
      .where(eq(wayfareUsers.suspendedBy, originalUserId));
    await tx
      .update(accountDeletionRequests)
      .set({
        retainedUserId,
        status: "processing",
        pendingDocumentObjectKeys,
        pendingMessageAttachmentReferences,
      })
      .where(
        and(
          eq(accountDeletionRequests.userId, originalUserId),
          inArray(accountDeletionRequests.status, ["requested", "processing"]),
        ),
      );
    await tx
      .update(wayfareUsers)
      .set({
        displayName: "Deleted account",
        email: null,
        devVerified: false,
        isSuspended: true,
        suspendedAt: now,
        suspendedReason: null,
        suspendedBy: null,
        deletedAt: now,
        updatedAt: now,
      })
      .where(eq(wayfareUsers.id, originalUserId));

    return {
      complete: false as const,
      originalUserId,
      retainedUserId,
      pendingDocumentObjectKeys,
      pendingMessageAttachmentReferences,
    };
  });

  if (prepared.complete) {
    return {
      requestId,
      status: "completed",
      deleted: true,
      retainedData: retainedDataNotice,
    };
  }

  try {
    await deleteAccountPrivateObjects({
      driverDocumentKeys: prepared.pendingDocumentObjectKeys,
      messageAttachmentReferences:
        prepared.pendingMessageAttachmentReferences,
    });
    await db.transaction(async (tx) => {
      await tx
        .update(accountDeletionRequests)
        .set({
          pendingDocumentObjectKeys: [],
          pendingMessageAttachmentReferences: [],
        })
        .where(
          and(
            eq(accountDeletionRequests.userId, prepared.originalUserId),
            eq(accountDeletionRequests.status, "processing"),
          ),
        );
    });
  } catch (error) {
    logger.error(
      {
        requestId,
        errorType: error instanceof Error ? error.name : "UnknownError",
        errorCode: errorCode(error),
      },
      "Private account files need a deletion retry",
    );
    return {
      requestId,
      status: "processing",
      deleted: false,
      retainedData: retainedDataNotice,
    };
  }

  try {
    await deleteFirebaseWayfareAccount(prepared.originalUserId);
  } catch (error) {
    logger.error(
      {
        requestId,
        errorType: error instanceof Error ? error.name : "UnknownError",
        errorCode: errorCode(error),
      },
      "Firebase account deletion needs a retry",
    );
    return {
      requestId,
      status: "processing",
      deleted: false,
      retainedData: retainedDataNotice,
    };
  }

  await db.transaction(async (tx) => {
    await tx
      .update(accountDeletionRequests)
      .set({
        userId: prepared.retainedUserId,
        retainedUserId: null,
        status: "completed",
        completedAt: new Date(),
        reason: null,
      })
      .where(
        and(
          eq(accountDeletionRequests.userId, prepared.originalUserId),
          inArray(accountDeletionRequests.status, ["requested", "processing"]),
        ),
      );
    await tx
      .update(accountDeletionRequests)
      .set({
        userId: prepared.retainedUserId,
        retainedUserId: null,
        reason: null,
      })
      .where(eq(accountDeletionRequests.userId, prepared.originalUserId));
    await tx
      .delete(wayfareUsers)
      .where(eq(wayfareUsers.id, prepared.originalUserId));
    await tx.insert(auditEvents).values({
      id: randomUUID(),
      actorUserId: options.actorUserId ?? null,
      entityType: "account_deletion_request",
      entityId: requestId,
      action: "account_deletion_completed",
      previousState: null,
      nextState: { retainedData: true },
      reason: null,
    });
  });

  return {
    requestId,
    status: "completed",
    deleted: true,
    retainedData: retainedDataNotice,
  };
}

export function deletionStatusMessage(status: string): {
  status: "received" | "processing" | "completed" | "unable_to_complete";
  message: string;
} {
  switch (status) {
    case "processing":
      return {
        status: "processing",
        message: "Your account is being deleted. Check this page for an update.",
      };
    case "completed":
      return {
        status: "completed",
        message:
          "Your profile has been deleted. Some ride, payment, safety, and audit records are retained in pseudonymized form where needed.",
      };
    case "rejected":
      return {
        status: "unable_to_complete",
        message:
          "We could not complete this request. Check that you used the email on the account and submit a new request.",
      };
    default:
      return {
        status: "received",
        message:
          "If an account matches this email, the request is queued for ownership verification.",
      };
  }
}

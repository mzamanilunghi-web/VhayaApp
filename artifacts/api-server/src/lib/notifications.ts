// Server notification event pipeline.
//
// Phase 1 (platform foundations): "Realtime transport, normalized messaging,
// server notification event pipeline, and push token registry."
//
// Provides:
//   - lookup of active push tokens for a user (server-side registry)
//   - a type-safe notification event envelope with a deep-link contract
//   - an audit-trail helper that records every published event
//
// Actual provider delivery (FCM/APNS) is intentionally left as a thin
// integration point here so a commercial push provider can be swapped in
// without touching the event schema or the REST/SSE transport.

import { randomUUID } from "node:crypto";
import { db } from "@workspace/db";
import { pushTokens, auditEvents } from "@workspace/db/schema";
import { eq, and, isNull } from "drizzle-orm";
import { logger } from "./logger";

export type NotificationChannel = "push" | "in_app" | "sse";

export type NotificationEventType =
  | "ride.offered"
  | "ride.offer_expired"
  | "ride.accepted"
  | "ride.cancelled"
  | "ride.arrived"
  | "ride.in_progress"
  | "ride.completed"
  | "ride.payment_captured"
  | "ride.refunded"
  | "support.message"
  | "support.case_updated"
  | "safety.sos"
  | "system.announcement";

export interface NotificationEventPayload {
  userId: string;
  type: NotificationEventType;
  title: string;
  body: string;
  deepLink?: string;
  rideRequestId?: string;
  conversationId?: string;
  data?: Record<string, string | number | boolean>;
}

export interface NotificationDeliveryRecord {
  eventId: string;
  userId: string;
  channel: NotificationChannel;
  token?: string;
  status: "queued" | "sent" | "delivered" | "failed";
  providerMessageId?: string;
  error?: string;
  createdAt: number;
}

const ACTIVE_TOKEN_MAX_AGE_MS = 90 * 24 * 60 * 60 * 1000;

export async function getActivePushTokens(
  userId: string,
): Promise<Array<{ id: string; token: string; platform: string }>> {
  const rows = await db
    .select({
      id: pushTokens.id,
      token: pushTokens.token,
      platform: pushTokens.platform,
      createdAt: pushTokens.createdAt,
    })
    .from(pushTokens)
    .where(
      and(
        eq(pushTokens.userId, userId),
        eq(pushTokens.optIn, true),
        isNull(pushTokens.invalidatedAt),
      ),
    );
  const now = Date.now();
  return rows.filter(
    (r) => r.createdAt.getTime() + ACTIVE_TOKEN_MAX_AGE_MS > now,
  );
}

export async function recordNotificationEvent(
  actorUserId: string | null,
  entityType: string,
  entityId: string,
  action: string,
  payload: NotificationEventPayload,
  deliveries: NotificationDeliveryRecord[],
): Promise<void> {
  await db.insert(auditEvents).values({
    id: `evt-${randomUUID()}`,
    actorUserId,
    entityType,
    entityId,
    action,
    previousState: null,
    nextState: {
      type: payload.type,
      title: payload.title,
      body: payload.body,
      deepLink: payload.deepLink ?? null,
      rideRequestId: payload.rideRequestId ?? null,
      conversationId: payload.conversationId ?? null,
      data: payload.data ?? {},
      deliveries,
    },
    reason: `notification:${payload.type}`,
    requestId: payload.rideRequestId ?? null,
    idempotencyKey: undefined,
  });
}

export function buildRideEventPayload(
  userId: string,
  type: NotificationEventType,
  rideRequestId: string,
  opts: {
    title: string;
    body: string;
    deepLink?: string;
    data?: Record<string, string | number | boolean>;
  },
): NotificationEventPayload {
  return {
    userId,
    type,
    title: opts.title,
    body: opts.body,
    deepLink: opts.deepLink ?? `vhaya://ride/${rideRequestId}`,
    rideRequestId,
    data: opts.data,
  };
}

export async function deliverToTokens(
  tokens: Array<{ id: string; token: string; platform: string }>,
  payload: NotificationEventPayload,
): Promise<NotificationDeliveryRecord[]> {
  const now = Date.now();
  const deliveries: NotificationDeliveryRecord[] = [];
  for (const t of tokens) {
    try {
      logger.info(
        {
          userId: payload.userId,
          type: payload.type,
          platform: t.platform,
          tokenId: t.id,
        },
        "Notification queued for delivery (no push provider configured)",
      );
      deliveries.push({
        eventId: `evt-${now}-${t.id}`,
        userId: payload.userId,
        channel: "push",
        token: t.token,
        status: "queued",
        createdAt: now,
      });
    } catch (error) {
      deliveries.push({
        eventId: `evt-${now}-${t.id}`,
        userId: payload.userId,
        channel: "push",
        token: t.token,
        status: "failed",
        error: error instanceof Error ? error.message : "Unknown delivery error",
        createdAt: now,
      });
    }
  }
  return deliveries;
}

export async function publishNotificationEvent(
  actorUserId: string | null,
  entityType: string,
  entityId: string,
  action: string,
  payload: NotificationEventPayload,
): Promise<NotificationDeliveryRecord[]> {
  const tokens = await getActivePushTokens(payload.userId);
  const deliveries = await deliverToTokens(tokens, payload);
  await recordNotificationEvent(
    actorUserId,
    entityType,
    entityId,
    action,
    payload,
    deliveries,
  );
  return deliveries;
}


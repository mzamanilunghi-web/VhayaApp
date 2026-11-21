import { randomUUID } from "node:crypto";
import { Router, type IRouter } from "express";
import { db } from "@workspace/db";
import { messages, conversationParticipants } from "@workspace/db/schema";
import { eq, and, desc, inArray, isNull, lt } from "drizzle-orm";
import { recordBody } from "../lib/wayfareData";
import { getWayfareUserId } from "../middlewares/firebaseIdentity";

const router: IRouter = Router();
const pageLimit = 50;

type MessageDeliveryState =
  | "sending"
  | "sent"
  | "delivered"
  | "read"
  | "failed";

const validDeliveryStates = new Set<MessageDeliveryState>([
  "sending",
  "sent",
  "delivered",
  "read",
  "failed",
]);

function statusError(error: unknown): number {
  return error && typeof error === "object" && "status" in error
    ? Number((error as { status: unknown }).status)
    : 0;
}

async function requireParticipant(
  conversationId: string,
  userId: string,
): Promise<boolean> {
  const rows = await db
    .select({ userId: conversationParticipants.userId })
    .from(conversationParticipants)
    .where(
      and(
        eq(conversationParticipants.conversationId, conversationId),
        eq(conversationParticipants.userId, userId),
      ),
    )
    .limit(1);
  return rows.length > 0;
}
router.post("/:conversationId/messages", async (req, res, next) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });
  try {
    const conversationId = String(req.params.conversationId ?? "");
    if (!conversationId) {
      return res.status(400).json({ error: "A conversation ID is required." });
    }
    if (!(await requireParticipant(conversationId, userId))) {
      return res.status(403).json({ error: "You are not a participant of this conversation." });
    }

    const body = recordBody(req.body);
    const type =
      typeof body.type === "string" ? body.type.trim() : "text";
    const content =
      typeof body.content === "string" ? body.content.trim() : "";
    const attachmentRef =
      typeof body.attachmentRef === "string" ? body.attachmentRef.trim() : undefined;
    const idempotencyKey =
      typeof body.idempotencyKey === "string" ? body.idempotencyKey.trim() : undefined;

    if (!content && !attachmentRef) {
      return res.status(400).json({ error: "A message must have content or an attachment." });
    }
    if (content.length > 4000) {
      return res.status(400).json({ error: "Message content is too long (max 4000 characters)." });
    }

    const id = `msg-${randomUUID()}`;
    const now = new Date();
    await db.insert(messages).values({
      id,
      conversationId,
      senderUserId: userId,
      type,
      content: content || null,
      contentVersion: 1,
      deliveryState: "sent",
      attachmentRef,
      createdAt: now,
    });

    return res.status(201).json({
      message: {
        id,
        conversationId,
        senderUserId: userId,
        type,
        content,
        attachmentRef,
        deliveryState: "sent",
        createdAt: now.getTime(),
      },
    });
  } catch (error) {
    const status = statusError(error);
    if (status >= 400 && status < 600) {
      return res.status(status).json({
        error: error instanceof Error ? error.message : "Message could not be sent.",
      });
    }
    return next(error);
  }
});

router.get("/:conversationId/messages", async (req, res, next) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });
  try {
    const conversationId = String(req.params.conversationId ?? "");
    if (!conversationId) {
      return res.status(400).json({ error: "A conversation ID is required." });
    }
    if (!(await requireParticipant(conversationId, userId))) {
      return res.status(403).json({ error: "You are not a participant of this conversation." });
    }

    const query = recordBody(req.query);
    const limit = Math.min(100, Math.max(1, Number(query.limit) || 30));
    const before =
      typeof query.before === "string" ? new Date(query.before) : undefined;

    const whereConditions = [
      eq(messages.conversationId, conversationId),
      isNull(messages.deletedAt),
    ];
    if (before && !Number.isNaN(before.getTime())) {
      whereConditions.push(lt(messages.createdAt, before));
    }

    // Build the query with optional cursor.
    let q = db
      .select({
        id: messages.id,
        conversationId: messages.conversationId,
        senderUserId: messages.senderUserId,
        type: messages.type,
        content: messages.content,
        attachmentRef: messages.attachmentRef,
        deliveryState: messages.deliveryState,
        createdAt: messages.createdAt,
      })
      .from(messages)
      .where(and(...whereConditions))
      .orderBy(desc(messages.createdAt))
      .limit(limit + 1);

    const rows = await q;
    const hasMore = rows.length > limit;
    const pageRows = rows.slice(0, limit).reverse();

    return res.json({
      messages: pageRows.map((m) => ({
        id: m.id,
        conversationId: m.conversationId,
        senderUserId: m.senderUserId,
        type: m.type,
        content: m.content,
        attachmentRef: m.attachmentRef,
        deliveryState: m.deliveryState,
        createdAt: m.createdAt.getTime(),
      })),
      limit,
      hasMore,
      oldestCreatedAt: pageRows[0]?.createdAt.getTime() ?? null,
    });
  } catch (error) {
    return next(error);
  }
});

router.patch("/:messageId", async (req, res, next) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });
  try {
    const messageId = String(req.params.messageId ?? "");
    if (!messageId) return res.status(400).json({ error: "A message ID is required." });

    const body = recordBody(req.body);
    const deliveryState = body.deliveryState;
    const state =
      typeof deliveryState === "string" ? deliveryState : undefined;
    if (!state || !validDeliveryStates.has(state as MessageDeliveryState)) {
      return res.status(400).json({ error: "A valid delivery state is required." });
    }

    const rows = await db
      .select({
        id: messages.id,
        conversationId: messages.conversationId,
        senderUserId: messages.senderUserId,
      })
      .from(messages)
      .where(eq(messages.id, messageId))
      .limit(1);
    const msg = rows[0];
    if (!msg) return res.status(404).json({ error: "Message not found." });

    if (!(await requireParticipant(msg.conversationId, userId))) {
      return res.status(403).json({ error: "You are not a participant of this conversation." });
    }

    await db
      .update(messages)
      .set({ deliveryState: state as MessageDeliveryState })
      .where(eq(messages.id, messageId));

    return res.json({
      message: {
        id: msg.id,
        conversationId: msg.conversationId,
        deliveryState: state,
      },
    });
  } catch (error) {
    const status = statusError(error);
    if (status >= 400 && status < 600) {
      return res.status(status).json({
        error: error instanceof Error ? error.message : "Message update failed.",
      });
    }
    return next(error);
  }
});

export default router;


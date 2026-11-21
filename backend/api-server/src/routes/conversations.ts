import { randomUUID } from "node:crypto";
import { Router, type IRouter } from "express";
import { db } from "@workspace/db";
import {
  conversations,
  conversationParticipants,
  wayfareUsers,
} from "@workspace/db/schema";
import { eq, and, or, desc, inArray, count, isNull } from "drizzle-orm";
import { recordBody } from "../lib/wayfareData";
import { getWayfareUserId } from "../middlewares/firebaseIdentity";

const router: IRouter = Router();
const pageLimit = 50;

type ConversationRow = typeof conversations.$inferSelect & {
  participantCount?: number;
  lastMessageAt?: Date | null;
  lastMessagePreview?: string | null;
};

function statusError(error: unknown): number {
  return error && typeof error === "object" && "status" in error
    ? Number((error as { status: unknown }).status)
    : 0;
}

function validConversationType(value: unknown): value is typeof conversations.$inferSelect["type"] {
  const valid = new Set([
    "rider_driver",
    "rider_support",
    "driver_support",
    "admin_incident",
    "safety_escalation",
    "system",
    "internal_note",
  ]);
  return typeof value === "string" && valid.has(value);
}

router.post("/", async (req, res, next) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });
  try {
    const body = recordBody(req.body);
    const type = body.type;
    const participantIds = Array.isArray(body.participantIds)
      ? body.participantIds.filter((id) => typeof id === "string")
      : [];
    const rideRequestId =
      typeof body.rideRequestId === "string" ? body.rideRequestId : null;

    if (!validConversationType(type)) {
      return res.status(400).json({ error: "A valid conversation type is required." });
    }
    if (!participantIds.includes(userId)) {
      participantIds.push(userId);
    }
    if (participantIds.length < 2) {
      return res.status(400).json({ error: "A conversation needs at least two participants." });
    }
    if (participantIds.length > 20) {
      return res.status(400).json({ error: "Too many participants." });
    }

    const id = `conv-${randomUUID()}`;
    const now = new Date();
    await db.transaction(async (tx) => {
      await tx.insert(conversations).values({
        id,
        type,
        rideRequestId,
        createdAt: now,
        updatedAt: now,
      });
      await tx.insert(conversationParticipants).values(
        participantIds.map((pid) => ({
          conversationId: id,
          userId: pid,
          role: pid === userId ? "creator" : "participant",
          joinedAt: now,
        })),
      );
    });

    return res.status(201).json({
      conversation: {
        id,
        type,
        rideRequestId,
        createdAt: now.getTime(),
        updatedAt: now.getTime(),
        participantCount: participantIds.length,
      },
    });
  } catch (error) {
    const status = statusError(error);
    if (status >= 400 && status < 600) {
      return res.status(status).json({
        error: error instanceof Error ? error.message : "Conversation could not be created.",
      });
    }
    return next(error);
  }
});

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
router.get("/", async (req, res, next) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });
  try {
    const query = recordBody(req.query);
    const page = Math.max(0, Number(query.page) || 0);
    const limit = Math.min(50, Math.max(1, Number(query.limit) || 25));

    const participantRows = await db
      .select({ conversationId: conversationParticipants.conversationId })
      .from(conversationParticipants)
      .where(eq(conversationParticipants.userId, userId));
    const convIds = participantRows.map((r) => r.conversationId);
    if (convIds.length === 0) {
      return res.json({ conversations: [], page, limit, hasMore: false });
    }

    const rows = await db
      .select({
        id: conversations.id,
        type: conversations.type,
        rideRequestId: conversations.rideRequestId,
        createdAt: conversations.createdAt,
        updatedAt: conversations.updatedAt,
        deletedAt: conversations.deletedAt,
      })
      .from(conversations)
      .where(
        and(
          inArray(conversations.id, convIds),
          isNull(conversations.deletedAt),
        ),
      )
      .orderBy(desc(conversations.updatedAt))
      .limit(limit + 1);

    const hasMore = rows.length > limit;
    const pageRows = rows.slice(0, limit);

    const enriched: ConversationRow[] = [];
    for (const row of pageRows) {
      const [pcount] = await db
        .select({ count: count() })
        .from(conversationParticipants)
        .where(eq(conversationParticipants.conversationId, row.id));
      enriched.push({
        ...row,
        participantCount: Number(pcount?.count ?? 0),
        lastMessageAt: null,
        lastMessagePreview: null,
      });
    }

    return res.json({
      conversations: enriched.map((c) => ({
        id: c.id,
        type: c.type,
        rideRequestId: c.rideRequestId,
        participantCount: c.participantCount,
        lastMessageAt: c.lastMessageAt?.getTime() ?? null,
        lastMessagePreview: c.lastMessagePreview,
        createdAt: c.createdAt.getTime(),
        updatedAt: c.updatedAt.getTime(),
      })),
      page,
      limit,
      hasMore,
    });
  } catch (error) {
    return next(error);
  }
});

router.get("/:id", async (req, res, next) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });
  try {
    const conversationId = String(req.params.id ?? "");
    if (!conversationId) return res.status(400).json({ error: "A conversation ID is required." });
    if (!(await requireParticipant(conversationId, userId))) {
      return res.status(403).json({ error: "You are not a participant of this conversation." });
    }

    const rows = await db
      .select({
        id: conversations.id,
        type: conversations.type,
        rideRequestId: conversations.rideRequestId,
        createdAt: conversations.createdAt,
        updatedAt: conversations.updatedAt,
        deletedAt: conversations.deletedAt,
      })
      .from(conversations)
      .where(eq(conversations.id, conversationId))
      .limit(1);
    const conv = rows[0];
    if (!conv || conv.deletedAt) {
      return res.status(404).json({ error: "Conversation not found." });
    }

    const participants = await db
      .select({
        userId: conversationParticipants.userId,
        role: conversationParticipants.role,
        joinedAt: conversationParticipants.joinedAt,
        lastReadAt: conversationParticipants.lastReadAt,
      })
      .from(conversationParticipants)
      .where(eq(conversationParticipants.conversationId, conversationId));

    return res.json({
      conversation: {
        id: conv.id,
        type: conv.type,
        rideRequestId: conv.rideRequestId,
        createdAt: conv.createdAt.getTime(),
        updatedAt: conv.updatedAt.getTime(),
      },
      participants: participants.map((p) => ({
        userId: p.userId,
        role: p.role,
        joinedAt: p.joinedAt.getTime(),
        lastReadAt: p.lastReadAt?.getTime() ?? null,
      })),
    });
  } catch (error) {
    return next(error);
  }
});

export default router;


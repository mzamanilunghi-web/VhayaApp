import { randomBytes } from "node:crypto";
import { and, eq, lte, sql } from "drizzle-orm";
import { Router, type IRouter, type Response } from "express";
import { authSessions, db } from "@workspace/db";
import { getWayfareUserId } from "../middlewares/firebaseIdentity";
import { hashAccountSessionToken } from "../lib/accountSession";

const router: IRouter = Router();
const SESSION_TTL_MS = 365 * 24 * 60 * 60 * 1_000;
const SESSION_RENEW_WINDOW_MS = 30 * 24 * 60 * 60 * 1_000;

function sessionConflict(res: Response) {
  return res.status(409).json({
    code: "SESSION_REPLACED",
    error: "This account was signed in on another device.",
  });
}

router.post("/auth/session/claim", async (req, res, next) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });

  try {
    const sessionId = randomBytes(32).toString("base64url");
    const tokenHash = hashAccountSessionToken(sessionId);
    const expiresAt = new Date(Date.now() + SESSION_TTL_MS);

    await db.transaction(async (tx) => {
      await tx.execute(
        sql`SELECT pg_advisory_xact_lock(hashtextextended(${userId}, 0))`,
      );
      await tx.delete(authSessions).where(eq(authSessions.userId, userId));
      await tx.insert(authSessions).values({ tokenHash, userId, expiresAt });
    });

    return res.json({ sessionId });
  } catch (error) {
    return next(error);
  }
});

router.get("/auth/session", async (req, res, next) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });

  const sessionId = req.get("x-wayfare-session");
  if (
    !sessionId ||
    sessionId.length > 128 ||
    !/^[A-Za-z0-9_-]+$/.test(sessionId)
  ) {
    return sessionConflict(res);
  }

  try {
    const tokenHash = hashAccountSessionToken(sessionId);
    const renewBefore = new Date(Date.now() + SESSION_RENEW_WINDOW_MS);
    const expiresAt = new Date(Date.now() + SESSION_TTL_MS);
    const renewed = await db
      .update(authSessions)
      .set({ expiresAt })
      .where(
        and(
          eq(authSessions.tokenHash, tokenHash),
          eq(authSessions.userId, userId),
          lte(authSessions.expiresAt, renewBefore),
        ),
      )
      .returning({ tokenHash: authSessions.tokenHash });

    if (renewed.length > 0) return res.json({ active: true });

    const [current] = await db
      .select({ tokenHash: authSessions.tokenHash })
      .from(authSessions)
      .where(
        and(
          eq(authSessions.tokenHash, tokenHash),
          eq(authSessions.userId, userId),
        ),
      )
      .limit(1);

    if (!current) return sessionConflict(res);
    return res.json({ active: true });
  } catch (error) {
    return next(error);
  }
});

router.delete("/auth/session", async (req, res, next) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });

  const sessionId = req.get("x-wayfare-session");
  if (
    !sessionId ||
    sessionId.length > 128 ||
    !/^[A-Za-z0-9_-]+$/.test(sessionId)
  ) {
    return sessionConflict(res);
  }

  try {
    const result = await db
      .update(authSessions)
      .set({ expiresAt: new Date() })
      .where(
        and(
          eq(authSessions.tokenHash, hashAccountSessionToken(sessionId)),
          eq(authSessions.userId, userId),
        ),
      )
      .returning({ tokenHash: authSessions.tokenHash });

    if (result.length === 0) return sessionConflict(res);
    return res.json({ signedOut: true });
  } catch (error) {
    return next(error);
  }
});

export default router;

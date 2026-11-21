import type { Request, RequestHandler } from "express";
import { authSessions, db, wayfareUsers } from "@workspace/db";
import { desc, eq } from "drizzle-orm";
import { logger } from "../lib/logger";
import { hashAccountSessionToken } from "../lib/accountSession";
import {
  ensureFirebaseWayfareProfile,
  getFirebaseWayfareProfile,
  verifyFirebaseIdToken,
} from "../lib/firebaseAdmin";

type WayfareIdentityRequest = Request & {
  wayfareUserId?: string;
};

const USER_SYNC_CACHE_MS = 30_000;
const userSyncCache = new Map<string, number>();

function bearerToken(req: Request): string | null {
  const authorization = req.headers.authorization;
  if (!authorization?.startsWith("Bearer ")) return null;
  return authorization.slice("Bearer ".length).trim() || null;
}

export function getWayfareUserId(req: Request): string | null {
  return (req as WayfareIdentityRequest).wayfareUserId ?? null;
}

export const attachFirebaseIdentity: RequestHandler = (req, res, next) => {
  if (
    req.path === "/healthz" ||
    req.path === "/payments/callback" ||
    req.path === "/payments/webhook"
  ) {
    next();
    return;
  }

  const token = bearerToken(req);
  if (!token) {
    res.status(401).json({ error: "Sign in to continue." });
    return;
  }

  void (async () => {
    let decoded;
    try {
      decoded = await verifyFirebaseIdToken(token);
    } catch {
      res.status(401).json({ error: "Your sign-in has expired. Sign in again." });
      return;
    }

    if (decoded.email_verified !== true || !decoded.email) {
      res.status(403).json({ error: "Verify your email before using Vhaya." });
      return;
    }

    let failureStage = "firebase_profile_read";
    try {
      const existingProfile = await getFirebaseWayfareProfile(decoded.uid);
      failureStage = "postgres_account_status_check";
      const existingRows = await db
        .select({ id: wayfareUsers.id, deletedAt: wayfareUsers.deletedAt })
        .from(wayfareUsers)
        .where(eq(wayfareUsers.id, decoded.uid))
        .limit(1);
      const existingRow = existingRows[0];
      if (existingRow?.deletedAt) {
        res.status(410).json({
          code: "ACCOUNT_DELETED",
          error: "This account has been deleted.",
        });
        return;
      }
      if (!existingProfile && existingRow) {
        res.status(409).json({
          error:
            "This account has existing Vhaya records and needs a reviewed identity link.",
        });
        return;
      }

      const verifiedEmail = decoded.email.trim().toLowerCase();
      const verifiedName =
        (typeof decoded.name === "string" ? decoded.name.trim() : "") ||
        verifiedEmail.split("@")[0] ||
        "Vhaya rider";
      failureStage = "firebase_profile_ensure";
      const profile =
        existingProfile &&
        existingProfile.email === verifiedEmail &&
        existingProfile.displayName === verifiedName
          ? existingProfile
          : await ensureFirebaseWayfareProfile(decoded);

      const lastSyncedAt = userSyncCache.get(profile.uid) ?? 0;
      if (lastSyncedAt <= Date.now()) {
        failureStage = "postgres_user_sync";
        await db
          .insert(wayfareUsers)
          .values({
            id: profile.uid,
            displayName: profile.displayName,
            email: profile.email,
            role: profile.role,
            devVerified: true,
            updatedAt: new Date(),
          })
          .onConflictDoUpdate({
            target: wayfareUsers.id,
            set: {
              displayName: profile.displayName,
              email: profile.email,
              role: profile.role,
              devVerified: true,
              updatedAt: new Date(),
            },
          });
        userSyncCache.set(profile.uid, Date.now() + USER_SYNC_CACHE_MS);
        if (userSyncCache.size > 5_000) {
          const now = Date.now();
          for (const [uid, syncedAt] of userSyncCache) {
            if (syncedAt <= now) userSyncCache.delete(uid);
          }
        }
      }

      if (req.path !== "/auth/session/claim") {
        const [activeSession] = await db
          .select({
            tokenHash: authSessions.tokenHash,
            expiresAt: authSessions.expiresAt,
          })
          .from(authSessions)
          .where(eq(authSessions.userId, profile.uid))
          .orderBy(desc(authSessions.createdAt))
          .limit(1);

        if (activeSession || req.path === "/auth/session") {
          const sessionId = req.get("x-wayfare-session");
          const validSessionId =
            typeof sessionId === "string" &&
            sessionId.length <= 128 &&
            /^[A-Za-z0-9_-]+$/.test(sessionId);
          const matchesActiveSession =
            validSessionId &&
            activeSession &&
            activeSession.expiresAt.getTime() > Date.now() &&
            activeSession.tokenHash === hashAccountSessionToken(sessionId);

          if (!matchesActiveSession) {
            res.status(409).json({
              code: "SESSION_REPLACED",
              error: "This account was signed in on another device.",
            });
            return;
          }
        }
      }

      (req as WayfareIdentityRequest).wayfareUserId = profile.uid;
      next();
    } catch (error) {
      const errorCode =
        error &&
        typeof error === "object" &&
        "code" in error &&
        /^[A-Za-z0-9/_-]{1,64}$/.test(String(error.code))
          ? String(error.code)
          : undefined;
      logger.error(
        {
          stage: failureStage,
          errorType: error instanceof Error ? error.name : "UnknownError",
          errorCode,
        },
        "Firebase-authenticated API request failed",
      );
      res.status(503).json({
        error: "Vhaya identity data could not be loaded. Try again shortly.",
      });
    }
  })();
};

import { randomUUID } from "node:crypto";
import { Router, type IRouter } from "express";
import { db } from "@workspace/db";
import { pushTokens } from "@workspace/db/schema";
import { eq } from "drizzle-orm";
import { recordBody } from "../lib/wayfareData";
import { getWayfareUserId } from "../middlewares/firebaseIdentity";
import { getWayfareRole } from "../middlewares/wayfareRoles";

const router: IRouter = Router();
const validPlatforms = new Set(["ios", "android", "web"]);

function statusError(error: unknown): number {
  return error && typeof error === "object" && "status" in error
    ? Number((error as { status: unknown }).status)
    : 0;
}

router.post("/", async (req, res, next) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });
  try {
    const role = await getWayfareRole(userId);
    const body = recordBody(req.body);
    const token = typeof body.token === "string" ? body.token.trim() : "";
    const platform =
      typeof body.platform === "string" ? body.platform.trim() : "";
    const appVersion =
      typeof body.appVersion === "string" ? body.appVersion.trim() : undefined;
    const locale =
      typeof body.locale === "string" ? body.locale.trim() : undefined;
    const optIn = body.optIn === undefined ? true : Boolean(body.optIn);

    if (!token || token.length < 10) {
      return res.status(400).json({ error: "A valid push token is required." });
    }
    if (!platform || !validPlatforms.has(platform)) {
      return res.status(400).json({
        error: "Platform must be one of: ios, android, web.",
      });
    }

    // Upsert by token: update the existing record or insert a new one.
    const existing = await db
      .select({ id: pushTokens.id })
      .from(pushTokens)
      .where(eq(pushTokens.token, token))
      .limit(1);

    const now = new Date();
    if (existing[0]) {
      await db
        .update(pushTokens)
        .set({
          userId,
          platform,
          appVersion,
          locale,
          optIn,
          invalidatedAt: null,
          updatedAt: now,
        })
        .where(eq(pushTokens.id, existing[0].id));
      return res.json({
        id: existing[0].id,
        token,
        platform,
        appVersion,
        locale,
        optIn,
        registered: true,
      });
    }

    const id = `push-${randomUUID()}`;
    await db.insert(pushTokens).values({
      id,
      userId,
      token,
      platform,
      appVersion,
      locale,
      optIn,
      createdAt: now,
      updatedAt: now,
    });
    return res.status(201).json({
      id,
      token,
      platform,
      appVersion,
      locale,
      optIn,
      registered: true,
    });
  } catch (error) {
    const status = statusError(error);
    if (status >= 400 && status < 600) {
      return res.status(status).json({
        error: error instanceof Error ? error.message : "Push token registration failed.",
      });
    }
    return next(error);
  }
});

router.get("/", async (req, res, next) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });
  try {
    const rows = await db
      .select({
        id: pushTokens.id,
        token: pushTokens.token,
        platform: pushTokens.platform,
        appVersion: pushTokens.appVersion,
        locale: pushTokens.locale,
        optIn: pushTokens.optIn,
        invalidatedAt: pushTokens.invalidatedAt,
        verifiedAt: pushTokens.verifiedAt,
        createdAt: pushTokens.createdAt,
        updatedAt: pushTokens.updatedAt,
      })
      .from(pushTokens)
      .where(eq(pushTokens.userId, userId))
      .orderBy(pushTokens.updatedAt);
    return res.json({
      tokens: rows.map((r) => ({
        ...r,
        createdAt: r.createdAt.getTime(),
        updatedAt: r.updatedAt.getTime(),
        invalidatedAt: r.invalidatedAt?.getTime() ?? null,
        verifiedAt: r.verifiedAt?.getTime() ?? null,
      })),
    });
  } catch (error) {
    return next(error);
  }
});

router.delete("/:id", async (req, res, next) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });
  try {
    const id = String(req.params.id ?? "");
    if (!id) return res.status(400).json({ error: "A token ID is required." });
    const existing = await db
      .select({ id: pushTokens.id, userId: pushTokens.userId })
      .from(pushTokens)
      .where(eq(pushTokens.id, id))
      .limit(1);
    if (!existing[0]) {
      return res.status(404).json({ error: "Push token not found." });
    }
    if (existing[0].userId !== userId) {
      return res.status(403).json({ error: "You can only invalidate your own tokens." });
    }
    await db
      .update(pushTokens)
      .set({ invalidatedAt: new Date(), updatedAt: new Date() })
      .where(eq(pushTokens.id, id));
    return res.json({ invalidated: true, id });
  } catch (error) {
    return next(error);
  }
});

export default router;

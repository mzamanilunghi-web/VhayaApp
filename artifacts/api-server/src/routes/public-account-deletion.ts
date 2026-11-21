import { randomUUID } from "node:crypto";
import { and, eq, isNull, sql } from "drizzle-orm";
import { Router, type IRouter } from "express";
import {
  SubmitAccountDeletionRequestBody,
  SubmitAccountDeletionRequestResponse,
  GetAccountDeletionRequestStatusParams,
  GetAccountDeletionRequestStatusResponse,
} from "@workspace/api-zod";
import { db } from "@workspace/db";
import { accountDeletionRequests, wayfareUsers } from "@workspace/db/schema";
import { accountDeletionPage } from "../lib/accountDeletionPage";
import { deletionStatusMessage } from "../lib/accountDeletion";

const router: IRouter = Router();
const PUBLIC_REQUEST_LIMIT = 5;
const PUBLIC_REQUEST_WINDOW_MS = 60 * 60 * 1_000;
const requestBuckets = new Map<string, { count: number; resetAt: number }>();

function allowRequest(ip: string): boolean {
  const now = Date.now();
  const current = requestBuckets.get(ip);
  if (!current || current.resetAt <= now) {
    requestBuckets.set(ip, { count: 1, resetAt: now + PUBLIC_REQUEST_WINDOW_MS });
    if (requestBuckets.size > 10_000) {
      for (const [key, bucket] of requestBuckets) {
        if (bucket.resetAt <= now) requestBuckets.delete(key);
      }
    }
    return true;
  }
  if (current.count >= PUBLIC_REQUEST_LIMIT) return false;
  current.count += 1;
  return true;
}

router.get("/account-deletion", (_req, res): void => {
  res.setHeader("Cache-Control", "no-store");
  res.type("html").send(accountDeletionPage);
});

router.post("/account-deletion/requests", async (req, res, next): Promise<void> => {
  const ip = req.ip || req.socket.remoteAddress || "unknown";
  if (!allowRequest(ip)) {
    res.setHeader("Retry-After", "3600");
    res.status(429).json({ error: "Too many requests. Try again later." });
    return;
  }

  const body =
    req.body && typeof req.body === "object" && !Array.isArray(req.body)
      ? { ...req.body, email: typeof req.body.email === "string" ? req.body.email.trim() : req.body.email }
      : req.body;
  const parsed = SubmitAccountDeletionRequestBody.safeParse(body);
  if (!parsed.success) {
    res.status(400).json({ error: "Enter the email used for your Wayfare account." });
    return;
  }

  try {
    const requestId = randomUUID();
    const normalizedEmail = parsed.data.email.trim().toLowerCase();
    const matches = await db
      .select({ id: wayfareUsers.id })
      .from(wayfareUsers)
      .where(
        and(
          eq(sql`lower(${wayfareUsers.email})`, normalizedEmail),
          isNull(wayfareUsers.deletedAt),
        ),
      )
      .limit(1);

    if (matches[0]) {
      await db.insert(accountDeletionRequests).values({
        id: requestId,
        userId: matches[0].id,
        status: "requested",
      });
    }

    res.status(202).json(
      SubmitAccountDeletionRequestResponse.parse({
        requestId,
        status: "received",
        message:
          "If an account matches this email, the request is queued for ownership verification.",
      }),
    );
  } catch (error) {
    next(error);
  }
});

router.get(
  "/account-deletion/requests/:requestId",
  async (req, res, next): Promise<void> => {
    const parsedParams = GetAccountDeletionRequestStatusParams.safeParse(req.params);
    if (!parsedParams.success) {
      res.status(400).json({ error: "The request reference is invalid." });
      return;
    }

    try {
      const [request] = await db
        .select({
          id: accountDeletionRequests.id,
          status: accountDeletionRequests.status,
          requestedAt: accountDeletionRequests.requestedAt,
        })
        .from(accountDeletionRequests)
        .where(eq(accountDeletionRequests.id, parsedParams.data.requestId))
        .limit(1);
      const status = deletionStatusMessage(request?.status ?? "requested");
      res.json(
        GetAccountDeletionRequestStatusResponse.parse({
          requestId: parsedParams.data.requestId,
          status: status.status,
          requestedAt: request?.requestedAt?.toISOString() ?? null,
          message: status.message,
        }),
      );
    } catch (error) {
      next(error);
    }
  },
);

export default router;

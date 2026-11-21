import {
  DeleteOwnAccountBody,
  DeleteOwnAccountResponse,
} from "@workspace/api-zod";
import { Router, type IRouter, type NextFunction, type Response } from "express";
import { and, eq, isNull } from "drizzle-orm";
import { db } from "@workspace/db";
import { accountDeletionRequests, wayfareUsers } from "@workspace/db/schema";
import { getWayfareUserId } from "../middlewares/firebaseIdentity";
import {
  completeAccountDeletion,
  createSelfDeletionRequest,
} from "../lib/accountDeletion";

const router: IRouter = Router();

function sendRouteError(error: unknown, res: Response, next: NextFunction): void {
  const status =
    error && typeof error === "object" && "status" in error
      ? Number((error as { status: unknown }).status)
      : 0;
  if (status >= 400 && status < 600) {
    res.status(status).json({
      error: error instanceof Error ? error.message : "The account could not be deleted.",
    });
    return;
  }
  next(error);
}

router.delete("/account", async (req, res, next): Promise<void> => {
  const userId = getWayfareUserId(req);
  if (!userId) {
    res.status(401).json({ error: "Sign in to continue." });
    return;
  }

  const parsed = DeleteOwnAccountBody.safeParse(req.body);
  if (!parsed.success || parsed.data.confirmation !== "DELETE") {
    res.status(400).json({ error: 'Type the confirmation value "DELETE" to continue.' });
    return;
  }

  try {
    const [account] = await db
      .select({ role: wayfareUsers.role })
      .from(wayfareUsers)
      .where(and(eq(wayfareUsers.id, userId), isNull(wayfareUsers.deletedAt)))
      .limit(1);

    if (!account) {
      res.status(410).json({ error: "This account has already been deleted." });
      return;
    }
    if (account.role !== "rider" && account.role !== "driver") {
      res.status(403).json({ error: "Only rider and driver accounts can be deleted here." });
      return;
    }

    const requestId = await createSelfDeletionRequest(userId, parsed.data.reason);
    try {
      const result = await completeAccountDeletion(requestId, {
        actorUserId: null,
        externalSubscriptionsCancelled: false,
      });
      const response = DeleteOwnAccountResponse.parse(result);
      res.status(response.deleted ? 200 : 202).json(response);
    } catch (error) {
      const [request] = await db
        .select({
          status: accountDeletionRequests.status,
          retainedUserId: accountDeletionRequests.retainedUserId,
        })
        .from(accountDeletionRequests)
        .where(eq(accountDeletionRequests.id, requestId))
        .limit(1);
      if (request?.status === "requested" && !request.retainedUserId) {
        await db
          .delete(accountDeletionRequests)
          .where(eq(accountDeletionRequests.id, requestId));
      }
      throw error;
    }
  } catch (error) {
    sendRouteError(error, res, next);
  }
});

export default router;

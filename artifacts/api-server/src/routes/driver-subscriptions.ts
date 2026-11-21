import { randomUUID } from "node:crypto";
import { and, desc, eq, inArray, sql } from "drizzle-orm";
import { Router, type IRouter, type NextFunction, type Request, type Response } from "express";
import { db } from "@workspace/db";
import {
  auditEvents,
  driverPromotionRedemptions,
  driverPromotions,
  driverProfiles,
  driverRatingRecords,
  driverSubscriptionPlans,
  driverSubscriptionRecords,
  driverSubscriptions,
  rideRequests,
} from "@workspace/db/schema";
import { getWayfareUserId } from "../middlewares/firebaseIdentity";
import { getWayfareRole } from "../middlewares/wayfareRoles";
import {
  calculatePromotionDiscount,
  effectiveSubscriptionStatus,
  evaluatePromotionEligibility,
} from "../lib/driverSubscriptionRules";
import { disablePaystackSubscription } from "../lib/paystackSubscriptions";

const router: IRouter = Router();

function sendRouteError(error: unknown, res: Response, next: NextFunction): void {
  const status =
    error && typeof error === "object" && "status" in error
      ? Number((error as { status: unknown }).status)
      : 0;
  if (status >= 400 && status < 600) {
    res.status(status).json({
      error: error instanceof Error ? error.message : "The subscription request could not be completed.",
    });
    return;
  }
  next(error);
}

async function requireDriver(req: Request, res: Response): Promise<string | null> {
  const userId = getWayfareUserId(req);
  if (!userId) {
    res.status(401).json({ error: "Sign in to continue." });
    return null;
  }
  if ((await getWayfareRole(userId)) !== "driver") {
    res.status(403).json({ error: "Only drivers can manage driver subscriptions." });
    return null;
  }
  return userId;
}

function millis(value: Date | null | undefined): number | null {
  return value ? value.getTime() : null;
}

function view(
  row: typeof driverSubscriptions.$inferSelect,
  planName: string | null,
) {
  return {
    driverId: row.driverId,
    planId: row.planId,
    planName,
    status: effectiveSubscriptionStatus(row.status, row.expiresAt),
    renewalMode: row.renewalMode,
    autoRenewActive: row.autoRenewActive,
    autoRenewIssue: row.autoRenewIssue,
    cancelAtPeriodEnd: row.cancelAtPeriodEnd,
    amountMinor: row.amountMinor,
    currency: row.currency,
    provider: row.provider,
    startedAt: millis(row.startedAt),
    expiresAt: millis(row.expiresAt),
    nextPaymentAt: millis(row.nextPaymentAt),
    autoRenewConsentAt: millis(row.autoRenewConsentAt),
  };
}

async function loadDriverFacts(driverId: string) {
  const [[rideCount], [profile], [rating]] = await Promise.all([
    db
      .select({ count: sql<number>`count(*)::int` })
      .from(rideRequests)
      .where(and(eq(rideRequests.driverId, driverId), eq(rideRequests.status, "completed"))),
    db
      .select({ city: driverProfiles.city })
      .from(driverProfiles)
      .where(eq(driverProfiles.userId, driverId))
      .limit(1),
    db
      .select()
      .from(driverRatingRecords)
      .where(eq(driverRatingRecords.driverId, driverId))
      .limit(1),
  ]);
  return {
    completedRideCount: rideCount?.count ?? 0,
    city: profile?.city ?? null,
    averageRating: rating?.averageRating ?? null,
    ratingCount: rating?.ratingCount ?? 0,
    rating: {
      averageRating: rating?.averageRating ?? null,
      ratingCount: rating?.ratingCount ?? 0,
      source: rating?.source ?? "verified_rider_ratings",
    },
  };
}

async function promotionOffers(
  driverId: string,
  plan: typeof driverSubscriptionPlans.$inferSelect,
  facts: Awaited<ReturnType<typeof loadDriverFacts>>,
) {
  const promos = await db
    .select()
    .from(driverPromotions)
    .where(eq(driverPromotions.isActive, true))
    .orderBy(desc(driverPromotions.createdAt));
  const promoIds = promos.map((promo) => promo.id);
  const redemptions = promoIds.length
    ? await db
        .select({
          promotionId: driverPromotionRedemptions.promotionId,
          driverId: driverPromotionRedemptions.driverId,
          status: driverPromotionRedemptions.status,
        })
        .from(driverPromotionRedemptions)
        .where(inArray(driverPromotionRedemptions.promotionId, promoIds))
    : [];
  const countByPromotion = new Map<string, number>();
  const redeemedByDriver = new Set<string>();
  for (const redemption of redemptions) {
    if (redemption.status === "pending" || redemption.status === "redeemed") {
      countByPromotion.set(
        redemption.promotionId,
        (countByPromotion.get(redemption.promotionId) ?? 0) + 1,
      );
    }
    if (
      redemption.driverId === driverId &&
      (redemption.status === "pending" || redemption.status === "redeemed")
    ) {
      redeemedByDriver.add(redemption.promotionId);
    }
  }
  return promos
    .filter((promo) => !promo.planId || promo.planId === plan.id)
    .map((promo) => {
      const eligibility = evaluatePromotionEligibility(
        {
          ...promo,
          alreadyRedeemed: redeemedByDriver.has(promo.id),
        },
        facts,
        {
          planId: plan.id,
          activeRedemptions: countByPromotion.get(promo.id) ?? 0,
        },
      );
      const discountMinor = eligibility.eligible
        ? calculatePromotionDiscount(plan.amountMinor, {
            discountType: promo.discountType,
            discountValue: promo.discountValue,
            maxDiscountMinor: promo.maxDiscountMinor,
          })
        : 0;
      return {
        id: promo.id,
        code: promo.code,
        name: promo.name,
        discountType: promo.discountType,
        discountValue: promo.discountValue,
        discountMinor,
        discountedAmountMinor: plan.amountMinor - discountMinor,
        currency: promo.currency,
        eligible: eligibility.eligible,
        reasons: eligibility.reasons,
      };
    });
}

router.get("/driver/subscription/offers", async (req, res, next) => {
  try {
    const driverId = await requireDriver(req, res);
    if (!driverId) return;
    const [plans, facts] = await Promise.all([
      db
        .select()
        .from(driverSubscriptionPlans)
        .where(eq(driverSubscriptionPlans.isActive, true))
        .orderBy(driverSubscriptionPlans.amountMinor, driverSubscriptionPlans.name),
      loadDriverFacts(driverId),
    ]);
    const offers = await Promise.all(
      plans.map(async (plan) => ({
        plan: {
          id: plan.id,
          name: plan.name,
          description: plan.description,
          amountMinor: plan.amountMinor,
          currency: plan.currency,
          billingInterval: plan.billingInterval,
          renewalMode: plan.renewalMode,
          isActive: plan.isActive,
        },
        promotions: await promotionOffers(driverId, plan, facts),
      })),
    );
    res.json({
      plans: offers,
      rating: facts.rating,
      completedRideCount: facts.completedRideCount,
      serviceArea: facts.city,
    });
  } catch (error) {
    sendRouteError(error, res, next);
  }
});

router.get("/driver/subscription", async (req, res, next) => {
  try {
    const driverId = await requireDriver(req, res);
    if (!driverId) return;
    const [current] = await db
      .select()
      .from(driverSubscriptions)
      .where(eq(driverSubscriptions.driverId, driverId))
      .limit(1);
    if (current) {
      const status = effectiveSubscriptionStatus(
        current.status,
        current.expiresAt,
      );
      if (status !== current.status) {
        await db
          .update(driverSubscriptions)
          .set({ status, updatedAt: new Date() })
          .where(eq(driverSubscriptions.driverId, driverId));
      }
      const [plan] = current.planId
        ? await db
            .select({ name: driverSubscriptionPlans.name })
            .from(driverSubscriptionPlans)
            .where(eq(driverSubscriptionPlans.id, current.planId))
            .limit(1)
        : [];
      res.json({
        subscription: view(
          { ...current, status },
          plan?.name ?? null,
        ),
      });
      return;
    }

    // Read legacy payment-ledger subscriptions until their existing term expires.
    const [legacy] = await db
      .select()
      .from(driverSubscriptionRecords)
      .where(eq(driverSubscriptionRecords.driverId, driverId))
      .orderBy(desc(driverSubscriptionRecords.startedAt))
      .limit(1);
    if (!legacy) {
      res.json({ subscription: null });
      return;
    }
    const status =
      legacy.status === "active" && !legacy.expiresAt
        ? "expired"
        : effectiveSubscriptionStatus(legacy.status, legacy.expiresAt);
    res.json({
      subscription: {
        driverId,
        planId: null,
        planName: null,
        status,
        renewalMode: "manual",
        autoRenewActive: false,
        autoRenewIssue: null,
        cancelAtPeriodEnd: false,
        amountMinor: legacy.amountMinor,
        currency: legacy.currency,
        provider: legacy.provider,
        startedAt: legacy.startedAt.getTime(),
        expiresAt: millis(legacy.expiresAt),
        nextPaymentAt: null,
        autoRenewConsentAt: null,
      },
    });
  } catch (error) {
    sendRouteError(error, res, next);
  }
});

router.post("/driver/subscription/cancel", async (req, res, next) => {
  try {
    const driverId = await requireDriver(req, res);
    if (!driverId) return;
    const [current] = await db
      .select()
      .from(driverSubscriptions)
      .where(eq(driverSubscriptions.driverId, driverId))
      .limit(1);
    if (!current) {
      res.status(404).json({ error: "No current subscription was found." });
      return;
    }
    if (current.autoRenewActive && current.provider === "paystack") {
      if (!current.providerSubscriptionCode || !current.providerEmailToken) {
        res.status(409).json({
          error: "Paystack cancellation details are unavailable; no changes were saved.",
        });
        return;
      }
      try {
        await disablePaystackSubscription(
          current.providerSubscriptionCode,
          current.providerEmailToken,
        );
      } catch (error) {
        res.status(502).json({
          error:
            error instanceof Error
              ? `Paystack could not confirm cancellation: ${error.message}`
              : "Paystack could not confirm cancellation.",
        });
        return;
      }
    }
    const now = new Date();
    const updated = await db.transaction(async (tx) => {
      const [locked] = await tx
        .select()
        .from(driverSubscriptions)
        .where(eq(driverSubscriptions.driverId, driverId))
        .for("update");
      if (!locked || locked.updatedAt.getTime() !== current.updatedAt.getTime()) {
        throw Object.assign(
          new Error("The subscription changed during cancellation. Refresh and try again."),
          { status: 409 },
        );
      }
      const [saved] = await tx
        .update(driverSubscriptions)
        .set({
          status: locked.status === "pending" ? "cancelled" : locked.status,
          renewalMode: "manual",
          autoRenewActive: false,
          autoRenewIssue: null,
          cancelAtPeriodEnd: locked.status === "active",
          providerEmailToken: null,
          cancelledAt: now,
          cancelledReason: "Driver requested no further automatic renewals.",
          updatedAt: now,
        })
        .where(eq(driverSubscriptions.driverId, driverId))
        .returning();
      if (locked.status === "pending") {
        const pending = await tx
          .select({ externalReference: driverSubscriptionRecords.externalReference })
          .from(driverSubscriptionRecords)
          .where(
            and(
              eq(driverSubscriptionRecords.driverId, driverId),
              eq(driverSubscriptionRecords.status, "pending"),
            ),
          );
        if (pending.length) {
          const references = pending.map((row) => row.externalReference);
          await tx
            .update(driverSubscriptionRecords)
            .set({ status: "cancelled" })
            .where(inArray(driverSubscriptionRecords.externalReference, references));
          await tx
            .update(driverPromotionRedemptions)
            .set({ status: "released" })
            .where(inArray(driverPromotionRedemptions.externalReference, references));
        }
      }
      return saved;
    });
    if (!updated) {
      res.status(409).json({ error: "The subscription changed during cancellation." });
      return;
    }
    await db.insert(auditEvents).values({
      id: randomUUID(),
      actorUserId: driverId,
      entityType: "driver_subscription",
      entityId: driverId,
      action: "driver_subscription_renewal_cancelled",
      previousState: {
        renewalMode: current.renewalMode,
        autoRenewActive: current.autoRenewActive,
        expiresAt: current.expiresAt?.toISOString() ?? null,
      },
      nextState: {
        renewalMode: updated.renewalMode,
        autoRenewActive: updated.autoRenewActive,
        cancelAtPeriodEnd: updated.cancelAtPeriodEnd,
      },
      reason: null,
    });
    const [plan] = updated.planId
      ? await db
          .select({ name: driverSubscriptionPlans.name })
          .from(driverSubscriptionPlans)
          .where(eq(driverSubscriptionPlans.id, updated.planId))
          .limit(1)
      : [];
    res.json({ subscription: view(updated, plan?.name ?? null) });
  } catch (error) {
    sendRouteError(error, res, next);
  }
});

export default router;

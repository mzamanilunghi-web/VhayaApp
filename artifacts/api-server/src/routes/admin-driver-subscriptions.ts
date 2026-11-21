import { randomUUID } from "node:crypto";
import {
  and,
  asc,
  desc,
  eq,
  gt,
  ilike,
  inArray,
  isNull,
  or,
  sql,
} from "drizzle-orm";
import { Router, type IRouter, type NextFunction, type Request, type Response } from "express";
import { db } from "@workspace/db";
import {
  driverPromotionRedemptions,
  driverPromotions,
  driverRatingRecords,
  driverSubscriptionPlans,
  driverSubscriptionRecords,
  driverSubscriptions,
  rideRequests,
  wayfareUsers,
} from "@workspace/db/schema";
import {
  CreateAdminDriverPromotionBody,
  CreateAdminDriverSubscriptionPlanBody,
  GetAdminDriverSubscriptionsQueryParams,
  UpdateAdminDriverPromotionBody,
  UpdateAdminDriverPromotionParams,
  UpdateAdminDriverSubscriptionBody,
  UpdateAdminDriverSubscriptionParams,
  UpdateAdminDriverSubscriptionPlanBody,
  UpdateAdminDriverSubscriptionPlanParams,
} from "@workspace/api-zod";
import { getWayfareUserId } from "../middlewares/firebaseIdentity";
import { getWayfareRole } from "../middlewares/wayfareRoles";
import {
  effectiveSubscriptionStatus,
  providerCancellationRequired,
} from "../lib/driverSubscriptionRules";
import { disablePaystackSubscription } from "../lib/paystackSubscriptions";

const router: IRouter = Router();
const pageLimit = 50;
const maxPage = 1000;

function routeError(status: number, message: string): Error & { status: number } {
  return Object.assign(new Error(message), { status });
}

function sendRouteError(error: unknown, res: Response, next: NextFunction): void {
  const status =
    error && typeof error === "object" && "status" in error
      ? Number((error as { status: unknown }).status)
      : 0;
  if (status >= 400 && status < 600) {
    res.status(status).json({
      error: error instanceof Error ? error.message : "The admin request could not be completed.",
    });
    return;
  }
  next(error);
}

async function requireAdmin(req: Request, res: Response): Promise<string | null> {
  const userId = getWayfareUserId(req);
  if (!userId) {
    res.status(401).json({ error: "Sign in to continue." });
    return null;
  }
  if ((await getWayfareRole(userId)) !== "admin") {
    res.status(403).json({ error: "Operations admin access is required." });
    return null;
  }
  return userId;
}

function millis(value: Date | null | undefined): number | null {
  return value ? value.getTime() : null;
}

function subscriptionView(
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

async function audit(
  actorId: string,
  entityType: string,
  entityId: string,
  action: string,
  previousState: Record<string, unknown> | null,
  nextState: Record<string, unknown>,
  reason?: string,
) {
  const { auditEvents } = await import("@workspace/db/schema");
  await db.insert(auditEvents).values({
    id: randomUUID(),
    actorUserId: actorId,
    entityType,
    entityId,
    action,
    previousState,
    nextState,
    reason: reason ?? null,
  });
}

function promotionView(
  row: typeof driverPromotions.$inferSelect,
  redemptionCount: number,
) {
  return {
    id: row.id,
    code: row.code,
    name: row.name,
    discountType: row.discountType,
    discountValue: row.discountValue,
    maxDiscountMinor: row.maxDiscountMinor,
    currency: row.currency,
    planId: row.planId,
    startsAt: row.startsAt.getTime(),
    endsAt: row.endsAt.getTime(),
    serviceArea: row.serviceArea,
    maxRedemptions: row.maxRedemptions,
    newDriverOnly: row.newDriverOnly,
    minimumCompletedRides: row.minimumCompletedRides,
    minimumRating: row.minimumRating,
    isActive: row.isActive,
    redemptionCount,
  };
}

function validatePromotionDates(
  startsAt: Date,
  endsAt: Date,
  discountType: "fixed" | "percentage",
  discountValue: number,
) {
  if (endsAt.getTime() <= startsAt.getTime()) {
    throw routeError(400, "Promotion end date must be after its start date.");
  }
  if (discountType === "percentage" && discountValue > 100) {
    throw routeError(400, "Percentage promotions cannot exceed 100%.");
  }
}

async function verifyPromotionPlan(planId: string | null | undefined) {
  if (!planId) return;
  const [plan] = await db
    .select({ id: driverSubscriptionPlans.id })
    .from(driverSubscriptionPlans)
    .where(eq(driverSubscriptionPlans.id, planId))
    .limit(1);
  if (!plan) throw routeError(404, "The selected subscription plan was not found.");
}

function isUniqueViolation(error: unknown): boolean {
  return Boolean(
    error &&
      typeof error === "object" &&
      "code" in error &&
      (error as { code?: unknown }).code === "23505",
  );
}

router.get("/admin/driver-subscription-plans", async (req, res, next) => {
  try {
    const actorId = await requireAdmin(req, res);
    if (!actorId) return;
    const [plans, counts] = await Promise.all([
      db.select().from(driverSubscriptionPlans).orderBy(asc(driverSubscriptionPlans.name)),
      db
        .select({
          planId: driverSubscriptions.planId,
          count: sql<number>`count(*)::int`,
        })
        .from(driverSubscriptions)
        .where(
          and(
            eq(driverSubscriptions.status, "active"),
            or(isNull(driverSubscriptions.expiresAt), gt(driverSubscriptions.expiresAt, new Date())),
          ),
        )
        .groupBy(driverSubscriptions.planId),
    ]);
    const countByPlan = new Map(
      counts.filter((row) => row.planId).map((row) => [row.planId!, row.count]),
    );
    res.json({
      plans: plans.map((plan) => ({
        id: plan.id,
        name: plan.name,
        description: plan.description,
        amountMinor: plan.amountMinor,
        currency: plan.currency,
        billingInterval: plan.billingInterval,
        renewalMode: plan.renewalMode,
        isActive: plan.isActive,
        activeSubscriptionCount: countByPlan.get(plan.id) ?? 0,
      })),
    });
  } catch (error) {
    sendRouteError(error, res, next);
  }
});

router.post("/admin/driver-subscription-plans", async (req, res, next) => {
  try {
    const actorId = await requireAdmin(req, res);
    if (!actorId) return;
    const parsed = CreateAdminDriverSubscriptionPlanBody.safeParse(req.body);
    if (!parsed.success) {
      res.status(400).json({ error: parsed.error.message });
      return;
    }
    const body = parsed.data;
    const id = randomUUID();
    const now = new Date();
    const [plan] = await db
      .insert(driverSubscriptionPlans)
      .values({
        id,
        name: body.name.trim(),
        description: body.description?.trim() || null,
        amountMinor: body.amountMinor,
        currency: body.currency,
        billingInterval: body.billingInterval,
        renewalMode: body.renewalMode,
        isActive: body.isActive ?? true,
        createdBy: actorId,
        updatedBy: actorId,
        createdAt: now,
        updatedAt: now,
      })
      .returning();
    await audit(actorId, "driver_subscription_plan", id, "created", null, {
      name: plan.name,
      amountMinor: plan.amountMinor,
      currency: plan.currency,
      billingInterval: plan.billingInterval,
      renewalMode: plan.renewalMode,
      isActive: plan.isActive,
    });
    res.status(201).json({
      plan: {
        id: plan.id,
        name: plan.name,
        description: plan.description,
        amountMinor: plan.amountMinor,
        currency: plan.currency,
        billingInterval: plan.billingInterval,
        renewalMode: plan.renewalMode,
        isActive: plan.isActive,
        activeSubscriptionCount: 0,
      },
    });
  } catch (error) {
    sendRouteError(error, res, next);
  }
});

router.patch("/admin/driver-subscription-plans/:planId", async (req, res, next) => {
  try {
    const actorId = await requireAdmin(req, res);
    if (!actorId) return;
    const params = UpdateAdminDriverSubscriptionPlanParams.safeParse(req.params);
    const parsed = UpdateAdminDriverSubscriptionPlanBody.safeParse(req.body);
    if (!params.success || !parsed.success) {
      res.status(400).json({
        error: !params.success
          ? params.error.message
          : parsed.error?.message ?? "Invalid request.",
      });
      return;
    }
    const input = parsed.data;
    if (!Object.keys(input).length) {
      res.status(400).json({ error: "Choose at least one plan field to update." });
      return;
    }
    const [current] = await db
      .select()
      .from(driverSubscriptionPlans)
      .where(eq(driverSubscriptionPlans.id, params.data.planId))
      .limit(1);
    if (!current) {
      res.status(404).json({ error: "Subscription plan was not found." });
      return;
    }
    const nextName = input.name?.trim() ?? current.name;
    const nextDescription =
      input.description === undefined
        ? current.description
        : input.description?.trim() || null;
    const nextAmount = input.amountMinor ?? current.amountMinor;
    const nextCurrency = input.currency ?? current.currency;
    const nextInterval = input.billingInterval ?? current.billingInterval;
    const nextRenewalMode = input.renewalMode ?? current.renewalMode;
    const nextIsActive = input.isActive ?? current.isActive;
    const billingChanged =
      nextName !== current.name ||
      nextAmount !== current.amountMinor ||
      nextCurrency !== current.currency ||
      nextInterval !== current.billingInterval ||
      nextRenewalMode !== current.renewalMode;
    const recurringTermsChanged =
      nextAmount !== current.amountMinor ||
      nextCurrency !== current.currency ||
      nextInterval !== current.billingInterval ||
      nextRenewalMode !== current.renewalMode;
    if (recurringTermsChanged) {
      const [activeRenewals] = await db
        .select({ count: sql<number>`count(*)::int` })
        .from(driverSubscriptions)
        .where(
          and(
            eq(driverSubscriptions.planId, current.id),
            eq(driverSubscriptions.status, "active"),
            eq(driverSubscriptions.autoRenewActive, true),
          ),
        );
      if ((activeRenewals?.count ?? 0) > 0) {
        res.status(409).json({
          error:
            "This plan has drivers with active automatic renewals. Stop or safely reassign those renewals before changing its price, interval, or renewal mode.",
        });
        return;
      }
    }
    const [updated] = await db
      .update(driverSubscriptionPlans)
      .set({
        name: nextName,
        description: nextDescription,
        amountMinor: nextAmount,
        currency: nextCurrency,
        billingInterval: nextInterval,
        renewalMode: nextRenewalMode,
        isActive: nextIsActive,
        paystackPlanCode: billingChanged ? null : current.paystackPlanCode,
        updatedBy: actorId,
        updatedAt: new Date(),
      })
      .where(eq(driverSubscriptionPlans.id, current.id))
      .returning();
    await audit(actorId, "driver_subscription_plan", current.id, "updated", {
      name: current.name,
      amountMinor: current.amountMinor,
      billingInterval: current.billingInterval,
      renewalMode: current.renewalMode,
      isActive: current.isActive,
    }, {
      name: updated.name,
      amountMinor: updated.amountMinor,
      billingInterval: updated.billingInterval,
      renewalMode: updated.renewalMode,
      isActive: updated.isActive,
    });
    const [count] = await db
      .select({ count: sql<number>`count(*)::int` })
      .from(driverSubscriptions)
      .where(
        and(
          eq(driverSubscriptions.planId, updated.id),
          eq(driverSubscriptions.status, "active"),
          or(isNull(driverSubscriptions.expiresAt), gt(driverSubscriptions.expiresAt, new Date())),
        ),
      );
    res.json({
      plan: {
        id: updated.id,
        name: updated.name,
        description: updated.description,
        amountMinor: updated.amountMinor,
        currency: updated.currency,
        billingInterval: updated.billingInterval,
        renewalMode: updated.renewalMode,
        isActive: updated.isActive,
        activeSubscriptionCount: count?.count ?? 0,
      },
    });
  } catch (error) {
    sendRouteError(error, res, next);
  }
});

router.get("/admin/driver-promotions", async (req, res, next) => {
  try {
    const actorId = await requireAdmin(req, res);
    if (!actorId) return;
    const [promotions, redemptionCounts] = await Promise.all([
      db.select().from(driverPromotions).orderBy(desc(driverPromotions.createdAt)),
      db
        .select({
          promotionId: driverPromotionRedemptions.promotionId,
          count: sql<number>`count(*) filter (where ${driverPromotionRedemptions.status} in ('pending', 'redeemed'))::int`,
        })
        .from(driverPromotionRedemptions)
        .groupBy(driverPromotionRedemptions.promotionId),
    ]);
    const counts = new Map(redemptionCounts.map((row) => [row.promotionId, row.count]));
    res.json({
      promotions: promotions.map((promotion) =>
        promotionView(promotion, counts.get(promotion.id) ?? 0),
      ),
    });
  } catch (error) {
    sendRouteError(error, res, next);
  }
});

router.post("/admin/driver-promotions", async (req, res, next) => {
  try {
    const actorId = await requireAdmin(req, res);
    if (!actorId) return;
    const parsed = CreateAdminDriverPromotionBody.safeParse(req.body);
    if (!parsed.success) {
      res.status(400).json({ error: parsed.error.message });
      return;
    }
    const body = parsed.data;
    validatePromotionDates(body.startsAt, body.endsAt, body.discountType, body.discountValue);
    await verifyPromotionPlan(body.planId);
    const id = randomUUID();
    const code = body.code.trim().toUpperCase();
    const now = new Date();
    const [promotion] = await db
      .insert(driverPromotions)
      .values({
        id,
        code,
        name: body.name.trim(),
        discountType: body.discountType,
        discountValue: body.discountValue,
        maxDiscountMinor: body.maxDiscountMinor ?? null,
        currency: body.currency,
        planId: body.planId ?? null,
        startsAt: body.startsAt,
        endsAt: body.endsAt,
        serviceArea: body.serviceArea?.trim() || null,
        maxRedemptions: body.maxRedemptions ?? null,
        newDriverOnly: body.newDriverOnly ?? false,
        minimumCompletedRides: body.minimumCompletedRides ?? null,
        minimumRating: body.minimumRating ?? null,
        isActive: body.isActive ?? true,
        createdBy: actorId,
        updatedBy: actorId,
        createdAt: now,
        updatedAt: now,
      })
      .returning();
    await audit(actorId, "driver_promotion", id, "created", null, {
      code: promotion.code,
      name: promotion.name,
      discountType: promotion.discountType,
      discountValue: promotion.discountValue,
      isActive: promotion.isActive,
    });
    res.status(201).json({ promotion: promotionView(promotion, 0) });
  } catch (error) {
    if (isUniqueViolation(error)) {
      res.status(409).json({ error: "A promotion with this code already exists." });
      return;
    }
    sendRouteError(error, res, next);
  }
});

router.patch("/admin/driver-promotions/:promotionId", async (req, res, next) => {
  try {
    const actorId = await requireAdmin(req, res);
    if (!actorId) return;
    const params = UpdateAdminDriverPromotionParams.safeParse(req.params);
    const parsed = UpdateAdminDriverPromotionBody.safeParse(req.body);
    if (!params.success || !parsed.success) {
      res.status(400).json({
        error: !params.success
          ? params.error.message
          : parsed.error?.message ?? "Invalid request.",
      });
      return;
    }
    const input = parsed.data;
    if (!Object.keys(input).length) {
      res.status(400).json({ error: "Choose at least one promotion field to update." });
      return;
    }
    const [current] = await db
      .select()
      .from(driverPromotions)
      .where(eq(driverPromotions.id, params.data.promotionId))
      .limit(1);
    if (!current) {
      res.status(404).json({ error: "Promotion was not found." });
      return;
    }
    const nextStartsAt = input.startsAt ?? current.startsAt;
    const nextEndsAt = input.endsAt ?? current.endsAt;
    const nextDiscountType = input.discountType ?? current.discountType;
    const nextDiscountValue = input.discountValue ?? current.discountValue;
    validatePromotionDates(nextStartsAt, nextEndsAt, nextDiscountType, nextDiscountValue);
    if (input.planId !== undefined) await verifyPromotionPlan(input.planId);
    const [updated] = await db
      .update(driverPromotions)
      .set({
        code: input.code?.trim().toUpperCase() ?? current.code,
        name: input.name?.trim() ?? current.name,
        discountType: nextDiscountType,
        discountValue: nextDiscountValue,
        maxDiscountMinor:
          input.maxDiscountMinor === undefined
            ? current.maxDiscountMinor
            : input.maxDiscountMinor,
        currency: input.currency ?? current.currency,
        planId: input.planId === undefined ? current.planId : input.planId,
        startsAt: nextStartsAt,
        endsAt: nextEndsAt,
        serviceArea:
          input.serviceArea === undefined
            ? current.serviceArea
            : input.serviceArea?.trim() || null,
        maxRedemptions:
          input.maxRedemptions === undefined
            ? current.maxRedemptions
            : input.maxRedemptions,
        newDriverOnly: input.newDriverOnly ?? current.newDriverOnly,
        minimumCompletedRides:
          input.minimumCompletedRides === undefined
            ? current.minimumCompletedRides
            : input.minimumCompletedRides,
        minimumRating:
          input.minimumRating === undefined
            ? current.minimumRating
            : input.minimumRating,
        isActive: input.isActive ?? current.isActive,
        updatedBy: actorId,
        updatedAt: new Date(),
      })
      .where(eq(driverPromotions.id, current.id))
      .returning();
    await audit(actorId, "driver_promotion", current.id, "updated", {
      code: current.code,
      discountType: current.discountType,
      discountValue: current.discountValue,
      isActive: current.isActive,
    }, {
      code: updated.code,
      discountType: updated.discountType,
      discountValue: updated.discountValue,
      isActive: updated.isActive,
    });
    const [count] = await db
      .select({
        count: sql<number>`count(*) filter (where ${driverPromotionRedemptions.status} in ('pending', 'redeemed'))::int`,
      })
      .from(driverPromotionRedemptions)
      .where(eq(driverPromotionRedemptions.promotionId, updated.id));
    res.json({
      promotion: promotionView(updated, count?.count ?? 0),
    });
  } catch (error) {
    if (isUniqueViolation(error)) {
      res.status(409).json({ error: "A promotion with this code already exists." });
      return;
    }
    sendRouteError(error, res, next);
  }
});

router.get("/admin/driver-subscriptions", async (req, res, next) => {
  try {
    const actorId = await requireAdmin(req, res);
    if (!actorId) return;
    const parsed = GetAdminDriverSubscriptionsQueryParams.safeParse(req.query);
    if (!parsed.success) {
      res.status(400).json({ error: parsed.error.message });
      return;
    }
    const { q, page, limit } = parsed.data;
    const search = q?.trim();
    const where = search
      ? and(
          eq(wayfareUsers.role, "driver"),
          isNull(wayfareUsers.deletedAt),
          or(
            ilike(wayfareUsers.displayName, `%${search}%`),
            ilike(wayfareUsers.email, `%${search}%`),
            ilike(wayfareUsers.id, `%${search}%`),
          ),
        )
      : and(eq(wayfareUsers.role, "driver"), isNull(wayfareUsers.deletedAt));
    const rows = await db
      .select({
        driverId: wayfareUsers.id,
        displayName: wayfareUsers.displayName,
        email: wayfareUsers.email,
        subscription: driverSubscriptions,
        planName: driverSubscriptionPlans.name,
      })
      .from(wayfareUsers)
      .leftJoin(driverSubscriptions, eq(driverSubscriptions.driverId, wayfareUsers.id))
      .leftJoin(
        driverSubscriptionPlans,
        eq(driverSubscriptionPlans.id, driverSubscriptions.planId),
      )
      .where(where)
      .orderBy(asc(wayfareUsers.displayName), asc(wayfareUsers.id))
      .limit(limit + 1)
      .offset(page * limit);
    const hasMore = rows.length > limit;
    const pageRows = rows.slice(0, limit);
    const driverIds = pageRows.map((row) => row.driverId);
    const [payments, ratingRows] = driverIds.length
      ? await Promise.all([
          db
            .select()
            .from(driverSubscriptionRecords)
            .where(inArray(driverSubscriptionRecords.driverId, driverIds))
            .orderBy(desc(driverSubscriptionRecords.startedAt)),
          db
            .select()
            .from(driverRatingRecords)
            .where(inArray(driverRatingRecords.driverId, driverIds)),
        ])
      : [[], []];
    const lastPaymentByDriver = new Map<
      string,
      (typeof payments)[number]
    >();
    for (const payment of payments) {
      if (!lastPaymentByDriver.has(payment.driverId)) {
        lastPaymentByDriver.set(payment.driverId, payment);
      }
    }
    const ratingByDriver = new Map(ratingRows.map((row) => [row.driverId, row]));
    res.json({
      drivers: pageRows.map((row) => {
        const sub = row.subscription?.driverId ? row.subscription : null;
        const payment = lastPaymentByDriver.get(row.driverId);
        const rating = ratingByDriver.get(row.driverId);
        const legacyStatus =
          payment?.status === "active" && !payment.expiresAt
            ? "expired"
            : payment
              ? effectiveSubscriptionStatus(payment.status, payment.expiresAt)
              : null;
        return {
          driverId: row.driverId,
          displayName: row.displayName,
          email: row.email,
          subscription: sub
            ? subscriptionView(sub, row.planName)
            : payment && legacyStatus
              ? {
                  driverId: row.driverId,
                  planId: payment.planId,
                  planName: null,
                  status: legacyStatus,
                  renewalMode: "manual",
                  autoRenewActive: false,
                  autoRenewIssue:
                    payment.status === "active" && !payment.expiresAt
                      ? "This legacy subscription has no expiry date. Set an end date before restoring access."
                      : null,
                  cancelAtPeriodEnd: false,
                  amountMinor: payment.amountMinor,
                  currency: payment.currency,
                  provider: payment.provider,
                  startedAt: payment.startedAt.getTime(),
                  expiresAt: millis(payment.expiresAt),
                  nextPaymentAt: null,
                  autoRenewConsentAt: null,
                }
              : null,
          lastPayment: payment
            ? {
                id: payment.id,
                provider: payment.provider,
                reference: payment.externalReference,
                amountMinor: payment.amountMinor,
                currency: payment.currency,
                status: payment.status,
                startedAt: payment.startedAt.getTime(),
                expiresAt: millis(payment.expiresAt),
              }
            : null,
          rating: {
            averageRating: rating?.averageRating ?? null,
            ratingCount: rating?.ratingCount ?? 0,
            source: rating?.source ?? "verified_rider_ratings",
          },
        };
      }),
      page,
      limit,
      hasMore,
    });
  } catch (error) {
    sendRouteError(error, res, next);
  }
});

router.patch("/admin/driver-subscriptions/:driverId", async (req, res, next) => {
  try {
    const actorId = await requireAdmin(req, res);
    if (!actorId) return;
    const params = UpdateAdminDriverSubscriptionParams.safeParse(req.params);
    const parsed = UpdateAdminDriverSubscriptionBody.safeParse(req.body);
    if (!params.success || !parsed.success) {
      res.status(400).json({
        error: !params.success
          ? params.error.message
          : parsed.error?.message ?? "Invalid request.",
      });
      return;
    }
    const { driverId } = params.data;
    const input = parsed.data;
    if (
      input.planId === undefined &&
      input.status === undefined &&
      input.expiresAt === undefined
    ) {
      res.status(400).json({ error: "Choose a plan, status, or end date to update." });
      return;
    }
    const [driver] = await db
      .select({ id: wayfareUsers.id })
      .from(wayfareUsers)
      .where(and(eq(wayfareUsers.id, driverId), eq(wayfareUsers.role, "driver")))
      .limit(1);
    if (!driver) {
      res.status(404).json({ error: "Driver was not found." });
      return;
    }
    const [current] = await db
      .select()
      .from(driverSubscriptions)
      .where(eq(driverSubscriptions.driverId, driverId))
      .limit(1);
    if (
      current?.status === "pending" &&
      ((input.status !== undefined && input.status !== "pending") ||
        (input.planId !== undefined && input.planId !== current.planId) ||
        (input.expiresAt !== undefined &&
          input.expiresAt?.getTime() !== current.expiresAt?.getTime()))
    ) {
      res.status(409).json({
        error:
          "This driver has an open payment checkout. Wait for it to finish or ask the driver to cancel it before changing access.",
      });
      return;
    }
    const [currentPlan] = current?.planId
      ? await db
          .select()
          .from(driverSubscriptionPlans)
          .where(eq(driverSubscriptionPlans.id, current.planId))
          .limit(1)
      : [];
    const [requestedPlan] = input.planId
      ? await db
          .select()
          .from(driverSubscriptionPlans)
          .where(eq(driverSubscriptionPlans.id, input.planId))
          .limit(1)
      : [];
    if (input.planId && !requestedPlan) {
      res.status(404).json({ error: "The selected subscription plan was not found." });
      return;
    }
    if (!current && (!input.planId || input.status !== "active" || !input.expiresAt)) {
      res.status(400).json({
        error: "Assigning a new subscription requires a plan, active status, and end date.",
      });
      return;
    }
    const nextPlanId = input.planId ?? current?.planId ?? null;
    const nextStatus = input.status ?? current?.status ?? "active";
    const nextExpiresAt =
      input.expiresAt === undefined ? current?.expiresAt ?? null : input.expiresAt;
    const now = new Date();
    if (nextStatus === "active" && (!nextPlanId || !nextExpiresAt || nextExpiresAt <= now)) {
      res.status(400).json({
        error: "An active subscription needs a plan and a future end date.",
      });
      return;
    }
    const cancellationRequired = current
      ? providerCancellationRequired(
          current,
          {
            ...(input.planId !== undefined ? { planId: nextPlanId } : {}),
            ...(input.status !== undefined ? { status: nextStatus } : {}),
            ...(input.expiresAt !== undefined ? { expiresAt: nextExpiresAt } : {}),
          },
        )
      : false;
    if (
      cancellationRequired &&
      current?.provider === "paystack" &&
      (!current.providerSubscriptionCode || !current.providerEmailToken)
    ) {
      res.status(409).json({
        error: "Paystack cancellation details are unavailable; no subscription changes were saved.",
      });
      return;
    }
    if (
      cancellationRequired &&
      current?.provider === "paystack" &&
      current.providerSubscriptionCode &&
      current.providerEmailToken
    ) {
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
    const changedPlan = Boolean(current && input.planId && input.planId !== current.planId);
    const saved = await db.transaction(async (tx) => {
      const [row] = await tx
        .select()
        .from(driverSubscriptions)
        .where(eq(driverSubscriptions.driverId, driverId))
        .limit(1)
        .for("update");
      if (current && (!row || row.updatedAt.getTime() !== current.updatedAt.getTime())) {
        throw routeError(409, "The subscription changed during this update. Refresh and try again.");
      }
      const status =
        nextStatus === "active" &&
        nextExpiresAt &&
        nextExpiresAt.getTime() <= now.getTime()
          ? "expired"
          : nextStatus;
      const renewalMode =
        row?.autoRenewActive && !cancellationRequired && !changedPlan
          ? row.renewalMode
          : "manual";
      const values = {
        driverId,
        planId: nextPlanId,
        status,
        renewalMode,
        amountMinor: changedPlan && requestedPlan
          ? requestedPlan.amountMinor
          : row?.amountMinor ?? requestedPlan?.amountMinor ?? 0,
        currency: changedPlan && requestedPlan
          ? requestedPlan.currency
          : row?.currency ?? requestedPlan?.currency ?? "ZAR",
        provider: row?.provider ?? null,
        startedAt: row?.startedAt ?? now,
        expiresAt: nextExpiresAt,
        nextPaymentAt: cancellationRequired ? null : row?.nextPaymentAt ?? null,
        providerSubscriptionCode: row?.providerSubscriptionCode ?? null,
        providerEmailToken: cancellationRequired
          ? null
          : row?.providerEmailToken ?? null,
        providerPlanCode: changedPlan ? null : row?.providerPlanCode ?? null,
        autoRenewConsentAt: row?.autoRenewConsentAt ?? null,
        autoRenewActive: cancellationRequired ? false : row?.autoRenewActive ?? false,
        autoRenewIssue: cancellationRequired ? null : row?.autoRenewIssue ?? null,
        cancelAtPeriodEnd:
          status === "cancelled"
            ? false
            : cancellationRequired
              ? true
              : row?.cancelAtPeriodEnd ?? false,
        cancelledAt:
          status === "cancelled" || cancellationRequired
            ? now
            : row?.cancelledAt ?? null,
        cancelledReason:
          input.reason?.trim() ||
          (cancellationRequired
            ? "Provider renewal cancelled before admin subscription change."
            : row?.cancelledReason ?? null),
        updatedAt: now,
      };
      if (row) {
        await tx
          .update(driverSubscriptions)
          .set(values)
          .where(eq(driverSubscriptions.driverId, driverId));
      } else {
        await tx.insert(driverSubscriptions).values(values);
      }
      const { auditEvents } = await import("@workspace/db/schema");
      await tx.insert(auditEvents).values({
        id: randomUUID(),
        actorUserId: actorId,
        entityType: "driver_subscription",
        entityId: driverId,
        action: "admin_subscription_updated",
        previousState: row
          ? {
              planId: row.planId,
              status: row.status,
              expiresAt: row.expiresAt?.toISOString() ?? null,
              renewalMode: row.renewalMode,
              autoRenewActive: row.autoRenewActive,
            }
          : null,
        nextState: {
          planId: values.planId,
          status: values.status,
          expiresAt: values.expiresAt?.toISOString() ?? null,
          renewalMode: values.renewalMode,
          autoRenewActive: values.autoRenewActive,
        },
        reason: input.reason?.trim() || null,
      });
      const [updated] = await tx
        .select()
        .from(driverSubscriptions)
        .where(eq(driverSubscriptions.driverId, driverId))
        .limit(1);
      return updated;
    });
    const planName = changedPlan
      ? requestedPlan?.name ?? null
      : currentPlan?.name ?? requestedPlan?.name ?? null;
    res.json({ subscription: subscriptionView(saved, planName) });
  } catch (error) {
    sendRouteError(error, res, next);
  }
});

export default router;

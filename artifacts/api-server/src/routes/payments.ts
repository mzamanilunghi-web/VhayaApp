import { createHmac, randomUUID, timingSafeEqual } from "node:crypto";
import { and, desc, eq, gte, isNull, lte, inArray, sql } from "drizzle-orm";
import { Router, type IRouter, type Request } from "express";
import { db } from "@workspace/db";
import {
  rideEvents,
  ridePayments,
  rideRequests,
  driverSubscriptionRecords,
  driverPromotionRedemptions,
  driverPromotions,
  driverSubscriptionPlans,
  driverSubscriptions,
  walletTopups,
  wayfareUsers,
} from "@workspace/db/schema";
import { InitializeDriverSubscriptionBody } from "@workspace/api-zod";
import { getWayfareUserId } from "../middlewares/firebaseIdentity";
import { getWayfareRole } from "../middlewares/wayfareRoles";
import { getPaystackCallbackUrl } from "../lib/runtimeConfig";
import { recordBody, syncWayfareUser } from "../lib/wayfareData";
import { addBillingInterval } from "../lib/driverSubscriptionRules";
import { findEligibleDriverPromotion } from "../lib/driverPromotionService";
import {
  createPaystackPlan,
  createPaystackSubscription,
  isPaystackPaymentMode,
  paystackRequest,
} from "../lib/paystackSubscriptions";

const router: IRouter = Router();
const PAYSTACK_API = "https://api.paystack.co";
const CURRENCY = "ZAR";
const MAX_HOLD_DAYS = 7;
const HOLD_WINDOW_MS = MAX_HOLD_DAYS * 24 * 60 * 60 * 1000;
type PaymentProvider = "mock" | "paystack";
export type PaymentStatus =
  | "pending"
  | "authorized"
  | "capture_pending"
  | "capture_failed"
  | "release_pending"
  | "release_failed"
  | "paid"
  | "failed"
  | "released"
  | "expired"
  | "refunded";

type PaystackPreauthorization = {
  reference?: string;
  status?: string;
  amount?: number | string;
  captured_amount?: number | string | null;
  currency?: string;
  expiry_date?: string | null;
  created_at?: string;
  captured_at?: string | null;
  released_at?: string | null;
};

type PaymentStatusOptions = {
  authorizedAt?: Date | null;
  expiresAt?: Date | null;
  capturedAmountMinor?: number | null;
  paidAt?: Date | null;
};

function providerMode(): PaymentProvider {
  return isPaystackPaymentMode() ? "paystack" : "mock";
}

function paymentSummary(payment: typeof ridePayments.$inferSelect) {
  return {
    id: payment.id,
    tripId: payment.requestId,
    driverId: payment.driverId ?? null,
    provider: payment.provider,
    reference: payment.reference,
    paymentMethod: payment.paymentMethod,
    amountMinor: payment.capturedAmountMinor ?? payment.amountMinor,
    commissionMinor: payment.commissionMinor,
    netAmountMinor:
      (payment.capturedAmountMinor ?? payment.amountMinor) -
      payment.commissionMinor,
    reservedAmountMinor: payment.amountMinor,
    capturedAmountMinor: payment.capturedAmountMinor,
    currency: payment.currency,
    status: payment.status as PaymentStatus,
    authorizedAt: payment.authorizedAt?.getTime() ?? null,
    expiresAt: payment.expiresAt?.getTime() ?? null,
    paidAt: payment.paidAt?.getTime() ?? null,
    driverPayoutId: payment.driverPayoutId ?? null,
    createdAt: payment.createdAt.getTime(),
  };
}

function isPaystackCallbackUrl(value: string): boolean {
  try {
    const url = new URL(value);
    return (
      url.protocol === "https:" &&
      url.pathname.endsWith("/api/payments/callback") &&
      !url.username &&
      !url.password &&
      !url.search &&
      !url.hash
    );
  } catch {
    return false;
  }
}

function statusError(error: unknown): number {
  return error && typeof error === "object" && "status" in error
    ? Number((error as { status: unknown }).status)
    : 0;
}

async function loadPaymentForRider(reference: string, riderId: string) {
  const rows = await db
    .select()
    .from(ridePayments)
    .where(and(eq(ridePayments.reference, reference), eq(ridePayments.riderId, riderId)))
    .limit(1);
  return rows[0] ?? null;
}

async function savePaymentStatus(
  payment: typeof ridePayments.$inferSelect,
  status: PaymentStatus,
  actorId: string,
  options: PaymentStatusOptions = {},
  onlyFrom?: readonly PaymentStatus[],
) {
  return db.transaction(async (tx) => {
    const currentRows = await tx
      .select()
      .from(ridePayments)
      .where(eq(ridePayments.id, payment.id))
      .limit(1)
      .for("update");
    const current = currentRows[0];
    if (!current) return false;
    if (onlyFrom && !onlyFrom.includes(current.status as PaymentStatus)) return false;
    if (
      (current.status === "paid" && status !== "paid") ||
      (current.status === "released" && status !== "released") ||
      (current.status === "authorized" && ["pending", "failed"].includes(status))
    ) {
      return false;
    }
    const now = new Date();
    await tx
      .update(ridePayments)
      .set({
        status,
        authorizedAt:
          options.authorizedAt ??
          (status === "authorized" ? current.authorizedAt ?? now : current.authorizedAt),
        expiresAt: options.expiresAt === undefined ? current.expiresAt : options.expiresAt,
        capturedAmountMinor:
          options.capturedAmountMinor === undefined
            ? current.capturedAmountMinor
            : options.capturedAmountMinor,
        paidAt:
          options.paidAt ??
          (status === "paid" ? current.paidAt ?? now : current.paidAt),
        updatedAt: now,
      })
      .where(eq(ridePayments.id, payment.id));
    if (current.status !== status) {
      const actions: Record<PaymentStatus, string> = {
        pending: "payment_pending",
        authorized: "payment_authorized",
        capture_pending: "payment_capture_pending",
        capture_failed: "payment_capture_failed",
        release_pending: "payment_release_pending",
        release_failed: "payment_release_failed",
        paid: "payment_paid",
        failed: "payment_failed",
        released: "payment_released",
        expired: "payment_expired",
        refunded: "payment_refunded",
      };
      await tx.insert(rideEvents).values({
        requestId: current.requestId,
        actorId,
        action: actions[status],
        details: {
          provider: current.provider,
          reference: current.reference,
          amountMinor: current.amountMinor,
          capturedAmountMinor: options.capturedAmountMinor ?? current.capturedAmountMinor,
          currency: current.currency,
          status,
        },
      });
    }
    return true;
  });
}

async function loadLatestPaymentForRide(requestId: string) {
  const rows = await db
    .select()
    .from(ridePayments)
    .where(eq(ridePayments.requestId, requestId))
    .orderBy(desc(ridePayments.createdAt))
    .limit(1);
  return rows[0] ?? null;
}

async function loadPaymentById(paymentId: string) {
  const rows = await db.select().from(ridePayments).where(eq(ridePayments.id, paymentId)).limit(1);
  return rows[0] ?? null;
}

async function verifyPaystackPreauthorization(reference: string) {
  const secret = process.env.PAYSTACK_SECRET_KEY;
  if (!secret) throw Object.assign(new Error("Paystack is not configured on the server yet."), { status: 503 });
  const response = await fetch(
    `${PAYSTACK_API}/preauthorization/verify/${encodeURIComponent(reference)}`,
    {
      headers: { Authorization: `Bearer ${secret}` },
      signal: AbortSignal.timeout(12_000),
    },
  );
  const payload = (await response.json()) as {
    status?: boolean;
    message?: string;
    data?: PaystackPreauthorization;
  };
  if (!response.ok || payload.status !== true || !payload.data) {
    throw Object.assign(
      new Error(payload.message || "Paystack could not verify this card authorization."),
      { status: 502 },
    );
  }
  return payload.data;
}

function mapPaystackStatus(status: string | undefined): PaymentStatus | null {
  switch (status) {
    case "authorized":
      return "authorized";
    case "captured":
    case "success":
      return "paid";
    case "released":
      return "released";
    case "expired":
      return "expired";
    case "failed":
    case "abandoned":
      return "failed";
    case "ongoing":
      return "capture_pending";
    default:
      return null;
  }
}

async function saveVerifiedPreauthorization(
  payment: typeof ridePayments.$inferSelect,
  data: PaystackPreauthorization,
  actorId: string,
) {
  const amount = Number(data.amount);
  if (
    data.reference !== payment.reference ||
    data.currency !== payment.currency ||
    !Number.isSafeInteger(amount) ||
    amount !== payment.amountMinor
  ) {
    await savePaymentStatus(payment, "failed", actorId);
    throw Object.assign(new Error("The verified card authorization does not match this ride fare."), {
      status: 409,
    });
  }

  const status = mapPaystackStatus(data.status);
  if (!status) return payment;
  const expiry = data.expiry_date ? new Date(data.expiry_date) : null;
  const captured = Number(data.captured_amount ?? data.amount);
  await savePaymentStatus(payment, status, actorId, {
    authorizedAt:
      status === "authorized" && data.created_at
        ? new Date(data.created_at)
        : status === "authorized"
          ? new Date()
          : undefined,
    expiresAt: expiry && Number.isFinite(expiry.getTime()) ? expiry : null,
    capturedAmountMinor:
      status === "paid" && Number.isSafeInteger(captured) ? captured : undefined,
    paidAt:
      status === "paid" && data.captured_at
        ? new Date(data.captured_at)
        : undefined,
  });
  return (await loadPaymentById(payment.id)) ?? payment;
}

export async function captureRidePaymentForCompletion(requestId: string, actorId: string) {
  const rideRows = await db
    .select()
    .from(rideRequests)
    .where(eq(rideRequests.id, requestId))
    .limit(1);
  const ride = rideRows[0];
  if (!ride || ride.status !== "completed") return null;

  let payment = await loadLatestPaymentForRide(requestId);
  if (!payment || ["paid", "released", "expired", "failed"].includes(payment.status)) {
    return payment;
  }
  const amountMinor = Math.round(ride.fare * 100);
  if (!Number.isSafeInteger(amountMinor) || amountMinor < 100 || amountMinor > payment.amountMinor) {
    throw Object.assign(new Error("The completed fare exceeds the amount reserved on the card."), {
      status: 409,
    });
  }

  if (payment.provider === "mock") {
    if (payment.status !== "authorized") return payment;
    await savePaymentStatus(payment, "paid", actorId, {
      capturedAmountMinor: amountMinor,
      paidAt: new Date(),
    });
    return (await loadPaymentById(payment.id)) ?? payment;
  }

  if (payment.status === "capture_pending" || payment.status === "capture_failed") {
    if (
      payment.status === "capture_pending" &&
      Date.now() - payment.updatedAt.getTime() < 60_000
    ) {
      return payment;
    }
    const verification = await verifyPaystackPreauthorization(payment.reference);
    payment = await saveVerifiedPreauthorization(payment, verification, actorId);
    if (["paid", "released", "expired", "failed"].includes(payment.status)) return payment;
    if (payment.status !== "authorized") return payment;
  }
  if (payment.status !== "authorized") return payment;

  const claimedPaymentId = await db.transaction(async (tx) => {
    const lockedRows = await tx
      .select()
      .from(ridePayments)
      .where(eq(ridePayments.id, payment!.id))
      .limit(1)
      .for("update");
    const current = lockedRows[0];
    if (!current || current.status !== "authorized") return null;
    const now = new Date();
    await tx
      .update(ridePayments)
      .set({ status: "capture_pending", updatedAt: now })
      .where(eq(ridePayments.id, current.id));
    await tx.insert(rideEvents).values({
      requestId: current.requestId,
      actorId,
      action: "payment_capture_pending",
      details: { provider: current.provider, reference: current.reference, status: "capture_pending" },
    });
    return current.id;
  });
  const claimed = claimedPaymentId ? await loadPaymentById(claimedPaymentId) : null;
  if (!claimed) return (await loadPaymentById(payment.id)) ?? payment;

  try {
    const secret = process.env.PAYSTACK_SECRET_KEY;
    if (!secret) throw new Error("Paystack is not configured on the server yet.");
    const response = await fetch(`${PAYSTACK_API}/preauthorization/capture`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${secret}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        reference: claimed.reference,
        currency: claimed.currency,
        amount: String(amountMinor),
      }),
      signal: AbortSignal.timeout(12_000),
    });
    const payload = (await response.json()) as {
      status?: boolean;
      message?: string;
      data?: PaystackPreauthorization;
    };
    const data = payload.data;
    if (!response.ok || payload.status !== true || !data) {
      await savePaymentStatus(claimed, "capture_failed", actorId);
      throw Object.assign(
        new Error(payload.message || "Paystack could not capture the completed ride fare."),
        { status: 502 },
      );
    }
    if (
      data.reference !== claimed.reference ||
      data.currency !== claimed.currency ||
      (data.amount !== undefined && Number(data.amount) > claimed.amountMinor)
    ) {
      await savePaymentStatus(claimed, "capture_failed", actorId);
      throw Object.assign(new Error("Paystack returned a capture that does not match this ride."), {
        status: 409,
      });
    }
    if (data.status === "success" || data.status === "captured") {
      await savePaymentStatus(claimed, "paid", actorId, {
        capturedAmountMinor: amountMinor,
        paidAt: data.captured_at ? new Date(data.captured_at) : new Date(),
      });
    } else if (data.status === "ongoing") {
      await savePaymentStatus(claimed, "capture_pending", actorId);
    } else {
      await savePaymentStatus(claimed, "capture_failed", actorId);
    }
  } catch (error) {
    if (statusError(error) === 0) {
      // A timeout may happen after Paystack accepted the capture. Keep the
      // state pending until verification or a signed webhook confirms it.
      await savePaymentStatus(claimed, "capture_pending", actorId);
      return (await loadPaymentById(claimed.id)) ?? claimed;
    }
    throw error;
  }
  return (await loadPaymentById(claimed.id)) ?? claimed;
}

export async function releaseRidePaymentForCancellation(
  requestId: string,
  actorId: string,
  paymentId?: string,
) {
  const rideRows = await db
    .select()
    .from(rideRequests)
    .where(eq(rideRequests.id, requestId))
    .limit(1);
  const ride = rideRows[0];
  if (!ride || !["cancelled", "unfulfilled"].includes(ride.status)) return null;
  const payment = paymentId
    ? await loadPaymentById(paymentId)
    : await loadLatestPaymentForRide(requestId);
  if (!payment || payment.requestId !== requestId) return null;
  if (!payment || ["paid", "released", "expired", "failed"].includes(payment.status)) return payment;
  if (payment.provider === "mock" && payment.status === "pending") {
    await savePaymentStatus(payment, "released", actorId);
    return (await loadPaymentById(payment.id)) ?? payment;
  }
  if (!["authorized", "release_pending", "release_failed"].includes(payment.status)) return payment;

  if (payment.provider === "mock") {
    await savePaymentStatus(payment, "released", actorId);
    return (await loadPaymentById(payment.id)) ?? payment;
  }
  if (
    payment.status === "release_pending" &&
    Date.now() - payment.updatedAt.getTime() < 60_000
  ) {
    return payment;
  }

  const claimedPaymentId = await db.transaction(async (tx) => {
    const lockedRows = await tx
      .select()
      .from(ridePayments)
      .where(eq(ridePayments.id, payment.id))
      .limit(1)
      .for("update");
    const current = lockedRows[0];
    if (!current || !["authorized", "release_pending", "release_failed"].includes(current.status)) {
      return null;
    }
    const now = new Date();
    await tx
      .update(ridePayments)
      .set({ status: "release_pending", updatedAt: now })
      .where(eq(ridePayments.id, current.id));
    if (current.status !== "release_pending") {
      await tx.insert(rideEvents).values({
        requestId: current.requestId,
        actorId,
        action: "payment_release_pending",
        details: { provider: current.provider, reference: current.reference, status: "release_pending" },
      });
    }
    return current.id;
  });
  const claimed = claimedPaymentId ? await loadPaymentById(claimedPaymentId) : null;
  if (!claimed) return (await loadPaymentById(payment.id)) ?? payment;

  try {
    const secret = process.env.PAYSTACK_SECRET_KEY;
    if (!secret) throw new Error("Paystack is not configured on the server yet.");
    const response = await fetch(`${PAYSTACK_API}/preauthorization/release`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${secret}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ reference: claimed.reference }),
      signal: AbortSignal.timeout(12_000),
    });
    const payload = (await response.json()) as {
      status?: boolean;
      message?: string;
      data?: { reference?: string; status?: string };
    };
    const data = payload.data;
    if (!response.ok || payload.status !== true || !data || data.reference !== claimed.reference) {
      await savePaymentStatus(claimed, "release_failed", actorId);
      throw Object.assign(
        new Error(payload.message || "Paystack could not release the cancelled ride hold."),
        { status: 502 },
      );
    }
    await savePaymentStatus(
      claimed,
      data.status === "released" ? "released" : "release_pending",
      actorId,
    );
  } catch (error) {
    if (statusError(error) === 0) {
      await savePaymentStatus(claimed, "release_pending", actorId);
      return (await loadPaymentById(claimed.id)) ?? claimed;
    }
    throw error;
  }
  return (await loadPaymentById(claimed.id)) ?? claimed;
}

router.get("/payments", async (req, res, next) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });

  try {
    if ((await getWayfareRole(userId)) !== "rider") {
      return res.status(403).json({ error: "Only riders can view ride payments." });
    }
    const rows = await db
      .select()
      .from(ridePayments)
      .where(eq(ridePayments.riderId, userId))
      .orderBy(desc(ridePayments.createdAt))
      .limit(200);
    return res.json({ payments: rows.map(paymentSummary) });
  } catch (error) {
    return next(error);
  }
});

router.get("/payments/callback", async (req, res, next) => {
  const referenceValue = req.query.reference ?? req.query.trxref;
  const reference = typeof referenceValue === "string" ? referenceValue : "";
  if (!/^[A-Za-z0-9.,=-]{1,100}$/.test(reference)) {
    return res.status(400).send("The Paystack callback is missing a valid reference.");
  }

  try {
    const [rideRow] = await db
      .select({ provider: ridePayments.provider })
      .from(ridePayments)
      .where(eq(ridePayments.reference, reference))
      .limit(1);
    const [subRow] = !rideRow
      ? await db
          .select({ provider: driverSubscriptionRecords.provider })
          .from(driverSubscriptionRecords)
          .where(eq(driverSubscriptionRecords.externalReference, reference))
          .limit(1)
      : [];
    const [topupRow] = !rideRow && !subRow
      ? await db
          .select({ provider: walletTopups.provider })
          .from(walletTopups)
          .where(eq(walletTopups.reference, reference))
          .limit(1)
      : [];
    const matched = rideRow ?? subRow ?? topupRow;
    if (!matched || matched.provider !== "paystack") {
      return res.status(404).send("This Vhaya payment could not be found.");
    }
    return res.redirect(
      303,
      `vhaya://payment-return?reference=${encodeURIComponent(reference)}`,
    );
  } catch (error) {
    return next(error);
  }
});

function webhookObject(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {};
}

function webhookString(value: unknown): string | null {
  return typeof value === "string" && value.length > 0 ? value : null;
}

function webhookDate(value: unknown): Date | null {
  if (typeof value !== "string") return null;
  const parsed = new Date(value);
  return Number.isFinite(parsed.getTime()) ? parsed : null;
}

function webhookError(status: number, message: string) {
  return Object.assign(new Error(message), { status });
}

async function handleDriverSubscriptionWebhook(
  event: string,
  data: Record<string, unknown>,
): Promise<boolean> {
  const subscriptionData = webhookObject(data.subscription);
  const customerData = webhookObject(data.customer);
  const reference = webhookString(data.reference);
  const providerSubscriptionCode =
    webhookString(subscriptionData.subscription_code) ??
    webhookString(data.subscription_code);

  if (event === "subscription.create") {
    const email = webhookString(customerData.email) ?? webhookString(data.email);
    const emailToken = webhookString(data.email_token);
    if (!providerSubscriptionCode || !email || !emailToken) return true;
    const [user] = await db
      .select({ id: wayfareUsers.id })
      .from(wayfareUsers)
      .where(eq(wayfareUsers.email, email))
      .limit(1);
    if (!user) return true;
    const [current] = await db
      .select()
      .from(driverSubscriptions)
      .where(eq(driverSubscriptions.driverId, user.id))
      .limit(1);
    if (
      current &&
      current.status === "active" &&
      current.renewalMode === "automatic" &&
      current.autoRenewConsentAt &&
      !current.cancelAtPeriodEnd
    ) {
      await db
        .update(driverSubscriptions)
        .set({
          providerSubscriptionCode,
          providerEmailToken: emailToken,
          autoRenewActive: true,
          autoRenewIssue: null,
          nextPaymentAt: webhookDate(subscriptionData.next_payment_date),
          updatedAt: new Date(),
        })
        .where(eq(driverSubscriptions.driverId, user.id));
    }
    return true;
  }

  if (
    event === "charge.success" ||
    event === "transaction.success" ||
    event === "charge.failed" ||
    event === "transaction.failed"
  ) {
    if (reference) {
      const [ledger] = await db
        .select()
        .from(driverSubscriptionRecords)
        .where(
          and(
            eq(driverSubscriptionRecords.provider, "paystack"),
            eq(driverSubscriptionRecords.externalReference, reference),
          ),
        )
        .limit(1);
      if (ledger) {
        if (
          event === "charge.failed" ||
          event === "transaction.failed" ||
          data.status === "failed"
        ) {
          await db.transaction(async (tx) => {
            const [lockedLedger] = await tx
              .select()
              .from(driverSubscriptionRecords)
              .where(eq(driverSubscriptionRecords.id, ledger.id))
              .for("update");
            if (
              !lockedLedger ||
              (lockedLedger.status !== "pending" && lockedLedger.status !== "cancelled")
            ) {
              return;
            }
            await tx
              .update(driverSubscriptionRecords)
              .set({ status: "expired" })
              .where(eq(driverSubscriptionRecords.id, ledger.id));
            await tx
              .update(driverSubscriptions)
              .set({
                status: "expired",
                autoRenewActive: false,
                autoRenewIssue: "The initial subscription payment failed.",
                updatedAt: new Date(),
              })
              .where(eq(driverSubscriptions.driverId, ledger.driverId));
            await tx
              .update(driverPromotionRedemptions)
              .set({ status: "released" })
              .where(eq(driverPromotionRedemptions.externalReference, reference));
          });
          return true;
        }
        const amount = Number(data.amount);
        if (
          !Number.isSafeInteger(amount) ||
          amount !== ledger.amountMinor ||
          data.currency !== ledger.currency
        ) {
          throw webhookError(
            400,
            "The subscription payment does not match the pending checkout.",
          );
        }
        if (ledger.status !== "pending" && ledger.status !== "cancelled") return true;
        if (!ledger.planId) {
          await db
            .update(driverSubscriptionRecords)
            .set({ status: "expired" })
            .where(eq(driverSubscriptionRecords.id, ledger.id));
          return true;
        }
        const [plan] = await db
          .select()
          .from(driverSubscriptionPlans)
          .where(eq(driverSubscriptionPlans.id, ledger.planId))
          .limit(1);
        if (!plan) {
          throw webhookError(409, "The subscription plan for this payment no longer exists.");
        }
        const startedAt = webhookDate(data.paid_at) ?? new Date();
        const expiresAt = addBillingInterval(startedAt, plan.billingInterval);
        const updated = await db.transaction(async (tx) => {
          const [lockedLedger] = await tx
            .select()
            .from(driverSubscriptionRecords)
            .where(eq(driverSubscriptionRecords.id, ledger.id))
            .for("update");
          if (
            !lockedLedger ||
            (lockedLedger.status !== "pending" && lockedLedger.status !== "cancelled")
          ) {
            return null;
          }
          const [current] = await tx
            .select()
            .from(driverSubscriptions)
            .where(eq(driverSubscriptions.driverId, ledger.driverId))
            .for("update");
          if (
            !current ||
            (current.status !== "pending" && current.status !== "cancelled") ||
            current.planId !== plan.id
          ) {
            await tx
              .update(driverSubscriptionRecords)
              .set({ status: "expired" })
              .where(eq(driverSubscriptionRecords.id, ledger.id));
            return null;
          }
          await tx
            .update(driverSubscriptionRecords)
            .set({
              status: "active",
              startedAt,
              expiresAt,
              metadata: {
                ...recordBody(lockedLedger.metadata),
                paid_at: startedAt.toISOString(),
              },
            })
            .where(eq(driverSubscriptionRecords.id, ledger.id));
          const [active] = await tx
            .update(driverSubscriptions)
            .set({
              status: "active",
              renewalMode: current.status === "cancelled" ? "manual" : current.renewalMode,
              amountMinor: plan.amountMinor,
              provider: "paystack",
              startedAt,
              expiresAt,
              nextPaymentAt: null,
              providerPlanCode: current.providerPlanCode ?? plan.paystackPlanCode,
              autoRenewActive: false,
              autoRenewIssue: null,
              autoRenewConsentAt: current.status === "cancelled" ? null : current.autoRenewConsentAt,
              cancelAtPeriodEnd: false,
              updatedAt: new Date(),
            })
            .where(eq(driverSubscriptions.driverId, ledger.driverId))
            .returning();
          await tx
            .update(driverPromotionRedemptions)
            .set({ status: "redeemed", redeemedAt: new Date() })
            .where(eq(driverPromotionRedemptions.externalReference, reference));
          return active ?? null;
        });
        if (!updated) return true;

        if (updated.renewalMode === "automatic" && updated.autoRenewConsentAt) {
          const authorization = webhookObject(data.authorization);
          const authCode = webhookString(authorization.authorization_code);
          const customerCode =
            webhookString(customerData.customer_code) ??
            webhookString(data.customer_code);
          const planCode = updated.providerPlanCode ?? plan.paystackPlanCode;
          let failureMessage: string | null = null;
          let created:
            | Awaited<ReturnType<typeof createPaystackSubscription>>
            | null = null;
          if (!authCode || !customerCode || !planCode) {
            failureMessage =
              "Payment succeeded, but Paystack did not provide the details needed to start automatic renewal. Renew manually.";
          } else {
            try {
              created = await createPaystackSubscription({
                customer: customerCode,
                plan: planCode,
                authorization: authCode,
                startDate: expiresAt,
              });
            } catch {
              failureMessage =
                "Payment succeeded, but Paystack automatic renewal could not be confirmed. Renew manually.";
            }
          }
          if (created?.subscription_code && created.email_token) {
            const [latest] = await db
              .select()
              .from(driverSubscriptions)
              .where(eq(driverSubscriptions.driverId, ledger.driverId))
              .limit(1);
            const stillConsented =
              latest?.status === "active" &&
              latest.planId === plan.id &&
              latest.renewalMode === "automatic" &&
              Boolean(latest.autoRenewConsentAt) &&
              !latest.cancelAtPeriodEnd;
            if (!stillConsented) {
              try {
                const { disablePaystackSubscription } = await import(
                  "../lib/paystackSubscriptions"
                );
                await disablePaystackSubscription(
                  created.subscription_code,
                  created.email_token,
                );
              } catch {
                await db
                  .update(driverSubscriptions)
                  .set({
                    providerSubscriptionCode: created.subscription_code,
                    providerEmailToken: created.email_token,
                    autoRenewActive: true,
                    autoRenewIssue:
                      "A renewal setup raced with a cancellation or plan change. Retry cancellation before the next payment date.",
                    updatedAt: new Date(),
                  })
                  .where(eq(driverSubscriptions.driverId, ledger.driverId));
                return true;
              }
            }
            await db
              .update(driverSubscriptions)
              .set({
                renewalMode: stillConsented ? "automatic" : "manual",
                providerSubscriptionCode: created.subscription_code,
                providerEmailToken: created.email_token,
                autoRenewActive: stillConsented,
                autoRenewIssue: null,
                nextPaymentAt: webhookDate(created.next_payment_date) ?? expiresAt,
                updatedAt: new Date(),
              })
              .where(eq(driverSubscriptions.driverId, ledger.driverId));
          } else if (failureMessage) {
            await db
              .update(driverSubscriptions)
              .set({
                renewalMode: "manual",
                autoRenewActive: false,
                autoRenewIssue: failureMessage,
                nextPaymentAt: null,
                updatedAt: new Date(),
              })
              .where(eq(driverSubscriptions.driverId, ledger.driverId));
          }
        }
        return true;
      }
    }

    if (event !== "charge.success" && event !== "transaction.success") return true;
    if (!providerSubscriptionCode || !reference) return true;
    const [current] = await db
      .select()
      .from(driverSubscriptions)
      .where(eq(driverSubscriptions.providerSubscriptionCode, providerSubscriptionCode))
      .limit(1);
    if (!current || !current.planId) return true;
    const [duplicate] = await db
      .select({ id: driverSubscriptionRecords.id })
      .from(driverSubscriptionRecords)
      .where(
        and(
          eq(driverSubscriptionRecords.provider, "paystack"),
          eq(driverSubscriptionRecords.externalReference, reference),
        ),
      )
      .limit(1);
    if (duplicate) return true;
    const [plan] = await db
      .select()
      .from(driverSubscriptionPlans)
      .where(eq(driverSubscriptionPlans.id, current.planId))
      .limit(1);
    if (!plan) return true;
    const amount = Number(data.amount);
    if (!Number.isSafeInteger(amount) || amount !== plan.amountMinor || data.currency !== plan.currency) {
      throw webhookError(400, "The renewal payment does not match the assigned plan.");
    }
    const paidAt = webhookDate(data.paid_at) ?? new Date();
    const providerNextDate = webhookDate(subscriptionData.next_payment_date);
    const expiresAt =
      providerNextDate && providerNextDate.getTime() > paidAt.getTime()
        ? providerNextDate
        : addBillingInterval(paidAt, plan.billingInterval);
    await db.transaction(async (tx) => {
      const [locked] = await tx
        .select()
        .from(driverSubscriptions)
        .where(eq(driverSubscriptions.driverId, current.driverId))
        .for("update");
      if (!locked || locked.providerSubscriptionCode !== providerSubscriptionCode) return;
      const [alreadyRecorded] = await tx
        .select({ id: driverSubscriptionRecords.id })
        .from(driverSubscriptionRecords)
        .where(
          and(
            eq(driverSubscriptionRecords.provider, "paystack"),
            eq(driverSubscriptionRecords.externalReference, reference),
          ),
        )
        .limit(1);
      if (alreadyRecorded) return;
      await tx.insert(driverSubscriptionRecords).values({
        id: randomUUID(),
        driverId: current.driverId,
        planId: plan.id,
        provider: "paystack",
        externalReference: reference,
        amountMinor: amount,
        currency: plan.currency,
        status: "active",
        metadata: { recurring_renewal: true, subscription_code: providerSubscriptionCode },
        startedAt: paidAt,
        expiresAt,
      });
      await tx
        .update(driverSubscriptions)
        .set({
          status: "active",
          amountMinor: plan.amountMinor,
          startedAt: paidAt,
          expiresAt,
          nextPaymentAt: providerNextDate,
          autoRenewActive: !locked.cancelAtPeriodEnd,
          autoRenewIssue: null,
          updatedAt: new Date(),
        })
        .where(eq(driverSubscriptions.driverId, current.driverId));
    });
    return true;
  }

  if (
    event === "invoice.payment_failed" ||
    event === "invoice.update" ||
    event === "subscription.not_renew" ||
    event === "subscription.disable"
  ) {
    if (!providerSubscriptionCode) return true;
    const [current] = await db
      .select()
      .from(driverSubscriptions)
      .where(eq(driverSubscriptions.providerSubscriptionCode, providerSubscriptionCode))
      .limit(1);
    if (!current) return true;
    if (event === "invoice.update" && data.status !== "failed") return true;
    const now = new Date();
    const hasPaidTimeLeft =
      current.expiresAt !== null && current.expiresAt.getTime() > now.getTime();
    const intentionalCancellation =
      current.cancelAtPeriodEnd || event === "subscription.not_renew";
    await db
      .update(driverSubscriptions)
      .set({
        status: hasPaidTimeLeft ? current.status : "expired",
        renewalMode: "manual",
        autoRenewActive: false,
        autoRenewIssue:
          event === "invoice.payment_failed" || event === "invoice.update"
            ? "Paystack renewal payment failed. Access remains until the paid-through date; renew manually."
            : intentionalCancellation
              ? null
              : "Paystack stopped automatic renewal. Renew manually to continue after the paid-through date.",
        providerEmailToken: null,
        nextPaymentAt: null,
        updatedAt: now,
      })
      .where(eq(driverSubscriptions.driverId, current.driverId));
    return true;
  }
  return false;
}

router.post("/payments/webhook", async (req, res, next) => {
  const secret = process.env.PAYSTACK_SECRET_KEY;
  const signature = req.get("x-paystack-signature");
  const rawBody = (req as Request & { rawBody?: Buffer }).rawBody;
  if (!secret || !signature || !rawBody) {
    return res.status(401).send("Paystack webhook signature is missing.");
  }

  const expected = createHmac("sha512", secret).update(rawBody).digest();
  const received = Buffer.from(signature, "hex");
  if (received.length !== expected.length || !timingSafeEqual(received, expected)) {
    return res.status(401).send("Paystack webhook signature is invalid.");
  }

  try {
    const payload = req.body as {
      event?: unknown;
      data?: unknown;
    };
    const event = typeof payload?.event === "string" ? payload.event : "";
    const data = webhookObject(payload?.data);
    if (await handleDriverSubscriptionWebhook(event, data)) {
      return res.sendStatus(200);
    }
    const preauthorizationData = data as PaystackPreauthorization;
    const handledEvents = new Set([
      "preauthorization.reserve.success",
      "preauthorization.reserve.failed",
      "preauthorization.capture.success",
      "preauthorization.capture.failed",
      "preauthorization.release.success",
      "preauthorization.release.failed",
    ]);
    if (!handledEvents.has(event)) return res.sendStatus(200);
    const reference =
      typeof preauthorizationData.reference === "string"
        ? preauthorizationData.reference
        : "";
    if (!reference) return res.sendStatus(200);

    const rows = await db
      .select()
      .from(ridePayments)
      .where(eq(ridePayments.reference, reference))
      .limit(1);
    const payment = rows[0];
    if (!payment || payment.provider !== "paystack") return res.sendStatus(200);

    const amount = Number(preauthorizationData.amount);
    const capturedAmount = Number(
      preauthorizationData.captured_amount ?? preauthorizationData.amount,
    );
    const currency = preauthorizationData.currency;
    if (
      currency !== payment.currency ||
      !Number.isSafeInteger(amount) ||
      amount !== payment.amountMinor
    ) {
      return res.status(400).send("The Paystack webhook does not match the ride authorization.");
    }

    const actorId = payment.riderId;
    const expiresAt = preauthorizationData.expiry_date
      ? new Date(preauthorizationData.expiry_date)
      : null;
    if (event === "preauthorization.reserve.success") {
      await savePaymentStatus(payment, "authorized", actorId, {
        authorizedAt: preauthorizationData.created_at
          ? new Date(preauthorizationData.created_at)
          : new Date(),
        expiresAt: expiresAt && Number.isFinite(expiresAt.getTime()) ? expiresAt : null,
      }, ["pending", "failed", "authorized"]);
      const currentPayment = await loadPaymentById(payment.id);
      if (
        currentPayment &&
        ["authorized", "release_pending", "release_failed"].includes(currentPayment.status)
      ) {
        const rideRows = await db
          .select()
          .from(rideRequests)
          .where(eq(rideRequests.id, payment.requestId))
          .limit(1);
        if (rideRows[0] && ["cancelled", "unfulfilled"].includes(rideRows[0].status)) {
          await releaseRidePaymentForCancellation(payment.requestId, actorId, payment.id);
        }
      }
    } else if (event === "preauthorization.reserve.failed") {
      await savePaymentStatus(payment, "failed", actorId, {}, ["pending", "failed"]);
    } else if (event === "preauthorization.capture.success") {
      if (!Number.isSafeInteger(capturedAmount) || capturedAmount < 0 || capturedAmount > payment.amountMinor) {
        return res.status(400).send("The captured amount is outside the authorized ride fare.");
      }
      await savePaymentStatus(payment, "paid", actorId, {
        capturedAmountMinor: capturedAmount,
        paidAt: preauthorizationData.captured_at
          ? new Date(preauthorizationData.captured_at)
          : new Date(),
      }, ["authorized", "capture_pending", "capture_failed"]);
    } else if (event === "preauthorization.capture.failed") {
      await savePaymentStatus(payment, "capture_failed", actorId, {}, [
        "authorized",
        "capture_pending",
        "capture_failed",
      ]);
    } else if (event === "preauthorization.release.success") {
      await savePaymentStatus(payment, "released", actorId, {}, [
        "authorized",
        "release_pending",
        "release_failed",
      ]);
    } else if (event === "preauthorization.release.failed") {
      await savePaymentStatus(payment, "release_failed", actorId, {}, [
        "authorized",
        "release_pending",
        "release_failed",
      ]);
    }
    return res.sendStatus(200);
  } catch (error) {
    return next(error);
  }
});

router.post("/payments/initialize", async (req, res, next) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });

  try {
    if ((await getWayfareRole(userId)) !== "rider") {
      return res.status(403).json({ error: "Only riders can pay for trips." });
    }

    const body = recordBody(req.body);
    const tripId = typeof body.tripId === "string" ? body.tripId : "";
    if (!tripId) return res.status(400).json({ error: "A ride ID is required." });

    const provider = providerMode();
    const rows = await db
      .select()
      .from(rideRequests)
      .where(and(eq(rideRequests.id, tripId), eq(rideRequests.riderId, userId)))
      .limit(1);
    const trip = rows[0];
    if (!trip) return res.status(404).json({ error: "Ride not found." });
    if (!["accepted", "arrived", "in_progress"].includes(trip.status) || !trip.driverId) {
      return res.status(409).json({ error: "Choose a driver before authorizing the ride fare." });
    }
    if (
      trip.pickupAt &&
      trip.pickupAt.getTime() - Date.now() > HOLD_WINDOW_MS
    ) {
      const availableAt = new Date(trip.pickupAt.getTime() - HOLD_WINDOW_MS);
      return res.status(409).json({
        error: `Card authorization opens on ${availableAt.toLocaleString("en-ZA", { timeZone: "Africa/Johannesburg" })}, within seven days of pickup.`,
        authorizationAvailableAt: availableAt.toISOString(),
      });
    }
    if (trip.currency !== CURRENCY) {
      return res.status(400).json({ error: `Card preauthorization currently supports ${CURRENCY} only.` });
    }

    const amountMinor = Math.round(trip.fare * 100);
    if (!Number.isSafeInteger(amountMinor) || amountMinor < 100) {
      return res.status(400).json({ error: "The final ride fare must be at least R1.00." });
    }

    let callbackUrl: string | undefined;
    if (provider === "paystack") {
      const candidate = getPaystackCallbackUrl();
      if (!candidate || !isPaystackCallbackUrl(candidate)) {
        return res.status(400).json({
          error: "PAYSTACK_CALLBACK_URL must be a secure HTTPS URL ending in /api/payments/callback.",
        });
      }
      callbackUrl = candidate;
      if (!process.env.PAYSTACK_SECRET_KEY) {
        return res.status(503).json({ error: "Paystack is not configured on the server yet." });
      }
    }

    const previousRows = await db
      .select()
      .from(ridePayments)
      .where(and(eq(ridePayments.requestId, trip.id), eq(ridePayments.riderId, userId)))
      .orderBy(desc(ridePayments.createdAt))
      .limit(1);
    let previous = previousRows[0];
    if (previous?.status === "paid") {
      return res.json({
        provider: previous.provider,
        mode: provider,
        payment: paymentSummary(previous),
        authorizationUrl: null,
      });
    }
    if (previous?.status === "authorized") {
      if (!previous.expiresAt || previous.expiresAt.getTime() > Date.now()) {
        return res.json({
          provider: previous.provider,
          mode: provider,
          payment: paymentSummary(previous),
          authorizationUrl: null,
        });
      }
      if (previous.provider === "mock") {
        await savePaymentStatus(previous, "expired", userId);
      } else {
        const data = await verifyPaystackPreauthorization(previous.reference);
        previous = await saveVerifiedPreauthorization(previous, data, userId);
      }
    }
    if (previous && ["capture_pending", "release_pending"].includes(previous.status)) {
      return res.json({
        provider: previous.provider,
        mode: provider,
        payment: paymentSummary(previous),
        authorizationUrl: null,
      });
    }
    if (previous?.status === "pending") {
      if (previous.provider !== provider || previous.amountMinor !== amountMinor) {
        return res.status(409).json({
          error: "A different payment attempt is still pending. Finish or close it before retrying.",
        });
      }
      return res.json({
        provider,
        mode: provider,
        payment: paymentSummary(previous),
        authorizationUrl: previous.authorizationUrl,
      });
    }
    if (previous?.status === "paid") {
      return res.json({
        provider: previous.provider,
        mode: provider,
        payment: paymentSummary(previous),
        authorizationUrl: null,
      });
    }

    const { email } = await syncWayfareUser(userId);
    if (provider === "paystack" && !email) {
      return res.status(400).json({
        error: "A verified account email is required for card checkout.",
      });
    }

    const reference = `vh-${randomUUID()}`;
    const paymentId = randomUUID();
    const now = new Date();
    // Platform commission: 20% of the fare (market-configurable)
    const commissionMinor = Math.round((amountMinor * 20) / 100);
    const inserted = await db
      .insert(ridePayments)
      .values({
        id: paymentId,
        requestId: trip.id,
        riderId: userId,
        driverId: trip.driverId ?? null,
        provider,
        reference,
        amountMinor,
        commissionMinor,
        paymentMethod: "card",
        currency: CURRENCY,
        status: "pending",
        createdAt: now,
        updatedAt: now,
      })
      .onConflictDoNothing()
      .returning();
    if (!inserted[0]) {
      const concurrentRows = await db
        .select()
        .from(ridePayments)
        .where(and(eq(ridePayments.requestId, trip.id), eq(ridePayments.riderId, userId)))
        .orderBy(desc(ridePayments.createdAt))
        .limit(1);
      const concurrentPayment = concurrentRows[0];
      if (
        concurrentPayment?.status === "pending" &&
        concurrentPayment.provider === provider &&
        (provider === "mock" || concurrentPayment.authorizationUrl)
      ) {
        return res.json({
          provider,
          mode: provider,
          payment: paymentSummary(concurrentPayment),
          authorizationUrl: concurrentPayment.authorizationUrl,
        });
      }
      return res.status(409).json({
        error: "A payment attempt is being prepared. Wait a moment and retry.",
      });
    }

    if (provider === "mock") {
      const payment = await loadPaymentForRider(reference, userId);
      if (!payment) throw new Error("The mock payment record could not be loaded.");
      return res.status(201).json({
        provider,
        mode: provider,
        payment: paymentSummary(payment),
        authorizationUrl: null,
      });
    }

    try {
      const response = await fetch(`${PAYSTACK_API}/preauthorization/initialize`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${process.env.PAYSTACK_SECRET_KEY}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          email,
          amount: String(amountMinor),
          currency: CURRENCY,
          reference,
          callback_url: callbackUrl,
          expire_after_days: MAX_HOLD_DAYS,
          expire_action: "release",
          metadata: JSON.stringify({ vhaya_trip_id: trip.id, vhaya_rider_id: userId }),
        }),
        signal: AbortSignal.timeout(12_000),
      });
      const payload = (await response.json()) as {
        status?: boolean;
        message?: string;
        data?: { authorization_url?: string; reference?: string };
      };
      const authorizationUrl = payload.data?.authorization_url;
      if (
        !response.ok ||
        payload.status !== true ||
        typeof authorizationUrl !== "string" ||
        !authorizationUrl.startsWith("https://checkout.paystack.com/preauthorization/") ||
        payload.data?.reference !== reference
      ) {
        throw new Error(payload.message || "Paystack did not return a valid card authorization link.");
      }
      await db
        .update(ridePayments)
        .set({ authorizationUrl, updatedAt: new Date() })
        .where(eq(ridePayments.id, paymentId));
      const payment = await loadPaymentForRider(reference, userId);
      if (!payment) throw new Error("The Paystack payment record could not be loaded.");
      return res.status(201).json({
        provider,
        mode: provider,
        payment: paymentSummary(payment),
        authorizationUrl,
      });
    } catch (error) {
      await db
        .update(ridePayments)
        .set({ status: "failed", updatedAt: new Date() })
        .where(eq(ridePayments.id, paymentId));
      throw Object.assign(
        new Error(
          error instanceof Error && error.name === "TimeoutError"
            ? "Paystack card authorization timed out. Please try again."
            : error instanceof Error
              ? error.message
              : "Paystack card authorization could not be started.",
        ),
        { status: 502 },
      );
    }
  } catch (error) {
    const status = statusError(error);
    if (status >= 400 && status < 600) {
      return res.status(status).json({ error: error instanceof Error ? error.message : "Payment setup failed." });
    }
    return next(error);
  }
});

router.post("/payments/verify", async (req, res, next) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });

  try {
    if ((await getWayfareRole(userId)) !== "rider") {
      return res.status(403).json({ error: "Only riders can verify ride payments." });
    }
    const body = recordBody(req.body);
    const reference = typeof body.reference === "string" ? body.reference : "";
    if (!reference) return res.status(400).json({ error: "A payment reference is required." });

    const payment = await loadPaymentForRider(reference, userId);
    if (!payment) return res.status(404).json({ error: "Payment not found." });
    if (payment.provider !== "paystack") {
      return res.status(409).json({ error: "Use the mock payment controls for this test payment." });
    }
    if (["authorized", "paid", "released", "expired"].includes(payment.status)) {
      return res.json({
        payment: paymentSummary(payment),
        verified: true,
        authorized: payment.status === "authorized",
      });
    }
    const data = await verifyPaystackPreauthorization(payment.reference);
    const updated = await saveVerifiedPreauthorization(payment, data, userId);
    const verified = ["authorized", "paid", "released", "expired"].includes(updated.status);
    return res.status(verified ? 200 : 202).json({
      payment: paymentSummary(updated),
      verified,
      authorized: updated.status === "authorized",
      error:
        updated.status === "failed"
          ? "Paystack reports that the card authorization did not complete."
          : undefined,
    });
  } catch (error) {
    const status = statusError(error);
    if (status >= 400 && status < 600) {
      return res.status(status).json({ error: error instanceof Error ? error.message : "Payment verification failed." });
    }
    return next(error);
  }
});

router.post("/payments/capture", async (req, res, next) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });

  try {
    if ((await getWayfareRole(userId)) !== "rider") {
      return res.status(403).json({ error: "Only riders can retry a completed ride capture." });
    }
    const body = recordBody(req.body);
    const tripId = typeof body.tripId === "string" ? body.tripId : "";
    const rows = await db
      .select()
      .from(rideRequests)
      .where(and(eq(rideRequests.id, tripId), eq(rideRequests.riderId, userId)))
      .limit(1);
    if (!rows[0]) return res.status(404).json({ error: "Ride not found." });
    if (rows[0].status !== "completed") {
      return res.status(409).json({ error: "The ride must be completed before the fare is captured." });
    }
    const payment = await captureRidePaymentForCompletion(tripId, userId);
    if (!payment) return res.status(404).json({ error: "No card authorization was found for this ride." });
    return res.json({ payment: paymentSummary(payment), captured: payment.status === "paid" });
  } catch (error) {
    const status = statusError(error);
    if (status >= 400 && status < 600) {
      return res.status(status).json({ error: error instanceof Error ? error.message : "Capture failed." });
    }
    return next(error);
  }
});

router.post("/payments/release", async (req, res, next) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });

  try {
    if ((await getWayfareRole(userId)) !== "rider") {
      return res.status(403).json({ error: "Only riders can retry a cancelled ride hold release." });
    }
    const body = recordBody(req.body);
    const tripId = typeof body.tripId === "string" ? body.tripId : "";
    const rows = await db
      .select()
      .from(rideRequests)
      .where(and(eq(rideRequests.id, tripId), eq(rideRequests.riderId, userId)))
      .limit(1);
    if (!rows[0]) return res.status(404).json({ error: "Ride not found." });
    if (!["cancelled", "unfulfilled"].includes(rows[0].status)) {
      return res.status(409).json({ error: "The card hold can only be released for a cancelled ride." });
    }
    const payment = await releaseRidePaymentForCancellation(tripId, userId);
    if (!payment) return res.status(404).json({ error: "No card authorization was found for this ride." });
    return res.json({ payment: paymentSummary(payment), released: payment.status === "released" });
  } catch (error) {
    const status = statusError(error);
    if (status >= 400 && status < 600) {
      return res.status(status).json({ error: error instanceof Error ? error.message : "Release failed." });
    }
    return next(error);
  }
});
function currentDriverSubscriptionView(
  row: typeof driverSubscriptions.$inferSelect,
  planName: string | null,
) {
  return {
    driverId: row.driverId,
    planId: row.planId,
    planName,
    status: row.status,
    renewalMode: row.renewalMode,
    autoRenewActive: row.autoRenewActive,
    autoRenewIssue: row.autoRenewIssue,
    cancelAtPeriodEnd: row.cancelAtPeriodEnd,
    amountMinor: row.amountMinor,
    currency: row.currency,
    provider: row.provider,
    startedAt: row.startedAt?.getTime() ?? null,
    expiresAt: row.expiresAt?.getTime() ?? null,
    nextPaymentAt: row.nextPaymentAt?.getTime() ?? null,
    autoRenewConsentAt: row.autoRenewConsentAt?.getTime() ?? null,
  };
}

router.post("/driver/subscription/initialize", async (req, res, next) => {
  const userId = getWayfareUserId(req);
  if (!userId) {
    res.status(401).json({ error: "Sign in to continue." });
    return;
  }
  try {
    if ((await getWayfareRole(userId)) !== "driver") {
      res.status(403).json({ error: "Only drivers can initialize subscription payments." });
      return;
    }
    const parsed = InitializeDriverSubscriptionBody.safeParse(req.body);
    if (!parsed.success) {
      res.status(400).json({ error: parsed.error.message });
      return;
    }
    const input = parsed.data;
    const [plan] = await db
      .select()
      .from(driverSubscriptionPlans)
      .where(eq(driverSubscriptionPlans.id, input.planId))
      .limit(1);
    if (!plan || !plan.isActive) {
      res.status(404).json({ error: "This subscription plan is not available." });
      return;
    }
    if (plan.renewalMode === "automatic" && input.autoRenewConsent !== true) {
      res.status(400).json({
        error: "Confirm automatic renewal before starting checkout for this plan.",
      });
      return;
    }

    const provider = providerMode();
    const [previousSubscription] = await db
      .select()
      .from(driverSubscriptions)
      .where(eq(driverSubscriptions.driverId, userId))
      .limit(1);
    if (previousSubscription?.autoRenewActive) {
      res.status(409).json({
        error: "Stop the existing automatic renewal before starting another subscription.",
      });
      return;
    }
    const [user] = await db
      .select()
      .from(wayfareUsers)
      .where(eq(wayfareUsers.id, userId))
      .limit(1);
    if (provider === "paystack" && !user?.email) {
      res.status(400).json({
        error: "A verified account email is required for Paystack checkout.",
      });
      return;
    }

    let promo: Awaited<ReturnType<typeof findEligibleDriverPromotion>>["promotion"] | null =
      null;
    let discountMinor = 0;
    if (input.promotionCode) {
      const selected = await findEligibleDriverPromotion({
        driverId: userId,
        planId: plan.id,
        code: input.promotionCode,
        amountMinor: plan.amountMinor,
      });
      promo = selected.promotion;
      discountMinor = selected.discountMinor;
    }
    const amountMinor = plan.amountMinor - discountMinor;
    if (amountMinor === 0 && provider === "paystack" && plan.renewalMode === "automatic") {
      res.status(400).json({
        error: "A fully discounted checkout cannot start automatic renewal; choose a manual plan.",
      });
      return;
    }

    let providerPlanCode = plan.paystackPlanCode;
    if (provider === "paystack" && plan.renewalMode === "automatic" && !providerPlanCode) {
      providerPlanCode = await createPaystackPlan({
        name: plan.name,
        description: plan.description,
        amountMinor: plan.amountMinor,
        currency: plan.currency,
        interval: plan.billingInterval,
      });
      await db
        .update(driverSubscriptionPlans)
        .set({ paystackPlanCode: providerPlanCode, updatedAt: new Date() })
        .where(
          and(
            eq(driverSubscriptionPlans.id, plan.id),
            isNull(driverSubscriptionPlans.paystackPlanCode),
          ),
        );
    }

    const now = new Date();
    const reference = `driver-subscription-${userId}-${randomUUID()}`;
    const expiresAt = addBillingInterval(now, plan.billingInterval);
    const consentAt =
      provider === "paystack" && plan.renewalMode === "automatic" ? now : null;
    const effectiveRenewalMode =
      provider === "paystack" ? plan.renewalMode : "manual";
    const autoRenewIssue =
      provider === "mock" && plan.renewalMode === "automatic"
        ? "Automatic renewal is unavailable in mock payment mode. Renew manually."
        : null;
    const ledgerProvider =
      amountMinor === 0 ? "promo" : provider;
    const ledgerStatus: "pending" | "active" =
      provider === "paystack" && amountMinor > 0 ? "pending" : "active";
    const ledgerId = randomUUID();

    const created = await db.transaction(async (tx) => {
      await tx
        .select({ id: wayfareUsers.id })
        .from(wayfareUsers)
        .where(eq(wayfareUsers.id, userId))
        .for("update");
      const [current] = await tx
        .select()
        .from(driverSubscriptions)
        .where(eq(driverSubscriptions.driverId, userId))
        .limit(1)
        .for("update");
      const currentStatus = current
        ? current.status === "active" &&
          current.expiresAt &&
          current.expiresAt.getTime() <= now.getTime()
          ? "expired"
          : current.status
        : null;
      if (currentStatus === "active" || currentStatus === "pending") {
        throw Object.assign(
          new Error("Driver already has an active or pending subscription."),
          { status: 409 },
        );
      }
      if (current?.autoRenewActive) {
        throw Object.assign(
          new Error("Stop the existing automatic renewal before starting another subscription."),
          { status: 409 },
        );
      }
      if (!current) {
        const [legacy] = await tx
          .select()
          .from(driverSubscriptionRecords)
          .where(
            and(
              eq(driverSubscriptionRecords.driverId, userId),
              eq(driverSubscriptionRecords.status, "active"),
            ),
          )
          .orderBy(desc(driverSubscriptionRecords.startedAt))
          .limit(1);
        if (!legacy || !legacy.expiresAt || legacy.expiresAt.getTime() > now.getTime()) {
          if (legacy) {
            throw Object.assign(
              new Error("An existing subscription must expire or be resolved before checkout."),
              { status: 409 },
            );
          }
        }
      }

      if (promo) {
        const [lockedPromo] = await tx
          .select()
          .from(driverPromotions)
          .where(eq(driverPromotions.id, promo.id))
          .limit(1)
          .for("update");
        if (!lockedPromo || !lockedPromo.isActive || now < lockedPromo.startsAt || now >= lockedPromo.endsAt) {
          throw Object.assign(new Error("This promotion is no longer available."), {
            status: 409,
          });
        }
        const [usage] = await tx
          .select({ count: sql<number>`count(*)::int` })
          .from(driverPromotionRedemptions)
          .where(
            and(
              eq(driverPromotionRedemptions.promotionId, promo.id),
              inArray(driverPromotionRedemptions.status, ["pending", "redeemed"]),
            ),
          );
        if (
          lockedPromo.maxRedemptions !== null &&
          (usage?.count ?? 0) >= lockedPromo.maxRedemptions
        ) {
          throw Object.assign(new Error("This promotion has reached its usage limit."), {
            status: 409,
          });
        }
        const [existingRedemption] = await tx
          .select()
          .from(driverPromotionRedemptions)
          .where(
            and(
              eq(driverPromotionRedemptions.promotionId, promo.id),
              eq(driverPromotionRedemptions.driverId, userId),
            ),
          )
          .limit(1)
          .for("update");
        if (existingRedemption?.status === "pending" || existingRedemption?.status === "redeemed") {
          throw Object.assign(new Error("You have already used this promotion."), {
            status: 409,
          });
        }
        if (existingRedemption) {
          await tx
            .update(driverPromotionRedemptions)
            .set({
              externalReference: reference,
              status: amountMinor === 0 || provider === "mock" ? "redeemed" : "pending",
              discountMinor,
              createdAt: now,
              redeemedAt: amountMinor === 0 || provider === "mock" ? now : null,
            })
            .where(eq(driverPromotionRedemptions.id, existingRedemption.id));
        } else {
          await tx.insert(driverPromotionRedemptions).values({
            id: randomUUID(),
            promotionId: promo.id,
            driverId: userId,
            externalReference: reference,
            status: amountMinor === 0 || provider === "mock" ? "redeemed" : "pending",
            discountMinor,
            createdAt: now,
            redeemedAt: amountMinor === 0 || provider === "mock" ? now : null,
          });
        }
      }

      const metadata = {
        vhaya_driver_id: userId,
        driver_subscription_plan_id: plan.id,
        promotion_id: promo?.id ?? null,
        promotion_code: promo?.code ?? null,
        discount_minor: discountMinor,
        requested_renewal_mode: plan.renewalMode,
        auto_renew_consent: consentAt !== null,
      };
      await tx.insert(driverSubscriptionRecords).values({
        id: ledgerId,
        driverId: userId,
        planId: plan.id,
        provider: ledgerProvider,
        externalReference: reference,
        amountMinor,
        currency: plan.currency,
        status: ledgerStatus,
        startedAt: now,
        expiresAt: ledgerStatus === "active" ? expiresAt : null,
        metadata,
      });
      const currentValues = {
        driverId: userId,
        planId: plan.id,
        status: ledgerStatus,
        renewalMode: effectiveRenewalMode,
        amountMinor,
        currency: plan.currency,
        provider: ledgerProvider,
        startedAt: ledgerStatus === "active" ? now : null,
        expiresAt: ledgerStatus === "active" ? expiresAt : null,
        nextPaymentAt: null,
        providerSubscriptionCode: current?.providerSubscriptionCode ?? null,
        providerEmailToken: current?.providerEmailToken ?? null,
        providerPlanCode: providerPlanCode,
        autoRenewConsentAt: consentAt,
        autoRenewActive: false,
        autoRenewIssue,
        cancelAtPeriodEnd: false,
        cancelledAt: null,
        cancelledReason: null,
        updatedAt: now,
      };
      if (current) {
        await tx
          .update(driverSubscriptions)
          .set(currentValues)
          .where(eq(driverSubscriptions.driverId, userId));
      } else {
        await tx.insert(driverSubscriptions).values(currentValues);
      }
      const [subscription] = await tx
        .select()
        .from(driverSubscriptions)
        .where(eq(driverSubscriptions.driverId, userId))
        .limit(1);
      return { subscription };
    });

    if (ledgerStatus === "active") {
      return res.status(201).json({
        provider: ledgerProvider,
        mode: provider,
        payment: {
          id: ledgerId,
          reference,
          amountMinor,
          currency: plan.currency,
          status: "active",
          paidAt: now.getTime(),
          expiresAt: expiresAt.getTime(),
          createdAt: now.getTime(),
        },
        subscription: currentDriverSubscriptionView(created.subscription, plan.name),
        authorizationUrl: null,
      });
    }

    try {
      const payment = await paystackRequest<{
        authorization_url?: string;
        reference?: string;
      }>("/transaction/initialize", {
        email: user?.email ?? "",
        amount: String(amountMinor),
        currency: plan.currency,
        reference,
        callback_url: getPaystackCallbackUrl(),
        metadata: {
          vhaya_driver_id: userId,
          driver_subscription_record_id: ledgerId,
          driver_subscription_plan_id: plan.id,
          promotion_id: promo?.id ?? null,
        },
      });
      if (!payment.authorization_url || payment.reference !== reference) {
        throw Object.assign(
          new Error("Paystack did not return the expected checkout reference."),
          { status: 502 },
        );
      }
      return res.status(201).json({
        provider: "paystack",
        mode: "paystack",
        payment: {
          id: ledgerId,
          reference,
          amountMinor,
          currency: plan.currency,
          status: "pending",
          paidAt: null,
          expiresAt: null,
          createdAt: now.getTime(),
        },
        subscription: currentDriverSubscriptionView(created.subscription, plan.name),
        authorizationUrl: payment.authorization_url,
      });
    } catch (error) {
      await db.transaction(async (tx) => {
        await tx
          .update(driverSubscriptionRecords)
          .set({ status: "expired" })
          .where(eq(driverSubscriptionRecords.id, ledgerId));
        await tx
          .update(driverSubscriptions)
          .set({
            status: "expired",
            autoRenewActive: false,
            autoRenewIssue: "Checkout did not start. Please try again.",
            updatedAt: new Date(),
          })
          .where(eq(driverSubscriptions.driverId, userId));
        if (promo) {
          await tx
            .update(driverPromotionRedemptions)
            .set({ status: "released" })
            .where(eq(driverPromotionRedemptions.externalReference, reference));
        }
      });
      throw error;
    }
  } catch (error) {
    const status = statusError(error);
    if (status >= 400 && status < 600) {
      res.status(status).json({
        error:
          error instanceof Error
            ? error.message
            : "Subscription initialization failed.",
      });
      return;
    }
    return next(error);
  }
});

router.post("/payments/mock-result", async (req, res, next) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });

  try {
    if (providerMode() !== "mock") {
      return res.status(403).json({ error: "Mock payments are disabled outside development." });
    }
    if ((await getWayfareRole(userId)) !== "rider") {
      return res.status(403).json({ error: "Only riders can authorize ride payments." });
    }
    const body = recordBody(req.body);
    const reference = typeof body.reference === "string" ? body.reference : "";
    if (!reference || typeof body.success !== "boolean") {
      return res.status(400).json({ error: "Choose a mock payment result." });
    }

    const payment = await loadPaymentForRider(reference, userId);
    if (!payment || payment.provider !== "mock") {
      return res.status(404).json({ error: "Mock payment not found." });
    }
    if (["authorized", "paid"].includes(payment.status)) {
      return res.json({ payment: paymentSummary(payment), receipt: paymentSummary(payment) });
    }
    if (payment.status !== "pending") {
      return res.status(409).json({ error: "This mock payment attempt has already been closed." });
    }

    const rideRows = await db
      .select()
      .from(rideRequests)
      .where(eq(rideRequests.id, payment.requestId))
      .limit(1);
    const rideCancelled =
      rideRows[0] && ["cancelled", "unfulfilled"].includes(rideRows[0].status);
    const nextStatus: PaymentStatus = body.success
      ? rideCancelled
        ? "released"
        : "authorized"
      : "failed";
    await savePaymentStatus(payment, nextStatus, userId, {
      authorizedAt: body.success && !rideCancelled ? new Date() : undefined,
      expiresAt: body.success && !rideCancelled ? new Date(Date.now() + HOLD_WINDOW_MS) : undefined,
    });
    const updated = await loadPaymentForRider(reference, userId);
    if (!updated) throw new Error("The mock payment record could not be loaded.");
    return res.json({
      payment: paymentSummary(updated),
      receipt: null,
    });
  } catch (error) {
    const status = statusError(error);
    if (status >= 400 && status < 600) {
      return res.status(status).json({ error: error instanceof Error ? error.message : "Mock payment failed." });
    }
    return next(error);
  }
});

const refundableStatuses = ["paid"] as const;
type RefundStatus = (typeof refundableStatuses)[number];

router.get("/driver/earnings", async (req, res, next) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });
  try {
    const role = await getWayfareRole(userId);
    const query = recordBody(req.query);
    const requestedDriverId =
      typeof query.driverId === "string" ? query.driverId : "";
    const targetDriverId =
      role === "admin" && requestedDriverId
        ? requestedDriverId
        : userId;
    if (role !== "admin" && targetDriverId !== userId) {
      return res
        .status(403)
        .json({ error: "You can only view your own earnings." });
    }

    const completedRides = await db
      .select({
        id: rideRequests.id,
        status: rideRequests.status,
        fare: rideRequests.fare,
        currency: rideRequests.currency,
        pickup: rideRequests.pickup,
        destination: rideRequests.destination,
        createdAt: rideRequests.createdAt,
      })
      .from(rideRequests)
      .where(
        and(
          eq(rideRequests.driverId, targetDriverId),
          eq(rideRequests.status, "completed"),
        ),
      )
      .orderBy(desc(rideRequests.createdAt));

    const rideIds = completedRides.map((ride) => ride.id);
    const payments =
      rideIds.length > 0
        ? await db
            .select()
            .from(ridePayments)
            .where(inArray(ridePayments.requestId, rideIds))
        : [];
    const paymentByRide = new Map(
      payments.map((payment) => [payment.requestId, payment]),
    );

    let estimatedEarningsMinor = 0;
    let paidEarningsMinor = 0;
    let totalCommissionMinor = 0;
    let totalNetMinor = 0;
    const records = completedRides.map((ride) => {
      const payment = paymentByRide.get(ride.id);
      const fareMinor = Math.round(ride.fare * 100);
      const paid =
        payment && payment.status === "paid"
          ? payment.capturedAmountMinor ?? payment.amountMinor
          : 0;
      const commissionMinor = payment?.commissionMinor ?? 0;
      const netMinor = paid - commissionMinor;
      estimatedEarningsMinor += fareMinor;
      paidEarningsMinor += paid;
      totalCommissionMinor += commissionMinor;
      totalNetMinor += netMinor;
      return {
        rideId: ride.id,
        pickup: ride.pickup,
        destination: ride.destination,
        fareMinor,
        currency: ride.currency,
        completedAt: ride.createdAt.getTime(),
        commissionMinor,
        netMinor,
        payment: payment
          ? {
              id: payment.id,
              provider: payment.provider,
              reference: payment.reference,
              paymentMethod: payment.paymentMethod,
              status: payment.status as string,
              capturedAmountMinor: payment.capturedAmountMinor ?? null,
              paidAt: payment.paidAt?.getTime() ?? null,
            }
          : null,
        paidMinor: paid,
      };
    });

    return res.json({
      driverId: targetDriverId,
      currency: completedRides[0]?.currency ?? null,
      estimatedEarningsMinor,
      paidEarningsMinor,
      totalCommissionMinor,
      totalNetMinor,
      outstandingEarningsMinor:
        estimatedEarningsMinor - paidEarningsMinor,
      rideCount: completedRides.length,
      paidRideCount: records.filter((record) => record.paidMinor > 0).length,
      records,
    });
  } catch (error) {
    return next(error);
  }
});

router.post("/admin/payments/:paymentId/refund", async (req, res, next) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });
  if ((await getWayfareRole(userId)) !== "admin") {
    return res
      .status(403)
      .json({ error: "Operations admin access is required." });
  }
  try {
    const paymentId = typeof req.params.paymentId === "string"
      ? req.params.paymentId
      : "";
    if (!paymentId) {
      return res.status(400).json({ error: "A payment ID is required." });
    }
    const body = recordBody(req.body);
    const reason = typeof body.reason === "string" ? body.reason.trim() : "";
    if (!reason || reason.length > 500) {
      return res.status(400).json({
        error: "Enter a refund reason (500 characters or fewer).",
      });
    }

    const [payment] = await db
      .select()
      .from(ridePayments)
      .where(eq(ridePayments.id, paymentId))
      .limit(1);
    if (!payment) {
      return res.status(404).json({ error: "Payment was not found." });
    }
    if (!refundableStatuses.includes(payment.status as RefundStatus)) {
      return res.status(409).json({
        error: "Only captured (paid) ride payments can be refunded.",
      });
    }

    const now = new Date();
    const refundId = randomUUID();
    await db.insert(rideEvents).values({
      requestId: payment.requestId,
      actorId: userId,
      action: "refund_requested",
      details: {
        refundId,
        paymentId: payment.id,
        amountMinor:
          payment.capturedAmountMinor ?? payment.amountMinor,
        currency: payment.currency,
        reason,
        requestedAt: now.getTime(),
      },
    });

    return res.status(202).json({
      refund: {
        id: refundId,
        paymentId: payment.id,
        requestId: payment.requestId,
        amountMinor:
          payment.capturedAmountMinor ?? payment.amountMinor,
        currency: payment.currency,
        status: "requested",
        reason,
        requestedBy: userId,
        requestedAt: now.getTime(),
      },
      payment: {
        id: payment.id,
        status: payment.status as string,
        note:
          "Refund requested. Funds are not returned until the payment provider confirms the refund; this record alone does not claim money was returned.",
      },
    });
  } catch (error) {
    return next(error);
  }
});

/**
 * GET /trips/:id/receipt
 * Returns a per-trip receipt for the rider or driver involved in the trip.
 * Includes fare, commission, payment method, driver details, and payout status.
 */
router.get("/trips/:tripId/receipt", async (req, res, next) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });
  try {
    const tripId = typeof req.params.tripId === "string" ? req.params.tripId : "";
    const [trip] = await db
      .select()
      .from(rideRequests)
      .where(eq(rideRequests.id, tripId))
      .limit(1);
    if (!trip) return res.status(404).json({ error: "Trip was not found." });
    if (trip.riderId !== userId && trip.driverId !== userId) {
      return res.status(403).json({
        error: "You can only view receipts for trips you participated in.",
      });
    }

    const [payment] = await db
      .select()
      .from(ridePayments)
      .where(eq(ridePayments.requestId, tripId))
      .orderBy(desc(ridePayments.createdAt))
      .limit(1);

    const [driver] = trip.driverId
      ? await db
          .select({ id: wayfareUsers.id, displayName: wayfareUsers.displayName })
          .from(wayfareUsers)
          .where(eq(wayfareUsers.id, trip.driverId))
          .limit(1)
      : [];

    const receipt = {
      tripId: trip.id,
      status: trip.status,
      pickup: trip.pickup,
      destination: trip.destination,
      fareMinor: Math.round(trip.fare * 100),
      currency: trip.currency,
      createdAt: trip.createdAt.getTime(),
      driver: driver
        ? { id: driver.id, displayName: driver.displayName }
        : null,
      payment: payment
        ? {
            id: payment.id,
            reference: payment.reference,
            provider: payment.provider,
            paymentMethod: payment.paymentMethod,
            amountMinor: payment.capturedAmountMinor ?? payment.amountMinor,
            commissionMinor: payment.commissionMinor,
            netMinor:
              (payment.capturedAmountMinor ?? payment.amountMinor) -
              payment.commissionMinor,
            status: payment.status,
            paidAt: payment.paidAt?.getTime() ?? null,
            driverPayoutId: payment.driverPayoutId ?? null,
          }
        : null,
    };

    return res.json({ receipt });
  } catch (error) {
    return next(error);
  }
});

export default router;

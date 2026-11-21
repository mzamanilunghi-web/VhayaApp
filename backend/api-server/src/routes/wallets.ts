import { createHash, randomBytes, randomUUID } from "node:crypto";
import { and, desc, eq, inArray, isNull, or } from "drizzle-orm";
import { Router, type IRouter } from "express";
import { db } from "@workspace/db";
import {
  rideEvents,
  rideRequests,
  wayfareUsers,
  wallets,
  walletTransactions,
  walletTopups,
  walletVouchers,
} from "@workspace/db/schema";
import { getWayfareUserId } from "../middlewares/firebaseIdentity";
import { getWayfareRole } from "../middlewares/wayfareRoles";
import { recordBody } from "../lib/wayfareData";

const router: IRouter = Router();
const PAYSTACK_API = "https://api.paystack.co";
const CURRENCY = "ZAR";
const MAX_WALLET_BALANCE_MINOR = 2_147_483_647;

function statusError(error: unknown): number {
  return error && typeof error === "object" && "status" in error
    ? Number((error as { status: unknown }).status)
    : 0;
}

async function loadOrCreateWallet(userId: string) {
  let rows = await db.select().from(wallets).where(eq(wallets.userId, userId)).limit(1);
  if (rows[0]) return rows[0];
  const id = `wallet-${userId}`;
  await db.insert(wallets).values({ id, userId, balanceMinor: 0, currency: CURRENCY }).onConflictDoNothing();
  rows = await db.select().from(wallets).where(eq(wallets.userId, userId)).limit(1);
  if (!rows[0]) throw new Error("Wallet could not be created.");
  return rows[0];
}

function walletSummary(wallet: typeof wallets.$inferSelect) {
  return {
    id: wallet.id,
    userId: wallet.userId,
    balanceMinor: wallet.balanceMinor,
    currency: wallet.currency,
    createdAt: wallet.createdAt.getTime(),
    updatedAt: wallet.updatedAt.getTime(),
  };
}

function walletTransactionView(transaction: typeof walletTransactions.$inferSelect) {
  return {
    id: transaction.id,
    type: transaction.type,
    amountMinor: transaction.amountMinor,
    currency: transaction.currency,
    reference: transaction.reference,
    description: transaction.description,
    createdAt: transaction.createdAt.getTime(),
  };
}

function routeError(status: number, message: string) {
  return Object.assign(new Error(message), { status });
}

function normalizePhoneNumber(value: string): string | null {
  let normalized = value.trim().replace(/[()\s-]/g, "");
  if (normalized.startsWith("00")) normalized = `+${normalized.slice(2)}`;
  if (/^0\d{9}$/.test(normalized)) normalized = `+27${normalized.slice(1)}`;
  else if (/^27\d{9}$/.test(normalized)) normalized = `+${normalized}`;
  else if (/^\d{9}$/.test(normalized)) normalized = `+27${normalized}`;
  return /^\+[1-9]\d{7,14}$/.test(normalized) ? normalized : null;
}

function hashVoucherCode(code: string): string {
  return createHash("sha256").update(code.trim().toUpperCase()).digest("hex");
}

router.get("/wallet", async (req, res, next) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });
  try {
    const wallet = await loadOrCreateWallet(userId);
    const txs = await db
      .select()
      .from(walletTransactions)
      .where(eq(walletTransactions.userId, userId))
      .orderBy(desc(walletTransactions.createdAt))
      .limit(50);
    return res.json({
      wallet: walletSummary(wallet),
      transactions: txs.map((t) => ({
        id: t.id,
        type: t.type,
        amountMinor: t.amountMinor,
        currency: t.currency,
        reference: t.reference,
        description: t.description,
        createdAt: t.createdAt.getTime(),
      })),
    });
  } catch (error) {
    const status = statusError(error);
    if (status >= 400 && status < 600) {
      return res.status(status).json({ error: error instanceof Error ? error.message : "Wallet lookup failed." });
    }
    return next(error);
  }
});
router.post("/wallet/top-up", async (req, res, next) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });
  try {
    const body = recordBody(req.body);
    const amountMinor = Number(body.amountMinor);
    if (!Number.isSafeInteger(amountMinor) || amountMinor <= 0) {
      return res.status(400).json({ error: "Enter a positive top-up amount (minor units)." });
    }
    const secret = process.env.PAYSTACK_SECRET_KEY;
    if (!secret) {
      return res.status(503).json({ error: "Paystack is not configured on the server yet." });
    }
    const userRows = await db.select().from(wayfareUsers).where(eq(wayfareUsers.id, userId)).limit(1);
    const user = userRows[0];
    if (!user?.email) {
      return res.status(400).json({ error: "A verified account email is required for Paystack checkout." });
    }

    const reference = `wallet-topup-${userId}-${Date.now()}`;
    if (body.callbackUrl !== undefined || body.callback_url !== undefined) {
      return res.status(400).json({ error: "Custom callback URLs are not permitted." });
    }
    const configuredCallback = process.env.PAYSTACK_WALLET_CALLBACK_URL;
    let callbackUrl: string | undefined;
    if (configuredCallback) {
      try {
        const url = new URL(configuredCallback);
        if (
          url.protocol === "https:" &&
          !url.username &&
          !url.password &&
          !url.search &&
          !url.hash
        ) {
          callbackUrl = configuredCallback;
        }
      } catch {
        callbackUrl = undefined;
      }
    }

    const response = await fetch(`${PAYSTACK_API}/transaction/initialize`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${secret}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        email: user.email,
        amount: amountMinor,
        currency: CURRENCY,
        reference,
        callback_url: callbackUrl,
        metadata: { userId, purpose: "wallet_topup" },
      }),
    });
    const payload = (await response.json()) as {
      status?: boolean;
      message?: string;
      data?: { authorization_url?: string; access_code?: string; reference?: string };
    };
    if (!response.ok || !payload.status || !payload.data?.authorization_url) {
      return res.status(502).json({ error: payload.message || "Paystack could not initialize the top-up." });
    }

    await db.insert(walletTopups).values({
      id: reference,
      userId,
      provider: "paystack",
      reference,
      amountMinor,
      currency: CURRENCY,
      status: "pending",
      authorizationUrl: payload.data.authorization_url,
    });

    return res.status(201).json({
      topup: {
        id: reference,
        reference,
        amountMinor,
        currency: CURRENCY,
        status: "pending",
        authorizationUrl: payload.data.authorization_url,
      },
    });
  } catch (error) {
    const status = statusError(error);
    if (status >= 400 && status < 600) {
      return res.status(status).json({ error: error instanceof Error ? error.message : "Top-up failed." });
    }
    return next(error);
  }
});

router.get("/wallet/top-ups/:reference/verify", async (req, res, next) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });
  try {
    const reference = typeof req.params.reference === "string" ? req.params.reference : "";
    if (!reference) return res.status(400).json({ error: "A reference is required." });

    const rows = await db.select().from(walletTopups).where(eq(walletTopups.reference, reference)).limit(1);
    const topup = rows[0];
    if (!topup || topup.userId !== userId) {
      return res.status(404).json({ error: "Top-up was not found." });
    }
    if (topup.status === "paid") {
      const wallet = await loadOrCreateWallet(userId);
      return res.json({ topup: { id: topup.id, reference: topup.reference, status: topup.status }, wallet: walletSummary(wallet) });
    }

    const secret = process.env.PAYSTACK_SECRET_KEY;
    if (!secret) return res.status(503).json({ error: "Paystack is not configured on the server yet." });

    const response = await fetch(`${PAYSTACK_API}/transaction/verify/${encodeURIComponent(reference)}`, {
      headers: { Authorization: `Bearer ${secret}` },
    });
    const payload = (await response.json()) as {
      status?: boolean;
      message?: string;
      data?: { status?: string; amount?: number; currency?: string; reference?: string };
    };
    if (!response.ok || !payload.status) {
      return res.status(502).json({ error: payload.message || "Paystack verification failed." });
    }
    const data = payload.data;
    const verified = data?.status === "success";
    if (!verified) {
      await db.update(walletTopups).set({ status: "failed", updatedAt: new Date() }).where(eq(walletTopups.id, topup.id));
      return res.status(409).json({ error: "Top-up was not successful." });
    }
    if (data?.amount !== topup.amountMinor || data?.currency !== topup.currency) {
      await db.update(walletTopups).set({ status: "failed", updatedAt: new Date() }).where(eq(walletTopups.id, topup.id));
      return res.status(409).json({ error: "Top-up amount or currency mismatch." });
    }

    await db.transaction(async (tx) => {
      const current = await tx.select().from(walletTopups).where(eq(walletTopups.id, topup.id)).limit(1).for("update");
      if (!current[0] || current[0].status === "paid") return;
      await tx.update(walletTopups).set({ status: "paid", updatedAt: new Date() }).where(eq(walletTopups.id, topup.id));
      const wallet = await loadOrCreateWallet(userId);
      await tx
        .update(wallets)
        .set({ balanceMinor: wallet.balanceMinor + topup.amountMinor, updatedAt: new Date() })
        .where(eq(wallets.id, wallet.id));
      await tx.insert(walletTransactions).values({
        id: `wtx-${reference}`,
        walletId: wallet.id,
        userId,
        type: "credit",
        amountMinor: topup.amountMinor,
        currency: topup.currency,
        reference,
        description: "Wallet top-up via Paystack",
      });
    });

    const wallet = await loadOrCreateWallet(userId);
    return res.json({ topup: { id: topup.id, reference, status: "paid" }, wallet: walletSummary(wallet) });
  } catch (error) {
    const status = statusError(error);
    if (status >= 400 && status < 600) {
      return res.status(status).json({ error: error instanceof Error ? error.message : "Verification failed." });
    }
    return next(error);
  }
});

router.post("/wallet/cash-change-credit", async (req, res, next) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });
  if ((await getWayfareRole(userId)) !== "driver") {
    return res.status(403).json({ error: "Only the assigned driver can credit cash change." });
  }

  const body = recordBody(req.body);
  const tripId = typeof body.tripId === "string" ? body.tripId.trim() : "";
  const amountMinor = body.amountMinor;
  const note =
    body.note === undefined || body.note === null
      ? null
      : typeof body.note === "string"
        ? body.note.trim().slice(0, 120) || null
        : undefined;
  if (
    !tripId ||
    tripId.length > 128 ||
    typeof amountMinor !== "number" ||
    !Number.isSafeInteger(amountMinor) ||
    amountMinor < 100 ||
    amountMinor > 50_000 ||
    note === undefined
  ) {
    return res.status(400).json({
      error: "Enter a valid completed cash trip and change amount from R 1 to R 500.",
    });
  }

  try {
    let result:
      | {
          wallet: ReturnType<typeof walletSummary>;
          riderWallet: ReturnType<typeof walletSummary>;
          transactions: ReturnType<typeof walletTransactionView>[];
          creditedAmountMinor: number;
          tripId: string;
        }
      | undefined;

    await db.transaction(async (tx) => {
      const [trip] = await tx
        .select()
        .from(rideRequests)
        .where(eq(rideRequests.id, tripId))
        .limit(1)
        .for("update");
      if (!trip) throw routeError(404, "This ride could not be found.");
      if (trip.driverId !== userId) {
        throw routeError(403, "Only the assigned driver can credit this ride.");
      }
      if (
        trip.status !== "completed" ||
        trip.tripData.paymentMethod !== "cash" ||
        trip.riderId === userId
      ) {
        throw routeError(409, "Cash change can only be credited for your completed cash rides.");
      }

      const riderTransactionId = `wtx-cash-rider-${trip.id}`;
      const driverTransactionId = `wtx-cash-driver-${trip.id}`;
      const [existing] = await tx
        .select()
        .from(walletTransactions)
        .where(eq(walletTransactions.id, riderTransactionId))
        .limit(1);
      if (existing) {
        if (existing.amountMinor !== amountMinor) {
          throw routeError(409, "Cash change has already been credited for this ride.");
        }
      } else {
        for (const walletUserId of [trip.riderId, userId].sort()) {
          await tx
            .insert(wallets)
            .values({
              id: `wallet-${walletUserId}`,
              userId: walletUserId,
              balanceMinor: 0,
              currency: CURRENCY,
            })
            .onConflictDoNothing();
        }
        const lockedWallets = await tx
          .select()
          .from(wallets)
          .where(inArray(wallets.userId, [trip.riderId, userId]))
          .orderBy(wallets.userId)
          .for("update");
        const riderWallet = lockedWallets.find((wallet) => wallet.userId === trip.riderId);
        const driverWallet = lockedWallets.find((wallet) => wallet.userId === userId);
        if (!riderWallet || !driverWallet) throw new Error("Wallets could not be loaded.");
        if (riderWallet.balanceMinor + amountMinor > MAX_WALLET_BALANCE_MINOR) {
          throw routeError(409, "The rider wallet cannot accept this credit.");
        }
        if (driverWallet.balanceMinor < amountMinor) {
          throw routeError(409, "Your wallet does not have enough credit to send this change.");
        }

        const now = new Date();
        const updatedRiderBalance = riderWallet.balanceMinor + amountMinor;
        const updatedDriverBalance = driverWallet.balanceMinor - amountMinor;
        await tx
          .update(wallets)
          .set({ balanceMinor: updatedRiderBalance, updatedAt: now })
          .where(eq(wallets.id, riderWallet.id));
        await tx
          .update(wallets)
          .set({ balanceMinor: updatedDriverBalance, updatedAt: now })
          .where(eq(wallets.id, driverWallet.id));
        await tx.insert(walletTransactions).values([
          {
            id: riderTransactionId,
            walletId: riderWallet.id,
            userId: trip.riderId,
            type: "cash_change_credit",
            amountMinor,
            currency: CURRENCY,
            reference: `cash-change-${trip.id}`,
            description: "Cash change credited by the assigned driver",
            createdAt: now,
          },
          {
            id: driverTransactionId,
            walletId: driverWallet.id,
            userId,
            type: "cash_change_debit",
            amountMinor: -amountMinor,
            currency: CURRENCY,
            reference: `cash-change-${trip.id}`,
            description: note ?? "Cash change credited to rider",
            createdAt: now,
          },
        ]);
        await tx.insert(rideEvents).values({
          requestId: trip.id,
          actorId: userId,
          action: "cash_change_credited",
          details: { amountMinor, reference: `cash-change-${trip.id}` },
          createdAt: now,
        });
      }

      const walletRows = await tx
        .select()
        .from(wallets)
        .where(inArray(wallets.userId, [trip.riderId, userId]));
      const riderWallet = walletRows.find((wallet) => wallet.userId === trip.riderId);
      const driverWallet = walletRows.find((wallet) => wallet.userId === userId);
      if (!riderWallet || !driverWallet) throw new Error("Wallets could not be loaded.");
      const driverTransactions = await tx
        .select()
        .from(walletTransactions)
        .where(eq(walletTransactions.userId, userId))
        .orderBy(desc(walletTransactions.createdAt))
        .limit(50);
      result = {
        wallet: walletSummary(driverWallet),
        riderWallet: walletSummary(riderWallet),
        transactions: driverTransactions.map(walletTransactionView),
        creditedAmountMinor: amountMinor,
        tripId: trip.id,
      };
    });

    if (!result) throw new Error("The cash-change credit was not recorded.");
    return res.json(result);
  } catch (error) {
    const status = statusError(error);
    if (status >= 400 && status < 600) {
      return res.status(status).json({
        error: error instanceof Error ? error.message : "Cash change could not be credited.",
      });
    }
    return next(error);
  }
});

router.post("/wallet/redeem-voucher", async (req, res, next) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });
  const rawCode = recordBody(req.body).code;
  const code = typeof rawCode === "string" ? rawCode.trim().toUpperCase() : "";
  if (!/^VHAYA-[A-F0-9]{8}-[A-F0-9]{8}$/.test(code)) {
    return res.status(400).json({ error: "Enter a valid Vhaya voucher code." });
  }

  try {
    let result:
      | {
          wallet: ReturnType<typeof walletSummary>;
          transactions: ReturnType<typeof walletTransactionView>[];
          creditedAmountMinor: number;
        }
      | undefined;

    await db.transaction(async (tx) => {
      const [voucher] = await tx
        .select()
        .from(walletVouchers)
        .where(eq(walletVouchers.codeHash, hashVoucherCode(code)))
        .limit(1)
        .for("update");
      if (!voucher) throw routeError(404, "This voucher code was not found.");
      if (voucher.status !== "issued") {
        throw routeError(409, "This voucher has already been used or was cancelled.");
      }
      if (voucher.expiresAt && voucher.expiresAt.getTime() <= Date.now()) {
        throw routeError(409, "This voucher has expired.");
      }

      await tx
        .insert(wallets)
        .values({
          id: `wallet-${userId}`,
          userId,
          balanceMinor: 0,
          currency: CURRENCY,
        })
        .onConflictDoNothing();
      const [wallet] = await tx
        .select()
        .from(wallets)
        .where(eq(wallets.userId, userId))
        .limit(1)
        .for("update");
      if (!wallet) throw new Error("Wallet could not be loaded.");
      if (wallet.balanceMinor + voucher.amountMinor > MAX_WALLET_BALANCE_MINOR) {
        throw routeError(409, "Your wallet cannot accept this voucher credit.");
      }

      const now = new Date();
      const updatedBalance = wallet.balanceMinor + voucher.amountMinor;
      await tx
        .update(walletVouchers)
        .set({ status: "redeemed", redeemedBy: userId, redeemedAt: now })
        .where(eq(walletVouchers.id, voucher.id));
      await tx
        .update(wallets)
        .set({ balanceMinor: updatedBalance, updatedAt: now })
        .where(eq(wallets.id, wallet.id));
      await tx.insert(walletTransactions).values({
        id: `wtx-voucher-${voucher.id}`,
        walletId: wallet.id,
        userId,
        type: "voucher_credit",
        amountMinor: voucher.amountMinor,
        currency: CURRENCY,
        reference: `voucher-${voucher.id}`,
        description: "Vhaya wallet voucher redeemed",
        createdAt: now,
      });

      const [updatedWallet] = await tx
        .select()
        .from(wallets)
        .where(eq(wallets.id, wallet.id))
        .limit(1);
      const transactions = await tx
        .select()
        .from(walletTransactions)
        .where(eq(walletTransactions.userId, userId))
        .orderBy(desc(walletTransactions.createdAt))
        .limit(50);
      if (!updatedWallet) throw new Error("Wallet could not be loaded.");
      result = {
        wallet: walletSummary(updatedWallet),
        transactions: transactions.map(walletTransactionView),
        creditedAmountMinor: voucher.amountMinor,
      };
    });

    if (!result) throw new Error("The voucher could not be redeemed.");
    return res.json(result);
  } catch (error) {
    const status = statusError(error);
    if (status >= 400 && status < 600) {
      return res.status(status).json({
        error: error instanceof Error ? error.message : "Voucher redemption failed.",
      });
    }
    return next(error);
  }
});

router.post("/wallet/transfer", async (req, res, next) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });
  const body = recordBody(req.body);
  const recipientInput =
    typeof body.recipient === "string" ? body.recipient.trim() : "";
  const amountMinor = body.amountMinor;
  const note =
    body.note === undefined || body.note === null
      ? null
      : typeof body.note === "string"
        ? body.note.trim().slice(0, 120) || null
        : undefined;
  if (
    recipientInput.length < 5 ||
    recipientInput.length > 254 ||
    typeof amountMinor !== "number" ||
    !Number.isSafeInteger(amountMinor) ||
    amountMinor < 500 ||
    amountMinor > MAX_WALLET_BALANCE_MINOR ||
    note === undefined
  ) {
    return res.status(400).json({
      error: "Enter a valid family member account and a transfer of at least R 5.00.",
    });
  }

  const isEmail = recipientInput.includes("@");
  const normalizedEmail = isEmail ? recipientInput.trim().toLowerCase() : null;
  const normalizedPhone = isEmail ? null : normalizePhoneNumber(recipientInput);
  if ((!normalizedEmail && !normalizedPhone) || (normalizedEmail && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(normalizedEmail))) {
    return res.status(400).json({
      error: "Use the recipient's account email or verified mobile number.",
    });
  }

  try {
    const recipientRows = await db
      .select({ id: wayfareUsers.id })
      .from(wayfareUsers)
      .where(
        and(
          isNull(wayfareUsers.deletedAt),
          normalizedEmail
            ? eq(wayfareUsers.email, normalizedEmail)
            : eq(wayfareUsers.phoneNumber, normalizedPhone!),
        ),
      )
      .limit(2);
    if (!recipientRows.length) {
      return res.status(404).json({ error: "No active Vhaya account matches that recipient." });
    }
    if (recipientRows.length > 1) {
      return res.status(409).json({ error: "That recipient is linked to more than one account." });
    }
    const recipientId = recipientRows[0]!.id;
    if (recipientId === userId) {
      return res.status(400).json({ error: "You cannot transfer wallet credit to yourself." });
    }

    let result:
      | {
          wallet: ReturnType<typeof walletSummary>;
          transactions: ReturnType<typeof walletTransactionView>[];
          transfer: { reference: string; amountMinor: number };
        }
      | undefined;

    await db.transaction(async (tx) => {
      const users = await tx
        .select({ id: wayfareUsers.id, deletedAt: wayfareUsers.deletedAt })
        .from(wayfareUsers)
        .where(inArray(wayfareUsers.id, [userId, recipientId]))
        .orderBy(wayfareUsers.id)
        .for("update");
      if (users.length !== 2 || users.some((user) => user.deletedAt)) {
        throw routeError(404, "An active Vhaya account could not be found.");
      }

      for (const walletUserId of [userId, recipientId].sort()) {
        await tx
          .insert(wallets)
          .values({
            id: `wallet-${walletUserId}`,
            userId: walletUserId,
            balanceMinor: 0,
            currency: CURRENCY,
          })
          .onConflictDoNothing();
      }
      const lockedWallets = await tx
        .select()
        .from(wallets)
        .where(inArray(wallets.userId, [userId, recipientId]))
        .orderBy(wallets.userId)
        .for("update");
      const senderWallet = lockedWallets.find((wallet) => wallet.userId === userId);
      const recipientWallet = lockedWallets.find((wallet) => wallet.userId === recipientId);
      if (!senderWallet || !recipientWallet) throw new Error("Wallets could not be loaded.");
      if (senderWallet.balanceMinor < amountMinor) {
        throw routeError(409, "Your wallet does not have enough credit for this transfer.");
      }
      if (recipientWallet.balanceMinor + amountMinor > MAX_WALLET_BALANCE_MINOR) {
        throw routeError(409, "The recipient wallet cannot accept this transfer.");
      }

      const reference = `wallet-transfer-${randomUUID()}`;
      const now = new Date();
      await tx
        .update(wallets)
        .set({ balanceMinor: senderWallet.balanceMinor - amountMinor, updatedAt: now })
        .where(eq(wallets.id, senderWallet.id));
      await tx
        .update(wallets)
        .set({ balanceMinor: recipientWallet.balanceMinor + amountMinor, updatedAt: now })
        .where(eq(wallets.id, recipientWallet.id));
      await tx.insert(walletTransactions).values([
        {
          id: `wtx-${randomUUID()}`,
          walletId: senderWallet.id,
          userId,
          type: "transfer_out",
          amountMinor: -amountMinor,
          currency: CURRENCY,
          reference,
          description: note ?? "Family wallet transfer sent",
          createdAt: now,
        },
        {
          id: `wtx-${randomUUID()}`,
          walletId: recipientWallet.id,
          userId: recipientId,
          type: "transfer_in",
          amountMinor,
          currency: CURRENCY,
          reference,
          description: "Family wallet transfer received",
          createdAt: now,
        },
      ]);

      const [updatedSenderWallet] = await tx
        .select()
        .from(wallets)
        .where(eq(wallets.id, senderWallet.id))
        .limit(1);
      const transactions = await tx
        .select()
        .from(walletTransactions)
        .where(eq(walletTransactions.userId, userId))
        .orderBy(desc(walletTransactions.createdAt))
        .limit(50);
      if (!updatedSenderWallet) throw new Error("Wallet could not be loaded.");
      result = {
        wallet: walletSummary(updatedSenderWallet),
        transactions: transactions.map(walletTransactionView),
        transfer: { reference, amountMinor },
      };
    });

    if (!result) throw new Error("The wallet transfer was not recorded.");
    return res.json(result);
  } catch (error) {
    const status = statusError(error);
    if (status >= 400 && status < 600) {
      return res.status(status).json({
        error: error instanceof Error ? error.message : "Wallet transfer failed.",
      });
    }
    return next(error);
  }
});

router.get("/admin/wallet-vouchers", async (req, res, next) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });
  if ((await getWayfareRole(userId)) !== "admin") {
    return res.status(403).json({ error: "Operations admin access is required." });
  }
  try {
    const rows = await db
      .select()
      .from(walletVouchers)
      .orderBy(desc(walletVouchers.createdAt))
      .limit(100);
    return res.json({
      vouchers: rows.map((voucher) => ({
        id: voucher.id,
        codeSuffix: voucher.codeHash.slice(-4).toUpperCase(),
        amountMinor: voucher.amountMinor,
        currency: voucher.currency,
        status:
          voucher.status === "issued" &&
          voucher.expiresAt &&
          voucher.expiresAt.getTime() <= Date.now()
            ? "expired"
            : voucher.status,
        createdAt: voucher.createdAt.getTime(),
        expiresAt: voucher.expiresAt?.getTime() ?? null,
        redeemedAt: voucher.redeemedAt?.getTime() ?? null,
      })),
    });
  } catch (error) {
    return next(error);
  }
});

router.post("/admin/wallet-vouchers", async (req, res, next) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });
  if ((await getWayfareRole(userId)) !== "admin") {
    return res.status(403).json({ error: "Operations admin access is required." });
  }
  const body = recordBody(req.body);
  const amountMinor = body.amountMinor;
  const expiresAtInput = body.expiresAt;
  if (
    typeof amountMinor !== "number" ||
    !Number.isSafeInteger(amountMinor) ||
    amountMinor < 100 ||
    amountMinor > MAX_WALLET_BALANCE_MINOR ||
    (expiresAtInput !== undefined &&
      (typeof expiresAtInput !== "number" ||
        !Number.isSafeInteger(expiresAtInput) ||
        expiresAtInput <= Date.now()))
  ) {
    return res.status(400).json({
      error: "Enter a valid voucher amount and, if needed, a future expiry time.",
    });
  }

  try {
    const codePart = randomBytes(8).toString("hex").toUpperCase();
    const code = `VHAYA-${codePart.slice(0, 8)}-${codePart.slice(8)}`;
    const id = `voucher-${randomUUID()}`;
    const expiresAt =
      typeof expiresAtInput === "number" ? new Date(expiresAtInput) : null;
    const [voucher] = await db
      .insert(walletVouchers)
      .values({
        id,
        codeHash: hashVoucherCode(code),
        amountMinor,
        currency: CURRENCY,
        status: "issued",
        createdBy: userId,
        expiresAt,
      })
      .returning();
    if (!voucher) throw new Error("The voucher could not be issued.");
    return res.status(201).json({
      voucher: {
        id: voucher.id,
        code,
        amountMinor: voucher.amountMinor,
        currency: voucher.currency,
        status: voucher.status,
        createdAt: voucher.createdAt.getTime(),
        expiresAt: voucher.expiresAt?.getTime() ?? null,
      },
    });
  } catch (error) {
    return next(error);
  }
});

router.patch("/admin/wallet-vouchers/:voucherId/revoke", async (req, res, next) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });
  if ((await getWayfareRole(userId)) !== "admin") {
    return res.status(403).json({ error: "Operations admin access is required." });
  }
  const voucherId =
    typeof req.params.voucherId === "string" ? req.params.voucherId : "";
  try {
    const [voucher] = await db
      .select()
      .from(walletVouchers)
      .where(eq(walletVouchers.id, voucherId))
      .limit(1);
    if (!voucher) return res.status(404).json({ error: "Voucher not found." });
    if (voucher.status !== "issued") {
      return res.status(409).json({ error: "Only unused vouchers can be cancelled." });
    }
    const [updated] = await db
      .update(walletVouchers)
      .set({ status: "revoked" })
      .where(and(eq(walletVouchers.id, voucher.id), eq(walletVouchers.status, "issued")))
      .returning({ id: walletVouchers.id });
    if (!updated) return res.status(409).json({ error: "Voucher status changed; refresh and try again." });
    return res.json({ id: updated.id, status: "revoked" });
  } catch (error) {
    return next(error);
  }
});

router.get("/admin/wallets", async (req, res, next) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });
  if ((await getWayfareRole(userId)) !== "admin") {
    return res.status(403).json({ error: "Operations admin access is required." });
  }
  try {
    const rows = await db
      .select({
        walletId: wallets.id,
        userId: wallets.userId,
        displayName: wayfareUsers.displayName,
        email: wayfareUsers.email,
        role: wayfareUsers.role,
        balanceMinor: wallets.balanceMinor,
        currency: wallets.currency,
        createdAt: wallets.createdAt,
        updatedAt: wallets.updatedAt,
      })
      .from(wallets)
      .innerJoin(wayfareUsers, eq(wayfareUsers.id, wallets.userId))
      .orderBy(desc(wallets.updatedAt));
    return res.json({
      wallets: rows.map((r) => ({
        ...r,
        createdAt: r.createdAt.getTime(),
        updatedAt: r.updatedAt.getTime(),
      })),
    });
  } catch (error) {
    return next(error);
  }
});

export default router;


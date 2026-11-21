import { and, desc, eq } from "drizzle-orm";
import { Router, type IRouter } from "express";
import { db } from "@workspace/db";
import { wayfareUsers, wallets, walletTransactions, walletTopups } from "@workspace/db/schema";
import { getWayfareUserId } from "../middlewares/firebaseIdentity";
import { getWayfareRole } from "../middlewares/wayfareRoles";
import { recordBody } from "../lib/wayfareData";

const router: IRouter = Router();
const PAYSTACK_API = "https://api.paystack.co";
const CURRENCY = "ZAR";

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


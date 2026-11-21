import type { PayoutTransferStatus } from "@workspace/db/schema";
import { paystackRequest } from "./paystackSubscriptions.ts";

export type PaystackBank = {
  name: string;
  code: string;
  currency?: string;
  active?: boolean;
};

export type PaystackRecipient = {
  recipient_code?: string;
  name?: string;
  currency?: string;
  active?: boolean;
  details?: {
    account_number?: string;
    account_name?: string | null;
    bank_name?: string;
  };
};

export type PaystackTransfer = {
  reference?: string;
  transfer_code?: string;
  status?: string;
  amount?: number;
  currency?: string;
};

export function normalizePaystackTransferStatus(
  value: unknown,
): PayoutTransferStatus {
  if (
    value === "success" ||
    value === "pending" ||
    value === "failed" ||
    value === "otp" ||
    value === "reversed"
  ) {
    return value;
  }
  if (value === "queued" || value === "processing") return "pending";
  return "unknown";
}

export function assertPaystackKeySafeForEnvironment(): void {
  const secret = process.env.PAYSTACK_SECRET_KEY;
  if (!secret) {
    throw Object.assign(new Error("Paystack is not configured."), { status: 503 });
  }
  const production = process.env.NODE_ENV === "production";
  const requiredPrefix = production ? "sk_live_" : "sk_test_";
  if (!secret.startsWith(requiredPrefix)) {
    throw Object.assign(
      new Error(
        production
          ? "Live Paystack transfers require a live secret key."
          : "Development transfers require a Paystack test secret key.",
      ),
      { status: 503 },
    );
  }
}

export function assertPaystackTransfersEnabled(): void {
  if (process.env.PAYSTACK_TRANSFERS_ENABLED !== "true") {
    throw Object.assign(
      new Error("Paystack driver transfers are disabled."),
      { status: 503 },
    );
  }
  assertPaystackKeySafeForEnvironment();
}

export async function listPaystackBanks(): Promise<PaystackBank[]> {
  const banks = await paystackRequest<PaystackBank[]>(
    "/bank?currency=ZAR&perPage=100",
  );
  return banks.filter(
    (bank) =>
      typeof bank.name === "string" &&
      typeof bank.code === "string" &&
      bank.active !== false,
  );
}

export async function createPaystackRecipient(input: {
  name: string;
  accountNumber: string;
  bankCode: string;
}): Promise<PaystackRecipient> {
  return paystackRequest<PaystackRecipient>("/transferrecipient", {
    type: "basa",
    name: input.name,
    account_number: input.accountNumber,
    bank_code: input.bankCode,
    currency: "ZAR",
  });
}

export async function createPaystackTransfer(input: {
  amountMinor: number;
  recipientCode: string;
  reference: string;
  reason: string;
}): Promise<PaystackTransfer> {
  return paystackRequest<PaystackTransfer>("/transfer", {
    source: "balance",
    amount: input.amountMinor,
    currency: "ZAR",
    recipient: input.recipientCode,
    reference: input.reference,
    reason: input.reason,
  });
}

export async function verifyPaystackTransfer(
  reference: string,
): Promise<PaystackTransfer> {
  return paystackRequest<PaystackTransfer>(
    `/transfer/verify/${encodeURIComponent(reference)}`,
  );
}

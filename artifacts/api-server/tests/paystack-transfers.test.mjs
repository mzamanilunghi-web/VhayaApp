import assert from "node:assert/strict";
import test from "node:test";
import {
  assertPaystackKeySafeForEnvironment,
  assertPaystackTransfersEnabled,
  createPaystackRecipient,
  createPaystackTransfer,
  listPaystackBanks,
  normalizePaystackTransferStatus,
  verifyPaystackTransfer,
} from "../src/lib/paystackTransfers.ts";
import { PaystackRequestError } from "../src/lib/paystackSubscriptions.ts";

function withPaystackTestKey() {
  const previous = {
    key: process.env.PAYSTACK_SECRET_KEY,
    enabled: process.env.PAYSTACK_TRANSFERS_ENABLED,
    nodeEnv: process.env.NODE_ENV,
    fetch: globalThis.fetch,
  };
  process.env.PAYSTACK_SECRET_KEY = "sk_test_payouts_only";
  process.env.PAYSTACK_TRANSFERS_ENABLED = "true";
  process.env.NODE_ENV = "test";
  return () => {
    if (previous.key === undefined) delete process.env.PAYSTACK_SECRET_KEY;
    else process.env.PAYSTACK_SECRET_KEY = previous.key;
    if (previous.enabled === undefined) delete process.env.PAYSTACK_TRANSFERS_ENABLED;
    else process.env.PAYSTACK_TRANSFERS_ENABLED = previous.enabled;
    if (previous.nodeEnv === undefined) delete process.env.NODE_ENV;
    else process.env.NODE_ENV = previous.nodeEnv;
    globalThis.fetch = previous.fetch;
  };
}

function paystackResponse(data, status = 200, message = "OK") {
  return new Response(JSON.stringify({ status: status < 400, message, data }), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

test("bank listing is restricted to active ZAR banks", async () => {
  const restore = withPaystackTestKey();
  try {
    let requestUrl = "";
    let authHeader = "";
    globalThis.fetch = async (url, init) => {
      requestUrl = String(url);
      authHeader = new Headers(init.headers).get("Authorization") ?? "";
      return paystackResponse([
        { name: "Standard Bank South Africa", code: "051001", active: true },
        { name: "Inactive bank", code: "999999", active: false },
      ]);
    };
    const banks = await listPaystackBanks();
    assert.equal(requestUrl, "https://api.paystack.co/bank?currency=ZAR&perPage=100");
    assert.equal(authHeader, "Bearer sk_test_payouts_only");
    assert.deepEqual(banks, [
      { name: "Standard Bank South Africa", code: "051001", active: true },
    ]);
  } finally {
    restore();
  }
});

test("recipient setup sends South African BASA details and does not store them here", async () => {
  const restore = withPaystackTestKey();
  try {
    let requestBody;
    globalThis.fetch = async (_url, init) => {
      requestBody = JSON.parse(init.body);
      return paystackResponse({
        recipient_code: "RCP_8976",
        active: true,
        currency: "ZAR",
        name: "Driver Example",
        details: {
          account_number: "0012348976",
          account_name: "Driver Example",
          bank_name: "Standard Bank South Africa",
        },
      });
    };
    const recipient = await createPaystackRecipient({
      name: "Driver Example",
      accountNumber: "0012348976",
      bankCode: "051001",
    });
    assert.deepEqual(requestBody, {
      type: "basa",
      name: "Driver Example",
      account_number: "0012348976",
      bank_code: "051001",
      currency: "ZAR",
    });
    assert.equal(recipient.recipient_code, "RCP_8976");
  } finally {
    restore();
  }
});

test("transfer uses the supplied stable reference and ZAR amount", async () => {
  const restore = withPaystackTestKey();
  try {
    const calls = [];
    globalThis.fetch = async (url, init) => {
      calls.push({ url: String(url), body: JSON.parse(init.body) });
      return paystackResponse({
        reference: "payout_stable_reference_123",
        transfer_code: "TRF_123",
        status: "pending",
        amount: 25000,
        currency: "ZAR",
      });
    };
    const input = {
      amountMinor: 25000,
      recipientCode: "RCP_8976",
      reference: "payout_stable_reference_123",
      reason: "Vhaya weekly driver payout",
    };
    await createPaystackTransfer(input);
    await createPaystackTransfer(input);
    assert.equal(calls.length, 2);
    assert.deepEqual(calls[0].body, {
      source: "balance",
      amount: 25000,
      currency: "ZAR",
      recipient: "RCP_8976",
      reference: input.reference,
      reason: input.reason,
    });
    assert.equal(calls[0].body.reference, calls[1].body.reference);
  } finally {
    restore();
  }
});

test("transfer verification uses the stable reference route", async () => {
  const restore = withPaystackTestKey();
  try {
    let requestUrl = "";
    globalThis.fetch = async (url) => {
      requestUrl = String(url);
      return paystackResponse({
        reference: "payout_stable_reference_123",
        status: "success",
        amount: 25000,
        currency: "ZAR",
      });
    };
    const result = await verifyPaystackTransfer("payout_stable_reference_123");
    assert.equal(requestUrl, "https://api.paystack.co/transfer/verify/payout_stable_reference_123");
    assert.equal(result.status, "success");
  } finally {
    restore();
  }
});

test("provider HTTP errors retain their status for safe failure handling", async () => {
  const restore = withPaystackTestKey();
  try {
    globalThis.fetch = async () =>
      paystackResponse(undefined, 400, "Insufficient balance");
    await assert.rejects(listPaystackBanks(), (error) => {
      assert.ok(error instanceof PaystackRequestError);
      assert.equal(error.providerHttpStatus, 400);
      assert.equal(error.status, 502);
      return true;
    });
  } finally {
    restore();
  }
});

test("transfer-status normalization preserves uncertain and terminal outcomes", () => {
  assert.equal(normalizePaystackTransferStatus("success"), "success");
  assert.equal(normalizePaystackTransferStatus("failed"), "failed");
  assert.equal(normalizePaystackTransferStatus("otp"), "otp");
  assert.equal(normalizePaystackTransferStatus("reversed"), "reversed");
  assert.equal(normalizePaystackTransferStatus("processing"), "pending");
  assert.equal(normalizePaystackTransferStatus("new-provider-state"), "unknown");
});

test("transfers require the explicit switch and a test key in development", () => {
  const restore = withPaystackTestKey();
  try {
    assert.doesNotThrow(assertPaystackKeySafeForEnvironment);
    assert.doesNotThrow(assertPaystackTransfersEnabled);
    process.env.PAYSTACK_TRANSFERS_ENABLED = "false";
    assert.throws(assertPaystackTransfersEnabled, /disabled/);
    process.env.PAYSTACK_TRANSFERS_ENABLED = "true";
    process.env.PAYSTACK_SECRET_KEY = "sk_live_not_allowed_in_test";
    assert.throws(assertPaystackTransfersEnabled, /test secret key/);
  } finally {
    restore();
  }
});

import type { DriverSubscriptionBillingInterval } from "@workspace/db/schema";

const PAYSTACK_API = "https://api.paystack.co";

type PaystackEnvelope<T> = {
  status?: boolean;
  message?: string;
  data?: T;
};

export class PaystackRequestError extends Error {
  readonly status = 502;
  readonly providerHttpStatus: number;

  constructor(
    message: string,
    providerHttpStatus: number,
  ) {
    super(message);
    this.name = "PaystackRequestError";
    this.providerHttpStatus = providerHttpStatus;
  }
}

export type PaystackPlanResult = {
  plan_code?: string;
};

export type PaystackSubscriptionResult = {
  subscription_code?: string;
  email_token?: string;
  next_payment_date?: string;
};

export function isPaystackPaymentMode(): boolean {
  const configured = process.env.PAYMENT_PROVIDER;
  if (configured === "mock" && process.env.NODE_ENV !== "production") return false;
  if (configured === "paystack") return true;
  if (!configured) return process.env.NODE_ENV === "production";
  if (configured === "mock" && process.env.NODE_ENV === "production") {
    throw Object.assign(
      new Error("Mock payments are not permitted in production."),
      { status: 503 },
    );
  }
  throw Object.assign(new Error("PAYMENT_PROVIDER must be mock or paystack."), {
    status: 503,
  });
}

export async function paystackRequest<T>(
  path: string,
  body?: Record<string, unknown>,
): Promise<T> {
  const secret = process.env.PAYSTACK_SECRET_KEY;
  if (!secret) {
    throw Object.assign(new Error("Paystack is not configured."), { status: 503 });
  }
  const response = await fetch(`${PAYSTACK_API}${path}`, {
    method: body ? "POST" : "GET",
    headers: {
      Authorization: `Bearer ${secret}`,
      "Content-Type": "application/json",
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
    signal: AbortSignal.timeout(12_000),
  });
  const payload = (await response.json().catch(() => null)) as
    | PaystackEnvelope<T>
    | null;
  if (!response.ok || payload?.status !== true || payload.data === undefined) {
    throw new PaystackRequestError(
      payload?.message || "Paystack could not complete the request.",
      response.status,
    );
  }
  return payload.data;
}

export async function createPaystackPlan(input: {
  name: string;
  description: string | null;
  amountMinor: number;
  currency: string;
  interval: DriverSubscriptionBillingInterval;
}): Promise<string> {
  const plan = await paystackRequest<PaystackPlanResult>("/plan", {
    name: input.name,
    description: input.description ?? undefined,
    amount: input.amountMinor,
    currency: input.currency,
    interval: input.interval,
    send_invoices: false,
    send_sms: false,
  });
  if (!plan.plan_code) {
    throw Object.assign(
      new Error("Paystack did not return a plan code."),
      { status: 502 },
    );
  }
  return plan.plan_code;
}

export async function createPaystackSubscription(input: {
  customer: string;
  plan: string;
  authorization: string;
  startDate: Date;
}): Promise<PaystackSubscriptionResult> {
  const result = await paystackRequest<PaystackSubscriptionResult>(
    "/subscription",
    {
      customer: input.customer,
      plan: input.plan,
      authorization: input.authorization,
      start_date: input.startDate.toISOString(),
    },
  );
  if (!result.subscription_code || !result.email_token) {
    throw Object.assign(
      new Error("Paystack did not return subscription cancellation details."),
      { status: 502 },
    );
  }
  return result;
}

export async function disablePaystackSubscription(
  subscriptionCode: string,
  emailToken: string,
): Promise<void> {
  await paystackRequest("/subscription/disable", {
    code: subscriptionCode,
    token: emailToken,
  });
}

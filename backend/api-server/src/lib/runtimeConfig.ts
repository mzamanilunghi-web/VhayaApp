function hasValidFirebaseServiceAccount(): boolean {
  const serialized = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if (!serialized) return false;
  try {
    const account = JSON.parse(serialized) as Record<string, unknown>;
    return (
      typeof account.project_id === "string" &&
      typeof account.client_email === "string" &&
      typeof account.private_key === "string"
    );
  } catch {
    return false;
  }
}

function isValidPaystackCallbackUrl(value: string | undefined): boolean {
  if (!value) return false;
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

export function getPaystackCallbackUrl(): string | undefined {
  const configured = process.env.PAYSTACK_CALLBACK_URL;
  if (configured) return configured;

  const domain = process.env.REPLIT_DOMAINS?.split(",")
    .map((candidate) => candidate.trim())
    .find(Boolean);
  const hostname = domain?.replace(/^https?:\/\//i, "").replace(/\/+$/, "");
  return hostname
    ? `https://${hostname}/api/payments/callback`
    : undefined;
}

export function assertRuntimeConfig(): void {
  const missing: string[] = [];
  const isProduction = process.env.NODE_ENV === "production";
  const disableDevelopmentTunnel =
    process.env.WAYFARE_DISABLE_DEV_TUNNEL === "true";

  if (!process.env.DATABASE_URL) missing.push("DATABASE_URL");
  if (!hasValidFirebaseServiceAccount()) {
    missing.push("FIREBASE_SERVICE_ACCOUNT_JSON (valid service-account JSON)");
  }
  if (
    !isProduction &&
    !disableDevelopmentTunnel &&
    !process.env.NGROK_AUTHTOKEN?.trim()
  ) {
    missing.push("NGROK_AUTHTOKEN (required for the public development API tunnel)");
  }

  const provider =
    process.env.PAYMENT_PROVIDER ??
    (isProduction ? "paystack" : "mock");
  if (provider !== "paystack" && provider !== "mock") {
    missing.push("PAYMENT_PROVIDER (must be paystack or mock)");
  }
  if (provider === "paystack") {
    const secretKey = process.env.PAYSTACK_SECRET_KEY;
    if (!secretKey) {
      missing.push("PAYSTACK_SECRET_KEY");
    } else if (
      !isProduction &&
      !secretKey.startsWith("sk_test_")
    ) {
      missing.push("PAYSTACK_SECRET_KEY (use a Paystack test key in development)");
    }
    const callbackUrl = getPaystackCallbackUrl();
    if (
      (callbackUrl && !isValidPaystackCallbackUrl(callbackUrl)) ||
      (isProduction && !callbackUrl)
    ) {
      missing.push("PAYSTACK_CALLBACK_URL (valid HTTPS callback URL required)");
    }
  }

  const transfersEnabled = process.env.PAYSTACK_TRANSFERS_ENABLED;
  if (transfersEnabled !== undefined && !["true", "false"].includes(transfersEnabled)) {
    missing.push("PAYSTACK_TRANSFERS_ENABLED (must be true or false)");
  }
  if (transfersEnabled === "true") {
    const secretKey = process.env.PAYSTACK_SECRET_KEY;
    const requiredPrefix = isProduction ? "sk_live_" : "sk_test_";
    if (!secretKey?.startsWith(requiredPrefix)) {
      missing.push(
        isProduction
          ? "PAYSTACK_SECRET_KEY (live key required for live driver transfers)"
          : "PAYSTACK_SECRET_KEY (test key required for development driver transfers)",
      );
    }
  }

  if (missing.length) {
    throw new Error(
      `API startup blocked. Configure or correct: ${missing.join(", ")}. ` +
        "Use Replit Secrets for credentials, then restart the API.",
    );
  }
}

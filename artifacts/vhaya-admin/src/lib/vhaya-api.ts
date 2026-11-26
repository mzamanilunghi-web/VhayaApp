import type { User } from "firebase/auth";
import type {
  AdminRide,
  GetAdminRidesStatus,
} from "@workspace/api-client-react";
export type { AdminRide, GetAdminRidesStatus } from "@workspace/api-client-react";

import { firebaseAuth } from "./firebase";

const API_BASE_URL = (
  import.meta.env.VITE_API_BASE_URL?.trim() ||
  `${import.meta.env.BASE_URL}api`
).replace(/\/+$/, "");
const SESSION_KEY = "vhaya-admin-session";

export type CaseStatus = "open" | "in_progress" | "resolved";
export type CasePriority = "normal" | "high" | "urgent";
export type SupportCategory =
  | "safety"
  | "payment"
  | "driver"
  | "rider"
  | "lost_item"
  | "fare_review"
  | "other";
export type PersonRole = "rider" | "driver" | "admin";

export interface AdminSession {
  userId: string;
  displayName: string;
  email: string | null;
}

export interface Overview {
  generatedAt: number;
  summary: {
    users: number;
    suspendedUsers: number;
    rideRequests: number;
    activeRides: number;
    pendingDriverReviews: number;
    paymentIssues: number;
    activeSupportCases: number;
    urgentSupportCases: number;
    unassignedSupportCases: number;
  };
  support: {
    last30Days: number;
    byCategory: Array<{ category: SupportCategory; count: number }>;
    byPriority: Array<{ priority: CasePriority; count: number }>;
  };
}

export interface AdminActivity {
  id: string;
  kind: string;
  actor: string;
  subject: string;
  summary: string;
  createdAt: number | string;
}

export interface AdminOnlineDriver {
  driverId: string;
  displayName: string;
  city: string | null;
  vehicle: string | null;
  vehicleColor: string | null;
  location: {
    latitude: number;
    longitude: number;
    updatedAt: number;
  } | null;
  locationState: "current" | "stale" | "waiting";
  lastLocationAt: number | null;
}

export interface AdminOnlineDriversResponse {
  generatedAt: number;
  drivers: AdminOnlineDriver[];
}

export interface Person {
  userId: string;
  displayName: string;
  email: string;
  role: PersonRole;
  isSuspended: boolean;
  suspendedReason: string | null;
  personType: "requester" | "driver";
  rideCount: number;
  completedRideCount: number;
  lastRideAt: string | null;
  driverProfile: Record<string, unknown> | null;
  lastReviewOutcome: string | null;
  lastReviewedAt: string | null;
  updatedAt: string;
}

export interface PeoplePage {
  people: Person[];
  page: number;
  limit: number;
  hasMore: boolean;
}

export interface RidesPage {
  rides: AdminRide[];
  page: number;
  limit: number;
  hasMore: boolean;
}

export interface PersonRide {
  id: string;
  status: string;
  pickupLabel: string;
  destinationLabel: string;
  participantRole: "requester" | "driver";
  fareMinor: number;
  currency: string;
  createdAt: string | number;
}

export interface PersonReview {
  outcome: string;
  staffNote: string | null;
  actor: string;
  createdAt: string | number;
}

export interface PersonDetail {
  person: Person;
  rides: PersonRide[];
  reviews: PersonReview[];
}

export interface SupportReply {
  id: string;
  sender: string;
  channel: string;
  message: string;
  createdAt: number | string;
  recipientUserId?: string | null;
}

export interface SupportReporter {
  userId?: string;
  displayName?: string;
  email?: string;
  role?: string;
}

export interface SupportRideSummary {
  id?: string;
  status?: string;
  pickupLabel?: string;
  destinationLabel?: string;
  fareMinor?: number;
  currency?: string;
  paymentMethod?: string;
  createdAt?: number | string;
}

export interface SupportCase {
  id: string;
  reporter: SupportReporter;
  originalMessage: string;
  replies: SupportReply[];
  rideSummary: SupportRideSummary | null;
  priority: CasePriority;
  status: CaseStatus;
  category: SupportCategory;
  assignedTo: string | null;
  staffNote: string | null;
  createdAt: number | string;
}

export interface Payment {
  id: string;
  tripId: string;
  driverId: string;
  riderName: string;
  riderEmail: string;
  provider: string;
  reference: string;
  paymentMethod: string;
  amountMinor: number;
  commissionMinor: number;
  capturedAmountMinor: number;
  currency: string;
  status: string;
  createdAt: number;
  paidAt: number | null;
  tripStatus: string;
}

export interface Wallet {
  walletId: string;
  userId: string;
  displayName: string;
  email: string;
  role: PersonRole;
  balanceMinor: number;
  currency: string;
  createdAt: number;
  updatedAt: number;
}

export interface Payout {
  id: string;
  driverId: string;
  driverName?: string;
  provider: string;
  externalReference: string;
  amountMinor: number;
  currency: string;
  periodStart: string | null;
  periodEnd: string | null;
  reconciliationStatus: "unreconciled" | "matched" | "exception";
  transferStatus:
    | "not_started"
    | "sending"
    | "pending"
    | "success"
    | "failed"
    | "otp"
    | "unknown"
    | "reversed";
  transferReference: string | null;
  transferCode: string | null;
  transferError: string | null;
  transferUpdatedAt: number | null;
  note: string | null;
  recordedAt: number | string;
  reconciledAt: number | string | null;
}

export interface PayoutBatch {
  id: string;
  periodStart: number;
  periodEnd: number;
  status: "open" | "submitted" | "processing" | "partially_paid" | "paid" | "failed";
  settlementMethod: "paystack" | "external" | null;
  totalAmountMinor: number;
  driverCount: number;
  paidAt: number | string | null;
  reconciledAt: number | string | null;
  createdBy: string | null;
}

export interface PayoutBank {
  name: string;
  code: string;
}

export interface PayoutRecipient {
  driverId: string;
  driverName: string;
  recipientName: string;
  bankName: string;
  accountLast4: string;
  currency: "ZAR";
  updatedAt: number;
}

export interface PayoutDriverRecipient {
  driverId: string;
  driverName: string;
  recipient: PayoutRecipient | null;
}

export interface PayoutBatchDriver {
  payoutId: number;
  driverId: string;
  driverName: string;
  amountMinor: number;
  currency: string;
  transferStatus: Payout["transferStatus"];
  transferError: string | null;
  transferUpdatedAt: number | null;
  recipientName: string | null;
  bankName: string | null;
  accountLast4: string | null;
}

export interface PayoutBatchDetail {
  batch: PayoutBatch;
  reviewToken: string;
  drivers: PayoutBatchDriver[];
}

export type SubscriptionBillingInterval =
  | "daily"
  | "weekly"
  | "monthly"
  | "quarterly"
  | "biannually"
  | "annually";
export type SubscriptionRenewalMode = "manual" | "automatic";
export type DriverSubscriptionStatus = "pending" | "active" | "expired" | "cancelled";
export type PromotionDiscountType = "fixed" | "percentage";

export interface DriverSubscriptionPlan {
  id: string;
  name: string;
  description: string | null;
  amountMinor: number;
  currency: "ZAR";
  billingInterval: SubscriptionBillingInterval;
  renewalMode: SubscriptionRenewalMode;
  isActive: boolean;
  activeSubscriptionCount: number;
}

export interface AdminDriverSubscription {
  driverId: string;
  planId: string | null;
  planName: string | null;
  status: DriverSubscriptionStatus;
  renewalMode: SubscriptionRenewalMode;
  autoRenewActive: boolean;
  autoRenewIssue: string | null;
  cancelAtPeriodEnd: boolean;
  amountMinor: number;
  currency: string;
  provider: string | null;
  startedAt: number | null;
  expiresAt: number | null;
  nextPaymentAt: number | null;
  autoRenewConsentAt: number | null;
}

export interface AdminDriverSubscriptionRow {
  driverId: string;
  displayName: string;
  email: string | null;
  subscription: AdminDriverSubscription | null;
  lastPayment: {
    id: string;
    provider: string;
    reference: string;
    amountMinor: number;
    currency: string;
    status: DriverSubscriptionStatus;
    startedAt: number;
    expiresAt: number | null;
  } | null;
  rating: {
    averageRating: number | null;
    ratingCount: number;
    source: string;
  };
}

export interface AdminDriverSubscriptionsPage {
  drivers: AdminDriverSubscriptionRow[];
  page: number;
  limit: number;
  hasMore: boolean;
}

export interface DriverPromotion {
  id: string;
  code: string;
  name: string;
  discountType: PromotionDiscountType;
  discountValue: number;
  maxDiscountMinor: number | null;
  currency: "ZAR";
  planId: string | null;
  startsAt: number;
  endsAt: number;
  serviceArea: string | null;
  maxRedemptions: number | null;
  newDriverOnly: boolean;
  minimumCompletedRides: number | null;
  minimumRating: number | null;
  isActive: boolean;
  redemptionCount: number;
}

export interface DriverSubscriptionPlanInput {
  name: string;
  description?: string | null;
  amountMinor: number;
  currency: "ZAR";
  billingInterval: SubscriptionBillingInterval;
  renewalMode: SubscriptionRenewalMode;
  isActive?: boolean;
}

export interface DriverPromotionInput {
  code: string;
  name: string;
  discountType: PromotionDiscountType;
  discountValue: number;
  maxDiscountMinor?: number | null;
  currency: "ZAR";
  planId?: string | null;
  startsAt: string;
  endsAt: string;
  serviceArea?: string | null;
  maxRedemptions?: number | null;
  newDriverOnly?: boolean;
  minimumCompletedRides?: number | null;
  minimumRating?: number | null;
  isActive?: boolean;
}

export interface AccountDeletionRequest {
  id: string;
  userId: string;
  displayName: string;
  email: string | null;
  role: PersonRole;
  status: "requested" | "processing" | "completed" | "rejected";
  requestedAt: string;
  pseudonymized: boolean;
  reason: string | null;
}

export interface AccountDeletionResult {
  requestId: string;
  status: "completed" | "processing";
  deleted: boolean;
  retainedData: string[];
}

export type DriverApplicationStatus =
  | "submitted"
  | "under_review"
  | "needs_follow_up";

export type DriverComplianceDocumentType =
  | "identity"
  | "driver_license"
  | "vehicle_registration"
  | "operating_permit";

export type DriverComplianceStatus =
  | "not_reviewed"
  | "verified"
  | "needs_follow_up"
  | "not_required";

export interface DriverComplianceCheck {
  documentType: DriverComplianceDocumentType;
  status: DriverComplianceStatus;
  expiresOn: string | null;
  staffNote: string | null;
  reviewedAt: number | null;
}

export interface DriverApplication {
  userId: string;
  displayName: string;
  email: string | null;
  applicationStatus: DriverApplicationStatus;
  verificationStatus: string;
  contactPhone: string | null;
  city: string | null;
  vehicle: string | null;
  vehicleColor: string | null;
  registration: string | null;
  licenceExpiry: string | null;
  operatingLicenceReference: string | null;
  staffNote: string | null;
  submittedAt: number | null;
  reviewedAt: number | null;
  complianceChecks: DriverComplianceCheck[];
  incompleteComplianceDocuments: string[];
}

export interface DriverComplianceRecord {
  userId: string;
  displayName: string;
  email: string;
  documents: Array<Record<string, unknown>>;
  incompleteDocuments: string[];
  [key: string]: unknown;
}

export class VhayaApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly retryAfter: string | null = null,
  ) {
    super(message);
    this.name = "VhayaApiError";
  }
}

function requireApiBase(): string {
  if (!API_BASE_URL) {
    throw new Error("VITE_API_BASE_URL is not configured.");
  }
  return API_BASE_URL;
}

async function readResponse<T>(response: Response): Promise<T> {
  const body = await response.json().catch(() => null);
  if (!response.ok) {
    const message =
      body &&
      typeof body === "object" &&
      "error" in body &&
      typeof body.error === "string"
        ? body.error
        : `Request failed (${response.status}).`;
    throw new VhayaApiError(message, response.status, response.headers.get("Retry-After"));
  }
  return body as T;
}

async function request<T>(
  path: string,
  init: RequestInit,
  user?: User,
  sessionId?: string,
): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  if (init.body && !(init.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }
  if (user) {
    try {
      headers.set("Authorization", `Bearer ${await user.getIdToken()}`);
    } catch {
      throw new Error(
        "Firebase could not provide a sign-in token. Check the Firebase session and network.",
      );
    }
  }
  if (sessionId) {
    headers.set("x-wayfare-session", sessionId);
  }

  const apiBase = requireApiBase();
  const requestUrl = `${apiBase}${path}`;
  let response: Response;
  try {
    response = await fetch(requestUrl, {
      ...init,
      headers,
      credentials: "omit",
    });
  } catch {
    const route =
      typeof window === "undefined"
        ? path.split("?")[0]
        : new URL(requestUrl, window.location.href).pathname;
    throw new Error(
      `The Vhaya API request ${(init.method ?? "GET").toUpperCase()} ${route} could not be reached from this preview.`,
    );
  }
  return readResponse<T>(response);
}

export async function claimVhayaSession(user: User): Promise<string> {
  const result = await request<{ sessionId: string }>(
    "/auth/session/claim",
    { method: "POST", headers: { "Content-Type": "application/json" } },
    user,
  );
  if (!result || typeof result.sessionId !== "string" || !result.sessionId) {
    throw new Error("The API did not return a Vhaya session ID.");
  }
  window.sessionStorage.setItem(SESSION_KEY, result.sessionId);
  return result.sessionId;
}

export async function releaseVhayaSession(user: User): Promise<void> {
  const sessionId = window.sessionStorage.getItem(SESSION_KEY);
  try {
    if (sessionId) {
      await request<unknown>(
        "/auth/session",
        { method: "DELETE" },
        user,
        sessionId,
      );
    }
  } finally {
    window.sessionStorage.removeItem(SESSION_KEY);
  }
}

export async function getAdminSession(
  user: User,
  sessionId = window.sessionStorage.getItem(SESSION_KEY) ?? "",
): Promise<AdminSession> {
  if (!sessionId) {
    throw new Error("Your Vhaya session is missing. Sign out and sign in again.");
  }
  return request<AdminSession>("/admin/session", { method: "GET" }, user, sessionId);
}

async function adminRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const user = firebaseAuth?.currentUser;
  const sessionId =
    typeof window === "undefined"
      ? ""
      : window.sessionStorage.getItem(SESSION_KEY) ?? "";
  if (!user || !sessionId) {
    throw new VhayaApiError("Sign in to your Vhaya admin account to continue.", 401);
  }
  return request<T>(path, init, user, sessionId);
}

function queryString(values: Record<string, string | number | undefined>): string {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(values)) {
    if (value !== undefined && value !== "") {
      params.set(key, String(value));
    }
  }
  const query = params.toString();
  return query ? `?${query}` : "";
}

function readArray<T>(body: unknown, keys: string[], endpoint: string): T[] {
  if (Array.isArray(body)) return body as T[];
  if (body && typeof body === "object") {
    const record = body as Record<string, unknown>;
    for (const key of keys) {
      if (Array.isArray(record[key])) return record[key] as T[];
    }
    throw new Error(
      `The ${endpoint} response did not contain an expected list (${keys.join(", ")}).`,
    );
  }
  throw new Error(`The ${endpoint} response was not a list.`);
}

export const vhayaApi = {
  overview: () => adminRequest<Overview>("/admin/overview"),
  onlineDrivers: () =>
    adminRequest<AdminOnlineDriversResponse>("/admin/online-drivers"),
  activity: async (limit = 50) => {
    const body = await adminRequest<{ activity: AdminActivity[] }>(
      `/admin/activity${queryString({ limit })}`,
    );
    return body.activity;
  },
  rides: (options: {
    q?: string;
    status?: GetAdminRidesStatus;
    page?: number;
    limit?: number;
  }) =>
    adminRequest<RidesPage>(
      `/admin/rides${queryString({
        q: options.q?.trim() && options.q.trim().length >= 2
          ? options.q.trim()
          : undefined,
        status: options.status ?? "all",
        page: options.page ?? 0,
        limit: options.limit ?? 25,
      })}`,
    ),
  cancelRide: (rideId: string) =>
    adminRequest<unknown>(
      `/rides/${encodeURIComponent(rideId)}/actions`,
      {
        method: "POST",
        body: JSON.stringify({ action: "adminCancel" }),
      },
    ),
  people: (options: { q?: string; type?: "all" | "requesters" | "drivers"; page?: number; limit?: number }) =>
    adminRequest<PeoplePage>(
      `/admin/people${queryString({
        q: options.q && options.q.length >= 2 ? options.q : undefined,
        type: options.type ?? "all",
        page: options.page ?? 0,
        limit: options.limit ?? 25,
      })}`,
    ),
  person: (userId: string) =>
    adminRequest<PersonDetail>(`/admin/people/${encodeURIComponent(userId)}`),
  updateRole: (userId: string, role: PersonRole) =>
    adminRequest<unknown>(`/admin/people/${encodeURIComponent(userId)}/role`, {
      method: "PATCH",
      body: JSON.stringify({ role }),
    }),
  suspendPerson: (userId: string, reason: string) =>
    adminRequest<unknown>(`/admin/people/${encodeURIComponent(userId)}/suspend`, {
      method: "PATCH",
      body: JSON.stringify({ suspend: true, reason }),
    }),
  unsuspendPerson: (userId: string) =>
    adminRequest<unknown>(`/admin/people/${encodeURIComponent(userId)}/suspend`, {
      method: "PATCH",
      body: JSON.stringify({ suspend: false }),
    }),
  reviewPerson: (userId: string, input: {
    subjectType: "requester" | "driver";
    outcome: "reviewed" | "needs_follow_up" | "approved" | "rejected";
    note?: string;
    checksConfirmed?: boolean;
  }) =>
    adminRequest<unknown>(`/admin/people/${encodeURIComponent(userId)}/reviews`, {
      method: "POST",
      body: JSON.stringify(input),
    }),
  driverApplications: async () =>
    readArray<DriverApplication>(
      await adminRequest<unknown>("/admin/driver-applications"),
      ["applications", "driverApplications"],
      "driver applications",
    ),
  driverCompliance: async () =>
    readArray<DriverComplianceRecord>(
      await adminRequest<unknown>("/admin/driver-compliance"),
      ["drivers", "profiles", "compliance"],
      "driver compliance",
    ),
  updateCompliance: (userId: string, input: {
    documentType: "identity" | "driver_license" | "vehicle_registration" | "operating_permit";
    status: "not_reviewed" | "verified" | "needs_follow_up" | "not_required";
    expiresOn?: string | null;
    note?: string;
  }) =>
    adminRequest<unknown>(`/admin/people/${encodeURIComponent(userId)}/compliance`, {
      method: "PATCH",
      body: JSON.stringify(input),
    }),
  supportCases: async () =>
    readArray<SupportCase>(
      await adminRequest<unknown>("/admin/support-cases"),
      ["cases", "supportCases"],
      "support cases",
    ),
  updateSupportCase: (caseId: string, input: {
    status?: CaseStatus;
    priority?: CasePriority;
    category?: SupportCategory;
    assignedTo?: "me" | null;
    staffNote?: string | null;
  }) =>
    adminRequest<{ case: SupportCase }>(
      `/admin/support-cases/${encodeURIComponent(caseId)}`,
      { method: "PATCH", body: JSON.stringify(input) },
    ),
  replyToSupportCase: (caseId: string, message: string) =>
    adminRequest<{ reply: SupportReply }>(
      `/admin/support-cases/${encodeURIComponent(caseId)}/replies`,
      { method: "POST", body: JSON.stringify({ message }) },
    ),
  payments: async (limit = 50) =>
    readArray<Payment>(
      await adminRequest<unknown>(`/admin/payments${queryString({ limit })}`),
      ["payments"],
      "payments",
    ),
  driverSubscriptionPlans: async () =>
    readArray<DriverSubscriptionPlan>(
      await adminRequest<unknown>("/admin/driver-subscription-plans"),
      ["plans"],
      "driver subscription plans",
    ),
  createDriverSubscriptionPlan: (input: DriverSubscriptionPlanInput) =>
    adminRequest<{ plan: DriverSubscriptionPlan }>("/admin/driver-subscription-plans", {
      method: "POST",
      body: JSON.stringify(input),
    }),
  updateDriverSubscriptionPlan: (
    planId: string,
    input: Partial<DriverSubscriptionPlanInput>,
  ) =>
    adminRequest<{ plan: DriverSubscriptionPlan }>(
      `/admin/driver-subscription-plans/${encodeURIComponent(planId)}`,
      { method: "PATCH", body: JSON.stringify(input) },
    ),
  driverSubscriptions: async (options: {
    q?: string;
    page?: number;
    limit?: number;
  }) =>
    adminRequest<AdminDriverSubscriptionsPage>(
      `/admin/driver-subscriptions${queryString({
        q:
          options.q?.trim() && options.q.trim().length >= 2
            ? options.q.trim()
            : undefined,
        page: options.page ?? 0,
        limit: options.limit ?? 25,
      })}`,
    ),
  updateDriverSubscription: (
    driverId: string,
    input: {
      planId?: string;
      status?: DriverSubscriptionStatus;
      expiresAt?: string | null;
      reason?: string;
    },
  ) =>
    adminRequest<{ subscription: AdminDriverSubscription }>(
      `/admin/driver-subscriptions/${encodeURIComponent(driverId)}`,
      { method: "PATCH", body: JSON.stringify(input) },
    ),
  driverPromotions: async () =>
    readArray<DriverPromotion>(
      await adminRequest<unknown>("/admin/driver-promotions"),
      ["promotions"],
      "driver promotions",
    ),
  createDriverPromotion: (input: DriverPromotionInput) =>
    adminRequest<{ promotion: DriverPromotion }>("/admin/driver-promotions", {
      method: "POST",
      body: JSON.stringify(input),
    }),
  updateDriverPromotion: (
    promotionId: string,
    input: Partial<DriverPromotionInput>,
  ) =>
    adminRequest<{ promotion: DriverPromotion }>(
      `/admin/driver-promotions/${encodeURIComponent(promotionId)}`,
      { method: "PATCH", body: JSON.stringify(input) },
    ),
  wallets: async () =>
    readArray<Wallet>(await adminRequest<unknown>("/admin/wallets"), ["wallets"], "wallets"),
  requestRefund: (paymentId: string, reason: string) =>
    adminRequest<unknown>(
      `/admin/payments/${encodeURIComponent(paymentId)}/refund`,
      { method: "POST", body: JSON.stringify({ reason }) },
    ),
  payouts: async () =>
    readArray<Payout>(await adminRequest<unknown>("/admin/payouts"), ["payouts"], "payouts"),
  createPayout: (input: {
    driverId: string;
    provider: "paystack" | "bank_transfer" | "cash" | "other";
    externalReference: string;
    amountMinor: number;
    currency: string;
    periodStart?: string;
    periodEnd?: string;
    note?: string;
    confirmedExternalTransfer: true;
  }) =>
    adminRequest<unknown>("/admin/payouts", {
      method: "POST",
      body: JSON.stringify(input),
    }),
  reconcilePayout: (payoutId: string, input: {
    reconciliationStatus: "matched" | "exception";
    note?: string;
  }) =>
    adminRequest<unknown>(`/admin/payouts/${encodeURIComponent(payoutId)}`, {
      method: "PATCH",
      body: JSON.stringify(input),
    }),
  payoutBatches: async () =>
    readArray<PayoutBatch>(
      await adminRequest<unknown>("/admin/payouts/batches"),
      ["batches"],
      "payout batches",
    ),
  payoutBatch: (batchId: string) =>
    adminRequest<PayoutBatchDetail>(
      `/admin/payouts/batches/${encodeURIComponent(batchId)}`,
    ),
  payoutBanks: async () =>
    (
      await adminRequest<{ banks: PayoutBank[] }>("/admin/payouts/banks")
    ).banks,
  payoutRecipients: async () =>
    (
      await adminRequest<{ drivers: PayoutDriverRecipient[] }>(
        "/admin/payouts/recipients",
      )
    ).drivers,
  savePayoutRecipient: (input: {
    driverId: string;
    name: string;
    accountNumber: string;
    bankCode: string;
  }) =>
    adminRequest<{ recipient: PayoutRecipient }>("/admin/payouts/recipients", {
      method: "POST",
      body: JSON.stringify(input),
    }),
  startPayoutBatchTransfers: (
    batchId: string,
    input: { confirmed: true; retryFailed?: boolean; reviewToken: string },
  ) =>
    adminRequest<{ batch: PayoutBatch; outcomes: unknown[] }>(
      `/admin/payouts/batches/${encodeURIComponent(batchId)}/transfers`,
      { method: "POST", body: JSON.stringify(input) },
    ),
  verifyPayoutTransfer: (payoutId: string) =>
    adminRequest<unknown>(
      `/admin/payouts/${encodeURIComponent(payoutId)}/verify`,
      { method: "POST" },
    ),
  recordExternalPayoutBatchPayment: (batchId: string) =>
    adminRequest<{ batch: PayoutBatch }>(
      `/admin/payouts/batches/${encodeURIComponent(batchId)}/external-payment`,
      { method: "POST", body: JSON.stringify({ confirmed: true }) },
    ),
  runWeeklyPayouts: (periodStart: string, periodEnd: string) =>
    adminRequest<unknown>("/admin/payouts/run-weekly", {
      method: "POST",
      body: JSON.stringify({
        periodStart: new Date(periodStart).toISOString(),
        periodEnd: new Date(periodEnd).toISOString(),
      }),
    }),
  approvePayoutBatch: (batchId: string) =>
    adminRequest<unknown>(
      `/admin/payouts/batches/${encodeURIComponent(batchId)}/approve`,
      { method: "PATCH" },
    ),
  accountDeletionRequests: async () =>
    readArray<AccountDeletionRequest>(
      await adminRequest<unknown>("/admin/account-deletion-requests"),
      ["requests"],
      "account deletion requests",
    ),
  completeAccountDeletion: (requestId: string) =>
    adminRequest<AccountDeletionResult>(
      `/admin/account-deletion-requests/${encodeURIComponent(requestId)}/complete`,
      {
        method: "POST",
        body: JSON.stringify({
          identityVerified: true,
          externalSubscriptionsCancelled: true,
        }),
      },
    ),
  rejectAccountDeletion: (requestId: string, reason: string) =>
    adminRequest<{ rejected: boolean }>(
      `/admin/account-deletion-requests/${encodeURIComponent(requestId)}/reject`,
      { method: "POST", body: JSON.stringify({ reason }) },
    ),
};

export function getVhayaSessionStorageKey(): string {
  return SESSION_KEY;
}

export function getVhayaApiBaseUrl(): string | undefined {
  return API_BASE_URL;
}

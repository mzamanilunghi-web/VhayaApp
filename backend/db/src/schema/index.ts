import {
  boolean,
  check,
  doublePrecision,
  integer,
  index,
  jsonb,
  pgTable,
  serial,
  text,
  timestamp,
  uniqueIndex,
} from "drizzle-orm/pg-core";
import { sql } from "drizzle-orm";

export type AdminReviewSubjectType = "requester" | "driver";
export type AdminReviewOutcome = "reviewed" | "needs_follow_up" | "approved" | "rejected";
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
export type SupportCaseCategory =
  | "safety"
  | "payment"
  | "driver"
  | "rider"
  | "lost_item"
  | "fare_review"
  | "other";
export type SupportCasePriority = "normal" | "high" | "urgent";
export type SupportCaseStatus = "open" | "in_progress" | "resolved";
export type PayoutReconciliationStatus = "unreconciled" | "matched" | "exception";
export type PayoutTransferStatus =
  | "not_started"
  | "sending"
  | "pending"
  | "success"
  | "failed"
  | "otp"
  | "unknown"
  | "reversed";
export type DriverSubscriptionStatus = "pending" | "active" | "expired" | "cancelled";
export type DriverSubscriptionBillingInterval =
  | "daily"
  | "weekly"
  | "monthly"
  | "quarterly"
  | "biannually"
  | "annually";
export type DriverSubscriptionRenewalMode = "manual" | "automatic";
export type DriverPromotionDiscountType = "fixed" | "percentage";
export type DriverPromotionRedemptionStatus = "pending" | "redeemed" | "released";

export const wayfareUsers = pgTable("wayfare_users", {
  id: text("id").primaryKey(),
  displayName: text("display_name").notNull(),
  email: text("email"),
  phoneNumber: text("phone_number"),
  role: text("role").notNull().default("rider"),
  devVerified: boolean("dev_verified").notNull().default(false),
  isSuspended: boolean("is_suspended").notNull().default(false),
  suspendedAt: timestamp("suspended_at", { withTimezone: true }),
  suspendedReason: text("suspended_reason"),
  suspendedBy: text("suspended_by"),
  deletedAt: timestamp("deleted_at", { withTimezone: true }),
  updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow(),
}, (table) => [
  uniqueIndex("wayfare_users_phone_number_idx").on(table.phoneNumber),
]);

export const authSessions = pgTable(
  "auth_sessions",
  {
    tokenHash: text("token_hash").primaryKey(),
    userId: text("user_id")
      .notNull()
      .references(() => wayfareUsers.id, { onDelete: "cascade" }),
    expiresAt: timestamp("expires_at", { withTimezone: true }).notNull(),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    index("auth_sessions_user_idx").on(table.userId),
    index("auth_sessions_expiry_idx").on(table.expiresAt),
  ],
);

export const driverProfiles = pgTable("driver_profiles", {
  userId: text("user_id")
    .primaryKey()
    .references(() => wayfareUsers.id, { onDelete: "cascade" }),
  applicationStatus: text("application_status").notNull().default("draft"),
  verificationStatus: text("verification_status").notNull().default("unverified"),
  contactPhone: text("contact_phone"),
  city: text("city"),
  vehicle: text("vehicle"),
  vehicleColor: text("vehicle_color"),
  registration: text("registration"),
  licenceExpiry: text("licence_expiry"),
  operatingLicenceReference: text("operating_licence_reference"),
  gravelReady: boolean("gravel_ready").notNull().default(false),
  documentsConsentAt: timestamp("documents_consent_at", { withTimezone: true }),
  isOnline: boolean("is_online").notNull().default(false),
  locationLatitude: doublePrecision("location_latitude"),
  locationLongitude: doublePrecision("location_longitude"),
  locationUpdatedAt: timestamp("location_updated_at", { withTimezone: true }),
  staffNote: text("staff_note"),
  submittedAt: timestamp("submitted_at", { withTimezone: true }),
  reviewedAt: timestamp("reviewed_at", { withTimezone: true }),
  updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow(),
}, (table) => [
  check(
    "driver_profiles_location_coordinates_check",
    sql`(
      ${table.locationLatitude} is null and ${table.locationLongitude} is null
    ) or (
      ${table.locationLatitude} is not null
      and ${table.locationLongitude} is not null
      and
      ${table.locationLatitude} between -90 and 90
      and ${table.locationLongitude} between -180 and 180
    )`,
  ),
  check(
    "driver_profiles_location_timestamp_check",
    sql`(
      ${table.locationLatitude} is null
      and ${table.locationLongitude} is null
      and ${table.locationUpdatedAt} is null
    ) or (
      ${table.locationLatitude} is not null
      and ${table.locationLongitude} is not null
      and ${table.locationUpdatedAt} is not null
    )`,
  ),
  check(
    "driver_profiles_offline_location_check",
    sql`${table.isOnline} or (
      ${table.locationLatitude} is null
      and ${table.locationLongitude} is null
      and ${table.locationUpdatedAt} is null
    )`,
  ),
  index("driver_profiles_online_location_idx").on(
    table.isOnline,
    table.locationUpdatedAt,
  ),
]);

export const driverComplianceChecks = pgTable(
  "driver_compliance_checks",
  {
    id: serial("id").primaryKey(),
    userId: text("user_id")
      .notNull()
      .references(() => wayfareUsers.id, { onDelete: "cascade" }),
    documentType: text("document_type").$type<DriverComplianceDocumentType>().notNull(),
    status: text("status").$type<DriverComplianceStatus>().notNull().default("not_reviewed"),
    expiresOn: text("expires_on"),
    staffNote: text("staff_note"),
    reviewedBy: text("reviewed_by").references(() => wayfareUsers.id, {
      onDelete: "restrict",
    }),
    reviewedAt: timestamp("reviewed_at", { withTimezone: true }),
    updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    uniqueIndex("driver_compliance_user_document_idx").on(
      table.userId,
      table.documentType,
    ),
    index("driver_compliance_expiry_idx").on(table.status, table.expiresOn),
    check(
      "driver_compliance_document_type_check",
      sql`${table.documentType} in ('identity', 'driver_license', 'vehicle_registration', 'operating_permit')`,
    ),
    check(
      "driver_compliance_status_check",
      sql`${table.status} in ('not_reviewed', 'verified', 'needs_follow_up', 'not_required')`,
    ),
  ],
);

export const driverApplicationDocuments = pgTable(
  "driver_application_documents",
  {
    id: serial("id").primaryKey(),
    userId: text("user_id")
      .notNull()
      .references(() => wayfareUsers.id, { onDelete: "cascade" }),
    documentType: text("document_type")
      .$type<DriverComplianceDocumentType>()
      .notNull(),
    objectKey: text("object_key").notNull(),
    fileName: text("file_name").notNull(),
    contentType: text("content_type").notNull(),
    fileSizeBytes: integer("file_size_bytes").notNull(),
    uploadedAt: timestamp("uploaded_at", { withTimezone: true })
      .notNull()
      .defaultNow(),
    updatedAt: timestamp("updated_at", { withTimezone: true })
      .notNull()
      .defaultNow(),
  },
  (table) => [
    uniqueIndex("driver_application_documents_user_type_idx").on(
      table.userId,
      table.documentType,
    ),
    index("driver_application_documents_user_idx").on(table.userId),
    check(
      "driver_application_documents_type_check",
      sql`${table.documentType} in ('identity', 'driver_license', 'vehicle_registration', 'operating_permit')`,
    ),
    check(
      "driver_application_documents_size_check",
      sql`${table.fileSizeBytes} > 0 and ${table.fileSizeBytes} <= 8388608`,
    ),
  ],
);

export const adminReviewRecords = pgTable(
  "admin_review_records",
  {
    id: serial("id").primaryKey(),
    subjectUserId: text("subject_user_id")
      .notNull()
      .references(() => wayfareUsers.id, { onDelete: "restrict" }),
    actorUserId: text("actor_user_id")
      .notNull()
      .references(() => wayfareUsers.id, { onDelete: "restrict" }),
    subjectType: text("subject_type").$type<AdminReviewSubjectType>().notNull(),
    outcome: text("outcome").$type<AdminReviewOutcome>().notNull(),
    note: text("note"),
    checksConfirmed: boolean("checks_confirmed").notNull().default(false),
    applicationStatusBefore: text("application_status_before"),
    applicationStatusAfter: text("application_status_after"),
    roleBefore: text("role_before"),
    roleAfter: text("role_after"),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    check(
      "admin_review_records_subject_type_check",
      sql`${table.subjectType} in ('requester', 'driver')`,
    ),
    check(
      "admin_review_records_outcome_check",
      sql`${table.outcome} in ('reviewed', 'needs_follow_up', 'approved', 'rejected')`,
    ),
    index("admin_review_records_subject_created_idx").on(
      table.subjectUserId,
      table.createdAt,
    ),
    index("admin_review_records_actor_created_idx").on(table.actorUserId, table.createdAt),
  ],
);

export const rideRequests = pgTable(
  "ride_requests",
  {
    id: text("id").primaryKey(),
    riderId: text("rider_id")
      .notNull()
      .references(() => wayfareUsers.id, { onDelete: "restrict" }),
    driverId: text("driver_id").references(() => wayfareUsers.id, { onDelete: "set null" }),
    status: text("status").notNull(),
    pickup: text("pickup").notNull(),
    destination: text("destination").notNull(),
    pickupLatitude: doublePrecision("pickup_latitude"),
    pickupLongitude: doublePrecision("pickup_longitude"),
    destinationLatitude: doublePrecision("destination_latitude"),
    destinationLongitude: doublePrecision("destination_longitude"),
    category: text("category").notNull(),
    currency: text("currency").notNull(),
    fare: doublePrecision("fare").notNull(),
    pricingMode: text("pricing_mode").notNull(),
    pickupAt: timestamp("pickup_at", { withTimezone: true }),
    route: jsonb("route").$type<Record<string, unknown> | null>(),
    tripData: jsonb("trip_data").$type<Record<string, unknown>>().notNull(),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    index("ride_requests_rider_created_idx").on(table.riderId, table.createdAt),
    index("ride_requests_dispatch_idx").on(table.status, table.createdAt),
    index("ride_requests_driver_idx").on(table.driverId, table.status),
  ],
);

export const supportCases = pgTable(
  "support_cases",
  {
    id: serial("id").primaryKey(),
    requestId: text("request_id")
      .notNull()
      .references(() => rideRequests.id, { onDelete: "cascade" }),
    sourceMessageId: text("source_message_id").notNull(),
    reporterUserId: text("reporter_user_id")
      .notNull()
      .references(() => wayfareUsers.id, { onDelete: "restrict" }),
    category: text("category").$type<SupportCaseCategory>().notNull().default("other"),
    priority: text("priority").$type<SupportCasePriority>().notNull().default("normal"),
    status: text("status").$type<SupportCaseStatus>().notNull().default("open"),
    assignedTo: text("assigned_to").references(() => wayfareUsers.id, {
      onDelete: "set null",
    }),
    staffNote: text("staff_note"),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    uniqueIndex("support_cases_source_message_idx").on(table.sourceMessageId),
    index("support_cases_queue_idx").on(table.status, table.priority, table.createdAt),
    check(
      "support_cases_category_check",
      sql`${table.category} in ('safety', 'payment', 'driver', 'rider', 'lost_item', 'fare_review', 'other')`,
    ),
    check(
      "support_cases_priority_check",
      sql`${table.priority} in ('normal', 'high', 'urgent')`,
    ),
    check(
      "support_cases_status_check",
      sql`${table.status} in ('open', 'in_progress', 'resolved')`,
    ),
  ],
);

export const rideOffers = pgTable(
  "ride_offers",
  {
    id: text("id").primaryKey(),
    requestId: text("request_id")
      .notNull()
      .references(() => rideRequests.id, { onDelete: "cascade" }),
    driverId: text("driver_id")
      .notNull()
      .references(() => wayfareUsers.id, { onDelete: "cascade" }),
    amount: doublePrecision("amount").notNull(),
    status: text("status").notNull().default("pending"),
    driverProfile: jsonb("driver_profile").$type<Record<string, unknown>>().notNull(),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    uniqueIndex("ride_offers_request_driver_idx").on(table.requestId, table.driverId),
    index("ride_offers_request_idx").on(table.requestId, table.createdAt),
  ],
);

export const driverPayoutRecords = pgTable(
  "driver_payout_records",
  {
    id: serial("id").primaryKey(),
    driverId: text("driver_id")
      .notNull()
      .references(() => wayfareUsers.id, { onDelete: "restrict" }),
    provider: text("provider").notNull(),
    externalReference: text("external_reference").notNull(),
    amountMinor: integer("amount_minor").notNull(),
    currency: text("currency").notNull(),
    periodStart: text("period_start"),
    periodEnd: text("period_end"),
    batchId: text("batch_id"),
    transferStatus: text("transfer_status")
      .$type<PayoutTransferStatus>()
      .notNull()
      .default("not_started"),
    transferReference: text("transfer_reference"),
    transferCode: text("transfer_code"),
    transferRecipientCode: text("transfer_recipient_code"),
    transferRecipientName: text("transfer_recipient_name"),
    transferBankName: text("transfer_bank_name"),
    transferAccountLast4: text("transfer_account_last4"),
    transferAttemptCount: integer("transfer_attempt_count").notNull().default(0),
    transferError: text("transfer_error"),
    transferUpdatedAt: timestamp("transfer_updated_at", { withTimezone: true }),
    reconciliationStatus: text("reconciliation_status")
      .$type<PayoutReconciliationStatus>()
      .notNull()
      .default("unreconciled"),
    note: text("note"),
    recordedBy: text("recorded_by")
      .notNull()
      .references(() => wayfareUsers.id, { onDelete: "restrict" }),
    recordedAt: timestamp("recorded_at", { withTimezone: true })
      .notNull()
      .defaultNow(),
    reconciledAt: timestamp("reconciled_at", { withTimezone: true }),
  },
  (table) => [
    uniqueIndex("driver_payout_provider_reference_idx").on(
      table.provider,
      table.externalReference,
    ),
    index("driver_payout_driver_recorded_idx").on(table.driverId, table.recordedAt),
    index("driver_payout_batch_idx").on(table.batchId),
    index("driver_payout_reconciliation_idx").on(
      table.reconciliationStatus,
      table.recordedAt,
    ),
    uniqueIndex("driver_payout_transfer_reference_idx").on(table.transferReference),
    check("driver_payout_amount_check", sql`${table.amountMinor} > 0`),
    check("driver_payout_transfer_attempt_count_check", sql`${table.transferAttemptCount} >= 0`),
    check(
      "driver_payout_transfer_status_check",
      sql`${table.transferStatus} in ('not_started', 'sending', 'pending', 'success', 'failed', 'otp', 'unknown', 'reversed')`,
    ),
    check(
      "driver_payout_reconciliation_status_check",
      sql`${table.reconciliationStatus} in ('unreconciled', 'matched', 'exception')`,
    ),
  ],
);

export const payoutBatches = pgTable("payout_batches", {
  id: text("id").primaryKey(),
  periodStart: timestamp("period_start", { withTimezone: true }).notNull(),
  periodEnd: timestamp("period_end", { withTimezone: true }).notNull(),
  status: text("status")
    .$type<"open" | "submitted" | "processing" | "partially_paid" | "paid" | "failed">()
    .notNull()
    .default("open"),
  settlementMethod: text("settlement_method").$type<"paystack" | "external">(),
  totalAmountMinor: integer("total_amount_minor").notNull().default(0),
  driverCount: integer("driver_count").notNull().default(0),
  paidAt: timestamp("paid_at", { withTimezone: true }),
  reconciledAt: timestamp("reconciled_at", { withTimezone: true }),
  createdBy: text("created_by")
    .notNull()
    .references(() => wayfareUsers.id, { onDelete: "restrict" }),
  createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow(),
}, (table) => [
  check(
    "payout_batch_status_check",
    sql`${table.status} in ('open', 'submitted', 'processing', 'partially_paid', 'paid', 'failed')`,
  ),
  check(
    "payout_batch_settlement_method_check",
    sql`${table.settlementMethod} is null or ${table.settlementMethod} in ('paystack', 'external')`,
  ),
]);

export const driverPayoutRecipients = pgTable(
  "driver_payout_recipients",
  {
    driverId: text("driver_id")
      .primaryKey()
      .references(() => wayfareUsers.id, { onDelete: "restrict" }),
    recipientCode: text("recipient_code").notNull(),
    accountName: text("account_name").notNull(),
    accountLast4: text("account_last4").notNull(),
    bankName: text("bank_name").notNull(),
    currency: text("currency").notNull().default("ZAR"),
    registeredBy: text("registered_by")
      .notNull()
      .references(() => wayfareUsers.id, { onDelete: "restrict" }),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    index("driver_payout_recipient_code_idx").on(table.recipientCode),
    index("driver_payout_recipient_registered_idx").on(table.registeredBy, table.createdAt),
    check("driver_payout_recipient_last4_check", sql`${table.accountLast4} ~ '^[0-9]{4}$'`),
    check("driver_payout_recipient_currency_check", sql`${table.currency} = 'ZAR'`),
  ],
);

export const driverRequestSkips = pgTable(
  "driver_request_skips",
  {
    id: serial("id").primaryKey(),
    requestId: text("request_id")
      .notNull()
      .references(() => rideRequests.id, { onDelete: "cascade" }),
    driverId: text("driver_id")
      .notNull()
      .references(() => wayfareUsers.id, { onDelete: "cascade" }),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    uniqueIndex("driver_request_skips_request_driver_idx").on(table.requestId, table.driverId),
  ],
);

export const rideEvents = pgTable(
  "ride_events",
  {
    id: serial("id").primaryKey(),
    requestId: text("request_id")
      .notNull()
      .references(() => rideRequests.id, { onDelete: "cascade" }),
    actorId: text("actor_id")
      .references(() => wayfareUsers.id, { onDelete: "restrict" }),
    action: text("action").notNull(),
    details: jsonb("details").$type<Record<string, unknown>>().notNull().default({}),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [index("ride_events_request_created_idx").on(table.requestId, table.createdAt)],
);

export const ridePayments = pgTable(
  "ride_payments",
  {
    id: text("id").primaryKey(),
    requestId: text("request_id")
      .notNull()
      .references(() => rideRequests.id, { onDelete: "cascade" }),
    riderId: text("rider_id")
      .notNull()
      .references(() => wayfareUsers.id, { onDelete: "restrict" }),
    driverId: text("driver_id").references(() => wayfareUsers.id, { onDelete: "set null" }),
    provider: text("provider").notNull(),
    reference: text("reference").notNull(),
    paymentMethod: text("payment_method").notNull().default("card"),
    amountMinor: integer("amount_minor").notNull(),
    commissionMinor: integer("commission_minor").notNull().default(0),
    currency: text("currency").notNull(),
    status: text("status").notNull().default("pending"),
    authorizationUrl: text("authorization_url"),
    authorizedAt: timestamp("authorized_at", { withTimezone: true }),
    expiresAt: timestamp("expires_at", { withTimezone: true }),
    capturedAmountMinor: integer("captured_amount_minor"),
    paidAt: timestamp("paid_at", { withTimezone: true }),
    driverPayoutId: text("driver_payout_id"),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    uniqueIndex("ride_payments_reference_idx").on(table.reference),
    uniqueIndex("ride_payments_one_pending_per_trip_idx")
      .on(table.requestId)
      .where(sql`${table.status} = 'pending'`),
    index("ride_payments_rider_created_idx").on(table.riderId, table.createdAt),
    index("ride_payments_request_created_idx").on(table.requestId, table.createdAt),
  ],
);
export const wallets = pgTable(
  "wallets",
  {
    id: text("id").primaryKey(),
    userId: text("user_id")
      .notNull()
      .unique()
      .references(() => wayfareUsers.id, { onDelete: "cascade" }),
    balanceMinor: integer("balance_minor").notNull().default(0),
    currency: text("currency").notNull().default("ZAR"),
    createdAt: timestamp("created_at", { withTimezone: true })
      .notNull()
      .defaultNow(),
    updatedAt: timestamp("updated_at", { withTimezone: true })
      .notNull()
      .defaultNow(),
  },
  (table) => [index("wallets_user_idx").on(table.userId)],
);

export const walletTransactions = pgTable(
  "wallet_transactions",
  {
    id: text("id").primaryKey(),
    walletId: text("wallet_id")
      .notNull()
      .references(() => wallets.id, { onDelete: "cascade" }),
    userId: text("user_id")
      .notNull()
      .references(() => wayfareUsers.id, { onDelete: "restrict" }),
    type: text("type").notNull(),
    amountMinor: integer("amount_minor").notNull(),
    currency: text("currency").notNull(),
    reference: text("reference"),
    description: text("description"),
    createdAt: timestamp("created_at", { withTimezone: true })
      .notNull()
      .defaultNow(),
  },
  (table) => [
    index("wallet_transactions_user_created_idx").on(table.userId, table.createdAt),
    index("wallet_transactions_wallet_created_idx").on(table.walletId, table.createdAt),
  ],
);

export const walletTopups = pgTable(
  "wallet_topups",
  {
    id: text("id").primaryKey(),
    userId: text("user_id")
      .notNull()
      .references(() => wayfareUsers.id, { onDelete: "restrict" }),
    provider: text("provider").notNull(),
    reference: text("reference").notNull(),
    amountMinor: integer("amount_minor").notNull(),
    currency: text("currency").notNull(),
    status: text("status").notNull().default("pending"),
    authorizationUrl: text("authorization_url"),
    createdAt: timestamp("created_at", { withTimezone: true })
      .notNull()
      .defaultNow(),
    updatedAt: timestamp("updated_at", { withTimezone: true })
      .notNull()
      .defaultNow(),
  },
  (table) => [
    uniqueIndex("wallet_topups_reference_idx").on(table.reference),
    index("wallet_topups_user_created_idx").on(table.userId, table.createdAt),
  ],
);

export const walletVouchers = pgTable(
  "wallet_vouchers",
  {
    id: text("id").primaryKey(),
    codeHash: text("code_hash").notNull(),
    amountMinor: integer("amount_minor").notNull(),
    currency: text("currency").notNull().default("ZAR"),
    status: text("status").notNull().default("issued"),
    createdBy: text("created_by").references(() => wayfareUsers.id, {
      onDelete: "set null",
    }),
    createdAt: timestamp("created_at", { withTimezone: true })
      .notNull()
      .defaultNow(),
    expiresAt: timestamp("expires_at", { withTimezone: true }),
    redeemedBy: text("redeemed_by").references(() => wayfareUsers.id, {
      onDelete: "set null",
    }),
    redeemedAt: timestamp("redeemed_at", { withTimezone: true }),
  },
  (table) => [
    uniqueIndex("wallet_vouchers_code_hash_idx").on(table.codeHash),
    index("wallet_vouchers_status_created_idx").on(table.status, table.createdAt),
    index("wallet_vouchers_redeemed_by_idx").on(table.redeemedBy, table.redeemedAt),
    check("wallet_vouchers_amount_check", sql`${table.amountMinor} > 0`),
    check(
      "wallet_vouchers_status_check",
      sql`${table.status} in ('issued', 'redeemed', 'revoked')`,
    ),
    check(
      "wallet_vouchers_redeemed_fields_check",
      sql`(${table.status} = 'redeemed' and ${table.redeemedBy} is not null and ${table.redeemedAt} is not null) or (${table.status} <> 'redeemed' and ${table.redeemedBy} is null and ${table.redeemedAt} is null)`,
    ),
  ],
);
export type ConversationType =
  | "rider_driver"
  | "rider_support"
  | "driver_support"
  | "admin_incident"
  | "safety_escalation"
  | "system"
  | "internal_note";

export type MessageDeliveryState = "sending" | "sent" | "delivered" | "read" | "failed";

export const idempotencyKeys = pgTable(
  "idempotency_keys",
  {
    id: text("id").primaryKey(),
    key: text("key").notNull(),
    userId: text("user_id").references(() => wayfareUsers.id, { onDelete: "cascade" }),
    scope: text("scope").notNull(),
    responseStatus: integer("response_status"),
    responseBody: jsonb("response_body").$type<Record<string, unknown>>(),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
    expiresAt: timestamp("expires_at", { withTimezone: true }).notNull(),
  },
  (table) => [
    uniqueIndex("idempotency_keys_scope_key_idx").on(table.scope, table.key),
    index("idempotency_keys_user_idx").on(table.userId, table.createdAt),
    index("idempotency_keys_expiry_idx").on(table.expiresAt),
  ],
);

export const conversations = pgTable(
  "conversations",
  {
    id: text("id").primaryKey(),
    type: text("type").$type<ConversationType>().notNull(),
    rideRequestId: text("ride_request_id").references(() => rideRequests.id, { onDelete: "cascade" }),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow(),
    deletedAt: timestamp("deleted_at", { withTimezone: true }),
  },
  (table) => [
    index("conversations_ride_idx").on(table.rideRequestId, table.createdAt),
    check(
      "conversations_type_check",
      sql`${table.type} in ('rider_driver', 'rider_support', 'driver_support', 'admin_incident', 'safety_escalation', 'system', 'internal_note')`,
    ),
  ],
);

export const conversationParticipants = pgTable(
  "conversation_participants",
  {
    conversationId: text("conversation_id")
      .notNull()
      .references(() => conversations.id, { onDelete: "cascade" }),
    userId: text("user_id")
      .notNull()
      .references(() => wayfareUsers.id, { onDelete: "cascade" }),
    role: text("role").notNull().default("participant"),
    joinedAt: timestamp("joined_at", { withTimezone: true }).notNull().defaultNow(),
    lastReadAt: timestamp("last_read_at", { withTimezone: true }),
  },
  (table) => [
    uniqueIndex("conversation_participants_pk").on(table.conversationId, table.userId),
    index("conversation_participants_user_idx").on(table.userId),
  ],
);
export const messages = pgTable(
  "messages",
  {
    id: text("id").primaryKey(),
    conversationId: text("conversation_id")
      .notNull()
      .references(() => conversations.id, { onDelete: "cascade" }),
    senderUserId: text("sender_user_id").references(() => wayfareUsers.id, { onDelete: "set null" }),
    type: text("type").notNull().default("text"),
    content: text("content"),
    contentVersion: integer("content_version").notNull().default(1),
    deliveryState: text("delivery_state").$type<MessageDeliveryState>().notNull().default("sent"),
    providerEventId: text("provider_event_id"),
    moderationStatus: text("moderation_status").notNull().default("none"),
    attachmentRef: text("attachment_ref"),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
    editedAt: timestamp("edited_at", { withTimezone: true }),
    deletedAt: timestamp("deleted_at", { withTimezone: true }),
    retainedUntil: timestamp("retained_until", { withTimezone: true }),
  },
  (table) => [
    index("messages_conversation_created_idx").on(table.conversationId, table.createdAt),
    index("messages_sender_idx").on(table.senderUserId, table.createdAt),
    check(
      "messages_delivery_state_check",
      sql`${table.deliveryState} in ('sending', 'sent', 'delivered', 'read', 'failed')`,
    ),
  ],
);

export const pushTokens = pgTable(
  "push_tokens",
  {
    id: text("id").primaryKey(),
    userId: text("user_id")
      .notNull()
      .references(() => wayfareUsers.id, { onDelete: "cascade" }),
    token: text("token").notNull(),
    platform: text("platform").notNull(),
    appVersion: text("app_version"),
    locale: text("locale"),
    optIn: boolean("opt_in").notNull().default(true),
    invalidatedAt: timestamp("invalidated_at", { withTimezone: true }),
    verifiedAt: timestamp("verified_at", { withTimezone: true }),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    uniqueIndex("push_tokens_token_idx").on(table.token),
    index("push_tokens_user_idx").on(table.userId, table.updatedAt),
    check("push_tokens_platform_check", sql`${table.platform} in ('ios', 'android', 'web')`),
  ],
);



export const auditEvents = pgTable(
  "audit_events",
  {
    id: text("id").primaryKey(),
    actorUserId: text("actor_user_id").references(() => wayfareUsers.id, { onDelete: "set null" }),
    entityType: text("entity_type").notNull(),
    entityId: text("entity_id").notNull(),
    action: text("action").notNull(),
    previousState: jsonb("previous_state").$type<Record<string, unknown>>(),
    nextState: jsonb("next_state").$type<Record<string, unknown>>(),
    reason: text("reason"),
    requestId: text("request_id"),
    idempotencyKey: text("idempotency_key"),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    index("audit_events_entity_idx").on(table.entityType, table.entityId, table.createdAt),
    index("audit_events_actor_idx").on(table.actorUserId, table.createdAt),
  ],
);

export const accountDeletionRequests = pgTable(
  "account_deletion_requests",
  {
    id: text("id").primaryKey(),
    userId: text("user_id")
      .notNull()
      .references(() => wayfareUsers.id, { onDelete: "restrict" }),
    retainedUserId: text("retained_user_id").references(() => wayfareUsers.id, {
      onDelete: "restrict",
    }),
    pendingDocumentObjectKeys: jsonb("pending_document_object_keys")
      .$type<string[]>()
      .notNull()
      .default(sql`'[]'::jsonb`),
    pendingMessageAttachmentReferences: jsonb("pending_message_attachment_references")
      .$type<string[]>()
      .notNull()
      .default(sql`'[]'::jsonb`),
    status: text("status").notNull().default("requested"),
    requestedAt: timestamp("requested_at", { withTimezone: true }).notNull().defaultNow(),
    completedAt: timestamp("completed_at", { withTimezone: true }),
    reason: text("reason"),
  },
  (table) => [
    index("account_deletion_requests_user_idx").on(table.userId, table.requestedAt),
    check(
      "account_deletion_requests_status_check",
      sql`${table.status} in ('requested', 'processing', 'completed', 'rejected')`,
    ),
  ],
);

export const driverSubscriptionPlans = pgTable(
  "driver_subscription_plans",
  {
    id: text("id").primaryKey(),
    name: text("name").notNull(),
    description: text("description"),
    amountMinor: integer("amount_minor").notNull(),
    currency: text("currency").notNull().default("ZAR"),
    billingInterval: text("billing_interval")
      .$type<DriverSubscriptionBillingInterval>()
      .notNull(),
    renewalMode: text("renewal_mode")
      .$type<DriverSubscriptionRenewalMode>()
      .notNull()
      .default("manual"),
    paystackPlanCode: text("paystack_plan_code"),
    isActive: boolean("is_active").notNull().default(true),
    createdBy: text("created_by")
      .notNull()
      .references(() => wayfareUsers.id, { onDelete: "restrict" }),
    updatedBy: text("updated_by")
      .notNull()
      .references(() => wayfareUsers.id, { onDelete: "restrict" }),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    check("driver_subscription_plans_amount_check", sql`${table.amountMinor} > 0`),
    check(
      "driver_subscription_plans_interval_check",
      sql`${table.billingInterval} in ('daily', 'weekly', 'monthly', 'quarterly', 'biannually', 'annually')`,
    ),
    check(
      "driver_subscription_plans_renewal_mode_check",
      sql`${table.renewalMode} in ('manual', 'automatic')`,
    ),
    index("driver_subscription_plans_active_idx").on(table.isActive, table.createdAt),
  ],
);

// Current entitlement state is separate from driver_subscription_records, which
// remains the append-only history of successful and pending payment attempts.
export const driverSubscriptions = pgTable(
  "driver_subscriptions",
  {
    driverId: text("driver_id")
      .primaryKey()
      .references(() => wayfareUsers.id, { onDelete: "restrict" }),
    planId: text("plan_id").references(() => driverSubscriptionPlans.id, {
      onDelete: "restrict",
    }),
    status: text("status")
      .$type<DriverSubscriptionStatus>()
      .notNull()
      .default("pending"),
    renewalMode: text("renewal_mode")
      .$type<DriverSubscriptionRenewalMode>()
      .notNull()
      .default("manual"),
    amountMinor: integer("amount_minor").notNull().default(0),
    currency: text("currency").notNull().default("ZAR"),
    provider: text("provider"),
    startedAt: timestamp("started_at", { withTimezone: true }),
    expiresAt: timestamp("expires_at", { withTimezone: true }),
    nextPaymentAt: timestamp("next_payment_at", { withTimezone: true }),
    providerSubscriptionCode: text("provider_subscription_code"),
    providerEmailToken: text("provider_email_token"),
    providerPlanCode: text("provider_plan_code"),
    autoRenewConsentAt: timestamp("auto_renew_consent_at", { withTimezone: true }),
    autoRenewActive: boolean("auto_renew_active").notNull().default(false),
    autoRenewIssue: text("auto_renew_issue"),
    cancelAtPeriodEnd: boolean("cancel_at_period_end").notNull().default(false),
    cancelledAt: timestamp("cancelled_at", { withTimezone: true }),
    cancelledReason: text("cancelled_reason"),
    updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    index("driver_subscriptions_status_expiry_idx").on(table.status, table.expiresAt),
    index("driver_subscriptions_plan_status_idx").on(table.planId, table.status),
    uniqueIndex("driver_subscriptions_provider_code_idx").on(
      table.providerSubscriptionCode,
    ),
    check(
      "driver_subscriptions_renewal_mode_check",
      sql`${table.renewalMode} in ('manual', 'automatic')`,
    ),
  ],
);

export const driverPromotions = pgTable(
  "driver_promotions",
  {
    id: text("id").primaryKey(),
    code: text("code").notNull(),
    name: text("name").notNull(),
    discountType: text("discount_type")
      .$type<DriverPromotionDiscountType>()
      .notNull(),
    discountValue: integer("discount_value").notNull(),
    maxDiscountMinor: integer("max_discount_minor"),
    currency: text("currency").notNull().default("ZAR"),
    planId: text("plan_id").references(() => driverSubscriptionPlans.id, {
      onDelete: "restrict",
    }),
    startsAt: timestamp("starts_at", { withTimezone: true }).notNull(),
    endsAt: timestamp("ends_at", { withTimezone: true }).notNull(),
    serviceArea: text("service_area"),
    maxRedemptions: integer("max_redemptions"),
    newDriverOnly: boolean("new_driver_only").notNull().default(false),
    minimumCompletedRides: integer("minimum_completed_rides"),
    minimumRating: doublePrecision("minimum_rating"),
    isActive: boolean("is_active").notNull().default(true),
    createdBy: text("created_by")
      .notNull()
      .references(() => wayfareUsers.id, { onDelete: "restrict" }),
    updatedBy: text("updated_by")
      .notNull()
      .references(() => wayfareUsers.id, { onDelete: "restrict" }),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    uniqueIndex("driver_promotions_code_idx").on(table.code),
    index("driver_promotions_active_dates_idx").on(
      table.isActive,
      table.startsAt,
      table.endsAt,
    ),
    check(
      "driver_promotions_discount_check",
      sql`(${table.discountType} = 'fixed' and ${table.discountValue} > 0) or (${table.discountType} = 'percentage' and ${table.discountValue} between 1 and 100)`,
    ),
    check(
      "driver_promotions_discount_cap_check",
      sql`${table.maxDiscountMinor} is null or ${table.maxDiscountMinor} > 0`,
    ),
    check(
      "driver_promotions_date_check",
      sql`${table.endsAt} > ${table.startsAt}`,
    ),
    check(
      "driver_promotions_usage_check",
      sql`${table.maxRedemptions} is null or ${table.maxRedemptions} > 0`,
    ),
    check(
      "driver_promotions_completed_rides_check",
      sql`${table.minimumCompletedRides} is null or ${table.minimumCompletedRides} >= 0`,
    ),
    check(
      "driver_promotions_rating_check",
      sql`${table.minimumRating} is null or ${table.minimumRating} between 1 and 5`,
    ),
  ],
);

export const driverPromotionRedemptions = pgTable(
  "driver_promotion_redemptions",
  {
    id: text("id").primaryKey(),
    promotionId: text("promotion_id")
      .notNull()
      .references(() => driverPromotions.id, { onDelete: "restrict" }),
    driverId: text("driver_id")
      .notNull()
      .references(() => wayfareUsers.id, { onDelete: "restrict" }),
    externalReference: text("external_reference").notNull(),
    status: text("status")
      .$type<DriverPromotionRedemptionStatus>()
      .notNull()
      .default("pending"),
    discountMinor: integer("discount_minor").notNull(),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
    redeemedAt: timestamp("redeemed_at", { withTimezone: true }),
  },
  (table) => [
    uniqueIndex("driver_promotion_driver_redemption_idx").on(
      table.promotionId,
      table.driverId,
    ),
    uniqueIndex("driver_promotion_reference_idx").on(table.externalReference),
    index("driver_promotion_redemptions_usage_idx").on(
      table.promotionId,
      table.status,
    ),
    check(
      "driver_promotion_redemptions_status_check",
      sql`${table.status} in ('pending', 'redeemed', 'released')`,
    ),
    check(
      "driver_promotion_redemptions_discount_check",
      sql`${table.discountMinor} >= 0`,
    ),
  ],
);

// This aggregate is updated by the server when a rider submits a completed-ride
// rating; client-supplied profile fields never qualify a driver for a promo.
export const driverRatingRecords = pgTable("driver_rating_records", {
  driverId: text("driver_id")
    .primaryKey()
    .references(() => wayfareUsers.id, { onDelete: "restrict" }),
  averageRating: doublePrecision("average_rating").notNull(),
  ratingCount: integer("rating_count").notNull(),
  source: text("source").notNull().default("verified_rider_ratings"),
  updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow(),
});

export const driverSubscriptionRecords = pgTable(
  "driver_subscription_records",
  {
    id: text("id").primaryKey(),
    driverId: text("driver_id")
      .notNull()
      .references(() => wayfareUsers.id, { onDelete: "restrict" }),
    planId: text("plan_id").references(() => driverSubscriptionPlans.id, {
      onDelete: "restrict",
    }),
    provider: text("provider").notNull(),
    externalReference: text("external_reference").notNull(),
    amountMinor: integer("amount_minor").notNull(),
    currency: text("currency").notNull().default("ZAR"),
    status: text("status")
      .$type<DriverSubscriptionStatus>()
      .notNull()
      .default("pending"),
    startedAt: timestamp("started_at", { withTimezone: true }).notNull().defaultNow(),
    expiresAt: timestamp("expires_at", { withTimezone: true }),
    metadata: jsonb("metadata").$type<Record<string, unknown>>().notNull().default({}),
    cancelledAt: timestamp("cancelled_at", { withTimezone: true }),
    cancelledReason: text("cancelled_reason"),
  },
  (table) => [
    uniqueIndex("driver_subscription_provider_reference_idx").on(
      table.provider,
      table.externalReference,
    ),
    index("driver_subscription_records_driver_started_idx").on(
      table.driverId,
      table.startedAt,
    ),
  ],
);


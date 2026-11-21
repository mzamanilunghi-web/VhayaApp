# Vhaya Admin Backend API

This guide documents the API currently available to a separate Vhaya admin app.
It is based on the running API implementation; the OpenAPI file at
`backend/api-spec/openapi.yaml` currently describes only part of the admin API.

## Base URL

Production:

```text
https://vhaya--bonaw56197.replit.app/api
```

Use the API origin as the base URL and append the paths below. For example:

```text
GET https://vhaya--bonaw56197.replit.app/api/admin/overview
```

Local development uses the API server's configured host and port. Do not put
the production URL in the admin app's source if you need to switch environments;
use an environment-specific public API base URL.

## Authentication and admin access

The API uses Firebase Authentication. Every admin route requires:

1. A signed-in Firebase user with a verified email.
2. A valid Firebase ID token in `Authorization`.
3. A Vhaya profile whose role is `admin`.
4. A valid Vhaya account session header if the account has claimed a session.

After signing in with the Firebase client SDK, get an ID token and claim a Vhaya
session:

```http
POST /auth/session/claim
Authorization: Bearer <firebase-id-token>
Content-Type: application/json
```

The claim response contains a `sessionId`:

```json
{ "sessionId": "opaque-session-id" }
```

Send both headers on subsequent requests:

```http
Authorization: Bearer <fresh-firebase-id-token>
x-wayfare-session: <sessionId>
```

Use the Firebase SDK's `getIdToken()` when making requests so expired Firebase
tokens are refreshed. The Vhaya session ID is separate from the Firebase token.
The backend stores only a hash of the Vhaya session ID. Claiming a session
replaces the previous session for that account, so a second sign-in can
invalidate the first admin app session. To sign out, call
`DELETE /auth/session` with both headers.

Admin access is checked by the backend on every admin operation. Do not rely on
hiding admin screens in the client as an authorization control. Admin roles
must be provisioned server-side; there is no public endpoint to grant yourself
admin access. Role changes made through the admin API are synchronized to the
Firebase profile and the operations database.

For the first admin account, the API supports an initial-admin email allowlist
through `VHAYA_INITIAL_ADMIN_EMAILS` (or the legacy
`WAYFARE_INITIAL_ADMIN_EMAILS`) on the server. Configure the verified admin
email before that user creates their Vhaya profile. This bootstrap allowlist is
used only when a profile is first created; it does not promote an existing
profile.

### Example fetch helper

```ts
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;

async function adminFetch<T>(
  path: string,
  idToken: string,
  sessionId: string,
  init: RequestInit = {},
): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
      Authorization: `Bearer ${idToken}`,
      "x-wayfare-session": sessionId,
      ...init.headers,
    },
  });

  const body = await response.json().catch(() => null);
  if (!response.ok) {
    throw Object.assign(
      new Error(body?.error ?? `Request failed (${response.status})`),
      { status: response.status, body },
    );
  }
  return body as T;
}
```

If the admin app is hosted on a different origin, check the API's production
CORS configuration. If `CORS_ALLOWED_ORIGINS` is configured, include the exact
admin app origin. The admin API uses bearer tokens, not cookie-based login.

## Shared conventions

- All paths below are relative to `/api`.
- Request bodies and responses use JSON.
- Timestamps are not fully normalized: some are ISO date strings, while fields
  explicitly converted by an endpoint are Unix epoch milliseconds.
- Monetary fields ending in `Minor` are integer minor units. Format them using
  the accompanying ISO currency code; do not treat them as major currency units.
- List endpoints have server-side limits. Most admin lists are newest-first.
- Successful writes are often recorded in the admin review or ride event
  history. The API does not expose a general-purpose delete endpoint.

### Common errors

| Status | Meaning |
| --- | --- |
| `400` | Invalid query or request fields; response is usually `{ "error": "..." }`. |
| `401` | Missing, invalid, expired, or unverified sign-in. |
| `403` | Signed in, but not authorized as an admin. |
| `404` | Requested account, case, payment, or payout does not exist. |
| `409` | The requested transition conflicts with the current record state. |
| `429` | API rate limit reached; retry after the `Retry-After` header. |
| `500` | Unexpected API error; the client receives a generic error message. |
| `503` | Firebase identity data could not be loaded temporarily. |

## Admin endpoints

### Session and dashboard

#### `GET /admin/session`

Checks admin access and returns the signed-in Vhaya account:

```json
{ "userId": "firebase-uid", "displayName": "Admin Name", "email": "admin@example.com" }
```

#### `GET /admin/overview`

Returns dashboard counters:

```json
{
  "generatedAt": 1794820000000,
  "summary": {
    "users": 120,
    "suspendedUsers": 2,
    "rideRequests": 450,
    "activeRides": 3,
    "pendingDriverReviews": 4,
    "paymentIssues": 1,
    "activeSupportCases": 8,
    "urgentSupportCases": 2,
    "unassignedSupportCases": 3
  },
  "support": {
    "last30Days": 30,
    "byCategory": [{ "category": "payment", "count": 8 }],
    "byPriority": [{ "priority": "urgent", "count": 2 }]
  }
}
```

`activeRides` counts rides in `accepted`, `arrived`, or `in_progress`.
`paymentIssues` counts `capture_failed`, `release_failed`, `capture_pending`,
and `release_pending` payment states.

#### `GET /admin/activity?limit=50`

Returns up to 100 recent admin review and selected operations events:

```json
{
  "activity": [{
    "id": "review-123",
    "kind": "Driver review",
    "actor": "Admin Name",
    "subject": "Driver Name",
    "summary": "approved",
    "createdAt": 1794820000000
  }]
}
```

### Ride operations

#### `GET /admin/rides?q=&status=all&page=0&limit=25`

Searches ride requests by ride ID, pickup, destination, rider, or driver. Search
text must contain 2–100 characters. Status may be `all`, `requested`,
`countered`, `accepted`, `arrived`, `in_progress`, `completed`, `cancelled`, or
`unfulfilled`. Pages are zero-based, with up to 50 results per page.

Each result includes the rider and optional driver identity, route labels,
category, fare in minor currency units, scheduled pickup time, status, and
created/updated timestamps. The response also includes `hasMore`.

#### `POST /rides/:requestId/actions`

An admin may cancel a non-terminal ride using `{ "action": "adminCancel" }`.
The server rejects cancellations that conflict with the current ride status.

### People and account management

#### `GET /admin/people?q=&type=all&page=0&limit=25`

Searches people by display name or email. `q` may be omitted or empty; if
provided, use at least two and at most 100 characters. `type` is `all`,
`requesters`, or `drivers`. `page` is zero-based (0–1000); `limit` is 1–50.

```json
{
  "people": [{
    "userId": "firebase-uid",
    "displayName": "Example User",
    "email": "user@example.com",
    "role": "rider",
    "isSuspended": false,
    "suspendedReason": null,
    "personType": "requester",
    "rideCount": 7,
    "completedRideCount": 5,
    "lastRideAt": "2026-11-15T10:00:00.000Z",
    "driverProfile": null,
    "lastReviewOutcome": null,
    "lastReviewedAt": null,
    "updatedAt": "2026-11-15T10:00:00.000Z"
  }],
  "page": 0,
  "limit": 25,
  "hasMore": false
}
```

`personType` is currently `requester` or `driver`, based on the account's
current role. The API does not return deleted accounts in this listing.

#### `GET /admin/people/:userId`

Returns `{ "person": ..., "rides": [...], "reviews": [...] }`. Each ride includes
its ID, status, pickup and destination labels, whether the person participated
as `requester` or `driver`, fare, currency, and creation timestamp. Reviews
include the outcome, staff note, actor, and any role/application status changes.
Each related list is limited to 100 records.

#### `PATCH /admin/people/:userId/role`

Body:

```json
{ "role": "rider" }
```

Allowed roles: `rider`, `driver`, `admin`. Changing your own role is rejected.
Granting `driver` requires both an approved driver application and approved
verification. Removing the final admin is rejected. Demoting a driver also
sets the driver's online state to false.

#### `PATCH /admin/people/:userId/suspend`

Suspend:

```json
{ "suspend": true, "reason": "Policy violation" }
```

Unsuspend:

```json
{ "suspend": false }
```

A reason is required when suspending. An admin cannot suspend their own
account. Suspended users cannot request rides or receive payouts.

#### `POST /admin/people/:userId/reviews`

Body:

```json
{
  "subjectType": "driver",
  "outcome": "approved",
  "note": "Checks completed.",
  "checksConfirmed": true
}
```

`subjectType`: `requester` or `driver`. `outcome`: `reviewed`,
`needs_follow_up`, `approved`, or `rejected`. `note` is optional and up to 1,000
characters. `checksConfirmed` is optional and defaults to false.

Driver applications awaiting decision can be `approved`, `rejected`, or moved
to `needs_follow_up`; the latter two require a note. Approval requires
`checksConfirmed: true` and all required compliance checks complete. Requester
reviews may only use `reviewed` or `needs_follow_up`. A successful write returns
HTTP `201` and `{ "review": ..., "person": ... }`.

### Driver applications and compliance

#### `GET /admin/driver-applications`

Returns up to 100 applications in `submitted`, `under_review`, or
`needs_follow_up`, including applicant details, vehicle/application details,
four compliance checks, and `incompleteComplianceDocuments`.

This queue is limited to pending driver applications. Document files are
reviewed outside Wayfare (for example, copies received by email); no document
upload is required or exposed by the API. Staff record the result of each
off-platform check with the compliance endpoint below.

#### `GET /admin/driver-compliance`

Returns up to 300 driver profiles and their compliance documents, including
`incompleteDocuments`. A check may be marked `needs_follow_up` when it was
reviewed before the current application submission.

Document types:

```text
identity | driver_license | vehicle_registration | operating_permit
```

Check statuses:

```text
not_reviewed | verified | needs_follow_up | not_required
```

#### `PATCH /admin/people/:userId/compliance`

Body:

```json
{
  "documentType": "driver_license",
  "status": "verified",
  "expiresOn": "2027-12-31",
  "note": "Copy received by email; name and expiry checked."
}
```

`expiresOn` may be omitted, an ISO calendar date (`YYYY-MM-DD`), an empty
string, or `null`. Expiry dates are optional for documents that do not require
one; a driver licence marked `verified` must include its expiry date. An expired
document cannot be set to `verified`. `note` is optional and up to 500
characters; `not_required` requires a note. No uploaded file is needed to record
a staff verification. The server stores the staff reviewer, review time, note,
and audit entry. A driver application can only be approved after all required
checks are complete and a separate staff decision is saved.
Returns
`{ "document": { "documentType", "status", "expiresOn", "staffNote", "reviewedAt" } }`.

### Payments and wallets

#### `GET /admin/payments?limit=50`

Returns the latest 1–100 ride payment records:

```json
{
  "payments": [{
    "id": "payment-id",
    "tripId": "ride-id",
    "driverId": "driver-uid",
    "riderName": "Rider Name",
    "riderEmail": "rider@example.com",
    "provider": "paystack",
    "reference": "provider-reference",
    "paymentMethod": "card",
    "amountMinor": 10000,
    "commissionMinor": 1500,
    "capturedAmountMinor": 10000,
    "currency": "ZAR",
    "status": "paid",
    "createdAt": 1794820000000,
    "paidAt": 1794820000000,
    "tripStatus": "completed"
  }]
}
```

#### `GET /admin/wallets`

Returns wallet balances for all accounts that have a wallet:
`{ "wallets": [{ "walletId", "userId", "displayName", "email", "role", "balanceMinor", "currency", "createdAt", "updatedAt" }] }`.
The timestamps are epoch milliseconds. There is currently no admin endpoint to
edit a wallet balance.

#### `POST /admin/payments/:paymentId/refund`

Body:

```json
{ "reason": "Duplicate charge" }
```

Requires a reason of 1–500 characters and a payment in `paid` state. Returns
HTTP `202` with a refund request record. **This endpoint only records the
request in the operations event log; it does not issue a refund to Paystack or
confirm that funds were returned.** The UI should say “refund requested,” not
“refunded.”

### Support cases

#### `GET /admin/support-cases`

Returns up to 100 cases with reporter, original message, replies, ride summary,
priority, status, assignment, and staff note. Most recent cases appear first.

#### `PATCH /admin/support-cases/:caseId`

Send any supported fields that need updating:

```json
{
  "status": "in_progress",
  "priority": "high",
  "category": "payment",
  "assignedTo": "me",
  "staffNote": "Contacted the rider."
}
```

Allowed statuses: `open`, `in_progress`, `resolved`. Priorities: `normal`,
`high`, `urgent`. Categories: `safety`, `payment`, `driver`, `rider`,
`lost_item`, `fare_review`, `other`. `assignedTo` accepts `"me"` to assign to
the current admin or `null` to unassign. `staffNote` may be `null` or up to
1,000 characters. Returns `{ "case": ... }`.

#### `POST /admin/support-cases/:caseId/replies`

Body: `{ "message": "Your support response" }`. Message length is 1–2,000
characters. Returns HTTP `201` with `{ "reply": { "id", "sender", "channel", "message", "createdAt", "recipientUserId" } }`.
A resolved case must be reopened before replying.

### Payout operations

#### `GET /admin/payouts`

Returns up to 100 recorded payouts, newest first. Fields include driver,
provider, external reference, `amountMinor`, currency, payout period,
reconciliation status, note, and recorded/reconciled timestamps.

#### `POST /admin/payouts`

Records a completed external transfer; this does not make the transfer:

```json
{
  "driverId": "driver-uid",
  "provider": "bank_transfer",
  "externalReference": "bank-ref-12345",
  "amountMinor": 25000,
  "currency": "ZAR",
  "periodStart": "2026-11-01",
  "periodEnd": "2026-11-07",
  "note": "Weekly transfer",
  "confirmedExternalTransfer": true
}
```

Providers: `paystack`, `bank_transfer`, `cash`, `other`. The external reference
must be 3–128 safe characters. Amount must be a positive safe integer;
currency must be three uppercase letters. Dates are optional ISO calendar
dates. `confirmedExternalTransfer: true` is required, and suspended/non-driver
accounts cannot receive recorded payouts. Returns HTTP `201` with `{ "payout": ... }`.

#### `PATCH /admin/payouts/:payoutId`

Reconcile a payout:

```json
{ "reconciliationStatus": "matched", "note": "Matched to bank statement." }
```

Statuses accepted here: `matched` or `exception`. An exception requires a
note (up to 1,000 characters). Returns `{ "payout": ... }`.

#### `POST /admin/payouts/run-weekly`

Creates a payout batch and payout records for eligible paid rides in the
specified interval:

```json
{
  "periodStart": "2026-11-09T00:00:00.000Z",
  "periodEnd": "2026-11-16T00:00:00.000Z"
}
```

The end must be after the start. A second batch for the same exact interval
returns `409`. This operation calculates net driver payouts (captured amount
minus commission) and marks the included ride payments as assigned to payouts.
It **does not send money**. Returns HTTP `201` with a `batch` and per-driver
totals.

#### `GET /admin/payouts/batches`

Returns up to 100 batches, newest first. Fields include period, status,
`totalAmountMinor`, driver count, paid/reconciled timestamps, creator.

#### `PATCH /admin/payouts/batches/:batchId/approve`

Approves an `open` batch by changing it to `submitted`. Only open batches can
be approved. This is an operations state change, not a bank transfer.

#### `PATCH /admin/payouts/batches/:batchId`

Body: `{ "status": "paid" }`. Allowed values are `open`, `submitted`, `paid`.
Setting `paid` stamps `paidAt` if it has not already been set. Use only after
the external disbursement is confirmed.

### Driver subscriptions and promotions

Subscription administration is available in the admin app at `/subscriptions`.
All monetary values in the API are integer minor units (for ZAR, cents). Admin
changes to current access are audited and do not charge a driver or modify
payment history.

#### `GET /admin/driver-subscription-plans`

Returns all plans, including paused plans:

```json
{
  "plans": [{
    "id": "plan-id",
    "name": "Monthly access",
    "description": null,
    "amountMinor": 29900,
    "currency": "ZAR",
    "billingInterval": "monthly",
    "renewalMode": "manual",
    "isActive": true,
    "activeSubscriptionCount": 4
  }]
}
```

Supported intervals are `daily`, `weekly`, `monthly`, `quarterly`,
`biannually`, and `annually`. Renewal mode is `manual` or `automatic`. Create a
plan with `POST /admin/driver-subscription-plans` using those fields, omitting
`id` and `activeSubscriptionCount`. `PATCH
/admin/driver-subscription-plans/:planId` accepts any plan field. Pausing a plan
blocks new checkouts, but does not end existing access. A plan's price,
interval, or renewal mode cannot be changed while it has active Paystack
automatic renewals; stop or reassign those renewals first.

#### `GET /admin/driver-subscriptions?q=&page=0&limit=25`

Searches drivers by name, email, or ID. Returns each driver's current
subscription, last payment summary, and server-maintained verified rating
aggregate. Legacy payments without an expiry are shown as expired until an
admin assigns an explicit end date.

#### `PATCH /admin/driver-subscriptions/:driverId`

Update an existing subscription or assign current access to a driver:

```json
{
  "planId": "plan-id",
  "status": "active",
  "expiresAt": "2027-01-01T23:59:59.000Z",
  "reason": "Approved access extension"
}
```

An active subscription requires a plan and a future end date. Assigning a new
subscription requires `planId`, `status: "active"`, and `expiresAt`. Changes to
an automatic Paystack subscription first disable future provider charges; if
Paystack does not confirm cancellation, the update is rejected. Open payment
checkouts cannot be changed by admin until checkout completes or the driver
cancels it. Admin changes never enroll a driver into automatic renewal or
create a payment.

#### `GET /admin/driver-promotions`

Returns promotions and counts of pending or successful redemptions.

#### `POST /admin/driver-promotions` and `PATCH /admin/driver-promotions/:promotionId`

Promotion fields include a unique `code`, `name`, `discountType` (`fixed` or
`percentage`), `discountValue`, optional `maxDiscountMinor`, `currency: "ZAR"`,
optional `planId`, `startsAt`, `endsAt`, optional exact `serviceArea`,
`maxRedemptions`, `newDriverOnly`, `minimumCompletedRides`, and `minimumRating`.
Percentages are limited to 100. A promotion can be paused without deleting its
redemption history. Eligibility is rechecked by the server at checkout; the
client cannot supply a price or claim eligibility.

For driver-facing endpoints, checkout behavior, and Paystack webhook setup,
see [Driver subscriptions API](DRIVER_SUBSCRIPTIONS_API.md).

## Suggested admin app screens

The current API supports these sections:

1. **Dashboard:** `/admin/overview`, `/admin/activity`
2. **People:** `/admin/people`, `/admin/people/:userId`, role and suspension
   updates, review history
3. **Driver reviews:** driver applications, compliance checks, review decisions
4. **Support:** case queue, assignment/status/priority updates, staff replies
5. **Payments:** payment list, wallet balances, refund-request logging
6. **Payouts:** manual payout records, reconciliation, weekly batch review
7. **Subscriptions:** driver plans, current access, automatic-renewal state,
   and promotion eligibility rules

Keep irreversible or financially significant actions behind a confirmation
step. In particular, label refund records as requests and label payout actions
according to the fact that actual transfer settlement happens outside this API.

## Related source-of-truth files

- `backend/api-server/src/routes/admin.ts` — primary admin operations
- `backend/api-server/src/routes/payments.ts` — admin refund request
- `backend/api-server/src/routes/wallets.ts` — wallet listing
- `backend/api-server/src/middlewares/firebaseIdentity.ts` — Firebase and
  Vhaya session authentication
- `backend/api-server/src/middlewares/wayfareRoles.ts` — role lookup
- `backend/api-spec/openapi.yaml` — partial OpenAPI contract

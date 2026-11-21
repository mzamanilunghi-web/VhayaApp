# Driver subscriptions API

This contract is for the separate Vhaya driver app. The driver app is not part
of this workspace; all prices, access periods, and promotion eligibility are
calculated by the server.

## Authentication

Use the signed-in driver's Firebase ID token in the `Authorization` header and
the Vhaya session in `x-wayfare-session` when a session has been claimed. The
driver must have the `driver` role.

All paths below are relative to the API base URL ending in `/api`.

## List plans and promotion offers

### `GET /driver/subscription/offers`

Returns active plans with promotion eligibility evaluated from trusted
server-side data. The server checks promotion dates, plan restrictions, usage
limits, new-driver status, completed rides, the driver's service city, and
verified rider ratings. Missing verified rating data does not qualify a driver
for a minimum-rating rule.

```json
{
  "plans": [{
    "plan": {
      "id": "plan-id",
      "name": "Monthly access",
      "description": "Driver access for one month",
      "amountMinor": 29900,
      "currency": "ZAR",
      "billingInterval": "monthly",
      "renewalMode": "automatic",
      "isActive": true
    },
    "promotions": [{
      "id": "promotion-id",
      "code": "NEWDRIVER",
      "name": "New driver offer",
      "discountType": "percentage",
      "discountValue": 20,
      "discountMinor": 5980,
      "discountedAmountMinor": 23920,
      "currency": "ZAR",
      "eligible": true,
      "reasons": []
    }]
  }],
  "rating": {
    "averageRating": 4.8,
    "ratingCount": 12,
    "source": "verified_rider_ratings"
  },
  "completedRideCount": 18,
  "serviceArea": "Cape Town"
}
```

The response may include ineligible promotions with `eligible: false` and a
human-readable `reasons` list. Only submit an eligible promotion code at
checkout; eligibility is checked again before payment.

## Start checkout

### `POST /driver/subscription/initialize`

Request:

```json
{
  "planId": "plan-id",
  "promotionCode": "NEWDRIVER",
  "autoRenewConsent": true
}
```

`planId` is required. `promotionCode` is optional. `autoRenewConsent` must be
`true` for an automatic-renewal plan; the API rejects automatic checkout without
that explicit consent. The app must not send an amount, price, currency, or
expiry date.

The API charges the selected plan's server-set price, less any eligible
one-time promotion. For automatic renewal, the first charge uses the
discounted amount and Paystack's subscription begins at the end of that paid
period using the undiscounted plan price. A successful initial charge does not
activate renewal unless Paystack returns the subscription details and the
server confirms the provider subscription was created.

Example pending Paystack response:

```json
{
  "provider": "paystack",
  "mode": "paystack",
  "payment": {
    "id": "payment-record-id",
    "reference": "driver-subscription-reference",
    "amountMinor": 23920,
    "currency": "ZAR",
    "status": "pending",
    "paidAt": null,
    "expiresAt": null,
    "createdAt": 1794820000000
  },
  "subscription": {
    "driverId": "driver-id",
    "planId": "plan-id",
    "planName": "Monthly access",
    "status": "pending",
    "renewalMode": "automatic",
    "autoRenewActive": false,
    "autoRenewIssue": null,
    "cancelAtPeriodEnd": false,
    "amountMinor": 23920,
    "currency": "ZAR",
    "provider": "paystack",
    "startedAt": null,
    "expiresAt": null,
    "nextPaymentAt": null,
    "autoRenewConsentAt": 1794820000000
  },
  "authorizationUrl": "https://checkout.paystack.com/..."
}
```

Open `authorizationUrl` to complete payment. Do not treat a redirect back to
the app as proof of payment; wait for `GET /driver/subscription` to report
active access. The server confirms payments from the signed Paystack webhook at
`POST /payments/webhook`. Configure this endpoint as the Paystack webhook URL
for the API deployment.

In local mock-payment mode, checkout activates a manual test term immediately.
Automatic charges are not simulated in mock mode, and the response reports
automatic renewal as unavailable.

## Read current access

### `GET /driver/subscription`

Returns `{ "subscription": null }` if no subscription exists, or the current
subscription with `status`, plan, renewal, paid-through date, next payment
date, and any `autoRenewIssue`. A pending payment is not active access.

Clients should show access through `expiresAt`; `autoRenewActive` indicates
whether the provider has confirmed future charges. `renewalMode: "automatic"`
alone is not proof that auto-renewal is active.

## Stop automatic renewal

### `POST /driver/subscription/cancel`

The request body is empty. For Paystack subscriptions, the API disables future
provider charges before saving cancellation. If Paystack does not confirm, the
API returns an error and leaves the stored renewal state unchanged.

The current paid period remains available until `expiresAt`. The response
changes the renewal mode to `manual`, sets `autoRenewActive` to `false`, and
sets `cancelAtPeriodEnd` when there is a paid active term. A pending checkout
is marked cancelled locally; if its one-time payment nevertheless settles,
the paid period is honored but automatic renewal is not enabled.

## Promotion qualifications

The server applies these rules when an admin configures them:

- `newDriverOnly`: the driver has no completed rides.
- `minimumCompletedRides`: the driver has at least the configured number of
  completed rides.
- `minimumRating`: the driver has enough verified ratings and their average
  meets the threshold.
- `serviceArea`: the driver's profile city matches the configured area.
- `startsAt` / `endsAt`: checkout occurs inside the promotion window.
- `planId`: the selected plan matches the promotion's plan restriction.
- `maxRedemptions`: pending checkouts and successful redemptions count toward
  the limit.

A promotion discount applies to the initial paid term only. A fully discounted
manual plan can start without a provider charge; a fully discounted
automatic-renewal checkout is rejected because Paystack needs a paid
authorization for future charges.

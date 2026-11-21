# Vhaya account deletion

This backend provides the account-deletion API and public request page for rider and driver accounts. The Android app is maintained in a separate project and must connect its account-settings screen to this API.

## Android API contract

Use the authenticated Vhaya API base URL and the same Firebase bearer token and session header as other signed-in requests.

```http
DELETE /api/account
Content-Type: application/json
Authorization: Bearer <Firebase ID token>
```

```json
{ "confirmation": "DELETE" }
```

- `200`: deletion finished. Sign the user out and clear local account/session data.
- `202`: identity cleanup is still processing. Sign the user out, retain the returned request reference for status checks, and do not retry by creating another request.
- `409`: an active ride or driver subscription must be resolved first.
- `410`: this account has already been deleted; clear local credentials and return to sign-in.

The API never accepts a target user ID from the client; it deletes only the authenticated rider or driver. Admin accounts cannot use this endpoint.

## Public deletion page

`GET /api/account-deletion` serves the public web page for users who have uninstalled the Android app. It accepts an account email, returns the same response whether or not that email matches an account, and gives the requester a private reference to check status:

- `POST /api/account-deletion/requests`
- `GET /api/account-deletion/requests/{requestId}`

Web requests are queued for staff ownership verification; they do not automatically delete an account. No email provider is configured, so the current flow uses the private request reference rather than sending email.

## Staff processing

The staff console has a deletion-request queue. Before completing a web request, staff must verify account ownership and confirm active rides and provider subscriptions have been resolved. A request that cannot be completed can be rejected with a reason; the public status page shows a generic result and does not disclose account details.

If Firebase Auth or Firestore cleanup fails after the profile has been scrubbed, the API leaves a minimal deleted-account lockout record and marks the request as processing. Staff can retry completion from the queue. Deleted accounts are blocked from future API requests, including requests made with previously issued tokens.

## Data handling

Deletion removes Firebase Authentication and Firestore profile data; the Vhaya profile and driver application/compliance records and their app-stored document files; active sessions and push tokens; authored message text and app-stored message attachments; and rider pickup, destination, coordinates, and route data. Message attachment references hosted outside Vhaya are removed from Vhaya, but the external host controls whether the referenced file itself is deleted.

Some operational records are retained in pseudonymized form for payment and payout reconciliation, safety incidents, disputes, and audit requirements. Examples include ride/payment totals, safety/dispute records, and audit history. The public page discloses these retention categories. The product owner must set and publish the applicable retention periods and legal grounds in the privacy policy; this implementation does not decide those periods.

## Store submission checklist

The backend does not edit either store's console or the separate Android app. Before submission:

1. Add a discoverable in-app account-deletion action in the Android app, call `DELETE /api/account`, show the confirmation and any `409` reason, then sign out on `200` or `202`.
2. Add the published public URL ending in `/api/account-deletion` to the Play Console account-deletion field, and verify it works without sign-in or reinstalling the app.
3. Complete Play Console Data safety answers and ensure the app's privacy policy describes the same deletion and retention behavior.
4. Staff the request queue and choose a deletion-processing timeframe to publish. The current page does not promise a timeframe because no operations SLA was provided.
5. If the same service supports an iOS app, provide an in-app deletion action there as well.

Policy references:

- [Google Play account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en)
- [Apple App Review account-deletion guideline](https://developer.apple.com/help/app-review/guideline-reference/5-1-1-account-deletion)

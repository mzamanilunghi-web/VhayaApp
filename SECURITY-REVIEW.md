# Wayfare Production Readiness and Security Review

**Date:** 2026-10-28

## Security findings

| # | Severity | File | Lines | Vulnerability | Confidence |
|---|----------|------|-------|---------------|------------|
| 1 | 🟡 MEDIUM | `backend/api-server/src/routes/wallets.ts` | 91-108 | The wallet top-up endpoint accepts and forwards a caller-provided `callbackUrl` to Paystack. This can redirect users to an attacker-controlled URL after payment initiation. | 8/10 |

### Recommended remediation

Use one server-configured HTTPS callback URL for wallet top-ups and reject or ignore the client-supplied callback value. Alternatively, enforce a strict callback-origin allowlist.

## Missing production evidence and security gaps

- Firebase authorized domains, identity-token rules, console settings, and session controls were not available for review.
- Firestore rules and database exposure, backup, TLS, migration, and least-privilege credential configuration could not be verified.
- CORS, proxy trust, TLS termination, security headers, and production environment provisioning require deployment verification.
- Paystack live/test key separation, webhook registration, callback configuration, and dashboard settings could not be verified.
- Provisioning of `WAYFARE_INITIAL_ADMIN_EMAILS`, `SESSION_SECRET`, `FIREBASE_SERVICE_ACCOUNT_JSON`, `PAYSTACK_SECRET_KEY`, and `DATABASE_URL` could not be confirmed.

## Production readiness gaps

1. **Launch scope remains explicitly draft:** pricing, cancellation, driver onboarding, support operations, and business policy are not approved for launch.
2. **Document verification is blocked:** secure identity/license/vehicle document storage, retention, access auditing, and third-party verification are not implemented.
3. **Support delivery is incomplete:** replies are stored and visible in-app, but notifications, unread state, ownership, escalation, SLAs, and staffed operations need definition.
4. **Money movement is incomplete:** automated driver payouts, provider-confirmed refunds, recipient verification, payout reconciliation, and idempotency protections are not complete.
5. **API hardening is incomplete:** CORS is unrestricted, rate limiting and abuse protection are absent, and no deployment-level security-header configuration is present.
6. **Operational verification is incomplete:** no database migration workflow, deployment manifests, CI workflow, release checklist, alerting, crash reporting, or backup/restore procedure is present in the repository.
7. **Testing coverage is not production-ready:** integration tests require external database environment variables, the operations test may skip silently, and no general automated unit/API/mobile suite is wired into a default test script.
8. **Mobile packaging gaps remain:** app configuration has no explicit native bundle/package identifiers, signing setup, store metadata, privacy manifests, or store-build workflow reviewed here.
9. **Health and observability gaps remain:** the health endpoint is shallow, structured logging exists, but metrics, tracing, alert thresholds, uptime monitoring, and incident/runbooks are not evidenced.
10. **Static web serving gaps remain:** the standalone web server lacks security headers, caching policy, SPA-aware routing review, CSP, and a production reverse-proxy setup.
11. **Large monolithic UI:** the main Expo route is approximately 9,000 lines and contains mock, rider, driver, and admin flows; this increases regression risk and makes focused testing difficult.
12. **Prototype artifacts are present:** mock payments and demo-support messaging remain in the application; production configuration and code paths must clearly isolate and disable them outside development.

## Validation performed

- Repository working tree was clean before review.
- The workspace build was started through Corepack but stopped while dependencies were still being downloaded, so typecheck/build completion could not be verified in this review.
- The ride concurrency test could not start because dependencies were not installed (`esbuild` was missing).

## Recommended launch blockers

1. Replace the wallet callback URL behavior with a server-controlled callback.
2. Complete legal and regulatory approval of pricing, cancellation, ride, payment, identity, retention, support, and safety policies.
3. Implement private document verification or formally disable driver onboarding.
4. Define production payment, payout, refund, and reconciliation workflows.
5. Configure TLS, restricted CORS, security headers, rate limiting, secrets, backups, migrations, monitoring, and alerting.
6. Add native app identifiers/signing/store configuration and CI-backed release checks.
7. Add focused automated tests for authorization, payment state transitions, wallet ownership, support privacy, rider/driver transitions, and admin actions.
8. Replace mock/demo behavior with production feature gates and verify mock modes are impossible in production.

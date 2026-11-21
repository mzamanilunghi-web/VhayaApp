# Competitor Feature Report — Ride-Hailing Support, Safety & Post-Trip

**Date:** 2026-10-21
**Scope:** Compare Wayfare-Mobile against Uber, Bolt, Lyft, inDrive (and industry best practices) for post-trip issue reporting, in-trip safety, and support operations.
**Sources:** Uber Help/Rider Safety, Lyft Help ride-history, Bolt Kenya support guide, TechCabal complaint-handling analysis, UberApps incident-reporting best practices.

## 1. Executive summary

Wayfare already has a solid foundation: riders can report issues on completed/cancelled/unfulfilled trips (category, priority, message), riders have a Support view showing reported text/priority/status + staff replies, and admins have a queue with assignment, status, staff notes, and replies. Support cases are also created when either party sends a channel:support trip message.

Gaps vs. competitors are concentrated in in-trip safety (emergency button, trip sharing, trusted contacts, audio recording), lost and found + fare review/refund flows, evidence attachments, SLA/audit and analytics, and driver-facing support. Addressing the P0/P1 items below would bring Wayfare to parity and exceed competitors on transparency (status + replies already surfaced to riders).

## 2. What competitors offer

### Uber
- Emergency Button in Safety Toolkit calls 911 with auto-shared live location, vehicle info, license plate (auto-shared in select cities).
- 24/7 incident support specially trained for urgent safety issues.
- Share My Trip + Trusted Contacts with reminders.
- Number privacy (in-app calling masks numbers).
- Community Guidelines enforcement; loss of access for violators.
- Background checks (annual reruns in US).
- Insurance coverage for covered accidents.
- Lost item recovery via driver contact; fare review/refunds via Help.
- Audio recording (region-dependent) for safety.

### Bolt
- Per-trip Get help / Support in ride history.
- Issue categories: overcharging/incorrect fares, driver behaviour, safety concerns, lost items, technical/app issues.
- Links complaint directly to trip data for faster resolution.
- Email + web portal fallback channels.
- Driver suspension/removal based on reports.
- Fast in-app resolution (often within minutes).

### Lyft
- Ride history with map overview, receipts, export (CSV/email Ride Report), tips up to 72h after payment.
- Lost and found: message driver directly with item description + phone; Contact Support if phone lost.
- Price review: select what went wrong + details.
- Receipts emailed within 24h; cancellation fees hidden, damage fees shown.

### inDrive (negative example)
- Complaints answered with generic we will look into it; chat auto-closes if user idle -> poor UX. Lesson: keep threads open, set SLAs, follow up.
## 3. Wayfare current state (baseline)

Already implemented:
- Rider Report an issue on completed/cancelled/unfulfilled trips (category, priority, message).
- GET /support/cases returns rider original message (resolved from sourceMessageId), priority, status, staff replies.
- Rider Support cards show reported text, priority, status, replies.
- Admin queue: list + assignment, status, staff note, replies, reporter, trip.
- Admin operations snapshot: live account, ride, driver-review, payment, and support totals; 30-day support intake by category and priority.
- Admin activity feed: account/driver review, role, payout, refund, ride-cancellation, and support-case actions from recorded staff events.
- Support cases created from trip chat (channel: support).
- Typecheck clean (api-server + wayfare).

Not present:
- In-trip emergency button / 911 (or local emergency) dial with trip details.
- Trip sharing / trusted contacts.
- Lost and found flow (message driver, item description).
- Fare review / refund request with receipt export.
- Evidence attachments (photos/receipts) on reports.
- Driver-facing report/support view.
- Response/resolution SLA targets, overdue-case escalation, and auto-close handling.
- Deeper support analytics such as resolution rates, driver/route trends, and fraud signals.
- AI triage/prioritization, duplicate/spam filtering.
- Notifications on case updates.

## 4. Recommended roadmap

### P0 - Safety and trust (ship first)
1. Emergency assistance button (already opens phone app for 112) - upgrade to in-app Safety Toolkit showing live location + vehicle/plate + trip id, one-tap call; auto-include in support case.
2. Share My Trip to trusted contacts with live link + ETA; trusted contacts list (device/server).
3. Number privacy - ensure all rider-driver calls go through masked/VoIP numbers (if not already).
4. Report during active trip (not just post-trip) for safety incidents; auto-create high/urgent case + notify admin.

### P1 - Post-trip support parity
5. Lost and found: I left an item -> message driver (masked) with description; fallback to support case if no response in N hours.
6. Fare review / refund request: category payment with receipt + amount; admin approve/deny with refund note.
7. Receipt export: per-trip and bulk CSV/PDF Ride Report (like Lyft) including map overview.
8. Tip driver up to 72h after payment (if payments support tips).
9. Evidence attachments (photos/receipts) on support cases; admin view + download.
10. Driver-facing support: driver dashboard view of own cases + ability to report issues on trips.

### P2 - Operations and compliance
11. SLA tracking: first-response time, resolution time; auto-escalate overdue; keep threads open (avoid inDrive auto-close).
12. Audit log of case status/assignment/note changes (who, when).
13. Notifications: push/email to rider/driver/admin on new reply/status change.
14. Analytics dashboard: case volume by category/priority, resolution rate, top drivers/routes, fraud signals.
15. AI triage: auto-classify category/priority, detect duplicates/spam, suggest replies.
16. Compliance exports: auditable incident records for regulators (GDPR-safe data handling).
17. Community Guidelines enforcement workflow: warnings, suspension, removal with audit.

### P3 - Polish
18. Rating follow-up: prompt rating if missing; allow edit within window.
19. Cancelled/unfulfilled trip fee transparency in receipt.
20. In-app help center/FAQ before creating a case (deflect common questions).

## 5. Suggested data model changes

- support_cases: add attachmentUrls text[], slaDueAt timestamp, firstResponseAt timestamp, resolvedAt timestamp, channel (in_app/email/phone), severity.
- support_case_events (audit): caseId, actorUserId, actorRole, action, from, to, createdAt.
- trusted_contacts: userId, name, phone/email, createdAt.
- trip_share_links: tripId, token, expiresAt.
- lost_items: tripId, reporterUserId, description, status, driverResponseAt.
- fare_reviews: tripId, requesterUserId, reason, requestedAmount, status, resolution.

## 6. API additions (sketch)

- POST /rides/:id/support (already via actions) - extend to accept attachments + duringTrip.
- POST /support/cases/:id/attachments
- GET /support/cases/:id/events
- POST /trips/:id/share and GET /trips/share/:token
- POST /trips/:id/lost-item
- POST /trips/:id/fare-review
- GET /trips/:id/receipt (PDF/CSV)
- GET /admin/support-cases/analytics

## 7. UX principles (learned from competitors failures)

- Never auto-close a conversation on idle; send reminders and keep thread open (inDrive anti-pattern).
- Always show status + last update to the reporter (Wayfare already does this - keep).
- Link complaint to trip data automatically (Bolt pattern).
- Provide fallback channels (email/portal) when app unavailable.
- Confirm receipt of report with ETA for response.
- Make emergency path one tap, always visible during active trip.

## 8. Immediate next steps (engineering)

1. Add attachments to existing report form (smallest win, high value).
2. Add during trip reporting + urgent auto-priority.
3. Implement Share My Trip + trusted contacts.
4. Lost and found + fare review endpoints/UI.
5. SLA + audit + notifications.
6. Driver support view.

End of report.


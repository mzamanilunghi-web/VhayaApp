# Vhaya requester experience — desk research

**As of:** 20 October 2026  
**Market focus:** Polokwane, South Africa  
**Scope:** Review the rider/requester journey against current Vhaya behavior and public local-market benchmarks. This is desk research, not direct customer research or legal advice.

## Summary

Vhaya already covers the basic request journey: route-based estimates, rider-entered fares, scheduled requests, driver offers, trip status, pickup PINs, and a rider-facing driver location during an active trip. The largest opportunities are not more ride categories; they are making the existing core journey dependable and trustworthy.

1. **Make the fare and cancellation terms clear and locally approved before request confirmation.**
2. **Prove driver availability and pickup reliability in the actual launch area before promising a fast ride.**
3. **Connect the safety and support controls to real people and notifications before presenting them as live.**
4. **Research local payment preferences before adding payment methods or settlement complexity.**

No direct Polokwane rider interviews or representative rider survey were available in this review. Competitor pages describe their own products and are benchmarks, not independent evidence that every feature is available to every local rider.

## Current Vhaya behavior checked in the project

- The draft estimate is **R20 per route kilometre across categories**, with no base, time, or minimum charge. Riders can also propose a fare. These are prototype settings, not an approved local tariff. See `artifacts/wayfare/lib/launch-policy.ts`.
- Card is the only enabled payment method; development uses mock payment results. Live Paystack checkout needs server configuration. Cash is disabled. See `artifacts/wayfare/lib/launch-policy.ts`.
- Cancellation fees are now disabled until local launch rules are approved. Existing historical trip records are not rewritten.
- The rider journey distinguishes finding a driver, a confirmed pickup, arrival, an unfulfilled request, cancellation, and completion. See `artifacts/wayfare/components/RiderJourney.tsx`.
- Vhaya has a trip PIN and shows assigned-driver location to the rider during an active trip. Trusted contacts can open a user-confirmed share sheet containing a trip snapshot, but this is not live tracking. Emergency calling opens the phone app for 112; staffed support and emergency dispatch are not connected. See `replit.md`.

## Evidence and product implications

### Price confidence and cancellation fairness

Uber's Polokwane page says riders can see the total price before confirming and pay that amount at the end. Vhaya's current route-kilometre estimate is more transparent than an undisclosed price, but a single rate without base, time, or minimum charges is still a demo rule rather than a market-calibrated quote. Cancellation fees remain off until a local policy is approved.

**Improve:** Agree the fare formula and cancellation policy first. Show whether an amount is an estimate or guaranteed price, the inputs used, and any cancellation amount before the final confirmation. Do not collect a fee that the product cannot explain or reliably settle.

### Availability before feature breadth

Uber and Bolt both advertise service in Polokwane. Their public pages emphasize convenient request availability and pickup reliability. For a new marketplace, the rider's first question is whether a verified driver will accept in the requested area and time, not whether the app has intercity or delivery options.

**Improve:** Pilot a defined coverage area and operating window. Track request-to-first-offer time, matched-request rate, driver cancellations, pickup-pin corrections, and unfulfilled requests. Show realistic availability and avoid promising an ETA until it is measured.

### Safety and post-ride support

Bolt's South Africa rider-safety page lists live trip sharing, emergency assistance, trusted contacts, pickup codes, and 24/7 support; it also warns that features vary by country. Uber's South Africa page asks riders to compare the driver photo, vehicle, and registration with the app. These are useful trust benchmarks, not commitments Vhaya should copy without operational backing.

**Improve:** Add a live trip link that notifies a trusted contact, provide a support path that reaches a staffed queue, and define who receives an emergency escalation. The current share sheet sends only a one-time snapshot; keep that limitation and the 112 dial action explicit until those services exist.

### Payment choice

A February 2025 Business Day report described inDrive's “Light Cashless” bank-transfer option in South Africa. It reported that cash can be cheaper for riders and gives drivers immediate access to earnings, while also noting safety risks. This is evidence that payment trade-offs exist locally, not proof that Vhaya should add cash or transfers immediately.

**Improve:** Ask local riders how they want to pay and what they expect after a failed, cancelled, or disputed trip. Add a method only after its receipts, refunds, fraud handling, and driver settlement path are defined.

## Suggested validation with riders

Run short, moderated sessions with 6–8 people who request rides in the intended Polokwane coverage area. Ask them to complete a request using a precise pickup pin, compare the estimate with their expectations, explain the cancellation terms in their own words, find the trip-sharing/help controls, and describe their preferred payment method. Include people who do not rely on a bank card and people who often use landmarks rather than street addresses. Treat this as directional feedback, not a statistically representative survey.

## Sources

- [Uber — Polokwane rides](https://www.uber.com/za/en/r/cities/taxi/polokwane-lp-za) — local booking and upfront-price claims.
- [Bolt — rider safety in South Africa](https://bolt.eu/en-za/rides/safety) — safety and support feature claims, with a market-availability caveat.
- [Uber — rider safety in South Africa](https://www.uber.com/za/en/ride/safety/) — checking the displayed driver and vehicle details.
- [Bolt — Polokwane](https://bolt.eu/en/cities/polokwane/) — local service page.
- [Business Day — inDrive introduces bank transfers in South Africa](https://www.businessday.co.za/bd/companies/telecoms-and-technology/2025-02-17-indrive-introduces-bank-transfers-for-payment-in-sa) — local payment trade-offs and the reported 2025 bank-transfer feature.

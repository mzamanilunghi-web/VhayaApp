import assert from "node:assert/strict";
import test from "node:test";
import {
  addBillingInterval,
  calculatePromotionDiscount,
  effectiveSubscriptionStatus,
  evaluatePromotionEligibility,
  providerCancellationRequired,
} from "../src/lib/driverSubscriptionRules.ts";

const now = new Date("2026-11-18T10:00:00.000Z");

function promotion(overrides = {}) {
  return {
    startsAt: new Date("2026-11-01T00:00:00.000Z"),
    endsAt: new Date("2026-12-01T00:00:00.000Z"),
    serviceArea: null,
    maxRedemptions: null,
    newDriverOnly: false,
    minimumCompletedRides: null,
    minimumRating: null,
    planId: null,
    isActive: true,
    ...overrides,
  };
}

function driver(overrides = {}) {
  return {
    completedRideCount: 4,
    city: "Cape Town",
    averageRating: 4.8,
    ratingCount: 12,
    ...overrides,
  };
}

test("manual subscription expiry advances by the selected renewal interval", () => {
  assert.equal(
    addBillingInterval(now, "weekly").toISOString(),
    "2026-11-25T10:00:00.000Z",
  );
  assert.equal(
    addBillingInterval(new Date("2026-01-31T10:00:00.000Z"), "monthly").toISOString(),
    "2026-02-28T10:00:00.000Z",
  );
  assert.equal(
    addBillingInterval(new Date("2026-11-30T10:00:00.000Z"), "quarterly").toISOString(),
    "2027-02-28T10:00:00.000Z",
  );
  assert.equal(
    addBillingInterval(new Date("2024-02-29T10:00:00.000Z"), "annually").toISOString(),
    "2025-02-28T10:00:00.000Z",
  );
});

test("promo qualification checks date, plan, usage, new-driver, rides, area, and rating", () => {
  const rules = promotion({
    newDriverOnly: true,
    minimumCompletedRides: 0,
    minimumRating: 4.5,
    serviceArea: "cape town",
    planId: "plan-1",
  });
  const facts = driver({ completedRideCount: 0 });
  assert.deepEqual(
    evaluatePromotionEligibility(rules, facts, {
      planId: "plan-1",
      activeRedemptions: 0,
      now,
    }),
    { eligible: true, reasons: [] },
  );

  const failed = evaluatePromotionEligibility(
    promotion({
      newDriverOnly: true,
      minimumCompletedRides: 7,
      minimumRating: 4.8,
      serviceArea: "Durban",
      maxRedemptions: 2,
      planId: "plan-2",
      alreadyRedeemed: true,
    }),
    driver({ averageRating: null, ratingCount: 0 }),
    { planId: "plan-1", activeRedemptions: 2, now },
  );
  assert.equal(failed.eligible, false);
  assert.equal(failed.reasons.length, 7);
  assert.ok(failed.reasons.some((reason) => reason.includes("verified driver rating")));
});

test("promotions fail closed when trusted rating data is missing", () => {
  const result = evaluatePromotionEligibility(
    promotion({ minimumRating: 4 }),
    driver({ averageRating: null, ratingCount: 0 }),
    { planId: "plan-1", activeRedemptions: 0, now },
  );
  assert.equal(result.eligible, false);
  assert.deepEqual(result.reasons, [
    "A verified driver rating is required for this promotion.",
  ]);
});

test("fixed and percentage discounts are capped and never exceed the plan price", () => {
  assert.equal(
    calculatePromotionDiscount(10_000, {
      discountType: "fixed",
      discountValue: 2_500,
      maxDiscountMinor: null,
    }),
    2_500,
  );
  assert.equal(
    calculatePromotionDiscount(10_000, {
      discountType: "percentage",
      discountValue: 25,
      maxDiscountMinor: 1_500,
    }),
    1_500,
  );
  assert.equal(
    calculatePromotionDiscount(10_000, {
      discountType: "fixed",
      discountValue: 12_000,
      maxDiscountMinor: null,
    }),
    10_000,
  );
});

test("expired active subscriptions become expired at the period end", () => {
  assert.equal(
    effectiveSubscriptionStatus("active", new Date("2026-11-18T09:59:59.000Z"), now),
    "expired",
  );
  assert.equal(
    effectiveSubscriptionStatus("active", new Date("2026-11-18T10:00:01.000Z"), now),
    "active",
  );
});

test("changing an automatic subscription requires stopping provider renewals", () => {
  const current = {
    autoRenewActive: true,
    planId: "plan-1",
    status: "active",
    expiresAt: new Date("2026-12-18T10:00:00.000Z"),
  };
  assert.equal(
    providerCancellationRequired(current, { planId: "plan-2" }),
    true,
  );
  assert.equal(
    providerCancellationRequired(current, { status: "cancelled" }),
    true,
  );
  assert.equal(
    providerCancellationRequired(current, {
      expiresAt: new Date("2026-12-25T10:00:00.000Z"),
    }),
    true,
  );
  assert.equal(
    providerCancellationRequired({ ...current, autoRenewActive: false }, { status: "cancelled" }),
    false,
  );
});

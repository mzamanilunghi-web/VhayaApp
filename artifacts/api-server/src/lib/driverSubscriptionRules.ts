export type BillingInterval =
  | "daily"
  | "weekly"
  | "monthly"
  | "quarterly"
  | "biannually"
  | "annually";

export type PromotionRules = {
  startsAt: Date;
  endsAt: Date;
  serviceArea: string | null;
  maxRedemptions: number | null;
  newDriverOnly: boolean;
  minimumCompletedRides: number | null;
  minimumRating: number | null;
  planId: string | null;
  isActive: boolean;
  alreadyRedeemed?: boolean;
};

export type DriverPromotionFacts = {
  completedRideCount: number;
  city: string | null;
  averageRating: number | null;
  ratingCount: number;
};

export type PromotionEligibility = {
  eligible: boolean;
  reasons: string[];
};

function daysInUtcMonth(year: number, monthIndex: number): number {
  return new Date(Date.UTC(year, monthIndex + 1, 0)).getUTCDate();
}

export function addBillingInterval(
  from: Date,
  interval: BillingInterval,
): Date {
  const result = new Date(from.getTime());
  switch (interval) {
    case "daily":
      result.setUTCDate(result.getUTCDate() + 1);
      return result;
    case "weekly":
      result.setUTCDate(result.getUTCDate() + 7);
      return result;
    case "monthly":
      return addUtcMonthsClamped(result, 1);
    case "quarterly":
      return addUtcMonthsClamped(result, 3);
    case "biannually":
      return addUtcMonthsClamped(result, 6);
    case "annually":
      return addUtcMonthsClamped(result, 12);
  }
}

function addUtcMonthsClamped(from: Date, months: number): Date {
  const day = from.getUTCDate();
  const targetMonth = from.getUTCMonth() + months;
  const firstOfTargetMonth = new Date(
    Date.UTC(
      from.getUTCFullYear(),
      targetMonth,
      1,
      from.getUTCHours(),
      from.getUTCMinutes(),
      from.getUTCSeconds(),
      from.getUTCMilliseconds(),
    ),
  );
  firstOfTargetMonth.setUTCDate(
    Math.min(day, daysInUtcMonth(firstOfTargetMonth.getUTCFullYear(), firstOfTargetMonth.getUTCMonth())),
  );
  return firstOfTargetMonth;
}

export function evaluatePromotionEligibility(
  promotion: PromotionRules,
  driver: DriverPromotionFacts,
  options: {
    planId: string;
    activeRedemptions: number;
    now?: Date;
  },
): PromotionEligibility {
  const reasons: string[] = [];
  const now = options.now ?? new Date();
  if (!promotion.isActive) reasons.push("This promotion is paused.");
  if (now < promotion.startsAt) reasons.push("This promotion has not started yet.");
  if (now >= promotion.endsAt) reasons.push("This promotion has expired.");
  if (promotion.planId && promotion.planId !== options.planId) {
    reasons.push("This promotion does not apply to the selected plan.");
  }
  if (
    promotion.maxRedemptions !== null &&
    options.activeRedemptions >= promotion.maxRedemptions
  ) {
    reasons.push("This promotion has reached its usage limit.");
  }
  if (promotion.alreadyRedeemed) {
    reasons.push("You have already used this promotion.");
  }
  if (promotion.newDriverOnly && driver.completedRideCount > 0) {
    reasons.push("This promotion is only for drivers with no completed rides.");
  }
  if (
    promotion.minimumCompletedRides !== null &&
    driver.completedRideCount < promotion.minimumCompletedRides
  ) {
    reasons.push("You do not meet the completed-ride requirement.");
  }
  if (promotion.serviceArea) {
    const expected = promotion.serviceArea.trim().toLocaleLowerCase();
    const actual = driver.city?.trim().toLocaleLowerCase();
    if (!actual || actual !== expected) {
      reasons.push("This promotion is not available in your service area.");
    }
  }
  if (promotion.minimumRating !== null) {
    if (
      driver.averageRating === null ||
      driver.ratingCount <= 0 ||
      !Number.isFinite(driver.averageRating)
    ) {
      reasons.push("A verified driver rating is required for this promotion.");
    } else if (driver.averageRating < promotion.minimumRating) {
      reasons.push("Your verified rating is below this promotion's requirement.");
    }
  }
  return { eligible: reasons.length === 0, reasons };
}

export function calculatePromotionDiscount(
  amountMinor: number,
  promotion: {
    discountType: "fixed" | "percentage";
    discountValue: number;
    maxDiscountMinor: number | null;
  },
): number {
  if (!Number.isSafeInteger(amountMinor) || amountMinor < 0) {
    throw new Error("Subscription price must be a non-negative integer.");
  }
  if (!Number.isSafeInteger(promotion.discountValue) || promotion.discountValue <= 0) {
    throw new Error("Promotion value must be a positive integer.");
  }
  if (promotion.discountType === "fixed") {
    return Math.min(amountMinor, promotion.discountValue);
  }
  if (promotion.discountValue > 100) {
    throw new Error("Percentage promotions cannot exceed 100%.");
  }
  let discount = Math.floor((amountMinor * promotion.discountValue) / 100);
  if (promotion.maxDiscountMinor !== null) {
    discount = Math.min(discount, promotion.maxDiscountMinor);
  }
  return Math.min(amountMinor, discount);
}

export function effectiveSubscriptionStatus(
  status: "pending" | "active" | "expired" | "cancelled",
  expiresAt: Date | null,
  now = new Date(),
): "pending" | "active" | "expired" | "cancelled" {
  if (status === "active" && expiresAt && expiresAt.getTime() <= now.getTime()) {
    return "expired";
  }
  return status;
}

export function providerCancellationRequired(
  current: {
    autoRenewActive: boolean;
    planId: string | null;
    status: "pending" | "active" | "expired" | "cancelled";
    expiresAt: Date | null;
  },
  update: {
    planId?: string | null;
    status?: "pending" | "active" | "expired" | "cancelled";
    expiresAt?: Date | null;
  },
): boolean {
  if (!current.autoRenewActive) return false;
  if (update.planId !== undefined && update.planId !== current.planId) return true;
  if (
    update.status !== undefined &&
    update.status !== "active"
  ) {
    return true;
  }
  if (
    update.expiresAt !== undefined &&
    update.expiresAt?.getTime() !== current.expiresAt?.getTime()
  ) {
    return true;
  }
  return false;
}

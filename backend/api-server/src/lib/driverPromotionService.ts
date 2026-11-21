import { and, eq, sql } from "drizzle-orm";
import { db } from "@workspace/db";
import {
  driverPromotionRedemptions,
  driverPromotions,
  driverProfiles,
  driverRatingRecords,
  rideRequests,
} from "@workspace/db/schema";
import {
  calculatePromotionDiscount,
  evaluatePromotionEligibility,
} from "./driverSubscriptionRules";

export async function loadDriverPromotionFacts(driverId: string) {
  const [[rideCount], [profile], [rating]] = await Promise.all([
    db
      .select({ count: sql<number>`count(*)::int` })
      .from(rideRequests)
      .where(and(eq(rideRequests.driverId, driverId), eq(rideRequests.status, "completed"))),
    db
      .select({ city: driverProfiles.city })
      .from(driverProfiles)
      .where(eq(driverProfiles.userId, driverId))
      .limit(1),
    db
      .select()
      .from(driverRatingRecords)
      .where(eq(driverRatingRecords.driverId, driverId))
      .limit(1),
  ]);
  return {
    completedRideCount: rideCount?.count ?? 0,
    city: profile?.city ?? null,
    averageRating: rating?.averageRating ?? null,
    ratingCount: rating?.ratingCount ?? 0,
  };
}

export async function findEligibleDriverPromotion(input: {
  driverId: string;
  planId: string;
  code: string;
  amountMinor: number;
  facts?: Awaited<ReturnType<typeof loadDriverPromotionFacts>>;
  now?: Date;
}) {
  const normalizedCode = input.code.trim().toUpperCase();
  const [promotion] = await db
    .select()
    .from(driverPromotions)
    .where(eq(driverPromotions.code, normalizedCode))
    .limit(1);
  if (!promotion) {
    throw Object.assign(new Error("This promotion code was not found."), {
      status: 404,
    });
  }
  const now = input.now ?? new Date();
  if (!promotion.isActive) {
    throw Object.assign(new Error("This promotion is paused."), { status: 409 });
  }
  const [redemptionCount, [existingRedemption]] = await Promise.all([
    db
      .select({ count: sql<number>`count(*)::int` })
      .from(driverPromotionRedemptions)
      .where(
        and(
          eq(driverPromotionRedemptions.promotionId, promotion.id),
          sql`${driverPromotionRedemptions.status} in ('pending', 'redeemed')`,
        ),
      ),
    db
      .select()
      .from(driverPromotionRedemptions)
      .where(
        and(
          eq(driverPromotionRedemptions.promotionId, promotion.id),
          eq(driverPromotionRedemptions.driverId, input.driverId),
          sql`${driverPromotionRedemptions.status} in ('pending', 'redeemed')`,
        ),
      )
      .limit(1),
  ]);
  const facts = input.facts ?? (await loadDriverPromotionFacts(input.driverId));
  const eligibility = evaluatePromotionEligibility(
    {
      ...promotion,
      alreadyRedeemed: Boolean(existingRedemption),
    },
    facts,
    {
      planId: input.planId,
      activeRedemptions: redemptionCount[0]?.count ?? 0,
      now,
    },
  );
  if (!eligibility.eligible) {
    throw Object.assign(
      new Error(`This promotion cannot be used: ${eligibility.reasons.join(" ")}`),
      { status: 409 },
    );
  }
  const discountMinor = calculatePromotionDiscount(input.amountMinor, {
    discountType: promotion.discountType,
    discountValue: promotion.discountValue,
    maxDiscountMinor: promotion.maxDiscountMinor,
  });
  return { promotion, discountMinor };
}

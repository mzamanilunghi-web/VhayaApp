import { eq } from "drizzle-orm";
import { db } from "@workspace/db";
import { wayfareUsers } from "@workspace/db/schema";
import { isWayfareRole, type WayfareRole } from "../middlewares/wayfareRoles";
import { getFirebaseWayfareProfile } from "./firebaseAdmin";

export async function syncWayfareUser(userId: string) {
  const firebaseProfile = await getFirebaseWayfareProfile(userId);
  if (firebaseProfile) {
    return {
      userId: firebaseProfile.uid,
      role: firebaseProfile.role,
      displayName: firebaseProfile.displayName,
      email: firebaseProfile.email,
    };
  }

  const rows = await db
    .select()
    .from(wayfareUsers)
    .where(eq(wayfareUsers.id, userId))
    .limit(1);
  const user = rows[0];
  if (!user) throw new Error("Vhaya account was not found.");

  return {
    userId: user.id,
    role: isWayfareRole(user.role) ? user.role : ("rider" satisfies WayfareRole),
    displayName: user.displayName,
    email: user.email,
  };
}

export function toMillis(value: Date | null | undefined): number | null {
  return value ? value.getTime() : null;
}

export function toDate(value: unknown): Date | null {
  if (typeof value !== "number" || !Number.isFinite(value)) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

export function recordBody(value: unknown): Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {};
}

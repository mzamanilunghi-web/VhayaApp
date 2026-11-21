import { eq } from "drizzle-orm";
import { db } from "@workspace/db";
import { wayfareUsers } from "@workspace/db/schema";
import {
  getFirebaseWayfareProfile,
  setFirebaseWayfareRole,
  type WayfareFirebaseRole,
} from "../lib/firebaseAdmin";

export type WayfareRole = WayfareFirebaseRole;

export function isWayfareRole(value: unknown): value is WayfareRole {
  return value === "admin" || value === "driver" || value === "rider";
}

export async function getWayfareRole(userId: string): Promise<WayfareRole> {
  const firebaseProfile = await getFirebaseWayfareProfile(userId);
  if (firebaseProfile) return firebaseProfile.role;

  const rows = await db
    .select({ role: wayfareUsers.role })
    .from(wayfareUsers)
    .where(eq(wayfareUsers.id, userId))
    .limit(1);
  const role = rows[0]?.role;
  return isWayfareRole(role) ? role : "rider";
}

export function updateFirebaseRole(
  userId: string,
  role: WayfareRole,
): Promise<boolean> {
  return setFirebaseWayfareRole(userId, role);
}

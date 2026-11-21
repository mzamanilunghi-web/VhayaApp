import { createHash } from "node:crypto";

export function hashAccountSessionToken(sessionToken: string) {
  return createHash("sha256").update(sessionToken).digest("hex");
}

import { createHmac, timingSafeEqual } from "node:crypto";

export const ROUTE_FARE_RATE_PER_KM = 20;
const QUOTE_TTL_MS = 10 * 60 * 1000;

export interface RouteCoordinates {
  latitude: number;
  longitude: number;
}

export interface QuotedRoute {
  distanceMeters: number;
  durationSeconds: number;
  encodedPolyline: string;
  originCoordinates: RouteCoordinates;
  destinationCoordinates: RouteCoordinates;
  provider: "osrm";
  fareEstimate: number;
}

interface RouteQuoteClaims {
  userId: string;
  pickup: string;
  destination: string;
  expiresAt: number;
  route: QuotedRoute;
}

function isCoordinates(value: unknown): value is RouteCoordinates {
  if (value === null || typeof value !== "object") return false;
  const coordinates = value as Partial<RouteCoordinates>;
  return (
    typeof coordinates.latitude === "number" &&
    Number.isFinite(coordinates.latitude) &&
    Math.abs(coordinates.latitude) <= 90 &&
    typeof coordinates.longitude === "number" &&
    Number.isFinite(coordinates.longitude) &&
    Math.abs(coordinates.longitude) <= 180
  );
}

export function estimateRouteFare(distanceMeters: number): number {
  return Math.round(((distanceMeters / 1000) * ROUTE_FARE_RATE_PER_KM + Number.EPSILON) * 100) / 100;
}

export function signRouteQuote(
  claims: Omit<RouteQuoteClaims, "expiresAt">,
): string | null {
  const secret = process.env.SESSION_SECRET;
  if (!secret) return null;
  const payload = Buffer.from(
    JSON.stringify({ ...claims, expiresAt: Date.now() + QUOTE_TTL_MS }),
  ).toString("base64url");
  const signature = createHmac("sha256", secret).update(payload).digest("base64url");
  return `${payload}.${signature}`;
}

export function verifyRouteQuote(
  token: unknown,
  expected: { userId: string; pickup: string; destination: string },
): RouteQuoteClaims | null {
  const secret = process.env.SESSION_SECRET;
  if (!secret || typeof token !== "string" || token.length > 100_000) return null;
  const [payload, signature, extra] = token.split(".");
  if (!payload || !signature || extra !== undefined) return null;

  const expectedSignature = createHmac("sha256", secret).update(payload).digest();
  let suppliedSignature: Buffer;
  try {
    suppliedSignature = Buffer.from(signature, "base64url");
  } catch {
    return null;
  }
  if (
    suppliedSignature.length !== expectedSignature.length ||
    !timingSafeEqual(suppliedSignature, expectedSignature)
  ) {
    return null;
  }

  try {
    const claims = JSON.parse(Buffer.from(payload, "base64url").toString("utf8")) as RouteQuoteClaims;
    const route = claims.route;
    if (
      claims.userId !== expected.userId ||
      claims.pickup !== expected.pickup ||
      claims.destination !== expected.destination ||
      !Number.isFinite(claims.expiresAt) ||
      claims.expiresAt <= Date.now() ||
      !route ||
      typeof route.provider !== "string" ||
      route.provider.length === 0 ||
      !Number.isFinite(route.distanceMeters) ||
      route.distanceMeters <= 0 ||
      route.distanceMeters > 10_000_000 ||
      !Number.isFinite(route.durationSeconds) ||
      route.durationSeconds <= 0 ||
      route.durationSeconds > 86_400 ||
      typeof route.encodedPolyline !== "string" ||
      route.encodedPolyline.length > 50_000 ||
      !isCoordinates(route.originCoordinates) ||
      !isCoordinates(route.destinationCoordinates) ||
      !Number.isFinite(route.fareEstimate) ||
      Math.abs(route.fareEstimate - estimateRouteFare(route.distanceMeters)) > 0.001
    ) {
      return null;
    }
    return claims;
  } catch {
    return null;
  }
}

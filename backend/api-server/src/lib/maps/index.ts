// Vhaya maps provider abstraction.
//
// Workstream 1 (Maps and location), Phase 1 (platform foundations):
//   "Create one provider abstraction for places, geocoding, routing, tiles,
//    ETA, and navigation so vendors can be replaced independently."
//
// This module defines a single MapsProvider interface. The default
// OpenStreetMap implementation wraps the existing Nominatim (places /
// geocoding / reverse) and Project OSRM (routing) calls. A commercial
// provider can be swapped in by setting MAPS_PROVIDER and adding an
// implementation behind the same interface. All provider credentials are
// read server-side from environment variables and never bundled into the
// native app or Expo config.

import { type RouteCoordinates, type QuotedRoute } from "../routeQuote";
import { createOpenStreetMapProvider } from "./openstreetmap";

// ========================================================================
// Types
// ========================================================================

export interface PlaceSuggestion {
  id: string;
  label: string;
  latitude: number;
  longitude: number;
  distanceMeters?: number;
  provider: string;
}

export interface GeocodeResult {
  latitude: number;
  longitude: number;
  label?: string;
  provider: string;
}

export interface RoutePreview {
  distanceMeters: number;
  durationSeconds: number;
  encodedPolyline: string;
  originCoordinates: RouteCoordinates;
  destinationCoordinates: RouteCoordinates;
  provider: string;
  fareEstimate: number;
}

export interface SignedRouteQuote {
  route: RoutePreview;
  quoteToken: string;
  currency: string;
  ratePerKm: number;
}

export interface MapsProviderHealth {
  provider: string;
  ok: boolean;
  latencyMs?: number;
  error?: string;
  checkedAt: number;
}

export type MapsProviderName = "openstreetmap";

export interface MapsProviderConfig {
  name: MapsProviderName;
  apiKey?: string;
  timeoutMs: number;
  maxRetries: number;
  baseRetryDelayMs: number;
}

export interface MapsProvider {
  readonly name: MapsProviderName;
  readonly config: MapsProviderConfig;
  searchPlaces(
    query: string,
    center?: RouteCoordinates,
  ): Promise<PlaceSuggestion[]>;
  reverseGeocode(coordinates: RouteCoordinates): Promise<string | undefined>;
  geocodeAddress(
    address: string,
    near?: RouteCoordinates,
  ): Promise<GeocodeResult | undefined>;
  previewRoute(
    pickup: RouteCoordinates,
    destination: RouteCoordinates,
    pickupText?: string,
    destinationText?: string,
  ): Promise<RoutePreview>;
  signQuote(
    userId: string,
    pickupText: string,
    destinationText: string,
    route: RoutePreview,
  ): Promise<string | null>;
  verifyQuote(
    token: unknown,
    expected: { userId: string; pickup: string; destination: string },
  ): Promise<{
    userId: string;
    pickup: string;
    destination: string;
    route: RoutePreview;
    expiresAt: number;
  } | null>;
  healthCheck(): Promise<MapsProviderHealth>;
}

// ========================================================================
// Errors
// ========================================================================

export class MapsProviderError extends Error {
  readonly provider: string;
  readonly code: string;
  readonly retryable: boolean;

  constructor(
    message: string,
    opts: {
      provider: string;
      code?: string;
      retryable?: boolean;
    },
  ) {
    super(message);
    this.name = "MapsProviderError";
    this.provider = opts.provider;
    this.code = opts.code ?? "MAPS_PROVIDER_ERROR";
    this.retryable = opts.retryable ?? false;
  }
}

export const MapsErrorCode = {
  PROVIDER_UNAVAILABLE: "MAPS_PROVIDER_UNAVAILABLE",
  INVALID_REQUEST: "MAPS_INVALID_REQUEST",
  NOT_FOUND: "MAPS_NOT_FOUND",
  QUOTA_EXCEEDED: "MAPS_QUOTA_EXCEEDED",
  TIMEOUT: "MAPS_TIMEOUT",
  QUOTE_SIGNING_NOT_CONFIGURED: "MAPS_QUOTE_SIGNING_NOT_CONFIGURED",
} as const;


// ========================================================================
// Circuit breaker
//
// Trips after `failureThreshold` consecutive failures and stays open for
// `resetTimeoutMs`, after which a single trial request is allowed. A
// successful trial closes the breaker.
// ========================================================================

export class CircuitBreaker {
  private failures = 0;
  private trippedAt: number | null = null;
  private halfOpen = false;

  constructor(
    private readonly name: string,
    private readonly failureThreshold: number,
    private readonly resetTimeoutMs: number,
  ) {}

  allow(): boolean {
    const now = Date.now();
    if (this.trippedAt !== null && now - this.trippedAt >= this.resetTimeoutMs) {
      this.halfOpen = true;
      this.trippedAt = null;
      return true;
    }
    if (this.trippedAt !== null) return false;
    return true;
  }

  recordSuccess(): void {
    this.failures = 0;
    this.trippedAt = null;
    this.halfOpen = false;
  }

  recordFailure(): void {
    this.failures += 1;
    this.halfOpen = false;
    if (this.failures >= this.failureThreshold) {
      this.trippedAt = Date.now();
    }
  }

  state(): "closed" | "open" | "half-open" {
    if (this.trippedAt !== null && !this.halfOpen) return "open";
    if (this.halfOpen) return "half-open";
    return "closed";
  }
}

// ========================================================================
// Retry helper
//
// Bounded retries with jittered exponential backoff for retryable errors.
// ========================================================================

async function withRetry<T>(
  providerName: string,
  maxRetries: number,
  baseRetryDelayMs: number,
  isRetryable: (error: unknown) => boolean,
  fn: () => Promise<T>,
): Promise<T> {
  let lastError: unknown = null;
  let attempts = 0;
  const maxAttempts = Math.max(1, maxRetries + 1);

  while (attempts < maxAttempts) {
    attempts += 1;
    try {
      return await fn();
    } catch (error) {
      lastError = error;
      const retryable =
        error instanceof MapsProviderError ? error.retryable : isRetryable(error);
      if (!retryable || attempts >= maxAttempts) break;
      const delayMs =
        baseRetryDelayMs * Math.pow(2, attempts - 1) +
        Math.floor(Math.random() * baseRetryDelayMs);
      await new Promise((resolve) => setTimeout(resolve, delayMs));
    }
  }

  throw lastError;
}

// ========================================================================
// Provider factory
//
// Selects the maps provider from MAPS_PROVIDER. Defaults to "openstreetmap".
// All credentials are read from server-side environment variables.
// ========================================================================

function readBoolean(value: unknown, fallback: boolean): boolean {
  if (value === undefined || value === null) return fallback;
  if (typeof value === "boolean") return value;
  const s = String(value).trim().toLowerCase();
  if (s === "true" || s === "1" || s === "yes" || s === "on") return true;
  if (s === "false" || s === "0" || s === "no" || s === "off") return false;
  return fallback;
}

function readMapsProviderConfig(): InternalMapsProviderConfig {
  const name =
    (process.env.MAPS_PROVIDER as MapsProviderName) ?? "openstreetmap";
  return {
    name,
    apiKey: process.env.MAPS_API_KEY ?? undefined,
    timeoutMs: Number(process.env.MAPS_TIMEOUT_MS ?? 10_000) || 10_000,
    maxRetries: Number(process.env.MAPS_MAX_RETRIES ?? 2) || 2,
    baseRetryDelayMs: Number(process.env.MAPS_BASE_RETRY_DELAY_MS ?? 500) || 500,
    circuitBreakerFailures: Number(
      process.env.MAPS_CIRCUIT_BREAKER_FAILURES ?? 5,
    ) || 5,
    circuitBreakerResetMs: Number(
      process.env.MAPS_CIRCUIT_BREAKER_RESET_MS ?? 30_000,
    ) || 30_000,
  };
}

// Internal circuit-breaker config fields (kept out of the public config type
// so provider implementations do not depend on them).
interface InternalMapsProviderConfig extends MapsProviderConfig {
  circuitBreakerFailures: number;
  circuitBreakerResetMs: number;
}

let providerCache: MapsProvider | null = null;
let providerCacheName: string | null = null;

export function getMapsProvider(): MapsProvider {
  const config = readMapsProviderConfig();
  if (
    providerCache !== null &&
    providerCacheName === config.name &&
    providerCache.config.apiKey === config.apiKey
  ) {
    return providerCache;
  }

  let provider: MapsProvider;
  switch (config.name) {
    case "openstreetmap":
      provider = createOpenStreetMapProvider(config as InternalMapsProviderConfig);
      break;
    default:
      throw new MapsProviderError(
        `Unknown maps provider: ${config.name}`,
        {
          provider: String(config.name),
          code: MapsErrorCode.PROVIDER_UNAVAILABLE,
          retryable: false,
        },
      );
  }

  providerCache = provider;
  providerCacheName = config.name;
  return provider;
}

export function resetMapsProviderCache(): void {
  providerCache = null;
  providerCacheName = null;
}

// Re-export commonly used types and signing helpers from routeQuote for
// consumers that want to build/verify quotes directly.
export type { RouteCoordinates, QuotedRoute };
export {
  estimateRouteFare,
  signRouteQuote,
  verifyRouteQuote,
} from "../routeQuote";


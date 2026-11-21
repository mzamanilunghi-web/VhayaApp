// OpenStreetMap-based maps provider.
//
// Wraps the existing Nominatim (places / geocoding / reverse) and Project
// OSRM (routing) calls behind the MapsProvider interface. Kept server-side:
// all requests originate from the API server, no provider credentials are
// embedded in the native app, and the provider can be swapped for a
// commercial vendor by adding another MapsProvider implementation.

import {
  CircuitBreaker,
  MapsErrorCode,
  MapsProvider,
  MapsProviderError,
  type GeocodeResult,
  type MapsProviderConfig,
  type PlaceSuggestion,
  type RoutePreview,
  type RouteCoordinates,
} from "./index";
import {
  geocodeOpenStreetMapAddress,
  reverseGeocodeOpenStreetMapCoordinates,
  searchOpenStreetMapPlaces,
  type OpenStreetMapPlace,
} from "../openStreetMap";
import { estimateRouteFare, type QuotedRoute } from "../routeQuote";

const OSRM_ROUTE_URL = "https://router.project-osrm.org/route/v1/driving";
const DEFAULT_CENTER: RouteCoordinates = {
  latitude: -26.2041,
  longitude: 28.0473,
};

function isRetryableHttpError(error: unknown): boolean {
  if (error instanceof MapsProviderError) return error.retryable;
  if (error instanceof Error) {
    const message = error.message.toLowerCase();
    return (
      message.includes("temporarily unavailable") ||
      message.includes("timeout") ||
      message.includes("ECONNRESET") ||
      message.includes("ETIMEDOUT") ||
      message.includes("EAI_AGAIN")
    );
  }
  return false;
}

function readOsrmCoordinates(value: unknown): RouteCoordinates | undefined {
  if (!Array.isArray(value) || value.length < 2) return undefined;
  const [longitude, latitude] = value;
  if (
    typeof latitude === "number" &&
    Number.isFinite(latitude) &&
    Math.abs(latitude) <= 90 &&
    typeof longitude === "number" &&
    Number.isFinite(longitude) &&
    Math.abs(longitude) <= 180
  ) {
    return { latitude, longitude };
  }
  return undefined;
}

export function createOpenStreetMapProvider(
  config: MapsProviderConfig & {
    circuitBreakerFailures: number;
    circuitBreakerResetMs: number;
  },
): MapsProvider {
  const breaker = new CircuitBreaker(
    config.name,
    config.circuitBreakerFailures,
    config.circuitBreakerResetMs,
  );
  async function fetchOsrmRoute(
    pickup: RouteCoordinates,
    destination: RouteCoordinates,
  ): Promise<RoutePreview> {
    const coordinates = [
      `${pickup.longitude},${pickup.latitude}`,
      `${destination.longitude},${destination.latitude}`,
    ].join(";");
    const url = new URL(`${OSRM_ROUTE_URL}/${coordinates}`);
    url.searchParams.set("overview", "full");
    url.searchParams.set("geometries", "polyline");

    const response = await fetch(url, {
      signal: AbortSignal.timeout(config.timeoutMs),
    });
    const payload = (await response.json()) as {
      code?: string;
      message?: string;
      routes?: Array<{
        distance?: number;
        duration?: number;
        geometry?: string;
      }>;
      waypoints?: Array<{ location?: unknown }>;
    };

    const route = payload.routes?.[0];
    const distanceMeters = route?.distance;
    const durationSeconds = route?.duration;
    const encodedPolyline = route?.geometry;
    const originCoordinates =
      readOsrmCoordinates(payload.waypoints?.[0]?.location) ?? pickup;
    const destinationCoordinates =
      readOsrmCoordinates(payload.waypoints?.[1]?.location) ?? destination;

    if (
      !response.ok ||
      payload.code !== "Ok" ||
      !route ||
      typeof distanceMeters !== "number" ||
      !Number.isFinite(distanceMeters) ||
      distanceMeters <= 0 ||
      typeof durationSeconds !== "number" ||
      !Number.isFinite(durationSeconds) ||
      durationSeconds <= 0 ||
      typeof encodedPolyline !== "string" ||
      encodedPolyline.length === 0 ||
      encodedPolyline.length > 50_000
    ) {
      throw new MapsProviderError(
        payload.message ??
          "OpenStreetMap could not find a drivable route between those places.",
        {
          provider: config.name,
          code: response.ok ? MapsErrorCode.NOT_FOUND : MapsErrorCode.PROVIDER_UNAVAILABLE,
          retryable: !response.ok,
        },
      );
    }

    return {
      distanceMeters,
      durationSeconds,
      encodedPolyline,
      originCoordinates,
      destinationCoordinates,
      provider: config.name,
      fareEstimate: estimateRouteFare(distanceMeters),
    };
  }

  return {
    name: config.name,
    config: {
      name: config.name,
      apiKey: config.apiKey,
      timeoutMs: config.timeoutMs,
      maxRetries: config.maxRetries,
      baseRetryDelayMs: config.baseRetryDelayMs,
    },
    async searchPlaces(query, center) {
      if (!breaker.allow()) {
        throw new MapsProviderError(
          "Maps provider circuit breaker is open.",
          {
            provider: config.name,
            code: MapsErrorCode.PROVIDER_UNAVAILABLE,
            retryable: true,
          },
        );
      }
      try {
        const places = await withRetry(
          config.name,
          config.maxRetries,
          config.baseRetryDelayMs,
          isRetryableHttpError,
          async () => searchOpenStreetMapPlaces(query, center),
        );
        breaker.recordSuccess();
        return places.map((place: OpenStreetMapPlace) => ({
          id: place.id,
          label: place.label,
          latitude: place.latitude,
          longitude: place.longitude,
          distanceMeters: place.distanceMeters,
          provider: config.name,
        }));
      } catch (error) {
        breaker.recordFailure();
        throw error;
      }
    },


    async reverseGeocode(coordinates) {
      if (!breaker.allow()) {
        throw new MapsProviderError(
          "Maps provider circuit breaker is open.",
          {
            provider: config.name,
            code: MapsErrorCode.PROVIDER_UNAVAILABLE,
            retryable: true,
          },
        );
      }
      try {
        const label = await withRetry(
          config.name,
          config.maxRetries,
          config.baseRetryDelayMs,
          isRetryableHttpError,
          async () => reverseGeocodeOpenStreetMapCoordinates(coordinates),
        );
        breaker.recordSuccess();
        return label;
      } catch (error) {
        breaker.recordFailure();
        throw error;
      }
    },
    async geocodeAddress(address, near) {
      if (!breaker.allow()) {
        throw new MapsProviderError(
          "Maps provider circuit breaker is open.",
          {
            provider: config.name,
            code: MapsErrorCode.PROVIDER_UNAVAILABLE,
            retryable: true,
          },
        );
      }
      try {
        const coordinates = await withRetry(
          config.name,
          config.maxRetries,
          config.baseRetryDelayMs,
          isRetryableHttpError,
          async () => geocodeOpenStreetMapAddress(address, near),
        );
        breaker.recordSuccess();
        if (!coordinates) return undefined;
        return {
          latitude: coordinates.latitude,
          longitude: coordinates.longitude,
          provider: config.name,
        };
      } catch (error) {
        breaker.recordFailure();
        throw error;
      }
    },
    async previewRoute(pickup, destination, pickupText, destinationText) {
      if (!breaker.allow()) {
        throw new MapsProviderError(
          "Maps provider circuit breaker is open.",
          {
            provider: config.name,
            code: MapsErrorCode.PROVIDER_UNAVAILABLE,
            retryable: true,
          },
        );
      }
      try {
        const route = await withRetry(
          config.name,
          config.maxRetries,
          config.baseRetryDelayMs,
          isRetryableHttpError,
          async () => fetchOsrmRoute(pickup, destination),
        );
        breaker.recordSuccess();
        return route;
      } catch (error) {
        breaker.recordFailure();
        throw error;
      }
    },
    async signQuote(userId, pickupText, destinationText, route) {
      const { signRouteQuote } = await import("../routeQuote");
      const token = signRouteQuote({
        userId,
        pickup: pickupText,
        destination: destinationText,
        route: route as QuotedRoute,
      });
      if (!token) {
        throw new MapsProviderError(
          "Secure route quotes are unavailable until the server session secret is configured.",
          {
            provider: config.name,
            code: MapsErrorCode.QUOTE_SIGNING_NOT_CONFIGURED,
            retryable: false,
          },
        );
      }
      return token;
    },
    async verifyQuote(token, expected) {
      const { verifyRouteQuote } = await import("../routeQuote");
      const claims = verifyRouteQuote(token, expected);
      if (!claims) return null;
      return {
        userId: claims.userId,
        pickup: claims.pickup,
        destination: claims.destination,
        route: claims.route,
        expiresAt: claims.expiresAt,
      };
    },
    async healthCheck() {
      const startedAt = Date.now();
      try {
        await this.searchPlaces("Pretoria", DEFAULT_CENTER);
        return {
          provider: config.name,
          ok: true,
          latencyMs: Date.now() - startedAt,
          checkedAt: Date.now(),
        };
      } catch (error) {
        return {
          provider: config.name,
          ok: false,
          error: error instanceof Error ? error.message : "Unknown provider error",
          latencyMs: Date.now() - startedAt,
          checkedAt: Date.now(),
        };
      }
    },
  };
}

// Imported lazily below to keep the module graph acyclic at load time.
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
      return fn();
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


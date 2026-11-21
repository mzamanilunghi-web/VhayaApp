import type { RouteCoordinates } from "./routeQuote";

const NOMINATIM_SEARCH_URL = "https://nominatim.openstreetmap.org/search";
const NOMINATIM_REVERSE_URL = "https://nominatim.openstreetmap.org/reverse";
const NOMINATIM_USER_AGENT = "VhayaRide/1.0 (address search)";
const CACHE_TTL_MS = 24 * 60 * 60 * 1000;

export const DEFAULT_OSM_SEARCH_CENTER: RouteCoordinates = {
  latitude: -26.2041,
  longitude: 28.0473,
};

export interface OpenStreetMapPlace {
  id: string;
  label: string;
  latitude: number;
  longitude: number;
  distanceMeters: number;
}

const searchCache = new Map<
  string,
  { expiresAt: number; places: OpenStreetMapPlace[] }
>();
const reverseCache = new Map<string, string>();
let requestQueue: Promise<void> = Promise.resolve();
let lastRequestAt = 0;

function distanceBetweenMeters(
  first: RouteCoordinates,
  second: RouteCoordinates,
): number {
  const radians = (degrees: number) => (degrees * Math.PI) / 180;
  const latitudeDelta = radians(second.latitude - first.latitude);
  const longitudeDelta = radians(second.longitude - first.longitude);
  const firstLatitude = radians(first.latitude);
  const secondLatitude = radians(second.latitude);
  const value =
    Math.sin(latitudeDelta / 2) ** 2 +
    Math.cos(firstLatitude) *
      Math.cos(secondLatitude) *
      Math.sin(longitudeDelta / 2) ** 2;
  return 6_371_000 * 2 * Math.atan2(Math.sqrt(value), Math.sqrt(1 - value));
}

async function fetchNominatim(url: string): Promise<Response> {
  const queued = requestQueue.then(async () => {
    const delayMs = Math.max(0, 1_000 - (Date.now() - lastRequestAt));
    if (delayMs) await new Promise((resolve) => setTimeout(resolve, delayMs));
    lastRequestAt = Date.now();
    return fetch(url, {
      headers: {
        "User-Agent": NOMINATIM_USER_AGENT,
        "Accept-Language": "en-ZA,en;q=0.9",
      },
      signal: AbortSignal.timeout(8_000),
    });
  });
  requestQueue = queued.then(
    () => undefined,
    () => undefined,
  );
  return queued;
}

function getSearchUrl(query: string, center?: RouteCoordinates): string {
  const params = new URLSearchParams({
    q: query,
    format: "jsonv2",
    addressdetails: "1",
    limit: "8",
  });
  if (center) {
    const latitudeDelta = 0.27;
    const longitudeDelta =
      latitudeDelta /
      Math.max(0.15, Math.cos((center.latitude * Math.PI) / 180));
    params.set(
      "viewbox",
      [
        center.longitude - longitudeDelta,
        center.latitude + latitudeDelta,
        center.longitude + longitudeDelta,
        center.latitude - latitudeDelta,
      ].join(","),
    );
    params.set("bounded", "0");
  }
  return `${NOMINATIM_SEARCH_URL}?${params.toString()}`;
}

export async function searchOpenStreetMapPlaces(
  query: string,
  center?: RouteCoordinates,
): Promise<OpenStreetMapPlace[]> {
  const normalizedQuery = query.trim();
  if (!normalizedQuery || normalizedQuery.length > 200) return [];

  const distanceCenter = center ?? DEFAULT_OSM_SEARCH_CENTER;
  const cacheKey = center
    ? `${normalizedQuery.toLocaleLowerCase("en-ZA")}:${center.latitude.toFixed(3)}:${center.longitude.toFixed(3)}`
    : `${normalizedQuery.toLocaleLowerCase("en-ZA")}:global`;
  const cached = searchCache.get(cacheKey);
  if (cached && cached.expiresAt > Date.now()) return cached.places;

  const response = await fetchNominatim(getSearchUrl(normalizedQuery, center));
  if (!response.ok) {
    throw new Error("OpenStreetMap place search is temporarily unavailable.");
  }
  const payload = (await response.json()) as Array<{
    place_id?: number | string;
    display_name?: string;
    lat?: string;
    lon?: string;
  }>;
  const places = payload
    .map((result): OpenStreetMapPlace | null => {
      const latitude = Number(result.lat);
      const longitude = Number(result.lon);
      if (
        typeof result.display_name !== "string" ||
        !Number.isFinite(latitude) ||
        !Number.isFinite(longitude)
      ) {
        return null;
      }
      return {
        id: `osm:${String(result.place_id ?? `${latitude},${longitude}`)}`,
        label: result.display_name,
        latitude,
        longitude,
        distanceMeters: Math.round(
          distanceBetweenMeters(distanceCenter, { latitude, longitude }),
        ),
      };
    })
    .filter((place): place is OpenStreetMapPlace => place !== null);
  if (center) {
    places.sort((first, second) => first.distanceMeters - second.distanceMeters);
  }

  searchCache.set(cacheKey, {
    expiresAt: Date.now() + CACHE_TTL_MS,
    places,
  });
  if (searchCache.size > 500) {
    const oldestKey = searchCache.keys().next().value;
    if (oldestKey) searchCache.delete(oldestKey);
  }
  return places;
}

export async function geocodeOpenStreetMapAddress(
  address: string,
  near?: RouteCoordinates,
): Promise<RouteCoordinates | undefined> {
  const place = (await searchOpenStreetMapPlaces(address, near))[0];
  if (!place) return undefined;
  return { latitude: place.latitude, longitude: place.longitude };
}

export async function reverseGeocodeOpenStreetMapCoordinates(
  coordinates: RouteCoordinates,
): Promise<string | undefined> {
  const cacheKey = `${coordinates.latitude.toFixed(5)}:${coordinates.longitude.toFixed(5)}`;
  const cached = reverseCache.get(cacheKey);
  if (cached) return cached;

  const params = new URLSearchParams({
    format: "jsonv2",
    lat: String(coordinates.latitude),
    lon: String(coordinates.longitude),
    zoom: "18",
    addressdetails: "1",
  });
  const response = await fetchNominatim(`${NOMINATIM_REVERSE_URL}?${params.toString()}`);
  if (!response.ok) {
    throw new Error("OpenStreetMap address lookup is temporarily unavailable.");
  }
  const payload = (await response.json()) as { display_name?: unknown };
  if (typeof payload.display_name !== "string" || !payload.display_name.trim()) {
    return undefined;
  }

  const label = payload.display_name.trim();
  reverseCache.set(cacheKey, label);
  if (reverseCache.size > 500) {
    const oldestKey = reverseCache.keys().next().value;
    if (oldestKey) reverseCache.delete(oldestKey);
  }
  return label;
}

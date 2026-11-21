import { Router, type IRouter } from "express";
import { getMapsProvider, type PlaceSuggestion } from "../lib/maps";
import { recordBody } from "../lib/wayfareData";
import { getWayfareUserId } from "../middlewares/firebaseIdentity";

const router: IRouter = Router();

function readCoordinates(value: unknown) {
  if (value === null || typeof value !== "object") return undefined;
  const coordinates = value as Record<string, unknown>;
  const latitude = coordinates.latitude;
  const longitude = coordinates.longitude;
  if (
    typeof latitude !== "number" ||
    !Number.isFinite(latitude) ||
    Math.abs(latitude) > 90 ||
    typeof longitude !== "number" ||
    !Number.isFinite(longitude) ||
    Math.abs(longitude) > 180
  ) {
    return undefined;
  }
  return { latitude, longitude };
}

router.post("/places/search", async (req, res) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });

  const body = recordBody(req.body);
  const query = typeof body.query === "string" ? body.query.trim() : "";
  if (query.length < 3 || query.length > 200) {
    return res.status(400).json({
      error: "Enter at least 3 characters to search nearby places.",
    });
  }
  const center = readCoordinates(body.proximity);

  try {
    const provider = getMapsProvider();
    const places = await provider.searchPlaces(query, center);
    return res.json({
      places: places.map((place: PlaceSuggestion) => ({
        ...place,
        provider: place.provider,
      })),
      provider: provider.name,
    });
  } catch (error) {
    return res.status(502).json({
      error:
        error instanceof Error
          ? error.message
          : "Place search is unavailable. Enter the address manually.",
    });
  }
});

router.post("/places/reverse", async (req, res) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });

  const coordinates = readCoordinates(recordBody(req.body).coordinates);
  if (!coordinates) {
    return res.status(400).json({ error: "Choose a valid point on the map." });
  }

  try {
    const provider = getMapsProvider();
    const label = await provider.reverseGeocode(coordinates);
    return res.json({
      place: label
        ? {
            label,
            latitude: coordinates.latitude,
            longitude: coordinates.longitude,
            provider: provider.name,
          }
        : null,
      provider: provider.name,
    });
  } catch (error) {
    return res.status(502).json({
      error:
        error instanceof Error
          ? error.message
          : "Address lookup is unavailable. The selected coordinates are still usable.",
    });
  }
});

export default router;

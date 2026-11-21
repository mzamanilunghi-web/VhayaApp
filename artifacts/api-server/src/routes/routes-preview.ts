import { Router, type IRouter } from "express";
import { getMapsProvider, type RouteCoordinates } from "../lib/maps";
import { estimateRouteFare } from "../lib/routeQuote";
import { recordBody } from "../lib/wayfareData";
import { getWayfareUserId } from "../middlewares/firebaseIdentity";

const router: IRouter = Router();

function readCoordinates(value: unknown): RouteCoordinates | undefined {
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

router.post("/routes/preview", async (req, res) => {
  const userId = getWayfareUserId(req);
  if (!userId) return res.status(401).json({ error: "Sign in to continue." });

  const body = recordBody(req.body);
  const pickup = typeof body.pickup === "string" ? body.pickup.trim() : "";
  const destination =
    typeof body.destination === "string" ? body.destination.trim() : "";
  if (!pickup || !destination || pickup.length > 300 || destination.length > 300) {
    return res.status(400).json({ error: "Enter a pickup and destination address." });
  }

  try {
    const provider = getMapsProvider();

    const pickupCoordinates =
      readCoordinates(body.pickupCoordinates) ??
      (await provider.geocodeAddress(pickup));
    if (!pickupCoordinates) {
      return res.status(422).json({
        error:
          "Could not locate the pickup. Choose a nearby suggestion or enter a more precise address.",
      });
    }

    const destinationCoordinates =
      readCoordinates(body.destinationCoordinates) ??
      (await provider.geocodeAddress(destination, pickupCoordinates));
    if (!destinationCoordinates) {
      return res.status(422).json({
        error:
          "Could not locate the destination. Choose a nearby suggestion or enter a more precise address.",
      });
    }

    const providerRoute = await provider.previewRoute(
      pickupCoordinates,
      destinationCoordinates,
      pickup,
      destination,
    );
    const distanceMeters = Math.max(1, Math.round(providerRoute.distanceMeters));
    const route = {
      ...providerRoute,
      distanceMeters,
      durationSeconds: Math.max(1, Math.round(providerRoute.durationSeconds)),
      fareEstimate: estimateRouteFare(distanceMeters),
    };
    const quoteToken = await provider.signQuote(
      userId,
      pickup,
      destination,
      route,
    );
    if (!quoteToken) {
      return res.status(503).json({
        code: "ROUTE_QUOTE_SIGNING_NOT_CONFIGURED",
        error:
          "Secure route estimates are unavailable until the server session secret is configured.",
      });
    }

    return res.json({
      route: { ...route, quoteToken },
      currency: "ZAR",
      ratePerKm: 20,
    });
  } catch (error) {
    return res.status(502).json({
      error:
        error instanceof Error
          ? error.message
          : "Routing is temporarily unavailable. Try again after confirming both addresses.",
    });
  }
});

export default router;

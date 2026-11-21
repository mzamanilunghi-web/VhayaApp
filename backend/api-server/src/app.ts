import express, { type ErrorRequestHandler, type Express, type RequestHandler } from "express";
import cors from "cors";
import pinoHttp from "pino-http";
import router from "./routes";
import healthRouter from "./routes/health";
import publicAccountDeletionRouter from "./routes/public-account-deletion";
import { logger } from "./lib/logger";
import { attachFirebaseIdentity } from "./middlewares/firebaseIdentity";

const app: Express = express();
app.set("etag", false);

// Security headers
app.use((_req, res, next) => {
  res.setHeader("X-Content-Type-Options", "nosniff");
  res.setHeader("X-Frame-Options", "DENY");
  res.setHeader("Referrer-Policy", "no-referrer");
  res.setHeader("Permissions-Policy", "geolocation=(), camera=(), microphone=()");
  if (process.env.NODE_ENV === "production") {
    res.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
  }
  next();
});

// CORS: restrict to configured origins in production; allow all in development.
const allowedOrigins = (process.env.CORS_ALLOWED_ORIGINS || "")
  .split(",")
  .map((s) => s.trim())
  .filter(Boolean);
const corsOptions: cors.CorsOptions = {
  origin:
    process.env.NODE_ENV === "production" && allowedOrigins.length
      ? allowedOrigins
      : true,
  credentials: true,
};
app.use(cors(corsOptions));

// Simple in-memory rate limiter for API routes.
const rateLimitWindowMs = Number(process.env.RATE_LIMIT_WINDOW_MS || 60_000);
const rateLimitMax = Number(process.env.RATE_LIMIT_MAX || 120);
const rateBuckets = new Map<string, { count: number; resetAt: number }>();
const rateLimiter: RequestHandler = (req, res, next) => {
  const key = req.ip || req.socket.remoteAddress || "unknown";
  const now = Date.now();
  const bucket = rateBuckets.get(key);
  if (!bucket || bucket.resetAt <= now) {
    rateBuckets.set(key, { count: 1, resetAt: now + rateLimitWindowMs });
    return next();
  }
  if (bucket.count >= rateLimitMax) {
    res.setHeader("Retry-After", Math.ceil((bucket.resetAt - now) / 1000).toString());
    return res.status(429).json({ error: "Too many requests. Please try again later." });
  }
  bucket.count += 1;
  return next();
};
app.use("/api", rateLimiter);

app.use(
  pinoHttp({
    logger,
    serializers: {
      req(req) {
        return {
          id: req.id,
          method: req.method,
          url: req.url?.split("?")[0],
        };
      },
      res(res) {
        return {
          statusCode: res.statusCode,
        };
      },
    },
  }),
);
app.use("/api", healthRouter);
app.use("/api", (_req, res, next) => {
  res.setHeader("Cache-Control", "private, no-store, no-cache, must-revalidate");
  res.setHeader("Pragma", "no-cache");
  next();
});
app.use(
  express.json({
    limit: "1mb",
    verify(req, _res, buffer) {
      (req as typeof req & { rawBody?: Buffer }).rawBody = Buffer.from(buffer);
    },
  }),
);
app.use(express.urlencoded({ extended: true, limit: "1mb" }));
app.use("/api", publicAccountDeletionRouter);
app.use("/api", attachFirebaseIdentity);
app.use("/api", router);
app.use("/api", (_req, res) => {
  res.status(404).json({ error: "API route not found." });
});

app.get("/", (_req, res) => {
  res.json({ service: "vhaya-api", status: "ok" });
});

const jsonErrorHandler: ErrorRequestHandler = (error, req, res, next) => {
  if (res.headersSent) return next(error);

  if (
    error &&
    typeof error === "object" &&
    "status" in error &&
    Number((error as { status: unknown }).status) === 413
  ) {
    return res.status(413).json({
      error: "Uploaded files must be 8 MB or smaller.",
    });
  }

  logger.error(
    {
      errorType: error instanceof Error ? error.name : "UnknownError",
      errorMessage: error instanceof Error ? error.message.slice(0, 200) : undefined,
      path: req.path,
      errorCode:
        error && typeof error === "object" && "code" in error &&
        /^[A-Za-z0-9/_-]{1,64}$/.test(String(error.code))
          ? String(error.code)
          : undefined,
    },
    "Unhandled API request error",
  );
  return res.status(500).json({ error: "The API request could not be completed." });
};

app.use(jsonErrorHandler);

export default app;

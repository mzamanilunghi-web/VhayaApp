import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { once } from "node:events";
import express from "express";
import pg from "pg";

const { Pool } = pg;

const testDatabaseUrl = process.env.WAYFARE_RIDES_TEST_DATABASE_URL;
if (!testDatabaseUrl) {
  throw new Error(
    "Set WAYFARE_RIDES_TEST_DATABASE_URL to a development or dedicated test PostgreSQL database.",
  );
}

const schemaName = `wayfare_rides_test_${process.pid}_${randomUUID().replaceAll("-", "")}`;
if (!/^wayfare_rides_test_[a-z0-9_]+$/.test(schemaName)) {
  throw new Error("Could not create a safe temporary test schema name.");
}

const adminPool = new Pool({ connectionString: testDatabaseUrl, max: 1 });
const originalDatabaseUrl = process.env.DATABASE_URL;
const originalSessionSecret = process.env.SESSION_SECRET;
let testPool;
let server;
let releasePairBarrier;
let barrierTimeout;
let schemaCreated = false;

async function createIsolatedSchema() {
  await adminPool.query(`CREATE SCHEMA "${schemaName}"`);
  schemaCreated = true;
  await adminPool.query(`
    CREATE TABLE "${schemaName}".wayfare_users (
      id text PRIMARY KEY,
      display_name text NOT NULL,
      email text,
      role text NOT NULL DEFAULT 'rider',
      dev_verified boolean NOT NULL DEFAULT false,
      updated_at timestamptz NOT NULL DEFAULT now()
    );
    CREATE TABLE "${schemaName}".ride_requests (
      id text PRIMARY KEY,
      rider_id text NOT NULL REFERENCES "${schemaName}".wayfare_users(id) ON DELETE RESTRICT,
      driver_id text REFERENCES "${schemaName}".wayfare_users(id) ON DELETE SET NULL,
      status text NOT NULL,
      pickup text NOT NULL,
      destination text NOT NULL,
      category text NOT NULL,
      currency text NOT NULL,
      fare double precision NOT NULL,
      pricing_mode text NOT NULL,
      pickup_at timestamptz,
      route jsonb,
      trip_data jsonb NOT NULL,
      created_at timestamptz NOT NULL DEFAULT now(),
      updated_at timestamptz NOT NULL DEFAULT now()
    );
    CREATE TABLE "${schemaName}".ride_events (
      id serial PRIMARY KEY,
      request_id text NOT NULL REFERENCES "${schemaName}".ride_requests(id) ON DELETE CASCADE,
      actor_id text NOT NULL REFERENCES "${schemaName}".wayfare_users(id) ON DELETE RESTRICT,
      action text NOT NULL,
      details jsonb NOT NULL DEFAULT '{}'::jsonb,
      created_at timestamptz NOT NULL DEFAULT now()
    );
  `);
}

function installConcurrentRequestBarrier(app) {
  let arrivals = 0;
  let waitingForPair = true;
  let release;
  const pairReady = new Promise((resolve) => {
    release = resolve;
  });
  releasePairBarrier = release;
  barrierTimeout = setTimeout(() => {
    waitingForPair = false;
    release();
  }, 5000);
  barrierTimeout.unref();

  app.use((req, _res, next) => {
    if (req.method !== "POST" || req.path !== "/rides" || !waitingForPair) {
      next();
      return;
    }
    arrivals += 1;
    if (arrivals === 2) {
      waitingForPair = false;
      clearTimeout(barrierTimeout);
      release();
    }
    void pairReady.then(() => next());
  });
}

async function run() {
  if (process.env.NODE_ENV === "production") {
    throw new Error("This integration test must not run in production.");
  }

  await createIsolatedSchema();

  const isolatedDatabaseUrl = new URL(testDatabaseUrl);
  isolatedDatabaseUrl.searchParams.set("options", `-c search_path=${schemaName}`);
  process.env.DATABASE_URL = isolatedDatabaseUrl.toString();
  process.env.SESSION_SECRET ||= "wayfare-rides-concurrency-test-only";

  const database = await import("@workspace/db");
  const { wayfareUsers } = await import("@workspace/db/schema");
  const [{ default: ridesRouter }, { signRouteQuote, estimateRouteFare }] = await Promise.all([
    import("../src/routes/rides.ts"),
    import("../src/lib/routeQuote.ts"),
  ]);
  testPool = database.pool;

  const riderId = `rides-test-${randomUUID()}`;
  const pickup = "Test pickup, Polokwane";
  const destination = "Test destination, Polokwane";
  const route = {
    distanceMeters: 5000,
    durationSeconds: 900,
    encodedPolyline: "",
    originCoordinates: { latitude: -23.9045, longitude: 29.4689 },
    destinationCoordinates: { latitude: -23.8962, longitude: 29.4486 },
    provider: "osrm",
    fareEstimate: estimateRouteFare(5000),
  };
  const quoteToken = signRouteQuote({ userId: riderId, pickup, destination, route });
  assert.ok(quoteToken, "the integration test must be able to sign a route quote");

  await database.db.insert(wayfareUsers).values({
    id: riderId,
    displayName: "Concurrency Test Rider",
    email: null,
    role: "rider",
  });

  const app = express();
  app.use(express.json());
  installConcurrentRequestBarrier(app);
  app.use((req, _res, next) => {
    req.wayfareUserId = riderId;
    next();
  });
  app.use(ridesRouter);

  server = app.listen(0, "127.0.0.1");
  await once(server, "listening");
  const address = server.address();
  assert.ok(address && typeof address === "object", "the test API must listen on a TCP port");
  const apiUrl = `http://127.0.0.1:${address.port}`;

  async function submitRide() {
    const response = await fetch(`${apiUrl}/rides`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        pickup,
        destination,
        category: "standard",
        pricingMode: "estimate",
        route: { quoteToken },
      }),
      signal: AbortSignal.timeout(15000),
    });
    return { status: response.status, body: await response.json() };
  }

  async function activeRideCount() {
    const result = await testPool.query(
      `SELECT count(*)::integer AS count
       FROM ride_requests
       WHERE rider_id = $1
         AND status NOT IN ('completed', 'cancelled', 'unfulfilled')`,
      [riderId],
    );
    return result.rows[0].count;
  }

  const [firstResult, secondResult] = await Promise.all([submitRide(), submitRide()]);
  const results = [firstResult, secondResult];
  const created = results.filter((result) => result.status === 201);
  const rejected = results.filter((result) => result.status === 409);

  assert.equal(created.length, 1, "exactly one concurrent request should create a ride");
  assert.equal(rejected.length, 1, "the competing request should receive HTTP 409");
  assert.match(
    rejected[0].body.error,
    /Finish or cancel your current ride request before requesting another ride/,
    "the conflict response should explain how to proceed",
  );
  assert.equal(await activeRideCount(), 1, "the database should contain only one active ride");

  const cancelledRideId = created[0].body.trip?.id;
  assert.equal(typeof cancelledRideId, "string", "the successful response should include the ride");
  await testPool.query("UPDATE ride_requests SET status = $1 WHERE id = $2", [
    "cancelled",
    cancelledRideId,
  ]);
  const afterCancel = await submitRide();
  assert.equal(afterCancel.status, 201, "a new ride should be allowed after cancellation");
  assert.equal(await activeRideCount(), 1, "the cancelled ride should not count as active");

  const completedRideId = afterCancel.body.trip?.id;
  assert.equal(typeof completedRideId, "string", "the post-cancellation response should include the ride");
  await testPool.query("UPDATE ride_requests SET status = $1 WHERE id = $2", [
    "completed",
    completedRideId,
  ]);
  const afterComplete = await submitRide();
  assert.equal(afterComplete.status, 201, "a new ride should be allowed after completion");
  assert.equal(await activeRideCount(), 1, "only the newest ride should remain active");

  console.log(
    "PASS: concurrent submissions create one active ride; the other receives a clear 409; new requests work after cancellation and completion.",
  );
}

try {
  await run();
} finally {
  releasePairBarrier?.();
  if (barrierTimeout) clearTimeout(barrierTimeout);
  if (server) {
    server.closeAllConnections?.();
    await new Promise((resolve) => server.close(() => resolve()));
  }
  if (testPool) await testPool.end();
  try {
    if (schemaCreated) {
      await adminPool.query(`DROP SCHEMA IF EXISTS "${schemaName}" CASCADE`);
    }
  } finally {
    await adminPool.end();
    if (originalDatabaseUrl === undefined) delete process.env.DATABASE_URL;
    else process.env.DATABASE_URL = originalDatabaseUrl;
    if (originalSessionSecret === undefined) delete process.env.SESSION_SECRET;
    else process.env.SESSION_SECRET = originalSessionSecret;
  }
}

import assert from "node:assert/strict";
import express from "express";
import pg from "pg";
import router from "../src/routes";

const { Pool } = pg;

const testDatabaseUrl = process.env.WAYFARE_TEST_DATABASE_URL;
if (!testDatabaseUrl) {
  console.log(
    "SKIP: Set WAYFARE_TEST_DATABASE_URL to run the operations integration tests.",
  );
  process.exit(0);
}

if (process.env.NODE_ENV === "production") {
  throw new Error("This integration test must not run in production.");
}

const schemaName = `wayfare_ops_test_${process.pid}`;
if (!/^wayfare_ops_test_[0-9]+$/.test(schemaName)) {
  throw new Error("Unsafe test schema name.");
}

const adminPool = new Pool({ connectionString: testDatabaseUrl, max: 1 });
let testPool;
let server;
let schemaCreated = false;

async function createIsolatedSchema() {
  await adminPool.query(`CREATE SCHEMA IF NOT EXISTS "${schemaName}"`);
  schemaCreated = true;
  await adminPool.query(`
    CREATE TABLE IF NOT EXISTS "${schemaName}".wayfare_users (
      id text PRIMARY KEY,
      display_name text NOT NULL,
      email text,
      role text NOT NULL DEFAULT 'rider',
      dev_verified boolean NOT NULL DEFAULT false,
      updated_at timestamptz NOT NULL DEFAULT now()
    );
    CREATE TABLE IF NOT EXISTS "${schemaName}".driver_profiles (
      user_id text PRIMARY KEY REFERENCES "${schemaName}".wayfare_users(id) ON DELETE CASCADE,
      application_status text NOT NULL DEFAULT 'draft',
      verification_status text NOT NULL DEFAULT 'unverified',
      vehicle text,
      vehicle_color text,
      registration text,
      licence_expiry text,
      operating_licence_reference text,
      is_online boolean NOT NULL DEFAULT false,
      staff_note text,
      submitted_at timestamptz,
      reviewed_at timestamptz,
      updated_at timestamptz NOT NULL DEFAULT now()
    );
    CREATE TABLE IF NOT EXISTS "${schemaName}".ride_requests (
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
      trip_data jsonb NOT NULL,
      created_at timestamptz NOT NULL DEFAULT now(),
      updated_at timestamptz NOT NULL DEFAULT now()
    );
    CREATE TABLE IF NOT EXISTS "${schemaName}".ride_payments (
      id text PRIMARY KEY,
      request_id text NOT NULL REFERENCES "${schemaName}".ride_requests(id) ON DELETE CASCADE,
      rider_id text NOT NULL REFERENCES "${schemaName}".wayfare_users(id) ON DELETE RESTRICT,
      provider text NOT NULL,
      reference text NOT NULL,
      amount_minor integer NOT NULL,
      currency text NOT NULL,
      status text NOT NULL DEFAULT 'pending',
      captured_amount_minor integer,
      paid_at timestamptz,
      created_at timestamptz NOT NULL DEFAULT now(),
      updated_at timestamptz NOT NULL DEFAULT now()
    );
    CREATE TABLE IF NOT EXISTS "${schemaName}".ride_events (
      id serial PRIMARY KEY,
      request_id text NOT NULL REFERENCES "${schemaName}".ride_requests(id) ON DELETE CASCADE,
      actor_id text NOT NULL REFERENCES "${schemaName}".wayfare_users(id) ON DELETE RESTRICT,
      action text NOT NULL,
      details jsonb NOT NULL DEFAULT '{}'::jsonb,
      created_at timestamptz NOT NULL DEFAULT now()
    );
    CREATE TABLE IF NOT EXISTS "${schemaName}".support_cases (
      id serial PRIMARY KEY,
      request_id text NOT NULL REFERENCES "${schemaName}".ride_requests(id) ON DELETE CASCADE,
      source_message_id text NOT NULL,
      reporter_user_id text NOT NULL REFERENCES "${schemaName}".wayfare_users(id) ON DELETE RESTRICT,
      category text NOT NULL DEFAULT 'other',
      priority text NOT NULL DEFAULT 'normal',
      status text NOT NULL DEFAULT 'open',
      assigned_to text REFERENCES "${schemaName}".wayfare_users(id) ON DELETE SET NULL,
      staff_note text,
      created_at timestamptz NOT NULL DEFAULT now(),
      updated_at timestamptz NOT NULL DEFAULT now()
    );
    CREATE TABLE IF NOT EXISTS "${schemaName}".driver_payout_records (
      id serial PRIMARY KEY,
      driver_id text NOT NULL REFERENCES "${schemaName}".wayfare_users(id) ON DELETE CASCADE,
      provider text NOT NULL,
      external_reference text NOT NULL,
      amount_minor integer NOT NULL,
      currency text NOT NULL,
      reconciliation_status text NOT NULL DEFAULT 'unreconciled',
      note text,
      recorded_at timestamptz NOT NULL DEFAULT now(),
      reconciled_at timestamptz
    );
  `);
}

function testAuthMiddleware(req, _res, next) {
  // Stand-in for attachFirebaseIdentity in tests: identifies the caller by header
  // and lets getWayfareRole resolve the role from the seeded database.
  const userId = req.headers["x-test-user-id"];
  if (typeof userId === "string" && userId) {
    (req).wayfareUserId = userId;
  }
  next();
}

function buildApp() {
  const app = express();
  app.use(express.json());
  app.use(testAuthMiddleware);
  app.use(router);
  return app;
}

async function seed() {
  const now = new Date();
  await testPool.query(
    `INSERT INTO "${schemaName}".wayfare_users (id, display_name, email, role) VALUES
      ($1, 'Rider One', 'rider@example.com', 'rider'),
      ($2, 'Driver One', 'driver@example.com', 'driver'),
      ($3, 'Admin One', 'admin@example.com', 'admin')
      ON CONFLICT (id) DO UPDATE SET role = EXCLUDED.role`,
    ["rider-1", "driver-1", "admin-1"],
  );
  await testPool.query(
    `INSERT INTO "${schemaName}".driver_profiles (user_id, application_status, verification_status, vehicle, vehicle_color, registration, licence_expiry, operating_licence_reference)
      VALUES ($1, 'submitted', 'pending', 'Toyota Corolla', 'White', 'ABC123', '2027-12-31', 'OL-REF')
      ON CONFLICT (user_id) DO NOTHING`,
    ["driver-1"],
  );
  // Two completed rides for driver-1: one paid, one not yet paid.
  await testPool.query(
    `INSERT INTO "${schemaName}".ride_requests (id, rider_id, driver_id, status, pickup, destination, category, currency, fare, pricing_mode, trip_data, created_at, updated_at)
      VALUES
        ('ride-paid', 'rider-1', 'driver-1', 'completed', 'A', 'B', 'standard', 'ZAR', 10000, 'estimate', '{}', $1, $1),
        ('ride-unpaid', 'rider-1', 'driver-1', 'completed', 'C', 'D', 'standard', 'ZAR', 15000, 'estimate', '{}', $1, $1),
        ('ride-other', 'rider-2', 'driver-1', 'completed', 'E', 'F', 'standard', 'ZAR', 20000, 'estimate', '{}', $1, $1)`,
    [now],
  );
  await testPool.query(
    `INSERT INTO "${schemaName}".ride_payments (id, request_id, rider_id, provider, reference, amount_minor, currency, status, captured_amount_minor, paid_at)
      VALUES
        ('pay-paid', 'ride-paid', 'rider-1', 'mock', 'ref-paid', 10000, 'ZAR', 'paid', 10000, $1),
        ('pay-unpaid', 'ride-unpaid', 'rider-1', 'mock', 'ref-unpaid', 15000, 'ZAR', 'authorized', NULL, NULL)`,
    [now],
  );
  await testPool.query(
    `INSERT INTO "${schemaName}".support_cases (id, request_id, source_message_id, reporter_user_id, category, priority, status, staff_note)
      VALUES
        (1, 'ride-paid', 'msg-1', 'rider-1', 'payment', 'high', 'open', 'Internal staff note'),
        (2, 'ride-other', 'msg-2', 'rider-2', 'driver', 'normal', 'open', NULL)`,
  );
  await testPool.query(
    `INSERT INTO "${schemaName}".driver_payout_records (driver_id, provider, external_reference, amount_minor, currency, reconciliation_status)
      VALUES ('driver-1', 'paystack', 'ref-1', 50000, 'ZAR', 'unreconciled')`,
  );
}

async function request(method, path, body, headers = {}) {
  const apiUrl = `http://127.0.0.1:${address.port}`;
  const res = await fetch(`${apiUrl}${path}`, {
    method,
    headers: { "content-type": "application/json", ...headers },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(15000),
  });
  return { status: res.status, body: await res.json() };
}

let address;

async function run() {
  await createIsolatedSchema();
  const isolatedDatabaseUrl = new URL(testDatabaseUrl);
  isolatedDatabaseUrl.searchParams.set("search_path", schemaName);
  testPool = new Pool({ connectionString: isolatedDatabaseUrl.toString(), max: 5 });
  await seed();

  const app = buildApp();
  server = app.listen(0, () => {
    address = server.address();
  });
  await new Promise((resolve) => server.once("listening", resolve));

  // --- Driver earnings ---
  {
    const riderEarnings = await request(
      "GET",
      "/driver/earnings?driverId=driver-1",
      undefined,
      { "x-test-user-id": "rider-1" },
    );
    assert.equal(riderEarnings.status, 403, "rider cannot view another driver's earnings");

    const driverEarnings = await request("GET", "/driver/earnings", undefined, {
      "x-test-user-id": "driver-1" },
    );
    assert.equal(driverEarnings.status, 200, JSON.stringify(driverEarnings.body));
    assert.equal(driverEarnings.body.driverId, "driver-1");
    assert.equal(driverEarnings.body.estimatedEarningsMinor, 45000);
    assert.equal(driverEarnings.body.paidEarningsMinor, 10000);
    assert.equal(driverEarnings.body.outstandingEarningsMinor, 35000);
    assert.equal(driverEarnings.body.rideCount, 3);
    assert.equal(driverEarnings.body.paidRideCount, 1);
    assert.equal(driverEarnings.body.records.length, 3);
    const paidRecord = driverEarnings.body.records.find(
      (record) => record.rideId === "ride-paid",
    );
    assert.equal(paidRecord.paidMinor, 10000);
    assert.equal(paidRecord.payment.status, "paid");

    const adminEarnings = await request(
      "GET",
      "/driver/earnings?driverId=driver-1",
      undefined,
      { "x-test-user-id": "admin-1" },
    );
    assert.equal(adminEarnings.status, 200, "admin can view any driver's earnings");
  }

  // --- Refund eligibility and audit ---
  {
    // Not eligible: authorized (not paid) payment.
    const notEligible = await request(
      "POST",
      "/admin/payments/pay-unpaid/refund",
      { reason: "Test" },
      { "x-test-user-id": "admin-1" },
    );
    assert.equal(notEligible.status, 409, "only paid payments can be refunded");

    // Eligible: paid payment returns 202 and does not change payment status to refunded.
    const refundResult = await request(
      "POST",
      "/admin/payments/pay-paid/refund",
      { reason: "Duplicate charge" },
      { "x-test-user-id": "admin-1" },
    );
    assert.equal(refundResult.status, 202, JSON.stringify(refundResult.body));
    assert.equal(refundResult.body.refund.status, "requested");
    assert.equal(refundResult.body.refund.amountMinor, 10000);
    assert.equal(refundResult.body.payment.status, "paid", "payment status must not change to refunded locally");
    assert.match(refundResult.body.payment.note, /provider confirms/, "note must warn money is not returned locally");

    // Missing reason -> 400
    const badReason = await request(
      "POST",
      "/admin/payments/pay-paid/refund",
      { reason: "" },
      { "x-test-user-id": "admin-1" },
    );
    assert.equal(badReason.status, 400, "refund requires a reason");

    // Non-admin cannot refund
    const nonAdmin = await request(
      "POST",
      "/admin/payments/pay-paid/refund",
      { reason: "Test" },
      { "x-test-user-id": "rider-1" },
    );
    assert.equal(nonAdmin.status, 403, "only staff can refund");

    // Audit trail recorded as a ride event
    const events = await testPool.query(
      `SELECT action, details FROM "${schemaName}".ride_events WHERE action = 'refund_requested'`,
    );
    assert.equal(events.rowCount, 1, "refund request audit event recorded");
    assert.equal(events.rows[0].details.reason, "Duplicate charge");
  }

  // --- Support case privacy ---
  {
    const riderCases = await request("GET", "/support/cases", undefined, {
      "x-test-user-id": "rider-1" },
    );
    assert.equal(riderCases.status, 200, JSON.stringify(riderCases.body));
    assert.equal(riderCases.body.cases.length, 1, "rider sees only their own cases");
    assert.equal(riderCases.body.cases[0].id, 1);
    // Staff note must be private (not returned to rider)
    assert.equal(riderCases.body.cases[0].staffNote, null, "staff note is private");

    const rider2Cases = await request("GET", "/support/cases", undefined, {
      "x-test-user-id": "rider-2" },
    );
    assert.equal(rider2Cases.body.cases.length, 1, "rider-2 sees only their own case");
    assert.equal(rider2Cases.body.cases[0].id, 2);
  }

  // --- Driver application permissions ---
  {
    // Non-rider cannot apply
    const notRider = await request(
      "POST",
      "/driver/application",
      {
        vehicle: "Toyota",
        vehicleColor: "White",
        registration: "ABC123",
        licenceExpiry: "2027-12-31",
        operatingLicenceReference: "OL-REF",
      },
      { "x-test-user-id": "driver-1" },
    );
    assert.equal(notRider.status, 403, "only riders can apply");

    // Incomplete application -> 400 with actionable error
    const incomplete = await request(
      "POST",
      "/driver/application",
      { vehicle: "", vehicleColor: "White", registration: "ABC123", licenceExpiry: "2027-12-31", operatingLicenceReference: "OL-REF" },
      { "x-test-user-id": "rider-1" },
    );
    assert.equal(incomplete.status, 400, "incomplete application is rejected");

    // Rider can see their application status
    const profile = await request("GET", "/driver/profile", undefined, {
      "x-test-user-id": "rider-1" },
    );
    assert.equal(profile.status, 200, "rider can view their driver profile");
  }

  console.log(
    "PASS: operations integration tests (earnings, refund, support privacy, application permissions).",
  );
}

try {
  await run();
} finally {
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
  }
}

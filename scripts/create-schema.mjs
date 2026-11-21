import { createRequire } from "module";
const require = createRequire("file:///D:/work/Ride/MurkyHatefulProgrammers/Wayfare-Mobile/lib/db/node_modules/pg/package.json");
const { Pool } = require("pg");
  {
    name: "wayfare_users",
    sql: "CREATE TABLE IF NOT EXISTS wayfare_users (id TEXT PRIMARY KEY, display_name TEXT NOT NULL, email TEXT, role TEXT NOT NULL DEFAULT 'rider', dev_verified BOOLEAN NOT NULL DEFAULT FALSE, is_suspended BOOLEAN NOT NULL DEFAULT FALSE, suspended_at TIMESTAMPTZ, suspended_reason TEXT, suspended_by TEXT, deleted_at TIMESTAMPTZ, updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW())"
  },
  {
    name: "driver_profiles",
    sql: "CREATE TABLE IF NOT EXISTS driver_profiles (user_id TEXT PRIMARY KEY REFERENCES wayfare_users(id) ON DELETE CASCADE, application_status TEXT NOT NULL DEFAULT 'draft', verification_status TEXT NOT NULL DEFAULT 'unverified', vehicle TEXT, vehicle_color TEXT, registration TEXT, licence_expiry TEXT, operating_licence_reference TEXT, is_online BOOLEAN NOT NULL DEFAULT FALSE, staff_note TEXT, submitted_at TIMESTAMPTZ, reviewed_at TIMESTAMPTZ, updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW())"
  },
  {
    name: "push_tokens",
    sql: "CREATE TABLE IF NOT EXISTS push_tokens (id SERIAL PRIMARY KEY, user_id TEXT NOT NULL REFERENCES wayfare_users(id) ON DELETE CASCADE, token TEXT NOT NULL, platform TEXT NOT NULL, createdAt TIMESTAMPTZ NOT NULL DEFAULT NOW(), updatedAt TIMESTAMPTZ NOT NULL DEFAULT NOW())"
  },
  {
    name: "account_deletion_requests",
    sql: "CREATE TABLE IF NOT EXISTS account_deletion_requests (id TEXT PRIMARY KEY, userId TEXT NOT NULL REFERENCES wayfare_users(id) ON DELETE RESTRICT, status TEXT NOT NULL DEFAULT 'requested', requestedAt TIMESTAMPTZ NOT NULL DEFAULT NOW(), completedAt TIMESTAMPTZ, reason TEXT)"
  {
  {
    name: "driver_subscription_records",
    sql: "CREATE TABLE IF NOT EXISTS driver_subscription_records (id TEXT PRIMARY KEY, driverId TEXT NOT NULL REFERENCES wayfare_users(id) ON DELETE RESTRICT, provider TEXT NOT NULL, externalReference TEXT NOT NULL, amountMinor INTEGER NOT NULL, currency TEXT NOT NULL DEFAULT 'ZAR', status TEXT NOT NULL DEFAULT 'pending', startedAt TIMESTAMPTZ NOT NULL DEFAULT NOW(), expiresAt TIMESTAMPTZ, metadata JSONB NOT NULL DEFAULT '{}'::jsonb, cancelledAt TIMESTAMPTZ, cancelledReason TEXT)"
    name: "audit_events",
  {
    name: "ride_requests",
    sql: "CREATE TABLE IF NOT EXISTS ride_requests (id TEXT PRIMARY KEY, driver_id TEXT REFERENCES wayfare_users(id) ON DELETE SET NULL, rider_id TEXT REFERENCES wayfare_users(id) ON DELETE SET NULL, pickup TEXT NOT NULL, destination TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'pending', fare NUMERIC NOT NULL DEFAULT 0, currency TEXT NOT NULL DEFAULT 'ZAR', createdAt TIMESTAMPTZ NOT NULL DEFAULT NOW(), updatedAt TIMESTAMPTZ NOT NULL DEFAULT NOW())"
    sql: "CREATE TABLE IF NOT EXISTS audit_events (id TEXT PRIMARY KEY, actor_user_id TEXT REFERENCES wayfare_users(id) ON DELETE SET NULL, entityType TEXT NOT NULL, entityId TEXT NOT NULL, action TEXT NOT NULL, previousState JSONB NOT NULL DEFAULT '{}'::jsonb, nextState JSONB NOT NULL DEFAULT '{}'::jsonb, reason TEXT, requestId TEXT, idempotencyKey TEXT, createdAt TIMESTAMPTZ NOT NULL DEFAULT NOW())"
  {
    name: "ride_trips",
    sql: "CREATE TABLE IF NOT EXISTS ride_trips (id TEXT PRIMARY KEY, ride_request_id TEXT REFERENCES ride_requests(id) ON DELETE SET NULL, driver_id TEXT REFERENCES wayfare_users(id) ON DELETE SET NULL, status TEXT NOT NULL DEFAULT 'pending', driver_name TEXT, passenger_name TEXT, departure_time TEXT, arrival_time TEXT, fare NUMERIC NOT NULL DEFAULT 0, currency TEXT NOT NULL DEFAULT 'ZAR', trip_distance NUMERIC, createdAt TIMESTAMPTZ NOT NULL DEFAULT NOW(), updatedAt TIMESTAMPTZ NOT NULL DEFAULT NOW())"
  {
  {
    name: "ride_messages",
    sql: "CREATE TABLE IF NOT EXISTS ride_messages (id TEXT PRIMARY KEY, trip_id TEXT NOT NULL REFERENCES ride_trips(id) ON DELETE CASCADE, sender_id TEXT NOT NULL REFERENCES wayfare_users(id) ON DELETE SET NULL, message TEXT NOT NULL, message_type TEXT NOT NULL DEFAULT 'text', createdAt TIMESTAMPTZ NOT NULL DEFAULT NOW())"
    name: "driver_compliance_checks",
  {
    name: "admin_review_records",
    sql: "CREATE TABLE IF NOT EXISTS admin_review_records (id SERIAL PRIMARY KEY, reviewer_id TEXT REFERENCES wayfare_users(id) ON DELETE SET NULL, reviewee_id TEXT NOT NULL REFERENCES wayfare_users(id) ON DELETE SET NULL, outcome TEXT NOT NULL, decision TEXT NOT NULL, reason TEXT, reviewed_at TIMESTAMPTZ)"
    sql: "CREATE TABLE IF NOT EXISTS driver_compliance_checks (id SERIAL PRIMARY KEY, user_id TEXT NOT NULL REFERENCES wayfare_users(id) ON DELETE CASCADE, document_type TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'not_reviewed', expires_on TEXT)"
  {
    name: "wayfare_roles",
    sql: "CREATE TABLE IF NOT EXISTS wayfare_roles (id SERIAL PRIMARY KEY, userId TEXT NOT NULL, role TEXT NOT NULL, createdAt TIMESTAMPTZ NOT NULL DEFAULT NOW(), updatedAt TIMESTAMPTZ NOT NULL DEFAULT NOW())"
  },
  {
    name: "admin_wallets",
    sql: "CREATE TABLE IF NOT EXISTS admin_wallets (id SERIAL PRIMARY KEY, amount NUMERIC NOT NULL DEFAULT 0, currency TEXT NOT NULL DEFAULT 'ZAR')"
  {
];

async function createTables() {
  try {
    await pool.connect();
    for (const table of TABLES) {
      try {
        await pool.query(table.sql);
        console.log("✅ Created: " + table.name);
      } catch (err) {
        if (err.message.includes("already exists")) {
          console.log("⏭  Skipped (exists): " + table.name);
        } else {
          console.error("❌ " + table.name + ":", err.message);
        }
      }
    }
  } catch (err) {
    console.error("Error:", err.message);
  } finally {
    await pool.end();
    console.log("Done!");
  }
}

createTables().catch(console.error);
    name: "driver_earnings",
    sql: "CREATE TABLE IF NOT EXISTS driver_earnings (id SERIAL PRIMARY KEY, driver_id TEXT NOT NULL, month TEXT NOT NULL, amount NUMERIC NOT NULL DEFAULT 0, currency TEXT NOT NULL DEFAULT 'ZAR', payoutId TEXT, payoutStatus TEXT)"
  {
    name: "auth_sessions",
    sql: "CREATE TABLE IF NOT EXISTS auth_sessions (token_hash TEXT PRIMARY KEY, user_id TEXT NOT NULL REFERENCES wayfare_users(id) ON DELETE CASCADE, expires_at TIMESTAMPTZ NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), CONSTRAINT auth_sessions_user_idx INDEX (user_id), CONSTRAINT auth_sessions_expiry_idx INDEX (expires_at))"

const pool = new Pool({
  connectionString: process.env.DATABASE_URL || "postgresql://postgres:postgres@localhost:5432wayfare",
});

const TABLES = [
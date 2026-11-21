import assert from "node:assert/strict";
import test from "node:test";
import {
  complianceDocumentTypes,
  isDriverComplianceStale,
  missingDriverCompliance,
  normalizeComplianceExpiry,
} from "../src/lib/driverCompliance.ts";

const today = "2026-11-03";

function verifiedChecks() {
  return complianceDocumentTypes.map((documentType) => ({
    documentType,
    status: "verified",
    expiresOn: documentType === "driver_license" ? "2027-12-31" : null,
    staffNote: null,
    reviewedAt: new Date("2026-10-30T10:00:00.000Z"),
  }));
}

test("all four current checks are required before approval", () => {
  assert.deepEqual(
    missingDriverCompliance([], today),
    [...complianceDocumentTypes],
  );
});

test("all verified checks with a current driver licence expiry are complete", () => {
  assert.deepEqual(missingDriverCompliance(verifiedChecks(), today), []);
});

test("a driver licence must have a valid future expiry", () => {
  const checks = verifiedChecks().map((check) =>
    check.documentType === "driver_license"
      ? { ...check, expiresOn: "2026-11-02" }
      : check,
  );
  assert.deepEqual(
    missingDriverCompliance(checks, today),
    ["driver_license"],
  );

  const withoutExpiry = verifiedChecks().map((check) =>
    check.documentType === "driver_license"
      ? { ...check, expiresOn: null }
      : check,
  );
  assert.deepEqual(
    missingDriverCompliance(withoutExpiry, today),
    ["driver_license"],
  );
});

test("missing expiry is allowed for checks that do not require one", () => {
  for (const documentType of [
    "identity",
    "vehicle_registration",
    "operating_permit",
  ]) {
    assert.deepEqual(
      normalizeComplianceExpiry(documentType, "verified", undefined),
      { ok: true, expiresOn: null },
    );
  }
});

test("a verified driver licence requires a valid expiry date", () => {
  assert.deepEqual(
    normalizeComplianceExpiry("driver_license", "verified", undefined),
    {
      ok: false,
      message: "A verified driver licence needs an expiry date.",
    },
  );
  assert.deepEqual(
    normalizeComplianceExpiry("driver_license", "verified", "2027-12-31"),
    { ok: true, expiresOn: "2027-12-31" },
  );
  assert.equal(
    normalizeComplianceExpiry("identity", "verified", "2026-02-30").ok,
    false,
  );
});

test("not-required checks need a recorded reason", () => {
  const noReason = verifiedChecks().map((check) =>
    check.documentType === "operating_permit"
      ? { ...check, status: "not_required" }
      : check,
  );
  assert.deepEqual(
    missingDriverCompliance(noReason, today),
    ["operating_permit"],
  );

  const withReason = noReason.map((check) =>
    check.documentType === "operating_permit"
      ? { ...check, staffNote: "Not required for this launch market." }
      : check,
  );
  assert.deepEqual(missingDriverCompliance(withReason, today), []);
});

test("unreviewed and follow-up checks remain incomplete", () => {
  const checks = verifiedChecks().map((check) =>
    check.documentType === "identity"
      ? { ...check, status: "needs_follow_up" }
      : check,
  );
  assert.deepEqual(
    missingDriverCompliance(checks, today),
    ["identity"],
  );
});

test("a check without a recorded review time is incomplete", () => {
  const checks = verifiedChecks().map((check) =>
    check.documentType === "vehicle_registration"
      ? { ...check, reviewedAt: null }
      : check,
  );
  assert.deepEqual(
    missingDriverCompliance(checks, today),
    ["vehicle_registration"],
  );
});

test("checks completed before an application resubmission need a fresh review", () => {
  const resubmittedAt = new Date("2026-11-03T12:00:00.000Z");
  assert.equal(
    isDriverComplianceStale(
      new Date("2026-11-03T11:59:59.000Z"),
      resubmittedAt,
    ),
    true,
  );
  assert.equal(
    isDriverComplianceStale(resubmittedAt, resubmittedAt),
    false,
  );
  assert.equal(
    isDriverComplianceStale(null, resubmittedAt),
    false,
  );
});

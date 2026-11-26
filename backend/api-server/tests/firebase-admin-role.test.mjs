import assert from "node:assert/strict";
import test from "node:test";
import { resolveWayfareProfileRole } from "../src/lib/firebaseAdmin.ts";

test("an explicitly allowlisted Firebase email receives admin role", () => {
  const previousPrimary = process.env.VHAYA_INITIAL_ADMIN_EMAILS;
  const previousLegacy = process.env.WAYFARE_INITIAL_ADMIN_EMAILS;
  try {
    process.env.VHAYA_INITIAL_ADMIN_EMAILS = " Bootstrap@Example.test ";
    delete process.env.WAYFARE_INITIAL_ADMIN_EMAILS;

    assert.equal(
      resolveWayfareProfileRole(false, undefined, "bootstrap@example.test"),
      "admin",
    );
    assert.equal(
      resolveWayfareProfileRole(true, "rider", "BOOTSTRAP@example.test"),
      "admin",
    );
    assert.equal(
      resolveWayfareProfileRole(true, "admin", "someone@example.test"),
      "admin",
    );
    assert.equal(
      resolveWayfareProfileRole(true, "rider", "someone@example.test"),
      "rider",
    );
    assert.equal(
      resolveWayfareProfileRole(true, "driver", "someone@example.test"),
      "driver",
    );
  } finally {
    if (previousPrimary === undefined) delete process.env.VHAYA_INITIAL_ADMIN_EMAILS;
    else process.env.VHAYA_INITIAL_ADMIN_EMAILS = previousPrimary;
    if (previousLegacy === undefined) delete process.env.WAYFARE_INITIAL_ADMIN_EMAILS;
    else process.env.WAYFARE_INITIAL_ADMIN_EMAILS = previousLegacy;
  }
});

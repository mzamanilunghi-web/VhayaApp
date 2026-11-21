import assert from "node:assert/strict";
import test from "node:test";
import { resolvePrivateObjectLocation } from "../src/lib/privateObjectDeletion.ts";

test("resolves the app's private object path without changing buckets", () => {
  assert.deepEqual(
    resolvePrivateObjectLocation("/objects/uploads/document-123", "/wayfare-bucket/private", true),
    {
      bucketName: "wayfare-bucket",
      objectName: "private/uploads/document-123",
    },
  );
});

test("resolves private Google Storage URLs into the configured private directory", () => {
  assert.deepEqual(
    resolvePrivateObjectLocation(
      "https://storage.googleapis.com/wayfare-bucket/private/uploads/document-123?download=1",
      "/wayfare-bucket/private",
      true,
    ),
    {
      bucketName: "wayfare-bucket",
      objectName: "private/uploads/document-123",
    },
  );
});

test("rejects paths that escape the configured private directory", () => {
  assert.throws(
    () =>
      resolvePrivateObjectLocation(
        "/objects/../public/profile-photo",
        "/wayfare-bucket/private",
        true,
      ),
    /outside private object storage|invalid/i,
  );
});

test("does not try to delete attachments hosted by another provider", () => {
  assert.equal(
    resolvePrivateObjectLocation(
      "https://files.example.net/shared/attachment",
      "/wayfare-bucket/private",
      false,
    ),
    null,
  );
});

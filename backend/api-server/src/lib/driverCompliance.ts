export const complianceDocumentTypes = [
  "identity",
  "driver_license",
  "vehicle_registration",
  "operating_permit",
] as const;

export type DriverComplianceDocumentType =
  (typeof complianceDocumentTypes)[number];

export type DriverComplianceCheckForReview = {
  documentType: string;
  status: string;
  expiresOn: string | null;
  staffNote: string | null;
  reviewedAt: Date | number | null;
};

export function isDriverComplianceStale(
  reviewedAt: Date | null,
  submittedAt: Date | null,
): boolean {
  return Boolean(
    reviewedAt && submittedAt && reviewedAt.getTime() < submittedAt.getTime(),
  );
}

function isIsoCalendarDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const parsed = new Date(`${value}T00:00:00.000Z`);
  return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value;
}

export function normalizeComplianceExpiry(
  documentType: string,
  status: string,
  value: unknown,
):
  | { ok: true; expiresOn: string | null }
  | { ok: false; message: string } {
  if (value === undefined || value === null || value === "") {
    if (documentType === "driver_license" && status === "verified") {
      return {
        ok: false,
        message: "A verified driver licence needs an expiry date.",
      };
    }
    return { ok: true, expiresOn: null };
  }

  if (typeof value !== "string" || !isIsoCalendarDate(value)) {
    return {
      ok: false,
      message: "Enter a valid expiry date or leave the field blank.",
    };
  }

  return { ok: true, expiresOn: value };
}

export function missingDriverCompliance(
  checks: readonly DriverComplianceCheckForReview[],
  today: string,
): DriverComplianceDocumentType[] {
  const checksByType = new Map(checks.map((check) => [check.documentType, check]));
  return complianceDocumentTypes.filter((documentType) => {
    const check = checksByType.get(documentType);
    if (!check) return true;
    const hasReviewTimestamp =
      check.reviewedAt instanceof Date
        ? Number.isFinite(check.reviewedAt.getTime())
        : typeof check.reviewedAt === "number" &&
          Number.isFinite(check.reviewedAt);
    if (!hasReviewTimestamp) return true;

    if (check.status === "not_required") {
      return !check.staffNote?.trim();
    }
    if (check.status !== "verified") return true;

    const expiryIsInvalid =
      check.expiresOn !== null &&
      (!isIsoCalendarDate(check.expiresOn) || check.expiresOn < today);
    const licenceExpiryMissing =
      documentType === "driver_license" && !check.expiresOn;
    return expiryIsInvalid || licenceExpiryMissing;
  });
}

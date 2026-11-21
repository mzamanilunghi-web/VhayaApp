# Threat Model

## Project Overview

Vhaya is a South African ride-service backend built with Firebase
Authentication, an Express API, PostgreSQL and Drizzle. The API supports rider,
driver and operations workflows. Driver application files are stored in a
private Replit App Storage bucket and are never served through public URLs.

## Assets

- **Driver identity and eligibility documents** — identity, licence, vehicle
  registration and operating-permit files can enable identity theft or
  fraudulent driver approval if exposed or altered.
- **Account identity and roles** — verified Firebase user IDs and
  server-managed rider, driver and admin roles control access to applications,
  files and ride operations.
- **Application and compliance records** — submitted details, review outcomes,
  staff notes and timestamps support safe driver activation and auditability.
- **Storage configuration and database credentials** — compromise could expose
  all stored documents or permit unauthorized changes.

## Trust Boundaries

- **Mobile/web client to API** — clients submit untrusted profile values and
  document bytes; the API must authenticate, validate and authorize each action.
- **API to PostgreSQL** — the API stores application metadata and review state;
  every query must be scoped to the authenticated applicant or an authorized
  admin.
- **API to App Storage** — the server stores files in a private bucket and
  proxies retrieval only after authorization; object keys must not be exposed.
- **Applicant to operations staff** — applicants may upload and replace their
  own files while an application is editable; only admins may retrieve
  submitted files or mark compliance checks.

## Scan Anchors

- Production API entry point: `backend/api-server/src/app.ts`.
- Highest-risk routes: `backend/api-server/src/routes/rides.ts` and
  `backend/api-server/src/routes/admin.ts`.
- Identity and role checks: `backend/api-server/src/middlewares/`.
- Generated build output and `artifacts/mockup-sandbox/` are not production
  runtime sources.

## Threat Categories

### Spoofing

An unauthenticated or impersonated user could submit or retrieve driver
documents. Every applicant and admin document route MUST verify the Firebase
identity server-side; staff retrieval MUST also require an admin role.

### Tampering

Client-provided document type, file name, MIME type, application ID or role
cannot be trusted. The API MUST derive ownership from the verified user ID,
allow only known document types, validate file size and file signatures, and
keep role assignment exclusively in the existing admin approval path.

### Repudiation

A driver or staff member may dispute when a file was submitted or reviewed.
Uploads and compliance decisions MUST retain server-generated timestamps and
the existing admin review audit trail; logs MUST NOT include file contents,
document names or storage keys.

### Information Disclosure

Public or cross-user access to identity and eligibility records could cause
serious harm. The bucket MUST remain private, API responses MUST expose only
document metadata to the owner, and file downloads MUST be restricted to the
owner (for editable applications) or authorized admins, with no-store headers.
The project needs an approved privacy notice and retention period before
publicly collecting documents.

### Denial of Service

Large uploads can consume memory, storage and API capacity. Uploads MUST have a
strict size limit and a fixed allowlist of image/PDF types; storage failures
MUST fail explicitly without granting access.

### Elevation of Privilege

Applicants must not approve themselves, change roles or access another
applicant's files by changing IDs. Every decision and download MUST be checked
server-side, and driver online/dispatch access MUST remain gated on staff
approval and compliance review.

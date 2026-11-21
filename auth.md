# Wayfare authentication and data plan

## Current direction

- Firebase Authentication is Wayfare's only sign-in provider. Require a verified email before an account can use the API.
- Firestore is the authoritative store for Firebase user profiles and access roles (`rider`, `driver`, and `admin`).
- Replit PostgreSQL remains authoritative for rides, payments, wallets, support cases, driver operations, and other relational application data.
- PostgreSQL's `wayfare_users` rows remain a server-maintained identity projection for operational foreign keys and SQL joins. Firebase/Firestore are authoritative for Firebase identity fields and roles.
- Clerk sign-in has been removed. Existing legacy identity and ride records are retained, but are not automatically available to Firebase accounts.

## Data flow

1. A client signs in with Firebase Authentication and sends its Firebase ID token to the API.
2. The API verifies the token with Firebase Admin, requires a verified email, and reads or creates the user's Firestore profile. New profiles default to `rider`; the trusted server-side initial-admin allowlist may create the first admin profile.
3. The API mirrors the verified profile into PostgreSQL for existing operational relations. Authorization for Firebase users reads the Firestore role; clients never supply or grant their own role.
4. Ride, payment, wallet, support, and driver-operation data continue to be read and written in PostgreSQL.
5. Staff role changes update Firestore and the PostgreSQL projection through the authenticated API.

## Migration and safety rules

- Do not link Firebase and legacy accounts by matching email. Use an explicit, reviewed Firebase UID mapping.
- Do not delete or rewrite existing PostgreSQL or Firestore records as part of authentication setup.
- A Firebase UID that collides with an existing PostgreSQL account ID but has no Firestore profile is rejected for manual review.
- Keep existing PostgreSQL and Firestore records intact. Any legacy-account access requires an explicit, reviewed Firebase UID mapping; do not restore Clerk as a fallback.
- Do not move ride or payment records to Firestore or add two-way synchronization.
- Do not assume production data is empty or apply development schema changes to production. Production PostgreSQL schema changes use Replit's Publish flow.

## Credentials and Firebase project setup

- Use the `irgadgetsofficialweb` Firebase project configured in `firebase.md` for the Firebase Admin SDK and any future client.
- Client-side Firebase configuration is not needed by the backend; configure it only when a client application is added.
- The Firebase service-account JSON is stored only in Replit Secrets as `FIREBASE_SERVICE_ACCOUNT_JSON` and must belong to the same project. Never copy it into source, the client bundle, or logs.
- Enable Firebase email/password sign-in in the Firebase project before testing account creation.
- Firestore profile and role documents are accessed through the API's Admin SDK. Before adding a client, verify the active Firestore security rules prevent clients from changing role fields; do not replace existing project rules without reviewing their other collection access.

## Remaining migration work

- Review existing Firebase and PostgreSQL records and prepare explicit Firebase UID mappings only if access to legacy accounts is required.
- The API development command applies the Drizzle schema before it starts accepting requests; production changes remain on Replit's Publish flow.
- Test Firebase sign-in, email verification, profile creation, role checks, role changes, and representative PostgreSQL operations.

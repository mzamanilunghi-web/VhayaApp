import { cert, getApps, initializeApp } from "firebase-admin/app";
import { getAuth, type DecodedIdToken } from "firebase-admin/auth";
import { FieldValue, getFirestore } from "firebase-admin/firestore";

export type WayfareFirebaseRole = "admin" | "driver" | "rider";

export type WayfareFirebaseProfile = {
  uid: string;
  displayName: string;
  email: string;
  emailVerified: true;
  role: WayfareFirebaseRole;
};

type ServiceAccount = {
  project_id?: unknown;
  client_email?: unknown;
  private_key?: unknown;
};

const profileCollection = "wayfare_users";
const PROFILE_CACHE_MS = 5_000;
const profileCache = new Map<
  string,
  { expiresAt: number; profile: WayfareFirebaseProfile | null }
>();

function validRole(value: unknown): value is WayfareFirebaseRole {
  return value === "admin" || value === "driver" || value === "rider";
}

function configuredInitialAdmin(email: string): boolean {
  const configuredEmails =
    process.env["VHAYA_INITIAL_ADMIN_EMAILS"] ||
    process.env["WAYFARE_INITIAL_ADMIN_EMAILS"];
  return Boolean(
    configuredEmails
      ?.split(/[,;\s]+/)
      .map((candidate) => candidate.trim().toLowerCase())
      .filter(Boolean)
      .includes(email),
  );
}

function firebaseApp() {
  const existing = getApps().find((candidate) => candidate.name === "vhaya-admin");
  if (existing) return existing;

  const serialized = process.env["FIREBASE_SERVICE_ACCOUNT_JSON"];
  if (!serialized) {
    throw new Error("FIREBASE_SERVICE_ACCOUNT_JSON is required for Firestore access.");
  }

  let account: ServiceAccount;
  try {
    account = JSON.parse(serialized) as ServiceAccount;
  } catch {
    throw new Error("FIREBASE_SERVICE_ACCOUNT_JSON must contain valid JSON.");
  }

  if (
    typeof account.project_id !== "string" ||
    typeof account.client_email !== "string" ||
    typeof account.private_key !== "string"
  ) {
    throw new Error("Firebase service-account JSON is missing required fields.");
  }

  return initializeApp(
    {
      credential: cert({
        projectId: account.project_id,
        clientEmail: account.client_email,
        privateKey: account.private_key.replace(/\\n/g, "\n"),
      }),
      projectId: account.project_id,
    },
    "vhaya-admin",
  );
}

export function firebaseAdminAuth() {
  return getAuth(firebaseApp());
}

function firestore() {
  return getFirestore(firebaseApp());
}

async function readProfile(
  uid: string,
): Promise<WayfareFirebaseProfile | null> {
  const snapshot = await firestore().collection(profileCollection).doc(uid).get();
  if (!snapshot.exists) return null;
  const data = snapshot.data();
  if (!data) return null;
  return {
    uid,
    displayName: typeof data.displayName === "string" ? data.displayName : "",
    email: typeof data.email === "string" ? data.email : "",
    emailVerified: true,
    role: validRole(data.role) ? data.role : "rider",
  };
}

export async function getFirebaseWayfareProfile(
  uid: string,
): Promise<WayfareFirebaseProfile | null> {
  const cached = profileCache.get(uid);
  if (cached && cached.expiresAt > Date.now()) return cached.profile;
  const profile = await readProfile(uid);
  profileCache.set(uid, { profile, expiresAt: Date.now() + PROFILE_CACHE_MS });
  if (profileCache.size > 5_000) {
    const now = Date.now();
    for (const [cachedUid, entry] of profileCache) {
      if (entry.expiresAt <= now) profileCache.delete(cachedUid);
    }
  }
  return profile;
}

export async function ensureFirebaseWayfareProfile(
  token: DecodedIdToken,
): Promise<WayfareFirebaseProfile> {
  const uid = token.uid;
  const email =
    typeof token.email === "string" ? token.email.trim().toLowerCase() : "";
  if (!uid || !email || token.email_verified !== true) {
    throw new Error("A verified Firebase email is required.");
  }

  const displayName =
    (typeof token.name === "string" ? token.name.trim() : "") ||
    email.split("@")[0] ||
    "Vhaya rider";
  const ref = firestore().collection(profileCollection).doc(uid);
  const profile = await firestore().runTransaction(async (transaction) => {
    const existing = await transaction.get(ref);
    const data = existing.data();
    const role = existing.exists
      ? validRole(data?.role)
        ? data.role
        : "rider"
      : configuredInitialAdmin(email)
        ? "admin"
        : "rider";

    const shouldWrite =
      !existing.exists ||
      data?.uid !== uid ||
      data?.email !== email ||
      data?.displayName !== displayName ||
      data?.emailVerified !== true ||
      !validRole(data?.role);
    if (!shouldWrite) {
      return { uid, email, displayName, emailVerified: true as const, role };
    }

    transaction.set(
      ref,
      {
        uid,
        email,
        displayName,
        emailVerified: true,
        role,
        ...(!existing.exists || !data?.createdAt
          ? { createdAt: FieldValue.serverTimestamp() }
          : {}),
        updatedAt: FieldValue.serverTimestamp(),
      },
      { merge: true },
    );
    return { uid, email, displayName, emailVerified: true as const, role };
  });
  profileCache.set(uid, { profile, expiresAt: Date.now() + PROFILE_CACHE_MS });
  return profile;
}

export async function setFirebaseWayfareRole(
  uid: string,
  role: WayfareFirebaseRole,
): Promise<boolean> {
  const ref = firestore().collection(profileCollection).doc(uid);
  const updated = await firestore().runTransaction(async (transaction) => {
    const existing = await transaction.get(ref);
    if (!existing.exists) return false;
    transaction.update(ref, {
      role,
      updatedAt: FieldValue.serverTimestamp(),
    });
    return true;
  });
  if (updated) {
    const cached = profileCache.get(uid);
    if (cached?.profile) {
      profileCache.set(uid, {
        profile: { ...cached.profile, role },
        expiresAt: Date.now() + PROFILE_CACHE_MS,
      });
    } else {
      profileCache.delete(uid);
    }
  }
  return updated;
}

export async function deleteFirebaseWayfareAccount(uid: string): Promise<void> {
  profileCache.delete(uid);
  try {
    await firebaseAdminAuth().deleteUser(uid);
  } catch (error) {
    if (
      !error ||
      typeof error !== "object" ||
      !("code" in error) ||
      error.code !== "auth/user-not-found"
    ) {
      throw error;
    }
  }

  await firestore().collection(profileCollection).doc(uid).delete();
  profileCache.delete(uid);
}

export function verifyFirebaseIdToken(token: string) {
  return firebaseAdminAuth().verifyIdToken(token, true);
}

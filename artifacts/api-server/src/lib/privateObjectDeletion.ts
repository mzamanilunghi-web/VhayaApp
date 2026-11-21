import { Storage } from "@google-cloud/storage";

const REPLIT_SIDECAR_ENDPOINT = "http://127.0.0.1:1106";

const objectStorageClient = new Storage({
  credentials: {
    audience: "replit",
    subject_token_type: "access_token",
    token_url: `${REPLIT_SIDECAR_ENDPOINT}/token`,
    type: "external_account",
    credential_source: {
      url: `${REPLIT_SIDECAR_ENDPOINT}/credential`,
      format: {
        type: "json",
        subject_token_field_name: "access_token",
      },
    },
    universe_domain: "googleapis.com",
  },
  projectId: "",
});

type ObjectLocation = { bucketName: string; objectName: string };

export function resolvePrivateObjectLocation(
  rawKey: string,
  privateObjectDir: string,
  required: boolean,
): ObjectLocation | null {
  const privateDir = `/${privateObjectDir.replace(/^\/+|\/+$/g, "")}`;
  if (privateDir === "/") {
    if (required) throw new Error("Private object storage is not configured.");
    return null;
  }

  let objectPath = rawKey.trim();
  if (objectPath.startsWith("https://storage.googleapis.com/")) {
    objectPath = new URL(objectPath).pathname;
  } else if (objectPath.startsWith("gs://")) {
    const path = objectPath.slice("gs://".length);
    const separator = path.indexOf("/");
    if (separator < 1) {
      if (required) throw new Error("The stored document key is invalid.");
      return null;
    }
    objectPath = `/${path.slice(0, separator)}/${path.slice(separator + 1)}`;
  } else if (objectPath.startsWith("/objects/")) {
    const entityPath = objectPath.slice("/objects/".length);
    objectPath = `${privateDir}/${entityPath}`;
  }

  if (!objectPath.startsWith(`${privateDir}/`)) {
    if (required) {
      throw new Error("A stored driver document is outside private object storage.");
    }
    return null;
  }

  const relativeObjectPath = objectPath.slice(privateDir.length + 1);
  const privateSegments = privateDir.slice(1).split("/");
  const objectSegments = relativeObjectPath.split("/");
  if (
    objectSegments.length === 0 ||
    objectSegments.some((part) => !part || part === "." || part === "..")
  ) {
    if (required) throw new Error("The stored document key is invalid.");
    return null;
  }

  return {
    bucketName: privateSegments[0],
    objectName: [...privateSegments.slice(1), ...objectSegments].join("/"),
  };
}

export async function deleteAccountPrivateObjects(input: {
  driverDocumentKeys: string[];
  messageAttachmentReferences: string[];
}): Promise<void> {
  if (input.driverDocumentKeys.length === 0 && input.messageAttachmentReferences.length === 0) {
    return;
  }

  const privateObjectDir = process.env.PRIVATE_OBJECT_DIR;
  if (!privateObjectDir) {
    if (
      input.driverDocumentKeys.length > 0 ||
      input.messageAttachmentReferences.some(
        (key) =>
          key.startsWith("/objects/") ||
          key.startsWith("gs://") ||
          key.startsWith("https://storage.googleapis.com/"),
      )
    ) {
      throw new Error("Private object storage is not configured.");
    }
    return;
  }

  const locations = new Map<string, ObjectLocation>();
  for (const key of input.driverDocumentKeys) {
    const location = resolvePrivateObjectLocation(key, privateObjectDir, true);
    if (location) locations.set(`${location.bucketName}/${location.objectName}`, location);
  }
  for (const reference of input.messageAttachmentReferences) {
    const location = resolvePrivateObjectLocation(reference, privateObjectDir, false);
    if (location) locations.set(`${location.bucketName}/${location.objectName}`, location);
  }

  await Promise.all(
    [...locations.values()].map(({ bucketName, objectName }) =>
      objectStorageClient
        .bucket(bucketName)
        .file(objectName)
        .delete({ ignoreNotFound: true }),
    ),
  );
}

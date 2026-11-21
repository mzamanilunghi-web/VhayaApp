import { assertRuntimeConfig } from "./lib/runtimeConfig";

assertRuntimeConfig();

const rawPort = process.env["PORT"];

if (!rawPort) {
  throw new Error(
    "PORT environment variable is required but was not provided.",
  );
}

const port = Number(rawPort);

if (Number.isNaN(port) || port <= 0) {
  throw new Error(`Invalid PORT value: "${rawPort}"`);
}

const [{ default: app }, { logger }] = await Promise.all([
  import("./app"),
  import("./lib/logger"),
]);

const server = app.listen(port, () => {
  logger.info({ port }, "Server listening");

  if (
    process.env.NODE_ENV !== "production" &&
    process.env.WAYFARE_DISABLE_DEV_TUNNEL !== "true"
  ) {
    void startDevelopmentTunnel();
  }
});

server.on("error", (err) => {
  logger.error({ err }, "Error listening on port");
  process.exit(1);
});

async function startDevelopmentTunnel(): Promise<void> {
  try {
    const { default: ngrok } = await import("@ngrok/ngrok");
    const listener = await ngrok.forward({
      addr: `127.0.0.1:${port}`,
      authtoken_from_env: true,
    });
    const publicUrl = listener.url();

    if (!publicUrl) {
      throw new Error("ngrok started without returning a public URL");
    }

    if (!process.env.PAYSTACK_CALLBACK_URL) {
      process.env.PAYSTACK_CALLBACK_URL = `${publicUrl}/api/payments/callback`;
    }

    logger.info(
      { apiBaseUrl: `${publicUrl}/api/` },
      "Public API base URL",
    );
  } catch (err) {
    logger.error({ err }, "Failed to start the public ngrok API tunnel");
    server.close(() => process.exit(1));
  }
}

import { mkdir, rm } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { build } from "esbuild";

const artifactDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const entryPoint = path.join(artifactDir, "tests/rides-concurrency.integration.mjs");
const outputDir = path.join(
  artifactDir,
  "node_modules/.cache",
  `wayfare-rides-concurrency-${process.pid}`,
);
await mkdir(outputDir, { recursive: true });
const outputFile = path.join(outputDir, "integration-test.mjs");

function safeErrorMessage(error) {
  const message = error instanceof Error ? error.message : String(error);
  return message.replace(/(?:postgres|postgresql):\/\/[^\s"'`]+/gi, "[database connection URL redacted]");
}

try {
  await build({
    entryPoints: [entryPoint],
    outfile: outputFile,
    bundle: true,
    platform: "node",
    format: "esm",
    target: "node22",
    external: ["*.node", "pg-native"],
    logLevel: "silent",
    banner: {
      js: `
        import { createRequire as __createRequire } from "node:module";
        globalThis.require = __createRequire(import.meta.url);
      `,
    },
  });
  await import(pathToFileURL(outputFile).href);
} catch (error) {
  console.error("Ride concurrency integration test failed:", safeErrorMessage(error));
  process.exitCode = 1;
} finally {
  await rm(outputDir, { recursive: true, force: true });
}

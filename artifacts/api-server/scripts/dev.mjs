import { spawnSync } from "node:child_process";

const steps = [
  ["pnpm", ["--filter", "@workspace/db", "run", "push"]],
  ["pnpm", ["run", "build"]],
  ["node", ["--enable-source-maps", "./dist/index.mjs"]],
];

for (const [command, args] of steps) {
  const result = spawnSync(command, args, {
    cwd: process.cwd(),
    env: process.env,
    stdio: "inherit",
  });

  if (result.error) {
    throw result.error;
  }

  if (result.status !== 0) {
    process.exit(result.status ?? 1);
  }
}

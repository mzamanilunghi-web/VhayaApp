---
name: Managed artifact services
description: Safe changes to generated Replit artifact configuration and service workflows.
---

Treat `.replit-artifact/artifact.toml` and its generated service workflow as platform-managed. Direct edits to the TOML and workflow overrides are rejected.

**Why:** the platform maintains artifact metadata and its service workflow together.

**How to apply:** Use the artifact TOML replacement flow for service or port changes, then restart the exact managed workflow instead of creating a duplicate. The managed API workflow supplies its required port setting; do not replace it with a manually configured backend workflow. Stop a manually created workflow before removing it and confirm its child processes exited; removing its workflow entry can leave PNPM/Expo processes holding ports.

**Why:** removing temporary workflow entries during a service handoff left their child processes running and caused port conflicts with the generated workflows.

**Why:** the API source requires `PORT`, but its artifact-managed workflow starts successfully without an inline `PORT` assignment.

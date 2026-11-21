---
name: Wayfare development schema sync
description: Apply the development database schema before relying on account persistence or sign-in.
---

When working directly in development, do not assume the PostgreSQL schema has been synchronized. Before exercising authenticated requests, inspect the required tables and use the existing `pnpm --filter @workspace/db run push` flow to apply the Drizzle schema to development. Do not run development DDL against production; production changes use Replit's Publish schema flow.

**Why:** Firebase can validate a token successfully while the first protected API request still fails at user synchronization if the development database is empty or missing `wayfare_users`.

**How to apply:** When debugging Firebase-authenticated API requests, check server credentials and the development schema separately. After the supported schema push, verify login and a protected endpoint; do not mistake a missing user table for an invalid token or CORS issue.

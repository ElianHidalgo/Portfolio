# Part 5b — Security Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the risks found in the security analysis before the contact form (part 6) and authentication (part 7) store sensitive data: least-privilege database access with one schema per module, rate limiting, uniform error responses, API security headers, static analysis in CI, and the deferred operational fixes — all at zero cost.

**Architecture:** Each module owns a PostgreSQL schema (`profile`, `projects`); Flyway (owner role) creates and migrates them; the app runs as a separate role with data-only rights (no DDL), so a flaw in any module can neither change the schema nor drop tables. A servlet filter in `shared` rate-limits per client IP (Bucket4j, in memory — one instance). All errors leave as `application/problem+json`. CodeQL and dependency review run in GitHub Actions (free for public repos).

**Tech Stack:** Spring Boot 4.1.2, Flyway, PostgreSQL roles/schemas, Bucket4j (in-memory), Testcontainers, GitHub Actions (CodeQL, dependency-review), Cloudflare `_headers`.

**Spec:** `docs/superpowers/specs/2026-11-14-portafolio-design.md` rev 3 (5.3, 5.5, 5.8; anti-goals: no over-engineering; zero cost).

## Global Constraints

- Zero cost; no new services. Code, comments, commits and docs in English; branch `feat/part-5b-hardening`; Conventional Commits with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- **No secrets in git or chat.** New credentials are typed by the user into Neon/Render only.
- **Simplicity over purity:** one runtime role for the whole app (data-only), not one role per module — per-module roles need one DataSource per module, which the spec's anti-goals rule out for this size. Documented as a deliberate trade-off.
- Deploy order matters: the Neon role and Render variables (Task 2 user steps) must exist **before** merging to `main`, or the next Render deploy fails to connect.

## Review Focus

1. **Runtime role tries DDL** (a bug or injection runs `DROP TABLE`/`CREATE TABLE`) — rejected by PostgreSQL. Pinned in Task 1 Step 1.
2. **Spoofed client IP** (`X-Forwarded-For: 1.2.3.4` sent by an attacker to dodge the limit) — limiter keys on the address the proxy chain guarantees. Pinned in Task 3 Step 1.
3. **Rate limit hits a real visitor or the Cloudflare build** — build makes ~10 requests; the limit leaves wide margin and answers 429 with `Retry-After`. Pinned in Task 3 Step 1.
4. **Malformed URLs and `/error`** — no HTML error pages, no server fingerprint. Pinned in Task 4 Step 1.
5. **Migration on an existing production database** (tables already in `public`) — moved without data loss. Pinned in Task 1 Step 1 (migration test from V6 state) and Task 6.

---

### Task 1: Schema per module and data-only runtime role

**Files:** `backend/src/main/resources/db/migration/V7__schema_per_module_and_app_role.sql`; entities `Profile`, `Skill`, `Project` (`@Table(schema = ...)`); `application.yml` (`spring.flyway.user/password` from `DB_MIGRATION_USERNAME`/`DB_MIGRATION_PASSWORD`, defaulting to the app credentials locally; `spring.flyway.default-schema: public`); `TestcontainersConfiguration`; new `backend/src/test/java/.../DatabasePrivilegesTests.java`.

- [ ] **Step 1: Failing tests**
  - `tablesLiveInTheirModuleSchema` — `profile.profile`, `profile.skill`, `projects.project` exist; nothing business-related in `public`.
  - `runtimeRoleCannotChangeTheSchema` — connecting as `portfolio_app`: `create table projects.x(id int)` and `drop table profile.skill` fail with SQLState `42501`.
  - `runtimeRoleCanReadAndWriteData` — as `portfolio_app`: `select` from both schemas and `update` a project title succeed.
  - existing API tests stay green (they run as the app role).
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement** V7: `create schema if not exists profile/projects`; `alter table … set schema …`; create role `portfolio_app NOLOGIN` only if missing (production gets a LOGIN role from the Neon console; no password ever in SQL); `grant usage on schema`, `grant select, insert, update, delete on all tables`, `grant usage, select on all sequences`, `alter default privileges` for future tables; `revoke create on schema public from public`. Testcontainers: start Postgres with an init script that creates `portfolio_app LOGIN PASSWORD 'test'` and point the app datasource at it, Flyway at the superuser.
- [ ] **Step 4: Run** full suite → pass.
- [ ] **Step 5: Commit** `feat(backend): schema per module and a data-only runtime database role`

### Task 2: Production credentials split (user + verification)

- [ ] **User (Neon console):** Roles → **New role** `portfolio_app` → copy the generated password (never in chat).
- [ ] **User (Render → Environment):** `DB_USERNAME=portfolio_app`, `DB_PASSWORD=<new>`, `DB_MIGRATION_USERNAME=neondb_owner`, `DB_MIGRATION_PASSWORD=<owner password>`; **Save only** (deploy happens on merge). Health Check Path `/api/health/liveness`.
- [ ] Merge only after both are confirmed; verify on Render logs `Successfully applied 1 migration` and the API answers.

### Task 3: Rate limiting

**Files:** `backend/pom.xml` (`com.bucket4j:bucket4j_jdk17-core`, exact version), `shared/web/RateLimitFilter.java`, `application.yml` (`app.rate-limit.requests-per-minute: ${RATE_LIMIT_PER_MINUTE:60}`), test `shared/RateLimitTests.java`.

- [ ] **Step 1: Failing tests** — `limitsRequestsPerClient` (61st request in a minute from one client → 429, `Retry-After` header, problem JSON detail `"Too many requests"`); `clientsAreLimitedIndependently`; `ignoresSpoofedForwardedForHeader` (rotating `X-Forwarded-For` values from the same client still hit the limit); `healthIsNotLimited`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement** — key = `CF-Connecting-IP` (set and overwritten by Cloudflare in front of Render — verified in Task 6), else `request.getRemoteAddr()`; never `X-Forwarded-For`. Buckets in a bounded `ConcurrentHashMap` (evict entries idle > 10 min). **Step 4: Run** → pass. **Step 5: Commit** `feat(backend): per-client rate limiting`

### Task 4: Uniform errors and API security headers

**Files:** `shared/web/ProblemErrorController.java` (replaces Boot's `/error` body with `ProblemDetail`), Tomcat customizer (`ErrorReportValve` `showReport=false`, `showServerInfo=false`), `shared/web/SecurityHeadersFilter.java`, tests in `shared/ErrorAndHeadersTests.java`.

- [ ] **Step 1: Failing tests** — `GET /error` → 404 problem JSON (not 500/999); every API response has `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `Cache-Control: no-store` on `/api/admin/**` patterns (none yet) and public `Cache-Control: public, max-age=60` on `GET /api/profile|projects`.
- [ ] **Step 2–4:** RED → implement → GREEN. **Step 5: Commit** `feat(backend): problem-json errors everywhere and API security headers`

### Task 5: CI, supply chain and deferred operational fixes

- [ ] `.github/workflows/codeql.yml` — CodeQL for `java-kotlin` (build-mode `none`) and `javascript-typescript`, on push/PR + weekly; `permissions: security-events: write, contents: read`.
- [ ] `ci.yml` — `persist-credentials: false` on checkouts; `actions/dependency-review-action` on pull requests (fails on high-severity vulnerable dependencies).
- [ ] `dependabot.yml` — ignore semver-major for `typescript` and `@types/node`; add `docker-compose` ecosystem.
- [ ] Node 22 everywhere (`frontend/Dockerfile` → `node:22-alpine`).
- [ ] Backend JVM: `MaxRAMPercentage=60`, `server.tomcat.threads.max: 20`.
- [ ] Frontend: `fetchWithRetry` per-attempt timeout 60 s (test: hanging fetch aborts); `verifySiteUrl` runs even with `PRERENDER=false` (test); `_headers`: font cache rules per locale (`/es/media/*`, `/en/media/*`), `X-Robots-Tag: noindex` for `/*/index.csr*` and `/404*` (tests in `copy-root.test.mjs`).
- [ ] Spec text: Pages → Workers static assets, deploy hook wording (§5.8, §6), ci.yml comment.
- [ ] Commit per area (`ci: …`, `build: …`, `fix(frontend): …`, `docs: …`).

### Task 6: Verify in production and review

- [ ] After merge: Render logs show V7 applied; API answers; probe: DDL impossible (by design, via tests), 61 rapid requests → 429 with `Retry-After`, spoofed `X-Forwarded-For` doesn't reset it, `/error` and malformed URLs → problem JSON / minimal page, API headers present; site still prerenders (build makes few API calls) and fonts are `immutable`.
- [ ] CodeQL first run: no high-severity alerts (or triaged).
- [ ] Final whole-branch review by a fresh reviewer; fix Critical/Important with TDD.

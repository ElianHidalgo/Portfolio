# Part 5 — First Deployment (Cloudflare Pages + Render + Neon) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The read-only portfolio is live on the internet at zero cost: static prerendered site on Cloudflare Pages, Spring Boot API on Render's free web service, PostgreSQL on Neon — with CI, dependency updates and security headers.

**Architecture:** Visitors only ever load static files from Cloudflare (public navigation becomes full page loads of prerendered HTML, so no visitor wakes the sleeping backend). Cloudflare's build reads the API on Render (`API_URL`) to prerender. Render builds the existing backend Dockerfile from `main`; Neon holds the data. GitHub Actions runs every test suite on each push/PR.

**Tech Stack:** Cloudflare Pages (free, `_redirects`, `_headers`), Render free web service (Docker, Virginia), Neon free Postgres (AWS us-east-1), GitHub Actions + Dependabot, Angular `security.autoCsp`, Spring Boot 4.1 config.

**Spec:** `docs/superpowers/specs/2026-11-14-portafolio-design.md` rev 3 — 2 (coste cero absoluto), 4.3, 5.1, 5.5, 5.8, 6 part 5.

## Global Constraints

- **Zero cost, absolute:** only free tiers that need no card; if any screen asks for payment, stop and ask the user.
- **No secrets in git or chat:** DB credentials only in Render's environment settings; `.env` stays ignored.
- Code, comments, commits and docs in English; Conventional Commits ending with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`; branch `feat/part-5-deploy`.
- Neon region AWS us-east-1 (Virginia) ↔ Render region Virginia. Use Neon's **direct** host (no `-pooler`) so Flyway migrations work.
- Render free limits: 512 MB RAM, 0.1 CPU, sleeps after 15 min, ~30–60 s wake; Render sets `PORT`.
- Public pages must not call the API at runtime (first load uses transfer cache; navigation loads prerendered HTML).

## Review Focus

1. **Visitor navigates home → project → back** in production — every page appears instantly, no request to `/api/*`. Pinned in Task 2 Step 1 and Task 5.
2. **Cloudflare build while Render is asleep** — the build waits for the wake-up instead of failing early. Pinned in Task 2 Step 1 (fetch retry) and Task 5.
3. **Build without `SITE_URL` on Cloudflare** — fails instead of publishing `localhost` canonical URLs. Pinned in Task 2 Step 1.
4. **Unknown URL on Pages** (`/es/projects/nope`, `/es/admin`) — client shell shows not-found, not the language picker. Pinned in Task 2 Step 1 (`_redirects`) and Task 5.
5. **Spring Boot on 0.1 CPU / 512 MB** — starts within Render's health-check window without OOM. Pinned in Task 1 Step 4 and Task 4.

---

### Task 1: Backend ready for Render + Neon

**Files:** `backend/src/main/resources/application.yml`, `backend/Dockerfile`, `backend/src/test/java/dev/elianhidalgo/portfolio/HealthEndpointTests.java`

- [ ] **Step 1: Failing test** — `actuatorDiscoveryPageIsNotExposed`: `GET /api` → status 404.
- [ ] **Step 2: Run** `./mvnw -B test -Dtest=HealthEndpointTests` → FAIL (200 links page).
- [ ] **Step 3: Implement** — `management.endpoints.web.discovery.enabled: false`; Hikari `maximum-pool-size: ${DB_POOL_SIZE:3}`, `minimum-idle: 0`, `connection-timeout: 20000` (Neon free + scale-to-zero); Dockerfile `ENV JAVA_TOOL_OPTIONS="-XX:+UseSerialGC -XX:MaxRAMPercentage=70 -XX:TieredStopAtLevel=1 -Xss512k -XX:+ExitOnOutOfMemoryError"` (low memory, faster start on 0.1 CPU).
- [ ] **Step 4: Run** full suite → pass; `docker run` the image with `--cpus=0.1 --memory=512m` against the local Postgres → `/api/health` UP; record startup time from the log.
- [ ] **Step 5: Commit** `feat(backend): production settings for Render free tier and Neon`

### Task 2: Frontend ready for Cloudflare Pages

**Files:** templates with public links (`projects-section.html`, `project-page.html`, `site-header.html`), `src/app/core/build-env.ts`, `src/app/app.routes.server.ts`, `scripts/copy-root.mjs` (+ test), `scripts/verify-prerender.mjs` (+ test), `angular.json`, new `root/_redirects`, `root/_headers`, specs touching hrefs.

**Interfaces:** public links are plain `href`s relative to `<base href>` (`projects/<slug>/`, `./#projects`); `fetchWithRetry(url, attempts = 6, delayMs = 15000)` used by `getPrerenderParams` (tolerates a ~90 s wake-up).

- [ ] **Step 1: Failing tests**
  - Projects section: project link `href` = `projects/a/` and has no router handling (plain anchor).
  - Project page: back link `href` = `./#projects`; header nav hrefs `./#about`, `./#projects`, `./#skills`.
  - `build-env`: `API_URL=https://x.onrender.com/` → `apiUrl` `https://x.onrender.com`.
  - `verify-prerender`: with `CF_PAGES=1` and no/localhost `SITE_URL` → problem "SITE_URL must be the public URL".
  - `copy-root`: writes `_redirects` (`/es/* /es/index.csr.html 200`, `/en/* /en/index.csr.html 200`) and `_headers` at the output root.
  - `fetchWithRetry`: retries on network error/5xx, succeeds on a later attempt, throws after the last.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement.** `_headers` for `/*`: `Strict-Transport-Security: max-age=31536000; includeSubDomains`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`, `Permissions-Policy: camera=(), microphone=(), geolocation=()`, `X-Frame-Options: DENY`; long cache for hashed assets (`/*.js`, `/*.css`, `/*/media/*` → `public, max-age=31536000, immutable`). `angular.json` build `"security": { "autoCsp": true }` (hash-based CSP for Angular's inline scripts).
- [ ] **Step 4: Run** all suites + `npm run build` → pass; built HTML contains a CSP `<meta http-equiv="Content-Security-Policy"`; Docker/nginx still serves (8081 check).
- [ ] **Step 5: Commit** `feat(frontend): static-host navigation, security headers and Pages fallbacks`

### Task 3: CI and repository security

**Files:** `.github/workflows/ci.yml`, `.github/dependabot.yml`, `README.md`

- [ ] **Step 1:** `ci.yml` on push/PR to `main`: job `backend` (Temurin 17, `./mvnw -B test`; Testcontainers uses the runner's Docker), job `frontend` (Node 22, `npm ci`, `npm run test:scripts`, `npx ng test --watch=false`, `PRERENDER=false npm run build`). Minimal `permissions: contents: read`; pinned action major versions.
- [ ] **Step 2:** `dependabot.yml`: weekly for `maven` (/backend), `npm` (/frontend), `docker` (/backend, /frontend), `github-actions` (/), grouped minor/patch.
- [ ] **Step 3:** Push the branch; CI must be green on GitHub (check via the public API: `GET /repos/ElianHidalgo/Portfolio/actions/runs`).
- [ ] **Step 4:** README badges + "Deployment" section. **User (GitHub UI):** enable secret scanning + push protection, Dependabot alerts, and a branch rule on `main` requiring the CI checks.
- [ ] **Step 5: Commit** `ci: run all test suites on GitHub Actions; add Dependabot`

### Task 4: Deploy (with the user, in their dashboards)

- [ ] **Render:** New → Web Service → repo `Portfolio`, **Root Directory** `backend`, **Runtime** Docker, **Region** Virginia, **Instance** Free, **Health Check Path** `/api/health`, **Auto-Deploy** on `main`. Environment: `DB_URL=jdbc:postgresql://<neon-direct-host>/<db>?sslmode=require`, `DB_USERNAME`, `DB_PASSWORD` (typed by the user from Neon's connection details). Verify `https://<service>.onrender.com/api/profile` returns the profile (first call wakes it).
- [ ] **Cloudflare Pages:** Create → Connect to Git → `Portfolio`; **Root directory** `frontend`; **Build command** `npm run build`; **Output** `dist/frontend/browser`; env `API_URL=https://<service>.onrender.com`, `SITE_URL=https://<project>.pages.dev`, `NODE_VERSION=22.23.5`. First deploy must log `Prerender verified.`
- [ ] Record both public URLs in the README (not secrets).

### Task 5: Verify in production and review

- [ ] playwright-core/Edge against `https://<project>.pages.dev`: JS off content; 0 `/api` requests on first load **and** after clicking a project and the header links; root redirect by language; `/es/projects/nope` → not-found; response headers include HSTS, nosniff, CSP meta present; no console errors.
- [ ] `curl -I` checks of headers and `_redirects` behaviour (no 308 loop on `index.csr.html`; if Pages rewrites `.html`, switch rules to `/es/index.csr` and re-verify).
- [ ] Final whole-branch review by a fresh reviewer; fix Critical/Important with TDD; merge with the user.

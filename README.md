# Portfolio

[![CI](https://github.com/ElianHidalgo/Portfolio/actions/workflows/ci.yml/badge.svg)](https://github.com/ElianHidalgo/Portfolio/actions/workflows/ci.yml)

Personal portfolio built as a full-stack showcase: a **Spring Boot** API designed as a modular monolith and an **Angular** front end, published in English and Spanish. Content (profile, skills, projects) will be managed from a private admin panel.

**Live:** https://victor-hidalgo.victorelianhe.workers.dev · API: https://portfolio-api-nthv.onrender.com/api/profile

> Status: **Part 5 — first deployment.** See the [design spec](docs/superpowers/specs/2026-11-14-portafolio-design.md) for the full roadmap.

## Architecture

```
portfolio/
  backend/             Spring Boot 4 API (Java 17, Maven), Spring Modulith, Flyway, PostgreSQL
  frontend/            Angular 22 app, built once per locale (/en/ and /es/)
  docker-compose.yml   db (PostgreSQL) + backend + frontend (nginx)
  docs/                design spec and implementation plans
```

When running with Docker Compose, **nginx is the single entry point** at `http://localhost:8080`:

| Path | Served by |
|---|---|
| `/` | Redirects to `/es/` (default language) |
| `/es/`, `/en/` | Angular app for each language |
| `/api/*` | Proxied to the Spring Boot backend |

## API

| Endpoint | Description |
|---|---|
| `GET /api/health` | Liveness check → `{"status":"UP", ...}`; also used to wake the backend up |
| `GET /api/profile` | Public profile and skills; `404` problem detail if no profile exists |
| `GET /api/projects` | Published projects in display order (summary fields) |
| `GET /api/projects/{slug}` | One published project with its description; `404` problem detail if missing or unpublished |
| `POST /api/auth/login` | `{username, password}` → `200 {token, expiresAt}` (JWT, 2 h); `401` for any wrong credentials (same answer for unknown users); `429` after 5 attempts per client in 15 min |
| `GET /api/admin/session` | Requires `Authorization: Bearer <token>`; `{username, expiresAt}`. Every `/api/admin/**` endpoint answers `401` problem JSON without a valid token |
| `GET /api/admin/messages?page&size` | Admin inbox, newest first: `{items, total, unread}` (size 1–50) |
| `PATCH /api/admin/messages/{id}/read` · `DELETE /api/admin/messages/{id}` | Mark a message read/unread (`{"read": true}`) · delete it; `404` if it does not exist |
| `POST /api/admin/publish` · `GET /api/admin/publish/status` | "Publicar": rebuilds the public site through the Cloudflare deploy hook → `202 {configured, lastRequestedAt}`; `429` within a minute of the last one, `503` when no hook is configured, `502` when Cloudflare fails |
| `POST /api/contact` | Stores a contact message `{name, email, message, locale, website}` → `202`; `400` if invalid, `429` past 5 messages per client per hour or 50 stored per 24 h; `website` is a honeypot (filled → `202`, nothing stored) |

Translatable texts are returned in both languages as `{"es": "...", "en": "..."}`; the front end picks the one for the page's language. Errors use the `application/problem+json` format (RFC 9457).

The backend is a modular monolith: each feature (`profile`, …) is a top-level package with `web`, `application` and `domain` layers, and `ModularityTests` fails the build if a module reaches into another module's internals.

## Prerequisites

- Java 17 and Maven 3.9 (or use the bundled `./mvnw`)
- Node.js `^22.22.3` or `^24.15.0`
- Docker, **running** — required by Docker Compose and by the backend tests (Testcontainers)

## Run everything

```bash
docker compose up --build
```

Open `http://localhost:8080`. No `.env` file is needed; defaults are used.

## Develop with hot reload

```bash
docker compose up db                 # PostgreSQL only
cd backend && ./mvnw spring-boot:run # API on http://localhost:8080 (needs JWT_SECRET, see below)
cd frontend && npm start             # Spanish build on http://localhost:4200, /api proxied to :8080
```

`npm start` serves **only the Spanish build** — Angular's dev server handles one locale at a time, so `/en/` (and the "English" link) shows Spanish there. To check both languages, run the full stack (`docker compose up --build`, or `APP_PORT=8081` if 8080 is busy) and open `/es/` and `/en/`.

Run outside Docker, the backend needs `JWT_SECRET` (and, to sign in at `/es/admin/`, `ADMIN_USERNAME`/`ADMIN_PASSWORD`) in its environment — for example the development values from `.env.example`.

If you changed `DB_PORT`, the backend does not read `.env`: point it at that port before starting it.

```bash
export DB_URL=jdbc:postgresql://localhost:5433/portfolio       # bash
$env:DB_URL = "jdbc:postgresql://localhost:5433/portfolio"     # PowerShell
```

## Configuration

Copy `.env.example` to `.env` to override any default.

| Variable | Default | Purpose |
|---|---|---|
| `DB_NAME` | `portfolio` | PostgreSQL database name |
| `DB_USERNAME` | `portfolio` | PostgreSQL user (also used by the backend) |
| `DB_PASSWORD` | `portfolio` | PostgreSQL password (also used by the backend) |
| `DB_PORT` | `5432` | Host port for PostgreSQL — change it if a local PostgreSQL already uses 5432 |
| `APP_PORT` | `8080` | Host port for the app (nginx) |

The backend reads `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` and `PORT` (default `8080`), plus `JWT_SECRET` (required: base64, at least 32 bytes — the app refuses to start without it), `ADMIN_USERNAME` and `ADMIN_PASSWORD` (the single admin, kept in line with these values at every startup; changing the password in Render and redeploying rotates it), `CORS_ALLOWED_ORIGINS` (exact site origins, comma-separated; default `http://localhost:4200`), `CONTACT_PER_CLIENT_PER_HOUR` (default `5`) and `CONTACT_DAILY_CAP` (default `50`).

## Run tests

```bash
cd backend && ./mvnw test               # integration tests with Testcontainers + module boundary check
cd frontend && npx ng test --watch=false
cd frontend && npm run test:scripts     # build scripts (language picker, prerender guard)
```

## Production build (prerendered)

`npm run build` (in `frontend/`) generates the public site as static HTML, so visitors never wait for the API and the backend is not on the request path:

1. Checks that every UI text has a Spanish translation.
2. Builds both languages and **prerenders** `/es/`, `/en/` and every published project (`/es/projects/<slug>/`, `/en/projects/<slug>/`), reading the API at build time. The browser reuses that data (hydration + transfer cache) instead of fetching it again.
3. Writes the root page `/`, which sends visitors to `/es/` or `/en/` from their browser language (Spanish by default).
4. **Fails the build** if a language is missing, no project page was generated, or any page was captured in its loading/error state.

| Variable | Default | Purpose |
|---|---|---|
| `API_URL` | `http://localhost:8080` | API read while prerendering (the backend must be running) |
| `SITE_URL` | `http://localhost:8080` | Public origin for canonical, `hreflang` and Open Graph links |
| `PRERENDER` | `true` | `false` builds a client-only app |

The Docker image builds with `PRERENDER=false`: no API exists while `docker compose` builds images, and this keeps `docker compose up` working from a clean clone. Pages that were not prerendered (e.g. a new project before the next build) are served by the client-only shell (`index.csr.html`).

## Deployment (zero cost)

| Piece | Service (free tier, no card) | Notes |
|---|---|---|
| Public site | Cloudflare Workers static assets (`frontend/wrangler.jsonc`) | Static prerendered HTML only (no Worker script); build `npm run build`, deploy `npx wrangler deploy`, root `frontend`; build env `API_URL`, `SITE_URL`, `NODE_VERSION`; unknown paths get the nearest `404.html` (each language's client shell) with a real 404 |
| API | Render web service (Docker, `backend/`) | 512 MB / 0.1 CPU, sleeps after 15 min (~2 min to wake); health check `/api/health/liveness`; env `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_MIGRATION_USERNAME`, `DB_MIGRATION_PASSWORD`, `CORS_ALLOWED_ORIGINS` (the site's exact origin), `JWT_SECRET`, `ADMIN_USERNAME`, `ADMIN_PASSWORD`, `CLOUDFLARE_DEPLOY_HOOK_URL` |
| Database | Neon PostgreSQL (AWS us-east-1) | Direct (non-pooler) host, `sslmode=require` |

Visitors only load static files: public links point at prerendered pages and the first load reuses build-time data, so nobody waits for the sleeping API. The Cloudflare build wakes it (retrying for ~3 min). The only browser calls go to the contact form: a `no-cors` ping to `/api/health/liveness` when the section comes near the viewport wakes the API before the visitor presses Send, and the CSP `connect-src` allows exactly the `API_URL` origin.

Admin: the panel lives at `/es/admin/` (Spanish only, rendered in the browser, never indexed). Generate `JWT_SECRET` in PowerShell with `$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)`. **Set `JWT_SECRET`, `ADMIN_USERNAME` and `ADMIN_PASSWORD` in Render before deploying code that needs them**: a missing or short `JWT_SECRET` stops the new deploy, and Render keeps serving the previous one. Likewise `CORS_ALLOWED_ORIGINS` must exist before the site calls the API. Locally, `docker compose` provides development-only values (see `.env.example`).

Publishing: create the hook in the Cloudflare dashboard (Worker `victor-hidalgo` → Settings → Builds → Deploy Hooks → name it, branch `main`) and set its URL as `CLOUDFLARE_DEPLOY_HOOK_URL` in Render. The URL is a secret: anyone who has it can trigger builds, and the API never logs or returns it. Without it, "Publicar" answers 503 and the rest of the panel works.

Database roles: each module owns a schema (`profile`, `projects`, `contact`, `auth`, `publishing`). Flyway migrates as the owner (`DB_MIGRATION_USERNAME`/`DB_MIGRATION_PASSWORD`); the app connects as `portfolio_app`, which can read and write data but not create, alter or drop anything. Create that role **with SQL** as the owner — `CREATE ROLE portfolio_app WITH LOGIN PASSWORD '…'; GRANT CONNECT ON DATABASE neondb TO portfolio_app;` — never in the Neon console, whose roles join `neon_superuser`. The API also rate-limits each client (60 requests/minute, keyed on Cloudflare's `CF-Connecting-IP`), refuses POST bodies over 16 KiB, and allows CORS only from `CORS_ALLOWED_ORIGINS`.

Security: `_headers` sets a CSP whose script allow-list is the SHA-256 of each inline script found in the build (no `'unsafe-inline'` for scripts), plus HSTS, `nosniff`, `X-Frame-Options: DENY`, a referrer policy and a permissions policy. Secrets live only in the Render dashboard. CI runs every test suite on each push; Dependabot proposes weekly updates.

## Translations

UI texts are written in English in the templates and marked with `i18n`. After adding or changing one:

```bash
cd frontend
npx ng extract-i18n --output-path src/locale
```

Then copy the new entries into `src/locale/messages.es.xlf` with a Spanish `<target>` and delete the generated `messages.xlf`.

`npm run build` refuses to build when a Spanish translation is missing: `scripts/check-translations.mjs` rejects units without a `<target>`, and Angular's `i18nMissingTranslation: "error"` rejects texts that have no unit at all.

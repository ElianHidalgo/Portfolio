# Portfolio

Personal portfolio built as a full-stack showcase: a **Spring Boot** API designed as a modular monolith and an **Angular** front end, published in English and Spanish. Content (profile, skills, projects) will be managed from a private admin panel.

> Status: **Part 3 — projects module.** See the [design spec](docs/superpowers/specs/2026-11-14-portafolio-design.md) for the full roadmap.

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
cd backend && ./mvnw spring-boot:run # API on http://localhost:8080
cd frontend && npm start             # Spanish build on http://localhost:4200, /api proxied to :8080
```

`npm start` serves **only the Spanish build** — Angular's dev server handles one locale at a time, so `/en/` (and the "English" link) shows Spanish there. To check both languages, run the full stack (`docker compose up --build`, or `APP_PORT=8081` if 8080 is busy) and open `/es/` and `/en/`.

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

The backend reads `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` and `PORT` (default `8080`).

## Run tests

```bash
cd backend && ./mvnw test               # integration tests with Testcontainers + module boundary check
cd frontend && npx ng test --watch=false
```

## Translations

UI texts are written in English in the templates and marked with `i18n`. After adding or changing one:

```bash
cd frontend
npx ng extract-i18n --output-path src/locale
```

Then copy the new entries into `src/locale/messages.es.xlf` with a Spanish `<target>` and delete the generated `messages.xlf`.

`npm run build` refuses to build when a Spanish translation is missing: `scripts/check-translations.mjs` rejects units without a `<target>`, and Angular's `i18nMissingTranslation: "error"` rejects texts that have no unit at all.

# Part 4 — Prerendered Public Site (SSG) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The production build writes ready-made HTML for every public page in both languages (`/es/`, `/en/`, `/es/projects/<slug>/`, `/en/projects/<slug>/`) with real content, SEO and social-sharing tags, plus a root page that sends visitors to their language — and the build fails instead of publishing a broken page.

**Architecture:** Angular's static output mode (`outputMode: "static"`, `@angular/ssr`) prerenders routes at build time, reading the API through a server-only interceptor that prefixes `API_URL`. Hydration with the HTTP transfer cache reuses the build-time data in the browser (no second request). A `PRERENDER=false` switch keeps a client-only build for local Docker, so `docker compose up` still works from a clean clone. A post-build script verifies the generated HTML.

**Tech Stack:** Angular 22.2.6 + `@angular/ssr` 22.2.7 / `@angular/platform-server` 22.2.6 (static output, `RenderMode.Prerender`, `getPrerenderParams`, `provideClientHydration`, `withEventReplay`), `Meta`/`Title`/`DOCUMENT` APIs, Node 22+ build scripts, nginx (local Docker only), playwright-core (verification only, outside the repo).

**Spec:** `docs/superpowers/specs/2026-11-14-portafolio-design.md` — 4.3 approach A ("página pública pre-generada"), 5.1 diagram, 5.7 (prerender, `/` redirect by browser language, `lang`/`hreflang`), 5.8 (Cloudflare builds by reading the public API).

## Global Constraints

- Code, identifiers, comments, commits and docs in **English**; new UI/SEO copy bilingual with Spanish targets; `npm run build` keeps the translation check.
- Build-time configuration only through environment variables: `API_URL` (default `http://localhost:8080`), `SITE_URL` (default `http://localhost:8080`), `PRERENDER` (default `true`). None is secret; nothing secret enters the bundle.
- Public pages never call the API at runtime on first load (data comes from the transfer cache); the admin area (part 8) will be client-rendered.
- Default language Spanish; root `/` picks `es` or `en` from `navigator.languages`, falling back to `es`, and works without JavaScript via a visible link.
- `docker compose up` from a clean clone must keep working without the backend running at image build time.
- SOLID/simplicity: one service owns page metadata (`SeoService`); one interceptor owns server-side URL rewriting; no interfaces with a single implementation.
- Work on branch `feat/part-4-prerender`; Conventional Commits ending with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **API down or empty during the build** — the build must fail with a clear message, never publish a page stuck on "Loading…" or showing the error state. Pinned in Task 4 Step 1.
2. **JavaScript disabled or slow** (crawlers, LinkedIn link previews, slow phones) — name, about, projects and skills are in the HTML itself. Pinned in Task 5 Step 2.
3. **Hydration mismatch** (server HTML differs from client render, e.g. footer year, fragment scroll) — no `NG05xx` errors in the console and no content flash. Pinned in Task 5 Step 2.
4. **Visitor with an English browser at `/`** — lands on `/en/`; unknown languages land on `/es/`; with JS off they see links to both. Pinned in Task 3 Step 1 and Task 5 Step 2.
5. **A project URL that was not prerendered** (new slug before rebuild, typo) — served by the client-only fallback page, which shows the not-found state, not the home page markup. Pinned in Task 3 Step 3 and Task 5 Step 2.

---

### Task 1: Static output, server-side API URL and hydration

**Files:**
- Modify: `frontend/package.json` (add `@angular/ssr@22.2.7`, `@angular/platform-server@22.2.6`, exact)
- Modify: `frontend/angular.json` — build options: `"outputMode": "static"`, `"server": "src/main.server.ts"`, no `ssr.entry` (static output has no runtime server); keep `i18nMissingTranslation: "error"`
- Create: `frontend/src/main.server.ts`, `frontend/src/app/app.config.server.ts`, `frontend/src/app/app.routes.server.ts`
- Create: `frontend/src/app/core/http/server-api-url.interceptor.ts` (+ spec)
- Modify: `frontend/src/app/app.config.ts` — `provideClientHydration(withEventReplay())` (transfer cache is on by default)
- Delete: any `server.ts` / Express files the schematic generates (static output has no runtime server)

**Interfaces:**
- Produces: `serverApiUrlInterceptor: HttpInterceptorFn` — rewrites requests whose URL starts with `/api/` to `${apiUrl}${url}`; leaves other URLs untouched; `apiUrl` comes from token `API_BASE_URL` (`InjectionToken<string>`), provided in `app.config.server.ts` from `process.env['API_URL'] ?? 'http://localhost:8080'`.
- Produces: `serverRoutes: ServerRoute[]` — when `process.env['PRERENDER'] === 'false'`: `[{ path: '**', renderMode: RenderMode.Client }]`; otherwise `''` → Prerender, `'projects/:slug'` → Prerender with `getPrerenderParams` returning `{ slug }` for every item of `GET ${API_URL}/api/projects` (fetch failure or non-200 throws with message `Prerender: cannot reach ${url}`), `'**'` → Client.

- [ ] **Step 1: Write the failing test** (`server-api-url.interceptor.spec.ts`, `HttpClient` + `provideHttpClient(withInterceptors([serverApiUrlInterceptor]))`, `provideHttpClientTesting()`, `{ provide: API_BASE_URL, useValue: 'http://api.test' }`)
  - `prefixes API calls with the build-time API URL` — `GET /api/profile` → `expectOne('http://api.test/api/profile')`.
  - `leaves other URLs alone` — `GET /assets/x.json` → `expectOne('/assets/x.json')`.
- [ ] **Step 2: Run** `cd frontend; npx ng test --watch=false` → FAIL (module not found).
- [ ] **Step 3: Install and wire.** `npx ng add @angular/ssr@22.2.7 --skip-confirmation`, then reduce to static output as listed above; implement the interceptor, token, server config (`mergeApplicationConfig(appConfig, { providers: [provideServerRendering(withRoutes(serverRoutes)), provideHttpClient(withFetch(), withInterceptors([serverApiUrlInterceptor])), { provide: API_BASE_URL, useValue: … }] })`).
- [ ] **Step 4: Run tests** → all pass. **Build check** with the dev backend on :8080: `npm run build` → Expected: success, files `dist/frontend/browser/es/index.html`, `dist/frontend/browser/es/projects/gamevault/index.html` (and `en/…`), plus `index.csr.html` per locale. `Select-String 'Victor Hidalgo' dist/frontend/browser/es/index.html` → match.
- [ ] **Step 5: Commit** `feat(frontend): prerender public pages at build time reading the API`

---

### Task 2: SEO and social metadata

**Files:**
- Create: `frontend/src/app/core/seo/seo.service.ts` (+ spec), `frontend/src/app/core/seo/site-origin.ts`
- Modify: `home-page.ts` (after profile loads), `project-page.ts` (replace its `Title` effect), `app.config.ts`, `app.config.server.ts`

**Interfaces:**
- Produces: token `SITE_ORIGIN: InjectionToken<string>` — browser: `inject(DOCUMENT).location.origin`; server: `process.env['SITE_URL'] ?? 'http://localhost:8080'` (no trailing slash).
- Produces: `SeoService.setPage(page: { title: string; description: string; path: string; type?: 'website' | 'article' }): void` — `path` is locale-free (e.g. `/projects/gamevault`). Sets: document title; `<meta name="description">`; `og:title`, `og:description`, `og:type`, `og:url`, `og:locale` (`es_EC` / `en_US`), `og:site_name`; `twitter:card=summary`; `<link rel="canonical">` = `${origin}/${locale}${path}`; `<link rel="alternate" hreflang="es|en|x-default">` (x-default → `/es`). Re-calling replaces tags/links (no duplicates).

- [ ] **Step 1: Write the failing tests** (`seo.service.spec.ts`, `LOCALE_ID 'es'`, `SITE_ORIGIN 'https://example.dev'`)
  - `sets title, description and Open Graph tags` — after `setPage({ title: 'GameVault | Portafolio', description: 'App…', path: '/projects/gamevault', type: 'article' })`: `document.title`, `meta[name=description]`, `meta[property="og:url"]` = `https://example.dev/es/projects/gamevault`, `og:locale` = `es_EC`, `og:type` = `article`.
  - `writes canonical and hreflang alternates` — canonical href `https://example.dev/es/projects/gamevault`; alternates `es` → `/es/projects/gamevault`, `en` → `/en/projects/gamevault`, `x-default` → `/es/projects/gamevault`.
  - `replaces previous tags instead of duplicating them` — two calls → exactly one `link[rel=canonical]` and one `meta[name=description]`.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement** with `Meta.updateTag` and `DOCUMENT` for `<link>` elements (query by `rel`/`hreflang`, create if missing). Home: title `Victor Hidalgo | Portafolio` (name from profile + `@@pageTitle`), description = localized headline, path `/`. Project page: title as today, description = localized summary, path `/projects/<slug>`, type `article`.
- [ ] **Step 4: Run tests** → pass; `npm run build` → `dist/frontend/browser/en/projects/gamevault/index.html` contains `hreflang="es"` and `og:title`.
- [ ] **Step 5: Commit** `feat(frontend): add canonical, hreflang and Open Graph metadata`

---

### Task 3: Root language page and client fallback

**Files:**
- Create: `frontend/root/index.html` — minimal page (inline styles using the same palette): inline script picks the locale and calls `location.replace('/es/')` or `('/en/')`; `<noscript>`-independent visible links "Español" / "English"; `<link rel="alternate" hreflang>` for both
- Create: `frontend/scripts/copy-root.mjs` — copies `root/index.html` to `dist/frontend/browser/index.html`
- Modify: `frontend/package.json` `build` script → `node scripts/check-translations.mjs && ng build && node scripts/copy-root.mjs && node scripts/verify-prerender.mjs` (verify script arrives in Task 4; until then omit it)
- Modify: `frontend/nginx.conf` — `location = /` serves `/index.html` (remove the 302); locale fallbacks become `try_files $uri $uri/index.html /es/index.csr.html;` (and `/en/index.csr.html`)

**Interfaces:**
- Produces: locale choice rule — first entry of `navigator.languages` (or `navigator.language`) whose primary subtag is `es` or `en`; none → `es`.

- [ ] **Step 1: Write the failing test** — `frontend/scripts/pick-locale.mjs` exports `pickLocale(languages: string[]): 'es' | 'en'`. Single source of truth: `copy-root.mjs` injects that function's source into the `<!-- pick-locale -->` placeholder of `root/index.html`, so the tested code is the shipped code. Test with Node's built-in runner, `frontend/scripts/pick-locale.test.mjs`:
  - `['en-US','es']` → `'en'`; `['es-EC']` → `'es'`; `['fr-FR','de']` → `'es'`; `['fr','en-GB']` → `'en'`; `[]` → `'es'`.
- [ ] **Step 2: Run** `node --test scripts/` → FAIL (module missing).
- [ ] **Step 3: Implement** `pick-locale.mjs`, `root/index.html`, `copy-root.mjs`, nginx changes. Add npm script `"test:scripts": "node --test scripts/"`.
- [ ] **Step 4: Run** `node --test scripts/` → 5 pass; `npm run build` → `dist/frontend/browser/index.html` exists and contains `pickLocale`.
- [ ] **Step 5: Commit** `feat(frontend): root page that sends visitors to their language`

---

### Task 4: Build guard and Docker client-only build

**Files:**
- Create: `frontend/scripts/verify-prerender.mjs` (+ `verify-prerender.test.mjs`)
- Modify: `frontend/package.json` build script (append the verify step), `frontend/Dockerfile` (`ARG PRERENDER=false`, `ENV PRERENDER=$PRERENDER`), `README.md`

**Interfaces:**
- Produces: `verifyPrerender(distDir: string): string[]` (list of problems; CLI exits 1 and prints them). Skipped (prints `Prerender disabled — skipping verification`) when `PRERENDER=false`. Rules, for each locale `es`/`en`: `index.html` exists, contains `id="hero"` and `id="projects"`, and contains no `class="status"`; every `projects/*/index.html` contains `<h1` and no `class="status"`; at least one project page exists.

- [ ] **Step 1: Write the failing tests** (`node --test`, temp dirs built in the test)
  - `passes a complete prerender` — fixture with both locales, hero/projects markers and one project page → `[]`.
  - `reports a page left in the loading or error state` — `es/index.html` containing `class="status"` → problem mentioning `es/index.html`.
  - `reports a missing locale or no project pages` → two problems.
- [ ] **Step 2: Run** `node --test scripts/` → FAIL.
- [ ] **Step 3: Implement**; wire into `npm run build`; Dockerfile build uses `PRERENDER=false` (local image has no API at build time — documented trade-off: local Docker serves the client-rendered app; production prerenders in part 5).
- [ ] **Step 4: Run** `node --test scripts/` → pass. With backend **stopped**: `npm run build` → Expected FAIL with `Prerender: cannot reach http://localhost:8080/api/projects`. With backend up → success with `Prerender verified`. `docker compose build frontend` → success without backend.
- [ ] **Step 5: README** — "Build" section: env vars `API_URL`, `SITE_URL`, `PRERENDER`, what the guard checks, why Docker builds client-only.
- [ ] **Step 6: Commit** `build(frontend): fail the build on incomplete prerender; client-only Docker build`

---

### Task 5: End-to-end verification and review

- [ ] **Step 1:** Serve a real prerendered build the way a static host would: the frontend Dockerfile also accepts `ARG API_URL` (default empty); build it with `docker compose build --build-arg PRERENDER=true --build-arg API_URL=http://host.docker.internal:8080 frontend` while the dev backend runs, then `APP_PORT=8081 docker compose up -d frontend`.
- [ ] **Step 2:** playwright-core on Edge (tooling in git-ignored `.superpowers/tools`):
  - JS **disabled**: `/es/` shows the name, about paragraphs, 3 project titles and skills; `/en/projects/gamevault/` shows the English title and description.
  - JS enabled: no console messages containing `NG05`; `/es/` makes **no** request to `/api/profile` or `/api/projects` on first load (transfer cache).
  - Root `/` with `locale: 'en-US'` → `/en/`; with `'fr-FR'` → `/es/`; JS disabled → both links visible.
  - `/es/projects/does-not-exist` → not-found state (client fallback), not the home markup.
- [ ] **Step 3:** Vercel Web Interface Guidelines pass over the new/changed files; Lighthouse is out of scope (no extra tooling).
- [ ] **Step 4:** Final whole-branch review by a fresh reviewer; fix Critical/Important with TDD; merge decision with the user.

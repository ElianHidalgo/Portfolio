# Part 8a — Admin Inbox and "Publicar" Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** From `/es/admin/`, the admin can:
- read, mark and delete contact messages;
- press **Publicar** to rebuild the public site through a Cloudflare Workers Builds Deploy Hook, and see when it was last published.

This is the first of three slices of spec step 8: 8a inbox and publishing, 8b profile and skills editor, 8c projects editor. Each slice ships on its own.

**Architecture:**
- **Backend:**
  - `contact` gains admin endpoints under `/api/admin/messages`, protected by the existing `/api/admin/**` rule; the `auth` module is not touched.
  - A new module `publishing` (schema `publishing`, migration V11) records each publish request and calls the Deploy Hook through an application port, `SiteRebuildTrigger`. The Cloudflare adapter is the only class that knows the URL, so tests replace it.
  - `SiteCorsFilter` allows `PATCH` and `DELETE`.
- **Frontend:**
  - The admin layout gets a navigation bar: **Inicio**, **Mensajes**.
  - The home page becomes a dashboard: unread count, last publication, and the **Publicar** button.
  - A new messages page.

**Tech Stack:** Spring Boot 4.1.2 (`RestClient` with timeouts), Spring Modulith, Flyway, Testcontainers, `MockRestServiceServer`; Angular 22 (signals, `httpResource`), Vitest.

**Spec:** `docs/superpowers/specs/2026-11-14-portafolio-design.md` (§5.3 `contact` and `publishing` rows, §5.6 "publishing is tested without calling Cloudflare", §5.7 admin panel, §6 step 8)

## Global Constraints

- All code, identifiers, comments, commits and docs are in English. The admin UI copy is Spanish only, plain text without `i18n` markers.
- Zero cost:
  - The Deploy Hook belongs to Workers Builds' free plan.
  - Each publish is one build, a few minutes of the free build-minute allowance.
  - No API token is required.
- `CLOUDFLARE_DEPLOY_HOOK_URL` is a secret: it lives only in the Render env and is never logged or returned by the API.
  - When it is not set, publishing answers 503 "Publishing is not configured" and nothing is recorded.
- Never edit applied migrations (V1–V10). New schema work goes in V11. `portfolio_app` keeps data-only rights.
- Admin responses are `no-store` and problem JSON on errors (already true for every non-public path).
- Messages are shown as text only: Angular interpolation, never `innerHTML`, line breaks kept with CSS `white-space: pre-wrap`.
- The outbound call to Cloudflare uses a connect timeout of 5 s and a read timeout of 15 s, and never retries automatically.
- Branch: `feat/part-8a-inbox-publishing` from `main`.

## Review Focus

1. **Deleting or marking a message id that does not exist, or a non-numeric id:** expected 404 or 400 problem JSON, never a 500. Tests `unknownMessageIs404` and `nonNumericIdIs400` in Task 1.
2. **Cloudflare is slow, down, or answers 4xx/5xx:** expected 502 "The site could not be rebuilt" within about 20 s. The failure is not recorded as a publication, and the button lets the admin retry. Tests `cloudflareFailureIsReportedAndNotRecorded` and `cloudflareTimeoutIsReported` in Task 2.
3. **Double click on Publicar, or two tabs:** a second request within 60 s gets 429 "A publication is already in progress", and the UI disables the button while a request is in flight. Tests `secondPublishWithinAMinuteIs429` (Task 2) and `disablesPublishWhileSending` (Task 4).
4. **Hook URL not configured** (local dev, or the Render var is missing): expected 503 with a clear message, never a call to an empty URL. Test `unconfiguredHookIs503` in Task 2.
5. **A message with HTML or scripts in its text:** it must render as literal text in the inbox. Test `rendersMessageTextLiterally` in Task 4.

---

### Task 1: Admin inbox API in `contact`

**Files:**
- Modify: `backend/src/main/java/dev/elianhidalgo/portfolio/contact/domain/ContactMessage.java` — add `markRead(boolean read)`.
- Modify: `backend/src/main/java/dev/elianhidalgo/portfolio/contact/domain/ContactMessageRepository.java` — add `Page<ContactMessage> findAllByOrderByCreatedAtDescIdDesc(Pageable)` and `long countByReadFalse()`.
- Create: `backend/src/main/java/dev/elianhidalgo/portfolio/contact/application/InboxService.java`
- Create: `backend/src/main/java/dev/elianhidalgo/portfolio/contact/application/MessageNotFoundException.java` — a plain `RuntimeException`.
- Create: `backend/src/main/java/dev/elianhidalgo/portfolio/contact/web/AdminMessagesController.java`
- Modify: `backend/src/main/java/dev/elianhidalgo/portfolio/shared/web/SiteCorsFilter.java` — allowed methods `GET, POST, PATCH, DELETE`.
- Test: `backend/src/test/java/dev/elianhidalgo/portfolio/contact/AdminMessagesApiTests.java`. Insert rows as owner, and send `Authorization: Bearer` + `TestTokens.valid()`. `TestTokens` moves to a public test helper in `dev.elianhidalgo.portfolio`, so other modules' tests can use it.

**Interfaces:**
- Produces:
  - `GET /api/admin/messages?page=0&size=20` (size clamped to 1–50) → `{"items":[{"id","name","email","message","locale","createdAt","read"}],"total":N,"unread":U}`, newest first.
  - `PATCH /api/admin/messages/{id}/read` with body `{"read": true|false}` → `204`.
  - `DELETE /api/admin/messages/{id}` → `204`.
  - An unknown id → 404 problem JSON with detail "Message not found". A non-numeric id → 400.
  - `InboxService`: `page(int page, int size)`, `setRead(long id, boolean read)`, `delete(long id)`, `unreadCount()`.

- [ ] **Step 1: Write the failing tests.**

```java
@Test void requiresAdminToken()              // GET without token → 401
@Test void listsNewestFirstWithCounts()      // 3 rows (2 unread) → items[0] is the newest, total 3, unread 2, message text intact
@Test void pagesAndClampsSize()               // size=500 → at most 50 items; page=1&size=2 of 3 rows → 1 item
@Test void marksReadAndUnread()               // PATCH {"read":true} → 204, unread drops; {"read":false} → back up
@Test void deletesAMessage()                  // DELETE → 204, row gone
@Test void unknownMessageIs404()              // PATCH and DELETE /999999 → 404 problem JSON "Message not found"
@Test void nonNumericIdIs400()                // DELETE /abc → 400 problem JSON
@Test void corsAllowsPatchAndDeleteFromTheSite() // preflight PATCH and DELETE from the site origin → 200
```

- [ ] **Step 2: Run the tests and confirm they fail.** Run `cd backend && ./mvnw -B -q test -Dtest=AdminMessagesApiTests`. Expected: FAIL with 404 on the new routes.
- [ ] **Step 3: Implement the repository methods, the service, the exception, the controller and the CORS methods.** The controller lives in `contact.web`, which keeps the module boundary: Modulith tests stay green.
- [ ] **Step 4: Run the full backend suite and confirm it passes.** Run `./mvnw -B -q verify`. Expected: BUILD SUCCESS.
- [ ] **Step 5: Commit** with `feat(contact): admin inbox API — list, mark read, delete`.

---

### Task 2: `publishing` module — Deploy Hook trigger and history

**Files:**
- Create: `backend/src/main/resources/db/migration/V11__create_publications.sql` — schema `publishing`, table `publishing.publication(id bigserial pk, requested_at timestamptz not null default now(), build_id varchar(64))`, data-only grants as in V8 and V9.
- Create:
  - `backend/src/main/java/dev/elianhidalgo/portfolio/publishing/domain/Publication.java`
  - `backend/src/main/java/dev/elianhidalgo/portfolio/publishing/domain/PublicationRepository.java` (`Optional<Publication> findFirstByOrderByRequestedAtDesc()`)
- Create:
  - `backend/src/main/java/dev/elianhidalgo/portfolio/publishing/application/SiteRebuildTrigger.java` — interface: `Optional<String> trigger()`, which returns Cloudflare's build id when given. It throws `RebuildFailedException`, a plain `RuntimeException`, on any failure.
  - `backend/src/main/java/dev/elianhidalgo/portfolio/publishing/application/PublishingService.java` — `publish()` returns `PublicationStatus`; `status()` returns `PublicationStatus`.
  - `backend/src/main/java/dev/elianhidalgo/portfolio/publishing/application/PublicationStatus.java` — `record (boolean configured, Instant lastRequestedAt)`.
  - `backend/src/main/java/dev/elianhidalgo/portfolio/publishing/application/PublishTooSoonException.java`
  - `backend/src/main/java/dev/elianhidalgo/portfolio/publishing/application/PublishingNotConfiguredException.java`
  - `backend/src/main/java/dev/elianhidalgo/portfolio/publishing/application/RebuildFailedException.java`
- Create: `backend/src/main/java/dev/elianhidalgo/portfolio/publishing/web/CloudflareDeployHook.java` — `implements SiteRebuildTrigger`. It uses `RestClient` (5 s connect, 15 s read), sends `POST` to `${app.publishing.deploy-hook-url}`, and reads `result.build_uuid` when present. It is only created when the property is not blank; otherwise a `NotConfigured` bean throws `PublishingNotConfiguredException`.
- Create: `backend/src/main/java/dev/elianhidalgo/portfolio/publishing/web/PublishController.java`
- Modify: `backend/src/main/resources/application.yml` — `app.publishing.deploy-hook-url: ${CLOUDFLARE_DEPLOY_HOOK_URL:}`.
- Test: `backend/src/test/java/dev/elianhidalgo/portfolio/publishing/PublishApiTests.java` (Spring + `@MockitoBean SiteRebuildTrigger`)
- Test: `backend/src/test/java/dev/elianhidalgo/portfolio/publishing/CloudflareDeployHookTests.java` (`MockRestServiceServer` bound to the adapter's `RestClient.Builder`; no network)

**Interfaces:**
- Produces:
  - `POST /api/admin/publish` → `202 {"configured":true,"lastRequestedAt":"…"}`. It returns 429 "A publication is already in progress" when the last one is less than 60 s old, 503 "Publishing is not configured", or 502 "The site could not be rebuilt".
  - `GET /api/admin/publish/status` → `200 {"configured":bool,"lastRequestedAt":"…"|null}`.

- [ ] **Step 1: Write the failing tests.**

```java
// PublishApiTests
@Test void publishRequiresAdminToken()                    // 401 without token
@Test void publishTriggersARebuildAndRecordsIt()           // trigger returns "b-1" → 202; status.lastRequestedAt set; row has build_id "b-1"
@Test void secondPublishWithinAMinuteIs429()               // two POSTs → 202 then 429; trigger called once
@Test void cloudflareFailureIsReportedAndNotRecorded()     // trigger throws RebuildFailedException → 502 problem JSON; no row
@Test void unconfiguredHookIs503()                         // trigger throws PublishingNotConfiguredException → 503; status.configured false
// CloudflareDeployHookTests
@Test void postsToTheHookAndReadsTheBuildId()              // expect POST to the URL; respond {"result":{"build_uuid":"abc"}} → Optional.of("abc")
@Test void cloudflareErrorBecomesRebuildFailed()           // respond 500 → RebuildFailedException; message has no URL
@Test void cloudflareTimeoutIsReported()                   // read timeout (simulated) → RebuildFailedException
```

- [ ] **Step 2: Run the tests and confirm they fail.** Run `./mvnw -B -q test -Dtest='PublishApiTests,CloudflareDeployHookTests'`. Expected: FAIL with missing classes or 404.
- [ ] **Step 3: Implement V11, the domain, the application and web classes, and the config.** Log only "Site rebuild requested" or "Site rebuild failed: <status>", never the URL.
- [ ] **Step 4: Run the full backend suite and confirm it passes.** Run `./mvnw -B -q verify`. Expected: BUILD SUCCESS; `ModularityTests` accept the new `publishing` module; `DatabasePrivilegesTests` list `publishing.publication`.
- [ ] **Step 5: Commit** with `feat(publishing): rebuild the site through a Cloudflare deploy hook`.

---

### Task 3: Admin frame — navigation and session-expired notice from the guard

**Files:**
- Modify: `frontend/src/app/admin/admin-layout/admin-layout.{html,scss,ts}` — navigation links `Inicio` (`/admin`) and `Mensajes` (`/admin/mensajes`) with `routerLinkActive`.
- Modify: `frontend/src/app/admin/admin.routes.ts` — add child `mensajes` → `MessagesPage` (Task 4), titled "Mensajes | Portafolio".
- Modify: `frontend/src/app/admin/auth/admin.guard.ts` (spec) — when a token exists but has expired, call `logout('expired')` before redirecting (part 7 deferred minor).
- Modify: `frontend/scripts/copy-root.mjs` (test) — also write the shell `es/admin/mensajes/index.html`, so reloading that page answers 200.

- [ ] **Step 1: Write the failing tests.**
  - `admin.guard.spec`: `marksAnExpiredSessionBeforeRedirecting` — with an expired token, `lastLogoutReason()` is `'expired'` and the token is gone.
  - `copy-root.test`: the new shell exists and carries the API origin.
- [ ] **Step 2: Run the tests and confirm they fail.** Run `cd frontend && npx ng test --watch=false && npm run test:scripts`. Expected: the new specs fail.
- [ ] **Step 3: Implement the changes.**
- [ ] **Step 4: Run the tests and confirm they pass.** Expected: all pass.
- [ ] **Step 5: Commit** with `feat(frontend): admin navigation; expired sessions explained by the guard`.

---

### Task 4: Dashboard with Publicar, and the messages page

**Files:**
- Create: `frontend/src/app/admin/api/admin-api.ts` — `AdminApi` service with:
  - `messages(page: number): Observable<MessagePage>`
  - `setRead(id: number, read: boolean): Observable<void>`
  - `deleteMessage(id: number): Observable<void>`
  - `publishStatus(): Observable<PublishStatus>`
  - `publish(): Observable<PublishStatus>`

  `publish` uses `timeout(COLD_START_TIMEOUT_MS)`. All URLs are `${apiOrigin}/api/admin/…`.
- Create: `frontend/src/app/admin/api/admin-models.ts` — `AdminMessage`, `MessagePage`, `PublishStatus`.
- Modify: `frontend/src/app/admin/home/admin-home-page.ts` (spec) — dashboard:
  - "Mensajes sin leer: N" linking to the messages page;
  - "Última publicación: {fecha local es-EC}" or "Aún no se ha publicado";
  - button **Publicar sitio**.
- Create: `frontend/src/app/admin/messages/messages-page.{ts,html,scss}` (spec) — list, newest first. Each item shows:
  - name, email as a `mailto:` link "Responder", date, language and the message text (`pre-wrap`);
  - unread items marked "Nuevo";
  - buttons "Marcar como leído/no leído" and "Eliminar". Delete asks for confirmation through `window.confirm`;
  - "Cargar más" when `items.length < total`.

Copy (Spanish only):

| Case | Text |
|---|---|
| Publish success | Publicación solicitada. El sitio se actualizará en 1–3 minutos. |
| 429 | Ya hay una publicación en curso. Espera un minuto. |
| 503 | La publicación no está configurada (falta el deploy hook). |
| 502/other | No se pudo publicar. Inténtalo de nuevo. |
| Empty inbox | No hay mensajes. |
| Delete confirm | ¿Eliminar este mensaje? No se puede deshacer. |

- [ ] **Step 1: Write the failing tests.**

```ts
// admin-home-page.spec
it('shows unread count and last publication')         // flush messages {unread:2} and status {lastRequestedAt} → "Mensajes sin leer: 2", formatted date
it('publishes and shows the result')                  // click → POST /api/admin/publish; flush 202 → success text; date updated
it('disablesPublishWhileSending')                     // button disabled until the response
it('explains 429, 503 and failures')                  // each status → its text from the table
// messages-page.spec
it('lists messages newest first with reply links')    // mailto:ana@example.com
it('rendersMessageTextLiterally')                     // message "<img src=x onerror=alert(1)>" appears as text; no <img> in DOM
it('marks a message read and unread')                 // PATCH body {read:true}; "Nuevo" badge disappears
it('deletes after confirmation only')                 // confirm false → no request; true → DELETE, item removed
it('loads more pages')                                 // total 3, page of 2 → "Cargar más" → page=1 request
```

- [ ] **Step 2: Run the tests and confirm they fail.** Run `npx ng test --watch=false`. Expected: the new specs fail.
- [ ] **Step 3: Implement the service, the models, the dashboard and the messages page.** Reuse `.admin-form` and `.button` styles, and design tokens.
- [ ] **Step 4: Run every frontend check and confirm it passes.**
  - Run `npx ng test --watch=false && npm run test:scripts && node scripts/check-translations.mjs`. Expected: all pass.
  - Run `npm run build` against the local backend. Expected: `Prerender verified.`
- [ ] **Step 5: Check the result end to end, locally.** Use `serve-dist.cjs` on :4200 plus the dev backend with dev `ADMIN_*`, and the hook URL pointed at a local stub, for example `node -e` with an HTTP server that answers `{"result":{"build_uuid":"local"}}`. With Playwright:
  1. Sign in.
  2. The dashboard shows the counts.
  3. Publicar shows the success text, and a second click gives the 429 text.
  4. Mensajes lists a message sent from the public form.
  5. Mark it read, then delete it.
  6. There are 0 CSP violations.

  Take screenshots at 375 px and 1280 px.
- [ ] **Step 6: Commit** with `feat(frontend): admin dashboard with Publicar and the messages inbox`.

---

### Task 5: Docs, deploy hook and production verification

**Files:**
- Modify: `README.md` — admin API rows; env `CLOUDFLARE_DEPLOY_HOOK_URL`; how to create the hook (Cloudflare dashboard → Worker `victor-hidalgo` → Settings → Builds → Deploy Hooks → name "Publicar desde el panel", branch `main`).
- Modify: `docs/superpowers/specs/…` (rev 7) — slices 8a/8b/8c, publishing details (60 s guard, 502/503), inbox pagination.
- Modify: `.env.example` — `CLOUDFLARE_DEPLOY_HOOK_URL=` (empty locally).

- [ ] **Step 1: Run the full suites, commit, push the branch, and wait for CI to pass.**
- [ ] **Step 2 (user):**
  1. Create the Deploy Hook in Cloudflare.
  2. Add `CLOUDFLARE_DEPLOY_HOOK_URL` in Render, then Save and deploy.
- [ ] **Step 3: Merge to `main` after the user approves.**
- [ ] **Step 4: Verify in production.**
  - The admin endpoints answer 401 without a token.
  - The user, signed in, sees the inbox and presses Publicar.
  - The Cloudflare build list shows a build triggered by the deploy hook.
  - The site stays healthy: hydration and CSP checks pass.

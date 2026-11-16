# Part 6 — Contact Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Visitors can send a message from the contact section of `/es/` and `/en/`. The message is stored in the `contact` module and protected against spam, oversized input and quota exhaustion. Notification emails are out of scope (spec non-goal), and reading messages comes in part 8.

**Architecture:**
- **Backend:** a new Spring Modulith module `contact` (`domain` / `application` / `web`) owns the `contact` schema (V8). It exposes `POST /api/contact` and, for now, nothing else.
- **Rate limiting:** the per-client token-bucket code from part 5b moves into a reusable `shared` type. The global filter and the contact endpoint each use their own limit.
- **Browser writes:** the browser now calls the API cross-origin for the first time. That needs CORS on the backend, the API origin in the CSP `connect-src`, and a request-size guard.
- **API origin:** the browser reads it from a `<meta name="api-origin">` tag written at prerender, mirroring how `SITE_ORIGIN` reads the canonical link.
- **Waking Render:** a wake ping fires when the contact section nears the viewport, not on every visit.

**Tech Stack:** Spring Boot 4.1.2, Spring Modulith 2.1.2, Bean Validation, Bucket4j 8.22.0, Flyway, PostgreSQL 17 / Testcontainers; Angular 22.2 (Reactive Forms, signals, `@angular/localize`), Vitest, node:test.

**Spec:** `docs/superpowers/specs/2026-11-14-portafolio-design.md` (§5.3 `contact` row, §5.5, §5.7 "Contacto", §6 step 6)

## Global Constraints

- All code, identifiers, comments, commits and docs are in English. UI copy is bilingual, with Spanish in `frontend/src/locale/messages.es.xlf`.
- Zero cost: no new paid or card-requiring service. No email provider, no external captcha.
- Never edit an applied Flyway migration (V1–V7). Schema changes go in V8.
- The runtime role `portfolio_app` gets data-only rights (select/insert/update/delete plus sequence usage) and never DDL.
- Errors are `application/problem+json` (RFC 9457). Every response carries the existing security headers. Non-public-GET responses are `Cache-Control: no-store` (already done by `ApiHeadersFilter`).
- The client IP comes from `CF-Connecting-IP`, else the socket address. Never trust `X-Forwarded-For`.
- No visitor email or message text is written to logs.
- Prerendered pages must contain no `data-state="` attribute (`verify-prerender.mjs` fails the build). Form states use `data-form-state` instead.
- Limits, which must be identical in backend and frontend:
  - name 1–100 characters;
  - email ≤ 254 characters and a valid address;
  - message 10–2000 characters;
  - locale `es` | `en`;
  - honeypot field `website` ≤ 200 characters.
- Defaults:
  - contact limit: 5 messages per client per hour (`app.contact.per-client-per-hour`, env `CONTACT_PER_CLIENT_PER_HOUR`);
  - global cap: 50 stored messages per rolling 24 h (`app.contact.daily-cap`, env `CONTACT_DAILY_CAP`);
  - request body ≤ 16384 bytes for POST `/api/**`;
  - CORS origins from `app.cors.allowed-origins` (env `CORS_ALLOWED_ORIGINS`, default `http://localhost:4200`).
- Branch: `feat/part-6-contact` from `main` (4ba71a9).

## Review Focus

1. **NUL or control characters in a field:** PostgreSQL rejects `\u0000` in text, which would surface as a 500. Expected: 400 problem JSON. Test `rejectsControlCharacters` in Task 4.
2. **Malformed JSON or the wrong content type:** expected 400 or 415 problem JSON, never Tomcat HTML or a 500. Test `rejectsMalformedJson` in Task 4.
3. **Render asleep when the visitor presses Send:** the first request can take 30–120 s. Expected: the "waking up" hint after 5 s, a client timeout at 90 s that ends in the retryable error state, and the form content kept. Test `showsWakingHintAndKeepsInputOnTimeout` in Task 6.
4. **Honeypot filled by a bot:** expected the same 202 as a real send, with nothing stored. Test `honeypotIsAcceptedButNotStored` in Task 4.
5. **A preflight from a disallowed origin, or a request without `Origin` (curl):** a disallowed preflight gets 403 with no `Access-Control-Allow-Origin`. A request without `Origin` is unaffected (CORS is a browser control; the rate limits protect the endpoint). Tests `rejectsPreflightFromUnknownOrigin` and `allowsPreflightFromSiteOrigin` in Task 2.

---

### Task 1: Reusable per-client rate limiter in `shared`

Moves the bucket map, idle eviction and client-key logic out of `RateLimitFilter`, so the contact module can reuse them. They go in the `shared` **base package**, because Modulith only exposes a module's base package; `shared.web` is internal. This is a pure refactor plus a unit test, and the existing `RateLimitTests` must stay green.

**Files:**
- Create: `backend/src/main/java/dev/elianhidalgo/portfolio/shared/ClientRateLimiter.java`
- Create: `backend/src/main/java/dev/elianhidalgo/portfolio/shared/ClientAddress.java`
- Modify: `backend/src/main/java/dev/elianhidalgo/portfolio/shared/web/RateLimitFilter.java`
- Test: `backend/src/test/java/dev/elianhidalgo/portfolio/shared/ClientRateLimiterTests.java` (plain JUnit, no Spring)

**Interfaces:**
- Produces:
  - `public final class ClientAddress { public static String of(HttpServletRequest request) }` — the non-blank `CF-Connecting-IP`, else `request.getRemoteAddr()`.
  - `public final class ClientRateLimiter { public ClientRateLimiter(int capacity, Duration period); public OptionalLong tryConsume(String clientKey) }` — returns empty when the request is allowed. When it is refused, returns the `Retry-After` seconds, ≥ 1, computed as in the current filter. Uses greedy refill of `capacity` per `period`, plus the existing sweep: every 1000 calls, forget clients idle for more than 10 minutes.

- [ ] **Step 1: Write the failing tests**

```java
@Test void allowsUpToCapacityThenRefusesWithRetryAfter() {
    var limiter = new ClientRateLimiter(2, Duration.ofHours(1));
    assertThat(limiter.tryConsume("a")).isEmpty();
    assertThat(limiter.tryConsume("a")).isEmpty();
    assertThat(limiter.tryConsume("a")).hasValueSatisfying(s -> assertThat(s).isBetween(1L, 3600L));
}
@Test void clientsHaveSeparateBuckets() {
    var limiter = new ClientRateLimiter(1, Duration.ofHours(1));
    limiter.tryConsume("a");
    assertThat(limiter.tryConsume("b")).isEmpty();
}
@Test void clientAddressPrefersCloudflareHeaderAndIgnoresForwardedFor() {
    var request = new MockHttpServletRequest();
    request.setRemoteAddr("10.0.0.1");
    request.addHeader("X-Forwarded-For", "198.51.100.9");
    assertThat(ClientAddress.of(request)).isEqualTo("10.0.0.1");
    request.addHeader("CF-Connecting-IP", "203.0.113.7");
    assertThat(ClientAddress.of(request)).isEqualTo("203.0.113.7");
}
```

- [ ] **Step 2: Run the tests and confirm they fail.** Run `cd backend && ./mvnw -B -q test -Dtest=ClientRateLimiterTests`. Expected: compilation failure, because the classes do not exist.
- [ ] **Step 3: Implement both classes and make `RateLimitFilter` delegate to them.** The filter keeps its order, its exemptions and its 429 body. It builds `new ClientRateLimiter(requestsPerMinute, Duration.ofMinutes(1))`.
- [ ] **Step 4: Run the tests and confirm they pass.** Run `./mvnw -B -q test -Dtest='ClientRateLimiterTests,RateLimitTests,ModularityTests'`. Expected: all pass.
- [ ] **Step 5: Commit** with `refactor(backend): extract a reusable per-client rate limiter into shared`.

---

### Task 2: CORS for the site origin and a request-size guard

**Files:**
- Create: `backend/src/main/java/dev/elianhidalgo/portfolio/shared/web/CorsConfig.java`
- Create: `backend/src/main/java/dev/elianhidalgo/portfolio/shared/web/RequestSizeLimitFilter.java`
- Modify: `backend/src/main/resources/application.yml`. Add `app.cors.allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:4200}` as a comma-separated list.
- Test: `backend/src/test/java/dev/elianhidalgo/portfolio/shared/CorsAndRequestSizeTests.java`. Use MockMvc and `properties = "app.cors.allowed-origins=https://site.example"`.

**Interfaces:**
- Produces, for later tasks:
  - CORS on `/api/**`: methods `GET`, `POST`; allowed header `Content-Type`; no credentials; `maxAge` 3600.
  - POST bodies over 16384 bytes get 413, and a POST without `Content-Length` gets 411. Both are problem JSON with `Cache-Control: no-store`.

- [ ] **Step 1: Write the failing tests.** Test against a real endpoint (`/api/profile`), plus `/api/contact`, which returns 404 until Task 4. That is fine, because the filter answers first.

```java
@Test void allowsPreflightFromSiteOrigin() throws Exception {
    mockMvc.perform(options("/api/contact").header("Origin", "https://site.example")
            .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "content-type"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "https://site.example"))
        .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
}
@Test void rejectsPreflightFromUnknownOrigin() throws Exception {
    mockMvc.perform(options("/api/contact").header("Origin", "https://evil.example")
            .header("Access-Control-Request-Method", "POST"))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
}
@Test void publicGetCarriesCorsHeaderForSiteOrigin() throws Exception {
    mockMvc.perform(get("/api/profile").header("Origin", "https://site.example"))
        .andExpect(header().string("Access-Control-Allow-Origin", "https://site.example"));
}
@Test void rejectsOversizedPostBody() throws Exception {
    mockMvc.perform(post("/api/contact").contentType(APPLICATION_JSON).content("x".repeat(16_385)))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(header().string("Cache-Control", "no-store"));
}
```

- [ ] **Step 2: Run the tests and confirm they fail.** Run `./mvnw -B -q test -Dtest=CorsAndRequestSizeTests`. Expected: FAIL, because there are no CORS headers and the 404 is not a 413.
- [ ] **Step 3: Implement the two classes.**
  - `CorsConfig implements WebMvcConfigurer`, constructor `@Value("${app.cors.allowed-origins}") List<String> allowedOrigins`. Use exact origins, never patterns or `*`.
  - `RequestSizeLimitFilter extends OncePerRequestFilter`:
    - `@Order(Ordered.HIGHEST_PRECEDENCE + 2)`, so it runs after the headers and rate-limit filters;
    - only acts on POST under `/api/`;
    - reads `request.getContentLengthLong()`: −1 means 411, more than 16384 means 413;
    - writes the body with the same problem-JSON shape as `RateLimitFilter` (`detail` "Request body too large" or "Content-Length required").
- [ ] **Step 4: Run the full backend suite and confirm it passes.** Run `./mvnw -B -q verify`. Expected: BUILD SUCCESS. All 23 existing tests plus the new ones pass.
- [ ] **Step 5: Commit** with `feat(backend): CORS for the site origin and a POST body size guard`.

---

### Task 3: `contact` schema, entity and repository (V8)

**Files:**
- Create: `backend/src/main/resources/db/migration/V8__create_contact_messages.sql`
- Create: `backend/src/main/java/dev/elianhidalgo/portfolio/contact/domain/ContactMessage.java`
- Create: `backend/src/main/java/dev/elianhidalgo/portfolio/contact/domain/ContactMessageRepository.java`
- Modify: `backend/src/test/java/dev/elianhidalgo/portfolio/DatabasePrivilegesTests.java`

**Interfaces:**
- Produces:
  - Table `contact.message` with these columns:
    - `id bigserial primary key`;
    - `name varchar(100) not null`;
    - `email varchar(254) not null`;
    - `body varchar(2000) not null`;
    - `locale varchar(2) not null check (locale in ('es','en'))`;
    - `created_at timestamptz not null default now()`;
    - `read boolean not null default false`.

    It also gets an index on `created_at`.
  - Entity `ContactMessage(String name, String email, String body, String locale)` is mapped to `@Table(schema = "contact", name = "message")`. It has getters for every column, and `createdAt` is set in the constructor as `Instant.now()`. `read` defaults to false.
  - `ContactMessageRepository extends JpaRepository<ContactMessage, Long>`, with `@Query(value = "select count(*) from contact.message where created_at > now() - interval '24 hours'", nativeQuery = true) long countLast24Hours();`.

- [ ] **Step 1: Write the failing tests in `DatabasePrivilegesTests`.**
  - Extend `tablesLiveInTheirModuleSchema` to also query the `contact` schema, and expect `"contact.message"` first in the list.
  - Add this test:

```java
@Test void runtimeRoleCanStoreMessagesButNotAlterTheContactSchema() throws SQLException {
    try (Connection c = asAppRole(); Statement s = c.createStatement()) {
        c.setAutoCommit(false);
        assertThat(s.executeUpdate("insert into contact.message(name, email, body, locale) "
                + "values ('a', 'a@b.co', 'hello there', 'es')")).isEqualTo(1);
        c.rollback();
        assertThatThrownBy(() -> s.execute("create table contact.injected(id int)"))
            .extracting(e -> ((SQLException) e).getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
    }
}
```

- [ ] **Step 2: Run the tests and confirm they fail.** Run `./mvnw -B -q test -Dtest=DatabasePrivilegesTests`. Expected: FAIL, because the relation `contact.message` does not exist.
- [ ] **Step 3: Write V8, the entity and the repository.**
  - V8 creates the schema and the table, then grants usage on schema `contact` to `portfolio_app`. Grant the table and its sequence explicitly: they are created in this same migration. Also set the same `alter default privileges` as V7, for future tables.
  - V8 assumes `portfolio_app` exists (created by V7 or, in production, by SQL). Do not recreate it.
  - Do not touch V7.
- [ ] **Step 4: Run the tests and confirm they pass.** Run `./mvnw -B -q test -Dtest='DatabasePrivilegesTests,ModularityTests'`. Expected: PASS. Hibernate `ddl-auto: validate` accepts the entity.
- [ ] **Step 5: Commit** with `feat(contact): contact schema and message table with data-only grants`.

---

### Task 4: `POST /api/contact` — validation, honeypot, per-client limit, daily cap

**Files:**
- Create: `backend/src/main/java/dev/elianhidalgo/portfolio/contact/application/ContactService.java`
- Create: `backend/src/main/java/dev/elianhidalgo/portfolio/contact/application/NewContactMessage.java`
- Create: `backend/src/main/java/dev/elianhidalgo/portfolio/contact/application/InboxFullException.java`
- Create: `backend/src/main/java/dev/elianhidalgo/portfolio/contact/web/ContactController.java`
- Create: `backend/src/main/java/dev/elianhidalgo/portfolio/contact/web/ContactRequest.java`
- Modify: `backend/src/main/resources/application.yml`. Add `app.contact.per-client-per-hour: ${CONTACT_PER_CLIENT_PER_HOUR:5}` and `app.contact.daily-cap: ${CONTACT_DAILY_CAP:50}`.
- Test: `backend/src/test/java/dev/elianhidalgo/portfolio/contact/ContactApiTests.java`. Use MockMvc, an owner `JdbcTemplate` to count rows (as in `HealthEndpointTests`), and `properties = {"app.contact.per-client-per-hour=2", "app.contact.daily-cap=3"}`. Delete rows from `contact.message` in `@BeforeEach`. Use a distinct `CF-Connecting-IP` per test, so the per-client limits do not interfere.

**Interfaces:**
- Consumes:
  - `ClientAddress.of`, `ClientRateLimiter` (Task 1);
  - `ContactMessage`, `ContactMessageRepository.countLast24Hours()` (Task 3).
- Produces:
  - `record ContactRequest(@NotBlank @Size(max=100) @Pattern(regexp="[^\\p{Cntrl}]*") String name, @NotBlank @Email @Size(max=254) String email, @NotBlank @Size(min=10, max=2000) @Pattern(regexp="[^\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]*") String message, @NotNull @Pattern(regexp="es|en") String locale, @Size(max=200) String website)`. `message` allows `\t`, `\n` and `\r`; a negated character class also matches newlines, so no `(?s)` flag is needed.
  - `record NewContactMessage(String name, String email, String body, String locale)`.
  - `ContactService.submit(NewContactMessage message): void`. It throws `InboxFullException` (a plain `RuntimeException`, not a Spring web type) when `countLast24Hours() >= dailyCap`. Otherwise it saves the message with `strip()`ped name/email/body.
  - `ContactController`: `POST /api/contact`, which returns **202 Accepted** with an empty body:
    1. per-client limit, via `new ClientRateLimiter(perClientPerHour, Duration.ofHours(1))`; when refused, 429 with `Retry-After` and detail "Too many messages, try again later";
    2. honeypot: when `website` is non-blank, return 202 and store nothing;
    3. `service.submit(...)`;
    4. `@ExceptionHandler(InboxFullException.class)` returns a 429 `ProblemDetail` with detail "The inbox is full for today" and `Retry-After: 3600`.

- [ ] **Step 1: Write the failing tests.** Helper: `send(ip, json)` posts with `CF-Connecting-IP`. `VALID` is `{"name":"Ana","email":"ana@example.com","message":"Hello, I have a project.","locale":"es","website":""}`.

```java
@Test void storesAValidMessage()            // 202, row count 1, stored body equals "Hello, I have a project.", read = false
@Test void honeypotIsAcceptedButNotStored() // website "https://spam.example" → 202, row count 0
@Test void rejectsInvalidFields()           // email "nope", message "short", locale "fr" → 400 problem JSON, row count 0
@Test void rejectsControlCharacters()       // name "A\u0000na" → 400 (not 500), row count 0
@Test void rejectsMalformedJson()           // body "{" → 400 problem JSON; text/plain body → 415 problem JSON
@Test void limitsMessagesPerClient()        // 2 × 202 from one IP, the 3rd → 429 with Retry-After; another IP → 202
@Test void capsStoredMessagesPerDay()       // 3 × 202 from 3 IPs, the 4th from a 4th IP → 429 "The inbox is full for today", row count 3
@Test void responseIsNeverCached()          // 202 has Cache-Control: no-store and X-Content-Type-Options: nosniff
```

- [ ] **Step 2: Run the tests and confirm they fail.** Run `./mvnw -B -q test -Dtest=ContactApiTests`. Expected: FAIL with 404 on `/api/contact`.
- [ ] **Step 3: Implement the service, records, exception and controller.** Keep them as listed under Interfaces. Do not log request fields.
- [ ] **Step 4: Run the full suite and confirm it passes.** Run `./mvnw -B -q verify`. Expected: BUILD SUCCESS, and `ModularityTests` confirm that `contact` only uses `shared`'s base package.
- [ ] **Step 5: Commit** with `feat(contact): POST /api/contact with validation, honeypot and spam limits`.

---

### Task 5: API origin in the browser, CSP `connect-src`, build guard

**Files:**
- Create: `frontend/src/app/core/http/api-origin.ts` (spec: `api-origin.spec.ts`)
- Modify: `frontend/src/app/app.config.server.ts` — add `...provideServerApiOrigin(buildEnv.apiUrl)`.
- Modify: `frontend/scripts/copy-root.mjs` (test: `copy-root.test.mjs`) — set `connect-src 'self' <API origin>`, where the API origin is `new URL(env.API_URL || 'http://localhost:8080').origin`.
- Modify: `frontend/scripts/verify-prerender.mjs` (test: `verify-prerender.test.mjs`) — the home page also requires `id="contact"` and `name="api-origin"`.

**Interfaces:**
- Produces:
  - `export const API_ORIGIN: InjectionToken<string>`. Its root factory runs in the browser: it reads `meta[name="api-origin"]`'s `content` and returns `''` when the tag is missing. A missing tag happens in client-only shells and the local Docker build, which uses relative `/api` through nginx.
  - `export function provideServerApiOrigin(apiUrl: string): (Provider | EnvironmentProviders)[]` provides `API_ORIGIN` = `apiUrl`. It also adds a `provideAppInitializer` that calls `inject(Meta).updateTag({ name: 'api-origin', content: apiUrl })`.

- [ ] **Step 1: Write the failing tests.**
  - `api-origin.spec.ts`:
    - `readsTheApiOriginFromTheMetaTag`: add `<meta name="api-origin" content="https://api.example">` to `document.head`, then `TestBed.inject(API_ORIGIN)` returns `'https://api.example'`. Remove the tag in `afterEach`.
    - `fallsBackToRelativeUrls`: with no tag, the token returns `''`.
  - `copy-root.test.mjs`: `build({ API_URL: 'https://api.example/' })('_headers')` matches `/connect-src 'self' https:\/\/api\.example;/`.
  - `verify-prerender.test.mjs`: change `HOME` to include `<meta name="api-origin" content="x">` and `<section id="contact"></section>`. Add a test `reports a home page without the contact section or API origin`: a home page missing both reports 2 problems, both naming the file.
- [ ] **Step 2: Run the tests and confirm they fail.** Run `cd frontend && npx ng test --watch=false && npm run test:scripts`. Expected: the new specs fail.
- [ ] **Step 3: Implement the token, the server provider, the CSP origin and the two required markers.**
- [ ] **Step 4: Run the tests and confirm they pass.** Run `npx ng test --watch=false && npm run test:scripts`. Expected: all pass (53 + 2 Vitest, 14 + 2 node).
- [ ] **Step 5: Commit** with `feat(frontend): expose the API origin to the browser and allow it in the CSP`.

> The production build (`npm run build`) fails from here until Task 6 adds `id="contact"`. That is intended: Tasks 5 and 6 merge together.

---

### Task 6: Contact section — form, states, wake ping, nav link

**Files:**
- Create: `frontend/src/app/core/models/contact.ts`
- Create: `frontend/src/app/core/http/backend-wake.ts` (spec: `backend-wake.spec.ts`)
- Create: `frontend/src/app/public/home/contact-section/contact-api.ts`
- Create: `frontend/src/app/public/home/contact-section/contact-section.{ts,html,scss}` (spec: `contact-section.spec.ts`)
- Modify: `frontend/src/app/public/home/home-page.{ts,html}`. Render `<app-contact-section />` after skills, inside the `profile.hasValue()` branch.
- Modify: `frontend/src/app/core/layout/site-header/site-header.html`. Add `<li><a href="./#contact" i18n="@@navContact">Contact</a></li>`.
- Modify: `frontend/src/locale/messages.es.xlf` (every id below)

**Interfaces:**
- Consumes: `API_ORIGIN` (Task 5). The API contract is from Task 4: 202, or 400/429 problem JSON.
- Produces:
  - `interface ContactMessage { name: string; email: string; message: string; locale: 'es' | 'en'; website: string }`
  - `ContactApi.send(message: ContactMessage): Observable<void>` makes a `POST ${apiOrigin}/api/contact` with `timeout(90_000)`.
  - `BackendWake.wake(): void` is idempotent: it runs at most once per page load. It calls `fetch(`${apiOrigin}/api/health/liveness`, { mode: 'no-cors', cache: 'no-store' })` and ignores errors. In `no-cors` mode it needs no CORS for actuator, and it never touches Neon.
  - `ContactSection` has a `state` signal of type `'idle' | 'sending' | 'sent' | 'limited' | 'error'` and a `slow` signal, which turns true 5 s into `sending`.
    - The root element is `<section id="contact" [attr.data-form-state]="state()">`.
    - Validators mirror the Global Constraints limits.
    - The locale comes from `LOCALE_ID`: `startsWith('es') ? 'es' : 'en'`.
    - The wake ping fires from an `IntersectionObserver` with `rootMargin: '800px 0px'`, created in `afterNextRender`, and also on the form's first `focusin`.
    - The honeypot is a `website` input inside a wrapper hidden off-screen, not with `display:none`, so bots still see it. It has `tabindex="-1"`, `autocomplete="off"` and `aria-hidden="true"`.
    - On `sent`, reset the form. On `error` or `limited`, keep the input.

UI copy (source English, then Spanish target):

| id | en | es |
|---|---|---|
| navContact / contactTitle | Contact | Contacto |
| contactIntro | Have a project or a role in mind? Write to me. | ¿Tienes un proyecto o un puesto en mente? Escríbeme. |
| contactName / contactEmail / contactMessage | Name / Email / Message | Nombre / Correo electrónico / Mensaje |
| contactNameError | Enter your name. | Escribe tu nombre. |
| contactEmailError | Enter a valid email. | Escribe un correo válido. |
| contactMessageError | Write between 10 and 2,000 characters. | Escribe entre 10 y 2000 caracteres. |
| contactSend / contactSending | Send message / Sending… | Enviar mensaje / Enviando… |
| contactWaking | The server is waking up; this can take up to a minute. | El servidor está despertando; puede tardar hasta un minuto. |
| contactSent | Thanks! Your message was sent. | ¡Gracias! Tu mensaje fue enviado. |
| contactLimited | Too many messages for now. Try again later. | Demasiados mensajes por ahora. Inténtalo más tarde. |
| contactError | The message couldn’t be sent. Try again. | No se pudo enviar el mensaje. Inténtalo de nuevo. |
| contactHoneypot | Leave this field empty | Deja este campo vacío |

- [ ] **Step 1: Write the failing tests.** Use `provideHttpClientTesting` and fake timers where noted.
  - `backend-wake.spec.ts` — `pingsLivenessOnce`: spy on `globalThis.fetch`. Calling `wake()` twice makes exactly 1 call, to `https://api.example/api/health/liveness` with `mode: 'no-cors'` (`API_ORIGIN` provided as `https://api.example`).
  - `contact-section.spec.ts`:
    - `doesNotSendAnInvalidForm`: submit an empty form. There is no HTTP request, and the three error messages are shown.
    - `sendsTheMessageWithLocaleAndShowsSuccess`: fill in valid data and submit. The request goes to `POST https://api.example/api/contact` with a body that includes `locale` and `website: ''`. Flush 202. `data-form-state` is `sent`, and the form is reset.
    - `showsLimitedOn429`: flush 429. The state is `limited` and the input is kept.
    - `showsWakingHintAndKeepsInputOnTimeout` (fake timers): after 5 s the waking text is visible. After 90 s the state is `error`, the input is kept, and the Send button is enabled again.
    - `renderedHtmlHasNoDataStateAttribute`: the rendered element contains no `data-state=` attribute.
- [ ] **Step 2: Run the tests and confirm they fail.** Run `npx ng test --watch=false`. Expected: the new specs fail.
- [ ] **Step 3: Implement the model, `BackendWake`, `ContactApi`, the component, the home-page and header changes, and the Spanish `<trans-unit>`s.** Style the section with existing tokens from `styles.scss`, following the visual direction memory: same section rhythm, inputs with visible focus rings, labels above fields, errors linked with `aria-describedby`, and a live region (`role="status"`) for the sending/sent/error messages.
- [ ] **Step 4: Run every frontend check and confirm it passes.**
  - Run `npx ng test --watch=false && npm run test:scripts`. Expected: all pass.
  - Run `node scripts/check-translations.mjs`. Expected: no missing translations.
  - With the dev backend running on `:8080` and `API_URL=http://localhost:8080 SITE_URL=http://localhost:8080`, run `npm run build`. Expected: ends with `Prerender verified.`
- [ ] **Step 5: Check the result visually and end to end, locally.**
  - Run `ng serve` on :4200 against the backend on :8080.
  - Take screenshots of `/es/#contact` and `/en/#contact` at 375 px and 1280 px, in light and dark mode.
  - Submit once and confirm 202, a row in `contact.message`, and the success state.
  - Stop the backend, submit, and confirm the error state with the input kept.
- [ ] **Step 6: Commit** with `feat(frontend): contact form with sending states, wake ping and nav link`.

---

### Task 7: Docs, deploy and production verification

**Files:**
- Modify: `README.md` — add `POST /api/contact` to the API table, and add the env vars `CORS_ALLOWED_ORIGINS`, `CONTACT_PER_CLIENT_PER_HOUR` and `CONTACT_DAILY_CAP` to Deployment.
- Modify: `docs/superpowers/specs/2026-11-14-portafolio-design.md` (rev 5 status line):
  - §5.5: CORS uses the exact Workers origin, not `*.pages.dev`;
  - §5.5: contact limits;
  - §5.3: schema per module (`contact`);
  - §5.7: the wake ping fires when the contact section approaches, not on page load.

- [ ] **Step 1: Update the docs, then run the full test run.** Run `cd backend && ./mvnw -B -q verify` and `cd frontend && npx ng test --watch=false && npm run test:scripts`. Expected: everything passes.
- [ ] **Step 2: Commit, push the branch and wait for CI to pass.** Commit with `docs: contact module, CORS and limits (spec rev 5)`, then run `git push -u origin feat/part-6-contact`. Expected: CI and CodeQL are green.
- [ ] **Step 3 (user, before merge):** in Render, add the env var `CORS_ALLOWED_ORIGINS=https://victor-hidalgo.victorelianhe.workers.dev`. The backend must allow the site before the site starts calling it.
- [ ] **Step 4: Merge to `main` after the user approves.** Render deploys, and Flyway applies V8 as `neondb_owner`. Cloudflare rebuilds the site.
- [ ] **Step 5: Verify in production.**
  - `curl -i -X OPTIONS` to `https://portfolio-api-nthv.onrender.com/api/contact` with `Origin` set to the site and `Access-Control-Request-Method: POST`. Expected: 200 and `Access-Control-Allow-Origin` set to the site.
  - The same request with `Origin: https://evil.example`. Expected: 403.
  - A honeypot POST through curl. Expected: 202, with no row stored (check in the Neon SQL Editor).
  - Playwright on `/es/`:
    - the live `_headers` CSP `connect-src` includes the Render origin;
    - scrolling to the contact section fires exactly 1 liveness request;
    - one real form submission ends in `data-form-state="sent"`, with 0 CSP violations.
  - Tell the user how to delete that test row in Neon (`delete from contact.message where email = '…';`, run as the owner).

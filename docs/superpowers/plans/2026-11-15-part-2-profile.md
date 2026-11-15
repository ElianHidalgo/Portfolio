# Part 2 — Profile Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `GET /api/profile` serves a bilingual profile with skills from PostgreSQL, and the Angular home page (`/es/`, `/en/`) renders it as hero, about-me and skills sections.

**Architecture:** A new Spring Modulith module `profile` (`web` → `application` → `domain`) backed by two Flyway-managed tables and a seed migration; `LocalizedText` lives in the `shared` module so `projects` can reuse it in part 3. The frontend gets a routed `HomePage` that loads the profile with `httpResource` and renders three presentational section components; the active language comes from Angular's `LOCALE_ID`.

**Tech Stack:** Spring Boot 4.1.2, Spring Data JPA, Flyway, Spring Modulith 2.1.2, Testcontainers; Angular 22.2.6 (`httpResource`, signals, standalone components), `@angular/localize`, Vitest.

**Spec:** `docs/superpowers/specs/2026-11-14-portafolio-design.md` (sections 5.3 `profile` row, 5.4, 5.6, 5.7, 6 part 2).

## Global Constraints

- All code, identifiers, comments, commits and repo docs in **English**; UI copy is bilingual ES/EN.
- Root package `dev.elianhidalgo.portfolio`; modules are top-level packages; controllers never expose JPA entities, only DTO records.
- Schema owned by Flyway (`ddl-auto: validate`); new migrations start at `V2`.
- Errors use `ProblemDetail` (RFC 9457).
- The public API returns **both languages** per text: `{"es": "...", "en": "..."}`.
- Skill categories are the fixed set `BACKEND, FRONTEND, DATABASE, DEVOPS, TOOLS`, displayed in that order; category names are translated in the UI, skill names are not.
- Frontend: standalone components, signals, SCSS with CSS variables, no UI library; source locale `en`, translation `src/locale/messages.es.xlf`.
- No admin endpoints in this part (they arrive in part 8).
- Work on branch `feat/part-2-profile`; commits use Conventional Commits ending with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **No profile row** (fresh DB without seed, or deleted) — API answers `404` `application/problem+json`, and the page shows the error state, never a blank page. Pinned in Task 1 Step 1 and Task 3 Step 1.
2. **Optional fields are null** (`location`, `email`, `linkedinUrl`, `avatarUrl`, one or both `cvUrl` values) or **no skills** — those elements are hidden; never the text "null", a broken image, or an empty "Skills" heading. Pinned in Task 3 Step 1.
3. **Multi-paragraph "about" text** (blank lines between paragraphs) — rendered as separate paragraphs. Pinned in Task 3 Step 1.
4. **A new UI string without a Spanish translation** — the localized build fails instead of silently showing English on `/es/`. Pinned in Task 3 Step 4 (`i18nMissingTranslation: "error"`).
5. **API down or slow to answer** (Cloud Run waking up later) — loading message, then an error with a working "Try again" button. Pinned in Task 3 Step 1.

---

### Task 1: Backend `profile` module with seed data

**Files:**
- Create: `backend/src/main/java/dev/elianhidalgo/portfolio/shared/LocalizedText.java`
- Create: `backend/src/main/java/dev/elianhidalgo/portfolio/profile/domain/{Profile,Skill,SkillCategory,ProfileRepository,SkillRepository}.java`
- Create: `backend/src/main/java/dev/elianhidalgo/portfolio/profile/application/{ProfileService,ProfileWithSkills,ProfileNotFoundException}.java`
- Create: `backend/src/main/java/dev/elianhidalgo/portfolio/profile/web/{ProfileController,ProfileResponse,SkillResponse}.java`
- Create: `backend/src/main/resources/db/migration/V2__create_profile_and_skills.sql`, `V3__seed_profile.sql`
- Modify: `backend/src/main/resources/application.yml` (add `spring.mvc.problemdetails.enabled: true`)
- Test: `backend/src/test/java/dev/elianhidalgo/portfolio/profile/ProfileApiTests.java`

**Interfaces:**
- Produces: `GET /api/profile` → `ProfileResponse` JSON:
  `{"fullName", "headline": {es,en}, "about": {es,en}, "location", "email", "githubUrl", "linkedinUrl", "avatarUrl", "cvUrl": {es,en}, "skills": [{"name", "category"}]}` — absent optional values are JSON `null`; skills sorted by `display_order`, then `name`.
- Produces: `record LocalizedText(String es, String en)` in `shared` (reused by `projects` in part 3).

- [ ] **Step 1: Write the failing tests** (`@SpringBootTest @AutoConfigureMockMvc @Import(TestcontainersConfiguration.class) @Transactional`, so per-test data changes roll back)

```java
@Test void returnsSeededProfileInBothLanguages() throws Exception {
    mockMvc.perform(get("/api/profile"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fullName").value("Elian Hidalgo"))
        .andExpect(jsonPath("$.headline.en").value("Full-stack developer — Java, Spring & Angular"))
        .andExpect(jsonPath("$.headline.es").value("Desarrollador full-stack — Java, Spring y Angular"))
        .andExpect(jsonPath("$.githubUrl").value("https://github.com/ElianHidalgo"))
        .andExpect(jsonPath("$.location").isEmpty())
        .andExpect(jsonPath("$.cvUrl.es").isEmpty());
}

@Test void returnsSkillsOrderedByDisplayOrderThenName() throws Exception {
    jdbc.update("insert into skill (name, category, display_order) values ('Kotlin', 'BACKEND', 1)");
    mockMvc.perform(get("/api/profile"))
        .andExpect(jsonPath("$.skills[*].name").value(contains(
            "Java", "Kotlin", "Spring Boot", "Angular", "TypeScript", "PostgreSQL", "Docker", "Git")))
        .andExpect(jsonPath("$.skills[0].category").value("BACKEND"));
}

@Test void returnsNotFoundProblemWhenProfileIsMissing() throws Exception {
    jdbc.update("delete from profile");
    mockMvc.perform(get("/api/profile"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value("Profile has not been created yet"));
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `cd backend; ./mvnw -B test -Dtest=ProfileApiTests`
Expected: 3 failures — `404` on the first two (no controller); the third fails on content type / `detail` (no problem body) or on `relation "profile" does not exist`.

- [ ] **Step 3: Write the migrations**

`V2__create_profile_and_skills.sql`:
- `profile`: `id smallint primary key default 1 check (id = 1)` (single row), `full_name varchar(100) not null`, `headline_es`/`headline_en varchar(160) not null`, `about_es`/`about_en text not null`, `location varchar(100)`, `email varchar(254)`, `github_url`, `linkedin_url`, `avatar_url`, `cv_url_es`, `cv_url_en` (`varchar(500)`, nullable), `updated_at timestamptz not null default now()`.
- `skill`: `id bigserial primary key`, `name varchar(60) not null unique`, `category varchar(20) not null check (category in ('BACKEND','FRONTEND','DATABASE','DEVOPS','TOOLS'))`, `display_order int not null default 0`.

`V3__seed_profile.sql`: one profile — `full_name` "Elian Hidalgo", headlines exactly as in the test, `about_en` "I build web applications end to end with Java, Spring Boot and Angular.\n\nThis text is sample content — edit it from the admin panel." and `about_es` "Construyo aplicaciones web de punta a punta con Java, Spring Boot y Angular.\n\nEste texto es de ejemplo — edítalo desde el panel de administración." (two paragraphs, use `E'...\n\n...'`), `github_url` "https://github.com/ElianHidalgo", every other optional column null. Skills with `display_order`: Java 1 BACKEND, Spring Boot 2 BACKEND, Angular 3 FRONTEND, TypeScript 4 FRONTEND, PostgreSQL 5 DATABASE, Docker 6 DEVOPS, Git 7 TOOLS.

- [ ] **Step 4: Implement the module**

- `domain`: JPA entities `Profile` (`@Table(name = "profile")`, `Short id`) and `Skill` mapping the columns above (`@Enumerated(EnumType.STRING) SkillCategory category`); `enum SkillCategory { BACKEND, FRONTEND, DATABASE, DEVOPS, TOOLS }`; `ProfileRepository extends JpaRepository<Profile, Short>`; `SkillRepository extends JpaRepository<Skill, Long>` with `List<Skill> findAllByOrderByDisplayOrderAscNameAsc()`.
- `application`: `record ProfileWithSkills(Profile profile, List<Skill> skills)`; `ProfileService.getProfile(): ProfileWithSkills` (`@Transactional(readOnly = true)`, takes the single row via `findAll().stream().findFirst()`, throws `ProfileNotFoundException` when empty); `ProfileNotFoundException extends ErrorResponseException` with `HttpStatus.NOT_FOUND` and detail `"Profile has not been created yet"`.
- `web`: `ProfileController` — `@GetMapping("/api/profile") ProfileResponse getProfile()`; records `ProfileResponse(String fullName, LocalizedText headline, LocalizedText about, String location, String email, String githubUrl, String linkedinUrl, String avatarUrl, LocalizedText cvUrl, List<SkillResponse> skills)` with `static ProfileResponse from(ProfileWithSkills)`, and `SkillResponse(String name, SkillCategory category)`.
- `application.yml`: `spring.mvc.problemdetails.enabled: true`.

- [ ] **Step 5: Run the whole backend suite**

Run: `./mvnw -B test`
Expected: `Tests run: 6, Failures: 0, Errors: 0` (3 new + 2 health + 1 modularity) and `BUILD SUCCESS`. The modularity test must still pass with the new `profile` → `shared` dependency.

- [ ] **Step 6: Commit**

```bash
git add backend/
git commit -m "feat(profile): add bilingual profile and skills API with seed data"
```

---

### Task 2: Frontend profile model, localized pipe and skill grouping

**Files:**
- Create: `frontend/src/app/core/models/profile.ts`
- Create: `frontend/src/app/shared/localized.pipe.ts`, `localized.pipe.spec.ts`
- Create: `frontend/src/app/public/home/skills-section/group-skills.ts`, `group-skills.spec.ts`

**Interfaces:**
- Consumes: Task 1's JSON shape.
- Produces:
  - `interface LocalizedText { es: string; en: string }`, `interface OptionalLocalizedText { es: string | null; en: string | null }`, `type SkillCategory = 'BACKEND' | 'FRONTEND' | 'DATABASE' | 'DEVOPS' | 'TOOLS'`, `interface Skill { name: string; category: SkillCategory }`, `interface Profile { fullName: string; headline: LocalizedText; about: LocalizedText; location: string | null; email: string | null; githubUrl: string | null; linkedinUrl: string | null; avatarUrl: string | null; cvUrl: OptionalLocalizedText; skills: Skill[] }`
  - `LocalizedPipe` (`name: 'localized'`): `transform(text: OptionalLocalizedText | null): string` — Spanish when `LOCALE_ID` starts with `es`, English otherwise; `''` for null.
  - `groupSkills(skills: Skill[]): SkillGroup[]` with `interface SkillGroup { category: SkillCategory; label: string; skills: Skill[] }` — groups in the fixed order, empty groups omitted, input order kept inside a group; `label` comes from `$localize` with ids `@@skillCategory.BACKEND` … `@@skillCategory.TOOLS` and English text `Backend`, `Frontend`, `Databases`, `DevOps`, `Tools`.

- [ ] **Step 1: Write the failing tests**

```ts
// localized.pipe.spec.ts
it('picks Spanish for an es locale', () => {
  TestBed.configureTestingModule({ providers: [{ provide: LOCALE_ID, useValue: 'es' }] });
  const pipe = TestBed.runInInjectionContext(() => new LocalizedPipe());
  expect(pipe.transform({ es: 'Hola', en: 'Hello' })).toBe('Hola');
});
it('picks English for en-US', () => { /* LOCALE_ID 'en-US' */ expect(pipe.transform({ es: 'Hola', en: 'Hello' })).toBe('Hello'); });
it('returns an empty string for missing values', () => {
  expect(pipe.transform(null)).toBe('');
  expect(pipe.transform({ es: null, en: null })).toBe('');
});

// group-skills.spec.ts
it('groups skills in the fixed category order and omits empty groups', () => {
  const groups = groupSkills([
    { name: 'Docker', category: 'DEVOPS' },
    { name: 'Java', category: 'BACKEND' },
    { name: 'Angular', category: 'FRONTEND' },
    { name: 'Spring Boot', category: 'BACKEND' },
  ]);
  expect(groups.map(g => g.category)).toEqual(['BACKEND', 'FRONTEND', 'DEVOPS']);
  expect(groups[0].skills.map(s => s.name)).toEqual(['Java', 'Spring Boot']);
  expect(groups[2].label).toBe('DevOps');
});
it('returns no groups for no skills', () => { expect(groupSkills([])).toEqual([]); });
```

- [ ] **Step 2: Run to verify they fail**

Run: `cd frontend; npx ng test --watch=false`
Expected: FAIL — cannot resolve `./localized.pipe` / `./group-skills`.

- [ ] **Step 3: Implement the model, pipe and `groupSkills`** with the signatures above (`LocalizedPipe` injects `LOCALE_ID` with `inject()`).

- [ ] **Step 4: Run tests**

Run: `npx ng test --watch=false` → Expected: all passed (5 new + existing).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/core frontend/src/app/shared frontend/src/app/public
git commit -m "feat(frontend): add profile model, localized pipe and skill grouping"
```

---

### Task 3: Home page with hero, about and skills sections

**Files:**
- Create: `frontend/src/app/public/home/home-page.{ts,html,scss,spec.ts}`
- Create: `frontend/src/app/public/home/hero-section/hero-section.{ts,html,scss}`, `about-section/about-section.{ts,html,scss}`, `skills-section/skills-section.{ts,html,scss}`
- Modify: `frontend/src/app/app.html` (only `<router-outlet />`), `app.spec.ts`, `app.routes.ts`, `app.config.ts`, `src/styles.scss`, `angular.json`, `src/locale/messages.es.xlf`

**Interfaces:**
- Consumes: Task 2's `Profile`, `LocalizedPipe`, `groupSkills`; Task 1's `GET /api/profile`.
- Produces: route `''` → `HomePage` (selector `app-home-page`), page title `$localize` `@@pageTitle` "Portfolio"; section components `HeroSection`, `AboutSection`, `SkillsSection`, each with `profile = input.required<Profile>()`.

- [ ] **Step 1: Write the failing tests** (`home-page.spec.ts`; providers `provideHttpClient()`, `provideHttpClientTesting()`; flush via `HttpTestingController.expectOne('/api/profile')`, then `await fixture.whenStable()`; a `sampleProfile()` helper returns a full `Profile` with English texts)

| Test | Arrange | Assert |
|---|---|---|
| `shows a loading message until the profile arrives` | no flush | text contains `Loading profile…` |
| `renders name, headline and each paragraph of the about text` | `about.en = 'First paragraph.\n\nSecond paragraph.'` | `#hero h1` = full name; `#hero` contains the headline; `#about p` count is 2 |
| `groups skills under translated category headings` | skills Java BACKEND, Docker DEVOPS | `#skills h3` texts = `['Backend', 'DevOps']`; `#skills li` texts = `['Java', 'Docker']` |
| `hides optional fields that are null` | `location`, `email`, `linkedinUrl`, `avatarUrl` null; `cvUrl = {es: null, en: null}`; `skills = []` | no `img`; no `a[href^="mailto:"]`; no link text `LinkedIn` / `Download CV`; no `#skills`; page text does not contain `null` |
| `shows an error with a working retry when the request fails` | flush status 500 | text contains `We couldn't load this section.`; clicking button `Try again` issues a new `/api/profile` request; flushing it renders `#hero h1` |
| `shows the error state when the profile does not exist` | flush status 404 | text contains `We couldn't load this section.` |

`app.spec.ts` becomes: `navigating to '' renders the home page` (`RouterTestingHarness`, routes from `app.routes.ts`, HTTP testing providers) → `app-home-page` exists.

- [ ] **Step 2: Run to verify they fail**

Run: `npx ng test --watch=false`
Expected: FAIL — cannot resolve `./home-page`.

- [ ] **Step 3: Implement**

- `app.config.ts`: add `provideHttpClient(withFetch())`. `app.routes.ts`: `{ path: '', component: HomePage, title: $localize`:@@pageTitle:Portfolio` }`. `app.html`: `<router-outlet />` only.
- `HomePage`: `profile = httpResource<Profile>(() => '/api/profile')`; template uses `@if` over `profile.isLoading()`, `profile.error()`, `profile.hasValue()`; the error block's button calls `profile.reload()`.
- `HeroSection` (`<section id="hero">`): `h1` full name, headline, location (if any), links — GitHub, LinkedIn, email (`mailto:`), `Download CV` (CV URL for the active language via `localized`) — each only when present; external links `target="_blank" rel="noopener"`; avatar `img` with `alt` = full name only when `avatarUrl` is set.
- `AboutSection` (`<section id="about">`, `h2` "About me"): split the localized text on blank lines (`/\n\s*\n/`), trim, drop empties, one `<p>` each (a `computed`).
- `SkillsSection` (`<section id="skills">`, `h2` "Skills"): rendered only when `groupSkills(...)` is non-empty; one `h3` + `ul` per group.
- `styles.scss`: CSS variables on `:root` (text, muted text, background, accent, max content width ~72rem, spacing scale) and a base responsive layout (single column, 16px side gutter). Full visual design is part 9.
- All UI copy gets `i18n` with explicit ids:

| id | English | Spanish |
|---|---|---|
| `pageTitle` | Portfolio | Portafolio |
| `profileLoading` | Loading profile… | Cargando perfil… |
| `loadError` | We couldn't load this section. | No pudimos cargar esta sección. |
| `retry` | Try again | Reintentar |
| `aboutTitle` | About me | Sobre mí |
| `skillsTitle` | Skills | Habilidades |
| `downloadCv` | Download CV | Descargar CV |
| `skillCategory.BACKEND` | Backend | Backend |
| `skillCategory.FRONTEND` | Frontend | Frontend |
| `skillCategory.DATABASE` | Databases | Bases de datos |
| `skillCategory.DEVOPS` | DevOps | DevOps |
| `skillCategory.TOOLS` | Tools | Herramientas |

- [ ] **Step 4: Translations and strict build**

`angular.json` build options: `"i18nMissingTranslation": "error"`. Run `npx ng extract-i18n --output-path src/locale`, merge the new units into `messages.es.xlf` with the Spanish targets from the table (remove `homeTitle`, which no longer exists), delete `messages.xlf`.
Run: `npx ng build --localize` → Expected: success, no missing-translation errors.
Then prove the guard: temporarily delete the `retry` `<target>`, rerun → Expected: build FAILS naming `retry`; restore it.

- [ ] **Step 5: Run tests**

Run: `npx ng test --watch=false` → Expected: all passed (6 home-page + 1 app + Task 2's 5).

- [ ] **Step 6: Commit**

```bash
git add frontend/
git commit -m "feat(frontend): render bilingual hero, about and skills sections from the API"
```

---

### Task 4: End-to-end check and README

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: everything above.

- [ ] **Step 1: Stack smoke test**

Run: `docker compose up -d --build`, wait for `curl.exe -s http://localhost:8080/api/health` to contain `"status":"UP"`, then
`curl.exe -s http://localhost:8080/api/profile` → Expected: JSON with `"fullName":"Elian Hidalgo"` and 7 skills.
`docker compose logs backend` → Expected: `Successfully applied 2 migrations` on an existing volume (or 3 on a fresh one) and no `ERROR`.
Then `docker compose down`.

- [ ] **Step 2: Check in a browser** — with the stack up, open `http://localhost:8080/es/` and `/en/` (use a browser tool if one is available; otherwise ask the user to look): Spanish/English headings, the seed headline, two "about" paragraphs, five skill groups (the seed fills all five). Record what was seen in the ledger.

- [ ] **Step 3: README** — add an "API" section listing `GET /api/health` and `GET /api/profile` (one-line description each, note that texts come as `{"es","en"}` pairs), and under "Translations" note that the build fails on a missing Spanish translation.

- [ ] **Step 4: Commit**

```bash
git add README.md
git commit -m "docs: document profile API and strict translations"
```

# Part 3 — Projects Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `GET /api/projects` and `GET /api/projects/{slug}` serve bilingual projects, shown as a "Projects" section on the home page and a `/projects/:slug` detail page, designed to look complete without images.

**Architecture:** New Spring Modulith module `projects` (`web` → `application` → `domain`), Flyway tables + seed with three real projects. Frontend: header and footer move to the app shell so every page shares them; a `ProjectsSection` (own `httpResource`) on the home page and a routed `ProjectPage`. Text helpers become plain functions reused by pipe and components.

**Tech Stack:** Spring Boot 4.1.2, Spring Data JPA (PostgreSQL `varchar[]` via Hibernate `SqlTypes.ARRAY`), Flyway, Testcontainers; Angular 22.2.6 (`httpResource`, router `withComponentInputBinding` + in-memory scrolling), `@angular/localize`, Vitest.

**Spec:** `docs/superpowers/specs/2026-11-14-portafolio-design.md` (5.3 `projects` row, 5.4, 5.7, 6 part 3). Design system: `src/styles.scss` tokens and `.section`/`.button` classes from part 2.

## Global Constraints

- Code, identifiers, comments, commits and repo docs in **English**; UI copy bilingual, Spanish targets in `messages.es.xlf`; `npm run build` must pass the translation check.
- Public API returns texts as `{"es","en"}`; only `published` projects are visible; unknown or unpublished slug → `404` `application/problem+json` with detail `"Project not found"`.
- Projects sort by `display_order`, then `slug`.
- Slug: lowercase letters, digits and single hyphens (`^[a-z0-9]+(-[a-z0-9]+)*$`), unique, shared by both languages.
- Images are optional: no image → no `<img>`, no placeholder box.
- Technology names, project titles that are brand names, and the owner's name carry `translate="no"`.
- Visual language: existing tokens and classes; no cards with shadows, no pills, no badges; left-aligned editorial rows.
- Work continues on branch `feat/part-2-profile` (part 2 is unmerged by the user's choice); Conventional Commits ending with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Unknown or unpublished slug** (typo in URL, project hidden later) — detail page shows "This project doesn't exist" with a link back home, never a spinner or blank. Pinned in Task 4 Step 1.
2. **Project without repo, demo or image** (the portfolio's repo is private today) — no empty buttons, no broken image. Pinned in Task 3 Step 1 and Task 4 Step 1.
3. **Projects API fails while the profile loads** — the home page still shows the profile; only the projects section shows its own error and retry. Pinned in Task 3 Step 1.
4. **Switching language on a detail page** — lands on the same project in the other language, not on the home page. Pinned in Task 2 Step 1.
5. **Header links from a detail page** ("About", "Projects", "Skills") — go to the home page section, not to a missing anchor on the detail page. Pinned in Task 2 Step 1.

---

### Task 1: Backend `projects` module with seed data

**Files:**
- Create: `backend/src/main/resources/db/migration/V5__create_projects.sql`, `V6__seed_projects.sql`
- Create: `backend/src/main/java/dev/elianhidalgo/portfolio/projects/domain/{Project,ProjectRepository}.java`
- Create: `.../projects/application/{ProjectService,ProjectNotFoundException}.java`
- Create: `.../projects/web/{ProjectController,ProjectSummaryResponse,ProjectDetailResponse}.java`
- Test: `backend/src/test/java/dev/elianhidalgo/portfolio/projects/ProjectsApiTests.java`

**Interfaces:**
- Consumes: `shared.LocalizedText(String es, String en)`.
- Produces: `GET /api/projects` → `ProjectSummaryResponse[]` = `{slug, title{es,en}, summary{es,en}, technologies: string[], repoUrl, demoUrl, imageUrl, featured}`; `GET /api/projects/{slug}` → `ProjectDetailResponse` = summary fields + `description{es,en}`.

- [ ] **Step 1: Write the failing tests** (same annotations as `ProfileApiTests`, incl. `@Transactional`)

```java
@Test void listsPublishedProjectsInDisplayOrder() throws Exception {
    mockMvc.perform(get("/api/projects"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].slug").value(contains("portfolio", "gamevault", "proxy-cache-api")))
        .andExpect(jsonPath("$[0].title.en").value("Personal portfolio"))
        .andExpect(jsonPath("$[0].technologies").value(hasItem("Spring Boot")))
        .andExpect(jsonPath("$[0].repoUrl").isEmpty())
        .andExpect(jsonPath("$[0].description").doesNotExist());
}
@Test void breaksDisplayOrderTiesBySlug() throws Exception {
    jdbc.update("insert into project (slug, title_es, title_en, summary_es, summary_en, description_es, description_en, display_order) "
        + "values ('aaa', 'A', 'A', 'a', 'a', 'a', 'a', 1)");
    mockMvc.perform(get("/api/projects")).andExpect(jsonPath("$[0].slug").value("aaa"));
}
@Test void hidesUnpublishedProjects() throws Exception {
    jdbc.update("update project set published = false where slug = 'gamevault'");
    mockMvc.perform(get("/api/projects")).andExpect(jsonPath("$.length()").value(2));
    mockMvc.perform(get("/api/projects/gamevault")).andExpect(status().isNotFound());
}
@Test void returnsProjectDetailBySlug() throws Exception {
    mockMvc.perform(get("/api/projects/gamevault"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title.es").value("GameVault"))
        .andExpect(jsonPath("$.repoUrl").value("https://github.com/ElianHidalgo/GameVault"))
        .andExpect(jsonPath("$.description.en").value(startsWith("GameVault is a cross-platform")));
}
@Test void returnsNotFoundProblemForUnknownSlug() throws Exception {
    mockMvc.perform(get("/api/projects/does-not-exist"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value("Project not found"));
}
```

- [ ] **Step 2: Run** `cd backend; ./mvnw -B test -Dtest=ProjectsApiTests` → Expected: 5 failures (404 / `relation "project" does not exist`).

- [ ] **Step 3: Migrations**

`V5__create_projects.sql` — table `project`: `id bigserial pk`, `slug varchar(80) not null unique check (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')`, `title_es/en varchar(120) not null`, `summary_es/en varchar(300) not null`, `description_es/en text not null`, `technologies varchar(40)[] not null default '{}'`, `repo_url/demo_url/image_url varchar(500)` null, `featured boolean not null default false`, `display_order int not null default 0`, `published boolean not null default true`, `created_at timestamptz not null default now()`.

`V6__seed_projects.sql` — exact content:

| slug | order | featured | title es / en | repo_url | technologies |
|---|---|---|---|---|---|
| `portfolio` | 1 | true | Portafolio personal / Personal portfolio | null (repo is private) | Java, Spring Boot, Spring Modulith, PostgreSQL, Flyway, Angular, TypeScript, Docker |
| `gamevault` | 2 | false | GameVault / GameVault | https://github.com/ElianHidalgo/GameVault | C#, .NET MAUI, XAML, MVVM |
| `proxy-cache-api` | 3 | false | API de catálogo con Proxy y Caché / Catalog API with Proxy and Cache | https://github.com/ElianHidalgo/demo-app-proxy-cache | Java, Spring Boot, REST, Design patterns |

Summaries (one sentence each) and descriptions (2–3 paragraphs separated by `\n\n`, honest about prototype status) in both languages, written from the repos' READMEs and the CV; GameVault's English description starts with "GameVault is a cross-platform".

- [ ] **Step 4: Implement**
- `domain.Project` entity (read-only getters), `List<String> technologies` mapped with `@JdbcTypeCode(SqlTypes.ARRAY)`; `ProjectRepository`: `List<Project> findAllByPublishedTrueOrderByDisplayOrderAscSlugAsc()`, `Optional<Project> findBySlugAndPublishedTrue(String slug)`.
- `application.ProjectService`: `List<Project> listPublished()`, `Project getPublished(String slug)` (throws `ProjectNotFoundException` — `ErrorResponseException`, 404, detail `"Project not found"`), both `@Transactional(readOnly = true)`.
- `web.ProjectController`: `@GetMapping("/api/projects") List<ProjectSummaryResponse> list()`, `@GetMapping("/api/projects/{slug}") ProjectDetailResponse get(@PathVariable String slug)`; records with `static from(Project)`.

- [ ] **Step 5: Run** `./mvnw -B test` → Expected: `Tests run: 11, Failures: 0` (5 new + 6 existing, Modularity included).
- [ ] **Step 6: Commit** `feat(projects): add bilingual projects API with seed data`

---

### Task 2: Shared text helpers and app shell (header/footer)

**Files:**
- Modify: `frontend/src/app/shared/localized.pipe.ts` (+ spec) — add `pickLocalized(text: OptionalLocalizedText | null, locale: string): string`; pipe delegates to it
- Create: `frontend/src/app/shared/paragraphs.ts` (+ spec) — `splitParagraphs(text: string): string[]` (split on `/\n\s*\n/`, trim, drop empties)
- Modify: `about-section.ts` — use both functions instead of injecting the pipe
- Create: `frontend/src/app/core/layout/site-header/site-header.{ts,html,scss,spec.ts}`, `site-footer/site-footer.{ts,html,scss}`
- Modify: `app.html` (skip link + `<app-site-header />` + `<router-outlet />` + `<app-site-footer />`), `app.ts`, `app.config.ts` (`provideRouter(routes, withComponentInputBinding(), withInMemoryScrolling({ anchorScrolling: 'enabled', scrollPositionRestoration: 'enabled' }))`), `home-page.{html,scss}` (drop header/footer/skip link; `<main id="content">` stays per page)

**Interfaces:**
- Produces: `pickLocalized`, `splitParagraphs`; `SiteHeader` nav = About `#about`, Projects `#projects`, Skills `#skills` as `routerLink="/" [fragment]`; language link `href = '/' + otherLocale + router.url` (e.g. on `es` at `/projects/gamevault` → `/en/projects/gamevault`).

- [ ] **Step 1: Write the failing tests**
  - `paragraphs.spec.ts`: `'A\n\nB'` → `['A','B']`; `'A\r\n\r\nB'` → `['A','B']`; `'  '` → `[]`.
  - `localized.pipe.spec.ts`: add `pickLocalized({es:'Hola',en:'Hello'}, 'es-EC')` → `'Hola'`.
  - `site-header.spec.ts` (with `provideRouter` + `RouterTestingHarness`, `LOCALE_ID` `'es'`): after navigating to `/projects/gamevault`, the language link `href` is `/en/projects/gamevault`; nav link texts are `['About','Projects','Skills']` and the Projects link `href` ends with `/#projects`.
- [ ] **Step 2: Run** `npx ng test --watch=false` → Expected: FAIL (missing modules).
- [ ] **Step 3: Implement** as in Interfaces; header/footer markup and styles move unchanged from `home-page` except the nav.
- [ ] **Step 4: Run** tests → all pass; existing HomePage tests unchanged.
- [ ] **Step 5: Commit** `refactor(frontend): move header and footer to the app shell; share text helpers`

---

### Task 3: Projects section on the home page

**Files:**
- Create: `frontend/src/app/core/models/project.ts` — `ProjectSummary { slug; title: LocalizedText; summary: LocalizedText; technologies: string[]; repoUrl: string|null; demoUrl: string|null; imageUrl: string|null; featured: boolean }`, `ProjectDetail extends ProjectSummary { description: LocalizedText }`
- Create: `frontend/src/app/public/home/projects-section/projects-section.{ts,html,scss,spec.ts}`
- Modify: `home-page.html` (order: hero, about, **projects**, skills)

**Interfaces:**
- Produces: `ProjectsSection` (`app-projects-section`), own `httpResource<ProjectSummary[]>(() => '/api/projects')`; renders `<section id="projects" class="section">` with `h2` "Projects"; one `<article>` per project: `h3 > a[routerLink="/projects/{slug}"]` title, summary, technologies line (`translate="no"`), actions "Code" (GitHub icon, `repoUrl`) and "Live demo" (`demoUrl`) only when present.

- [ ] **Step 1: Write the failing tests** (`HttpTestingController`, `provideRouter([])`)
  - `renders each project with a link to its page` — 2 projects → `#projects h3 a` hrefs `['/projects/a','/projects/b']`, texts = English titles.
  - `shows technologies and only the links that exist` — project with `repoUrl` only → link text `Code` present, `Live demo` absent.
  - `renders nothing when there are no projects` — `[]` → no `#projects`.
  - `shows its own error and retry when projects fail` — 500 → text `Projects didn’t load.` and button `Try again` re-requests `/api/projects`.
- [ ] **Step 2: Run** → FAIL (missing module).
- [ ] **Step 3: Implement.** Layout: each article a row in the section's right column separated by `--color-line` hairlines; title `--text-l`, weight 700, link without underline until hover; summary in ink; technologies in `--color-muted`, `--text-s`, spaced with `gap`; actions as `.button` (secondary). If `imageUrl` exists: image above the text, `aspect-ratio: 16/10`, `loading="lazy"`, `width`/`height` set. Header nav gains "Projects".
- [ ] **Step 4: i18n** — new ids `projectsTitle` Projects/Proyectos, `projectCode` Code/Código, `projectDemo` Live demo/Ver demo, `projectsLoadError` "Projects didn’t load." / "Los proyectos no cargaron.", `navProjects` Projects/Proyectos; merge into `messages.es.xlf`; `node scripts/check-translations.mjs` passes.
- [ ] **Step 5: Run** tests + `npm run build` → pass.
- [ ] **Step 6: Commit** `feat(frontend): add projects section to the home page`

---

### Task 4: Project detail page

**Files:**
- Create: `frontend/src/app/public/project/project-page.{ts,html,scss,spec.ts}`
- Modify: `app.routes.ts` (`{ path: 'projects/:slug', component: ProjectPage }`), `app.spec.ts`

**Interfaces:**
- Consumes: `ProjectDetail`, `pickLocalized`, `splitParagraphs`, `Icon`.
- Produces: `ProjectPage` (`app-project-page`) with `slug = input.required<string>()` (router input binding), `httpResource<ProjectDetail>(() => '/api/projects/' + this.slug())`; sets document title to `"<title> | Portfolio"` (`Portafolio` in Spanish via `$localize` `@@pageTitle`).

- [ ] **Step 1: Write the failing tests**
  - `renders title, summary, technologies and description paragraphs` — description `'One.\n\nTwo.'` → `h1` = title, `article p.description` count 2.
  - `hides image and missing links` — `imageUrl`, `demoUrl`, `repoUrl` null → no `img`, no `Code`/`Live demo` links.
  - `shows a not-found message with a way back on 404` — text `This project doesn’t exist.` and a link `Back to all projects` with href `/#projects`.
  - `sets the document title from the project` — `Title.getTitle()` = `GameVault | Portfolio`.
  - `app.spec`: navigating to `/projects/gamevault` renders `app-project-page`.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement.** Layout inside `<main id="content" class="page">`: back link (`routerLink="/" fragment="projects"`, text "All projects"), `h1` at `clamp(2.5rem, 1rem + 6vw, 5rem)`, weight 800, tight tracking (echo of the hero, smaller); summary as lead (`--text-xl`, muted); technologies line; actions; optional image; description paragraphs in a `38rem` measure; loading and generic error states like the home page.
- [ ] **Step 4: i18n** — `backToProjects` All projects / Todos los proyectos, `projectNotFound` "This project doesn’t exist." / "Este proyecto no existe.", `projectNotFoundBack` Back to all projects / Volver a los proyectos, `projectLoading` Loading project… / Cargando proyecto…; check passes.
- [ ] **Step 5: Run** tests + `npm run build` → pass.
- [ ] **Step 6: Commit** `feat(frontend): add project detail page`

---

### Task 5: Visual review, guidelines pass and docs

- [ ] **Step 1:** Restart `ng serve` (translations changed); screenshots with headless Edge of `/` and `/projects/gamevault` — light + dark at 1440, light at 500 wide. Critique as a designer; fix spacing/hierarchy issues found; re-shoot.
- [ ] **Step 2:** Rebuild the Docker stack on `APP_PORT=8081`; confirm `/en/projects/gamevault` is English, language switch keeps the project, `/es/projects/does-not-exist` shows the not-found state.
- [ ] **Step 3:** Run the Vercel Web Interface Guidelines checklist (fetched from `https://raw.githubusercontent.com/vercel-labs/web-interface-guidelines/main/command.md`) over the new templates/styles; fix findings except documented exceptions (Title Case, font preload).
- [ ] **Step 4:** README "API" table gains `GET /api/projects` and `GET /api/projects/{slug}`.
- [ ] **Step 5: Commit** `docs: document projects API` (+ any `style(frontend): …` fix commits from Steps 1–3).

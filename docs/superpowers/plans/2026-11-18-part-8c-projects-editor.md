# Part 8c — Admin Projects Editor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** From `/es/admin/proyectos/`, the admin can list every project (published or hidden), create one, edit its bilingual texts, technologies, links and flags, reorder and delete projects. The public site changes only after **Publicar** (part 8a).

Third and last slice of spec step 8 (8a inbox and publishing, 8b profile and skills — both done).

**Owner decisions (2026-11-18):**
- The slug is chosen at creation and read-only afterwards.
- Images are out of scope: the editor never shows or changes `image_url`.

**Architecture:**
- **Backend:** `projects` gains admin endpoints under `/api/admin/projects`, protected by the existing `/api/admin/**` rule. No migration is needed: every column exists, and `portfolio_app` already has DML on `projects.*` and its sequence (V7).
- **Shared:** `SafeLink` and the text patterns move from `profile.web` to the `shared` module's base package, so both modules use one rule. The body limit becomes 64 KiB for `/api/admin/**`.
- **Frontend:** two new admin pages — the **Proyectos** list and the **project edit page** — in the admin navigation. The edit page reuses `unsaved-changes.guard.ts`.

**Tech Stack:** Spring Boot 4.1.2, Spring Modulith, Bean Validation, Testcontainers; Angular 22 (signals, reactive forms), Vitest.

**Spec:** `docs/superpowers/specs/2026-11-18-part-8c-projects-editor-design.md` (rev 9 of the parent spec `2026-11-14-portafolio-design.md`, §6 step 8c)

## Global Constraints

- **Language and copy:**
  - All code, identifiers, comments, commits and docs are in English.
  - Admin UI copy is in Spanish only, as plain text without `i18n` markers.
- **Cost and storage:** zero cost — no new services, no storage, no uploads.
- **Migrations:** never edit applied migrations (V1–V11). This part needs none.
- **Field limits** match the columns. They are enforced by Bean Validation and answer 400 problem JSON whose `detail` names the field.

| Field | Rule |
|---|---|
| `slug` (create only) | required, ≤ 80, `^[a-z0-9]+(-[a-z0-9]+)*$`, unique → 409 |
| `title.es`, `title.en` | required, ≤ 120, no control characters |
| `summary.es`, `summary.en` | required, ≤ 300, no control characters |
| `description.es`, `description.en` | required, ≤ 4000, line breaks and tabs allowed, no other control characters |
| `technologies` | ≤ 20 entries; each trimmed, non-blank, ≤ 40, no control characters; no duplicates (case-insensitive); order kept |
| `repoUrl`, `demoUrl` | optional, ≤ 500, `https://` only (`@SafeLink` without `allowPath`) |
| `featured`, `published` | required booleans |

- **Text normalization:** every text is trimmed, and optional links left blank are stored as `null`.
- **Read-only fields on update:** `slug` and `imageUrl` are never changed by an update. Unknown JSON properties are ignored, as Jackson does today.
- **Never zero published projects:** an update that hides the last published project, or a delete of it, answers 409 "At least one project must stay published" and changes nothing. This is checked under a row lock, so two tabs cannot both pass.
- **New projects:** placed last (`displayOrder = max + 1`, or 0 when there are none). The form's *Publicado* checkbox starts unchecked.
- **Body limit:** 64 KiB for `POST`/`PUT`/`PATCH` under `/api/admin/`, 16 KiB elsewhere under `/api/`. Chunked bodies still answer 411.
- **Admin responses:** `no-store`, with problem JSON on errors.
- **Branch:** `feat/part-8c-projects-editor`, which already carries the design commit.

## Review Focus

1. **Hiding or deleting the last published project**, including from two tabs at once. Expect 409. The public `GET /api/projects` must never come back empty. Tests `keepsAtLeastOnePublishedProject` (Task 1) and `explainsTheLastPublishedRule` (Task 2).
2. **A body that tries to change the slug or image on update:** `{"slug":"other","imageUrl":"javascript:x",…}`. Both are ignored, and the stored values stay as they were. Test `updateIgnoresSlugAndImage` (Task 1).
3. **Long bilingual descriptions with accents or emoji:** two 4000-character descriptions of `"ñ"` (about 16 KB) must save, not answer 413. Test `acceptsMaximumLengthDescriptions` (Task 1).
4. **Technologies typed loosely:** input `" Java, ,java ,Spring "` is refused client-side as a duplicate. A blank entry is dropped by the client. The server refuses duplicates and blank entries with 400. Tests `rejectsBadTechnologies` (Task 1) and `parsesTechnologies` (Task 3).
5. **Leaving the edit page with unsaved changes, or opening `editar/?id=999999`:** leaving asks for confirmation. An unknown id shows "Proyecto no encontrado." with a link back to the list. Tests `confirmsBeforeLeaving` and `explainsAnUnknownProject` (Task 3).

---

### Task 1: Admin projects API

**Files:**
- Move: `backend/src/main/java/dev/elianhidalgo/portfolio/profile/web/SafeLink.java` → `backend/src/main/java/dev/elianhidalgo/portfolio/shared/SafeLink.java` (make it `public`).
- Create: `backend/src/main/java/dev/elianhidalgo/portfolio/shared/TextRules.java` — `public static final String NO_CONTROL`, `NO_CONTROL_EXCEPT_WHITESPACE`.
- Modify: `ProfileRequest` and `AdminSkillsController`, which now use them; the behaviour is unchanged.
- Modify: `backend/src/main/java/dev/elianhidalgo/portfolio/projects/domain/Project.java`:
  - the constructor `Project(String slug, ProjectChanges changes, int displayOrder, Instant now)`;
  - `getId()`, `update(ProjectChanges)`, `placeAt(int)`.
- Create: `…/projects/domain/ProjectChanges.java` — a record of title, summary and description in ES/EN, `List<String> technologies`, `repoUrl`, `demoUrl`, `featured` and `published`.
- Modify: `…/projects/domain/ProjectRepository.java`:
  - `boolean existsBySlug(String)`;
  - `Optional<Project> findFirstByOrderByDisplayOrderDesc()`;
  - `List<Project> findAllByOrderByDisplayOrderAscSlugAsc()`;
  - `@Lock(PESSIMISTIC_WRITE) List<Project> findAllByPublishedTrue()`.
- Create: `…/projects/application/ProjectEditor.java`:
  - `List<Project> list()`, `Project get(long id)`;
  - `Project create(String slug, ProjectChanges)`, `Project update(long id, ProjectChanges)`;
  - `void delete(long id)`, `List<Project> reorder(List<Long> ids)`.
- Create: `…/projects/application/DuplicateSlugException.java`, `LastPublishedProjectException.java`, `InvalidProjectOrderException.java` — plain `RuntimeException`s. The existing `ProjectNotFoundException` is reused for unknown ids.
- Create: `…/projects/web/AdminProjectsController.java`, `ProjectRequest.java` (with nested `CreateProjectRequest` or `slug` on a separate record) and `ProjectsWebAdvice.java` (`HIGHEST_PRECEDENCE`, scoped to `AdminProjectsController`, same shape as `ProfileWebAdvice`).
- Modify: `backend/src/main/java/dev/elianhidalgo/portfolio/shared/web/RequestSizeLimitFilter.java` — `MAX_ADMIN_BODY_BYTES = 65_536` for URIs starting with `/api/admin/`.
- Test: `backend/src/test/java/dev/elianhidalgo/portfolio/projects/AdminProjectsApiTests.java`.
  - Use `TestTokens.valid()`.
  - Restore the seeded projects after each test through owner JDBC, as `ProfileSnapshot` does: snapshot the `projects.project` rows in `@BeforeEach` and restore them in `@AfterEach`.
- Test: `backend/src/test/java/dev/elianhidalgo/portfolio/shared/CorsAndRequestSizeTests.java` — new cases.

**Interfaces:**
- **Produces**, with `AdminProject` = `{"id","slug","title":{es,en},"summary":{es,en},"description":{es,en},"technologies":[…],"repoUrl","demoUrl","imageUrl","featured","published","displayOrder"}`:
  - `GET /api/admin/projects` → `AdminProject[]` in display order.
  - `GET /api/admin/projects/{id}` → `AdminProject`.
  - `POST /api/admin/projects` with `{"slug", title, summary, description, technologies, repoUrl, demoUrl, featured, published}` → `201 AdminProject`.
  - `PUT /api/admin/projects/{id}` with the same body without `slug` → `200 AdminProject`.
  - `DELETE /api/admin/projects/{id}` → `204`.
  - `PUT /api/admin/projects/order` with `{"ids":[…all]}` → `200 AdminProject[]`; `display_order` becomes the index.
- **Errors:**
  - Unknown id → 404 "Project not found".
  - Duplicate slug → 409 "A project with that slug already exists". Checked first; the unique-constraint violation also maps to 409.
  - Last published → 409 "At least one project must stay published".
  - Partial or stale order → 400 "The order must list every project exactly once".
  - Invalid body or non-numeric id → 400.

- [ ] **Step 1: Write the failing tests.**

```java
// AdminProjectsApiTests
@Test void requiresAdminToken()                  // GET, POST, PUT, DELETE, PUT order without token → 401
@Test void listsAllProjectsIncludingHidden()     // hide gamevault via PUT → GET admin list still has 3, public /api/projects has 2
@Test void createsAHiddenProjectAtTheEnd()       // POST slug "new-thing", published false → 201, displayOrder = max + 1; public list unchanged; GET /api/projects/new-thing → 404
@Test void duplicateSlugIs409()                  // POST slug "portfolio" → 409 problem JSON
@Test void rejectsBadSlugs()                     // "New Thing", "-a", "a--b", "a/b", 81 chars → 400 naming slug
@Test void updatesEveryEditableField()           // PUT → 200; GET /api/projects/{slug} shows the new title, technologies in sent order, demoUrl
@Test void updateIgnoresSlugAndImage()           // PUT with "slug":"other","imageUrl":"javascript:x" → 200; slug and imageUrl unchanged in DB
@Test void blankLinksBecomeNull()                // repoUrl "  " → null
@Test void rejectsUnsafeLinks()                  // repoUrl "javascript:alert(1)", demoUrl "http://x", "//evil.example" → 400
@Test void rejectsMissingOrTooLongText()         // title.es "" → 400; summary.en 301 chars → 400; description.es 4001 → 400
@Test void acceptsMaximumLengthDescriptions()    // description.es and .en = "ñ".repeat(4000) → 200
@Test void rejectsBadTechnologies()              // ["Java","java"] → 400; ["Java"," "] → 400; 21 entries → 400; one of 41 chars → 400
@Test void keepsAtLeastOnePublishedProject()     // hide all but one → OK; hiding/deleting the last → 409, still published; deleting a hidden one → 204
@Test void deletesAProject()                     // DELETE hidden project → 204; GET by id → 404
@Test void unknownProjectIs404()                 // GET, PUT, DELETE /999999 → 404 "Project not found"
@Test void reordersAllProjects()                 // reversed ids → 200; public list follows
@Test void reorderRejectsAPartialList()          // missing, duplicated or unknown id → 400; order unchanged
// CorsAndRequestSizeTests
@Test void adminBodiesMayReach64KiB()            // PUT /api/admin/projects/1 with a 40 KiB body → not 413 (400 or 401 is fine); 65 KiB → 413
@Test void publicBodiesStayAt16KiB()             // POST /api/contact 17 KiB → 413 (existing behaviour kept)
```

- [ ] **Step 2: Run the tests and confirm they fail.** Run `cd backend && ./mvnw -B -q test -Dtest='AdminProjectsApiTests,CorsAndRequestSizeTests'`. Expected: FAIL with 404 on the new routes.
- [ ] **Step 3: Move `SafeLink` and add `TextRules`.** Run `AdminProfileApiTests` and `AdminSkillsApiTests`. Expected: still green.
- [ ] **Step 4: Implement the domain methods, the editor, the exceptions, the controller with its advice, and the size filter.**
  - **Last-published rule:** in `update` (when `published` turns false) and in `delete` (when the project is published), lock with `findAllByPublishedTrue()`. If the project is the only row returned, throw `LastPublishedProjectException`.
  - **Technology checks:** run in `ProjectRequest`, with `@AssertTrue` methods or a compact constructor that strips each entry.
- [ ] **Step 5: Run the full backend suite and confirm it passes.** Run `./mvnw -B -q verify`. Expected: BUILD SUCCESS; `ModularityTests` green.
- [ ] **Step 6: Commit** with `feat(projects): admin API to create, edit, reorder and delete projects`.

---

### Task 2: Projects list page

**Files:**
- Modify: `frontend/src/app/admin/api/admin-api.ts`:
  - `projects(): Observable<AdminProject[]>`, `project(id: number): Observable<AdminProject>`;
  - `createProject(body: ProjectCreate): Observable<AdminProject>`, `updateProject(id: number, body: ProjectUpdate): Observable<AdminProject>`;
  - `deleteProject(id: number): Observable<void>`, `reorderProjects(ids: number[]): Observable<AdminProject[]>`.
- Modify: `frontend/src/app/admin/api/admin-models.ts` — `AdminProject`, `ProjectUpdate`, and `ProjectCreate` (= `ProjectUpdate & { slug: string }`). Texts use the existing `LocalizedText`.
- Create: `frontend/src/app/admin/projects/projects-page.{ts,html,scss}` (spec).
- Modify: `frontend/src/app/admin/admin.routes.ts` — the child `proyectos` → `ProjectsPage` ("Proyectos | Portafolio").
- Modify: `admin-layout.html` — nav `Inicio · Perfil · Skills · Proyectos · Mensajes`.
- Modify: `frontend/scripts/copy-root.mjs` (+ `copy-root.test.mjs`) — shells `es/admin/proyectos/` and `es/admin/proyectos/editar/`, and the header comment lists them.

**Behaviour:**
- The page shows the notice "Los cambios se verán en el sitio cuando pulses **Publicar** en Inicio.", as on Perfil and Skills.
- Each row shows:
  - the title (ES), the slug in muted text, and the badge *Publicado* or *Oculto*;
  - **↑** / **↓**, which send the full id list; the first row's ↑ and the last row's ↓ are disabled;
  - **Editar**, a link to `editar/?id=<id>`;
  - **Eliminar**, with `window.confirm('¿Eliminar este proyecto? No se puede deshacer.')`.
- A **Nuevo proyecto** link goes to `editar/`.
- A row's buttons wait while its request is in flight.
- After a reorder 400, the list reloads and shows "La lista cambió en otra pestaña. Se recargó."

**Copy:**
- Delete 409 → "No puedes ocultar ni eliminar el último proyecto publicado."
- Empty list → "Todavía no hay proyectos."
- Other failure → "No se pudo guardar. Inténtalo de nuevo."

- [ ] **Step 1: Write the failing tests.**

```ts
// projects-page.spec
it('lists projects with their state')              // flush 3 → titles in order; "Oculto" badge on a hidden one
it('moves a project down')                         // ↓ on first → PUT order [2,1,3]; last row ↓ disabled
it('reloads after a stale reorder')                // flush 400 → GET again + notice
it('deletes after confirmation only')              // confirm false → nothing; true → DELETE, row gone
it('explainsTheLastPublishedRule')                 // DELETE flush 409 → "No puedes ocultar ni eliminar el último proyecto publicado."
// copy-root.test: proyectos and proyectos/editar shells exist and carry the API origin
```

- [ ] **Step 2: Run the tests and confirm they fail.** Run `cd frontend && npx ng test --watch=false && npm run test:scripts`.
- [ ] **Step 3: Implement the API methods, models, page, route, navigation and shells.** Reuse `.admin-page__title`, `.button`, design tokens and the skills-page row styles where they fit.
- [ ] **Step 4: Run the tests and confirm they pass.**
- [ ] **Step 5: Commit** with `feat(frontend): admin projects list`.

---

### Task 3: Project edit page

**Files:**
- Create: `frontend/src/app/admin/projects/project-edit-page.{ts,html,scss}` (spec).
- Modify: `frontend/src/app/admin/admin.routes.ts` — the child `proyectos/editar` → `ProjectEditPage` ("Proyecto | Portafolio", `canDeactivate: [unsavedChangesGuard]`). It reads the `id` query parameter.

**Behaviour:**
- **Modes:**
  - Without `id`: create mode. The title is "Nuevo proyecto", the slug input is editable and *Publicado* starts unchecked.
  - With `id`: edit mode. The page loads the project with `project(id)`; the slug shows as read-only text (`/es/projects/<slug>`).
- **Fields:** ES and EN side by side, stacked under 640 px, as on Perfil.
  - Title, summary and description, with character counters on summary and description.
  - Technologies as one text input "Separadas por comas", parsed by `parseTechnologies(text: string): string[]`, which splits on `,`, trims and drops empty entries.
  - Repository URL, demo URL, *Destacado* and *Publicado*.
- **Client-side checks** mirror the backend table:
  - slug pattern hint: "Solo minúsculas, números y guiones (p. ej. mi-proyecto)";
  - link hint: "Debe empezar con https://";
  - duplicate technology: "Tecnología repetida: <name>".
- **After saving:**
  - Create success → navigate to `/es/admin/proyectos/` with the notice "Proyecto creado. Está oculto hasta que marques Publicado." (passed through navigation state, or a query flag the list reads once).
  - Update success → "Proyecto guardado." The form becomes pristine.
- **Errors:**
  - Create 409 → beside the slug: "Ya existe un proyecto con esa URL."
  - Update 409 → beside *Publicado*: "No puedes ocultar el último proyecto publicado."
  - 400 → "Revisa los campos marcados."
  - 413 → "El texto es demasiado largo."
  - Load 404 → "Proyecto no encontrado." with a link "Volver a proyectos".
  - Other failures → "No se pudo guardar. Inténtalo de nuevo."

- [ ] **Step 1: Write the failing tests.**

```ts
// project-edit-page.spec
it('parsesTechnologies')                            // " Java, ,Spring Boot ,," → ["Java","Spring Boot"]
it('creates a project')                             // no id → slug editable; fill + save → POST body has slug, published false, technologies array; navigates to list
it('explainsADuplicateSlug')                        // POST flush 409 → message beside slug
it('blocks a bad slug and a duplicate technology')  // slug "Mi Proyecto" → hint, no request; "Java, java" → "Tecnología repetida: java", no request
it('loads and updates a project')                   // id=1 → form filled, slug read-only; edit title.en → PUT body without slug; "Proyecto guardado."
it('explainsTheLastPublishedRuleOnUpdate')          // PUT flush 409 → message beside Publicado
it('explainsAnUnknownProject')                      // GET flush 404 → "Proyecto no encontrado." + link
it('confirmsBeforeLeaving')                         // dirty → hasUnsavedChanges() true; after save → false
```

- [ ] **Step 2: Run the tests and confirm they fail.** Run `npx ng test --watch=false`.
- [ ] **Step 3: Implement the page.**
- [ ] **Step 4: Run every frontend check and confirm it passes.** Run `npx ng test --watch=false && npm run test:scripts && node scripts/check-translations.mjs`, then `npm run build` against the local backend. Expected: all pass, `Prerender verified.`
- [ ] **Step 5: Check end to end, locally.**
  - **Setup:** `serve-dist.cjs` on :4200, plus the dev backend with the dev `ADMIN_*` values.
  - **Flow, with Playwright:**
    1. Sign in → Proyectos → Nuevo proyecto. Create `demo-project`, hidden. It appears last with *Oculto*.
    2. Edit it: mark it Publicado, change title.en, save, reload — the values are kept. Leave with unsaved changes — a confirm is shown.
    3. Move it up. Try to hide every project — the last one is refused. Delete `demo-project`.
    4. Check that `GET /api/projects` reflects each step. Rebuild the site: the public projects section follows the new order.
  - **Pass criteria:** 0 CSP violations.
  - **Screenshots:** list and edit page at 375 px and 1280 px (see the visual design memory).
- [ ] **Step 6: Commit** with `feat(frontend): admin project edit page`.

---

### Task 4: Docs

**Files:**
- Modify: `README.md` — admin API rows for projects, and a note that images are still changed in `frontend/public/` plus a migration (editor scope).
- Modify: `docs/superpowers/specs/2026-11-14-portafolio-design.md` (rev 10) — 8c done; part 8 complete.

- [ ] **Step 1: Update the docs and run the full suites.** Run `cd backend && ./mvnw -B -q verify` and `cd frontend && npx ng test --watch=false && npm run test:scripts`. Expected: all green.
- [ ] **Step 2: Commit** with `docs: admin projects editor (spec rev 10)`.

Push, merge and production verification belong to the controller after the final review and the owner's approval:
- **Anonymous checks:** the new admin routes answer 401; a `PUT` preflight from the site answers 200, and from another origin 403; reloading `/es/admin/proyectos/` and `/es/admin/proyectos/editar/` answers 200.
- **Owner check:** the owner signs in, edits a project, presses Publicar and sees the change after the build.
- Do not probe the login from the user's IP.

# Part 8b — Admin Profile and Skills Editor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** From `/es/admin/`, the admin can:
- edit the profile (name, headline and about in Spanish and English, location, email, GitHub, LinkedIn, avatar path, CV paths);
- add, rename, recategorize, delete and reorder skills;

and is reminded that the public site changes only after **Publicar** (part 8a).

Second of three slices of spec step 8 (8a inbox and publishing — done; 8b this; 8c projects editor).

**Scope decision (user, 2026-11-18):** text and link fields only. No file uploads and no new storage: avatar and CV files keep living in `frontend/public/`, and the editor only changes the paths that point at them.

**Architecture:**
- **Backend:** `profile` gains admin endpoints under `/api/admin/profile` and `/api/admin/skills`, protected by the existing `/api/admin/**` rule. No migration: the tables already have every column, and `portfolio_app` already has `select, insert, update, delete` on `profile.*` and the skill sequence (V7).
- **Shared web:** `SiteCorsFilter` allows `PUT`; `RequestSizeLimitFilter` covers `PUT` and `PATCH` as well as `POST` (8a deferred minor).
- **Frontend:** two new admin pages, **Perfil** and **Skills**, in the admin navigation, with a leave-without-saving confirmation on the profile form.

**Tech Stack:** Spring Boot 4.1.2, Spring Modulith, Bean Validation, Testcontainers; Angular 22 (signals, reactive forms), Vitest.

**Spec:** `docs/superpowers/specs/2026-11-14-portafolio-design.md` (§5.3 `profile` row: `PUT /api/admin/profile`, CRUD `/api/admin/skills`; fixed skill categories; §5.7 admin panel; §6 step 8b)

## Global Constraints

- All code, identifiers, comments, commits and docs are in English. The admin UI copy is Spanish only, plain text without `i18n` markers.
- Zero cost: no new services, no storage, no uploads.
- Never edit applied migrations (V1–V11). This part needs none.
- Skill categories are the fixed set `BACKEND, FRONTEND, DATABASE, DEVOPS, TOOLS`, shown as `Backend, Frontend, Base de datos, DevOps, Herramientas`.
- Field limits (match the columns; enforced by Bean Validation, 400 problem JSON listing the field):

| Field | Rule |
|---|---|
| `fullName` | required, ≤ 100 |
| `headline.es`, `headline.en` | required, ≤ 160 |
| `about.es`, `about.en` | required, ≤ 2500 (keeps the whole body under the 16 KiB request limit) |
| `location` | optional, ≤ 100 |
| `email` | optional, ≤ 254, valid address |
| `githubUrl`, `linkedinUrl` | optional, ≤ 500, must start with `https://` |
| `avatarUrl`, `cvUrl.es`, `cvUrl.en` | optional, ≤ 500, either `https://…` or a relative path matching `^[A-Za-z0-9][A-Za-z0-9._/-]*$` without `..` |
| skill `name` | required, ≤ 60, unique (case-insensitive) |

- Optional fields: blank or whitespace is stored as `null`. Every text is trimmed.
- The admin profile body uses the same shape as the public `GET /api/profile` (`headline`/`about`/`cvUrl` as `{es, en}`), so the frontend reuses `LocalizedText`.
- Admin responses are `no-store` and problem JSON on errors (already true for every non-public path).
- Branch: `feat/part-8b-profile-skills-editor` from `main`.

## Review Focus

1. **A `javascript:` or `data:` link, or `//evil.example` as a path:** rejected with 400 before it can reach the public site's `href`/`src`. Test `rejectsUnsafeLinksAndPaths` in Task 1.
2. **Duplicate skill name, including different case ("Java" vs "java"):** 409 problem JSON "A skill with that name already exists", and the UI says so next to the field. Tests `duplicateSkillNameIs409` (Task 1) and `explainsADuplicateName` (Task 3).
3. **Reorder with a stale or partial list** (a skill was added or deleted in another tab): 400 "The order must list every skill exactly once"; nothing changes; the UI reloads the list. Test `reorderRejectsAPartialList` in Task 1.
4. **Leaving the profile page with unsaved changes:** the browser asks for confirmation; after a successful save it does not. Test `confirmsBeforeLeavingWithUnsavedChanges` in Task 2.
5. **A body over 16 KiB or a chunked `PUT`/`PATCH`:** 413 / 411 like `POST`; the profile form shows "El texto es demasiado largo." Tests `putAndPatchBodiesAreLimited` (Task 1) and `explainsTooLarge` (Task 2).

---

### Task 1: Admin profile and skills API

**Files:**
- Modify: `backend/src/main/java/dev/elianhidalgo/portfolio/profile/domain/Profile.java` — `update(ProfileChanges changes, Instant now)`.
- Create: `backend/src/main/java/dev/elianhidalgo/portfolio/profile/domain/ProfileChanges.java` — record of all editable fields (already trimmed / nulled).
- Modify: `backend/src/main/java/dev/elianhidalgo/portfolio/profile/domain/Skill.java` — public constructor `Skill(String name, SkillCategory category, int displayOrder)`, `getId()`, `rename(String)`, `moveTo(SkillCategory)`, `placeAt(int)`.
- Modify: `backend/src/main/java/dev/elianhidalgo/portfolio/profile/domain/SkillRepository.java` — `boolean existsByNameIgnoreCase(String)`, `boolean existsByNameIgnoreCaseAndIdNot(String, Long)`, `Optional<Skill> findFirstByOrderByDisplayOrderDesc()`.
- Create: `backend/src/main/java/dev/elianhidalgo/portfolio/profile/application/ProfileEditor.java` — `ProfileWithSkills update(ProfileChanges)`.
- Create: `backend/src/main/java/dev/elianhidalgo/portfolio/profile/application/SkillEditor.java` — `Skill create(String name, SkillCategory)`, `Skill update(long id, String name, SkillCategory)`, `void delete(long id)`, `List<Skill> reorder(List<Long> ids)`.
- Create: `…/profile/application/SkillNotFoundException.java`, `DuplicateSkillNameException.java`, `InvalidSkillOrderException.java` — plain `RuntimeException`s.
- Create: `backend/src/main/java/dev/elianhidalgo/portfolio/profile/web/AdminProfileController.java` and `AdminSkillsController.java`, with request records and a link validator (`SafeLink` constraint) in `profile.web`.
- Modify: `backend/src/main/java/dev/elianhidalgo/portfolio/shared/web/SiteCorsFilter.java` — methods `GET, POST, PUT, PATCH, DELETE`.
- Modify: `backend/src/main/java/dev/elianhidalgo/portfolio/shared/web/RequestSizeLimitFilter.java` — apply to `POST`, `PUT`, `PATCH` under `/api/`.
- Test: `backend/src/test/java/dev/elianhidalgo/portfolio/profile/AdminProfileApiTests.java`, `AdminSkillsApiTests.java` (use `TestTokens.valid()`; restore the seeded profile after each test, e.g. `@Transactional` owner-side reset or re-running the V4/V10 values).
- Test: `backend/src/test/java/dev/elianhidalgo/portfolio/shared/CorsAndRequestSizeTests.java` — new cases.

**Interfaces:**
- Produces:
  - `GET /api/admin/profile` → same JSON as `GET /api/profile` (profile and skills).
  - `PUT /api/admin/profile` with `{"fullName","headline":{"es","en"},"about":{"es","en"},"location","email","githubUrl","linkedinUrl","avatarUrl","cvUrl":{"es","en"}}` → `200` with the saved profile in the public shape.
  - `POST /api/admin/skills` `{"name","category"}` → `201 {"id","name","category","displayOrder"}`, placed last.
  - `PUT /api/admin/skills/{id}` `{"name","category"}` → `200` skill.
  - `DELETE /api/admin/skills/{id}` → `204`.
  - `PUT /api/admin/skills/order` `{"ids":[…]}` → `200` list of skills in the new order; `display_order` becomes the index (0, 1, 2…).
  - Errors: unknown id 404 "Skill not found"; duplicate name 409; invalid body 400; non-numeric id 400.
  - The public `SkillResponse` gains `id` (needed by the admin; harmless publicly).

- [ ] **Step 1: Write the failing tests.**

```java
// AdminProfileApiTests
@Test void requiresAdminToken()                   // GET and PUT without token → 401
@Test void readsTheCurrentProfile()               // GET → fullName "Victor Hidalgo", skills present
@Test void updatesEveryFieldAndTrims()            // PUT with "  Ana  " → 200, fullName "Ana"; GET /api/profile shows the change; updatedAt moved
@Test void blankOptionalFieldsBecomeNull()        // location "  " → null in the response
@Test void rejectsMissingOrTooLongText()          // headline.es "" → 400; about.en 2501 chars → 400; detail names the field
@Test void rejectsUnsafeLinksAndPaths()           // githubUrl "javascript:alert(1)", avatarUrl "//evil.example/a.png", cvUrl.es "../x.pdf", linkedinUrl "http://…" → 400 each
@Test void acceptsRelativePathsAndHttps()          // cvUrl.es "cv/cv-es.pdf", avatarUrl "https://example.com/a.jpg" → 200
// AdminSkillsApiTests
@Test void createsASkillAtTheEnd()                // POST {"name":"Kotlin","category":"BACKEND"} → 201, displayOrder = max + 1
@Test void updatesNameAndCategory()               // PUT → 200; GET /api/profile shows it
@Test void duplicateSkillNameIs409()              // POST existing name in other case → 409 problem JSON; PUT renaming onto another → 409; PUT keeping its own name → 200
@Test void rejectsUnknownCategory()               // "category":"COOKING" → 400
@Test void deletesASkill()                        // DELETE → 204; gone
@Test void unknownSkillIs404()                    // PUT and DELETE /999999 → 404 "Skill not found"
@Test void reordersAllSkills()                    // PUT order reversed → 200; public list follows the new order
@Test void reorderRejectsAPartialList()           // missing one id, duplicated id, or unknown id → 400; order unchanged
// CorsAndRequestSizeTests
@Test void corsAllowsPutFromTheSite()             // preflight PUT → 200
@Test void putAndPatchBodiesAreLimited()          // PUT 17 KiB → 413; chunked PATCH → 411
```

- [ ] **Step 2: Run the tests and confirm they fail.** Run `cd backend && ./mvnw -B -q test -Dtest='AdminProfileApiTests,AdminSkillsApiTests,CorsAndRequestSizeTests'`. Expected: FAIL with 404/403 on the new routes.
- [ ] **Step 3: Implement the domain methods, the editors, the exceptions (mapped to problem JSON as in `contact`), the controllers, CORS and the size filter.** Duplicate detection checks the repository first and also maps the DB unique-constraint violation to 409 (two tabs racing).
- [ ] **Step 4: Run the full backend suite and confirm it passes.** Run `./mvnw -B -q verify`. Expected: BUILD SUCCESS; `ModularityTests` green.
- [ ] **Step 5: Commit** with `feat(profile): admin API to edit the profile and manage skills`.

---

### Task 2: Profile editor page

**Files:**
- Modify: `frontend/src/app/admin/api/admin-api.ts` — `profile(): Observable<Profile>`, `saveProfile(body: ProfileUpdate): Observable<Profile>`, plus the skill calls used by Task 3: `createSkill(name, category): Observable<Skill>`, `updateSkill(id, name, category): Observable<Skill>`, `deleteSkill(id): Observable<void>`, `reorderSkills(ids: number[]): Observable<Skill[]>`.
- Modify: `frontend/src/app/admin/api/admin-models.ts` — `ProfileUpdate`; `frontend/src/app/core/models/profile.ts` — `Skill.id: number`.
- Create: `frontend/src/app/admin/profile/profile-page.{ts,html,scss}` (spec) — reactive form; Spanish and English fields side by side (stacked under 640 px); character counters on headline and about; client-side limits and link rules mirroring the backend table.
- Create: `frontend/src/app/admin/unsaved-changes.guard.ts` (spec) — `canDeactivate` asking `window.confirm('Tienes cambios sin guardar. ¿Salir de todos modos?')` when the component reports `hasUnsavedChanges()`; also `beforeunload` while dirty.
- Modify: `frontend/src/app/admin/admin.routes.ts` — child `perfil` → `ProfilePage` ("Perfil | Portafolio", with the guard); `admin-layout.html` — nav `Inicio · Perfil · Skills · Mensajes`.
- Modify: `frontend/scripts/copy-root.mjs` (+ `copy-root.test.mjs`) — shells `es/admin/perfil/` and `es/admin/skills/`.

Copy (Spanish only):

| Case | Text |
|---|---|
| Notice at the top of Perfil and Skills | Los cambios se verán en el sitio cuando pulses **Publicar** en Inicio. |
| Saved | Perfil guardado. |
| 400 | Revisa los campos marcados. (field messages next to each field) |
| 413 | El texto es demasiado largo. |
| Other failure | No se pudo guardar. Inténtalo de nuevo. |
| Link rule hint | Debe empezar con https:// |
| Path rule hint | Ruta dentro del sitio (p. ej. cv/cv-es.pdf) o enlace https:// |

- [ ] **Step 1: Write the failing tests.**

```ts
// profile-page.spec
it('loads the profile into the form')                 // flush GET → fullName input "Victor Hidalgo", about.en filled
it('saves and shows the confirmation')                 // edit headline.es → PUT body has headline.es; flush 200 → "Perfil guardado."
it('blocks unsafe links before sending')               // githubUrl "javascript:x" → error hint, no request
it('maps a 400 problem to field errors')               // flush 400 → "Revisa los campos marcados."
it('explainsTooLarge')                                 // flush 413 → "El texto es demasiado largo."
it('confirmsBeforeLeavingWithUnsavedChanges')          // dirty → guard calls confirm; after save → no confirm
// copy-root.test: perfil and skills shells exist and carry the API origin
```

- [ ] **Step 2: Run the tests and confirm they fail.** Run `cd frontend && npx ng test --watch=false && npm run test:scripts`. Expected: the new specs fail.
- [ ] **Step 3: Implement the API methods, the models, the page, the guard, routes, navigation and shells.** Reuse `.admin-form`, `.button`, design tokens.
- [ ] **Step 4: Run the tests and confirm they pass.**
- [ ] **Step 5: Commit** with `feat(frontend): admin profile editor`.

---

### Task 3: Skills page

**Files:**
- Create: `frontend/src/app/admin/skills/skills-page.{ts,html,scss}` (spec).
- Modify: `frontend/src/app/admin/admin.routes.ts` — child `skills` → `SkillsPage` ("Skills | Portafolio").

Behaviour:
- Skills grouped by category in the fixed order, each group ordered by `displayOrder`.
- Each row: name, category select, **Guardar** (enabled when changed), **↑** / **↓** (move within its category; sends the full id list to `reorderSkills`), **Eliminar** (`window.confirm('¿Eliminar esta skill?')`).
- A form at the top: name + category + **Agregar**.
- Buttons of a row wait while its request is in flight. On a reorder 400 the list reloads and shows "La lista cambió en otra pestaña. Se recargó."

Copy: duplicate → "Ya existe una skill con ese nombre."; empty group → "Sin skills en esta categoría."; other failure → "No se pudo guardar. Inténtalo de nuevo."

- [ ] **Step 1: Write the failing tests.**

```ts
it('groups skills by category in the fixed order')     // Backend before Frontend; labels in Spanish
it('adds a skill')                                     // POST {name:"Kotlin",category:"BACKEND"} → appears last in Backend
it('explainsADuplicateName')                           // flush 409 → "Ya existe una skill con ese nombre."
it('renames and recategorizes')                        // PUT body; row moves to the new group
it('moves a skill up within its category')             // ↑ → PUT order with the swapped ids; first row has ↑ disabled
it('reloads after a stale reorder')                    // flush 400 → GET again + notice
it('deletes after confirmation only')                  // confirm false → nothing; true → DELETE
```

- [ ] **Step 2: Run the tests and confirm they fail.** Run `npx ng test --watch=false`.
- [ ] **Step 3: Implement the page.**
- [ ] **Step 4: Run every frontend check and confirm it passes.** Run `npx ng test --watch=false && npm run test:scripts && node scripts/check-translations.mjs`, then `npm run build` against the local backend. Expected: all pass, `Prerender verified.`
- [ ] **Step 5: Check end to end, locally.** `serve-dist.cjs` on :4200 + dev backend with dev `ADMIN_*`. With Playwright: sign in → Perfil: change headline.es, save, reload, value kept; leave dirty → confirm shown → Skills: add, rename, move, delete → `GET /api/profile` reflects each step → rebuild the site and the public page shows the new headline → 0 CSP violations. Screenshots at 375 px and 1280 px (see visual design memory).
- [ ] **Step 6: Commit** with `feat(frontend): admin skills page`.

---

### Task 4: Docs and production verification

**Files:**
- Modify: `README.md` — admin API rows for profile and skills; how to change the CV or avatar (replace the file in `frontend/public/`, or point the path at an `https://` link).
- Modify: `docs/superpowers/specs/2026-11-14-portafolio-design.md` (rev 8) — 8b done: text-only editor, link rules, no uploads by decision.

- [ ] **Step 1: Run the full suites, commit, push the branch, wait for CI to pass.**
- [ ] **Step 2: Merge to `main` after the user approves.**
- [ ] **Step 3: Verify in production.** Anonymous: new admin routes answer 401; preflight `PUT` from the site 200, from another origin 403; reloading `/es/admin/perfil/` and `/es/admin/skills/` answers 200. The user, signed in: edits a field, saves, presses Publicar, and sees the change on the public site after the build. Do not probe the login from the user's IP.

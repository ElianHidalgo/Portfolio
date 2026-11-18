# Part 8c — Projects editor (design)

Approved by the owner on 2026-11-18. Parent spec: `2026-11-14-portafolio-design.md` (rev 9).

## Intent

The owner wants to create, edit, hide, reorder and delete portfolio projects from the admin panel, in Spanish and English, without writing Flyway migrations. As with part 8b, changes reach the public site only after pressing **Publicar**.

## Goals

- A projects list in the panel showing every project, published or hidden.
- A bilingual edit page for one project.
- Create, update, delete and reorder through an admin API in the `projects` module.

## Non-goals

- **Images.** The editor neither shows nor changes `image_url`. An existing value is kept as is. The owner has another plan for images later.
- Changing a project's slug after creation (see Approaches).
- Redirects from old slugs.
- File uploads or any new storage.

## Anti-goals

- **A hidden or deleted last project.** The public build fails with zero published projects, so the panel must never allow this.
- **A dead link.** A link the owner already shared must never break because of an edit.

## Approaches

- **Chosen: list plus a separate edit page, mirroring 8b.** Descriptions are long and bilingual, so editing every project inline in one list, as the Skills page does, would be unwieldy.
- **Slug, option A (chosen by the owner):** the slug is set once, at creation, and is read-only afterwards. This keeps shared links stable. To change a slug, the owner creates a new project.
- **Slug, option B (rejected):** an editable slug with a warning. Old URLs would return 404 unless redirects were added.

## Behaviour

### Panel → Proyectos (`/es/admin/proyectos/`)

- Every project is listed in display order.
- Each row shows the title (ES) and a *Publicado*/*Oculto* badge.
- Each row has ↑ ↓ buttons, *Editar*, and *Eliminar*. Eliminar asks for confirmation.
- A **Nuevo proyecto** button sits on the page.
- The nav order is: Inicio · Perfil · Skills · Proyectos · Mensajes.

### Edit page (`/es/admin/proyectos/editar/` and `/es/admin/proyectos/editar/?id=<id>`)

- The page is a static shell. Without `id` it creates a new project; with `id` it edits that project.
- Fields, with ES and EN side by side:
  - title, at most 120 characters;
  - summary, at most 300 characters;
  - description, at most 4000 characters.
- **Technologies:** one comma-separated input.
  - Each entry is trimmed.
  - Each entry is non-blank and at most 40 characters.
  - At most 20 entries.
  - No duplicates, compared case-insensitively.
  - Order is kept.
- **Repository URL and demo URL:** optional, `https` only, at most 500 characters.
- **Destacado** and **Publicado** checkboxes.
- **Slug:** editable only when creating.
  - Pattern `^[a-z0-9]+(-[a-z0-9]+)*$`, at most 80 characters.
  - A duplicate returns 409, shown beside the field.
  - When editing, the slug is shown read-only.
- The page warns before leaving with unsaved changes, using the existing `unsaved-changes.guard.ts`.

### Rules

- A new project is placed last in display order. The form's *Publicado* checkbox starts unchecked.
- **Last published project:** hiding it or deleting it is refused with 409 and a clear message. This is checked in the same transaction as the change.
- **Unknown id:** 404.
- **Reorder:** takes the full id list. A partial or stale list returns 400, exactly like skills.

## API (projects module, bearer-protected under `/api/admin/**`)

| Method | Path | Result |
|---|---|---|
| GET | `/api/admin/projects` | All projects, in display order |
| GET | `/api/admin/projects/{id}` | One project, with all fields including `slug` and `imageUrl` (read-only) |
| POST | `/api/admin/projects` | 201; body includes `slug` |
| PUT | `/api/admin/projects/{id}` | 200; body has no `slug`, and `slug` or `imageUrl` in the body is ignored |
| DELETE | `/api/admin/projects/{id}` | 204 |
| PUT | `/api/admin/projects/order` | `{"ids":[…all ids]}` |

Errors are problem JSON with field errors, the same shape as 8b. The frontend reuses the problem-detail → field-error mapping.

## Constraints

- **Request size (Claude's call).** The 16 KiB body limit is too small: two 4000-character descriptions with accents can exceed it. The limit becomes **64 KiB for `/api/admin/**`**, and public paths stay at 16 KiB.
- **No migration needed.** The schema already has every column, and the `portfolio_app` role already has DML on schema `projects`.
- **Public API unchanged.** It still filters `published = true`.
- **Zero cost.** Nothing new is billable.

## Left to the builder

- Package layout inside `projects`, mirroring `profile`.
- Exact Spanish copy for messages and the empty state.

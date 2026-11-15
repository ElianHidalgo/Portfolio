# Part 1 — Project Skeleton Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A monorepo where `docker compose up` from a clean clone starts PostgreSQL, a Spring Boot backend exposing `GET /api/health` with Flyway applied, and an Angular frontend built in English and Spanish, all behind nginx at `http://localhost:8080`.

**Architecture:** `backend/` is a Spring Boot 4.1 modular monolith (Spring Modulith verification test from day one, no business modules yet). `frontend/` is an Angular 22 app using `@angular/localize`, built once per locale into `/en/` and `/es/`. `docker-compose.yml` wires `db` → `backend` → `frontend` (nginx serving the static build and proxying `/api`).

**Tech Stack:** Java 17, Spring Boot 4.1.2, Spring Modulith 2.1.2, Spring Data JPA, Flyway, Actuator, PostgreSQL 17, Testcontainers, Maven 3.9; Angular 22.2.7 (standalone, zoneless, SCSS, Vitest), `@angular/localize`; Docker Compose, nginx.

**Spec:** `docs/superpowers/specs/2026-11-14-portafolio-design.md` (sections 5.2, 5.3, 5.4, 5.8 "Desarrollo local", 6 part 1).

## Global Constraints

- All code, identifiers, comments, commit messages, README and repo docs are in **English**.
- Java 17 (Temurin); Spring Boot **4.1.2** (latest stable on 2026-11-15); Spring Modulith **2.1.2** (line matching Boot 4.1). Versions pinned in `pom.xml`.
- Angular **22.2.7**, pinned in `package.json`; CLI used through `npx @angular/cli@22.2.7`, never a global install.
- Node must satisfy Angular 22's engines: `^22.22.3 || ^24.15.0 || >=26.0.0`.
- Root package: `dev.<github-user>.portfolio`. **The user supplies `<github-user>` before Task 2**; use it everywhere this plan writes `<github-user>`.
- Schema is owned by Flyway; `spring.jpa.hibernate.ddl-auto=validate`.
- Configuration comes from environment variables with local-friendly defaults; `.env.example` is versioned, `.env` is git-ignored. No secrets in the repo.
- Backend listens on `${PORT:8080}` (Cloud Run injects `PORT`).
- Source locale is `en` (templates written in English); `es` is a translation. The site's default language is Spanish: `/` redirects to `/es/`.
- `docker compose up` must work from a clean clone **without** a `.env` file.
- Commits use Conventional Commits and end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Clean clone with no `.env`** — `docker compose up` must still start everything (compose uses `${VAR:-default}` everywhere). Pinned in Task 5, Step 4.
2. **Backend starting before PostgreSQL accepts connections** — the backend must not crash-loop; `db` has a `pg_isready` healthcheck and `backend` waits on `service_healthy`. Pinned in Task 5, Step 4.
3. **Refreshing a deep link** such as `/es/projects/foo` in the browser — nginx must return that locale's `index.html` (200), not 404. Pinned in Task 5, Step 5.
4. **Windows line endings** breaking Linux containers (shell scripts, `mvnw`) — `.gitattributes` forces LF; Docker builds use the image's `mvn`, not `mvnw`. Pinned in Task 1, Step 3 and Task 3.
5. **Port 5432 already taken** by a locally installed PostgreSQL — the host port is `${DB_PORT:-5432}`, documented in the README. Pinned in Task 5, Step 2.

---

### Task 1: Prerequisites and repository

**Files:**
- Create: `.gitignore`, `.gitattributes`, `README.md` (stub)
- Existing: `docs/` (spec and this plan get committed)

**Interfaces:**
- Produces: a git repo on branch `main` that later tasks commit into.

- [ ] **Step 1: Upgrade Node** (the user runs this; it changes their machine)

Run: `winget upgrade OpenJS.NodeJS.LTS` (installs Node 24 LTS), then open a new terminal.
Verify: `node -v` prints `v24.15.0` or newer (or `v22.22.3`+).

- [ ] **Step 2: Start Docker Desktop**

Verify: `docker info --format '{{.ServerVersion}}'` prints a version (no "failed to connect").

- [ ] **Step 3: Init repo with ignore and attribute files**

`git init -b main`. `.gitignore` covers: `.env`, `backend/target/`, `frontend/node_modules/`, `frontend/dist/`, `frontend/.angular/`, IDE folders (`.idea/`, `.vscode/`), `*.log`.
`.gitattributes`:
```
* text=auto eol=lf
*.cmd text eol=crlf
*.bat text eol=crlf
*.jar binary
```
`README.md`: title "Portfolio" and one line "Work in progress — see docs/." (expanded in Task 5).

- [ ] **Step 4: Commit**

```bash
git add .gitignore .gitattributes README.md docs/
git commit -m "chore: initialize repository with design spec and part 1 plan"
```

---

### Task 2: Backend skeleton with health endpoint and Flyway

**Files:**
- Create (generated): `backend/` via Spring Initializr — `pom.xml`, `mvnw*`, `src/main/java/dev/<github-user>/portfolio/PortfolioApplication.java`, `src/test/java/.../TestcontainersConfiguration.java`
- Create: `backend/src/main/resources/application.yml` (replaces generated `application.properties`)
- Create: `backend/src/main/resources/db/migration/V1__baseline.sql`
- Test: `backend/src/test/java/dev/<github-user>/portfolio/HealthEndpointTests.java`
- Test: `backend/src/test/java/dev/<github-user>/portfolio/ModularityTests.java`

**Interfaces:**
- Produces: `GET /api/health` → `200 {"status":"UP"}` (used by Task 5 smoke test and later by the frontend wake-up ping). Env vars `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `PORT`.

- [ ] **Step 1: Generate the project**

```bash
curl https://start.spring.io/starter.zip -d type=maven-project -d language=java \
  -d bootVersion=4.1.2 -d javaVersion=17 -d groupId=dev.<github-user> \
  -d artifactId=portfolio -d name=portfolio -d packageName=dev.<github-user>.portfolio \
  -d dependencies=web,data-jpa,flyway,postgresql,actuator,validation,testcontainers,modulith \
  -o backend.zip
```
Unzip into `backend/`, delete `backend.zip`. Confirm `pom.xml` has Boot `4.1.2` and the Modulith BOM at `2.1.2` (set it if Initializr chose another 2.1.x). Ensure `flyway-database-postgresql` is a dependency.

- [ ] **Step 2: Write the failing tests**

```java
// HealthEndpointTests.java
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class HealthEndpointTests {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void healthEndpointIsPublicAndUp() throws Exception {
        mockMvc.perform(get("/api/health"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void baselineMigrationIsApplied() {
        Integer applied = jdbc.queryForObject(
            "select count(*) from flyway_schema_history where version = '1' and success", Integer.class);
        assertThat(applied).isEqualTo(1);
    }
}

// ModularityTests.java
class ModularityTests {
    @Test
    void moduleBoundariesAreRespected() {
        ApplicationModules.of(PortfolioApplication.class).verify();
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `cd backend; ./mvnw test`
Expected: `healthEndpointIsPublicAndUp` FAILS with 404 (health lives at `/actuator/health`); `baselineMigrationIsApplied` FAILS (no `flyway_schema_history` / no version 1). `ModularityTests` passes.

- [ ] **Step 4: Configure `application.yml` and add the baseline migration**

`application.yml` values:
- `spring.application.name: portfolio`
- `spring.datasource.url: ${DB_URL:jdbc:postgresql://localhost:5432/portfolio}`, `username: ${DB_USERNAME:portfolio}`, `password: ${DB_PASSWORD:portfolio}`
- `spring.jpa.hibernate.ddl-auto: validate`, `spring.jpa.open-in-view: false`
- `management.endpoints.web.base-path: /api`, `management.endpoints.web.exposure.include: health`
- `server.port: ${PORT:8080}`

`V1__baseline.sql`: a single SQL comment explaining it marks the schema baseline (business tables arrive with their modules).

- [ ] **Step 5: Run tests to verify they pass**

Run: `./mvnw test`
Expected: `Tests run: 3, Failures: 0, Errors: 0` and `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add backend/
git commit -m "feat(backend): add Spring Boot skeleton with health endpoint and Flyway baseline"
```

---

### Task 3: Backend container image

**Files:**
- Create: `backend/Dockerfile`, `backend/.dockerignore`

**Interfaces:**
- Consumes: Task 2's Maven project.
- Produces: an image that listens on `PORT` (default 8080) and reads `DB_*` env vars. Reused unchanged for Cloud Run in part 5.

- [ ] **Step 1: Write the multi-stage Dockerfile**

Build stage `maven:3.9-eclipse-temurin-17`: copy `pom.xml` first and run `mvn -B dependency:go-offline` (layer cache), then copy `src/` and run `mvn -B package -DskipTests` (tests run in CI/locally, they need Docker). Runtime stage `eclipse-temurin:17-jre`: run as a non-root user, copy the jar, `EXPOSE 8080`, `ENTRYPOINT ["java","-jar","/app/app.jar"]`.
`.dockerignore`: `target/`, `.mvn/wrapper/maven-wrapper.jar`, `*.iml`.

- [ ] **Step 2: Build it**

Run: `docker build -t portfolio-backend backend`
Expected: build ends with `naming to docker.io/library/portfolio-backend`.

- [ ] **Step 3: Commit**

```bash
git add backend/Dockerfile backend/.dockerignore
git commit -m "build(backend): add multi-stage Docker image"
```

---

### Task 4: Frontend skeleton with English and Spanish builds

**Files:**
- Create (generated): `frontend/` via Angular CLI — `src/app/app.ts`, `app.html`, `app.scss`, `app.spec.ts`, `app.config.ts`, `angular.json`, `package.json`
- Create: `frontend/src/locale/messages.es.xlf`
- Create: `frontend/proxy.conf.json`

**Interfaces:**
- Produces: `npx ng build --localize` writes `frontend/dist/frontend/browser/en/` and `.../es/`, each with its own `index.html` (`lang` and `<base href>` set per locale). `npm start` serves Spanish on port 4200 with `/api` proxied to `http://localhost:8080`.

- [ ] **Step 1: Generate the app and add localize**

```bash
npx @angular/cli@22.2.7 new frontend --style=scss --routing --ssr=false --skip-git --defaults
cd frontend
npx ng add @angular/localize --skip-confirmation
```

- [ ] **Step 2: Write the failing test** (replace generated `app.spec.ts` body)

```ts
it('renders the under-construction heading', async () => {
  const fixture = TestBed.createComponent(App);
  await fixture.whenStable();
  const h1 = fixture.nativeElement.querySelector('main h1');
  expect(h1?.textContent?.trim()).toBe('Portfolio under construction');
});
```

- [ ] **Step 3: Run it to verify it fails**

Run: `npx ng test --watch=false`
Expected: FAIL — `main h1` not found (generated placeholder template).

- [ ] **Step 4: Replace the template and configure i18n**

`app.html`: replace the generated placeholder with `<main><h1 i18n="@@homeTitle">Portfolio under construction</h1></main>` followed by `<router-outlet />`.
`angular.json` → project `i18n`: `"sourceLocale": {"code": "en", "subPath": "en"}`, `"locales": {"es": {"translation": "src/locale/messages.es.xlf", "subPath": "es"}}`. Add build configuration `es` with `"localize": ["es"]` and make `serve`'s default `buildTarget` use it, with `"proxyConfig": "proxy.conf.json"`.
Run `npx ng extract-i18n --output-path src/locale`, copy `messages.xlf` to `messages.es.xlf`, add `<target>Portafolio en construcción</target>` for `homeTitle`, delete `messages.xlf`.
`proxy.conf.json`: `{"/api": {"target": "http://localhost:8080", "secure": false}}`.

- [ ] **Step 5: Run the test and the localized build**

Run: `npx ng test --watch=false` → Expected: 1 passed.
Run: `npx ng build --localize` → Expected: no errors, and
`Select-String "Portafolio en construcción" dist/frontend/browser/es/main-*.js` finds a match,
`Select-String 'lang="es"' dist/frontend/browser/es/index.html` finds a match,
`Select-String 'lang="en"' dist/frontend/browser/en/index.html` finds a match.

- [ ] **Step 6: Commit**

```bash
git add frontend/
git commit -m "feat(frontend): add Angular skeleton with English and Spanish builds"
```

---

### Task 5: Docker Compose stack, nginx and README

**Files:**
- Create: `frontend/Dockerfile`, `frontend/.dockerignore`, `frontend/nginx.conf`
- Create: `docker-compose.yml`, `.env.example`
- Modify: `README.md`

**Interfaces:**
- Consumes: Task 3 backend image build context, Task 4 `npx ng build --localize` output, Task 2 `GET /api/health`.
- Produces: `http://localhost:8080` → `/` redirects to `/es/`, `/es/` and `/en/` serve the app, `/api/*` proxies to the backend.

- [ ] **Step 1: Frontend image and nginx config**

Dockerfile: build stage `node:24-alpine` (`npm ci`, `npx ng build --localize`); runtime `nginx:stable-alpine` serving `dist/frontend/browser` from `/usr/share/nginx/html` with `nginx.conf`. `.dockerignore`: `node_modules/`, `dist/`, `.angular/`.
`nginx.conf` rules (listen 80):
- `location = /` → `return 302 /es/;` (part 4 replaces this with browser-language detection)
- `location /api/` → `proxy_pass http://backend:8080;` with `Host` and `X-Forwarded-*` headers
- `location /es/` → `try_files $uri $uri/ /es/index.html;` and the same for `/en/`

- [ ] **Step 2: Compose file and `.env.example`**

Services:
- `db`: `postgres:17-alpine`, env `POSTGRES_DB/USER/PASSWORD` from `${DB_NAME:-portfolio}`, `${DB_USERNAME:-portfolio}`, `${DB_PASSWORD:-portfolio}`; named volume `db-data`; ports `${DB_PORT:-5432}:5432`; healthcheck `pg_isready -U ${DB_USERNAME:-portfolio} -d ${DB_NAME:-portfolio}` every 5s.
- `backend`: build `./backend`; env `DB_URL=jdbc:postgresql://db:5432/${DB_NAME:-portfolio}`, `DB_USERNAME`, `DB_PASSWORD` with the same defaults; `depends_on: db: condition: service_healthy`; no published port.
- `frontend`: build `./frontend`; ports `${APP_PORT:-8080}:80`; `depends_on: [backend]`.

`.env.example` lists `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `DB_PORT`, `APP_PORT` with the default values and a one-line comment each.

- [ ] **Step 3: Validate the compose file**

Run: `docker compose config --quiet`
Expected: no output, exit code 0.

- [ ] **Step 4: Smoke test from a state with no `.env`**

Run: `Test-Path .env` → `False` (rename it temporarily if present), then `docker compose up -d --build`.
Wait until `docker compose ps` shows `db` healthy and `backend` running, then:
`curl.exe -s http://localhost:8080/api/health` → Expected: `{"status":"UP"}`
`docker compose logs backend` → Expected: contains `Successfully applied 1 migration` (first run) and no `Connection refused` stack traces.

- [ ] **Step 5: Smoke test the frontend routes**

`curl.exe -s -o NUL -w "%{http_code} %{redirect_url}" http://localhost:8080/` → Expected: `302 http://localhost:8080/es/`
`curl.exe -s http://localhost:8080/es/ | Select-String 'lang="es"'` → match
`curl.exe -s http://localhost:8080/en/ | Select-String 'lang="en"'` → match
`curl.exe -s -o NUL -w "%{http_code}" http://localhost:8080/es/projects/foo` → Expected: `200`
Then `docker compose down`.

- [ ] **Step 6: Write the README**

Sections: what the project is (one paragraph), architecture overview (`backend/`, `frontend/`, compose services), prerequisites (Java 17, Maven, Node `^22.22.3 || ^24.15.0`, Docker running — Testcontainers needs it for backend tests), "Run everything" (`docker compose up --build` → `http://localhost:8080`), "Develop with hot reload" (`docker compose up db`, `cd backend; ./mvnw spring-boot:run`, `cd frontend; npm start` → `http://localhost:4200`), configuration table from `.env.example` (mention `DB_PORT` if 5432 is taken), "Run tests" (`./mvnw test`, `npx ng test --watch=false`), and a link to the spec.

- [ ] **Step 7: Commit**

```bash
git add docker-compose.yml .env.example frontend/Dockerfile frontend/.dockerignore frontend/nginx.conf README.md
git commit -m "build: add Docker Compose stack with nginx entrypoint and README"
```

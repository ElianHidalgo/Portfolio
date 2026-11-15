# Portafolio web — Diseño

Fecha: 2026-11-14
Estado: **aprobado** el 2026-11-15 (revisión 2: despliegue gratuito, bilingüe, página pública pre-generada)

## 1. Intención y porqué

Un portafolio personal que cumpla tres propósitos a la vez:

1. **Reclutadores**: ver rápido quién soy, mi stack y mis proyectos; contactarme o descargar mi CV.
2. **Clientes freelance**: entender qué puedo construirles y tener una vía clara para escribirme.
3. **Demostrar dominio de Java + Spring y Angular**: el propio sitio y su código (público en GitHub) son la prueba. Debe estar limpio, bien organizado y levantarse con un comando.

Además, el proyecto es un **vehículo de aprendizaje**: se construye por partes, entendiendo el porqué de cada decisión.

El sitio debe estar **en línea, ser rápido y accesible** (quien llega desde un CV o LinkedIn no debe esperar), estar **en español e inglés** (reclutadores y clientes de fuera) y costar **cero** en hosting.

El contenido (perfil, skills, proyectos) se administra desde un **panel privado** con login, no editando archivos.

## 2. Objetivos, no-objetivos y anti-objetivos

**Objetivos**
- Sitio público de una sola página: hero, sobre mí, skills, proyectos, contacto, enlace al CV; más una página de detalle por proyecto.
- Todo el sitio público en **español e inglés**, incluido el contenido editable.
- La página pública carga al instante: **el visitante nunca espera a que el backend despierte**.
- Panel `/admin` con login para editar perfil, skills y proyectos (en ambos idiomas), publicar los cambios y leer los mensajes de contacto.
- Backend Spring Boot como **monolito modular** con límites entre módulos verificados por test.
- **Desplegado en internet con hosting gratuito** y despliegue automático desde GitHub.
- En local, todo el stack se levanta con `docker compose up` desde un clon limpio.
- README que explique arquitectura, cómo correrlo, cómo desplegarlo y decisiones tomadas.

**No-objetivos (v1)**
- Dominio propio (se usan los subdominios gratuitos; se puede añadir después).
- Subida de archivos/imágenes: imágenes y CV se referencian por URL.
- Múltiples usuarios o roles: hay un único administrador.
- Refresh tokens, recuperación de contraseña, 2FA.
- Notificación por email de nuevos mensajes.
- Blog, analíticas, más idiomas que ES/EN.
- Compilación nativa con GraalVM (posible mejora futura para reducir el arranque del backend).

**Anti-objetivos (sería un fracaso aunque funcione)**
- Sobre-ingeniería: microservicios, colas, Kubernetes, capas sin necesidad. Un revisor técnico debe ver criterio, no complejidad.
- Que no levante a la primera para alguien que clona el repo.
- Código copiado sin entender: cada parte debe poder explicarse en una entrevista.
- Módulos que se acceden entre sí por dentro (la "modularidad" sería solo de nombre).
- Que un visitante vea una página en blanco o un spinner largo mientras el backend arranca.
- Que el hosting genere cobros.

## 3. Restricciones

- Máquina de desarrollo: Windows 11, Java 17 (Temurin), Maven 3.9, Node 22, npm 10, Docker 29, Git.
- Angular CLI no está instalado globalmente: se usa vía `npx @angular/cli`.
- Versiones: la última estable de Spring Boot compatible con Java 17 y la última estable de Angular al momento de crear el proyecto; se fijan en `pom.xml`/`package.json`.
- **Desarrollo íntegramente en inglés** (decisión del usuario): código, nombres de clases/variables/columnas, comentarios, mensajes de commit, README y documentación técnica del repo. El contenido visible del sitio sigue siendo ES/EN, y el panel admin en español.
- **Coste cero**: solo planes gratuitos. Se acepta registrar una tarjeta en Google Cloud (requisito de Cloud Run) siempre que no haya cargos.
- Planes gratuitos vigentes (nov 2026): Cloudflare Pages (gratis, 500 builds/mes); Cloud Run (cuota gratuita permanente, escala a cero, arranque en frío de la JVM de ~10–20 s); Neon (PostgreSQL gratuito, 1 GB por proyecto, también escala a cero).

## 4. Enfoques considerados

### 4.1 Arquitectura del backend

| Enfoque | Ventajas | Desventajas |
|---|---|---|
| **A. Monolito modular** (una app Spring, paquetes por módulo + Spring Modulith) | Simple de correr y depurar; modularidad real verificada por test; muy valorado hoy | Los límites dependen de disciplina + test |
| B. Maven multimódulo | Límites impuestos por compilador | Más poms y configuración; fricción al aprender; excesivo para 4 módulos |
| C. Microservicios + gateway | "Suena" impresionante | Contradice "simple"; complejidad distribuida sin necesidad |

**Elegido: A.** Da modularidad verificable manteniendo la simplicidad.

### 4.2 Hosting

| Enfoque | Ventajas | Desventajas |
|---|---|---|
| VPS propio con el mismo `docker compose` (~5 USD/mes) | Control total, sin arranques en frío | No es gratis |
| Plataforma gestionada para todo (Render/Railway/Fly) | Sin servidor que mantener | Los planes gratis duermen; BD gestionada encarece |
| **Partes separadas: frontend estático + backend en plataforma + BD gestionada** | Gratis | Más piezas; CORS entre dominios |
| Oracle Cloud Always Free (VPS gratis) | Gratis con control total | Recorte del plan en ago 2026, falta de capacidad, riesgo de que reclamen servidores inactivos |

**Elegido por el usuario: partes separadas**, porque se requiere coste cero.

### 4.3 Cómo evitar que el visitante espere al backend

| Enfoque | Ventajas | Desventajas |
|---|---|---|
| **A. Página pública pre-generada** (HTML generado en el build con los datos de la API; el backend solo para panel y contacto) | Carga instantánea desde CDN; buen SEO; mantiene Spring | Los cambios se ven tras publicar (1–3 min); una pieza más en la publicación |
| B. Spring compilado a nativo (GraalVM) | Arranque < 1 s | Builds lentos, problemas con librerías; complejidad para aprender |
| C. Cambiar de stack (p. ej. Cloudflare Workers + D1) | Instantáneo y gratis | Elimina Spring: contradice el propósito 3 |

**Elegido por el usuario: A, con el backend en Cloud Run** (frente a Render: arranque de ~10–20 s en vez de ~1 min y más memoria).

## 5. Arquitectura

### 5.1 Vista general

```
Visitante ──► Cloudflare Pages (HTML pre-generado /es y /en)   ← instantáneo
                 │
                 ├─ formulario de contacto ─► Cloud Run (Spring) ─► Neon (PostgreSQL)
                 └─ "ping" al cargar la página para despertar el backend

Admin ──► /admin (Angular, renderizado en el navegador) ─► Cloud Run ─► Neon
            └─ botón "Publicar" ─► backend llama al deploy hook de Cloudflare Pages
                                     └─ Cloudflare reconstruye el sitio leyendo la API pública
```

### 5.2 Estructura del repositorio (monorepo)

```
portfolio/
  backend/              Spring Boot (Maven) + Dockerfile
  frontend/             Angular
  docker-compose.yml    desarrollo/demo local: postgres + backend + frontend
  .env.example          variables (credenciales admin, JWT secret, DB, CORS, deploy hook)
  .github/workflows/    CI (tests) y despliegue del backend a Cloud Run
  README.md
  docs/
```

### 5.3 Backend — módulos

Paquete raíz `dev.<usuario>.portfolio` (usuario de GitHub pendiente; se define antes de crear el proyecto). Cada módulo es un paquete de primer nivel con sus capas internas; solo expone lo que otros necesitan (Spring Modulith: lo que está en subpaquetes `internal` no es accesible desde fuera).

| Módulo | Responsabilidad | Endpoints públicos | Endpoints admin |
|---|---|---|---|
| `profile` | Datos personales (nombre, titular ES/EN, sobre mí ES/EN, ubicación, email, GitHub, LinkedIn, URL avatar, URL CV ES/EN) y lista de skills (nombre, categoría, orden) | `GET /api/profile` | `PUT /api/admin/profile`, CRUD `/api/admin/skills` |
| `projects` | Proyectos: título ES/EN, slug, resumen ES/EN, descripción ES/EN, tecnologías (tags), URL repo, URL demo, URL imagen, destacado, orden, publicado | `GET /api/projects` (solo publicados), `GET /api/projects/{slug}` | CRUD `/api/admin/projects` |
| `contact` | Mensajes del formulario: nombre, email, mensaje, idioma de la página, fecha, leído | `POST /api/contact` | `GET /api/admin/messages`, `PATCH .../{id}/read`, `DELETE .../{id}` |
| `publishing` | Disparar la reconstrucción del sitio público (llamada HTTP al deploy hook de Cloudflare Pages) y guardar la fecha de la última publicación | — | `POST /api/admin/publish`, `GET /api/admin/publish/status` |
| `auth` | Login del admin, emisión y validación de JWT, configuración de Spring Security | `POST /api/auth/login` | — |
| `shared` | Manejo global de errores (`ProblemDetail`), CORS, config común | `GET /api/health` (ping de "despertar") | — |

Capas dentro de cada módulo: `web` (controllers + DTOs), `application` (servicios), `domain`/`persistence` (entidades JPA + repositorios). Los controllers nunca exponen entidades JPA, solo DTOs (records).

La API pública devuelve **ambos idiomas** en cada respuesta (p. ej. `{"summary": {"es": "...", "en": "..."}}`); el frontend elige según la versión que se está generando.

### 5.4 Persistencia
- PostgreSQL (Neon en producción, contenedor en local). Migraciones con **Flyway** (`ddl-auto=validate`; el esquema lo define Flyway, no Hibernate).
- Textos bilingües como **pares de columnas** (`summary_es`, `summary_en`), ambas obligatorias.
- El **slug es único y compartido** entre idiomas.
- Las **categorías de skills son un conjunto fijo** (p. ej. Backend, Frontend, Base de datos, DevOps, Herramientas) cuyo nombre se traduce en la interfaz; el nombre del skill no se traduce.
- Una migración de datos semilla con un perfil y un proyecto de ejemplo (en ambos idiomas) para que el sitio no arranque vacío.

### 5.5 Autenticación y seguridad
- Un único admin. Usuario y contraseña vienen de variables de entorno (`ADMIN_USERNAME`, `ADMIN_PASSWORD`); al arrancar, si no existe, se crea con la contraseña hasheada (BCrypt).
- `POST /api/auth/login` devuelve un JWT firmado (secreto en `JWT_SECRET`), expiración 2 horas.
- Spring Security stateless: `/api/admin/**` requiere token válido; el resto de `/api/**` es público.
- **CORS**: orígenes permitidos desde la variable `CORS_ALLOWED_ORIGINS` (dominio `*.pages.dev` del sitio y `http://localhost:4200` en desarrollo).
- `POST /api/contact` con validación de entrada y límite de tamaño; anti-spam básico: campo honeypot oculto.
- Ningún secreto en el repositorio: `.env` en `.gitignore`; en Cloud Run, variables de entorno configuradas en el servicio.

### 5.6 API y calidad
- Validación con Bean Validation en los DTOs de entrada.
- Errores en formato `ProblemDetail` (RFC 9457).
- Documentación con **springdoc-openapi** (Swagger UI en `/swagger-ui.html`).
- Tests:
  - Test de Spring Modulith que verifica los límites entre módulos.
  - Tests de integración por módulo con **Testcontainers** (PostgreSQL real).
  - Tests unitarios donde haya lógica (servicios, JWT).
  - El módulo `publishing` se prueba sin llamar a Cloudflare (cliente HTTP simulado).

### 5.7 Frontend — Angular
- Componentes standalone, signals, `HttpClient`.
- **Pre-generación (SSG)** de las páginas públicas en el build: Angular hace prerender leyendo la API (`API_URL` en tiempo de build). Las rutas de detalle se generan para cada slug publicado.
- **Bilingüe** con la traducción integrada de Angular (`@angular/localize`), que produce una versión por idioma:
  - `/es/` y `/en/` — página pública única con secciones (hero, sobre mí, skills, proyectos, contacto).
  - `/es/projects/:slug` y `/en/projects/:slug` — detalle de proyecto.
  - `/` — página mínima que redirige a `/es/` o `/en/` según el idioma del navegador (español por defecto).
  - Selector de idioma en el encabezado que lleva a la misma página en el otro idioma.
  - Etiquetas `lang` y `hreflang` correctas para accesibilidad y SEO.
- **Panel admin** (`/admin/login` y `/admin/**`), **solo en español**, **cargado de forma lazy** y renderizado en el navegador (no se pre-genera); requiere regla de fallback de SPA en Cloudflare Pages.
  - Formularios con campos ES y EN lado a lado.
  - Botón **"Publicar"** que llama a `POST /api/admin/publish` y muestra la fecha de la última publicación.
  - Aviso visible de que el panel puede tardar unos segundos al entrar (el backend despierta).
- **Contacto**: al cargar la página pública se lanza en segundo plano `GET /api/health` para despertar el backend; el formulario muestra un estado "enviando" claro y maneja errores.
- Estructura por funcionalidad: `public/`, `admin/`, `core/` (servicios API, interceptor JWT, guard), `shared/` (componentes reutilizables).
- Interceptor que añade `Authorization: Bearer` a las peticiones admin; guard que redirige a login si no hay token o expiró; token guardado en `sessionStorage`.
- Estilos: SCSS propio con variables CSS (sin librería de componentes), diseño responsive.
- **Accesibilidad**: HTML semántico, contraste suficiente, navegación por teclado, textos alternativos en imágenes.

### 5.8 Ejecución y despliegue

**Desarrollo local**
- `docker compose up db` + `mvn spring-boot:run` + `ng serve` con proxy de `/api` hacia el backend, para recarga en caliente.
- `docker compose up` levanta todo como demo local (db + backend + frontend servido por nginx con proxy de `/api`) en `http://localhost:8080`.
- Configuración por variables de entorno; `.env.example` versionado, `.env` en `.gitignore`.

**Producción (gratis)**
- **Base de datos**: Neon, un proyecto en una región cercana a la de Cloud Run; conexión con SSL. Flyway aplica las migraciones al arrancar el backend.
- **Backend**: Cloud Run, escala a cero (`min-instances=0`), memoria suficiente para Spring (512 MiB–1 GiB), región elegible para la cuota gratuita. La imagen se construye con el `Dockerfile` multi-stage y se guarda en Artifact Registry. Variables: credenciales DB, `JWT_SECRET`, `ADMIN_*`, `CORS_ALLOWED_ORIGINS`, `CF_DEPLOY_HOOK_URL`.
- **Frontend**: Cloudflare Pages conectado al repo de GitHub (integración Git): build en cada push a `main` y cuando el backend llama al **deploy hook** (botón "Publicar").
- **GitHub Actions**: en cada push/PR ejecuta los tests del backend; en push a `main`, construye la imagen y despliega a Cloud Run.
- **Protección de coste**: alerta de presupuesto en Google Cloud a un monto mínimo para detectar cualquier cargo.

## 6. Construcción por partes

Cada parte deja algo funcionando y entendible:

1. **Esqueleto**: repo, backend con `/api/health`, frontend vacío con ES/EN configurado, Postgres + Flyway, `docker compose up` funcionando.
2. **Módulo `profile`** (lectura pública, bilingüe) + secciones hero/sobre mí/skills en Angular.
3. **Módulo `projects`** (lectura pública, bilingüe) + sección proyectos y página de detalle.
4. **Pre-generación**: build estático de `/es` y `/en` leyendo la API, redirección de la raíz y selector de idioma.
5. **Primer despliegue**: Neon + Cloud Run + Cloudflare Pages + GitHub Actions; el sitio de solo lectura queda en línea.
6. **Módulo `contact`** + formulario y ping de despertar.
7. **Módulo `auth`** + login y guard en Angular.
8. **Panel admin**: CRUD bilingüe de perfil, skills, proyectos, bandeja de mensajes y botón "Publicar" (módulo `publishing`).
9. **Pulido**: diseño visual, accesibilidad, Swagger, README, test de Modulith completo.

## 7. Decisiones

**Tomadas por el usuario**
- Monolito modular con Spring Modulith.
- Despliegue en la v1, con coste cero: frontend, backend y base de datos separados.
- Sitio y contenido editable en español e inglés.
- Página pública pre-generada; backend en Cloud Run (acepta registrar tarjeta sin cargos).

**Tomadas por Claude (revisables)**
- PostgreSQL + Flyway; Neon como proveedor en producción.
- Cloudflare Pages para el frontend.
- JWT stateless de 2 h, sin refresh token; un único admin por variables de entorno.
- Imágenes y CV por URL (sin subida de archivos en v1); un CV por idioma.
- SCSS propio sin librería de UI.
- Rutas `/es/` y `/en/`, redirección por idioma del navegador, traducción integrada de Angular.
- Textos bilingües como pares de columnas; slug compartido; categorías de skills fijas.
- Panel admin solo en español.
- Botón "Publicar" en vez de reconstruir con cada guardado.
- Cloudflare Pages con integración Git + deploy hook; GitHub Actions para tests y despliegue del backend.
- Alerta de presupuesto en Google Cloud.
- nginx solo para la demo local con `docker compose`.

## 8. A criterio del implementador

- Nombres exactos de clases, DTOs y columnas.
- Paleta, tipografía y detalles visuales (dentro de "profesional y simple").
- Formato exacto del contenido de semilla.
- Región concreta de Cloud Run y Neon (cercanas entre sí y dentro de la cuota gratuita).
- Ajustes para reducir el arranque en frío de Spring (p. ej. inicialización lazy, CDS) si se mide que hace falta.

## 9. Pendiente

- Usuario de GitHub para el paquete raíz (`dev.<usuario>.portfolio`): antes de la parte 1.

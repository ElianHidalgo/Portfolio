# Portafolio web — Diseño

Fecha: 2026-11-14
Estado: **aprobado** el 2026-11-15 (revisión 4, 2026-11-15: backend en Render en lugar de Cloud Run por un prepago de 50 USD; frontend en Cloudflare Workers con archivos estáticos en lugar de Pages; esquema por módulo y rol de BD de solo datos; revisión 5, 2026-11-15: módulo `contact` con límites anti-spam, CORS al origen exacto del sitio y ping de despertar al acercarse al formulario; revisión 6, 2026-11-15: autenticación — el entorno manda sobre la contraseña del admin, JWT HS256 con emisor, límite de intentos de login, cabecera `Authorization` en CORS y páginas propias del panel; revisión 7, 2026-11-18: la parte 8 se divide en 8a bandeja + "Publicar", 8b editor de perfil y skills, 8c editor de proyectos; revisión 8, 2026-11-18: 8b hecha — editor solo de texto y enlaces, sin subida de archivos por decisión del dueño)

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
- **Coste cero absoluto** (regla del usuario): ningún pago, prepago ni tarjeta. Solo planes gratuitos que no la exijan.
- Planes gratuitos vigentes (nov 2026): Cloudflare Workers con archivos estáticos (gratis, sin tarjeta; 3 000 min de build/mes); Render (servicio web gratuito sin tarjeta: 750 h/mes, 512 MB, 0,1 CPU, se duerme tras 15 min sin tráfico y despierta en ~30–60 s); Neon (PostgreSQL gratuito, 1 GB por proyecto, también escala a cero).

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

**Elegido por el usuario: A, con el backend en Render.** Inicialmente se eligió Cloud Run (arranque más rápido), pero Google Cloud le exigió al usuario un prepago de 50 USD, incompatible con el coste cero. Con la página pre-generada, el arranque lento de Render solo afecta al build, al formulario de contacto (mitigado con un ping al acercarse a la sección de contacto) y al panel admin.

## 5. Arquitectura

### 5.1 Vista general

```
Visitante ──► Cloudflare Workers (HTML pre-generado /es y /en)   ← instantáneo
                 │
                 ├─ formulario de contacto ─► Render (Spring) ─► Neon (PostgreSQL)
                 └─ "ping" al acercarse al formulario para despertar el backend

Admin ──► /admin (Angular, renderizado en el navegador) ─► Render ─► Neon
            └─ botón "Publicar" ─► backend llama al deploy hook de Cloudflare (Workers Builds)
                                     └─ Cloudflare reconstruye el sitio leyendo la API pública
```

### 5.2 Estructura del repositorio (monorepo)

```
portfolio/
  backend/              Spring Boot (Maven) + Dockerfile
  frontend/             Angular
  docker-compose.yml    desarrollo/demo local: postgres + backend + frontend
  .env.example          variables (credenciales admin, JWT secret, DB, CORS, deploy hook)
  .github/workflows/    CI (tests); Render despliega el backend desde GitHub
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
| `publishing` | Disparar la reconstrucción del sitio público (llamada HTTP al deploy hook de Workers Builds) y guardar la fecha de la última publicación | — | `POST /api/admin/publish`, `GET /api/admin/publish/status` |
| `auth` | Login del admin, emisión y validación de JWT, configuración de Spring Security | `POST /api/auth/login` | `GET /api/admin/session` |
| `shared` | Manejo global de errores (`ProblemDetail`), CORS, config común | `GET /api/health` (ping de "despertar") | — |

Cada módulo es dueño de su **esquema** de base de datos (`profile`, `projects`, `contact`); la app se conecta con un rol de solo datos (`portfolio_app`) y Flyway migra con el rol dueño.

Capas dentro de cada módulo: `web` (controllers + DTOs), `application` (servicios), `domain`/`persistence` (entidades JPA + repositorios). Los controllers nunca exponen entidades JPA, solo DTOs (records).

La API pública devuelve **ambos idiomas** en cada respuesta (p. ej. `{"summary": {"es": "...", "en": "..."}}`); el frontend elige según la versión que se está generando.

### 5.4 Persistencia
- PostgreSQL (Neon en producción, contenedor en local). Migraciones con **Flyway** (`ddl-auto=validate`; el esquema lo define Flyway, no Hibernate).
- Textos bilingües como **pares de columnas** (`summary_es`, `summary_en`), ambas obligatorias.
- El **slug es único y compartido** entre idiomas.
- Las **categorías de skills son un conjunto fijo** (p. ej. Backend, Frontend, Base de datos, DevOps, Herramientas) cuyo nombre se traduce en la interfaz; el nombre del skill no se traduce.
- Una migración de datos semilla con un perfil y un proyecto de ejemplo (en ambos idiomas) para que el sitio no arranque vacío.

### 5.5 Autenticación y seguridad
- Un único admin. Usuario y contraseña vienen de variables de entorno (`ADMIN_USERNAME`, `ADMIN_PASSWORD`); en cada arranque la cuenta se ajusta a ellas (se crea si no existe y se vuelve a hashear con BCrypt si la contraseña cambió), así que rotar la contraseña solo requiere cambiarla en Render. Usuario de 3 a 50 caracteres; contraseña de 12 caracteres como mínimo y 72 bytes como máximo (límite de BCrypt); si no cumplen, no hay admin y se registra un aviso sin los valores.
- `POST /api/auth/login` devuelve un JWT HS256 firmado (secreto en `JWT_SECRET`: base64 de al menos 32 bytes; sin él la app no arranca), emisor `portfolio-api`, expiración 2 horas. Cualquier fallo responde igual (401, mismo tiempo de BCrypt aunque el usuario no exista); 5 intentos por cliente cada 15 minutos.
- Spring Security stateless (sin sesiones ni cookies, por eso CSRF no aplica): `/api/admin/**` requiere token válido; el resto de `/api/**` es público. Los 401 son problem JSON con `WWW-Authenticate: Bearer`. CORS permite la cabecera `Authorization`.
- **CORS**: orígenes exactos permitidos desde la variable `CORS_ALLOWED_ORIGINS` (el dominio `*.workers.dev` del sitio en producción y `http://localhost:4200` en desarrollo); sin comodines ni credenciales.
- **Límites de la API**: 60 peticiones por minuto por cliente (identificado por `CF-Connecting-IP`, nunca `X-Forwarded-For`) y cuerpos POST de 16 KiB como máximo.
- `POST /api/contact` con validación de entrada y límite de tamaño; anti-spam básico: campo honeypot oculto (respuesta idéntica, no se guarda), 5 mensajes por cliente y hora, y un tope de 50 mensajes guardados por 24 h para no llenar la base gratuita.
- Ningún secreto en el repositorio: `.env` en `.gitignore`; en Render, variables de entorno configuradas en el servicio.

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
- **Panel admin** (`/admin/login` y `/admin/**`), **solo en español**, **cargado de forma lazy** y renderizado en el navegador (no se pre-genera); lo sirven páginas propias (`es/admin/index.html` y `es/admin/login/index.html`, copias del shell cliente con `<meta name="api-origin">`) para responder 200 en vez del 404, con `X-Robots-Tag: noindex`. La versión en inglés no tiene panel.
  - Formularios con campos ES y EN lado a lado.
  - Botón **"Publicar"** que llama a `POST /api/admin/publish` y muestra la fecha de la última publicación.
  - Aviso visible de que el panel puede tardar unos segundos al entrar (el backend despierta).
- **Contacto**: cuando la sección de contacto se acerca a la pantalla (o al enfocar el formulario) se lanza en segundo plano `GET /api/health/liveness` para despertar el backend (no toca la base de datos; solo quien llega al formulario lo despierta); el navegador conoce el origen de la API por `<meta name="api-origin">` escrito en el build y la CSP `connect-src` solo lo permite a él; el formulario muestra un estado "enviando" claro y maneja errores.
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
- **Base de datos**: Neon, un proyecto en una región cercana a la de Render; conexión con SSL. Flyway aplica las migraciones al arrancar el backend.
- **Backend**: Render, servicio web gratuito con el `Dockerfile` multi-stage, desplegado automáticamente desde `main`; JVM ajustada a 512 MB y 0,1 CPU. Variables: credenciales DB, `JWT_SECRET`, `ADMIN_*`, `CORS_ALLOWED_ORIGINS`, `CF_DEPLOY_HOOK_URL`.
- **Frontend**: Cloudflare Workers (solo archivos estáticos, `frontend/wrangler.jsonc`) conectado al repo de GitHub (Workers Builds): build en cada push a `main` y cuando el backend llama al **deploy hook** (botón "Publicar").
- **GitHub Actions**: en cada push/PR ejecuta los tests del backend; en push a `main`, Render despliega el backend automáticamente.
- **Protección de coste**: alerta de presupuesto en Google Cloud a un monto mínimo para detectar cualquier cargo.

## 6. Construcción por partes

Cada parte deja algo funcionando y entendible:

1. **Esqueleto**: repo, backend con `/api/health`, frontend vacío con ES/EN configurado, Postgres + Flyway, `docker compose up` funcionando.
2. **Módulo `profile`** (lectura pública, bilingüe) + secciones hero/sobre mí/skills en Angular.
3. **Módulo `projects`** (lectura pública, bilingüe) + sección proyectos y página de detalle.
4. **Pre-generación**: build estático de `/es` y `/en` leyendo la API, redirección de la raíz y selector de idioma.
5. **Primer despliegue**: Neon + Render + Cloudflare Workers + GitHub Actions; el sitio de solo lectura queda en línea.
6. **Módulo `contact`** + formulario y ping de despertar.
7. **Módulo `auth`** + login y guard en Angular.
8. **Panel admin**, en tres entregas publicables por separado:
   - **8a**: bandeja de mensajes (paginada, más nuevos primero, leído/no leído, borrar) y botón "Publicar" (módulo `publishing`: deploy hook de Workers Builds en `CLOUDFLARE_DEPLOY_HOOK_URL`, como mínimo un minuto entre publicaciones, 503 si no está configurado, 502 si Cloudflare falla; el historial guarda la fecha de cada publicación).
   - **8b** (hecha): editor bilingüe de perfil y skills, solo texto y enlaces. `GET`/`PUT /api/admin/profile` y `POST`/`PUT`/`DELETE /api/admin/skills` más `PUT /api/admin/skills/order`; GitHub y LinkedIn deben ser `https`; avatar y CV son `https` o una ruta relativa sin `..`; `409` si el nombre de una skill se repite. Sin subida de archivos ni almacenamiento nuevo, por decisión del dueño (2026-11-18): el CV y el avatar siguen en `frontend/public/`. Los cambios llegan al sitio público tras pulsar Publicar.
   - **8c**: editor bilingüe de proyectos.
9. **Pulido**: diseño visual, accesibilidad, Swagger, README, test de Modulith completo.

## 7. Decisiones

**Tomadas por el usuario**
- Monolito modular con Spring Modulith.
- Despliegue en la v1, con coste cero: frontend, backend y base de datos separados.
- Sitio y contenido editable en español e inglés.
- Página pública pre-generada; backend en Render (sin tarjeta), tras descartar Cloud Run por su prepago.

**Tomadas por Claude (revisables)**
- PostgreSQL + Flyway; Neon como proveedor en producción.
- Cloudflare Workers (archivos estáticos) para el frontend; Cloudflare creó Workers en lugar de Pages al conectar el repo.
- JWT stateless de 2 h, sin refresh token; un único admin por variables de entorno.
- Imágenes y CV por URL (sin subida de archivos en v1); un CV por idioma.
- SCSS propio sin librería de UI.
- Rutas `/es/` y `/en/`, redirección por idioma del navegador, traducción integrada de Angular.
- Textos bilingües como pares de columnas; slug compartido; categorías de skills fijas.
- Panel admin solo en español.
- Botón "Publicar" en vez de reconstruir con cada guardado.
- Cloudflare Workers Builds con integración Git + deploy hook; GitHub Actions para tests y despliegue del backend.
- Alerta de presupuesto en Google Cloud.
- nginx solo para la demo local con `docker compose`.

## 8. A criterio del implementador

- Nombres exactos de clases, DTOs y columnas.
- Paleta, tipografía y detalles visuales (dentro de "profesional y simple").
- Formato exacto del contenido de semilla.
- Región concreta de Render y Neon (cercanas entre sí y dentro de la cuota gratuita).
- Ajustes para reducir el arranque en frío de Spring (p. ej. inicialización lazy, CDS) si se mide que hace falta.

## 9. Pendiente

- Usuario de GitHub para el paquete raíz (`dev.<usuario>.portfolio`): antes de la parte 1.

-- The owner's projects (sources: the repositories' READMEs and the CV).
-- From part 8 on, projects are edited from the admin panel.

insert into project (slug, display_order, featured, title_es, title_en, summary_es, summary_en,
                     description_es, description_en, technologies, repo_url)
values
(
    'portfolio', 1, true,
    'Portafolio personal',
    'Personal portfolio',
    'Este sitio: una API en Spring Boot organizada como monolito modular y un frontend en Angular, bilingüe y desplegado sin coste.',
    'This site: a Spring Boot API organised as a modular monolith and an Angular front end, bilingual and deployed at no cost.',
    E'El backend es un monolito modular con Spring Modulith: cada funcionalidad (perfil, proyectos, contacto) vive en su propio módulo, y un test hace fallar el build si un módulo accede a las partes internas de otro. El esquema de la base de datos lo gestiona Flyway y las pruebas de integración usan un PostgreSQL real con Testcontainers.\n\n'
     'El frontend en Angular usa componentes standalone y signals, y se publica en español e inglés con la traducción integrada del framework. El build falla si falta una traducción.\n\n'
     'Todo el proyecto se levanta con un solo comando de Docker Compose y se construye por partes, cada una con su plan, sus pruebas y una revisión de código independiente.',
    E'The back end is a modular monolith built with Spring Modulith: each feature (profile, projects, contact) lives in its own module, and a test fails the build if one module reaches into another''s internals. Flyway owns the database schema, and integration tests run against a real PostgreSQL through Testcontainers.\n\n'
     'The Angular front end uses standalone components and signals, and ships in Spanish and English with the framework''s built-in translation. The build fails if a translation is missing.\n\n'
     'The whole project starts with a single Docker Compose command and is built in parts, each with its own plan, tests and an independent code review.',
    array['Java', 'Spring Boot', 'Spring Modulith', 'PostgreSQL', 'Flyway', 'Angular', 'TypeScript', 'Docker'],
    'https://github.com/ElianHidalgo/Portfolio'
),
(
    'gamevault', 2, false,
    'GameVault',
    'GameVault',
    'App multiplataforma en .NET MAUI para organizar una colección personal de videojuegos, consolas y accesorios.',
    'A cross-platform .NET MAUI app to organise a personal collection of video games, consoles and accessories.',
    E'GameVault es una aplicación multiplataforma hecha con .NET MAUI para gestionar una colección personal de videojuegos, consolas y accesorios.\n\n'
     'Este primer prototipo cubre el flujo principal: inicio de sesión, registro de artículos, catálogo personal con filtros por categoría y búsqueda, sobre una estructura preparada para MVVM.\n\n'
     'Los siguientes pasos son el almacenamiento local en SQLite, la integración con una API REST, el escaneo de códigos de barras y QR, y la sincronización offline-first.',
    E'GameVault is a cross-platform application built with .NET MAUI to manage a personal collection of video games, consoles and accessories.\n\n'
     'This first prototype covers the main flow: sign-in, item registration, and a personal catalogue with category filters and search, on an MVVM-ready structure.\n\n'
     'Next steps are local storage with SQLite, REST API integration, barcode and QR scanning, and offline-first synchronisation.',
    array['C#', '.NET MAUI', 'XAML', 'MVVM'],
    'https://github.com/ElianHidalgo/GameVault'
),
(
    'proxy-cache-api', 3, false,
    'API de catálogo con Proxy y Caché',
    'Catalog API with Proxy and Cache',
    'API REST en Spring Boot que aplica los patrones Proxy y Caché sobre un servicio de productos con latencia simulada.',
    'A Spring Boot REST API that applies the Proxy and Cache patterns to a product service with simulated latency.',
    E'Una API REST de catálogo de productos en Spring Boot en la que cada llamada tiene una latencia simulada a propósito, para que el efecto del diseño sea medible.\n\n'
     'Sobre esa base implementé una caché y el patrón Proxy, cuidando la cohesión, el acoplamiento y el uso de abstracciones. También resolví el problema de CORS que aparece al conectar un frontend en desarrollo.\n\n'
     'Fue una actividad del curso de Análisis de Diseño de Software, evaluada por la calidad del diseño y no solo por que funcione.',
    E'A Spring Boot product catalogue REST API where every call has a deliberately simulated latency, so the effect of the design can be measured.\n\n'
     'On top of it I implemented a cache and the Proxy pattern, focusing on cohesion, coupling and the use of abstractions. I also solved the CORS issue that appears when a front end connects during development.\n\n'
     'It was an activity for the Software Design Analysis course, graded on the quality of the design, not only on whether it works.',
    array['Java', 'Spring Boot', 'REST', 'Design patterns'],
    'https://github.com/ElianHidalgo/demo-app-proxy-cache'
);
